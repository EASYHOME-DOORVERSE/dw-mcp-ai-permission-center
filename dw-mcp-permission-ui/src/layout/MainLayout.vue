<template>
  <el-container class="layout-container">
    <el-aside :width="isCollapse ? '64px' : '220px'" class="layout-aside">
      <div class="logo">
        <img
          :src="isCollapse ? '/favicon.png' : '/logo-brand.png'"
          alt="logo"
          :class="isCollapse ? 'logo-icon' : 'logo-img'"
        />
      </div>
      <el-menu
        :default-active="route.path"
        router
        :collapse="isCollapse"
        background-color="#1d1e1f"
        text-color="#bfcbd9"
        active-text-color="#409eff"
      >
        <el-menu-item index="/dashboard">
          <el-icon><DataAnalysis /></el-icon>
          <template #title>工作台</template>
        </el-menu-item>
        <el-menu-item v-if="isAdmin" index="/user">
          <el-icon><User /></el-icon>
          <template #title>用户管理</template>
        </el-menu-item>
        <el-menu-item v-if="isAdmin" index="/role">
          <el-icon><UserFilled /></el-icon>
          <template #title>角色管理</template>
        </el-menu-item>
        <el-menu-item index="/apikey">
          <el-icon><Key /></el-icon>
          <template #title>API Key</template>
        </el-menu-item>
        <el-menu-item index="/tool">
          <el-icon><SetUp /></el-icon>
          <template #title>MCP 工具</template>
        </el-menu-item>
        <el-menu-item v-if="isAdmin" index="/tool-category">
          <el-icon><Collection /></el-icon>
          <template #title>工具分类</template>
        </el-menu-item>
        <el-menu-item v-if="isAdmin" index="/datasource">
          <el-icon><Coin /></el-icon>
          <template #title>数据源</template>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="layout-header">
        <el-icon class="collapse-btn" @click="isCollapse = !isCollapse">
          <Fold v-if="!isCollapse" /><Expand v-else />
        </el-icon>
        <span class="header-title">{{ route.meta.title }}</span>
        <div class="header-right">
          <el-dropdown trigger="click" @command="handleUserCommand">
            <span class="user-dropdown-link">
              <el-icon><UserFilled /></el-icon>
              <span class="user-name">{{ nickname }}</span>
              <el-tag v-if="isAdmin" type="" size="small">管理员</el-tag>
              <el-tag v-if="!isAdmin" type="warning" size="small">普通用户</el-tag>
              <el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile"><el-icon><User /></el-icon>个人信息</el-dropdown-item>
                <el-dropdown-item command="password"><el-icon><Lock /></el-icon>修改密码</el-dropdown-item>
                <el-dropdown-item divided command="logout"><el-icon><SwitchButton /></el-icon>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="layout-main">
        <router-view />
      </el-main>
    </el-container>

    <!-- 个人信息弹窗 -->
    <el-dialog v-model="profileVisible" title="个人信息" width="480" destroy-on-close>
      <el-form :model="profileForm" :rules="profileRules" ref="profileFormRef" label-width="80px">
        <el-form-item label="用户名">
          <el-input :model-value="profileForm.username" disabled />
        </el-form-item>
        <el-form-item label="显示名称" prop="nickname">
          <el-input v-model="profileForm.nickname" placeholder="请输入显示名称" maxlength="128" />
        </el-form-item>
        <el-form-item prop="email">
          <template #label>
            <span style="display:inline-flex;align-items:center;gap:4px">
              邮箱
              <el-tooltip content="忘记密码时通过此邮箱接收重置验证码，请填写常用邮箱" placement="top">
                <el-icon style="cursor:help;font-size:14px"><QuestionFilled /></el-icon>
              </el-tooltip>
            </span>
          </template>
          <el-input v-model="profileForm.email" placeholder="请输入邮箱" maxlength="128" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="profileForm.remark" type="textarea" :rows="2" placeholder="请输入备注" maxlength="256" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="profileVisible = false">取消</el-button>
        <el-button type="primary" :loading="profileSaving" @click="saveProfile">保存</el-button>
      </template>
    </el-dialog>

    <!-- 修改密码弹窗 -->
    <el-dialog v-model="passwordVisible" title="修改密码" width="480" destroy-on-close>
      <el-form :model="passwordForm" :rules="passwordRules" ref="passwordFormRef" label-width="100px">
        <el-form-item label="旧密码" prop="oldPassword">
          <el-input v-model="passwordForm.oldPassword" type="password" show-password placeholder="请输入旧密码" />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="passwordForm.newPassword" type="password" show-password placeholder="请输入新密码（至少8位）" />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input v-model="passwordForm.confirmPassword" type="password" show-password placeholder="请再次输入新密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordVisible = false">取消</el-button>
        <el-button type="primary" :loading="passwordSaving" @click="savePassword">确认修改</el-button>
      </template>
    </el-dialog>
  </el-container>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { QuestionFilled } from '@element-plus/icons-vue'
import { getMyProfile, updateMyProfile, changeMyPassword, logout } from '../api'

const route = useRoute()
const router = useRouter()
const isCollapse = ref(false)

const isAdmin = (() => {
  try {
    return JSON.parse(localStorage.getItem('admin_roles') || '[]').includes('ADMIN')
  } catch {
    return false
  }
})()

const nickname = ref(localStorage.getItem('admin_nickname') || '')
const userId = localStorage.getItem('admin_userId') || ''

async function handleLogout() {
  try {
    await logout()
  } catch {
    // 即使后端调用失败也继续清理本地
  }
  localStorage.removeItem('admin_token')
  localStorage.removeItem('admin_roles')
  localStorage.removeItem('admin_userId')
  localStorage.removeItem('admin_nickname')
  router.push('/login')
}

function handleUserCommand(cmd) {
  if (cmd === 'profile') openProfile()
  else if (cmd === 'password') openPassword()
  else if (cmd === 'logout') handleLogout()
}

// ========== 个人信息 ==========
const profileVisible = ref(false)
const profileSaving = ref(false)
const profileFormRef = ref()
const profileForm = reactive({ username: '', nickname: '', email: '', remark: '' })
const profileRules = {
  nickname: [{ max: 128, message: '显示名称最长128字符', trigger: 'blur' }],
  email: [{ max: 128, message: '邮箱最长128字符', trigger: 'blur' }],
  remark: [{ max: 256, message: '备注最长256字符', trigger: 'blur' }]
}

async function openProfile() {
  try {
    const res = await getMyProfile()
    const d = res.data
    profileForm.username = d.username || ''
    profileForm.nickname = d.nickname || ''
    profileForm.email = d.email || ''
    profileForm.remark = d.remark || ''
    profileVisible.value = true
  } catch (e) {
    ElMessage.error('获取用户信息失败')
  }
}

async function saveProfile() {
  await profileFormRef.value.validate()
  profileSaving.value = true
  try {
    const res = await updateMyProfile({
      nickname: profileForm.nickname,
      email: profileForm.email,
      remark: profileForm.remark
    })
    // 更新 localStorage 和头部显示名称
    const newNickname = res.data?.nickname || profileForm.nickname
    localStorage.setItem('admin_nickname', newNickname)
    nickname.value = newNickname
    ElMessage.success('保存成功')
    profileVisible.value = false
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '保存失败')
  } finally {
    profileSaving.value = false
  }
}

// ========== 修改密码 ==========
const passwordVisible = ref(false)
const passwordSaving = ref(false)
const passwordFormRef = ref()
const passwordForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

const validateConfirmPassword = (rule, value, callback) => {
  if (value !== passwordForm.newPassword) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const passwordRules = {
  oldPassword: [{ required: true, message: '请输入旧密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 8, max: 64, message: '密码长度8-64位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ]
}

function openPassword() {
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
  passwordVisible.value = true
}

async function savePassword() {
  await passwordFormRef.value.validate()
  passwordSaving.value = true
  try {
    await changeMyPassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword
    })
    ElMessage.success('密码修改成功，请重新登录')
    passwordVisible.value = false
    // 修改密码后强制重新登录
    handleLogout()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '密码修改失败')
  } finally {
    passwordSaving.value = false
  }
}
</script>

<style scoped>
.layout-container { height: 100vh; }
.layout-aside {
  background: #1d1e1f;
  transition: width 0.3s;
  overflow: hidden;
}
.logo {
  height: auto;
  min-height: 60px;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 8px;
  padding: 5px 12px 5px 20px;
  color: #fff;
  font-size: 16px;
  font-weight: 600;
  border-bottom: 1px solid #333;
}
.logo-text { white-space: nowrap; }
.logo-icon { width: 28px; height: 28px; flex-shrink: 0; }
.logo-img {
  width: 60%;
  flex-shrink: 0;
  animation: logoGlow 2.5s ease-in-out infinite;
  filter: drop-shadow(0 0 6px rgba(64, 158, 255, 0.4));
}
@keyframes logoGlow {
  0%, 100% {
    filter: drop-shadow(0 0 6px rgba(64, 158, 255, 0.3));
  }
  50% {
    filter: drop-shadow(0 0 14px rgba(64, 158, 255, 0.7));
  }
}
.layout-header {
  display: flex;
  align-items: center;
  border-bottom: 1px solid #e6e6e6;
  background: #fff;
  padding: 0 20px;
}
.collapse-btn { cursor: pointer; font-size: 20px; }
.header-title { margin-left: 12px; font-size: 16px; font-weight: 500; }
.header-right { margin-left: auto; display: flex; align-items: center; gap: 12px; }
.user-dropdown-link {
  display: flex; align-items: center; gap: 6px; cursor: pointer;
  font-size: 14px; color: #606266; outline: none;
}
.user-dropdown-link:hover { color: #409eff; }
.user-name { max-width: 120px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.layout-main { background: #f5f5f5; padding: 20px; }
</style>
