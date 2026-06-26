# 更新日志

本项目遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/) 格式。

## [1.0.0] - 2025-06-25

### 新增
- 基于 RBAC 的 MCP 工具权限管理
- 双通道认证（JWT 管理端 + API Key MCP 端）
- AI 一键生成工具配置（SSE 流式）
- JDBC 多数据源动态执行
- 跨节点实时同步（Redis Pub/Sub + 定时兜底）
- Dashboard 调用统计工作台
- 用户/角色/API Key/工具/分类/数据源完整 CRUD
- Vue 3 + Element Plus 管理后台
- 微服务 OpenFeign 接口声明

### 安全
- BCrypt 密码加密存储
- 登录错误锁定（5次/30分钟）
- JWT 黑名单即时失效
- API Key 过期时间控制
