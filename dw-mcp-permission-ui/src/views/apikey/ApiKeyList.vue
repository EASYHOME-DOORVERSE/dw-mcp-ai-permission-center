<template>
  <div class="page-container">
    <el-card class="search-card" shadow="never">
      <el-form :model="query" inline>
        <el-form-item v-if="isAdmin" label="用户">
          <el-select v-model="query.userId" placeholder="全部" clearable filterable style="width:160px" @change="loadData">
            <el-option v-for="u in allUsers" :key="u.id" :label="`${u.username}${u.nickname ? '（' + u.nickname + '）' : ''}`" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width:120px">
            <el-option label="启用" :value="1" /><el-option label="停用" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadData">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" style="margin-top:12px">
      <div class="table-toolbar">
        <el-button type="primary" @click="openGenerate"><el-icon><Plus /></el-icon> 生成 Key</el-button>
      </div>
      <el-table :data="tableData" v-loading="loading" stripe border>
        <el-table-column type="index" label="序号" width="60" />
        <el-table-column v-if="isAdmin" prop="userId" label="用户" width="200">
          <template #default="{ row }">
            {{ userLabel(row.userId) }}
          </template>
        </el-table-column>
        <el-table-column prop="keyName" label="名称" width="150" />
        <el-table-column prop="accountId" label="账户ID" width="150">
          <template #default="{ row }">
            {{ row.accountId || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="apiKey" label="API Key" min-width="200">
          <template #default="{ row }">
            <code class="key-code">{{ row.apiKey }}</code>
          </template>
        </el-table-column>
        <el-table-column prop="expiredAt" label="过期时间" width="170">
          <template #default="{ row }">{{ row.expiredAt || '永不过期' }}</template>
        </el-table-column>
        <el-table-column prop="lastUsedAt" label="最近使用" width="170" />
        <el-table-column prop="status" label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="copyMcpConfig(row)">复制配置</el-button>
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link :type="row.status === 1 ? 'warning' : 'success'"
              @click="toggleKey(row)">{{ row.status === 1 ? '停用' : '启用' }}</el-button>
            <el-popconfirm title="确定删除？" @confirm="handleDelete(row.id)">
              <template #reference><el-button link type="danger">删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="table-pagination" background layout="total, sizes, prev, pager, next"
        :total="total" v-model:current-page="query.current" v-model:page-size="query.size"
        :page-sizes="[10,20,50]" @change="loadData" />
    </el-card>

    <!-- 生成 Key 弹窗 -->
    <el-dialog v-model="genVisible" title="生成 API Key" width="500px" destroy-on-close>
      <el-form :model="genForm" label-width="80px" ref="genFormRef" :rules="genRules">
        <el-form-item v-if="isAdmin" label="用户" prop="userId">
          <el-select v-model="genForm.userId" placeholder="请选择用户" filterable style="width:100%">
            <el-option
              v-for="u in allUsers"
              :key="u.id"
              :label="`${u.username}${u.nickname ? '（' + u.nickname + '）' : ''}`"
              :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="名称" prop="keyName"><el-input v-model="genForm.keyName" placeholder="如：开发环境Key" /></el-form-item>
        <el-form-item label="账户ID"><el-input v-model="genForm.accountId" placeholder="用于SQL模板参数自动注入" /></el-form-item>
        <el-form-item label="过期时间"><el-date-picker v-model="genForm.expiredAt" type="datetime" placeholder="留空永不过期" style="width:100%" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="genVisible = false">取消</el-button>
        <el-button type="primary" @click="submitGenerate">生成</el-button>
      </template>
    </el-dialog>

    <!-- 编辑 Key 弹窗 -->
    <el-dialog v-model="editVisible" title="编辑 API Key" width="500px" destroy-on-close>
      <el-form :model="editForm" label-width="80px">
        <el-form-item label="用户">
          <el-select v-model="editForm.userId" disabled style="width:100%">
            <el-option v-for="u in allUsers" :key="u.id" :label="`${u.username}${u.nickname ? '（' + u.nickname + '）' : ''}`" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="名称"><el-input v-model="editForm.keyName" placeholder="如：开发环境Key" /></el-form-item>
        <el-form-item label="账户ID"><el-input v-model="editForm.accountId" placeholder="用于SQL模板参数自动注入" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 新 Key 展示弹窗 -->
    <el-dialog v-model="resultVisible" title="API Key 已生成" width="520px" :close-on-click-modal="false">
      <el-alert type="warning" :closable="false" show-icon style="margin-bottom:16px">
        <template #title>请立即复制保存，关闭后无法再次查看完整 Key</template>
      </el-alert>
      <el-input :model-value="generatedKey" readonly>
        <template #append>
          <el-button @click="copyKey">复制</el-button>
        </template>
      </el-input>
      <template #footer>
        <el-button type="primary" @click="resultVisible = false">我已保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listApiKeys, generateApiKey, getApiKey, disableApiKey, enableApiKey, deleteApiKey, listUsers ,updateApiKey} from '../../api'

const MCP_SERVER_URL = import.meta.env.VITE_MCP_SERVER_URL || window.location.origin
const isAdmin = (() => {
  try { return JSON.parse(localStorage.getItem('admin_roles') || '[]').includes('ADMIN') } catch { return false }
})()
const currentUserId = (() => {
  const id = localStorage.getItem('admin_userId')
  return id ? Number(id) : null
})()

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const query = reactive({ current: 1, size: 20, userId: undefined, status: undefined })

const allUsers = ref([])

const genVisible = ref(false)
const genForm = reactive({ userId: null, keyName: '', accountId: '', expiredAt: null })
const genFormRef = ref()
const genRules = computed(() => ({
  userId: isAdmin ? [{ required: true, message: '请选择用户', trigger: 'change' }] : [],
  keyName: [{ required: true, message: '请输入名称', trigger: 'blur' }]
}))

const editVisible = ref(false)
const editForm = reactive({ id: null, userId: null, keyName: '', accountId: '' })

const resultVisible = ref(false)
const generatedKey = ref('')

async function loadData() {
  loading.value = true
  try {
    const params = { ...query }
    if (params.userId === '') params.userId = undefined
    const res = await listApiKeys(params)
    tableData.value = res.data.records; total.value = res.data.total
  } finally { loading.value = false }
}

async function loadUsers() {
  if (!isAdmin) return // 非 admin 不需要加载用户列表
  const res = await listUsers({ current: 1, size: 999 })
  allUsers.value = res.data.records || []
}

function resetQuery() { query.userId = undefined; query.status = undefined; query.current = 1; loadData() }

function openGenerate() {
  // 非 admin 用户默认填入自己的 userId
  Object.assign(genForm, { userId: isAdmin ? null : currentUserId, keyName: '', accountId: '', expiredAt: null })
  genVisible.value = true
}

async function submitGenerate() {
  await genFormRef.value.validate()
  const res = await generateApiKey(genForm)
  generatedKey.value = res.data.apiKey
  genVisible.value = false
  resultVisible.value = true
  loadData()
}

function openEdit(row) {
  Object.assign(editForm, { id: row.id, userId: row.userId, keyName: row.keyName, accountId: row.accountId || '' })
  editVisible.value = true
}

async function submitEdit() {
  await updateApiKey(editForm)
  ElMessage.success('更新成功')
  editVisible.value = false
  loadData()
}

async function toggleKey(row) {
  if (row.status === 1) { await disableApiKey(row.id); ElMessage.success('已停用') }
  else { await enableApiKey(row.id); ElMessage.success('已启用') }
  loadData()
}

async function handleDelete(id) { await deleteApiKey(id); ElMessage.success('删除成功'); loadData() }

async function copyKey() {
  const text = generatedKey.value
  if (!text) return
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text)
      ElMessage.success('已复制')
      return
    }
  } catch (e) { /* 降级 */ }
  const textarea = document.createElement('textarea')
  textarea.value = text
  textarea.setAttribute('readonly', '')
  textarea.style.position = 'fixed'
  textarea.style.opacity = '0'
  document.body.appendChild(textarea)
  textarea.focus()
  textarea.select()
  try {
    document.execCommand('copy') ? ElMessage.success('已复制') : ElMessage.error('复制失败，请手动复制')
  } catch { ElMessage.error('复制失败，请手动复制') }
  finally { document.body.removeChild(textarea) }
}

async function copyMcpConfig(row) {
  try {
    const res = await getApiKey(row.id)
    const fullKey = res.data.apiKey
    const config = {
      mcpServers: {
        'dw-mcp-permission-server': {
          type: 'http',
          url: MCP_SERVER_URL + '/mcp',
          headers: { Authorization: 'Bearer ' + fullKey },
          timeout: 60000
        }
      }
    }
    const text = JSON.stringify(config, null, 2)
    if (window.isSecureContext && navigator.clipboard) {
      await navigator.clipboard.writeText(text)
      ElMessage.success('MCP 配置已复制到剪贴板')
    } else {
      const textarea = document.createElement('textarea')
      textarea.value = text
      textarea.style.position = 'fixed'
      textarea.style.opacity = '0'
      document.body.appendChild(textarea)
      textarea.focus()
      textarea.select()
      try {
        document.execCommand('copy') ? ElMessage.success('MCP 配置已复制到剪贴板') : ElMessage.error('复制失败，请手动复制')
      } catch { ElMessage.error('复制失败，请手动复制') }
      finally { document.body.removeChild(textarea) }
    }
  } catch {
    ElMessage.error('复制失败，请重试')
  }
}

function userLabel(userId) {
  const u = allUsers.value.find(u => u.id === userId)
  if (!u) return userId
  return u.nickname ? `${u.username}（${u.nickname}）` : u.username
}

onMounted(() => {
  // 非 admin 用户自动按自己 ID 过滤（后端也会强制过滤，这里做前端默认值）
  if (!isAdmin && currentUserId) {
    query.userId = currentUserId
  }
  loadUsers()
  loadData()
})
</script>

<style scoped>
.page-container { height: 100%; }
.search-card :deep(.el-card__body) { padding-bottom: 0; }
.table-toolbar { margin-bottom: 12px; }
.table-pagination { margin-top: 16px; justify-content: flex-end; }
.key-code { font-size: 12px; color: #606266; word-break: break-all; }
</style>
