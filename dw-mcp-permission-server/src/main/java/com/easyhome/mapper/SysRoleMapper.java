package com.easyhome.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.easyhome.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {

    /**
     * 查询用户拥有的所有启用角色
     */
    @Select("""
            SELECT r.* FROM sys_role r
            INNER JOIN sys_user_role ur ON r.id = ur.role_id
            WHERE ur.user_id = #{userId}
              AND r.status = 1
              AND r.is_deleted = 0
              AND ur.is_deleted = 0
            """)
    List<SysRole> selectRolesByUserId(@Param("userId") Long userId);

    /**
     * 根据工具ID查询所有关联的启用角色
     *
     * @param toolId 工具ID
     * @return 角色列表
     */
    @Select("""
            SELECT DISTINCT r.* FROM sys_role r
            INNER JOIN sys_role_tool rt ON r.id = rt.role_id
            WHERE rt.tool_id = #{toolId}
              AND r.status = 1
              AND r.is_deleted = 0
            """)
    List<SysRole> selectRolesByToolId(@Param("toolId") Long toolId);
}
