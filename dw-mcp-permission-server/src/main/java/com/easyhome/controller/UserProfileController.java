package com.easyhome.controller;

import com.easyhome.api.dto.ChangePasswordDTO;
import com.easyhome.api.dto.UpdateProfileDTO;
import com.easyhome.api.vo.Result;
import com.easyhome.api.vo.SysUserVO;
import com.easyhome.entity.SysUser;
import com.easyhome.security.JwtAuthenticationToken;
import com.easyhome.security.JwtBlacklistService;
import com.easyhome.security.JwtUtil;
import com.easyhome.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 用户自助操作（任意已认证用户）
 * <p>
 * 独立于 SysUserController，避免被类级别 @PreAuthorize("hasRole('ADMIN')") 拦截。
 */
@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class UserProfileController {

    private final SysUserService userService;
    private final JwtUtil jwtUtil;
    private final JwtBlacklistService jwtBlacklistService;

    @GetMapping
    public Result<SysUserVO> getMyProfile() {
        Long userId = getCurrentUserId();
        SysUser user = userService.getById(userId);
        return user == null ? Result.fail(404, "用户不存在") : Result.ok(toVO(user));
    }

    @PutMapping("/profile")
    public Result<SysUserVO> updateProfile(@RequestBody @Validated UpdateProfileDTO dto) {
        Long userId = getCurrentUserId();
        SysUser updated = userService.updateProfile(userId, dto.getNickname(), dto.getEmail(), dto.getRemark());
        return Result.ok(toVO(updated));
    }

    @PutMapping("/password")
    public Result<Void> changePassword(@RequestBody @Validated ChangePasswordDTO dto) {
        Long userId = getCurrentUserId();
        userService.changePassword(userId, dto.getOldPassword(), dto.getNewPassword());
        // 修改密码后，将当前 Token 加入黑名单，强制重新登录
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth && jwtAuth.getToken() != null) {
            long remainingMs = jwtUtil.getTokenRemainingMs(jwtAuth.getExpiration());
            jwtBlacklistService.blacklist(jwtAuth.getToken(), remainingMs);
        }
        return Result.ok();
    }

    private Long getCurrentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getUserId();
        }
        return null;
    }

    private SysUserVO toVO(SysUser u) {
        SysUserVO vo = new SysUserVO();
        vo.setId(u.getId());
        vo.setUsername(u.getUsername());
        vo.setNickname(u.getNickname());
        vo.setEmail(u.getEmail());
        vo.setRemark(u.getRemark());
        vo.setStatus(u.getStatus());
        vo.setCreatorId(u.getCreatorId());
        vo.setCreator(u.getCreator());
        vo.setCreatedAt(u.getCreatedAt());
        vo.setUpdatedAt(u.getUpdatedAt());
        return vo;
    }
}
