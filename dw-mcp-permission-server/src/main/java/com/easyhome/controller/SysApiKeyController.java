package com.easyhome.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.easyhome.api.dto.SysApiKeyDTO;
import com.easyhome.api.feign.SysApiKeyClient;
import com.easyhome.api.vo.PageResult;
import com.easyhome.api.vo.Result;
import com.easyhome.api.vo.SysApiKeyVO;
import com.easyhome.entity.SysApiKey;
import com.easyhome.security.JwtAuthenticationToken;
import com.easyhome.service.SysApiKeyService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/api-keys")
@RequiredArgsConstructor
public class SysApiKeyController implements SysApiKeyClient {

    private final SysApiKeyService apiKeyService;

    /**
     * 获取当前 JWT 认证信息
     */
    private JwtAuthenticationToken getCurrentAuth() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth;
        }
        return null;
    }

    /**
     * 判断当前用户是否为 ADMIN 角色
     */
    private boolean isAdmin() {
        JwtAuthenticationToken auth = getCurrentAuth();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    /**
     * 获取当前用户 ID
     */
    private Long getCurrentUserId() {
        JwtAuthenticationToken auth = getCurrentAuth();
        return auth != null ? auth.getUserId() : null;
    }

    @Override
    public Result<PageResult<SysApiKeyVO>> page(long current, long size, Long userId, Integer status) {
        // 非 admin 用户强制只能查看自己的 Key
        if (!isAdmin()) {
            userId = getCurrentUserId();
        }
        Page<SysApiKey> p = apiKeyService.page(current, size, userId, status);
        return Result.ok(PageResult.of(p.getCurrent(), p.getSize(), p.getTotal(),
                p.getRecords().stream().map(k -> toVO(k, true)).toList()));
    }

    @Override
    public Result<SysApiKeyVO> generate(@RequestBody @Validated SysApiKeyDTO dto) {
        Long userId;
        if (isAdmin()) {
            // 管理员必须指定用户
            if (dto.getUserId() == null) {
                return Result.fail(400, "请选择用户");
            }
            userId = dto.getUserId();
        } else {
            // 普通用户只能为自己生成 Key，忽略前端传值
            userId = getCurrentUserId();
        }
        SysApiKey key = apiKeyService.generateKey(userId, dto.getKeyName());
        if (dto.getExpiredAt() != null) {
            key.setExpiredAt(dto.getExpiredAt());
        }
        if (dto.getAccountId() != null) {
            key.setAccountId(dto.getAccountId());
        }
        apiKeyService.updateById(key);
        // 生成接口返回完整明文 key
        return Result.ok(toVO(key, false));
    }

    @PutMapping
    public Result<Void> update(@RequestBody SysApiKeyDTO dto) {
        if (dto.getId() == null) {
            return Result.fail("ID不能为空");
        }
        SysApiKey key = apiKeyService.getById(dto.getId());
        if (key == null) {
            return Result.fail("API Key不存在");
        }
        if (dto.getKeyName() != null) {
            key.setKeyName(dto.getKeyName());
        }
        if (dto.getAccountId() != null) {
            key.setAccountId(dto.getAccountId());
        }
        apiKeyService.updateById(key);
        return Result.ok();
    }

    @Override
    public Result<SysApiKeyVO> getById(@PathVariable Long id) {
        SysApiKey key = apiKeyService.getById(id);
        if (key == null) {
            return Result.fail(404, "API Key 不存在");
        }
        if (!isAdmin() && !key.getUserId().equals(getCurrentUserId())) {
            return Result.fail(403, "无权查看此 API Key");
        }
        return Result.ok(toVO(key, false));
    }

    @Override
    public Result<Void> disable(@PathVariable Long id) {
        // 非 admin 用户只能操作自己的 Key
        if (!isAdmin()) {
            SysApiKey key = apiKeyService.getById(id);
            if (key == null || !key.getUserId().equals(getCurrentUserId())) {
                return Result.fail(403, "无权操作此 API Key");
            }
        }
        apiKeyService.disableKey(id);
        return Result.ok();
    }

    @Override
    public Result<Void> enable(@PathVariable Long id) {
        if (!isAdmin()) {
            SysApiKey key = apiKeyService.getById(id);
            if (key == null || !key.getUserId().equals(getCurrentUserId())) {
                return Result.fail(403, "无权操作此 API Key");
            }
        }
        apiKeyService.enableKey(id);
        return Result.ok();
    }

    @Override
    public Result<Void> delete(@PathVariable Long id) {
        if (!isAdmin()) {
            SysApiKey key = apiKeyService.getById(id);
            if (key == null || !key.getUserId().equals(getCurrentUserId())) {
                return Result.fail(403, "无权操作此 API Key");
            }
        }
        apiKeyService.deleteKey(id);
        return Result.ok();
    }

    // ---- 转换方法 ----

    /**
     * @param mask true=脱敏展示（列表/详情），false=完整明文（仅生成时）
     */
    private SysApiKeyVO toVO(SysApiKey k, boolean mask) {
        SysApiKeyVO vo = new SysApiKeyVO();
        vo.setId(k.getId());
        vo.setUserId(k.getUserId());
        vo.setKeyName(k.getKeyName());
        vo.setAccountId(k.getAccountId());
        String raw = k.getApiKey();
        if (mask && raw != null && raw.length() > 10) {
            vo.setApiKey(raw.substring(0, 6) + "****" + raw.substring(raw.length() - 4));
        } else {
            vo.setApiKey(raw);
        }
        vo.setExpiredAt(k.getExpiredAt());
        vo.setLastUsedAt(k.getLastUsedAt());
        vo.setStatus(k.getStatus());
        vo.setCreatedAt(k.getCreatedAt());
        vo.setUpdatedAt(k.getUpdatedAt());
        return vo;
    }
}
