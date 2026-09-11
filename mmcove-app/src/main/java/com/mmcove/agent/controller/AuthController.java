package com.mmcove.agent.controller;

import com.mmcove.agent.common.context.UserSessionContext;
import com.mmcove.agent.common.model.dto.ApiResponse;
import com.mmcove.agent.common.model.entity.ApiUser;
import com.mmcove.agent.service.JwtService;
import com.mmcove.agent.service.UserService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 自建用户体系认证接口（本地 JWT）。
 * 前端聊天端 / 后台端统一走此接口登录、刷新、登出、获取当前用户。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final JwtService jwtService;

    public record RegisterRequest(String username, String password) {}
    public record LoginRequest(String username, String password) {}
    public record RefreshRequest(String refreshToken) {}
    public record UserInfo(String id, String username, Integer role) {}
    public record AuthResponse(String accessToken, String refreshToken, UserInfo user) {}

    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@RequestBody RegisterRequest req) {
        ApiUser user = userService.register(req.username(), req.password());
        return ApiResponse.success(issue(user));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@RequestBody LoginRequest req) {
        ApiUser user = userService.login(req.username(), req.password());
        return ApiResponse.success(issue(user));
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(@RequestBody RefreshRequest req) {
        if (req.refreshToken() == null || req.refreshToken().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "refreshToken 不能为空");
        }
        Claims claims;
        try {
            claims = jwtService.parseAndVerify(req.refreshToken());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "refresh token 无效或已过期");
        }
        if (!"refresh".equals(claims.get("type"))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "token 类型错误");
        }
        ApiUser user = userService.getById(claims.getSubject());
        if (user == null || !user.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户不可用");
        }
        return ApiResponse.success(issue(user));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        // 无状态 JWT：客户端清除本地 token 即完成登出
        return ApiResponse.success();
    }

    @GetMapping("/me")
    public ApiResponse<UserInfo> me() {
        String userId = UserSessionContext.get().getUserId();
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录");
        }
        ApiUser user = userService.getById(userId);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户不存在");
        }
        return ApiResponse.success(new UserInfo(user.getId(), user.getUsername(), user.getRole()));
    }

    private AuthResponse issue(ApiUser user) {
        String access = jwtService.generateAccessToken(user.getId(), user.getUsername(), user.getRole());
        String refresh = jwtService.generateRefreshToken(user.getId(), user.getUsername(), user.getRole());
        return new AuthResponse(access, refresh, new UserInfo(user.getId(), user.getUsername(), user.getRole()));
    }
}
