package com.easyhome.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.easyhome.entity.McpTool;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface McpToolMapper extends BaseMapper<McpTool> {

    /**
     * 查询用户（通过角色）可访问的所有启用工具
     * 核心RBAC查询：用户 → 角色 → 工具
     */
    @Select("""
            SELECT * FROM mcp_tool t
            WHERE t.id IN (
              SELECT DISTINCT rt.tool_id FROM sys_role_tool rt INNER JOIN sys_user_role ur ON rt.role_id = ur.role_id
              WHERE ur.user_id = #{userId}
                AND ur.is_deleted = 0
              )
              AND t.status = 1
              AND t.is_deleted = 0
            ORDER BY t.sort_order ASC, t.id ASC
            """)
    List<McpTool> selectToolsByUserId(@Param("userId") Long userId);

    /**
     * 统计用户（通过角色）可访问的启用工具数量
     */
    @Select("""
            SELECT COUNT(1) FROM mcp_tool t
            WHERE t.id IN (
              SELECT DISTINCT rt.tool_id FROM sys_role_tool rt INNER JOIN sys_user_role ur ON rt.role_id = ur.role_id
              WHERE ur.user_id = #{userId}
                AND ur.is_deleted = 0
              )
              AND t.status = 1
              AND t.is_deleted = 0
            """)
    int countEnabledToolsByUserId(@Param("userId") Long userId);

    /**
     * 查询角色可访问的所有启用工具
     */
    @Select("""
            SELECT t.* FROM mcp_tool t
            INNER JOIN sys_role_tool rt ON t.id = rt.tool_id
            WHERE rt.role_id = #{roleId}
              AND t.status = 1
              AND t.is_deleted = 0
            ORDER BY t.sort_order ASC, t.id ASC
            """)
    List<McpTool> selectToolsByRoleId(@Param("roleId") Long roleId);

    /**
     * 统计使用指定数据源的工具数量
     *
     * @param datasourceKey 数据源 Key
     * @return 使用该数据源的工具数量
     */
    @Select("""
            SELECT COUNT(1) FROM mcp_tool
            WHERE datasource_key = #{datasourceKey}
              AND is_deleted = 0
            """)
    int countToolsByDatasourceKey(@Param("datasourceKey") String datasourceKey);

    /**
     * 按用户 RBAC 权限分页查询启用的工具（支持 toolName/displayName/toolType/categoryId 筛选）
     */
    @Select("""
            SELECT t.* FROM mcp_tool t
            WHERE t.id IN (
              SELECT DISTINCT rt.tool_id FROM sys_role_tool rt
              INNER JOIN sys_user_role ur ON rt.role_id = ur.role_id
              WHERE ur.user_id = #{userId} AND ur.is_deleted = 0
            )
            AND t.status = 1 AND t.is_deleted = 0
            AND ((t.tool_name LIKE CONCAT('%',#{toolName},'%') OR t.display_name LIKE CONCAT('%',#{toolName},'%')) OR #{toolName} IS NULL)
            AND (t.tool_type = #{toolType} OR #{toolType} IS NULL)
            AND (t.category_id = #{categoryId} OR #{categoryId} IS NULL)
            ORDER BY t.sort_order ASC, t.id ASC
            """)
    Page<McpTool> selectToolsByUserIdPaged(
            Page<McpTool> page,
            @Param("userId") Long userId,
            @Param("toolName") String toolName,
            @Param("toolType") String toolType,
            @Param("categoryId") Long categoryId
    );
}
