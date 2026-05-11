<template>
  <div class="paper-detail" v-if="paper">
    <a-page-header
      :title="paper.title"
      sub-title="论文详情"
      @back="() => $router.push('/papers')"
    >
      <template #tags>
        <a-tag :color="statusColor(paper.status)">{{ statusText(paper.status) }}</a-tag>
      </template>
    </a-page-header>

    <a-row :gutter="16">
      <!-- 左侧：论文内容 -->
      <a-col :span="17">
        <a-card title="📄 论文内容" :bordered="false">
          <a-tabs v-model:activeKey="activeTab">
            <a-tab-pane key="content" tab="正文">
              <div v-if="paper.content" class="markdown-body paper-content-scroll">
                <MarkdownRender :content="paper.content" />
              </div>
              <a-empty v-else description="暂无内容" />
            </a-tab-pane>
            <a-tab-pane key="sections" tab="章节概览">
              <a-table
                v-if="paperSections.length"
                :dataSource="paperSections"
                :columns="sectionColumns"
                rowKey="title"
                :pagination="false"
              >
                <template #bodyCell="{ column, record }">
                  <template v-if="column.key === 'preview'">
                    {{ record.content?.substring(0, 100) }}...
                  </template>
                </template>
              </a-table>
              <a-empty v-else description="暂无章节" />
            </a-tab-pane>
          </a-tabs>
        </a-card>
      </a-col>

      <!-- 右侧：任务记录 -->
      <a-col :span="7">
        <a-card title="⚡ 执行记录" :bordered="false">
          <a-timeline v-if="tasks.length">
            <a-timeline-item
              v-for="(task, i) in tasks"
              :key="i"
              :color="task.status === 'COMPLETED' ? 'green' : task.status === 'FAILED' ? 'red' : 'blue'"
            >
              <div class="task-item">
                <a-tag :color="roleColor(task.agentRole)">{{ task.description }}</a-tag>
                <div class="task-status">
                  <a-tag :color="task.status === 'COMPLETED' ? 'success' : task.status === 'FAILED' ? 'error' : 'processing'">
                    {{ task.status }}
                  </a-tag>
                  <span class="task-time" v-if="task.durationMs">{{ task.durationMs }}ms</span>
                </div>
              </div>
            </a-timeline-item>
          </a-timeline>
          <a-empty v-else description="暂无执行记录" />
        </a-card>

        <!-- 审稿意见 -->
        <a-card v-if="paperStore.currentPaper?.reviewComments?.length" title="🔎 审稿意见" :bordered="false" style="margin-top: 16px">
          <div v-for="(comment, i) in paperStore.currentPaper.reviewComments" :key="i" class="review-item">
            <div class="review-index">#{{ i + 1 }}</div>
            <div class="review-text">{{ comment }}</div>
          </div>
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { usePaperStore } from '@/stores/paper'
import MarkdownRender from '@/components/MarkdownRender.vue'

const route = useRoute()
const paperStore = usePaperStore()

const activeTab = ref('content')

const paper = computed(() => paperStore.currentPaper)
const tasks = computed(() => paperStore.currentTasks || [])

const paperSections = computed(() => {
  if (!paper.value?.content) return []
  // 按 ## 标题拆分
  const sections = []
  const lines = paper.value.content.split('\n')
  let current = null
  for (const line of lines) {
    if (line.startsWith('## ')) {
      if (current) sections.push(current)
      current = { title: line.replace('## ', '').trim(), content: '' }
    } else if (current) {
      current.content += line + '\n'
    }
  }
  if (current) sections.push(current)
  return sections
})

const sectionColumns = [
  { title: '章节', dataIndex: 'title', key: 'title' },
  { title: '内容预览', key: 'preview' }
]

onMounted(async () => {
  const id = Number(route.params.id)
  if (id) {
    await paperStore.fetchPaperDetail(id)
    // 获取任务记录
    try {
      const { getPaperTasks } = await import('@/api')
      const res = await getPaperTasks(id)
      paperStore.currentTasks = res.data?.tasks || []
    } catch (e) {
      console.error('获取任务记录失败:', e)
    }
  }
})

function statusColor(status) {
  const map = { DRAFT: 'default', REVIEWING: 'processing', COMPLETED: 'success', FAILED: 'error' }
  return map[status] || 'default'
}

function statusText(status) {
  const map = { DRAFT: '草稿', REVIEWING: '审阅中', COMPLETED: '已完成', FAILED: '失败' }
  return map[status] || status
}

function roleColor(role) {
  const colors = { SUPERVISOR: 'purple', RESEARCHER: 'blue', WRITER: 'green', REVIEWER: 'orange', POLISHER: 'cyan' }
  return colors[role] || 'default'
}
</script>

<style scoped>
.paper-detail {
  max-width: 100%;
  margin: 0 auto;
  overflow: hidden;
}
.paper-content-scroll {
  max-height: calc(100vh - 350px);
  overflow-y: auto;
  padding-right: 8px;
  scroll-behavior: smooth;
}
.markdown-body pre {
  white-space: pre-wrap;
  word-wrap: break-word;
  word-break: break-word;
  max-height: 800px;
  overflow-y: auto;
  background: #f9f9f9;
  padding: 16px;
  border-radius: 8px;
  font-size: 13px;
  line-height: 1.7;
}
.task-item {
  font-size: 13px;
}
.task-status {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
}
.task-time {
  color: #999;
  font-size: 12px;
}
.review-item {
  padding: 8px 0;
  border-bottom: 1px solid #f0f0f0;
}
.review-index {
  font-weight: bold;
  color: #1890ff;
  margin-bottom: 4px;
}
.review-text {
  font-size: 13px;
  color: #666;
  line-height: 1.5;
}
</style>
