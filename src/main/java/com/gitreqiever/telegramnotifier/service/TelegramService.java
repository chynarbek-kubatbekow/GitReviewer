package com.gitreqiever.telegramnotifier.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gitreqiever.telegramnotifier.config.TelegramProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class TelegramService {

    private static final int TELEGRAM_MESSAGE_LIMIT = 3500;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final TelegramProperties properties;

    public TelegramService(RestClient restClient, ObjectMapper objectMapper, TelegramProperties properties) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public void sendJson(Map<String, Object> payload) {
        String title = resolveTitle(payload);
        String escapedJson = escapeHtml(toPrettyJson(payload));

        List<String> chunks = splitMessage(escapedJson);
        for (int index = 0; index < chunks.size(); index++) {
            sendMessage(formatMessage(title, chunks.get(index), index + 1, chunks.size()));
        }
    }

    public void sendGitlabMergeRequest(Map<String, Object> payload) {
        sendMessage(formatGitlabMergeRequestMessage(payload));
    }

    private String resolveTitle(Map<String, Object> payload) {
        return payload.get("title") instanceof String value && !value.isBlank()
                ? value
                : "New JSON notification";
    }

    private String formatMessage(String title, String escapedJson, int part, int totalParts) {
        String suffix = totalParts > 1 ? " (%d/%d)".formatted(part, totalParts) : "";
        return "<b>%s%s</b>%n%n<pre>%s</pre>".formatted(escapeHtml(title), suffix, escapedJson);
    }

    private String formatGitlabMergeRequestMessage(Map<String, Object> payload) {
        String jobStatus = stringValue(payload, "jobStatus", "success");
        String status = "success".equalsIgnoreCase(jobStatus) ? "✅ SUCCESS" : "❌ FAILED";
        String jobName = stringValue(payload, "jobName", "notify");
        String serviceName = stringValue(payload, "serviceName", "unknown-service");
        String mrTitle = stringValue(payload, "mergeRequestTitle", "Merge Request");
        String sourceBranch = stringValue(payload, "sourceBranch", "-");
        String targetBranch = stringValue(payload, "targetBranch", "-");
        String author = stringValue(payload, "author", "-");
        String reviewers = stringValue(payload, "telegramUsers", "-");
        String mergeRequestUrl = stringValue(payload, "mergeRequestUrl", "");
        String pipelineUrl = stringValue(payload, "pipelineUrl", "");

        return """
                %s — <b>%s</b>

                📦 Сервис: <b>%s</b>
                🔀 MR: <b>%s</b>
                🌿 Ветка: <code>%s</code> → <code>%s</code>
                👤 Автор: %s
                👀 Ревьювер: %s

                🔗 <a href="%s">Merge Request</a>
                🔗 <a href="%s">Пайплайн</a>""".formatted(
                status,
                escapeHtml(jobName),
                escapeHtml(serviceName),
                escapeHtml(mrTitle),
                escapeHtml(sourceBranch),
                escapeHtml(targetBranch),
                escapeHtml(author),
                escapeHtml(reviewers),
                escapeHtml(mergeRequestUrl),
                escapeHtml(pipelineUrl)
        );
    }

    private String toPrettyJson(Map<String, Object> payload) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid JSON payload", exception);
        }
    }

    private void sendMessage(String text) {
        String url = "https://api.telegram.org/bot%s/sendMessage".formatted(properties.botToken());

        restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "chat_id", properties.chatId(),
                        "text", text,
                        "parse_mode", "HTML",
                        "disable_web_page_preview", true
                ))
                .retrieve()
                .toBodilessEntity();
    }

    private List<String> splitMessage(String message) {
        if (message.length() <= TELEGRAM_MESSAGE_LIMIT) {
            return List.of(message);
        }

        List<String> chunks = new ArrayList<>();
        for (int index = 0; index < message.length(); index += TELEGRAM_MESSAGE_LIMIT) {
            chunks.add(message.substring(index, Math.min(index + TELEGRAM_MESSAGE_LIMIT, message.length())));
        }
        return chunks;
    }

    private String stringValue(Map<String, Object> payload, String key, String fallback) {
        Object value = payload.get(key);
        return value instanceof String string && !string.isBlank() ? string : fallback;
    }

    private String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
