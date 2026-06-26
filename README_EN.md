[中文](README.md) | English

# DW MCP AI Permission Center

DW MCP AI Permission Center is an enterprise-grade AI tool access control middleware built exclusively for the Model Context Protocol (MCP) and grounded on the standard RBAC permission model.
It comes with built-in JDBC data source proxy and HTTP interface proxy capabilities, enabling one-click conversion of database SQL statements and business HTTP interfaces into standardized MCP tools. It delivers unified access authentication, tool visibility isolation, and multi-data-source access permission interception for Cursor, Claude Desktop, and all self-developed AI Agent clients. It addresses core pain points in a one-stop manner—including chaotic permissions, unauthorized data access, unregulated secret key management, and lack of hierarchical tool governance—arising from multiple users and AI clients sharing MCP tools.

## Feature Overview

- **RBAC Dynamic Tool List**: Different roles are bound to different MCP tools. After API Key authentication, users only see tools authorized by their roles.
- **JWT + API Key Dual-Channel Authentication**: Management side JWT login + MCP side API Key Bearer Token, with fully isolated paths.
- **User Self-Service**: Any authenticated user can update profile and change password (old password verification required), without ADMIN permission.
- **Role-Based Hierarchical Views**: Admins see all data; regular users only see tools and corresponding categories they have access to, with isolated endpoints to avoid permission denial.
- **API Key Authentication**: Bearer Token format, compatible with mainstream MCP clients such as Claude Desktop and Cursor.
- **JDBC Tool Execution**: Dynamic multi-datasource switching via Dynamic-Datasource, executing SQL templates and returning JSON results.
- **HTTP Proxy Tools** (Planned): Proxy external HTTP interfaces as MCP tools.
- **Feign API Declarations**: Management interfaces are declared via OpenFeign for easy microservice invocation or Gateway proxy.
- **AI One-Click Generation**: Input an SQL template, and AI streams tool names, descriptions, and JSON Schema generation in real time via SSE.
- **Permission Caching**: Redis-based user permission cache + API Key validation cache, asynchronously cleared in batches when RBAC changes.
- **Multi-Machine Deployment**: Based on Redisson distributed locks to ensure concurrency safety for role assignment and other operations.
- **Full Logical Deletion**: All data is only marked as deleted, supporting recovery and auditing (except the `sys_role_tool` table, which uses physical deletion).

## Tech Stack

| Component | Version | Description |
|------|------|------|
| Java | 21 | Runtime environment |
| Spring Boot | 4.0.6 | Base framework |
| Spring AI | 2.0.0 | MCP Server implementation (Streamable HTTP transport) |
| Spring Security | 7.x | Stateless JWT + API Key dual-channel authentication |
| Spring Cloud OpenFeign | 2025.0.0 | Feign declarations for management interfaces |
| MyBatis-Plus | 3.5.16 | ORM + pagination + logical deletion |
| Dynamic-Datasource | 4.5.0 | Dynamic multi-datasource switching |
| FastJson2 | 2.0.58 | JSON serialization (Controller + service layers) |
| Redisson | 4.1.0 | Distributed lock + Redis client |
| JJWT | 0.12.6 | JWT generation and validation |
| MySQL | 8.0 | Production database |
| PostgreSQL / Oracle / SQL Server | - | Multi-datasource JDBC drivers |
| Hutool | 5.8.36 | Utility library |
| Vue 3 + Element Plus | - | Management frontend |
| Lombok | - | Code simplification |

## Project Structure

```
dw-mcp-ai-permission-center/              Parent POM (packaging=pom)
├── dw-mcp-permission-api/                 API declaration module (lightweight jar for consumers)
│   └── com.easyhome.api
│       ├── dto/                           Request DTOs (8: User, Role, ApiKey, Tool, Datasource, ToolCategory, UpdateProfile, ChangePassword)
│       ├── vo/                            Response VOs (8: Result, PageResult + 6 business VOs)
│       └── feign/                         Feign Client interfaces (6: User, Role, ApiKey, Tool, Datasource, ToolCategory)
├── dw-mcp-permission-server/              Service implementation module (Spring Boot entry point)
│   └── com.easyhome
│       ├── controller/                    Controllers (8: 6 Feign implementations + AuthController + call log query)
│       ├── service/                       Service interfaces 8 + Impl 8
│       ├── mapper/                        MyBatis-Plus Mappers (9)
│       ├── entity/                        Entities (BaseEntity + 9)
│       ├── config/                        Configuration classes (Security, MybatisPlus, MetaHandler, Async, Filter, Redisson)
│       ├── common/config/                 Global exception handler (standardized 400/403/404/500 responses)
│       ├── security/                      Dual-channel authentication (JwtAuthFilter + JwtAuthenticationToken + ApiKeyAuthFilter + ApiKeyAuthenticationToken)
│       ├── mcp/                           MCP core (McpToolRegistry + JdbcToolExecutor + events/filters/sync)
│       │   ├── event/                     Event-driven (RbacChangeEvent + McpToolChangeEvent + async listeners)
│       │   └── filter/                    MCP tool list RBAC filtering
│       ├── ai/                            AI one-click tool configuration generation (streaming SSE + sync)
│       └── App.java                       Startup class
├── dw-mcp-permission-ui/                  Vue 3 + Element Plus management frontend (standalone frontend project)
└── pom.xml
```

## Database Design

Database name: `dw_mcp_permission`, 9 tables in total:

```
sys_user ──1:N── sys_user_role ──N:1── sys_role ──1:N── sys_role_tool ──N:1── mcp_tool ──N:1── mcp_tool_category
    │                                                                  │
    └──1:N── sys_api_key                                               │
                                                                       └──1:N── mcp_tool_call_log
                                                 mcp_datasource
```

| Table | Description | Common Fields |
|------|------|---------|
| `sys_user` | System user | All (status/is_deleted/creator/modifier/time) |
| `sys_role` | System role | All |
| `sys_user_role` | User-role association | is_deleted/creator/time (logical deletion) |
| `sys_api_key` | User API Key | All |
| `mcp_datasource` | MCP datasource configuration | All |
| `mcp_tool_category` | MCP tool category | is_deleted/creator/modifier/time (no status) |
| `mcp_tool` | MCP tool definition | All |
| `sys_role_tool` | Role-tool association | creator/time (physical deletion, no is_deleted) |
| `mcp_tool_call_log` | MCP tool call log | Only id + business fields (does not inherit BaseEntity) |

Common field conventions:
- `status`: 1=enabled, 2=disabled
- `is_deleted`: 0=not deleted, 1=deleted (global logical deletion, physical deletion is prohibited; `sys_role_tool` is an exception and uses physical deletion)
- `creator_id` / `creator`: Creator (auto-filled by MetaHandler from SecurityContext)
- `modifier_id` / `modifier`: Modifier (same as above)
- `created_at` / `updated_at`: Timestamps (auto-maintained by the database)

## Quick Start

### Requirements

- JDK 21
- MySQL 8.0+
- Redis 6.0+
- Node.js 18+ (frontend build)
- Maven 3.8+

### 1. Initialize the Database

```sql
-- Execute DDL + initialization data
source dw-mcp-permission-server/src/main/resources/db/schema.sql;
```

Initialization data includes:
- 1 default tool category: `other`
- 3 preset roles: `ADMIN`, `DATA_ANALYST`, `READONLY`
- 1 admin user: `admin`
- 1 demo API Key: `sk-demo-admin-key-for-testing-only`

### 2. Configure Environment Variables

Copy `.env.example` to `.env` and modify according to your environment:

```bash
cp .env.example .env
# Edit .env to fill in database, Redis, JWT secret, etc.
```

See [.env.example](.env.example) for all configuration options. Key items include:

| Configuration | Description |
|---------|------|
| `MYSQL_HOST` / `MYSQL_USER` / `MYSQL_PASSWORD` | Database connection |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | Redis connection |
| `JWT_SECRET` | JWT signing secret (must be changed to a strong random string in production) |
| `CORS_ALLOWED_ORIGINS` | Allowed frontend origins (comma-separated) |
| `MAIL_*` | SMTP mail configuration (for forgot-password verification codes) |
| `OPENAI_API_KEY` / `OPENAI_BASE_URL` / `OPENAI_MODEL` | AI model configuration |

> You can also edit `application.yml` directly or create `application-dev.yml` to override defaults.

### 3. Build and Start

```bash
# Backend
mvn clean package -DskipTests
java -jar dw-mcp-permission-server/target/dw-mcp-permission-server-1.0.0.jar
```

The service listens on `http://localhost:8091` by default.

```bash
# Frontend (development mode)
cd dw-mcp-permission-ui
npm install
npm run dev
```

The frontend listens on `http://localhost:5173` by default, proxying API requests to the backend via Vite.

## API Interfaces

### MCP Protocol Endpoints

| Endpoint | Description |
|------|------|
| `POST /mcp` | MCP Streamable HTTP endpoint (Claude Desktop / Cursor connection URL) |

MCP client configuration example (Claude Desktop `claude_desktop_config.json`):

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

### Management Interfaces

All management interface paths start with `/api/` and require JWT authentication; most interfaces require the ADMIN role.

| Module | Path Prefix | Feign Client | Description |
|------|---------|-------------|------|
| Authentication | `/api/auth` | None | JWT login / user info |
| User Management | `/api/users` | `SysUserClient` | CRUD + role assignment (ADMIN) |
| User Self-Service | `/api/users/me/*` | `SysUserClient` | Update profile / change password (any authenticated user) |
| Role Management | `/api/roles` | `SysRoleClient` | CRUD + tool authorization (ADMIN) |
| API Key Management | `/api/api-keys` | `SysApiKeyClient` | Generate / enable / disable / delete |
| MCP Tool Management | `/api/mcp/tools` | `McpToolClient` | CRUD + enable/disable (ADMIN) |
| MCP Tools (Mine) | `/api/mcp/tools/mine` | `McpToolClient` | Enabled tools accessible by current user (any authenticated user) |
| MCP Tool Categories | `/api/mcp/tool-categories` | `McpToolCategoryClient` | CRUD (ADMIN) |
| MCP Tool Categories (Mine) | `/api/mcp/tool-categories/mine` | `McpToolCategoryClient` | Categories of current user's tools (any authenticated user) |
| MCP Datasource Management | `/api/mcp/datasources` | `McpDatasourceClient` | CRUD + connectivity test (ADMIN) |
| AI Tool Generation | `/api/ai` | None | Streaming / sync AI generation of tool configuration (ADMIN) |
| Call Log Query | `/api/mcp/tool-call-logs` | None | Paginated query of tool call records |

### Microservice Invocation

Other services only need to depend on the `api` module to call via Feign:

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

## Authentication Mechanism

### Dual-Channel Authentication

| Channel | Path | Authentication | Filter |
|------|------|---------|--------|
| Management | `/api/**` | JWT (account/password login) | `JwtAuthFilter` |
| MCP | `/mcp/**` | API Key (Bearer Token) | `ApiKeyAuthFilter` |

The two filters isolate paths through `shouldNotFilter`, without interfering with each other.

### JWT Authentication Flow

```
1. User logs in with account and password → AuthController issues JWT Token
2. Frontend requests with Header: Authorization: Bearer <jwt-token>
3. JwtAuthFilter intercepts → verifies signature + expiration → builds JwtAuthenticationToken
4. SecurityContext stores authentication info (userId/username/roles)
```

### API Key Authentication Flow

```
1. MCP client requests with Header: Authorization: Bearer sk-xxxx
2. ApiKeyAuthFilter intercepts → validates Key from cache/database
3. After validation → loads user info + role list → builds ApiKeyAuthenticationToken
4. SecurityContext stores authentication info → subsequent RBAC queries use it
```

### Permission Control

- `/api/auth/login` — Public access
- `/api/**` — Requires JWT authentication; method-level role validation via `@PreAuthorize`
  - `@PreAuthorize("hasRole('ADMIN')")` — Requires ADMIN role (user/role/tool management, etc.)
  - `@PreAuthorize("isAuthenticated()")` — Any authenticated user (/mine endpoints, profile/password modification)
- `/mcp/**` — Requires API Key authentication; tool list is dynamically filtered by user role
- `/actuator/health` — Public access

### Global Exception Handling

| Exception Type | HTTP Status | Scenario |
|---------|-----------|------|
| `IllegalArgumentException` | 400 | Business parameter validation failure |
| `MethodArgumentNotValidException` | 400 | @Validated validation failure |
| `AuthorizationDeniedException` | 403 | @PreAuthorize permission denied |
| `NoResourceFoundException` | 404 | Request path does not exist |
| Other `Exception` | 500 | Uncaught exception fallback |

## RBAC Tool Dynamic Filtering

Filtering chain when MCP client calls `tools/list`:

```
User API Key → Locate user → Query user roles → Query tools authorized by roles → Return tool list
```

Core implementation: `McpToolsListFilter` → `McpToolPermissionService.getAccessibleToolNames()` → `McpToolMapper.selectToolsByUserId(userId)`

```sql
SELECT t.* FROM mcp_tool t
INNER JOIN sys_role_tool rt ON t.id = rt.tool_id
INNER JOIN sys_user_role ur ON rt.role_id = ur.role_id
WHERE ur.user_id = #{userId}
  AND t.status = 1 AND t.is_deleted = 0
  AND rt.is_deleted = 0 AND ur.is_deleted = 0
```

## JDBC Tool Execution Flow

```
1. MCP client calls tools/call, passing tool name + parameters
2. RbacMcpContextExtractor extracts userId from SecurityContext and injects it into TransportContext
3. McpToolRegistry.executeWithRbacCheck() validates RBAC permission
4. JdbcToolExecutor replaces parameters into #{param} placeholders in the SQL template
5. DatasourceRegistry.createConnection() creates a native JDBC connection
6. SqlPaginationUtil automatically appends pagination + executes COUNT query
7. JdbcTemplate.execute(sql) executes the query
8. FastJson2 serializes results to JSON and returns to AI
9. Call logs are asynchronously saved to mcp_tool_call_log
```
