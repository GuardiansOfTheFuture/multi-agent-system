<template>
  <div class="paper-list">
    <a-card title="📚 论文列表" :bordered="false">
      <template #extra>
        <a-space>
          <a-button type="primary" size="small" @click="showCreateModal = true">
            <template #icon><plus-outlined /></template>
            创建论文
          </a-button>
          <a-button size="small" @click="fetchData">
            <template #icon><reload-outlined /></template>
            刷新
          </a-button>
        </a-space>
      </template>

      <a-table
        :dataSource="paperStore.paperList"
        :columns="columns"
        :loading="paperStore.loading"
        rowKey="id"
        :pagination="{ current: paperStore.paperPage, pageSize: paperStore.paperSize, total: paperStore.paperTotal, showTotal: t => '共 ' + t + ' 篇', onChange: p => paperStore.fetchPaperList(p) }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <a-tag :color="statusColor(record.status)">{{ statusText(record.status) }}</a-tag>
          </template>
          <template v-if="column.key === 'flowId'">
            <span style="color:rgba(255,255,255,0.5)">{{ record.flowId || '默认' }}</span>
          </template>
          <template v-if="column.key === 'createdAt'">
            {{ formatTime(record.createdAt) }}
          </template>
          <template v-if="column.key === 'action'">
            <a-space>
              <a-button type="link" size="small" @click="viewPaper(record.id)">查看</a-button>
              <a-button v-if="record.status === 'DRAFT' || record.status === 'FAILED'" type="link" size="small" style="color:#52c41a" @click="goWrite(record.id)">执行</a-button>
              <a-popconfirm title="确定删除?" @confirm="handleDelete(record.id)">
                <a-button type="link" size="small" danger>删除</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
        <template #emptyText>
          <a-empty description="暂无论文，去创建一篇吧！" />
        </template>
      </a-table>
    </a-card>

    <!-- 创建论文弹窗 -->
    <a-modal
      v-model:open="showCreateModal"
      title="📝 创建论文任务"
      width="460px"
      :footer="null"
      wrap-class-name="save-flow-modal"
    >
      <a-form layout="vertical" size="middle" class="create-form">
        <a-form-item label="论文主题" required>
          <a-input v-model:value="createForm.topic" placeholder="如：深度学习在医疗影像分割中的应用" />
        </a-form-item>
        <a-form-item label="详细描述">
          <a-textarea v-model:value="createForm.description" :rows="2" placeholder="研究方向、背景、预期目标..." />
        </a-form-item>
        <a-form-item label="关键词">
          <a-input v-model:value="createForm.keywords" placeholder="用逗号分隔" />
        </a-form-item>
        <a-form-item label="写作流程">
          <a-select v-model:value="createForm.flowId" placeholder="选择流程（可选）">
            <a-select-option value="">默认标准流程</a-select-option>
            <a-select-option v-for="f in flowOptions" :key="f.value" :value="f.value">
              {{ f.label }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <div style="display:flex;gap:12px;justify-content:flex-end;margin-top:16px">
          <a-button @click="showCreateModal = false">取消</a-button>
          <a-button type="primary" :loading="creating" :disabled="!createForm.topic.trim()" @click="handleCreatePaper">创建任务</a-button>
        </div>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { usePaperStore } from '@/stores/paper'
import { createPaper, getFlowList } from '@/api'
import { message } from 'ant-design-vue'
import { ReloadOutlined, PlusOutlined } from '@ant-design/icons-vue'

const router = useRouter()
const paperStore = usePaperStore()

const showCreateModal = ref(false)
const creating = ref(false)
const createForm = reactive({ topic: '', description: '', keywords: '', flowId: '' })
const flowOptions = ref([])

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 70 },
  { title: '标题', dataIndex: 'title', key: 'title', ellipsis: true },
  { title: '状态', key: 'status', width: 90 },
  { title: '流程', key: 'flowId', width: 100 },
  { title: '创建时间', key: 'createdAt', width: 160 },
  { title: '操作', key: 'action', width: 160 }
]

onMounted(async () => {
  paperStore.fetchPaperList()
  try {
    const res = await getFlowList()
    flowOptions.value = (res.data || []).map(f => ({ value: f.id, label: f.name }))
  } catch (_) {}
})

function fetchData() { paperStore.fetchPaperList() }

async function handleCreatePaper() {
  const topic = createForm.topic.trim()
  if (!topic) return
  creating.value = true
  try {
    const data = {
      topic,
      description: createForm.description.trim(),
      keywords: createForm.keywords.trim(),
      flowId: createForm.flowId || undefined
    }
    await createPaper(data)
    message.success('论文任务已创建')
    showCreateModal.value = false
    createForm.topic = ''; createForm.description = ''; createForm.keywords = ''; createForm.flowId = ''
    paperStore.fetchPaperList()
  } catch (e) { message.error('创建失败: ' + e.message) }
  finally { creating.value = false }
}

function goWrite(id) {
  router.push(`/write?paperId=${id}`)
}

function statusColor(status) {
  const map = { DRAFT: 'default', REVIEWING: 'processing', COMPLETED: 'success', FAILED: 'error' }
  return map[status] || 'default'
}
function statusText(status) {
  const map = { DRAFT: '待执行', REVIEWING: '执行中', COMPLETED: '已完成', FAILED: '失败' }
  return map[status] || status
}
function formatTime(time) {
  if (!time) return '-'
  return new Date(time).toLocaleString('zh-CN')
}
function viewPaper(id) { router.push(`/paper/${id}`) }

async function handleDelete(id) {
  try { await paperStore.removePaper(id); message.success('删除成功') }
  catch (e) { message.error('删除失败: ' + e.message) }
}
</script>

<style scoped>
.paper-list { height: 100%; display: flex; flex-direction: column; }
.paper-list :deep(.ant-card) { flex: 1; display: flex; flex-direction: column; min-height: 0; }
.paper-list :deep(.ant-card-body) { flex: 1; overflow-y: auto; padding: 16px 24px; }
.paper-list :deep(.ant-table-container) { height: 100%; display: flex; flex-direction: column; }
.paper-list :deep(.ant-table-body) { flex: 1; overflow-y: auto !important; }
.create-form :deep(.ant-form-item-label > label) { color: rgba(255,255,255,0.85) !important; }
</style>
