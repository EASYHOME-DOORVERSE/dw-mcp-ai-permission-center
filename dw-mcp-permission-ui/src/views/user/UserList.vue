<template>
  <div class="page-container">
    <!-- 搜索栏 -->
    <el-card class="search-card" shadow="never">
      <el-form :model="query" inline>
        <el-form-item label="用户名">
          <el-input v-model="query.username" placeholder="模糊搜索" clearable @keyup.enter="loadData" />
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

    <!-- 操作栏 + 表格 -->
    <el-card shadow="never" style="margin-top:12px">
      <div class="table-toolbar">
        <el-button type="primary" @click="openForm(null)"><el-icon><Plus /></el-icon> 新增用户</el-button>
      </div>
      <el-table :data="tableData" v-loading="loading" stripe border>
        <el-table-column type="index" label="序号" width="60" />
        <el-table-column prop="username" label="用户名" width="140" />
        <el-table-column prop="nickname" label="显示名称" width="140" />
        <el-table-column prop="email" label="邮箱" min-width="180" />
        <el-table-column prop="status" label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="操作" width="340" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openForm(row)">编辑</el-button>
            <el-button link :type="row.status === 1 ? 'warning' : 'success'"
              @click="toggleStatus(row)">{{ row.status === 1 ? '停用' : '启用' }}</el-button>
            <el-button link type="primary" @click="openRoleAssign(row)">角色</el-button>
            <el-popconfirm title="确定重置密码(Admin@2024)为默认密码吗？" :width="200" @confirm="handleResetPassword(row.id)">
              <template #reference><el-button link type="warning">重置密码</el-button></template>
            </el-popconfirm>
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

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="formVisible" :title="form.id ? '编辑用户' : '新增用户'" width="500px" destroy-on-close>
      <el-form :model="form" label-width="80px" ref="formRef" :rules="formRules">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password
            :placeholder="form.id ? '留空则不修改' : '必填，至少8位'" />
        </el-form-item>
        <el-form-item label="显示名称"><el-input v-model="form.nickname" /></el-form-item>
        <el-form-item>
          <template #label>
            <span style="display:inline-flex;align-items:center;gap:4px">
              邮箱
              <el-tooltip content="忘记密码时通过此邮箱接收重置验证码，请填写常用邮箱" placement="top">
                <el-icon style="cursor:help;font-size:14px"><QuestionFilled /></el-icon>
              </el-tooltip>
            </span>
          </template>
          <el-input v-model="form.email" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="状态" v-if="form.id">
          <el-radio-group v-model="form.status"><el-radio :value="1">启用</el-radio><el-radio :value="2">停用</el-radio></el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <!-- 角色分配弹窗 -->
    <el-dialog v-model="roleVisible" title="分配角色" width="440px" destroy-on-close>
      <el-checkbox-group v-model="selectedRoleIds">
        <el-checkbox v-for="r in allRoles" :key="r.id" :value="r.id" :label="r.roleName" />
      </el-checkbox-group>
      <template #footer>
        <el-button @click="roleVisible = false">取消</el-button>
        <el-button type="primary" @click="submitRoles">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { QuestionFilled } from '@element-plus/icons-vue'
import {
  listUsers, createUser, updateUser, deleteUser,
  updateUserStatus, getUserRoles, assignUserRoles,
  resetUserPassword, listRoles
} from '../../api'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const query = reactive({ current: 1, size: 20, username: '', status: undefined })

const formVisible = ref(false)
const form = reactive({ id: null, username: '', password: '', nickname: '', email: '', remark: '', status: 1 })
const formRef = ref()
const formRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_-]+$/, message: '用户名只能包含字母、数字、下划线、连字符，不支持中文', trigger: 'blur' }
  ]
}

const roleVisible = ref(false)
const roleUserId = ref(null)
const allRoles = ref([])
const selectedRoleIds = ref([])

async function loadData() {
  loading.value = true
  try {
    const res = await listUsers(query)
    tableData.value = res.data.records
    total.value = res.data.total
  } finally { loading.value = false }
}

function resetQuery() {
  query.username = ''; query.status = undefined; query.current = 1; loadData()
}

function openForm(row) {
  Object.assign(form, { id: null, username: '', password: '', nickname: '', email: '', remark: '', status: 1 })
  if (row) Object.assign(form, { id: row.id, username: row.username, nickname: row.nickname, email: row.email, remark: row.remark, status: row.status })
  formVisible.value = true
}

async function submitForm() {
  await formRef.value.validate()
  if (form.id) {
    const payload = { ...form }
    // 编辑时密码留空表示不修改，不传给后端
    if (!payload.password) delete payload.password
    await updateUser(form.id, payload)
    ElMessage.success('更新成功')
  } else {
    await createUser({ ...form })
    ElMessage.success('创建成功')
  }
  formVisible.value = false; loadData()
}

async function handleDelete(id) {
  await deleteUser(id); ElMessage.success('删除成功'); loadData()
}

async function handleResetPassword(id) {
  await resetUserPassword(id); ElMessage.success('密码已重置为默认密码')
}

async function toggleStatus(row) {
  const newStatus = row.status === 1 ? 2 : 1
  await updateUserStatus(row.id, newStatus)
  ElMessage.success(newStatus === 1 ? '已启用' : '已停用'); loadData()
}

async function openRoleAssign(row) {
  roleUserId.value = row.id
  const [roleRes, userRoleRes] = await Promise.all([listRoles({ current: 1, size: 999 }), getUserRoles(row.id)])
  allRoles.value = roleRes.data.records
  selectedRoleIds.value = (userRoleRes.data || []).map(r => r.id)
  roleVisible.value = true
}

async function submitRoles() {
  await assignUserRoles(roleUserId.value, selectedRoleIds.value)
  ElMessage.success('角色分配成功'); roleVisible.value = false
}

onMounted(loadData)
</script>

<style scoped>
.page-container { height: 100%; }
.search-card :deep(.el-card__body) { padding-bottom: 0; }
.table-toolbar { margin-bottom: 12px; }
.table-pagination { margin-top: 16px; justify-content: flex-end; }
</style>
