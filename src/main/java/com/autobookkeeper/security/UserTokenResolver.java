package com.autobookkeeper.security;

import com.autobookkeeper.config.AutoBookkeeperProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Optional;

@Component
public class UserTokenResolver {

    public static final String DEFAULT_OWNER_KEY = "default";

    private final AutoBookkeeperProperties properties;

    public UserTokenResolver(AutoBookkeeperProperties properties) {
        this.properties = properties;
    }

    public Optional<AuthenticatedUser> resolve(String providedToken) {
        if (providedToken == null || providedToken.isBlank()) {
            return Optional.empty();
        }
        Optional<AuthenticatedUser> user = resolveConfiguredUserToken(providedToken);
        if (user.isPresent()) {
            return user;
        }
        String apiToken = properties.apiToken();
        if (apiToken != null && !apiToken.isBlank() && apiToken.equals(providedToken)) {
            return Optional.of(new AuthenticatedUser(DEFAULT_OWNER_KEY));
        }
        return Optional.empty();
    }

    public boolean hasConfiguredTokens() {
        return hasText(properties.apiToken()) || hasText(properties.userTokens());
    }

    private Optional<AuthenticatedUser> resolveConfiguredUserToken(String providedToken) {
        String userTokens = properties.userTokens();
        if (userTokens == null || userTokens.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(userTokens.split(","))
                .map(String::trim)
                .filter(entry -> !entry.isBlank())
                .map(entry -> entry.split(":", 2))
                .filter(parts -> parts.length == 2)
                .filter(parts -> !parts[0].isBlank() && parts[1].equals(providedToken))
                .map(parts -> new AuthenticatedUser(parts[0].trim()))
                .findFirst();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
