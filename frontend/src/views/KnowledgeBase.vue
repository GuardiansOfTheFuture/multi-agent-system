<template>
  <div class="kb-app">
    <header class="kb-header">
      <span class="kb-logo">📚 知识库</span>
      <a-tabs v-model:activeKey="scopeTab" size="small" style="margin:0 16px">
        <a-tab-pane key="private" tab="我的文献" />
        <a-tab-pane key="shared" tab="共享库" />
      </a-tabs>
      <span style="flex:1" />
      <a-button size="small" type="primary" @click="showUpload = true">
        <upload-outlined /> 上传文献
      </a-button>
    </header>

    <div class="kb-body">
      <aside class="kb-left">
        <a-spin :spinning="loading">
          <div
            v-for="doc in docs"
            :key="doc.id"
            class="kb-doc-item"
            :class="{ active: activeDoc?.id === doc.id }"
            @click="selectDoc(doc)"
          >
            <span class="kb-doc-icon">{{ fileIcon(doc.fileType) }}</span>
            <div class="kb-doc-info">
              <div class="kb-doc-title">{{ doc.title || doc.filename }}</div>
              <div class="kb-doc-meta">
                <span v-if="doc.authors">{{ doc.authors }}</span>
                <span v-if="doc.year">({{ doc.year }})</span>
                <a-tag size="small" color="default">{{ doc.fileType }}</a-tag>
              </div>
              <div class="kb-doc-stat">{{ doc.totalChunks || 0 }} 块 · {{ (doc.totalChars || 0).toLocaleString() }} 字</div>
            </div>
            <a-popconfirm title="删除？" @confirm="delDoc(doc.id)" v-if="scopeTab === 'private'">
              <a-button type="text" size="small" danger><delete-outlined /></a-button>
            </a-popconfirm>
          </div>
          <a-empty v-if="!docs.length && !loading" description="暂无文献" :image-style="{ height: '40px' }" />
        </a-spin>
      </aside>

      <main class="kb-main">
        <template v-if="activeDoc">
          <div class="kb-doc-detail">
            <h3>{{ activeDoc.title || activeDoc.filename }}</h3>
            <p class="kb-doc-meta-line" v-if="activeDoc.authors || activeDoc.year">
              {{ activeDoc.authors || '' }}{{ activeDoc.year ? ' (' + activeDoc.year + ')' : '' }}
            </p>
            <a-divider style="margin:8px 0;border-color:rgba(255,255,255,0.04)" />
            <a-spin :spinning="chunksLoading">
              <a-collapse v-if="chunks.length" :bordered="false" ghost accordion expand-icon-position="end">
                <a-collapse-panel v-for="chunk in chunks" :key="chunk.chunkIndex"
                  :header="'#' + (chunk.chunkIndex + 1) + ' · ' + (chunk.charCount || 0) + ' 字'">
                  <p class="kb-chunk-text">{{ chunk.text }}</p>
                </a-collapse-panel>
              </a-collapse>
              <a-empty v-if="!chunks.length && !chunksLoading" description="暂无分块" :image-style="{ height: '30px' }" />
            </a-spin>
          </div>
        </template>
        <div v-else class="kb-empty">
          <span style="font-size:48px">📖</span>
          <p style="color:rgba(255,255,255,0.3);margin-top:12px">选择左侧文献查看分块</p>
        </div>
      </main>
    </div>

    <!-- 上传弹窗 -->
    <a-modal v-model:open="showUpload" title="上传文献到知识库" :footer="null" @cancel="resetUpload">
      <a-form layout="vertical">
        <a-form-item label="选择文件">
          <a-upload
            :before-upload="handleUpload"
            :show-upload-list="false"
            accept=".pdf,.docx,.doc,.md,.txt,.html"
          >
            <a-button type="dashed" :loading="uploading" block>
              <upload-outlined /> {{ uploading ? '解析入库中...' : '点击选择文件' }}
            </a-button>
          </a-upload>
          <div style="font-size:11px;color:rgba(255,255,255,0.3);margin-top:6px">
            支持 PDF / Word / Markdown / TXT / HTML
          </div>
        </a-form-item>
        <a-form-item label="范围">
          <a-radio-group v-model:value="uploadScope">
            <a-radio value="PRIVATE">个人库</a-radio>
            <a-radio value="SHARED">共享库</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-alert v-if="uploadResult" :message="uploadResult" type="success" show-icon style="margin-top:8px" />
        <a-alert v-if="uploadError" :message="uploadError" type="error" show-icon style="margin-top:8px" closable @close="uploadError=''" />
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { UploadOutlined, DeleteOutlined } from '@ant-design/icons-vue'

const scopeTab = ref('private')
const docs = ref([])
const loading = ref(false)
const activeDoc = ref(null)
const chunks = ref([])
const chunksLoading = ref(false)
const showUpload = ref(false)
const uploading = ref(false)
const uploadScope = ref('PRIVATE')
const uploadResult = ref('')
const uploadError = ref('')

function fileIcon(ext) {
  const m = { pdf: '📕', docx: '📄', doc: '📄', md: '📝', txt: '📃', html: '🌐' }
  return m[(ext || '').toLowerCase()] || '📁'
}

async function loadDocs() {
  loading.value = true
  try {
    const url = scopeTab.value === 'private' ? '/api/knowledge/my' : '/api/knowledge/shared'
    const token = localStorage.getItem('paperai_token')
    const resp = await fetch(url, { headers: token ? { Authorization: 'Bearer ' + token } : {} })
    const res = await resp.json()
    docs.value = res.data || []
  } catch (e) { console.error(e) }
  finally { loading.value = false }
}

async function selectDoc(doc) {
  activeDoc.value = doc
  chunks.value = []
  chunksLoading.value = true
  try {
    const token = localStorage.getItem('paperai_token')
    const resp = await fetch('/api/knowledge/' + doc.id + '/chunks', { headers: token ? { Authorization: 'Bearer ' + token } : {} })
    const res = await resp.json()
    console.log('chunks response:', res)
    chunks.value = res.data || []
  } catch (e) { console.error(e) }
  finally { chunksLoading.value = false }
}

async function handleUpload(file) {
  uploading.value = true
  uploadResult.value = ''
  uploadError.value = ''
  try {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('scope', uploadScope.value)
    const token = localStorage.getItem('paperai_token')
    const resp = await fetch('/api/knowledge/upload', {
      method: 'POST',
      headers: token ? { Authorization: 'Bearer ' + token } : {},
      body: formData
    })
    const res = await resp.json()
    if (res.code === 200) {
      uploadResult.value = `入库成功：${res.data.totalChunks} 块，${(res.data.totalChars || 0).toLocaleString()} 字`
      loadDocs()
    } else {
      uploadError.value = res.message || '入库失败'
    }
  } catch (e) {
    uploadError.value = '上传失败: ' + (e.message || '网络错误')
  } finally { uploading.value = false }
  return false
}

async function delDoc(id) {
  try {
    const token = localStorage.getItem('paperai_token')
    const resp = await fetch('/api/knowledge/' + id, { method: 'DELETE', headers: token ? { Authorization: 'Bearer ' + token } : {} })
    const res = await resp.json()
    if (res.code === 200) { message.success('已删除'); loadDocs() }
    else { message.error(res.message) }
  } catch (e) { message.error('删除失败') }
}

function resetUpload() { uploadResult.value = ''; uploadError.value = '' }

watch(scopeTab, () => { activeDoc.value = null; chunks.value = []; loadDocs() })
onMounted(() => loadDocs())
</script>

<style scoped>
.kb-app { height:100%; display:flex; flex-direction:column; overflow:hidden; }
.kb-header {
  display:flex; align-items:center; padding:12px 24px;
  background:rgba(15,20,38,0.7); border-bottom:1px solid rgba(255,255,255,0.06); flex-shrink:0;
}
.kb-logo { font-size:16px; font-weight:600; color:rgba(255,255,255,0.9); }
.kb-body { flex:1; display:flex; overflow:hidden; min-height:0; }
.kb-left {
  width:300px; flex-shrink:0; overflow-y:auto; padding:8px;
  background:rgba(15,20,38,0.3); border-right:1px solid rgba(255,255,255,0.05);
}
.kb-doc-item {
  display:flex; align-items:flex-start; gap:10px; padding:10px;
  border-radius:8px; cursor:pointer; transition:background 0.2s;
  border:1px solid transparent;
}
.kb-doc-item:hover { background:rgba(255,255,255,0.03); }
.kb-doc-item.active { background:rgba(114,46,209,0.1); border-color:rgba(114,46,209,0.2); }
.kb-doc-icon { font-size:24px; flex-shrink:0; margin-top:2px; }
.kb-doc-info { flex:1; min-width:0; }
.kb-doc-title { font-size:13px; font-weight:500; color:rgba(255,255,255,0.85); overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.kb-doc-meta { font-size:11px; color:rgba(255,255,255,0.4); margin-top:2px; display:flex; gap:6px; align-items:center; }
.kb-doc-stat { font-size:10px; color:rgba(255,255,255,0.25); margin-top:2px; }
.kb-main {
  flex:1; min-width:0; overflow-y:auto; padding:20px 32px;
  background:linear-gradient(180deg, rgba(18,24,42,0.85) 0%, rgba(15,20,38,0.75) 100%);
}
.kb-doc-detail h3 { font-size:18px; color:rgba(255,255,255,0.9); margin:0 0 4px; }
.kb-doc-meta-line { font-size:13px; color:rgba(255,255,255,0.45); margin:0; }
.kb-chunk-text { font-size:13px; line-height:1.7; color:rgba(255,255,255,0.75); white-space:pre-wrap; margin:0; }
.kb-empty { display:flex; flex-direction:column; align-items:center; justify-content:center; height:100%; }
</style>
