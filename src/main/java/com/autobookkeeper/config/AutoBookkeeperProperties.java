package com.autobookkeeper.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "autobookkeeper")
public record AutoBookkeeperProperties(
        String apiToken,
        String userTokens,
        String inviteCode,
        String inviteCodes,
        Ai ai,
        Privacy privacy
) {
    @ConstructorBinding
    public AutoBookkeeperProperties {
    }

    public AutoBookkeeperProperties(String apiToken, Ai ai, Privacy privacy) {
        this(apiToken, "", "", "", ai, privacy);
    }

    public record Ai(String provider, String apiKey, int timeoutMs, String endpoint, String model) {
        public Ai(String provider, String apiKey, int timeoutMs) {
            this(provider, apiKey, timeoutMs, "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions", "qwen3.6-flash");
        }

        public Ai(String provider, String apiKey, int timeoutMs, String endpoint) {
            this(provider, apiKey, timeoutMs, endpoint, "qwen3.6-flash");
        }
    }

    public record Privacy(boolean persistOriginalImage, boolean redactLogs) {
    }
}
