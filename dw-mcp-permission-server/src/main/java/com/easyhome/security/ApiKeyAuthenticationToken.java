package com.easyhome.security;

import com.easyhome.entity.SysUser;
import com.easyhome.entity.UserPermissionCache;
import lombok.Getter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * API Key 认证令牌
 * 认证成功后存入 SecurityContext，携带用户信息和角色权限
 * <p>
 * 注意：
 * - roles 参数仅用于构造 authorities，不单独存储
 * - 权限信息通过 getAuthorities() 获取（继承自 AbstractAuthenticationToken）
 */
public class ApiKeyAuthenticationToken extends AbstractAuthenticationToken {

    @Getter
    private final SysUser user;
    private final String apiKey;
    @Getter
    private final Set<String> accessibleToolNames;

    @Getter
    private final String accountId;

    /**
     * 构造认证令牌
     *
     * @param user   用户信息
     * @param apiKey API Key
     * @param userPermission 用户权限信息（用于构建 Spring Security 权限）
     */
    public ApiKeyAuthenticationToken(SysUser user, String apiKey, UserPermissionCache userPermission, String accountId) {
        super(buildAuthorities(userPermission.getRoleCodes()));
        this.user = user;
        this.accessibleToolNames = userPermission.getAccessibleToolNames();
        this.apiKey = apiKey;
        this.accountId = accountId;
        // 标记为已认证
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return apiKey;
    }

    @Override
    public Object getPrincipal() {
        return user.getUsername();
    }

    /**
     * 将角色列表转换为 Spring Security 权限集合
     *
     * @param roleCodes 角色代码列表
     * @return 权限集合
     */
    private static Collection<GrantedAuthority> buildAuthorities(List<String> roleCodes) {
        return roleCodes.stream()
                .map(roleCode -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + roleCode))
                .toList();
    }
}
