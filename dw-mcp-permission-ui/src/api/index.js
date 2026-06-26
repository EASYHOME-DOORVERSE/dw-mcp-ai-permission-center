import request from './request'

// ========== 认证相关 ==========
export function getAuthInfo() { return request.get('/auth/info') }
export function logout() { return request.post('/auth/logout') }

// ========== 用户管理 ==========
export function listUsers(params) { return request.get('/users', { params }) }
export function getUser(id) { return request.get(`/users/${id}`) }
export function createUser(data) { return request.post('/users', data) }
export function updateUser(id, data) { return request.put(`/users/${id}`, data) }
export function deleteUser(id) { return request.delete(`/users/${id}`) }
export function updateUserStatus(id, status) { return request.put(`/users/${id}/status/${status}`) }
export function getUserRoles(id) { return request.get(`/users/${id}/roles`) }
export function assignUserRoles(id, roleIds) { return request.put(`/users/${id}/roles`, roleIds) }
export function resetUserPassword(id) { return request.put(`/users/${id}/reset-password`) }

// ========== 用户自助操作 ==========
export function getMyProfile() { return request.get('/users/me') }
export function updateMyProfile(data) { return request.put('/users/me/profile', data) }
export function changeMyPassword(data) { return request.put('/users/me/password', data) }

// ========== 角色管理 ==========
export function listRoles(params) { return request.get('/roles', { params }) }
export function getRole(id) { return request.get(`/roles/${id}`) }
export function createRole(data) { return request.post('/roles', data) }
export function updateRole(id, data) { return request.put(`/roles/${id}`, data) }
export function deleteRole(id) { return request.delete(`/roles/${id}`) }
export function getRoleTools(id) { return request.get(`/roles/${id}/tools`) }
export function assignRoleTools(id, toolIds) { return request.put(`/roles/${id}/tools`, toolIds) }

// ========== API Key 管理 ==========
export function listApiKeys(params) { return request.get('/api-keys', { params }) }
export function generateApiKey(data) { return request.post('/api-keys/generate', data) }
export function disableApiKey(id) { return request.put(`/api-keys/${id}/disable`) }
export function enableApiKey(id) { return request.put(`/api-keys/${id}/enable`) }
export function deleteApiKey(id) { return request.delete(`/api-keys/${id}`) }
export function getApiKey(id) { return request.get(`/api-keys/${id}`) }
export function updateApiKey(data) { return request.put('/api-keys', data) }

// ========== MCP 工具管理 ==========
export function listTools(params) { return request.get('/mcp/tools', { params }) }
export function listMyTools(params) { return request.get('/mcp/tools/mine', { params }) }
export function getTool(id) { return request.get(`/mcp/tools/${id}`) }
export function createTool(data) { return request.post('/mcp/tools', data) }
export function updateTool(id, data) { return request.put(`/mcp/tools/${id}`, data) }
export function deleteTool(id) { return request.delete(`/mcp/tools/${id}`) }
export function updateToolStatus(id, status) { return request.put(`/mcp/tools/${id}/status/${status}`) }

// ========== MCP 工具分类管理 ==========
export function listCategories() { return request.get('/mcp/tool-categories') }
export function listMyCategories() { return request.get('/mcp/tool-categories/mine') }
export function createCategory(data) { return request.post('/mcp/tool-categories', data) }
export function updateCategory(id, data) { return request.put(`/mcp/tool-categories/${id}`, data) }
export function deleteCategory(id) { return request.delete(`/mcp/tool-categories/${id}`) }

// ========== MCP 数据源管理 ==========
export function listDatasources(params) { return request.get('/mcp/datasources', { params }) }

// ========== AI 一键生成 ==========
export function generateToolConfig(data) { return request.post('/ai/tool-generate', data, { timeout: 300000 }) }

/**
 * AI 流式生成工具配置（SSE）
 * 因 EventSource 不支持自定义 header，使用 fetch + ReadableStream 实现。
 *
 * @param {object} data - 请求体 { sqlTemplate, datasourceKey }
 * @param {object} handlers - 事件处理器 { onThinking, onSchemaFetching, onResult, onError, onComplete }
 * @returns {function} abortFn - 调用后终止 SSE 连接
 */
export function generateToolConfigStream(data, handlers = {}) {
  const controller = new AbortController()
  const token = localStorage.getItem('admin_token')

  fetch('/api/ai/tool-generate-stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'text/event-stream',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: JSON.stringify(data),
    signal: controller.signal
  }).then(response => {
    if (!response.ok) {
      handlers.onError?.(`请求失败: ${response.status}`)
      handlers.onComplete?.()
      return
    }
    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    function dispatchEvents(text) {
      const parts = text.split('\n\n')
      for (let i = 0; i < parts.length - 1; i++) {
        const part = parts[i]
        if (!part.trim()) continue
        let eventName = 'message'
        let eventData = ''
        for (const line of part.split('\n')) {
          if (line.startsWith('event:')) {
            eventName = line.slice(6).trim()
          } else if (line.startsWith('data:')) {
            eventData = line.slice(5).trim()
          }
        }
        switch (eventName) {
          case 'thinking':
            handlers.onThinking?.(eventData.replace(/\\n/g, '\n').replace(/\\\\/g, '\\'))
            break
          case 'schema_fetching':
            handlers.onSchemaFetching?.(eventData)
            break
          case 'result':
            handlers.onResult?.(eventData)
            break
          case 'error':
            handlers.onError?.(eventData.replace(/\\n/g, '\n').replace(/\\\\/g, '\\'))
            break
        }
      }
      // 返回最后一个可能不完整的片段作为新 buffer
      return parts[parts.length - 1]
    }

    function read() {
      reader.read().then(({ done, value }) => {
        if (done) {
          // 流结束前，将 buffer 中剩余的完整事件全部派发
          if (buffer.trim()) {
            buffer = dispatchEvents(buffer + '\n\n')
          }
          handlers.onComplete?.()
          return
        }
        buffer += decoder.decode(value, { stream: true })
        buffer = dispatchEvents(buffer)
        read()
      }).catch(err => {
        if (err.name !== 'AbortError') {
          handlers.onError?.(err.message)
        }
        handlers.onComplete?.()
      })
    }
    read()
  }).catch(err => {
    if (err.name !== 'AbortError') {
      handlers.onError?.(err.message)
    }
    handlers.onComplete?.()
  })

  // 返回终止函数
  return () => controller.abort()
}
// ========== 看板 & 调用日志 ==========
export function getDashboardStats(params) { return request.get('/dashboard/stats', { params }) }
export function queryCallLogs(params) { return request.get('/dashboard/call-logs', { params }) }
export function manualAggregateStats(startDate, endDate) { return request.post('/dashboard/aggregate-stats', null, { params: { startDate, endDate } }) }

export function getDatasource(id) { return request.get(`/mcp/datasources/${id}`) }
export function createDatasource(data) { return request.post('/mcp/datasources', data) }
export function updateDatasource(id, data) { return request.put(`/mcp/datasources/${id}`, data) }
export function deleteDatasource(id) { return request.delete(`/mcp/datasources/${id}`) }
export function testDatasourceConnection(id) { return request.post(`/mcp/datasources/${id}/test`) }
