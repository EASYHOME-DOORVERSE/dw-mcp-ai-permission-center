package com.easyhome.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.easyhome.entity.McpDatasource;

/**
 * MCP 数据源管理服务
 */
public interface McpDatasourceService {

    Page<McpDatasource> page(long current, long size, String dsKey, Integer status);

    McpDatasource getById(Long id);

    McpDatasource create(McpDatasource datasource);

    McpDatasource update(McpDatasource datasource);

    void delete(Long id);

    /** 测试数据源连通性，返回描述信息 */
    String testConnection(Long id);
}
