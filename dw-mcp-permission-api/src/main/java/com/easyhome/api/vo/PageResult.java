package com.easyhome.api.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 分页响应结构
 */
@Data
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前页码（从1开始） */
    private long current;

    /** 每页条数 */
    private long size;

    /** 总记录数 */
    private long total;

    /** 当前页数据 */
    private List<T> records;

    public static <T> PageResult<T> of(long current, long size, long total, List<T> records) {
        PageResult<T> page = new PageResult<>();
        page.current = current;
        page.size = size;
        page.total = total;
        page.records = records;
        return page;
    }
}
