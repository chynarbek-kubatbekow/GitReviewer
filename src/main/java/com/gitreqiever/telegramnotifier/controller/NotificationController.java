package com.gitreqiever.telegramnotifier.controller;

import com.gitreqiever.telegramnotifier.config.TelegramProperties;
import com.gitreqiever.telegramnotifier.service.TelegramService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/telegram")
public class NotificationController {

    private final TelegramService telegramService;
    private final TelegramProperties properties;

    public NotificationController(TelegramService telegramService, TelegramProperties properties) {
        this.telegramService = telegramService;
        this.properties = properties;
    }

    @GetMapping("/ping")
    public Map<String, Object> ping() {
        return Map.of("ok", true);
    }

    @PostMapping(
            value = "/notify",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Map<String, Object>> notifyTelegram(
            @RequestHeader(value = "x-webhook-secret", required = false) String webhookSecret,
            @RequestBody Map<String, Object> payload
    ) {
        Optional<ResponseEntity<Map<String, Object>>> validationError = validateRequest(webhookSecret);
        if (validationError.isPresent()) {
            return validationError.get();
        }

        telegramService.sendJson(payload);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping(
            value = "/gitlab/merge-request",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Map<String, Object>> notifyGitlabMergeRequest(
            @RequestHeader(value = "x-webhook-secret", required = false) String webhookSecret,
            @RequestBody Map<String, Object> payload
    ) {
        return sendGitlabMergeRequest(webhookSecret, payload);
    }

    @PostMapping(
            value = "/gitlab/merge-request",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Map<String, Object>> notifyGitlabMergeRequestForm(
            @RequestHeader(value = "x-webhook-secret", required = false) String webhookSecret,
            @RequestParam Map<String, String> payload
    ) {
        return sendGitlabMergeRequest(webhookSecret, asObjectMap(payload));
    }

    private ResponseEntity<Map<String, Object>> sendGitlabMergeRequest(
            String webhookSecret,
            Map<String, Object> payload
    ) {
        Optional<ResponseEntity<Map<String, Object>>> validationError = validateRequest(webhookSecret);
        if (validationError.isPresent()) {
            return validationError.get();
        }

        telegramService.sendGitlabMergeRequest(payload);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    private Optional<ResponseEntity<Map<String, Object>>> validateRequest(String webhookSecret) {
        if (hasInvalidWebhookSecret(webhookSecret)) {
            return Optional.of(error(HttpStatus.UNAUTHORIZED, "Invalid webhook secret"));
        }

        if (!properties.hasCredentials()) {
            return Optional.of(error(HttpStatus.INTERNAL_SERVER_ERROR, "Telegram credentials are not configured"));
        }

        return Optional.empty();
    }

    private boolean hasInvalidWebhookSecret(String webhookSecret) {
        return properties.hasWebhookSecret() && !properties.webhookSecret().equals(webhookSecret);
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(Map.of(
                        "ok", false,
                        "error", message
                ));
    }

    private Map<String, Object> asObjectMap(Map<String, String> payload) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.putAll(payload);
        return result;
    }
}
