package com.gitreqiever.telegramnotifier.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "telegram")
public record TelegramProperties(
        String botToken,
        String chatId,
        String webhookSecret
) {
    public boolean hasCredentials() {
        boolean hasBotToken = botToken != null && !botToken.isBlank();
        boolean hasChatId = chatId != null && !chatId.isBlank();

        return hasBotToken && hasChatId;
    }

    public boolean hasWebhookSecret() {
        return webhookSecret != null && !webhookSecret.isBlank();
    }
}
