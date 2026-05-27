package com.autobookkeeper.user;

import com.autobookkeeper.config.AutoBookkeeperProperties;
import com.autobookkeeper.repository.TransactionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;

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
        String configuredInviteCode = properties.inviteCode();
        if (configuredInviteCode == null || configuredInviteCode.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Invite code is not configured");
        }
        if (!configuredInviteCode.equals(inviteCode)) {
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

    private String normalizeUsername(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
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
