# Telegram MR Notifier

## PRD

### Название

Добавить Telegram-уведомление при создании Merge Request.

### Цель

Сервис должен принимать данные из GitLab CI/CD при запуске Merge Request pipeline или push pipeline в рабочих ветках и отправлять уведомление в Telegram-чат через Telegram-бота, который уже добавлен в группу.

### Как это работает

```text
GitLab pipeline
        ↓
GitLab CI job telegram_notify_gitlab
        ↓
Физический сервер с этим Spring Boot приложением
        ↓
Telegram Bot API
        ↓
Telegram-группа или канал
```

GitLab не отправляет сообщение в Telegram напрямую. GitLab CI отправляет данные на сервер:

```text
POST /api/telegram/gitlab/merge-request
```

Сервер проверяет секрет, форматирует сообщение и отправляет его в Telegram через Bot API.

Уведомление отправляется для Merge Request pipeline в `test`, `dev`, `main`, `master`, а также для push pipeline в эти ветки.

## GitLab flow

Рекомендуемый процесс для разработки:

```text
feature branch -> Merge Request в dev -> code review -> merge в dev
dev -> release Merge Request в main
```

`dev` - ветка для проверки и объединения текущих задач. В нее вливаются feature-ветки после review.

`main` - стабильная ветка. В нее лучше вливать только проверенные изменения из `dev`.

Если проект маленький и работает один разработчик, можно делать MR сразу в `main`. Но для командного процесса ветка `dev` удобнее: она отделяет ежедневную разработку от стабильной версии.

## Что должно быть в сообщении

```text
✅ SUCCESS — telegram_notify_gitlab

📦 Сервис: mobile-client-service
🔀 MR: Исправление OCR проверки паспорта
🌿 Ветка: feature/ocr-fix → develop
👤 Автор: Иван Иванов
👀 Ревьювер: @reviewer_username

🔗 Merge Request
🔗 Пайплайн
```

Сообщение содержит:

- статус job;
- название сервиса;
- название Merge Request;
- source branch;
- target branch;
- автора;
- тег ревьювера из `${TELEGRAM_USERS}`;
- ссылку на Merge Request;
- ссылку на pipeline.
- данные commit.

## Переменные на сервере

На физическом сервере приложению нужны environment variables:

```text
PORT=10000
TELEGRAM_BOT_TOKEN=your_bot_token
TELEGRAM_CHAT_ID=your_chat_id
WEBHOOK_SECRET=your-secret
```

Назначение:

- `PORT` - порт приложения. По умолчанию можно использовать `10000`.
- `TELEGRAM_BOT_TOKEN` - токен Telegram-бота от BotFather.
- `TELEGRAM_CHAT_ID` - id группы или канала, куда бот отправляет сообщения.
- `WEBHOOK_SECRET` - секрет для защиты endpoint от посторонних запросов.

Реальные значения нельзя хранить в Git. Они должны быть только на сервере и в GitLab CI/CD Variables.

## Требования к Telegram

1. Telegram-бот уже создан через BotFather.
2. Бот добавлен в нужную группу или канал.
3. У бота есть право отправлять сообщения.
4. Известен `TELEGRAM_CHAT_ID` группы или канала.

Если бот пишет в канал, он должен быть добавлен в канал как администратор.

## Endpoint сервера

Health checks:

```text
GET /actuator/health
GET /api/telegram/ping
GET /
GET /health
GET /uptime
HEAD /
HEAD /health
HEAD /uptime
```

Endpoint для GitLab MR:

```text
POST /api/telegram/gitlab/merge-request
Content-Type: application/x-www-form-urlencoded
Header: x-webhook-secret: WEBHOOK_SECRET
```

Endpoint также принимает JSON.

## GitLab CI/CD variables

В GitLab проекте открыть:

```text
Settings -> CI/CD -> Variables
```

Добавить:

```text
TELEGRAM_NOTIFY_URL=https://your-domain.com
TELEGRAM_NOTIFY_SECRET=your-secret
TELEGRAM_USERS=@reviewer_username
SERVICE_NAME=mobile-client-service
```

Назначение:

- `TELEGRAM_NOTIFY_URL` - публичный URL физического сервера без `/api/...`.
- `TELEGRAM_NOTIFY_SECRET` - тот же секрет, что `WEBHOOK_SECRET` на сервере.
- `TELEGRAM_USERS` - reviewer или reviewers, например `@ivan @aliya`.
- `SERVICE_NAME` - название сервиса в сообщении. Если переменная пустая, используется `CI_PROJECT_NAME`.

Для `TELEGRAM_NOTIFY_SECRET` рекомендуется включить `Masked`. `Protected` включать не нужно, если MR pipeline запускается из обычных feature-веток.

## GitLab CI job

Готовый шаблон находится в файле:

```text
.gitlab-ci.telegram-notify.yml
```

В основном `.gitlab-ci.yml` проекта можно подключить его так:

```yaml
include:
  - local: .gitlab-ci.telegram-notify.yml
```

В pipeline должен быть stage `notify`:

```yaml
stages:
  - build
  - test
  - deploy
  - notify
```

Job запускается для Merge Request pipeline в `test`, `dev`, `main`, `master` и для push pipeline в эти ветки:

```yaml
rules:
  - if: >
      $CI_PIPELINE_SOURCE == "merge_request_event" &&
      $CI_MERGE_REQUEST_TARGET_BRANCH_NAME =~ /^(test|dev|main|master)$/
  - if: >
      $CI_PIPELINE_SOURCE == "push" &&
      $CI_COMMIT_BRANCH =~ /^(test|dev|main|master)$/
```

Ошибка отправки Telegram-сообщения не ломает pipeline, потому что job имеет `allow_failure: true`, а `curl` заканчивается через `|| true`.

## Деплой на физический сервер

### Вариант 1: Docker

На сервере должны быть установлены Docker и доступ к Git-репозиторию.

Сборка образа:

```bash
docker build -t telegram-mr-notifier .
```

Запуск контейнера:

```bash
docker run -d \
  --name telegram-mr-notifier \
  --restart unless-stopped \
  -p 10000:10000 \
  -e PORT=10000 \
  -e TELEGRAM_BOT_TOKEN="your_bot_token" \
  -e TELEGRAM_CHAT_ID="your_chat_id" \
  -e WEBHOOK_SECRET="your-secret" \
  telegram-mr-notifier
```

Проверка:

```bash
curl http://localhost:10000/health
```

### Вариант 2: systemd без Docker

На сервере нужны Java 21 и Maven.

Сборка:

```bash
mvn clean package -DskipTests
```

Файл переменных:

```bash
sudo nano /etc/telegram-mr-notifier.env
```

Пример:

```text
PORT=10000
TELEGRAM_BOT_TOKEN=your_bot_token
TELEGRAM_CHAT_ID=your_chat_id
WEBHOOK_SECRET=your-secret
```

Пример systemd service:

```ini
[Unit]
Description=Telegram MR Notifier
After=network.target

[Service]
EnvironmentFile=/etc/telegram-mr-notifier.env
WorkingDirectory=/opt/telegram-mr-notifier
ExecStart=/usr/bin/java -jar /opt/telegram-mr-notifier/target/telegram-json-notifier-0.1.0.jar
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

Запуск:

```bash
sudo systemctl daemon-reload
sudo systemctl enable telegram-mr-notifier
sudo systemctl start telegram-mr-notifier
sudo systemctl status telegram-mr-notifier
```

## Публичный доступ к серверу

GitLab должен иметь доступ к серверу по публичному HTTPS URL.

Рекомендуемая схема:

```text
https://your-domain.com
        ↓
Nginx reverse proxy
        ↓
localhost:10000
```

Минимально нужно:

- домен или публичный IP;
- открытый порт `443`;
- HTTPS сертификат, например Let's Encrypt;
- reverse proxy на приложение;
- firewall, который разрешает внешний HTTPS-трафик.

В GitLab переменная `TELEGRAM_NOTIFY_URL` должна указывать именно на публичный адрес:

```text
TELEGRAM_NOTIFY_URL=https://your-domain.com
```

## Проверка вручную

После деплоя можно проверить endpoint без GitLab:

```bash
curl -X POST https://your-domain.com/api/telegram/gitlab/merge-request \
  -H "x-webhook-secret: your-secret" \
  -d "jobStatus=success" \
  --data-urlencode "jobName=telegram_notify_mr" \
  --data-urlencode "serviceName=mobile-client-service" \
  --data-urlencode "mergeRequestTitle=Исправление OCR проверки паспорта" \
  --data-urlencode "sourceBranch=feature/ocr-fix" \
  --data-urlencode "targetBranch=main" \
  --data-urlencode "author=Иван Иванов" \
  --data-urlencode "telegramUsers=@reviewer_username" \
  --data-urlencode "mergeRequestUrl=https://gitlab.com/group/project/-/merge_requests/1" \
  --data-urlencode "pipelineUrl=https://gitlab.com/group/project/-/pipelines/1"
```

Если все настроено правильно, бот отправит сообщение в Telegram.

## Критерии приемки

- Добавлена job для отправки Telegram-уведомления по MR.
- Уведомление отправляется только для Merge Request pipeline в `main` или `master`.
- В сообщении отображается название MR.
- В сообщении есть ссылка на MR.
- В сообщении есть ссылка на pipeline.
- В сообщении отображается автор.
- В сообщении отображаются source и target branch.
- В сообщении используется `${TELEGRAM_USERS}` для тега ревьювера.
- Ошибка отправки Telegram-сообщения не ломает pipeline.

## Частые проблемы

- `401 Invalid webhook secret` - `TELEGRAM_NOTIFY_SECRET` в GitLab не совпадает с `WEBHOOK_SECRET` на сервере.
- `Telegram credentials are not configured` - на сервере не заданы `TELEGRAM_BOT_TOKEN` или `TELEGRAM_CHAT_ID`.
- Сообщение не приходит - бот не добавлен в группу/канал или не имеет права отправлять сообщения.
- GitLab job не запускается - pipeline не является Merge Request pipeline.
- GitLab не может достучаться до сервера - проверь публичный URL, HTTPS, firewall и reverse proxy.
