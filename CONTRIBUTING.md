# 贡献指南

感谢你关注并愿意参与 **DW MCP AI Permission Center** 项目的贡献！

本指南将帮助你快速了解如何参与到本项目中，无论你是提交 Bug 报告、提出新功能建议，还是提交代码 Pull Request，我们都表示热烈欢迎。

---

## 如何参与贡献

### 报告 Bug

如果你在使用过程中发现 Bug，请按照以下步骤操作：

1. 在提交新的 Issue 前，请先搜索 [Issues](https://github.com/easyhome/dw-mcp-ai-permission-center/issues) 列表，确认该问题是否已被记录。
2. 如果没有找到相关问题，请使用 `Bug report` 模板创建新 Issue。
3. 请尽可能详细地描述问题，包括：
   - 问题的简要描述
   - 复现步骤
   - 期望结果与实际结果
   - 运行环境（JDK、MySQL、Redis、Node 等版本）
   - 相关错误日志或截图

### 建议功能

如果你对本项目有新功能或改进建议，欢迎提交功能建议 Issue：

1. 搜索现有 Issue，确认是否已有类似建议。
2. 使用 `Feature request` 模板创建新 Issue。
3. 描述清楚你的使用场景、预期收益以及建议的实现思路。

### 提交代码

我们欢迎你通过 Pull Request 提交代码贡献，包括修复 Bug、新增功能、优化性能或完善文档等。

---

## 开发环境搭建

在开始开发前，请确保本地已安装并配置好以下环境：

| 组件 | 版本要求 | 说明 |
|------|---------|------|
| JDK | 21 | Java 后端运行环境 |
| MySQL | 8.0 | 关系型数据库 |
| Redis | 6.0+ | 缓存、分布式锁、Pub/Sub 同步 |
| Node.js | 18+ | 前端工程依赖 |
| Maven | 3.9+ | Java 项目构建工具 |

### 后端启动

1. 克隆仓库到本地。
2. 使用 MySQL 创建项目数据库，并导入 `dw-mcp-permission-server/src/main/resources/db` 目录下的初始化脚本。
3. 根据实际情况修改 `application-dev.yml` 中的数据库和 Redis 连接配置。
4. 执行以下命令编译并启动后端服务：

```bash
mvn clean install
mvn -pl dw-mcp-permission-server spring-boot:run
```

### 前端启动

```bash
cd dw-mcp-permission-ui
npm install
npm run dev
```

---

## 项目结构简介

本项目采用多模块 Maven 工程结构：

```
dw-mcp-ai-permission-center
├── dw-mcp-permission-api      # 微服务 OpenFeign 接口声明与 DTO/VO
├── dw-mcp-permission-server   # 后端主服务：权限管理、MCP 工具、数据源、统计等
└── dw-mcp-permission-ui       # 前端管理后台：Vue 3 + Element Plus
```

- **dw-mcp-permission-api**：供内部微服务调用的 API 接口模块，包含 Feign Client、DTO、VO 等。
- **dw-mcp-permission-server**：核心业务后端服务，提供 RBAC 权限、认证、工具管理、数据源管理、Dashboard 统计等能力。
- **dw-mcp-permission-ui**：面向管理员的前端操作界面，采用 Vue 3 组合式 API 与 Element Plus 组件库构建。

---

## 代码规范要求

### Java 后端

- 严格遵循《阿里巴巴 Java 开发手册》编码规范。
- 使用 JDK 21 新特性时应注意兼容性与可读性。
- 类、方法、变量命名需清晰表达意图，避免拼音或无意义缩写。
- 保持代码整洁，避免过度嵌套与重复代码。
- 新增核心逻辑应配套单元测试。

### 前端

- 统一使用 **Vue 3 组合式 API**（`<script setup>` 风格）进行开发。
- 组件、变量、函数命名采用语义化英文，优先使用小驼峰命名法。
- 保持组件职责单一，复杂页面按功能拆分子组件。
- 使用 Element Plus 组件库，遵循其设计规范。
- 合理使用 TypeScript 类型（如已启用），提升代码可维护性。

---

## Git 分支策略

本项目采用 **Git Flow 风格** 的分支管理策略：

- **main**：稳定发布分支，仅接受来自 `develop` 或已发布版本的合并。
- **develop**：日常开发分支，功能开发完成后合并至此。
- **feature/xxx**：功能开发分支，从 `develop` 检出，完成后合并回 `develop`。
- **bugfix/xxx**：Bug 修复分支，从 `develop` 检出，完成后合并回 `develop`。
- **hotfix/xxx**（可选）：生产环境紧急修复分支，从 `main` 检出，完成后同时合并回 `main` 与 `develop`。

```
main    ─────────────────────────────────────
              ↘       ↑                  ↑
develop  ──────●──────●──────────────────●──
                  ↘  /                  /
feature/xxx        ●───────────────────/
```

---

## Commit 规范

本项目遵循 [Conventional Commits](https://www.conventionalcommits.org/zh-hans/v1.0.0/) 规范，提交信息格式如下：

```
<type>(<scope>): <subject>

<body>

<footer>
```

常用 `type` 类型：

| 类型 | 说明 |
|------|------|
| `feat` | 新增功能 |
| `fix` | 修复 Bug |
| `docs` | 文档相关变更 |
| `style` | 代码格式调整，不影响功能逻辑 |
| `refactor` | 代码重构 |
| `perf` | 性能优化 |
| `test` | 测试相关变更 |
| `chore` | 构建、工具、依赖等杂项变更 |

示例：

```
feat(auth): 新增登录错误锁定机制

- 登录失败 5 次后自动锁定账号 30 分钟
- 锁定期间拒绝登录并返回明确提示
```

---

## PR 流程

参与代码贡献的标准流程如下：

1. **Fork 仓库**：将项目 Fork 到你的 GitHub 账号下。
2. **创建分支**：基于 `develop` 分支创建 `feature/xxx` 或 `bugfix/xxx` 分支。
3. **本地开发**：按照代码规范完成开发，并确保本地测试通过。
4. **提交 Commit**：遵循 Conventional Commits 规范提交代码。
5. **同步上游**：发起 PR 前，建议先同步上游 `develop` 分支并解决冲突。
6. **发起 Pull Request**：将分支推送到自己的 Fork 仓库，然后向本项目的 `develop` 分支发起 PR。
7. **代码 Review**：维护者将对 PR 进行 Review，可能提出修改意见，请及时响应。
8. **合并代码**：Review 通过且 CI 检查全部通过后，由维护者合并到 `develop` 分支。

---

## Issue 提交指南

为了让我们更高效地处理你的 Issue，请遵守以下约定：

- 提交前请先搜索是否已有重复 Issue。
- 请使用对应的 Issue 模板填写。
- 标题应简洁明了，概括问题核心。
- 正文应包含充分上下文、复现步骤、期望结果与实际结果。
- 如涉及安全问题，请勿在公开 Issue 中披露敏感信息，请通过项目维护者邮箱私下联系。

---

再次感谢你的贡献！
