package com.easyhome.config;

import com.easyhome.mcp.filter.McpToolsListFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author DW MCP Team
 * @date 2026/5/27
 */
@Configuration
public class FilterConfig {

    @Bean
    public FilterRegistrationBean<McpToolsListFilter> mcpToolsListFilterRegistration(McpToolsListFilter filter) {
        FilterRegistrationBean<McpToolsListFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.addUrlPatterns("/mcp"); // 只拦截 /mcp 路径
        registration.setName("mcpToolsListFilter");
        registration.setOrder(Integer.MAX_VALUE); // 设置为最后执行，确保在认证过滤器之后
        return registration;
    }
}
