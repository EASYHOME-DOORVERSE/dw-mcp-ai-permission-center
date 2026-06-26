package com.easyhome.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.easyhome.entity.McpTool;
import com.easyhome.entity.McpToolCategory;
import com.easyhome.mapper.McpToolCategoryMapper;
import com.easyhome.mapper.McpToolMapper;
import com.easyhome.service.McpToolCategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class McpToolCategoryServiceImpl implements McpToolCategoryService {

    private final McpToolCategoryMapper categoryMapper;
    private final McpToolMapper toolMapper;

    @Override
    public List<McpToolCategory> list() {
        return categoryMapper.selectList(new LambdaQueryWrapper<McpToolCategory>()
                .orderByAsc(McpToolCategory::getSortOrder)
                .orderByAsc(McpToolCategory::getId));
    }

    @Override
    public List<McpToolCategory> listByUserId(Long userId) {
        return categoryMapper.selectCategoriesByUserId(userId);
    }

    @Override
    public McpToolCategory getById(Long id) {
        return categoryMapper.selectById(id);
    }

    @Override
    public McpToolCategory getByCode(String categoryCode) {
        return categoryMapper.selectOne(new LambdaQueryWrapper<McpToolCategory>()
                .eq(McpToolCategory::getCategoryCode, categoryCode));
    }

    @Override
    public McpToolCategory getDefaultCategory() {
        McpToolCategory category = getByCode(McpToolCategory.DEFAULT_CATEGORY_CODE);
        if (category == null) {
            log.warn("默认分类不存在，自动创建");
            category = new McpToolCategory();
            category.setCategoryName("其他");
            category.setCategoryCode(McpToolCategory.DEFAULT_CATEGORY_CODE);
            category.setSortOrder(9999);
            category.setRemark("默认分类，工具未指定分类或分类被删除时自动归入");
            categoryMapper.insert(category);
        }
        return category;
    }

    @Override
    public McpToolCategory create(McpToolCategory category) {
        categoryMapper.insert(category);
        return category;
    }

    @Override
    public McpToolCategory update(McpToolCategory category) {
        categoryMapper.updateById(category);
        return categoryMapper.selectById(category.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        McpToolCategory category = categoryMapper.selectById(id);
        if (category == null) {
            return;
        }

        // 默认分类不可删除
        if (McpToolCategory.DEFAULT_CATEGORY_CODE.equals(category.getCategoryCode())) {
            throw new IllegalStateException("默认分类不可删除");
        }

        // 将该分类下的工具归入默认分类
        McpToolCategory defaultCategory = getDefaultCategory();
        toolMapper.update(null, new LambdaUpdateWrapper<McpTool>()
                .eq(McpTool::getCategoryId, id)
                .set(McpTool::getCategoryId, defaultCategory.getId()));
        log.info("分类[{}]删除，关联工具已归入默认分类[{}]", category.getCategoryName(), defaultCategory.getCategoryName());

        // 逻辑删除分类
        categoryMapper.deleteById(id);
    }
}
