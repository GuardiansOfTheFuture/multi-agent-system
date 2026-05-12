<template>
  <div class="register-page">
    <a-card title="PaperAI 注册" class="register-card">
      <a-form :model="form" layout="vertical" @submit.prevent="handleRegister">
        <a-form-item label="用户名" required>
          <a-input v-model:value="form.username" placeholder="请输入用户名" />
        </a-form-item>
        <a-form-item label="密码" required>
          <a-input-password v-model:value="form.password" placeholder="请输入密码" />
        </a-form-item>
        <a-form-item label="邮箱">
          <a-input v-model:value="form.email" placeholder="选填" />
        </a-form-item>
        <a-button type="primary" html-type="submit" block :loading="loading">注册</a-button>
        <div style="margin-top: 12px; text-align: center">
          <span>已有账号？</span>
          <router-link to="/login">去登录</router-link>
        </div>
      </a-form>
    </a-card>
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
  if (!form.username || !form.password) {
    message.warning('请填写用户名和密码')
    return
  }
  loading.value = true
  try {
    await store.doRegister(form.username, form.password, form.email)
    message.success('注册成功')
    router.push('/')
  } catch (e) {
    message.error(e.message || '注册失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.register-page {
  display: flex; justify-content: center; align-items: center;
  min-height: 100vh; background: #f0f2f5;
}
.register-card { width: 400px; }
</style>
