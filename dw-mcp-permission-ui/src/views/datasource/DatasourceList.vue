<template>
  <div class="page-container">
    <el-card class="search-card" shadow="never">
      <el-form :model="query" inline>
        <el-form-item label="数据源Key">
          <el-input v-model="query.dsKey" placeholder="模糊搜索" clearable @keyup.enter="loadData" />
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
        <el-button type="primary" @click="openForm(null)"><el-icon><Plus /></el-icon> 新增数据源</el-button>
      </div>
      <el-table :data="tableData" v-loading="loading" stripe border>
        <el-table-column type="index" label="序号" width="60" />
        <el-table-column prop="dsKey" label="Key" width="140" />
        <el-table-column prop="dsName" label="名称" width="160" />
        <el-table-column prop="dbType" label="类型" width="100" align="center" />
        <el-table-column prop="url" label="JDBC URL" min-width="260" show-overflow-tooltip />
        <el-table-column prop="username" label="用户名" width="120" />
        <el-table-column prop="status" label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="testConn(row.id)">连通性测试</el-button>
            <el-button link type="primary" @click="openForm(row)">编辑</el-button>
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
    <el-dialog v-model="formVisible" :title="form.id ? '编辑数据源' : '新增数据源'" width="620px" destroy-on-close>
      <el-form :model="form" label-width="100px" ref="formRef" :rules="formRules">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="数据源Key" prop="dsKey"><el-input v-model="form.dsKey" :disabled="!!form.id" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="名称" prop="dsName"><el-input v-model="form.dsName" /></el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="数据库类型">
          <el-select v-model="form.dbType" style="width:200px">
            <el-option label="MySQL" value="mysql" /><el-option label="PostgreSQL" value="postgresql" />
            <el-option label="Oracle" value="oracle" /><el-option label="SQL Server" value="sqlserver" />
          </el-select>
        </el-form-item>
        <el-form-item label="JDBC URL" prop="url"><el-input v-model="form.url" /></el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="用户名" prop="username"><el-input v-model="form.username" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="密码" prop="password">
              <el-input v-model="form.password" type="password" show-password
                :placeholder="form.id ? '留空则不修改' : '必填'" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="驱动类"><el-input v-model="form.driverClass" placeholder="留空自动推断" /></el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="最小连接数"><el-input-number v-model="form.poolMinSize" :min="1" style="width:100%" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="最大连接数"><el-input-number v-model="form.poolMaxSize" :min="1" style="width:100%" /></el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listDatasources, createDatasource, updateDatasource, deleteDatasource, testDatasourceConnection } from '../../api'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const query = reactive({ current: 1, size: 20, dsKey: '', status: undefined })

const formVisible = ref(false)
const defaultForm = () => ({ id: null, dsKey: '', dsName: '', dbType: 'mysql', url: '', username: '', password: '', driverClass: '', poolMinSize: 5, poolMaxSize: 20, remark: '', status: 1 })
const form = reactive(defaultForm())
const formRef = ref()
const formRules = computed(() => ({
  dsKey: [
    { required: true, message: '请输入Key', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_-]+$/, message: '数据源Key只能包含字母、数字、下划线、连字符，不支持中文', trigger: 'blur' }
  ],
  dsName: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  url: [{ required: true, message: '请输入JDBC URL', trigger: 'blur' }],
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: form.id ? [] : [{ required: true, message: '请输入密码', trigger: 'blur' }]
}))

async function loadData() {
  loading.value = true
  try { const res = await listDatasources(query); tableData.value = res.data.records; total.value = res.data.total }
  finally { loading.value = false }
}
function resetQuery() { query.dsKey = ''; query.status = undefined; query.current = 1; loadData() }

function openForm(row) {
  Object.assign(form, defaultForm())
  if (row) Object.assign(form, { ...row, password: '' })
  formVisible.value = true
}

async function submitForm() {
  await formRef.value.validate()
  if (form.id) { await updateDatasource(form.id, { ...form }); ElMessage.success('更新成功') }
  else { await createDatasource({ ...form }); ElMessage.success('创建成功') }
  formVisible.value = false; loadData()
}

async function handleDelete(id) { await deleteDatasource(id); ElMessage.success('删除成功'); loadData() }

async function testConn(id) {
  const res = await testDatasourceConnection(id)
  const msg = res.data || '未知结果'
  if (msg.includes('成功')) ElMessage.success(msg)
  else ElMessageBox.alert(msg, '连通性测试失败', { type: 'error' })
}

onMounted(loadData)
</script>

<style scoped>
.page-container { height: 100%; }
.search-card :deep(.el-card__body) { padding-bottom: 0; }
.table-toolbar { margin-bottom: 12px; }
.table-pagination { margin-top: 16px; justify-content: flex-end; }
</style>
