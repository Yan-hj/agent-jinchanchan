package com.game.agent.api.controller;

import com.game.agent.api.model.auth.LoginRequest;
import com.game.agent.api.model.auth.LoginResponse;
import com.game.agent.api.model.auth.UserInfo;
import com.game.agent.api.security.JwtTokenProvider;
import com.game.agent.api.security.PermissionConfig;
import com.game.agent.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final Map<String, String> USERS = Map.of(
            "admin", "admin123",
            "analyst", "analyst123",
            "basic", "basic123"
    );

    private static final Map<String, String> USER_ROLES = Map.of(
            "admin", "ADMIN",
            "analyst", "ANALYST",
            "basic", "BASIC"
    );

    private final JwtTokenProvider tokenProvider;
    private final PermissionConfig permissionConfig;

    public AuthController(JwtTokenProvider tokenProvider, PermissionConfig permissionConfig) {
        this.tokenProvider = tokenProvider;
        this.permissionConfig = permissionConfig;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        String password = USERS.get(request.username());
        if (password == null || !password.equals(request.password())) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error("G0001", "用户名或密码错误"));
        }

        String role = USER_ROLES.get(request.username());
        String token = tokenProvider.generateToken(request.username(), role);
        Set<String> allowedSources = permissionConfig.getAllowedSources(role);

        return ResponseEntity.ok(ApiResponse.success(
                new LoginResponse(token, request.username(), role, allowedSources)));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserInfo>> me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error("G0001", "未认证"));
        }

        String userId = auth.getName();
        String role = (String) auth.getDetails();
        Set<String> allowedSources = permissionConfig.getAllowedSources(role);

        return ResponseEntity.ok(ApiResponse.success(new UserInfo(userId, role, allowedSources)));
    }
}
