<template>
  <div class="page-container">
    <el-card class="search-card" shadow="never">
      <el-form :model="query" inline>
        <el-form-item label="角色编码">
          <el-input v-model="query.roleCode" placeholder="模糊搜索" clearable @keyup.enter="loadData" />
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
        <el-button type="primary" @click="openForm(null)"><el-icon><Plus /></el-icon> 新增角色</el-button>
      </div>
      <el-table :data="tableData" v-loading="loading" stripe border>
        <el-table-column type="index" label="序号" width="60" />
        <el-table-column prop="roleCode" label="角色编码" width="160" />
        <el-table-column prop="roleName" label="角色名称" width="160" />
        <el-table-column prop="description" label="描述" min-width="200" />
        <el-table-column prop="status" label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :disabled="isProtected(row)" @click="openForm(row)">编辑</el-button>
            <el-button link type="primary" @click="openToolAssign(row)">工具授权</el-button>
            <el-popconfirm title="确定删除？" @confirm="handleDelete(row.id)" :disabled="isProtected(row)">
              <template #reference><el-button link type="danger" :disabled="isProtected(row)">删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="table-pagination" background layout="total, sizes, prev, pager, next"
        :total="total" v-model:current-page="query.current" v-model:page-size="query.size"
        :page-sizes="[10,20,50]" @change="loadData" />
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="formVisible" :title="form.id ? '编辑角色' : '新增角色'" width="500px" destroy-on-close>
      <el-form :model="form" label-width="80px" ref="formRef" :rules="formRules">
        <el-form-item label="角色编码" prop="roleCode">
          <el-input v-model="form.roleCode" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="form.roleName" />
        </el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="状态" v-if="form.id">
          <el-radio-group v-model="form.status"><el-radio :value="1">启用</el-radio><el-radio :value="2">停用</el-radio></el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <!-- 工具授权弹窗 -->
    <el-dialog v-model="toolVisible" title="工具授权" width="580px" destroy-on-close>
      <div v-for="group in groupedTools" :key="group.categoryId" class="tool-group">
        <div class="tool-group-title">{{ group.categoryName }}</div>
        <el-checkbox-group v-model="selectedToolIds">
          <div v-for="t in group.tools" :key="t.id" class="tool-checkbox-item">
            <el-checkbox :value="t.id" :label="t.toolName">
              <span>{{ t.toolName }}</span>
              <span v-if="t.displayName" style="color:#909399;margin-left:4px">（{{ t.displayName }}）</span>
            </el-checkbox>
          </div>
        </el-checkbox-group>
      </div>
      <template #footer>
        <el-button @click="toolVisible = false">取消</el-button>
        <el-button type="primary" @click="submitTools">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listRoles, createRole, updateRole, deleteRole, getRoleTools, assignRoleTools, listTools, listCategories } from '../../api'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const query = reactive({ current: 1, size: 20, roleCode: '', status: undefined })

const formVisible = ref(false)
const form = reactive({ id: null, roleCode: '', roleName: '', description: '', status: 1 })
const formRef = ref()
const formRules = {
  roleCode: [
    { required: true, message: '请输入角色编码', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_-]+$/, message: '角色编码只能包含字母、数字、下划线、连字符，不支持中文', trigger: 'blur' }
  ],
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }]
}

const toolVisible = ref(false)
const toolRoleId = ref(null)
const allTools = ref([])
const allCategories = ref([])
const selectedToolIds = ref([])

/** 按 categoryId 分组，组内按 sortOrder 升序，组间按分类 sortOrder 升序 */
const groupedTools = computed(() => {
  // 构建分类排序映射
  const categorySortMap = {}
  for (const c of allCategories.value) {
    categorySortMap[c.id] = c
  }
  // 按分类分组
  const groupMap = new Map()
  for (const t of allTools.value) {
    const cid = t.categoryId || 0
    if (!groupMap.has(cid)) groupMap.set(cid, [])
    groupMap.get(cid).push(t)
  }
  // 组内按 sortOrder 升序
  for (const tools of groupMap.values()) {
    tools.sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))
  }
  // 组间按分类 sortOrder 升序排列
  const groups = []
  for (const [cid, tools] of groupMap.entries()) {
    const cat = categorySortMap[cid]
    groups.push({
      categoryId: cid,
      categoryName: cat ? cat.categoryName : '未分类',
      categorySortOrder: cat ? (cat.sortOrder ?? 9999) : 9999,
      tools
    })
  }
  groups.sort((a, b) => a.categorySortOrder - b.categorySortOrder)
  return groups
})

/** 系统内置受保护角色编码，禁止编辑/停用/删除 */
const PROTECTED_ROLE_CODES = ['ADMIN']
const isProtected = (row) => PROTECTED_ROLE_CODES.includes(row.roleCode)

async function loadData() {
  loading.value = true
  try {
    const res = await listRoles(query)
    tableData.value = res.data.records; total.value = res.data.total
  } finally { loading.value = false }
}
function resetQuery() { query.roleCode = ''; query.status = undefined; query.current = 1; loadData() }

function openForm(row) {
  Object.assign(form, { id: null, roleCode: '', roleName: '', description: '', status: 1 })
  if (row) Object.assign(form, { id: row.id, roleCode: row.roleCode, roleName: row.roleName, description: row.description, status: row.status })
  formVisible.value = true
}

async function submitForm() {
  await formRef.value.validate()
  if (form.id) { await updateRole(form.id, { ...form }); ElMessage.success('更新成功') }
  else { await createRole({ ...form }); ElMessage.success('创建成功') }
  formVisible.value = false; loadData()
}

async function handleDelete(id) { await deleteRole(id); ElMessage.success('删除成功'); loadData() }

async function openToolAssign(row) {
  toolRoleId.value = row.id
  const [toolRes, roleToolRes, catRes] = await Promise.all([
    listTools({ current: 1, size: 999 }),
    getRoleTools(row.id),
    listCategories()
  ])
  allTools.value = toolRes.data.records
  allCategories.value = catRes.data || []
  selectedToolIds.value = (roleToolRes.data || []).map(t => t.id)
  toolVisible.value = true
}

async function submitTools() {
  await assignRoleTools(toolRoleId.value, selectedToolIds.value)
  ElMessage.success('工具授权成功'); toolVisible.value = false
}

onMounted(loadData)
</script>

<style scoped>
.page-container { height: 100%; }
.search-card :deep(.el-card__body) { padding-bottom: 0; }
.table-toolbar { margin-bottom: 12px; }
.table-pagination { margin-top: 16px; justify-content: flex-end; }
.tool-group { margin-bottom: 16px; }
.tool-group:last-child { margin-bottom: 0; }
.tool-group-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  padding: 4px 0;
  margin-bottom: 8px;
  border-bottom: 1px solid #ebeef5;
}
.tool-checkbox-item { margin-bottom: 4px; }
.tool-checkbox-item:last-child { margin-bottom: 0; }
</style>
