package com.easyhome.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.easyhome.entity.McpToolCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface McpToolCategoryMapper extends BaseMapper<McpToolCategory> {

    /**
     * 查询用户可访问工具所属的去重分类
     * <p>
     * 通过 RBAC 链路（用户 → 角色 → 工具 → 分类）关联查询，
     * 只返回用户有权限访问的启用工具所属的分类。
     */
    @Select("""
            SELECT DISTINCT c.* FROM mcp_tool_category c
            INNER JOIN mcp_tool t ON t.category_id = c.id
            WHERE t.id IN (
              SELECT DISTINCT rt.tool_id FROM sys_role_tool rt
              INNER JOIN sys_user_role ur ON rt.role_id = ur.role_id
              WHERE ur.user_id = #{userId} AND ur.is_deleted = 0
            )
            AND t.status = 1 AND t.is_deleted = 0
            AND c.is_deleted = 0
            ORDER BY c.sort_order ASC, c.id ASC
            """)
    List<McpToolCategory> selectCategoriesByUserId(@Param("userId") Long userId);
}
