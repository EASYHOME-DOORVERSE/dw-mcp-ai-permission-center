<template>
  <div class="login-wrapper">
    <div class="login-bg">
      <ParticleNetwork />
    </div>
    <div class="login-card">
      <div class="login-card-left">
        <div class="logo-glow">
          <img src="/favicon.png" alt="MCP AuthProxy" class="login-logo-img" />
        </div>
      </div>
      <div class="login-card-right">
        <img src="/title.png" alt="MCP AuthProxy" class="login-title-img" />

        <!-- Step 1：输入用户名，发送验证码 -->
        <el-form v-if="step === 1" :model="form" ref="step1Ref" label-width="0" class="login-form"
          @submit.prevent="sendResetCode">
          <p class="step-hint">请输入您的账号，验证码将发送至注册邮箱</p>
          <el-form-item prop="username">
            <el-input v-model="form.username" placeholder="请输入用户名" size="large" :prefix-icon="User"
              @keyup.enter="sendResetCode" />
          </el-form-item>
          <el-form-item>
            <el-button class="login-btn" size="large" :loading="loading" style="width:100%"
              @click="sendResetCode">发送验证码</el-button>
          </el-form-item>
        </el-form>

        <!-- Step 2：输入验证码 + 新密码 -->
        <el-form v-else :model="form" :rules="resetRules" ref="step2Ref" label-width="0" class="login-form">
          <p class="step-hint">验证码已发送至 <strong>{{ maskedEmail }}</strong></p>
          <el-form-item prop="code">
            <el-input v-model="form.code" placeholder="6位邮箱验证码" size="large" maxlength="6" :prefix-icon="Key" />
          </el-form-item>
          <el-form-item prop="newPassword">
            <el-input v-model="form.newPassword" type="password" placeholder="新密码（至少 6 位）" size="large"
              :prefix-icon="Lock" show-password />
          </el-form-item>
          <el-form-item prop="confirmPassword">
            <el-input v-model="form.confirmPassword" type="password" placeholder="再次输入新密码" size="large"
              :prefix-icon="Lock" show-password @keyup.enter="doResetPassword" />
          </el-form-item>
          <el-form-item>
            <el-button class="login-btn" size="large" :loading="loading" style="width:100%"
              @click="doResetPassword">确认重置</el-button>
          </el-form-item>
          <el-alert v-if="resetSuccess" title="密码重置成功，即将跳转登录…" type="success" show-icon :closable="false"
            style="margin-top:12px" />
        </el-form>

        <a class="login-forgot" @click="router.push('/login')">← 返回登录</a>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Key, Lock } from '@element-plus/icons-vue'
import ParticleNetwork from '../../components/ParticleNetwork.vue'
import axios from 'axios'

const router = useRouter()

const step = ref(1)
const loading = ref(false)
const resetSuccess = ref(false)
const step1Ref = ref(null)
const step2Ref = ref(null)

const form = reactive({ username: '', code: '', newPassword: '', confirmPassword: '' })

const maskedEmail = ref('')

const resetRules = {
  code: [{ required: true, message: '请输入验证码', trigger: 'blur' },
    { len: 6, message: '验证码为 6 位', trigger: 'blur' }],
  newPassword: [{ required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 8, message: '密码长度不能少于 8 位', trigger: 'blur' }],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    { validator: (_, v, cb) => v === form.newPassword ? cb() : cb(new Error('两次输入不一致')), trigger: 'blur' }
  ]
}

async function sendResetCode() {
  if (!form.username.trim()) {
    ElMessage.warning('请输入用户名')
    return
  }
  loading.value = true
  try {
    const res = await axios.post('/api/auth/send-reset-code', { username: form.username })
    if (res.data.code === 200) {
      // 脱敏显示邮箱（后端未返回邮箱，前端用用户名提示）
      maskedEmail.value = form.username + ' 的注册邮箱'
      step.value = 2
    } else {
      ElMessage.error(res.data.message || '发送失败')
    }
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '发送失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

async function doResetPassword() {
  const valid = await step2Ref.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    const res = await axios.post('/api/auth/reset-password', {
      username: form.username,
      code: form.code,
      newPassword: form.newPassword
    })
    if (res.data.code === 200) {
      resetSuccess.value = true
      ElMessage.success('密码重置成功，即将跳转登录')
      setTimeout(() => router.push('/login'), 1500)
    } else {
      ElMessage.error(res.data.message || '重置失败')
    }
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '重置失败，请稍后重试')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-wrapper {
  height: 100vh; display: flex;
  align-items: center; justify-content: center;
  position: relative; overflow: hidden;
  background: radial-gradient(ellipse at center, #1a1a3e 0%, #0b1120 70%);
}
.login-bg { position: absolute; inset: 0; z-index: 0; }
.login-card {
  position: relative; z-index: 1;
  display: flex; width: 880px; min-height: 500px;
  background: rgba(20, 20, 40, 0.55);
  border-radius: 36px;
  box-shadow:
    0 0 0 1px rgba(255, 255, 255, 0.08),
    0 32px 64px rgba(0, 0, 0, 0.4),
    0 0 80px rgba(107, 70, 193, 0.12);
  overflow: hidden;
  backdrop-filter: blur(30px);
  -webkit-backdrop-filter: blur(30px);
  animation: cardEnter 0.8s cubic-bezier(0.16, 1, 0.3, 1) forwards;
  opacity: 0; transform: translateY(30px);
}
@keyframes cardEnter { to { opacity: 1; transform: translateY(0); } }
.login-card-left {
  flex: 1; display: flex; align-items: center; justify-content: center;
  padding: 48px;
  background: linear-gradient(180deg, rgba(107, 70, 193, 0.06) 0%, transparent 100%);
}
.logo-glow { position: relative; }
.logo-glow::before {
  content: ''; position: absolute; inset: 0; border-radius: 50%;
  background: radial-gradient(circle, rgba(139, 108, 202, 0.25) 0%, transparent 70%);
  animation: pulse 3s ease-in-out infinite;
}
@keyframes pulse { 0%,100% { transform: scale(1); opacity: 0.6; } 50% { transform: scale(1.15); opacity: 0.25; } }
.login-logo-img {
  position: relative; z-index: 1; width: 100%; height: auto;
  filter: drop-shadow(0 8px 32px rgba(139, 108, 202, 0.5));
  animation: float 4s ease-in-out infinite;
}
@keyframes float { 0%,100% { transform: translateY(0); } 50% { transform: translateY(-12px); } }
.login-title-img {
  max-width: 260px; width: 100%; height: auto;
  margin-bottom: 36px; align-self: center;
}
.login-card-right {
  flex: 1; display: flex; flex-direction: column; justify-content: center;
  padding: 48px 56px;
}
.step-hint {
  color: rgba(255, 255, 255, 0.55);
  font-size: 13px;
  margin: 0 0 20px 0;
}
.step-hint strong {
  color: #b8a0e8;
}
.login-form :deep(.el-input__wrapper) {
  background: rgba(255, 255, 255, 0.05);
  border-radius: 14px;
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.1) inset;
  padding: 4px 18px;
  transition: all 0.3s ease;
}
.login-form :deep(.el-input__wrapper.is-focus) {
  box-shadow:
    0 0 0 1px rgba(139, 108, 202, 0.6) inset,
    0 0 20px rgba(139, 108, 202, 0.15);
  background: rgba(255, 255, 255, 0.08);
}
.login-form :deep(.el-input__inner) {
  height: 50px; font-size: 15px; color: #e8e8f0;
}
.login-form :deep(.el-input__inner::placeholder) {
  color: rgba(255, 255, 255, 0.35);
}
.login-btn {
  background: linear-gradient(135deg, #9a7dd4 0%, #7c5cc8 50%, #6b4eb8 100%);
  border: none; border-radius: 14px; height: 50px;
  font-size: 16px; font-weight: 600; letter-spacing: 2px; color: #fff;
  box-shadow: 0 8px 24px rgba(107, 70, 193, 0.4);
  transition: all 0.3s cubic-bezier(0.16, 1, 0.3, 1);
  position: relative; overflow: hidden;
}
.login-btn::before {
  content: ''; position: absolute; top: 0; left: -100%;
  width: 100%; height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255,255,255,0.2), transparent);
  transition: left 0.6s;
}
.login-btn:hover {
  transform: translateY(-2px) scale(1.01);
  box-shadow: 0 12px 36px rgba(107, 70, 193, 0.55);
}
.login-btn:hover::before { left: 100%; }
.login-forgot {
  display: block; text-align: right;
  color: rgba(255, 255, 255, 0.4); font-size: 13px;
  margin-top: 16px; text-decoration: none; cursor: pointer;
  transition: color 0.3s;
}
.login-forgot:hover { color: #b8a0e8; }

@media (max-width: 768px) {
  .login-card { flex-direction: column; width: 92vw; min-height: auto; border-radius: 28px; }
  .login-card-left { padding: 36px; }
  .login-logo-img { max-width: 160px; }
  .logo-glow { padding: 24px; }
  .login-card-right { padding: 32px 28px; }
  .login-title-img { max-width: 200px; margin-bottom: 24px; }
}
</style>
