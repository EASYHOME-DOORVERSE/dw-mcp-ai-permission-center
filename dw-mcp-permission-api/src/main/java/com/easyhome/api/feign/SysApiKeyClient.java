package com.easyhome.api.feign;

import com.easyhome.api.dto.SysApiKeyDTO;
import com.easyhome.api.vo.PageResult;
import com.easyhome.api.vo.Result;
import com.easyhome.api.vo.SysApiKeyVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/**
 * API Key 管理 Feign Client
 */
@FeignClient(name = "mcp-permission-center", contextId = "sysApiKeyClient", path = "/api/api-keys")
public interface SysApiKeyClient {

    /**
     * 分页查询 API Key 列表（密钥脱敏）
     */
    @GetMapping
    Result<PageResult<SysApiKeyVO>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Integer status
    );

    /**
     * 为用户生成新 API Key
     * <p>返回的 apiKey 为完整明文（sk-xxxx），此后不再展示，请妥善保存。
     */
    @PostMapping("/generate")
    Result<SysApiKeyVO> generate(@RequestBody SysApiKeyDTO dto);

    /**
     * 获取 API Key 完整明文（仅限所有者或管理员）
     */
    @GetMapping("/{id}")
    Result<SysApiKeyVO> getById(@PathVariable Long id);

    /**
     * 停用 API Key
     */
    @PutMapping("/{id}/disable")
    Result<Void> disable(@PathVariable Long id);

    /**
     * 启用 API Key
     */
    @PutMapping("/{id}/enable")
    Result<Void> enable(@PathVariable Long id);

    /**
     * 逻辑删除 API Key
     */
    @DeleteMapping("/{id}")
    Result<Void> delete(@PathVariable Long id);
}
