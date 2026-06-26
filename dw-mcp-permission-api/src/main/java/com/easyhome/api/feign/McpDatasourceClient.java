package com.easyhome.api.feign;

import com.easyhome.api.dto.McpDatasourceDTO;
import com.easyhome.api.vo.McpDatasourceVO;
import com.easyhome.api.vo.PageResult;
import com.easyhome.api.vo.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/**
 * MCP 数据源管理 Feign Client
 */
@FeignClient(name = "mcp-permission-center", contextId = "mcpDatasourceClient", path = "/api/mcp/datasources")
public interface McpDatasourceClient {

    /**
     * 分页查询数据源列表
     */
    @GetMapping
    Result<PageResult<McpDatasourceVO>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String dsKey,
            @RequestParam(required = false) Integer status
    );

    /**
     * 查询数据源详情（密码字段不返回）
     */
    @GetMapping("/{id}")
    Result<McpDatasourceVO> getById(@PathVariable Long id);

    /**
     * 新增数据源
     */
    @PostMapping
    Result<McpDatasourceVO> create(@RequestBody McpDatasourceDTO dto);

    /**
     * 更新数据源（id必填）
     */
    @PutMapping("/{id}")
    Result<McpDatasourceVO> update(@PathVariable Long id, @RequestBody McpDatasourceDTO dto);

    /**
     * 逻辑删除数据源
     */
    @DeleteMapping("/{id}")
    Result<Void> delete(@PathVariable Long id);

    /**
     * 测试数据源连通性
     */
    @PostMapping("/{id}/test")
    Result<String> testConnection(@PathVariable Long id);
}
