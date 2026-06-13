package com.autobookkeeper.api;

import com.autobookkeeper.api.dto.AuthRequest;
import com.autobookkeeper.api.dto.AuthResponse;
import com.autobookkeeper.api.dto.ChangePasswordRequest;
import com.autobookkeeper.api.dto.LegacyMigrationRequest;
import com.autobookkeeper.api.dto.LegacyMigrationResponse;
import com.autobookkeeper.api.dto.ProfileResponse;
import com.autobookkeeper.api.dto.RegisterRequest;
import com.autobookkeeper.user.AppUser;
import com.autobookkeeper.user.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        AppUser user = authService.register(request.username(), request.password(), request.inviteCode());
        return new AuthResponse(user.getUsername(), user.getApiToken());
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody AuthRequest request) {
        AppUser user = authService.login(request.username(), request.password());
        return new AuthResponse(user.getUsername(), user.getApiToken());
    }

    @PostMapping("/migrate-legacy")
    public LegacyMigrationResponse migrateLegacy(@Valid @RequestBody LegacyMigrationRequest request, HttpServletRequest httpRequest) {
        int migratedCount = authService.migrateLegacyTransactions(httpRequest.getHeader("X-API-Token"), request.legacyToken());
        return new LegacyMigrationResponse(migratedCount);
    }

    @PostMapping("/change-password")
    public Map<String, String> changePassword(@Valid @RequestBody ChangePasswordRequest request, HttpServletRequest httpRequest) {
        authService.changePassword(httpRequest.getHeader("X-API-Token"), request.currentPassword(), request.newPassword());
        return Map.of("status", "ok");
    }

    @GetMapping("/profile")
    public ProfileResponse profile(HttpServletRequest httpRequest) {
        AppUser user = authService.getProfile(httpRequest.getHeader("X-API-Token"));
        return new ProfileResponse(user.getUsername(), user.getOwnerKey(), user.getApiToken(), user.getAvatarData(), user.getCreatedAt());
    }

    @PostMapping("/avatar")
    public Map<String, String> uploadAvatar(@RequestBody Map<String, String> body, HttpServletRequest httpRequest) {
        String avatarData = body.getOrDefault("avatarData", "");
        authService.updateAvatar(httpRequest.getHeader("X-API-Token"), avatarData);
        return Map.of("status", "ok");
    }
}
