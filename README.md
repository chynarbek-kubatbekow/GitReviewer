# Telegram JSON notifier

Java/Spring Boot server for Render. It accepts webhook data and sends Telegram messages.

## Render environment

Set these variables in Render:

```text
TELEGRAM_BOT_TOKEN=your_bot_token
TELEGRAM_CHAT_ID=-1003894821178
WEBHOOK_SECRET=your-secret
```

`WEBHOOK_SECRET` is optional, but recommended. Requests must then include:

```text
x-webhook-secret: your-secret
```

## Endpoints

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

For UptimeRobot, use one of these URLs:

```text
https://your-service.onrender.com/
https://your-service.onrender.com/health
https://your-service.onrender.com/uptime
```

`HEAD` requests are accepted and return an empty `204 No Content` response.

Generic JSON notification:

```text
POST /api/telegram/notify
Content-Type: application/json
```

GitLab Merge Request notification:

```text
POST /api/telegram/gitlab/merge-request
Content-Type: application/x-www-form-urlencoded
```

The MR endpoint also accepts JSON.

## GitLab CI job

Use `.gitlab-ci.telegram-notify.yml` as a ready template. In the target GitLab project, either copy the job into `.gitlab-ci.yml` or include it:

```yaml
include:
  - local: .gitlab-ci.telegram-notify.yml
```

Make sure your pipeline has a `notify` stage:

```yaml
stages:
  - build
  - test
  - deploy
  - notify
```

Add these GitLab CI/CD variables:

```text
TELEGRAM_NOTIFY_URL=https://your-service.onrender.com
TELEGRAM_NOTIFY_SECRET=your-secret
TELEGRAM_USERS=@reviewer_username
SERVICE_NAME=mobile-client-service
```

`SERVICE_NAME` is optional. If it is empty, the job uses `CI_PROJECT_NAME`.

The job runs only for Merge Request pipelines:

```yaml
rules:
  - if: '$CI_PIPELINE_SOURCE == "merge_request_event"'
```

Telegram send errors do not break the pipeline because the job has `allow_failure: true` and the `curl` command ends with `|| true`.

## Expected Telegram message

```text
SUCCESS - notify

Service: mobile-client-service
MR: Fix OCR passport validation
Branch: feature/ocr-fix -> develop
Author: Ivan Ivanov
Reviewer: @reviewer_username

Merge Request
Pipeline
```

The actual message uses Telegram HTML formatting with bold text, code formatting, emoji labels, and clickable links.

## Deploy on Render

Recommended: create a Render Blueprint from `render.yaml`.

Manual Web Service setup:

```text
Runtime: Docker
Build Command: empty
Start Command: empty
Health Check Path: /actuator/health
```

If Docker is not available, try native commands:

```bash
mvn clean package -DskipTests
java -jar target/telegram-json-notifier-0.1.0.jar
```
