<template>
  <a-layout style="min-height: 100vh; overflow: hidden; background: #0a0e17; position: relative;">
    <!-- 背景光球 -->
    <div class="layout-orbs">
      <div class="layout-orb orb-purple" />
      <div class="layout-orb orb-blue" />
      <div class="layout-orb orb-cyan" />
    </div>
    <!-- 侧边栏 -->
    <a-layout-sider v-model:collapsed="collapsed" :trigger="null" collapsible width="220" class="layout-sider">
      <div class="logo">
        <div class="logo-icon">⚡</div>
        <span v-if="!collapsed" class="logo-text">PaperAI</span>
      </div>
      <a-menu
        v-model:selectedKeys="selectedKeys"
        theme="dark"
        mode="inline"
        class="side-menu"
        @click="handleMenuClick"
      >
        <a-menu-item key="/write">
          <template #icon><edit-outlined /></template>
          <span>论文写作</span>
        </a-menu-item>
        <a-menu-item key="/papers">
          <template #icon><file-text-outlined /></template>
          <span>论文列表</span>
        </a-menu-item>
        <a-menu-item key="/flow">
          <template #icon><apartment-outlined /></template>
          <span>流程画布</span>
        </a-menu-item>
        <a-menu-item key="/knowledge-graph">
          <template #icon><share-alt-outlined /></template>
          <span>知识图谱</span>
        </a-menu-item>
        <a-menu-item key="/agents">
          <template #icon><robot-outlined /></template>
          <span>Agent 管理</span>
        </a-menu-item>
      </a-menu>
    </a-layout-sider>

    <!-- 主区域 -->
    <a-layout style="height: 100vh; overflow: hidden; display: flex; flex-direction: column; background: transparent;">
      <a-layout-header class="header">
        <menu-unfold-outlined v-if="collapsed" class="trigger" @click="collapsed = !collapsed" />
        <menu-fold-outlined v-else class="trigger" @click="collapsed = !collapsed" />
        <span class="header-title">{{ pageTitle }}</span>
        <div class="header-right">
          <a-tag color="purple" style="border:1px solid rgba(114,46,209,0.3);background:rgba(114,46,209,0.1)">
            通义千问 qwen3.6-plus
          </a-tag>
          <a-dropdown>
            <a class="user-dropdown" @click.prevent>
              <user-outlined style="margin-right:4px" />
              {{ store.user?.username || '用户' }}
              <down-outlined />
            </a>
            <template #overlay>
              <a-menu>
                <a-menu-item key="profile" disabled>
                  <user-outlined /> {{ store.user?.username }}
                </a-menu-item>
                <a-menu-divider />
                <a-menu-item key="logout" @click="handleLogout">
                  <logout-outlined /> 退出登录
                </a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
        </div>
      </a-layout-header>

      <a-layout-content class="content">
        <router-view />
      </a-layout-content>
    </a-layout>
  </a-layout>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import {
  EditOutlined, FileTextOutlined, RobotOutlined, ApartmentOutlined, ShareAltOutlined,
  UserOutlined, LogoutOutlined, DownOutlined, MenuUnfoldOutlined, MenuFoldOutlined
} from '@ant-design/icons-vue'

const router = useRouter()
const route = useRoute()
const store = useUserStore()

const collapsed = ref(false)
const selectedKeys = ref([route.path])
const pageTitle = computed(() => route.meta?.title || 'PaperAI')

function handleMenuClick({ key }) {
  selectedKeys.value = [key]
  router.push(key)
}

function handleLogout() {
  store.logout()
  router.push('/login')
}
</script>

<style scoped>
/* ===== 背景光球 ===== */
.layout-orbs {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 0;
  overflow: hidden;
}
.layout-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(120px);
  opacity: 0.06;
  animation: orbDrift 20s ease-in-out infinite;
}
.orb-purple {
  width: 500px; height: 500px;
  top: -80px; left: 180px;
  background: radial-gradient(circle, #9254de, transparent);
  animation-delay: 0s;
}
.orb-blue {
  width: 400px; height: 400px;
  bottom: -60px; right: 100px;
  background: radial-gradient(circle, #597ef7, transparent);
  animation-delay: -7s;
}
.orb-cyan {
  width: 350px; height: 350px;
  top: 45%; left: 55%;
  background: radial-gradient(circle, #36cfc9, transparent);
  animation-delay: -14s;
}
@keyframes orbDrift {
  0%, 100% { transform: translate(0, 0) scale(1); }
  25% { transform: translate(30px, -40px) scale(1.15); }
  50% { transform: translate(-20px, 20px) scale(0.9); }
  75% { transform: translate(-40px, -10px) scale(1.1); }
}

/* ===== 侧边栏 ===== */
.layout-sider {
  position: relative;
  z-index: 1;
  background: linear-gradient(180deg, rgba(10,14,23,0.96) 0%, rgba(15,20,38,0.94) 100%) !important;
  backdrop-filter: blur(20px) saturate(140%);
  -webkit-backdrop-filter: blur(20px) saturate(140%);
}
.layout-sider::after {
  content: '';
  position: absolute;
  right: 0; top: 0; bottom: 0;
  width: 1px;
  background: linear-gradient(180deg, transparent, rgba(146,84,222,0.4), rgba(89,126,247,0.3), rgba(54,207,201,0.2), transparent);
  animation: borderShimmer 8s ease-in-out infinite;
}
@keyframes borderShimmer {
  0%, 100% { opacity: 0.6; }
  50% { opacity: 1; }
}
.logo {
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #fff;
  font-size: 18px;
  font-weight: bold;
  border-bottom: 1px solid rgba(255,255,255,0.06);
  background: rgba(255,255,255,0.02);
}
.logo-icon { font-size: 24px; }
.logo-text { white-space: nowrap; letter-spacing: 1px; }

/* 菜单 */
.side-menu {
  background: transparent !important;
  border-right: none !important;
}
.side-menu :deep(.ant-menu-item) {
  color: rgba(255,255,255,0.5) !important;
  margin: 2px 8px;
  border-radius: 8px;
  transition: all 0.2s;
}
.side-menu :deep(.ant-menu-item:hover) {
  color: rgba(255,255,255,0.85) !important;
  background: rgba(255,255,255,0.04) !important;
}
.side-menu :deep(.ant-menu-item-selected) {
  background: linear-gradient(135deg, rgba(146,84,222,0.2), rgba(89,126,247,0.12), rgba(54,207,201,0.08)) !important;
  color: #fff !important;
  box-shadow: inset 0 0 0 1px rgba(146,84,222,0.15);
}
.side-menu :deep(.ant-menu-item-selected::after) {
  display: none;
}

/* ===== 顶部栏 ===== */
.header {
  position: relative;
  z-index: 1;
  background: rgba(15,20,35,0.6) !important;
  backdrop-filter: blur(24px) saturate(150%);
  -webkit-backdrop-filter: blur(24px) saturate(150%);
  padding: 0 24px;
  display: flex;
  align-items: center;
  border-bottom: 1px solid rgba(255,255,255,0.06);
}
.header::after {
  content: '';
  position: absolute;
  bottom: 0; left: 0; right: 0;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(146,84,222,0.2), rgba(54,207,201,0.2), transparent);
  animation: borderShimmer 8s ease-in-out infinite;
  pointer-events: none;
}
.trigger {
  font-size: 18px;
  cursor: pointer;
  color: rgba(255,255,255,0.45);
  transition: color 0.2s;
}
.trigger:hover { color: rgba(255,255,255,0.85); }
.header-title {
  margin-left: 16px;
  font-size: 15px;
  font-weight: 600;
  color: rgba(255,255,255,0.8);
}
.header-right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 14px;
}
.user-dropdown {
  color: rgba(255,255,255,0.6);
  cursor: pointer;
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 4px;
}
.user-dropdown:hover { color: rgba(255,255,255,0.9); }

/* ===== 内容区 ===== */
.content {
  margin: 12px;
  overflow: auto;
  flex: 1;
  background: transparent;
}
</style>
