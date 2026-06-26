package com.easyhome.mcp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.easyhome.entity.McpDatasource;
import com.easyhome.mapper.McpDatasourceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * 数据源连接注册表
 *
 * 不使用连接池，每次执行 SQL 时直接从数据库读取连接信息并创建 JDBC 连接，
 * 执行完毕后立即关闭。适用于 Hologres 等对连接池初始化不兼容的云数据库。
 * 无需在启动时预加载，也无需在 CRUD 时维护缓存。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatasourceRegistry {

    private final McpDatasourceMapper datasourceMapper;

    /**
     * 根据 dsKey 获取数据源配置信息。
     * 用于获取 dbType 等元数据，以决定分页 SQL 的拼接方式。
     */
    public McpDatasource getDatasource(String dsKey) {
        McpDatasource ds = datasourceMapper.selectOne(new LambdaQueryWrapper<McpDatasource>()
                .eq(McpDatasource::getDsKey, dsKey)
                .eq(McpDatasource::getStatus, 1));
        if (ds == null) {
            throw new IllegalArgumentException("数据源[" + dsKey + "]不存在或未启用");
        }
        return ds;
    }

    /**
     * 根据 dsKey 创建一条新的 JDBC 连接。
     * 每次调用均从数据库读取最新的连接信息并创建新连接，由调用方负责关闭。
     */
    public Connection getConnection(String dsKey) throws SQLException {
        McpDatasource ds = getDatasource(dsKey);
        return createConnection(ds);
    }

    /**
     * 根据 McpDatasource 配置创建 JDBC 连接，避免重复查询数据库。
     */
    public Connection createConnection(McpDatasource ds) throws SQLException {
        try {
            if (StringUtils.hasText(ds.getDriverClass())) {
                Class.forName(ds.getDriverClass());
            }
        } catch (ClassNotFoundException e) {
            throw new SQLException("数据源[" + ds.getDsKey() + "]驱动加载失败: " + ds.getDriverClass(), e);
        }
        return DriverManager.getConnection(ds.getUrl(), ds.getUsername(), ds.getPassword());
    }
}
