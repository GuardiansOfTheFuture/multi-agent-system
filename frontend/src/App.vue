<template>
  <a-config-provider :locale="locale" :theme="antTheme">
    <GlobalParticles />
    <router-view />
  </a-config-provider>
</template>

<script setup>
import { reactive } from 'vue'
import zhCN from 'ant-design-vue/es/locale/zh_CN'
import dayjs from 'dayjs'
import 'dayjs/locale/zh-cn'
import GlobalParticles from '@/components/GlobalParticles.vue'

dayjs.locale('zh-cn')
const locale = zhCN

// Ant Design 5 token 级暗色主题
const antTheme = {
  token: {
    colorPrimary: '#9254de',
    colorBgBase: '#0a0e17',
    colorTextBase: 'rgba(255, 255, 255, 0.85)',
    colorBorder: 'rgba(255, 255, 255, 0.08)',
    colorBgContainer: 'rgba(15, 20, 38, 0.7)',
    colorBgElevated: 'rgba(20, 26, 45, 0.95)',
    borderRadius: 8,
    wireframe: false
  }
}
</script>
