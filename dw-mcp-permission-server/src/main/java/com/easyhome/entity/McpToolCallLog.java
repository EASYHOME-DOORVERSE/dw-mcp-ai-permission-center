package com.easyhome.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * MCP 工具调用日志
 *
 * 记录每次 tools/call 的执行情况：用户、工具、时间、耗时、成功/失败。
 * 不继承 BaseEntity（无需状态管理、逻辑删除、审计人等字段）。
 */
@Data
@Builder
@TableName("mcp_tool_call_log")
public class McpToolCallLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** MCP 工具数据库 ID */
    private Long toolId;

    /** MCP 工具名称（格式：tool_{id}） */
    private String toolName;

    /** 调用用户 ID */
    private Long userId;

    /** 调用用户名 */
    private String username;

    /** 调用发起时间（毫秒精度） */
    private LocalDateTime callAt;

    /** 调用总耗时（毫秒） */
    private Long durationMs;

    /** 是否成功：true=成功，false=失败或被拒绝 */
    private Boolean success;

    /** 失败/拒绝原因，成功时为 null */
    private String denyReason;

    /** 调用参数（JSON 格式，脱敏后存储） */
    private String requestArgs;

    /** 响应内容字节数 */
    private Integer responseSize;

    /** 记录创建时间 */
    private LocalDateTime createdAt;
}
