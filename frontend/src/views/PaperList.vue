<template>
  <div class="paper-list">
    <a-card title="📚 论文列表" :bordered="false">
      <template #extra>
        <a-button type="primary" size="small" @click="fetchData">
          <template #icon><reload-outlined /></template>
          刷新
        </a-button>
      </template>

      <a-table
        :dataSource="paperStore.paperList"
        :columns="columns"
        :loading="paperStore.loading"
        rowKey="id"
        :pagination="{ pageSize: 10 }"
      >
        <template #bodyCell="{ column, record }">
          <!-- 状态 -->
          <template v-if="column.key === 'status'">
            <a-tag :color="statusColor(record.status)">{{ statusText(record.status) }}</a-tag>
          </template>

          <!-- 时间 -->
          <template v-if="column.key === 'createdAt'">
            {{ formatTime(record.createdAt) }}
          </template>

          <!-- 操作 -->
          <template v-if="column.key === 'action'">
            <a-space>
              <a-button type="link" size="small" @click="viewPaper(record.id)">查看</a-button>
              <a-popconfirm title="确定删除?" @confirm="handleDelete(record.id)">
                <a-button type="link" size="small" danger>删除</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>

        <template #emptyText>
          <a-empty description="暂无论文，去写一篇吧！" />
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { usePaperStore } from '@/stores/paper'
import { message } from 'ant-design-vue'
import { ReloadOutlined } from '@ant-design/icons-vue'

const router = useRouter()
const paperStore = usePaperStore()

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 70 },
  { title: '论文标题', dataIndex: 'title', key: 'title', ellipsis: true },
  { title: '关键词', dataIndex: 'keywords', key: 'keywords', ellipsis: true },
  { title: '状态', key: 'status', width: 100 },
  { title: '创建时间', key: 'createdAt', width: 180 },
  { title: '操作', key: 'action', width: 140 }
]

onMounted(() => {
  paperStore.fetchPaperList()
})

function fetchData() {
  paperStore.fetchPaperList()
}

function statusColor(status) {
  const map = { DRAFT: 'default', REVIEWING: 'processing', COMPLETED: 'success', FAILED: 'error' }
  return map[status] || 'default'
}

function statusText(status) {
  const map = { DRAFT: '草稿', REVIEWING: '审阅中', COMPLETED: '已完成', FAILED: '失败' }
  return map[status] || status
}

function formatTime(time) {
  if (!time) return '-'
  return new Date(time).toLocaleString('zh-CN')
}

function viewPaper(id) {
  router.push(`/paper/${id}`)
}

async function handleDelete(id) {
  try {
    await paperStore.removePaper(id)
    message.success('删除成功')
  } catch (e) {
    message.error('删除失败: ' + e.message)
  }
}
</script>
