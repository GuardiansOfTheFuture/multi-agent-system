<template>
  <div class="register-page">
    <div class="auth-bg">
      <div class="auth-orb orb-1" />
      <div class="auth-orb orb-2" />
      <div class="auth-orb orb-3" />
    </div>
    <div class="register-card-wrap">
      <div class="auth-brand">⚡ PaperAI</div>
      <div class="auth-subtitle">创建您的账号</div>
      <a-form :model="form" layout="vertical" @submit.prevent="handleRegister" class="auth-form">
        <a-form-item>
          <a-input v-model:value="form.username" placeholder="用户名" size="large" />
        </a-form-item>
        <a-form-item>
          <a-input-password v-model:value="form.password" placeholder="密码" size="large" />
        </a-form-item>
        <a-form-item>
          <a-input v-model:value="form.email" placeholder="邮箱（选填）" size="large" />
        </a-form-item>
        <a-button type="primary" html-type="submit" block size="large" :loading="loading">注 册</a-button>
        <div class="auth-link">
          <span>已有账号？</span>
          <router-link to="/login">去登录</router-link>
        </div>
      </a-form>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { message } from 'ant-design-vue'

const router = useRouter()
const store = useUserStore()
const loading = ref(false)
const form = reactive({ username: '', password: '', email: '' })

async function handleRegister() {
  if (!form.username || !form.password) { message.warning('请填写用户名和密码'); return }
  loading.value = true
  try {
    await store.doRegister(form.username, form.password, form.email)
    message.success('注册成功')
    router.push('/')
  } catch (e) {
    message.error(e.message || '注册失败')
  } finally { loading.value = false }
}
</script>

<style scoped>
.register-page {
  display: flex; justify-content: center; align-items: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #0a0e17 0%, #111827 40%, #0f1729 70%, #0a0f1a 100%);
  position: relative; overflow: hidden;
}
.auth-bg { position: absolute; inset: 0; pointer-events: none; }
.auth-orb {
  position: absolute; border-radius: 50%; filter: blur(80px); opacity: 0.15;
}
.orb-1 { width: 500px; height: 500px; top: -150px; right: -100px; background: radial-gradient(circle, #13c2c2, transparent); animation: orbFloat 8s ease-in-out infinite; }
.orb-2 { width: 400px; height: 400px; bottom: -100px; left: -80px; background: radial-gradient(circle, #1890ff, transparent); animation: orbFloat 10s ease-in-out infinite reverse; }
.orb-3 { width: 300px; height: 300px; top: 50%; left: 50%; transform: translate(-50%,-50%); background: radial-gradient(circle, #9254de, transparent); animation: orbFloat 12s ease-in-out infinite 2s; }
@keyframes orbFloat {
  0%, 100% { transform: translateY(0) scale(1); }
  50% { transform: translateY(-30px) scale(1.1); }
}
.register-card-wrap {
  position: relative; z-index: 1;
  width: 400px; padding: 40px 36px;
  background: rgba(15,20,38,0.75);
  backdrop-filter: blur(24px);
  -webkit-backdrop-filter: blur(24px);
  border: 1px solid rgba(255,255,255,0.08);
  border-radius: 16px;
}
.auth-brand { font-size: 28px; font-weight: 800; color: #fff; text-align: center; letter-spacing: 2px; }
.auth-subtitle { font-size: 13px; color: rgba(255,255,255,0.35); text-align: center; margin: 8px 0 32px; }
.auth-form :deep(.ant-form-item) { margin-bottom: 20px; }
.auth-link { margin-top: 16px; text-align: center; font-size: 13px; color: rgba(255,255,255,0.4); }
.auth-link a { color: #b37feb; margin-left: 4px; font-weight: 500; }
</style>
