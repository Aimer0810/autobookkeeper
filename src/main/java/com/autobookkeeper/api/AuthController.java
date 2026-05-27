package com.autobookkeeper.api;

import com.autobookkeeper.api.dto.AuthRequest;
import com.autobookkeeper.api.dto.AuthResponse;
import com.autobookkeeper.api.dto.RegisterRequest;
import com.autobookkeeper.user.AppUser;
import com.autobookkeeper.user.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
