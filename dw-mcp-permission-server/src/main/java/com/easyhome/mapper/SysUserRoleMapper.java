package com.easyhome.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.easyhome.entity.SysUserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Set;

@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {

    /**
     * 根据多个角色ID分页查询用户ID（游标分页，避免深度分页问题）
     *
     * @param roleIds 角色ID集合
     * @param lastUserId 上一次查询的最后一个用户ID（首次查询传 null 或 0）
     * @param limit 每页数量
     * @return 用户ID列表（已去重）
     */
    @Select("<script>" +
            "SELECT DISTINCT user_id FROM sys_user_role" +
            " WHERE role_id IN" +
            " <foreach collection='roleIds' item='roleId' open='(' separator=',' close=')'>" +
            "   #{roleId}" +
            " </foreach>" +
            " AND is_deleted = 0" +
            " AND user_id > #{lastUserId}" +
            " ORDER BY user_id" +
            " LIMIT #{limit}" +
            "</script>")
    List<Long> selectUserIdsByRoleIdsWithCursor(
            @Param("roleIds") Set<Long> roleIds,
            @Param("lastUserId") Long lastUserId,
            @Param("limit") Integer limit);
}
