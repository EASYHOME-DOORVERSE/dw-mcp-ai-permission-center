package com.easyhome.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.easyhome.api.dto.McpDatasourceDTO;
import com.easyhome.api.feign.McpDatasourceClient;
import com.easyhome.api.vo.McpDatasourceVO;
import com.easyhome.api.vo.PageResult;
import com.easyhome.api.vo.Result;
import com.easyhome.entity.McpDatasource;
import com.easyhome.service.McpDatasourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/mcp/datasources")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class McpDatasourceController implements McpDatasourceClient {

    private final McpDatasourceService datasourceService;

    @Override
    public Result<PageResult<McpDatasourceVO>> page(long current, long size,
                                                    String dsKey, Integer status) {
        Page<McpDatasource> p = datasourceService.page(current, size, dsKey, status);
        return Result.ok(PageResult.of(p.getCurrent(), p.getSize(), p.getTotal(),
                p.getRecords().stream().map(this::toVO).toList()));
    }

    @Override
    public Result<McpDatasourceVO> getById(@PathVariable Long id) {
        McpDatasource ds = datasourceService.getById(id);
        return ds == null ? Result.fail(404, "数据源不存在") : Result.ok(toVO(ds));
    }

    @Override
    public Result<McpDatasourceVO> create(@RequestBody @Validated McpDatasourceDTO dto) {
        if (!StringUtils.hasText(dto.getPassword())) {
            return Result.fail(400, "数据库密码不能为空");
        }
        return Result.ok(toVO(datasourceService.create(fromDTO(dto))));
    }

    @Override
    public Result<McpDatasourceVO> update(@PathVariable Long id,
                                          @RequestBody @Validated McpDatasourceDTO dto) {
        McpDatasource ds = fromDTO(dto);
        ds.setId(id);
        return Result.ok(toVO(datasourceService.update(ds)));
    }

    @Override
    public Result<Void> delete(@PathVariable Long id) {
        try {
            datasourceService.delete(id);
            return Result.ok();
        } catch (IllegalStateException e) {
            // 数据源被工具绑定，不允许删除
            return Result.fail(400, e.getMessage());
        } catch (Exception e) {
            // 其他未知错误
            return Result.fail(500, "删除数据源失败：" + e.getMessage());
        }
    }

    @Override
    public Result<String> testConnection(@PathVariable Long id) {
        return Result.ok(datasourceService.testConnection(id));
    }

    // ---- 转换方法 ----

    private McpDatasourceVO toVO(McpDatasource ds) {
        McpDatasourceVO vo = new McpDatasourceVO();
        vo.setId(ds.getId());
        vo.setDsKey(ds.getDsKey());
        vo.setDsName(ds.getDsName());
        vo.setDbType(ds.getDbType());
        vo.setUrl(ds.getUrl());
        vo.setUsername(ds.getUsername());
        // 密码不返回
        vo.setDriverClass(ds.getDriverClass());
        vo.setPoolMinSize(ds.getPoolMinSize());
        vo.setPoolMaxSize(ds.getPoolMaxSize());
        vo.setRemark(ds.getRemark());
        vo.setStatus(ds.getStatus());
        vo.setCreatorId(ds.getCreatorId());
        vo.setCreator(ds.getCreator());
        vo.setCreatedAt(ds.getCreatedAt());
        vo.setUpdatedAt(ds.getUpdatedAt());
        return vo;
    }

    private McpDatasource fromDTO(McpDatasourceDTO dto) {
        McpDatasource ds = new McpDatasource();
        ds.setDsKey(dto.getDsKey());
        ds.setDsName(dto.getDsName());
        ds.setDbType(dto.getDbType() != null ? dto.getDbType() : "mysql");
        ds.setUrl(dto.getUrl());
        ds.setUsername(dto.getUsername());
        ds.setPassword(dto.getPassword());
        ds.setDriverClass(dto.getDriverClass());
        ds.setPoolMinSize(dto.getPoolMinSize() != null ? dto.getPoolMinSize() : 5);
        ds.setPoolMaxSize(dto.getPoolMaxSize() != null ? dto.getPoolMaxSize() : 20);
        ds.setRemark(dto.getRemark());
        if (dto.getStatus() != null) ds.setStatus(dto.getStatus());
        return ds;
    }
}
