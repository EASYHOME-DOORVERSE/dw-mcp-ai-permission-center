<template>
  <div class="page-container">
    <el-card shadow="never">
      <div class="table-toolbar">
        <el-button type="primary" @click="openForm(null)"><el-icon><Plus /></el-icon> 新增分类</el-button>
      </div>
      <el-table :data="tableData" v-loading="loading" stripe border>
        <el-table-column type="index" label="序号" width="60" />
        <el-table-column prop="categoryCode" label="分类编码" width="160" />
        <el-table-column prop="categoryName" label="分类名称" width="160" />
        <el-table-column prop="sortOrder" label="排序" width="80" align="center" />
        <el-table-column prop="remark" label="备注" min-width="200" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openForm(row)">编辑</el-button>
            <el-popconfirm
              title="确定删除？删除后该分类下工具将归入默认分类"
              width="240"
              @confirm="handleDelete(row.id)">
              <template #reference>
                <el-button link type="danger" :disabled="row.builtIn">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="formVisible" :title="form.id ? '编辑分类' : '新增分类'" width="480px" destroy-on-close>
      <el-form :model="form" label-width="90px" ref="formRef" :rules="formRules">
        <el-form-item label="分类编码" prop="categoryCode">
          <el-input v-model="form.categoryCode" :disabled="!!form.id" placeholder="如：data_query" />
        </el-form-item>
        <el-form-item label="分类名称" prop="categoryName">
          <el-input v-model="form.categoryName" placeholder="如：数据查询" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" style="width:100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listCategories, createCategory, updateCategory, deleteCategory } from '../../api'

const loading = ref(false)
const tableData = ref([])

const formVisible = ref(false)
const defaultForm = () => ({ id: null, categoryCode: '', categoryName: '', sortOrder: 0, remark: '' })
const form = reactive(defaultForm())
const formRef = ref()
const formRules = {
  categoryCode: [
    { required: true, message: '请输入分类编码', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_-]+$/, message: '编码只能包含字母、数字、下划线、连字符', trigger: 'blur' }
  ],
  categoryName: [{ required: true, message: '请输入分类名称', trigger: 'blur' }]
}

async function loadData() {
  loading.value = true
  try {
    const res = await listCategories()
    tableData.value = res.data || []
  } finally {
    loading.value = false
  }
}

function openForm(row) {
  Object.assign(form, defaultForm())
  if (row) Object.assign(form, { id: row.id, categoryCode: row.categoryCode, categoryName: row.categoryName, sortOrder: row.sortOrder, remark: row.remark })
  formVisible.value = true
}

async function submitForm() {
  await formRef.value.validate()
  if (form.id) {
    await updateCategory(form.id, { ...form })
    ElMessage.success('更新成功')
  } else {
    await createCategory({ ...form })
    ElMessage.success('创建成功')
  }
  formVisible.value = false
  loadData()
}

async function handleDelete(id) {
  await deleteCategory(id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(loadData)
</script>

<style scoped>
.page-container { height: 100%; }
.table-toolbar { margin-bottom: 12px; }
</style>
