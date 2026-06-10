package com.autobookkeeper.security;

import com.autobookkeeper.config.AutoBookkeeperProperties;
import com.autobookkeeper.user.AppUserRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Optional;

@Component
public class UserTokenResolver {

    public static final String DEFAULT_OWNER_KEY = "default";

    private final AutoBookkeeperProperties properties;
    private final AppUserRepository appUserRepository;

    public UserTokenResolver(AutoBookkeeperProperties properties, AppUserRepository appUserRepository) {
        this.properties = properties;
        this.appUserRepository = appUserRepository;
    }

    public Optional<AuthenticatedUser> resolve(String providedToken) {
        if (providedToken == null || providedToken.isBlank()) {
            return Optional.empty();
        }
        Optional<AuthenticatedUser> user = resolveDatabaseUserToken(providedToken);
        if (user.isPresent()) {
            return user;
        }
        user = resolveConfiguredUserToken(providedToken);
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
        return appUserRepository.count() > 0 || StringUtils.hasText(properties.apiToken()) || StringUtils.hasText(properties.userTokens());
    }

    private Optional<AuthenticatedUser> resolveDatabaseUserToken(String providedToken) {
        return appUserRepository.findByApiToken(providedToken)
                .map(user -> new AuthenticatedUser(user.getOwnerKey()));
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
}
