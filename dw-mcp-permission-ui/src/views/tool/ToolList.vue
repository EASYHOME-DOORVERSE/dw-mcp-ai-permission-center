<template>
  <div class="page-container">
    <el-card class="search-card" shadow="never">
      <el-form :model="query" inline>
        <el-form-item label="工具名">
          <el-input v-model="query.toolName" placeholder="工具名/显示名称" clearable @keyup.enter="loadData" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="query.categoryId" placeholder="全部" clearable style="width:140px">
            <el-option v-for="c in allCategories" :key="c.id" :label="c.categoryName" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="query.toolType" placeholder="全部" clearable style="width:130px">
            <el-option label="JDBC" value="JDBC" /><el-option label="HTTP_PROXY" value="HTTP_PROXY" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="isAdmin" label="状态">
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
        <el-button v-if="isAdmin" type="primary" @click="openForm(null)"><el-icon><Plus /></el-icon> 新增工具</el-button>
      </div>
      <el-table :data="tableData" v-loading="loading" stripe border>
        <el-table-column type="index" label="序号" width="60" />
        <el-table-column prop="toolName" label="工具名" width="200" />
        <el-table-column prop="toolType" label="类型" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="row.toolType === 'JDBC' ? '' : 'warning'" size="small">{{ row.toolType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="displayName" label="显示名" width="300" />
        <el-table-column prop="categoryName" label="分类" width="110" />
        <el-table-column prop="description" label="描述" min-width="180" show-overflow-tooltip />
        <el-table-column v-if="isAdmin" prop="datasourceKey" label="数据源" width="120" />
        <el-table-column v-if="isAdmin" prop="status" label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="isAdmin" prop="sortOrder" label="排序" width="70" />
        <el-table-column v-if="isAdmin" label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button v-if="isAdmin" link type="primary" @click="openForm(row)">编辑</el-button>
            <el-button v-if="isAdmin" link :type="row.status === 1 ? 'warning' : 'success'"
              @click="toggleStatus(row)">{{ row.status === 1 ? '停用' : '启用' }}</el-button>
            <el-popconfirm v-if="isAdmin" title="确定删除？" @confirm="handleDelete(row.id)">
              <template #reference><el-button link type="danger">删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="table-pagination" background layout="total, sizes, prev, pager, next"
        :total="total" v-model:current-page="query.current" v-model:page-size="query.size"
        :page-sizes="[10,20,50]" @change="loadData" />
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="formVisible" :title="form.id ? '编辑工具' : '新增工具'" width="60%" destroy-on-close>
      <el-form :model="form" label-width="90px" ref="formRef" :rules="formRules">
        <!-- 工具类型：始终置顶 -->
        <el-form-item label="工具类型" prop="toolType">
          <el-select v-model="form.toolType" style="width:100%">
            <el-option label="JDBC" value="JDBC" /><el-option label="HTTP_PROXY" value="HTTP_PROXY" />
          </el-select>
        </el-form-item>

        <!-- JDBC 专属字段 -->
        <template v-if="form.toolType === 'JDBC'">
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="数据源Key" prop="datasourceKey">
                <el-select
                  v-model="form.datasourceKey"
                  filterable
                  clearable
                  placeholder="请选择或搜索数据源"
                  style="width:100%">
                  <el-option
                    v-for="ds in allDatasources"
                    :key="ds.id"
                    :label="`${ds.dsKey}（${ds.dsName}）`"
                    :value="ds.dsKey" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="分类" prop="categoryId">
                <el-select v-model="form.categoryId" placeholder="请选择分类" clearable style="width:100%">
                  <el-option v-for="c in allCategories" :key="c.id" :label="c.categoryName" :value="c.id" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="SQL模板" prop="sqlTemplate" class="sql-template-item">
            <div class="sql-pagination-hint">
              <el-icon><InfoFilled /></el-icon>
              <span>
                <b>参数语法：</b>用 <code>#{paramName}</code> 表示必填参数；用 <code>[[AND 条件 = #{param}]]</code> 包裹可选条件块，当该参数未传入时条件自动省略。<br>
                <b>自动分页：</b>无分页语句时自动追加 LIMIT/OFFSET；若单页超过 1000 行将自动替换为 1000。可通过 <code>_pageNum</code>（页码）和 <code>_pageSize</code>（每页行数）控制分页。
              </span>
            </div>
            <el-input v-model="form.sqlTemplate" type="textarea" :rows="4" placeholder="SELECT * FROM t WHERE id = #{id}" :disabled="aiGenerating" />
            <div class="sql-ai-actions">
              <el-button type="primary" :loading="aiGenerating" :disabled="!form.sqlTemplate?.trim() || aiGenerating" @click="generateByAi">
                <el-icon><MagicStick /></el-icon> AI 一键生成
              </el-button>
            </div>

            <!-- AI 思考过程面板 -->
            <div v-if="aiPanelVisible" class="ai-thinking-panel">
              <div class="ai-thinking-header">
                <span v-if="aiGenerating">AI 思考中<span class="thinking-dots"><span>.</span><span>.</span><span>.</span></span></span>
                <span v-else>AI 已完成</span>
                <div class="ai-thinking-header-actions">
                  <el-tooltip v-if="aiGenerating" content="终止生成" placement="top" :enterable="false">
                    <el-icon class="stop-icon" @click="stopAiGenerate"><VideoPause /></el-icon>
                  </el-tooltip>
                  <el-button v-if="!aiGenerating" size="small" text @click="aiPanelVisible = false">收起</el-button>
                </div>
              </div>
              <div class="ai-thinking-content" ref="thinkingScrollRef">
                <div class="ai-thinking-text" v-html="aiThinkingHtml"></div>
              </div>
            </div>
          </el-form-item>
        </template>

        <!-- HTTP_PROXY 专属字段 -->
        <template v-if="form.toolType === 'HTTP_PROXY'">
          <div class="sql-pagination-hint" style="margin-bottom:12px">
            <el-icon><InfoFilled /></el-icon>
            <span>
              <b>HTTP代理说明：</b>将现有HTTP接口封装为MCP工具，AI可通过参数调用。<br>
              <b>URL路径变量：</b>用 <code>{paramName}</code> 表示路径参数，如 <code>/api/users/{userId}</code>。<br>
              <b>参数传递：</b>GET/DELETE时剩余参数作为QueryString；POST/PUT时作为JSON Body发送。
            </span>
          </div>
          <el-row :gutter="16">
            <el-col :span="8">
              <el-form-item label="HTTP方法" prop="httpMethod">
                <el-select v-model="form.httpMethod" style="width:100%">
                  <el-option label="GET" value="GET" /><el-option label="POST" value="POST" />
                  <el-option label="PUT" value="PUT" /><el-option label="DELETE" value="DELETE" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="16">
              <el-form-item label="URL" prop="httpUrl"><el-input v-model="form.httpUrl" placeholder="http://host:port/api/path/{id}" /></el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="分类">
                <el-select v-model="form.categoryId" placeholder="请选择分类" clearable style="width:100%">
                  <el-option v-for="c in allCategories" :key="c.id" :label="c.categoryName" :value="c.id" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="请求头"><el-input v-model="form.httpHeaders" type="textarea" :rows="2" placeholder='{"Authorization":"Bearer xxx","Content-Type":"application/json"}' /></el-form-item>
        </template>

        <!-- 通用字段（SQL 模板之后） -->
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="工具名" prop="toolName">
              <el-input v-model="form.toolName" :disabled="!!form.id || fieldDisabled" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="显示名称" prop="displayName">
              <el-input v-model="form.displayName" :disabled="form.toolType === 'JDBC' && !sqlReady" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="排序">
              <el-input-number v-model="form.sortOrder" :min="0" style="width:100%" :disabled="fieldDisabled" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="2" :disabled="fieldDisabled" />
        </el-form-item>

        <el-form-item label="InputSchema">
          <div class="input-schema-actions">
            <el-button size="small" :disabled="fieldDisabled || !form.inputSchema" @click="formatSchema('input')">格式化 JSON</el-button>
          </div>
          <el-input v-model="form.inputSchema" class="json-editor" type="textarea" :rows="10" placeholder='JSON Schema，留空使用默认空对象' :disabled="fieldDisabled" />
        </el-form-item>

        <el-form-item label="OutputSchema">
          <div class="input-schema-actions">
            <el-button size="small" :disabled="fieldDisabled || !form.outputSchema" @click="formatSchema('output')">格式化 JSON</el-button>
          </div>
          <el-input v-model="form.outputSchema" class="json-editor" type="textarea" :rows="8" placeholder='JSON Schema，描述工具返回的数据结构' :disabled="fieldDisabled" />
        </el-form-item>

        <el-form-item label="状态" v-if="form.id">
          <el-radio-group v-model="form.status"><el-radio :value="1">启用</el-radio><el-radio :value="2">停用</el-radio></el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm" :disabled="aiGenerating">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed, watch, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { InfoFilled, MagicStick, Plus, VideoPause } from '@element-plus/icons-vue'
import { listTools, listMyTools, createTool, updateTool, deleteTool, updateToolStatus, listCategories, listMyCategories, listDatasources, generateToolConfigStream } from '../../api'

// 判断是否为 admin
const isAdmin = (() => {
  try { return JSON.parse(localStorage.getItem('admin_roles') || '[]').includes('ADMIN') } catch { return false }
})()

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const query = reactive({ current: 1, size: 20, toolName: '', toolType: '', categoryId: undefined, status: undefined })

const allCategories = ref([])
const allDatasources = ref([])

const formVisible = ref(false)
const defaultForm = () => ({ id: null, toolName: '', toolType: 'JDBC', displayName: '', description: '', categoryId: null, datasourceKey: '', sqlTemplate: '', httpMethod: 'GET', httpUrl: '', httpHeaders: '', inputSchema: '', outputSchema: '', sortOrder: 0, remark: '', status: 1 })
const form = reactive(defaultForm())
const formRef = ref()
const formRules = {
  toolName: [
    { required: true, message: '请输入工具名', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_-]+$/, message: '工具名只能包含字母、数字、下划线、连字符，不支持中文', trigger: 'blur' }
  ],
  toolType:      [{ required: true, message: '请选择类型', trigger: 'change' }],
  categoryId:    [{ required: true, message: '请选择分类', trigger: 'change' }],
  displayName:   [{ required: true, message: '请输入显示名称', trigger: 'blur' }],
  description:   [{ required: true, message: '请输入描述', trigger: 'blur' }],
  datasourceKey: [{ required: true, message: '请选择数据源', trigger: 'change' }],
  sqlTemplate:   [{ required: true, message: '请输入SQL模板', trigger: 'blur' }],
  httpMethod:    [{ required: true, message: '请选择HTTP方法', trigger: 'change' }],
  httpUrl:       [{ required: true, message: '请输入URL', trigger: 'blur' }]
}

// AI 生成相关状态
const aiGenerating = ref(false)         // 是否正在生成（控制表单禁用）
const aiPanelVisible = ref(false)       // 思考面板是否显示
const aiThinking = ref('')              // 思考过程原始文字（LLM 输出 chunk 直接追加）
const thinkingScrollRef = ref(null)     // 思考内容容器 ref
let aiAbortFn = null                    // SSE 终止函数

// 将原始 aiThinking 转成带样式的 HTML：
//   [SYS:...] → 蓝色系统消息
//   [OK:...]  → 绿色完成消息
//   [ERR:...] → 红色错误消息
//   其余文字  → 正常颜色，HTML 转义后保留换行
const aiThinkingHtml = computed(() => {
  const text = aiThinking.value
  if (!text) return ''
  const escaped = text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
  const lines = escaped.split('\n')
  return lines.map(line => {
    if (line.startsWith('[SYS:')) {
      return `<span class="ai-msg-sys">${line}</span>`
    } else if (line.startsWith('[OK:')) {
      return `<span class="ai-msg-ok">${line}</span>`
    } else if (line.startsWith('[ERR:')) {
      return `<span class="ai-msg-err">${line}</span>`
    }
    return `<span>${line}</span>`
  }).join('\n')
})

const sqlReady = computed(() => form.toolType !== 'JDBC' || !!(form.sqlTemplate && form.sqlTemplate.trim().length > 0))
const fieldDisabled = computed(() => form.toolType === 'JDBC' && (!sqlReady.value || aiGenerating.value))

// 思考内容更新时自动滚动到底部
watch(aiThinking, () => {
  nextTick(() => {
    if (thinkingScrollRef.value) {
      thinkingScrollRef.value.scrollTop = thinkingScrollRef.value.scrollHeight
    }
  })
})

async function loadData() {
  loading.value = true
  try {
    const params = { ...query }
    // 普通用户不传 status 参数（/mine 端点不支持）
    if (!isAdmin) delete params.status
    const res = isAdmin ? await listTools(params) : await listMyTools(params)
    tableData.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

async function loadOptions() {
  try {
    const catRes = isAdmin ? await listCategories() : await listMyCategories()
    allCategories.value = catRes.data || []
  } catch (e) {
    console.error('加载分类失败', e)
  }
  // 数据源仅管理员需要（普通用户不显示数据源列，也没有编辑弹窗）
  if (isAdmin) {
    try {
      const dsRes = await listDatasources({ current: 1, size: 999 })
      allDatasources.value = (dsRes.data?.records || []).filter(d => d.status === 1)
    } catch (e) {
      console.error('加载数据源失败', e)
      ElMessage.error('数据源加载失败')
    }
  }
}

function resetQuery() {
  query.toolName = ''; query.toolType = ''; query.categoryId = undefined
  if (isAdmin) query.status = undefined
  query.current = 1
  loadData()
}

async function openForm(row) {
  Object.assign(form, defaultForm())
  if (row) {
    Object.assign(form, row)
  } else {
    form.sortOrder = total.value + 1
  }
  // 重置 AI 面板状态
  aiGenerating.value = false
  aiPanelVisible.value = false
  aiThinking.value = ''
  aiAbortFn = null
  if (allCategories.value.length === 0 || allDatasources.value.length === 0) {
    await loadOptions()
  }
  formVisible.value = true
}

async function submitForm() {
  await formRef.value.validate()
  if (form.id) { await updateTool(form.id, { ...form }); ElMessage.success('更新成功') }
  else { await createTool({ ...form }); ElMessage.success('创建成功') }
  formVisible.value = false; loadData()
}

async function handleDelete(id) { await deleteTool(id); ElMessage.success('删除成功'); loadData() }
async function toggleStatus(row) {
  const s = row.status === 1 ? 2 : 1
  await updateToolStatus(row.id, s); ElMessage.success(s === 1 ? '已启用' : '已停用'); loadData()
}

function generateByAi() {
  if (!form.sqlTemplate || !form.sqlTemplate.trim()) {
    ElMessage.warning('请先输入 SQL 模板')
    return
  }
  // 初始化面板状态
  aiGenerating.value = true
  aiPanelVisible.value = true
  aiThinking.value = ''

  aiAbortFn = generateToolConfigStream(
    {
      sqlTemplate: form.sqlTemplate.trim(),
      datasourceKey: form.datasourceKey || undefined
    },
    {
      onThinking(chunk) {
        aiThinking.value += chunk
      },
      onSchemaFetching(data) {
        try {
          const tables = JSON.parse(data)
          aiThinking.value += `\n[SYS: 正在查询表结构: ${Array.isArray(tables) ? tables.join(', ') : data}]\n`
        } catch {
          aiThinking.value += `\n[SYS: 正在查询表结构...]\n`
        }
      },
      onResult(jsonStr) {
        try {
          const data = JSON.parse(jsonStr)
          if (data.toolName && !form.toolName) form.toolName = data.toolName
          if (data.displayName && !form.displayName) form.displayName = data.displayName
          if (data.description && !form.description) form.description = data.description
          if (data.inputSchema) {
            form.inputSchema = tryFormatJson(data.inputSchema)
          }
          if (data.outputSchema) {
            form.outputSchema = tryFormatJson(data.outputSchema)
          }
          // 将 AI 优化后的 SQL 回写到模板（如有）
          if (data.optimizedSqlTemplate && data.optimizedSqlTemplate.trim()) {
            const optimized = data.optimizedSqlTemplate.trim()
            if (optimized !== form.sqlTemplate.trim()) {
              form.sqlTemplate = optimized
              aiThinking.value += '\n\n[SYS: SQL模板已由AI优化，请核对]'
            }
          }
          aiThinking.value += '\n\n[OK: 生成完成，已填入表单，请检查并确认各字段]'
          ElMessage.success('AI 生成成功，请检查并确认各字段')
          // AI 结果已写入表单，立即解除禁用（不依赖 onCompleted 回调，因为 SSE 连接可能延迟关闭）
          aiGenerating.value = false
          aiAbortFn = null
        } catch (e) {
          aiThinking.value += `\n[ERR: 解析结果失败：${e.message}]`
          ElMessage.error('解析 AI 结果失败：' + e.message)
        }
      },
      onError(msg) {
        aiThinking.value += `\n[ERR: ${msg}]`
        ElMessage.error('AI 生成失败：' + msg)
      },
      onComplete() {
        aiGenerating.value = false
        aiAbortFn = null
      }
    }
  )
}

function stopAiGenerate() {
  if (aiAbortFn) {
    aiAbortFn()
    aiAbortFn = null
  }
  aiGenerating.value = false
  aiThinking.value += '\n\n[SYS: 已手动终止]'
}

function tryFormatJson(str) {
  try {
    return JSON.stringify(JSON.parse(str), null, 2)
  } catch {
    return str
  }
}

function formatSchema(type) {
  const key = type === 'input' ? 'inputSchema' : 'outputSchema'
  if (!form[key]) return
  try {
    const parsed = JSON.parse(form[key])
    form[key] = JSON.stringify(parsed, null, 2)
  } catch {
    ElMessage.warning('当前内容不是合法 JSON，无法格式化')
  }
}

onMounted(() => {
  loadOptions()
  loadData()
})
</script>

<style scoped>
.page-container { height: 100%; }
.search-card :deep(.el-card__body) { padding-bottom: 0; }
.table-toolbar { margin-bottom: 12px; }
.table-pagination { margin-top: 16px; justify-content: flex-end; }
.sql-pagination-hint {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin-bottom: 6px;
  padding: 8px 12px;
  background: #fdf6ec;
  border: 1px solid #faecd8;
  border-radius: 4px;
  color: #e6a23c;
  font-size: 12px;
  line-height: 1.6;
}
.sql-pagination-hint .el-icon {
  margin-top: 2px;
  flex-shrink: 0;
}
.sql-ai-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
}
/* 强制 SQL模板 form-item 内容区纵向排列 */
.sql-template-item :deep(.el-form-item__content) {
  flex-direction: column;
  align-items: stretch;
}
.input-schema-actions {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 4px;
}
.json-editor :deep(.el-textarea__inner) {
  font-family: 'Courier New', Courier, monospace;
}

/* AI 思考面板 */
.ai-thinking-panel {
  margin-top: 8px;
  border: 1px solid #3a3a4a;
  border-radius: 6px;
  overflow: hidden;
  background: #1e1e2e;
}
.ai-thinking-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 12px;
  background: #2a2a3e;
  color: #a0a8c0;
  font-size: 12px;
  font-weight: 500;
}
.ai-thinking-header-actions {
  display: flex;
  align-items: center;
}
/* 停止图标 */
.stop-icon {
  font-size: 16px;
  color: #ff6b6b;
  cursor: pointer;
  transition: color 0.2s, transform 0.2s;
}
.stop-icon:hover {
  color: #ff9999;
  transform: scale(1.2);
}
/* 思考中 ... 动画：三个点依次颜色渐变循环 */
.thinking-dots span {
  display: inline-block;
  animation: dot-glow 1.5s ease-in-out infinite;
  color: #a0a8c0;
}
.thinking-dots span:nth-child(1) { animation-delay: 0s; }
.thinking-dots span:nth-child(2) { animation-delay: 0.3s; }
.thinking-dots span:nth-child(3) { animation-delay: 0.6s; }
@keyframes dot-glow {
  0%, 100% { color: #3a3a5a; }
  50%       { color: #7b8fff; }
}
.ai-thinking-content {
  height: 200px;
  overflow-y: auto;
  padding: 10px 12px;
}
/* 替代原来的 pre，用 div 支持 v-html 带颜色样式 */
.ai-thinking-text {
  margin: 0;
  font-family: 'Courier New', Courier, monospace;
  font-size: 12px;
  color: #c8d0e8;
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.6;
}
.ai-thinking-text span { display: inline; }
/* 系统状态消息：蓝灰色 */
.ai-thinking-text .ai-msg-sys {
  color: #7b8fff;
  font-style: italic;
}
/* 完成消息：绿色 */
.ai-thinking-text .ai-msg-ok {
  color: #56d364;
  font-weight: 600;
}
/* 错误消息：红色 */
.ai-thinking-text .ai-msg-err {
  color: #ff6b6b;
}
</style>
