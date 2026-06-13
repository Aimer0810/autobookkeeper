package com.autobookkeeper.user;

import com.autobookkeeper.config.AutoBookkeeperProperties;
import com.autobookkeeper.repository.TransactionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import java.util.stream.Stream;

@Service
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final TransactionRepository transactionRepository;
    private final AutoBookkeeperProperties properties;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(AppUserRepository appUserRepository, TransactionRepository transactionRepository, AutoBookkeeperProperties properties) {
        this.appUserRepository = appUserRepository;
        this.transactionRepository = transactionRepository;
        this.properties = properties;
    }

    public AppUser register(String username, String password, String inviteCode) {
        if (!hasConfiguredInviteCodes()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Invite code is not configured");
        }
        if (!isValidInviteCode(inviteCode)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid invite code");
        }
        String normalizedUsername = normalizeUsername(username);
        if (appUserRepository.existsByUsername(normalizedUsername)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }
        AppUser user = new AppUser(
                normalizedUsername,
                passwordEncoder.encode(password),
                uniqueOwnerKey(),
                uniqueToken(),
                Instant.now()
        );
        return appUserRepository.save(user);
    }

    public AppUser login(String username, String password) {
        String normalizedUsername = normalizeUsername(username);
        AppUser user = appUserRepository.findByUsername(normalizedUsername)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        return user;
    }

    @Transactional
    public int migrateLegacyTransactions(String currentApiToken, String legacyToken) {
        AppUser user = appUserRepository.findByApiToken(currentApiToken)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Registered account token is required"));
        String configuredLegacyToken = properties.apiToken();
        if (configuredLegacyToken == null || configuredLegacyToken.isBlank() || !configuredLegacyToken.equals(legacyToken)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid legacy token");
        }
        return transactionRepository.migrateLegacyTransactionsToOwner(user.getOwnerKey());
    }

    public void changePassword(String apiToken, String currentPassword, String newPassword) {
        AppUser user = appUserRepository.findByApiToken(apiToken)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token"));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        appUserRepository.save(user);
    }

    public String regenerateToken(String apiToken) {
        AppUser user = appUserRepository.findByApiToken(apiToken)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token"));
        user.setApiToken(uniqueToken());
        appUserRepository.save(user);
        return user.getApiToken();
    }

    private String normalizeUsername(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private boolean hasConfiguredInviteCodes() {
        return StringUtils.hasText(properties.inviteCode()) || StringUtils.hasText(properties.inviteCodes());
    }

    private boolean isValidInviteCode(String inviteCode) {
        if (inviteCode == null || inviteCode.isBlank()) {
            return false;
        }
        return configuredInviteCodes().anyMatch(inviteCode::equals);
    }

    private Stream<String> configuredInviteCodes() {
        Stream<String> singleCode = StringUtils.hasText(properties.inviteCode()) ? Stream.of(properties.inviteCode()) : Stream.empty();
        Stream<String> multipleCodes = StringUtils.hasText(properties.inviteCodes())
                ? Arrays.stream(properties.inviteCodes().split(","))
                : Stream.empty();
        return Stream.concat(singleCode, multipleCodes)
                .map(String::trim)
                .filter(StringUtils::hasText);
    }

    private String uniqueOwnerKey() {
        String ownerKey;
        do {
            ownerKey = "user_" + randomToken(18);
        } while (appUserRepository.existsByOwnerKey(ownerKey));
        return ownerKey;
    }

    private String uniqueToken() {
        String token;
        do {
            token = "ak_" + randomToken(32);
        } while (appUserRepository.existsByApiToken(token));
        return token;
    }

    private String randomToken(int byteLength) {
        byte[] bytes = new byte[byteLength];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
