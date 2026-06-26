package com.easyhome.controller;

import com.easyhome.api.vo.Result;
import com.easyhome.entity.SysUser;
import com.easyhome.security.JwtAuthenticationToken;
import com.easyhome.security.JwtBlacklistService;
import com.easyhome.security.JwtUtil;
import com.easyhome.security.LoginRequest;
import com.easyhome.security.LoginResponse;
import com.easyhome.service.EmailService;
import com.easyhome.service.SysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 管理端认证接口
 *
 * 使用账号密码登录，返回 JWT Token。
 * 此接口与 MCP 端的 API Key 认证完全隔离：
 *   - /api/auth/login  → 账号密码 → JWT → 管理端 /api/**
 *   - /mcp/**          → API Key   → ApiKeyAuthenticationToken → MCP 端
 */
@RestController
@RequestMapping("/api/auth")
@Slf4j
@RequiredArgsConstructor
public class AuthController {

    private final SysUserService userService;
    private final EmailService emailService;
    private final JwtUtil jwtUtil;
    private final JwtBlacklistService jwtBlacklistService;

    private static final int MAX_LOGIN_ATTEMPTS = 5;

    /**
     * 账号密码登录
     *
     * @param request 包含 username 和 password
     * @return JWT Token 和用户基本信息
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest request) {
        String username = request.getUsername();

        // 1. 检查账号是否被锁定
        if (userService.isLoginLocked(username)) {
            long minutes = userService.getLoginLockRemainingMinutes(username);
            return Result.fail(423, String.format("密码错误次数过多，账号已锁定，请 %d 分钟后再试", minutes));
        }

        // 2. 验证用户名密码
        var user = userService.authenticate(username, request.getPassword());
        if (user == null) {
            userService.recordLoginFailure(username);
            int failCount = userService.getLoginFailCount(username);
            int remaining = MAX_LOGIN_ATTEMPTS - failCount;
            if (remaining <= 0) {
                return Result.fail(423, "密码错误次数过多，账号已锁定 30 分钟");
            }
            return Result.fail(401, String.format("用户名或密码错误，还可尝试 %d 次", remaining));
        }

        // 3. 登录成功，清除错误计数
        userService.clearLoginFailures(username);

        // 查询用户角色
        var roles = userService.getRoles(user.getId());
        var roleCodes = roles.stream()
                .map(r -> r.getRoleCode())
                .toList();

        // 生成 JWT
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), roleCodes);

        return Result.ok(new LoginResponse(
                token,
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                roleCodes
        ));
    }

    /**
     * 获取当前登录用户信息
     *
     * @param authentication JWT 认证令牌
     * @return 当前用户的基本信息和角色
     */
    @GetMapping("/info")
    public Result<LoginResponse> info(@AuthenticationPrincipal JwtAuthenticationToken authentication) {
        if (authentication == null) {
            return Result.fail(401, "未登录");
        }
        return Result.ok(new LoginResponse(
                null,
                authentication.getUserId(),
                authentication.getUsername(),
                null,
                authentication.getAuthorities().stream()
                        .map(a -> a.getAuthority().replace("ROLE_", ""))
                        .toList()
        ));
    }

    /**
     * 退出登录
     * <p>
     * 将当前 JWT Token 加入黑名单，使其立即失效。
     * 前端调用此接口后，应同时清除本地存储的 Token。
     */
    @PostMapping("/logout")
    public Result<Void> logout(@AuthenticationPrincipal JwtAuthenticationToken authentication) {
        if (authentication != null && authentication.getToken() != null) {
            long remainingMs = jwtUtil.getTokenRemainingMs(authentication.getExpiration());
            jwtBlacklistService.blacklist(authentication.getToken(), remainingMs);
            log.info("用户 {} 退出登录，Token 已加入黑名单", authentication.getUsername());
        }
        return Result.ok();
    }

    /**
     * 忘记密码：发送验证码到用户注册邮箱
     *
     * @param body { "username": "xxx" }
     */
    @PostMapping("/send-reset-code")
    public Result<Void> sendResetCode(@RequestBody java.util.Map<String, String> body) {
        String username = body.get("username");
        if (username == null || username.isBlank()) {
            return Result.fail(400, "请输入用户名");
        }
        SysUser user = userService.findByUsername(username);
        if (user == null) {
            // 不暴露用户名是否存在，统一返回成功
            return Result.ok();
        }
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            return Result.fail(400, "该用户未绑定邮箱，请联系管理员重置密码");
        }
        try {
            emailService.sendResetCode(username, user.getEmail());
        } catch (IllegalStateException e) {
            return Result.fail(429, e.getMessage());
        } catch (RuntimeException e) {
            return Result.fail(500, e.getMessage());
        }
        return Result.ok();
    }

    /**
     * 忘记密码：验证码通过后重置密码
     *
     * @param body { "username": "xxx", "code": "123456", "newPassword": "xxx" }
     */
    @PostMapping("/reset-password")
    public Result<Void> resetPassword(@RequestBody java.util.Map<String, String> body) {
        String username = body.get("username");
        String code = body.get("code");
        String newPassword = body.get("newPassword");
        if (username == null || username.isBlank() || code == null || newPassword == null) {
            return Result.fail(400, "参数不完整");
        }
        if (newPassword.length() < 8) {
            return Result.fail(400, "新密码长度不能少于 8 位");
        }
        if (!emailService.verifyResetCode(username, code)) {
            return Result.fail(400, "验证码错误或已过期");
        }
        try {
            userService.resetPasswordByUsername(username, newPassword);
        } catch (IllegalArgumentException e) {
            return Result.fail(400, e.getMessage());
        }
        emailService.consumeResetCode(username);
        return Result.ok();
    }
}
