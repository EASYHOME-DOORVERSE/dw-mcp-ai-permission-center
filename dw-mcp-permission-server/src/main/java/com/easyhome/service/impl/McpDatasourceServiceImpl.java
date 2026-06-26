package com.easyhome.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.easyhome.entity.McpDatasource;
import com.easyhome.mapper.McpDatasourceMapper;
import com.easyhome.mapper.McpToolMapper;
import com.easyhome.service.McpDatasourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Connection;
import java.sql.DriverManager;

@Slf4j
@Service
@RequiredArgsConstructor
public class McpDatasourceServiceImpl implements McpDatasourceService {

    private final McpDatasourceMapper datasourceMapper;
    private final McpToolMapper toolMapper;

    @Override
    public Page<McpDatasource> page(long current, long size, String dsKey, Integer status) {
        LambdaQueryWrapper<McpDatasource> wrapper = new LambdaQueryWrapper<McpDatasource>()
                .like(StringUtils.hasText(dsKey), McpDatasource::getDsKey, dsKey)
                .eq(status != null, McpDatasource::getStatus, status)
                .orderByDesc(McpDatasource::getCreatedAt);
        return datasourceMapper.selectPage(new Page<>(current, size), wrapper);
    }

    @Override
    public McpDatasource getById(Long id) {
        return datasourceMapper.selectById(id);
    }

    @Override
    public McpDatasource create(McpDatasource datasource) {
        datasourceMapper.insert(datasource);
        return datasource;
    }

    @Override
    public McpDatasource update(McpDatasource datasource) {
        // 密码为空时不更新，保留数据库中已有的密码
        if (!StringUtils.hasText(datasource.getPassword())) {
            datasource.setPassword(null);
        }
        datasourceMapper.updateById(datasource);
        return datasourceMapper.selectById(datasource.getId());
    }

    @Override
    public void delete(Long id) {
        // 查询数据源信息
        McpDatasource datasource = datasourceMapper.selectById(id);
        if (datasource == null) {
            log.warn("删除数据源失败：数据源不存在, id={}", id);
            return;
        }

        // 检查是否有工具绑定该数据源
        int toolCount = toolMapper.countToolsByDatasourceKey(datasource.getDsKey());
        if (toolCount > 0) {
            log.warn("删除数据源失败：有 {} 个工具绑定该数据源, dsKey={}", toolCount, datasource.getDsKey());
            throw new IllegalStateException(
                    String.format("无法删除数据源 [%s]，当前有 %d 个工具正在使用该数据源", 
                            datasource.getDsName(), toolCount));
        }

        // 没有工具绑定，可以删除
        datasourceMapper.deleteById(id);
        log.info("数据源已删除: {}", datasource.getDsKey());
    }

    @Override
    public String testConnection(Long id) {
        McpDatasource ds = datasourceMapper.selectById(id);
        if (ds == null) {
            return "数据源不存在";
        }
        try {
            if (StringUtils.hasText(ds.getDriverClass())) {
                Class.forName(ds.getDriverClass());
            }
            try (Connection conn = DriverManager.getConnection(ds.getUrl(), ds.getUsername(), ds.getPassword())) {
                String product = conn.getMetaData().getDatabaseProductName();
                String version = conn.getMetaData().getDatabaseProductVersion();
                return "连接成功：" + product + " " + version;
            }
        } catch (Exception e) {
            log.warn("数据源[{}]连通性测试失败: {}", ds.getDsKey(), e.getMessage());
            return "连接失败：" + e.getMessage();
        }
    }
}
