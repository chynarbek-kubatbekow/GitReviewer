# Telegram MR Notifier

Сервис принимает данные из GitLab CI/CD и отправляет уведомления в Telegram-группу при создании или обновлении Merge Request.

## Что делает сервис

- принимает HTTP-запрос от GitLab CI job;
- форматирует сообщение для Telegram;
- отправляет сообщение в Telegram-группу через бота;
- добавляет в сообщение MR, ветки, автора, ревьювера, commit и pipeline;
- принимает `HEAD` и `GET` запросы от UptimeRobot;
- хранит токены только в переменных окружения.

## Схема работы

```text
GitLab Merge Request
        ↓
GitLab CI job telegram_notify_mr
        ↓
Render server
        ↓
Telegram group
```

GitLab Webhook для этой схемы не нужен. Уведомление отправляет CI job из `.gitlab-ci.yml`.

## Переменные Render

В Render открой сервис, перейди в `Environment` и добавь:

```text
TELEGRAM_BOT_TOKEN=токен_бота
TELEGRAM_CHAT_ID=-1003894821178
WEBHOOK_SECRET=любой_секрет
```

`TELEGRAM_BOT_TOKEN` берется у BotFather.

`TELEGRAM_CHAT_ID` — id Telegram-группы.

`WEBHOOK_SECRET` защищает сервер от посторонних запросов.

Не добавляй реальные токены в `.env.example`, README или исходный код. Реальные значения должны быть только в Render Environment и GitLab CI/CD Variables.

## Деплой на Render

### Через Blueprint

1. Загрузи этот проект в GitHub или GitLab.
2. В Render нажми `New`.
3. Выбери `Blueprint`.
4. Укажи репозиторий с этим проектом.
5. Render прочитает `render.yaml`.
6. После создания сервиса добавь переменные окружения из раздела выше.

### Через Web Service

Если создаешь сервис вручную:

```text
Runtime: Docker
Build Command: оставить пустым
Start Command: оставить пустым
Health Check Path: /actuator/health
```

Если Docker выбрать нельзя:

```text
Build Command: mvn clean package -DskipTests
Start Command: java -jar target/telegram-json-notifier-0.1.0.jar
```

## Проверка сервера

После деплоя открой:

```text
https://your-service.onrender.com/health
```

Ожидаемый ответ:

```json
{
  "ok": true
}
```

Для UptimeRobot используй:

```text
https://your-service.onrender.com/uptime
```

Можно использовать `HEAD`. Если сервис мониторинга отправляет только `GET`, это тоже работает.

## Endpoint для GitLab

GitLab CI job отправляет данные сюда:

```text
POST https://your-service.onrender.com/api/telegram/gitlab/merge-request
```

Header:

```text
x-webhook-secret: WEBHOOK_SECRET
```

Сервер принимает `application/x-www-form-urlencoded` и JSON.

## Настройка GitLab проекта

Открой проект, где создаются Merge Request:

```text
Settings → CI/CD → Variables
```

Добавь переменные:

```text
TELEGRAM_NOTIFY_URL=https://your-service.onrender.com
TELEGRAM_NOTIFY_SECRET=тот_же_секрет_что_WEBHOOK_SECRET_на_Render
TELEGRAM_USERS=@reviewer_username
```

Необязательно:

```text
SERVICE_NAME=mobile-client-service
```

Если `SERVICE_NAME` не задан, будет использован `CI_PROJECT_NAME`.

Для `TELEGRAM_NOTIFY_SECRET` можно выбрать `Masked`. `Protected` лучше не включать, если pipeline запускается из обычных feature-веток.

## Файл .gitlab-ci.yml

В корне GitLab-проекта создай или обнови:

```text
.gitlab-ci.yml
```

Минимальная конфигурация:

```yaml
stages:
  - notify

telegram_notify_mr:
  stage: notify
  image:
    name: curlimages/curl:8.8.0
    entrypoint: [""]
  allow_failure: true
  script:
    - |
      case "$CI_JOB_STATUS" in
        failed) STATUS="failed" ;;
        *) STATUS="success" ;;
      esac

      curl -sS -X POST "$TELEGRAM_NOTIFY_URL/api/telegram/gitlab/merge-request" \
        -H "x-webhook-secret: $TELEGRAM_NOTIFY_SECRET" \
        -d "jobStatus=$STATUS" \
        --data-urlencode "jobName=$CI_JOB_NAME" \
        --data-urlencode "serviceName=${SERVICE_NAME:-$CI_PROJECT_NAME}" \
        --data-urlencode "mergeRequestTitle=$CI_MERGE_REQUEST_TITLE" \
        --data-urlencode "sourceBranch=$CI_MERGE_REQUEST_SOURCE_BRANCH_NAME" \
        --data-urlencode "targetBranch=$CI_MERGE_REQUEST_TARGET_BRANCH_NAME" \
        --data-urlencode "author=$GITLAB_USER_NAME" \
        --data-urlencode "telegramUsers=$TELEGRAM_USERS" \
        --data-urlencode "mergeRequestUrl=$CI_MERGE_REQUEST_PROJECT_URL/-/merge_requests/$CI_MERGE_REQUEST_IID" \
        --data-urlencode "commitTitle=$CI_COMMIT_TITLE" \
        --data-urlencode "commitShortSha=$CI_COMMIT_SHORT_SHA" \
        --data-urlencode "commitUrl=$CI_PROJECT_URL/-/commit/$CI_COMMIT_SHA" \
        --data-urlencode "pipelineUrl=$CI_PIPELINE_URL" || true
  rules:
    - if: '$CI_PIPELINE_SOURCE == "merge_request_event"'
```

Если `stages` уже есть, добавь туда `notify`. Если в файле уже есть другие jobs, вставь `telegram_notify_mr` в конец.

## Сообщение в Telegram

Пример:

```text
✅ SUCCESS — telegram_notify_mr

📦 Сервис: BALAM-3
🔀 MR: Draft: Feature/my change
🌿 Ветка: feature/my-change → main
👤 Автор: chynarbek-kubatbekow
👀 Ревьювер: @reviewer

🧩 Commit: d59a1d4
📝 Описание: Update notification config

🔗 Merge Request
🔗 Пайплайн
```

`Merge Request` открывает MR.

`Пайплайн` открывает конкретный pipeline.

`Commit` открывает commit, который запустил pipeline.

## Проверка вручную

```bash
curl -X POST https://your-service.onrender.com/api/telegram/gitlab/merge-request \
  -H "x-webhook-secret: your-secret" \
  -d "jobStatus=success" \
  --data-urlencode "jobName=telegram_notify_mr" \
  --data-urlencode "serviceName=test-service" \
  --data-urlencode "mergeRequestTitle=Test MR" \
  --data-urlencode "sourceBranch=feature/test" \
  --data-urlencode "targetBranch=main" \
  --data-urlencode "author=developer" \
  --data-urlencode "telegramUsers=@reviewer" \
  --data-urlencode "mergeRequestUrl=https://gitlab.com/group/project/-/merge_requests/1" \
  --data-urlencode "commitTitle=Test commit message" \
  --data-urlencode "commitShortSha=abc1234" \
  --data-urlencode "commitUrl=https://gitlab.com/group/project/-/commit/abc1234" \
  --data-urlencode "pipelineUrl=https://gitlab.com/group/project/-/pipelines/1"
```

## Команды для сервера

```powershell
cd C:\Users\User\Desktop\GitReqiever
git status
git add .
git commit -m "Update Telegram notifier setup guide"
git push
```

## Команды для GitLab-проекта

```powershell
cd C:\Users\User\Desktop\BALAM
git status
git add .gitlab-ci.yml
git commit -m "Update Telegram MR notification job"
git push gitlab feature/my-change
```

## Частые проблемы

Если pipeline не появился, проверь права пользователя в GitLab.

Если Telegram-сообщение не пришло, проверь `TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHAT_ID` и логи Render.

Если GitLab job показывает `401`, значит `TELEGRAM_NOTIFY_SECRET` не совпадает с `WEBHOOK_SECRET`.

Если ревьювер не отображается, проверь `TELEGRAM_USERS`.

Если Render долго отвечает первым запросом, добавь UptimeRobot на `/uptime`.
