package com.autobookkeeper.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "autobookkeeper")
public record AutoBookkeeperProperties(
        String apiToken,
        Ai ai,
        Privacy privacy
) {
    public record Ai(String provider, String apiKey, int timeoutMs, String endpoint) {
        public Ai(String provider, String apiKey, int timeoutMs) {
            this(provider, apiKey, timeoutMs, "https://api.openai.com/v1/chat/completions");
        }
    }

    public record Privacy(boolean persistOriginalImage, boolean redactLogs) {
    }
}
