<template>
  <a-layout style="min-height: 100vh">
    <!-- 侧边栏 -->
    <a-layout-sider v-model:collapsed="collapsed" :trigger="null" collapsible theme="dark" width="220">
      <div class="logo">
        <div class="logo-icon">📄</div>
        <span v-if="!collapsed" class="logo-text">PaperAI</span>
      </div>
      <a-menu
        v-model:selectedKeys="selectedKeys"
        theme="dark"
        mode="inline"
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
        <a-menu-item key="/agents">
          <template #icon><robot-outlined /></template>
          <span>Agent 管理</span>
        </a-menu-item>
      </a-menu>
    </a-layout-sider>

    <!-- 主区域 -->
    <a-layout style="height: 100vh; overflow: hidden; display: flex; flex-direction: column">
      <a-layout-header class="header">
        <menu-unfold-outlined
          v-if="collapsed"
          class="trigger"
          @click="collapsed = !collapsed"
        />
        <menu-fold-outlined
          v-else
          class="trigger"
          @click="collapsed = !collapsed"
        />
        <span class="header-title">{{ pageTitle }}</span>
        <div class="header-right">
          <a-tag color="blue">通义千问 qwen3.6-plus</a-tag>
        </div>
      </a-layout-header>

      <a-layout-content class="content">
        <router-view />
      </a-layout-content>

      <a-layout-footer class="footer">
        PaperAI ©2026 Multi-Agent 论文写作协作系统
      </a-layout-footer>
    </a-layout>
  </a-layout>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import {
  EditOutlined,
  FileTextOutlined,
  RobotOutlined,
  MenuUnfoldOutlined,
  MenuFoldOutlined
} from '@ant-design/icons-vue'

const router = useRouter()
const route = useRoute()

const collapsed = ref(false)
const selectedKeys = ref(['/write'])

const pageTitle = computed(() => route.meta?.title || 'PaperAI')

function handleMenuClick({ key }) {
  selectedKeys.value = [key]
  router.push(key)
}
</script>

<style scoped>
.logo {
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #fff;
  font-size: 18px;
  font-weight: bold;
  border-bottom: 1px solid rgba(255,255,255,0.1);
}
.logo-icon { font-size: 28px; }
.logo-text { white-space: nowrap; }

.header {
  background: #fff;
  padding: 0 24px;
  display: flex;
  align-items: center;
  box-shadow: 0 1px 4px rgba(0,0,0,0.08);
}
.trigger {
  font-size: 18px;
  cursor: pointer;
  color: #666;
}
.trigger:hover { color: #1890ff; }
.header-title {
  margin-left: 16px;
  font-size: 16px;
  font-weight: 500;
  color: #333;
}
.header-right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 12px;
}

.content {
  margin: 16px 24px 0 24px;
  overflow-y: auto;
  height: calc(100vh - 64px - 48px - 16px);
}
.content:deep(.ant-card) {
  margin-bottom: 0;
}

.footer {
  text-align: center;
  color: #999;
  font-size: 12px;
  height: 48px;
  line-height: 48px;
  padding: 0 24px;
  background: #fafafa;
  flex-shrink: 0;
}
</style>
