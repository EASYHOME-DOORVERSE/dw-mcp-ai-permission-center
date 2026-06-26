package com.easyhome.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.easyhome.entity.SysApiKey;
import com.easyhome.entity.SysUser;

import java.util.List;

/**
 * API Key 管理服务（在原有认证接口基础上扩充管理方法）
 */
public interface SysApiKeyService {

    /**
     * API Key 验证结果：包含用户信息和 accountId，避免重复查询数据库
     */
    record ApiKeyValidationResult(SysUser user, String accountId) {}

    /** 根据 Bearer Token 验证并返回对应用户，验证失败返回 null */
    ApiKeyValidationResult validateAndGetUser(String apiKey);

    /** 根据 API Key 获取 accountId */
    String getAccountIdByApiKey(String apiKey);

    /** 为指定用户生成新 API Key，返回含完整明文 key 的实体 */
    SysApiKey generateKey(Long userId, String keyName);

    /** 禁用指定 API Key */
    void disableKey(Long keyId);

    /** 启用指定 API Key */
    void enableKey(Long keyId);

    /** 逻辑删除指定 API Key */
    void deleteKey(Long keyId);

    /** 分页查询 API Key 列表 */
    Page<SysApiKey> page(long current, long size, Long userId, Integer status);

    /** 根据 ID 查询 API Key */
    SysApiKey getById(Long keyId);

    /** 查询用户的所有 API Key */
    List<SysApiKey> listByUserId(Long userId);


    /** 更新 API Key */
    void updateById(SysApiKey apiKey);
    /**
     * 清除指定用户所有 API Key 的验证缓存
     * <p>
     * 当用户被停用/删除时调用，确保缓存的用户对象立即失效
     *
     * @param userId 用户 ID
     */
    void evictApiKeyCacheByUserId(Long userId);
}
