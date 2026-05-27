package com.autobookkeeper.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    private final Environment environment;
    private final String version;
    private final String commit;
    private final String buildTime;

    public HealthController(Environment environment,
                            @Value("${autobookkeeper.version:0.1.0}") String version,
                            @Value("${autobookkeeper.build.commit:${GIT_COMMIT:unknown}}") String commit,
                            @Value("${autobookkeeper.build.time:${BUILD_TIME:unknown}}") String buildTime) {
        this.environment = environment;
        this.version = version;
        this.commit = commit;
        this.buildTime = buildTime;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        List<String> profiles = Arrays.asList(environment.getActiveProfiles());
        return Map.of(
                "status", "UP",
                "version", version,
                "commit", commit,
                "buildTime", buildTime,
                "profiles", profiles,
                "ai", aiHealth()
        );
    }

    private Map<String, Object> aiHealth() {
        String apiKey = environment.getProperty("autobookkeeper.ai.api-key");
        return Map.of(
                "apiKeyConfigured", apiKey != null && !apiKey.isBlank() && !"{{API_KEY}}".equals(apiKey),
                "endpoint", environment.getProperty("autobookkeeper.ai.endpoint", ""),
                "model", environment.getProperty("autobookkeeper.ai.model", ""),
                "timeoutMs", environment.getProperty("autobookkeeper.ai.timeout-ms", Integer.class, 0)
        );
    }
}
