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

    public HealthController(Environment environment, @Value("${autobookkeeper.version:0.1.0}") String version) {
        this.environment = environment;
        this.version = version;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        List<String> profiles = Arrays.asList(environment.getActiveProfiles());
        return Map.of(
                "status", "UP",
                "version", version,
                "profiles", profiles
        );
    }
}
