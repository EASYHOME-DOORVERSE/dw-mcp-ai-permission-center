package com.easyhome.service;

import com.easyhome.entity.McpToolCategory;

import java.util.List;

/**
 * MCP 工具分类管理服务
 */
public interface McpToolCategoryService {

    /** 查询所有分类（按排序值升序） */
    List<McpToolCategory> list();

    /** 查询用户可访问工具所属的去重分类 */
    List<McpToolCategory> listByUserId(Long userId);

    /** 根据ID查询 */
    McpToolCategory getById(Long id);

    /** 根据分类编码查询 */
    McpToolCategory getByCode(String categoryCode);

    /** 获取默认分类（code=other） */
    McpToolCategory getDefaultCategory();

    /** 新增分类 */
    McpToolCategory create(McpToolCategory category);

    /** 更新分类 */
    McpToolCategory update(McpToolCategory category);

    /**
     * 删除分类
     * <p>该分类下的工具自动归入默认"其他"分类，默认分类不可删除。
     */
    void delete(Long id);
}
