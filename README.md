中文 | [English](README_EN.md)

# DW MCP AI Permission Center

DW MCP AI Permission Center 是一套专为 Model Context Protocol（MCP）打造、基于标准 RBAC 权限模型的企业级 AI 工具权限管控中台。
内置 JDBC 数据源代理、HTTP 接口代理能力，可自动将数据库 SQL、业务 HTTP 接口一键转换为标准化 MCP 工具；面向 Cursor、Claude Desktop、各类自研 AI Agent 客户端提供统一接入鉴权、工具可见性隔离、多数据源访问权限拦截能力，一站式解决多用户、多 AI 客户端共用 MCP 工具时权限混乱、数据越权、密钥管理失控、工具无分级管控等核心痛点。

## 功能概述

- **RBAC 动态工具列表**：不同角色绑定不同 MCP 工具，用户通过 API Key 认证后仅看到其角色授权的工具
- **JWT + API Key 双通道认证**：管理端 JWT 登录 + MCP 端 API Key Bearer Token，路径完全隔离
- **用户自助管理**：任意已认证用户可修改个人信息、修改密码（需校验旧密码），无需 ADMIN 权限
- **角色分级视图**：管理员查看全部数据，普通用户仅看自己有权限的工具和对应分类，接口隔离避免权限拒绝
- **API Key 认证**：Bearer Token 方式，兼容 Claude Desktop、Cursor 等主流 MCP 客户端
- **JDBC 工具执行**：通过 Dynamic-Datasource 多数据源动态切换，执行 SQL 模版并返回 JSON 结果
- **HTTP Proxy 工具**（规划中）：代理外部 HTTP 接口作为 MCP 工具
- **Feign API 声明**：管理接口通过 OpenFeign 声明，方便微服务调用或 Gateway 代理
- **AI 一键生成**：输入 SQL 模板，AI 流式生成工具名称、描述和 JSON Schema，SSE 实时展示思考过程
- **权限缓存**：基于 Redis 的用户权限缓存 + API Key 验证缓存，RBAC 变更时异步批量清除
- **多机部署**：基于 Redisson 分布式锁保证角色分配等操作的并发安全
- **全逻辑删除**：所有数据仅标记删除，支持恢复和审计（`sys_role_tool` 表除外，该表使用物理删除）

## 技术栈
| 组件 | 版本 | 说明 |
|------|------|------|
| Java | 21 | 运行环境 |
| Spring Boot | 4.0.6 | 基础框架 |
| Spring AI | 2.0.0 | MCP Server 实现（Streamable HTTP 传输） |
| Spring Security | 7.x | 无状态 JWT + API Key 双通道认证 |
| Spring Cloud OpenFeign | 2025.0.0 | 管理接口 Feign 声明 |
| MyBatis-Plus | 3.5.16 | ORM + 分页 + 逻辑删除 |
| Dynamic-Datasource | 4.5.0 | 多数据源动态切换 |
| FastJson2 | 2.0.58 | JSON 序列化（Controller 层 + 业务层） |
| Redisson | 4.1.0 | 分布式锁 + Redis 客户端 |
| JJWT | 0.12.6 | JWT 生成与验证 |
| MySQL | 8.0 | 生产数据库 |
| PostgreSQL / Oracle / SQL Server | - | 多数据源 JDBC 驱动 |
| Hutool | 5.8.36 | 工具类 |
| Vue 3 + Element Plus | - | 管理后台前端 |
| Lombok | - | 代码简化 |

## 项目结构

```
dw-mcp-ai-permission-center/              父 POM (packaging=pom)
├── dw-mcp-permission-api/                 API 声明模块（轻量 jar，供调用方依赖）
│   └── com.easyhome.api
│       ├── dto/                           请求 DTO（8 个：User、Role、ApiKey、Tool、Datasource、ToolCategory、UpdateProfile、ChangePassword）
│       ├── vo/                            响应 VO（8 个：Result、PageResult + 6 个业务 VO）
│       └── feign/                         Feign Client 接口（6 个：User、Role、ApiKey、Tool、Datasource、ToolCategory）
├── dw-mcp-permission-server/              服务实现模块（Spring Boot 启动入口）
│   └── com.easyhome
│       ├── controller/                    Controller（8 个：6 个 Feign 接口实现 + AuthController + 调用日志查询）
│       ├── service/                       Service 接口 8 个 + Impl 8 个
│       ├── mapper/                        MyBatis-Plus Mapper（9 个）
│       ├── entity/                        实体类（BaseEntity + 9 个）
│       ├── config/                        配置类（Security、MybatisPlus、MetaHandler、Async、Filter、Redisson）
│       ├── common/config/                 全局异常处理器（400/403/404/500 标准化响应）
│       ├── security/                      双通道认证（JwtAuthFilter + JwtAuthenticationToken + ApiKeyAuthFilter + ApiKeyAuthenticationToken）
│       ├── mcp/                           MCP 核心（McpToolRegistry + JdbcToolExecutor + 事件/过滤器/同步）
│       │   ├── event/                     事件驱动（RbacChangeEvent + McpToolChangeEvent + 异步监听器）
│       │   └── filter/                    MCP 工具列表 RBAC 过滤
│       ├── ai/                            AI 一键生成工具配置（流式 SSE + 同步）
│       └── App.java                       启动类
├── dw-mcp-permission-ui/                  Vue 3 + Element Plus 管理后台（独立前端项目）
└── pom.xml
```

## 数据库设计

数据库名：`dw_mcp_permission`，共 9 张表：

```
sys_user ──1:N── sys_user_role ──N:1── sys_role ──1:N── sys_role_tool ──N:1── mcp_tool ──N:1── mcp_tool_category
    │                                                                  │
    └──1:N── sys_api_key                                               │
                                                                       └──1:N── mcp_tool_call_log
                                                 mcp_datasource
```

| 表名 | 说明 | 公共字段 |
|------|------|---------|
| `sys_user` | 系统用户 | 全部（status/is_deleted/creator/modifier/time） |
| `sys_role` | 系统角色 | 全部 |
| `sys_user_role` | 用户-角色关联 | is_deleted/creator/time（逻辑删除） |
| `sys_api_key` | 用户 API Key | 全部 |
| `mcp_datasource` | MCP 数据源配置 | 全部 |
| `mcp_tool_category` | MCP 工具分类 | is_deleted/creator/modifier/time（无 status） |
| `mcp_tool` | MCP 工具定义 | 全部 |
| `sys_role_tool` | 角色-工具关联 | creator/time（物理删除，无 is_deleted） |
| `mcp_tool_call_log` | MCP 工具调用日志 | 仅 id + 业务字段（不继承 BaseEntity） |

公共字段约定：
- `status`：1=启用，2=停用
- `is_deleted`：0=未删除，1=已删除（全局逻辑删除，禁止物理删除；`sys_role_tool` 表例外，使用物理删除）
- `creator_id` / `creator`：创建人（MetaHandler 从 SecurityContext 自动填充）
- `modifier_id` / `modifier`：修改人（同上）
- `created_at` / `updated_at`：时间戳（数据库自动维护）

## 快速开始

### 环境要求

- JDK 21
- MySQL 8.0+
- Redis 6.0+
- Node.js 18+（前端构建）
- Maven 3.8+

### 1. 初始化数据库

```sql
-- 执行 DDL + 初始化数据
source dw-mcp-permission-server/src/main/resources/db/schema.sql;
```

初始化数据包含：
- 1 个默认工具分类：`other`
- 3 个预置角色：`ADMIN`、`DATA_ANALYST`、`READONLY`
- 1 个管理员用户：`admin`
- 1 个演示 API Key：`sk-demo-admin-key-for-testing-only`

### 2. 配置环境变量

复制 `.env.example` 为 `.env`，根据实际环境修改：

```bash
cp .env.example .env
# 编辑 .env 填写数据库、Redis、JWT 密钥等配置
```

完整配置项参见 [.env.example](.env.example)，主要包含：

| 配置项 | 说明 |
|---------|------|
| `MYSQL_HOST` / `MYSQL_USER` / `MYSQL_PASSWORD` | 数据库连接 |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | Redis 连接 |
| `JWT_SECRET` | JWT 签名密钥（生产环境必须修改为强随机字符串） |
| `CORS_ALLOWED_ORIGINS` | CORS 允许的前端域名（多个用逗号分隔） |
| `MAIL_*` | SMTP 邮件配置（忘记密码验证码） |
| `OPENAI_API_KEY` / `OPENAI_BASE_URL` / `OPENAI_MODEL` | AI 模型配置 |

> 也可以直接编辑 `application.yml` 或创建 `application-dev.yml` 覆盖默认值。

### 3. 编译启动

```bash
# 后端
mvn clean package -DskipTests
java -jar dw-mcp-permission-server/target/dw-mcp-permission-server-1.0.0.jar
```

服务默认监听 `http://localhost:8091`。

```bash
# 前端（开发模式）
cd dw-mcp-permission-ui
npm install
npm run dev
```

前端默认监听 `http://localhost:5173`，通过 Vite 代理访问后端接口。

## API 接口

### MCP 协议端点

| 端点 | 说明 |
|------|------|
| `POST /mcp` | MCP Streamable HTTP 端点（Claude Desktop / Cursor 连接地址） |

MCP 客户端配置示例（Claude Desktop `claude_desktop_config.json`）：

```json
{
  "mcpServers": {
    "mcp-permission-center": {
      "url": "http://localhost:8091/mcp",
      "headers": {
        "Authorization": "Bearer sk-your-api-key-here"
      }
    }
  }
}
```

### 管理接口

所有管理接口路径以 `/api/` 开头，需要 JWT 认证；大部分接口需要 ADMIN 角色。

| 模块 | 路径前缀 | Feign Client | 说明 |
|------|---------|-------------|------|
| 认证 | `/api/auth` | 无 | JWT 登录/用户信息 |
| 用户管理 | `/api/users` | `SysUserClient` | CRUD + 角色分配（ADMIN） |
| 用户自助 | `/api/users/me/*` | `SysUserClient` | 修改个人信息/修改密码（任意已认证用户） |
| 角色管理 | `/api/roles` | `SysRoleClient` | CRUD + 工具授权（ADMIN） |
| API Key 管理 | `/api/api-keys` | `SysApiKeyClient` | 生成/启用/停用/删除 |
| MCP 工具管理 | `/api/mcp/tools` | `McpToolClient` | CRUD + 启停（ADMIN） |
| MCP 工具（我的） | `/api/mcp/tools/mine` | `McpToolClient` | 当前用户可访问的启用工具（任意已认证用户） |
| MCP 工具分类 | `/api/mcp/tool-categories` | `McpToolCategoryClient` | CRUD（ADMIN） |
| MCP 工具分类（我的） | `/api/mcp/tool-categories/mine` | `McpToolCategoryClient` | 当前用户工具所属分类（任意已认证用户） |
| MCP 数据源管理 | `/api/mcp/datasources` | `McpDatasourceClient` | CRUD + 连通性测试（ADMIN） |
| AI 工具生成 | `/api/ai` | 无 | 流式/同步 AI 生成工具配置（ADMIN） |
| 调用日志查询 | `/api/mcp/tool-call-logs` | 无 | 分页查询工具调用记录 |

### 微服务调用

其他服务只需依赖 api 模块即可通过 Feign 调用：

```xml
<dependency>
    <groupId>com.easyhome</groupId>
    <artifactId>dw-mcp-permission-api</artifactId>
    <version>1.0.0</version>
</dependency>
```

```java
@EnableFeignClients(basePackages = "com.easyhome.api.feign")
@SpringBootApplication
public class YourApplication { }

@RestController
@RequiredArgsConstructor
public class YourController {
    private final SysUserClient sysUserClient;

    public Result<SysUserVO> getUser(Long id) {
        return sysUserClient.getById(id);
    }
}
```

## 认证机制

### 双通道认证

| 通道 | 路径 | 认证方式 | Filter |
|------|------|---------|--------|
| 管理端 | `/api/**` | JWT（账号密码登录） | `JwtAuthFilter` |
| MCP 端 | `/mcp/**` | API Key（Bearer Token） | `ApiKeyAuthFilter` |

两个 Filter 通过 `shouldNotFilter` 实现路径隔离，互不干扰。

### JWT 认证流程

```
1. 用户账号密码登录 → AuthController 签发 JWT Token
2. 前端请求 Header: Authorization: Bearer <jwt-token>
3. JwtAuthFilter 拦截 → 验证签名 + 过期时间 → 构建 JwtAuthenticationToken
4. SecurityContext 存入认证信息（userId/username/roles）
```

### API Key 认证流程

```
1. MCP 客户端请求 Header: Authorization: Bearer sk-xxxx
2. ApiKeyAuthFilter 拦截 → 从缓存/数据库验证 Key
3. 验证通过 → 加载用户信息 + 角色列表 → 构建 ApiKeyAuthenticationToken
4. SecurityContext 存入认证信息 → 后续 RBAC 查询使用
```

### 权限控制

- `/api/auth/login` — 公开访问
- `/api/**` — 需要 JWT 认证，方法级别通过 `@PreAuthorize` 做角色校验
  - `@PreAuthorize("hasRole('ADMIN')")` — 需要 ADMIN 角色（用户/角色/工具管理等）
  - `@PreAuthorize("isAuthenticated()")` — 任意已认证用户（/mine 端点、个人信息/密码修改）
- `/mcp/**` — 需要 API Key 认证，工具列表根据用户角色动态过滤
- `/actuator/health` — 公开访问

### 全局异常处理

| 异常类型 | HTTP 状态码 | 场景 |
|---------|-----------|------|
| `IllegalArgumentException` | 400 | 业务参数校验失败 |
| `MethodArgumentNotValidException` | 400 | @Validated 校验失败 |
| `AuthorizationDeniedException` | 403 | @PreAuthorize 权限拒绝 |
| `NoResourceFoundException` | 404 | 请求路径不存在 |
| 其他 `Exception` | 500 | 未捕获异常兜底 |

## RBAC 工具动态过滤

MCP 客户端调用 `tools/list` 时的过滤链路：

```
用户 API Key → 定位用户 → 查询用户角色 → 查询角色授权的工具 → 返回工具列表
```

核心实现：`McpToolsListFilter` → `McpToolPermissionService.getAccessibleToolNames()` → `McpToolMapper.selectToolsByUserId(userId)`

```sql
SELECT t.* FROM mcp_tool t
INNER JOIN sys_role_tool rt ON t.id = rt.tool_id
INNER JOIN sys_user_role ur ON rt.role_id = ur.role_id
WHERE ur.user_id = #{userId}
  AND t.status = 1 AND t.is_deleted = 0
  AND rt.is_deleted = 0 AND ur.is_deleted = 0
```

## JDBC 工具执行流程

```
1. MCP 客户端调用 tools/call，传入工具名 + 参数
2. RbacMcpContextExtractor 从 SecurityContext 提取 userId 注入 TransportContext
3. McpToolRegistry.executeWithRbacCheck() 校验 RBAC 权限
4. JdbcToolExecutor 将参数替换到 SQL 模板的 #{param} 占位符
5. DatasourceRegistry.createConnection() 创建原生 JDBC 连接
6. SqlPaginationUtil 自动追加分页 + 执行 COUNT 查询
7. JdbcTemplate.execute(sql) 执行查询
8. FastJson2 序列化结果为 JSON 返回给 AI
9. 异步保存调用日志到 mcp_tool_call_log
```
