<template>
  <div class="kg-app" @click="bgClick">
    <header class="kg-top">
      <span class="kg-logo">🔗 知识图谱</span>
      <a-select v-model:value="currentId" :options="kgOptions" style="width:200px" placeholder="选择图谱…" @change="loadKg" popup-class-name="kg-select-popup" />
      <a-button size="small" type="dashed" @click="handleNew">+ 新建</a-button>
      <a-tooltip placement="bottom"><template #title><div v-for="c in stats" :key="c.type">{{c.icon}} {{c.name}}: {{c.count}}</div></template><span class="kg-stat">{{nodes.length}}实体 / {{edges.length}}关系</span></a-tooltip>
      <a-input-search v-model:value="searchText" placeholder="搜索…" size="small" style="width:150px" @search="searchNode" allowClear @clear="clearSearch" />
      <span style="flex:1" />
      <a-space :size="4">
        <a-tooltip title="居中"><a-button size="small" @click="fitView">⊡</a-button></a-tooltip>
        <a-tooltip title="聚焦"><a-button size="small" @click="focusSel">◎</a-button></a-tooltip>
        <a-tooltip title="全屏"><a-button size="small" @click="toggleFS">⛶</a-button></a-tooltip>
        <a-tooltip title="导出PNG"><a-button size="small" @click="exportPNG">📷</a-button></a-tooltip>
        <a-dropdown>
          <a-button size="small">⇲</a-button>
          <template #overlay>
            <a-menu>
              <a-menu-item @click="exportJSON">导出 JSON</a-menu-item>
              <a-menu-item @click="exportCSV">导出 CSV</a-menu-item>
              <a-menu-divider />
              <a-menu-item @click="openImport">📥 导入 JSON</a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>
        <a-divider type="vertical" style="border-color:rgba(255,255,255,0.08);height:18px;margin:0 4px" />
        <a-tooltip title="AI"><a-button size="small" type="primary" @click="openExtract" :loading="extracting">🤖</a-button></a-tooltip>
        <a-tooltip title="保存"><a-button size="small" type="primary" :disabled="saving" @click="save">💾</a-button></a-tooltip>
        <a-tooltip title="复制"><a-button size="small" @click="dup" v-if="currentId">📋</a-button></a-tooltip>
        <a-popconfirm title="删除？" @confirm="del" v-if="currentId"><a-button size="small" danger>🗑</a-button></a-popconfirm>
        <a-tooltip title="撤销"><a-button size="small" :disabled="!canUndo" @click="undo">↩</a-button></a-tooltip>
        <a-tooltip title="重做"><a-button size="small" :disabled="!canRedo" @click="redo">↪</a-button></a-tooltip>
        <a-popover title="图例" trigger="click" placement="bottomRight">
          <template #content><div class="legend"><div v-for="e in E" :key="e.type" class="legend-r"><span class="legend-d" :style="{background:e.color}" />{{e.name}}</div></div></template>
          <a-button size="small">📖</a-button>
        </a-popover>
      </a-space>
    </header>
    <div class="kg-body">
      <aside class="kg-left">
        <div class="left-hd"><span>筛选</span></div>
        <div class="left-sec">
          <div class="left-label">实体类型<span v-if="ef.length<E.length" class="left-badge">{{ef.length}}/{{E.length}}</span></div>
          <div v-for="e in E" :key="e.type" class="left-row" :class="{on:ef.includes(e.type)}" @click="toggleEf(e.type)"><span class="left-dot" :style="{background:e.color}"/><span>{{e.icon}} {{e.name}}</span></div>
        </div>
        <div class="left-sec">
          <div class="left-label">关系类型<span v-if="rf.length<R.length" class="left-badge">{{rf.length}}/{{R.length}}</span></div>
          <div v-for="r in R" :key="r.type" class="left-row" :class="{on:rf.includes(r.type)}" @click="toggleRf(r.type)"><span class="left-dot" :style="{background:r.color}"/><span>→ {{r.name}}</span></div>
        </div>
        <a-button size="small" @click="clearFilters" style="width:100%;margin-top:8px">重置</a-button>
      </aside>
      <div class="kg-main" ref="mainRef">
        <svg ref="svgRef" class="kg-svg"></svg>
        <div class="kg-ctx" v-if="ctx.show" :style="{left:ctx.x+'px',top:ctx.y+'px'}" @click.stop>
          <div class="ctx-item" @click="ctxNewNode">+ 新建实体</div>
          <div class="ctx-item" v-if="ctxNid" @click="ctxLink">🔗 创建关系</div>
          <div class="ctx-item danger" v-if="ctxNid||ctxEid" @click="ctxDel">🗑 删除</div>
        </div>
        <div class="kg-empty" v-if="nodes.length===0">
          <div class="empty-icon">🧭</div><div class="empty-title">还没有图谱数据</div><div class="empty-desc">开始你的知识探索吧</div>
          <a-space style="margin-top:16px" :size="8"><a-button size="small" @click="newEntity">+ 新建实体</a-button><a-button size="small" type="primary" @click="openExtract">🤖 AI 生成</a-button></a-space>
        </div>
      </div>
      <aside class="kg-right" v-if="panelTarget">
        <div class="right-hd"><span>{{panelTarget==='node'?'实体详情':'关系详情'}}</span><span class="right-close" @click="closePanel">✕</span></div>
        <template v-if="panelTarget==='node'&&selNode">
          <div class="right-field"><span class="right-k">名称</span><a-input v-model:value="selNode.data.label" size="small" @change="flushNode"/></div>
          <div class="right-field"><span class="right-k">类型</span><a-select v-model:value="selNode.type" size="small" style="width:100%" :popup-match-select-width="false" popup-class-name="kg-select-popup" :options="typeOpts" @change="flushNode"/></div>
          <div class="right-field"><span class="right-k">ID</span><span class="right-val">{{selNode.id}}</span></div>
          <div class="right-field"><span class="right-k">描述</span><a-textarea v-model:value="selNode.data.desc" :rows="3" size="small" @change="flushNode"/></div>
          <div class="right-field"><span class="right-k">关联</span><span class="right-val">{{relCnt(selNode.id)}} 条</span></div>
          <a-space :size="4" style="margin-top:4px;width:100%;flex-wrap:wrap">
            <a-button size="small" danger @click="removeNode">删除</a-button>
            <a-button size="small" @click="copyId(selNode.id)">复制ID</a-button>
            <a-button size="small" :type="linkMode?'primary':'default'" @click="toggleLink">{{linkMode?'🔗 连线中...':'🔗 连线'}}</a-button>
          </a-space>
        </template>
        <template v-if="panelTarget==='edge'&&selEdge">
          <div class="right-field"><span class="right-k">类型</span><a-select v-model:value="selEdge.data.relationType" size="small" style="width:100%" :popup-match-select-width="false" popup-class-name="kg-select-popup" :options="relOpts" @change="flushEdge"/></div>
          <div class="right-field"><span class="right-k">方向</span><span class="right-val">{{nodeName(selEdge.source)}} → {{nodeName(selEdge.target)}}</span></div>
          <div class="right-field"><span class="right-k">描述</span><a-textarea v-model:value="selEdge.data.desc" :rows="2" size="small" @change="flushEdge"/></div>
          <a-button size="small" danger @click="removeEdge">删除关系</a-button>
        </template>
      </aside>
    </div>
    <footer class="kg-foot">
      <span v-for="c in stats" :key="c.type" class="fstat">{{c.icon}} {{c.name}} {{c.count}}</span>
      <span style="flex:1"/><span v-if="hasFilter" class="fstat" style="color:#7C3AED">已筛选</span>
    </footer>
    <a-modal v-model:open="extractOpen" :title="exPreview?'📋 抽取结果预览':'🤖 AI 知识抽取'" :footer="null" :width="exPreview?900:520" @cancel="closeExtract">
      <!-- 配置页 -->
      <template v-if="!exPreview">
        <a-row :gutter="16">
          <a-col :span="12">
            <div class="ex-label">抽取实体类型</div>
            <a-checkbox-group v-model:value="exEntityTypes" style="display:flex;flex-direction:column;gap:2px">
              <a-checkbox v-for="e in E" :key="e.type" :value="e.type"><span :style="{color:e.color}">{{e.icon}} {{e.name}}</span></a-checkbox>
            </a-checkbox-group>
          </a-col>
          <a-col :span="12">
            <div class="ex-label">抽取关系类型</div>
            <a-checkbox-group v-model:value="exRelationTypes" style="display:flex;flex-direction:column;gap:2px">
              <a-checkbox v-for="r in R" :key="r.type" :value="r.type"><span :style="{color:r.color}">→ {{r.name}}</span></a-checkbox>
            </a-checkbox-group>
          </a-col>
        </a-row>
        <div class="ex-label" style="margin-top:12px">置信度阈值: {{exConf.toFixed(1)}}</div>
        <a-slider v-model:value="exConf" :min="0.5" :max="1.0" :step="0.1" />
        <a-divider style="margin:12px 0;border-color:rgba(255,255,255,0.06)" />
        <a-tabs v-model:activeKey="exInputTab" size="small" type="card">
          <a-tab-pane key="text" tab="📝 文本粘贴">
            <a-textarea v-model:value="extractText" :rows="6" placeholder="粘贴论文摘要或全文…" style="margin-top:8px"/>
          </a-tab-pane>
          <a-tab-pane key="file" tab="📄 文件上传">
            <div style="margin-top:8px;padding:20px;border:1px dashed rgba(255,255,255,0.15);border-radius:6px;text-align:center">
              <template v-if="!exFileResult">
                <a-upload
                  :before-upload="handleFileUpload"
                  :show-upload-list="false"
                  accept=".pdf,.docx,.md,.txt"
                >
                  <a-button type="dashed">
                    <upload-outlined /> 选择文件
                  </a-button>
                </a-upload>
                <div style="font-size:11px;color:rgba(255,255,255,0.3);margin-top:8px">支持 PDF / Word(.docx) / Markdown(.md) / TXT</div>
              </template>
              <template v-else>
                <div style="text-align:left">
                  <div style="margin-bottom:8px;font-size:12px;color:var(--muted)">
                    📄 {{exFileResult.filename}}<span v-if="exFileResult.pageCount"> · {{exFileResult.pageCount}}页</span> · {{exFileResult.charCount}}字
                  </div>
                  <a-textarea :value="exFileResult.preview" :rows="4" disabled />
                  <a-space style="margin-top:8px">
                    <a-button size="small" @click="exFileResult=null;extractText=''">重新上传</a-button>
                    <a-tag v-if="exFileResult.text" color="green">√ 已加载 {{exFileResult.charCount}} 字</a-tag>
                  </a-space>
                </div>
              </template>
            </div>
          </a-tab-pane>
        </a-tabs>
        <a-form-item label="主题" style="margin-top:8px"><a-input v-model:value="extractTopic" placeholder="可选，辅助AI聚焦"/></a-form-item>
        <div style="text-align:right"><a-button @click="extractOpen=false">取消</a-button><a-button type="primary" @click="doExtract" :loading="extracting" style="margin-left:8px">开始抽取</a-button></div>
      </template>
      <!-- 结果预览页 -->
      <template v-if="exPreview">
        <a-row :gutter="12">
          <a-col :span="12">
            <div class="ex-label">实体 ({{exEntities.length}}) <a-button size="small" type="link" @click="exSelectAllEntities" style="font-size:10px">全选</a-button></div>
            <div class="ex-list">
              <div v-for="(e,i) in exEntities" :key="i" class="ex-item" :class="{sel:e._sel!==false}">
                <a-checkbox :checked="e._sel!==false" @click.stop="e._sel=e._sel===false?true:false" style="margin-right:4px" />
                <a-input v-model:value="e.name" size="small" style="width:70px" @click.stop placeholder="名称" />
                <a-select v-model:value="e.type" size="small" style="width:70px" @click.stop :options="typeOpts" :popup-match-select-width="false" popup-class-name="kg-select-popup" />
                <span class="ex-conf" :style="{color:e.confidence>=0.8?'#52c41a':e.confidence>=0.6?'#faad14':'#ff4d4f'}">{{(e.confidence*100).toFixed(0)}}%</span>
              </div>
            </div>
          </a-col>
          <a-col :span="12">
            <div class="ex-label">关系 ({{exRelations.length}})</div>
            <div class="ex-list">
              <div v-for="(r,i) in exRelations" :key="i" class="ex-item" :class="{sel:r._sel!==false}">
                <a-checkbox :checked="r._sel!==false" @click.stop="r._sel=r._sel===false?true:false" style="margin-right:2px;flex-shrink:0" />
                <span style="font-size:10px;color:var(--muted);flex-shrink:0">{{exEntityName(r.source)}}</span>
                <a-select v-model:value="r.type" size="small" style="width:60px;flex-shrink:0" @click.stop :options="relOpts" :popup-match-select-width="false" popup-class-name="kg-select-popup" />
                <span style="font-size:10px;color:var(--muted);flex-shrink:0">{{exEntityName(r.target)}}</span>
                <span class="ex-conf" :style="{color:r.confidence>=0.8?'#52c41a':r.confidence>=0.6?'#faad14':'#ff4d4f'}">{{(r.confidence*100).toFixed(0)}}%</span>
              </div>
            </div>
          </a-col>
        </a-row>
        <a-divider style="margin:12px 0;border-color:rgba(255,255,255,0.06)" />
        <div style="display:flex;align-items:center;gap:8px">
          <a-radio-group v-model:value="exImportMode" size="small">
            <a-radio-button value="merge">合并到当前图谱</a-radio-button>
            <a-radio-button value="new">创建新图谱</a-radio-button>
          </a-radio-group>
          <span style="flex:1"/>
          <a-button size="small" @click="exPreview=false">返回编辑</a-button>
          <a-button size="small" type="primary" @click="doExtract(true)" :loading="extracting">重新抽取</a-button>
          <a-button size="small" type="primary" @click="doImport">导入选中 ({{exSelectedCount}})</a-button>
        </div>
      </template>
    </a-modal>
    <input type="file" ref="fileInput" accept=".json" style="display:none" @change="handleImportFile" />
  </div>
</template>

<script setup>
import { ref,reactive,computed,onMounted,onUnmounted,nextTick } from 'vue'
import * as d3 from 'd3'
import { listKg as apiListKg,getKg as apiGetKg,createKg as apiCreateKg,updateKg as apiUpdateKg,deleteKg as apiDeleteKg,duplicateKg as apiDupKg,extractKg as apiExtractKg,extractKgFromFile } from '@/api'
import { message } from 'ant-design-vue'
import { UploadOutlined } from '@ant-design/icons-vue'

const E=[{type:'concept',icon:'💡',name:'概念',color:'#FBBF24'},{type:'paper',icon:'📄',name:'论文',color:'#3B82F6'},{type:'author',icon:'👤',name:'作者',color:'#8B5CF6'},{type:'method',icon:'⚙️',name:'方法',color:'#10B981'},{type:'dataset',icon:'📊',name:'数据集',color:'#EC4899'},{type:'topic',icon:'🎯',name:'主题',color:'#EF4444'},{type:'problem',icon:'❓',name:'问题',color:'#F97316'},{type:'finding',icon:'✨',name:'发现',color:'#EAB308'}]
const R=[{type:'uses',name:'使用',color:'#60a5fa'},{type:'extends',name:'扩展',color:'#10b981'},{type:'part_of',name:'属于',color:'#a78bfa'},{type:'contradicts',name:'矛盾',color:'#ef4444'},{type:'related_to',name:'相关',color:'#6b7280'},{type:'proposes',name:'提出',color:'#f59e0b'},{type:'evaluates',name:'评估',color:'#06b6d4'},{type:'cites',name:'引用',color:'#f472b6'}]
const EM=Object.fromEntries(E.map(e=>[e.type,e])),RM=Object.fromEntries(R.map(r=>[r.type,r]))
const typeOpts=E.map(e=>({value:e.type,label:e.icon+' '+e.name})),relOpts=R.map(r=>({value:r.type,label:'→ '+r.name}))

const nodes=ref([]),edges=ref([]),currentId=ref(null),kgList=ref([]),saving=ref(false)
const kgOptions=computed(()=>kgList.value.map(k=>({value:k.id,label:k.name||'未命名'})))
const stats=computed(()=>{const m={};nodes.value.forEach(n=>{const t=n.type||'concept';m[t]=(m[t]||0)+1});return Object.entries(m).sort((a,b)=>b[1]-a[1]).map(([k,v])=>{const e=EM[k]||EM.concept;return{type:k,icon:e.icon,name:e.name,count:v}})})

const ef=ref(E.map(e=>e.type)),rf=ref(R.map(e=>e.type)),hasFilter=computed(()=>ef.value.length<E.length||rf.value.length<R.length)
function toggleEf(t){const i=ef.value.indexOf(t);i>-1?ef.value.splice(i,1):ef.value.push(t);initD3()}
function toggleRf(t){const i=rf.value.indexOf(t);i>-1?rf.value.splice(i,1):rf.value.push(t);initD3()}
function clearFilters(){ef.value=E.map(e=>e.type);rf.value=R.map(e=>e.type);initD3()}

const panelTarget=ref(null),selNode=ref(null),selEdge=ref(null),linkMode=ref(false)
function selN(id){selEdge.value=null;panelTarget.value='node';selNode.value=nodes.value.find(n=>n.id===id)}
function selE(id){selNode.value=null;panelTarget.value='edge';selEdge.value=edges.value.find(e=>e.id===id)}
function closePanel(){panelTarget.value=null;selNode.value=null;selEdge.value=null}
function relCnt(id){return edges.value.filter(e=>(e.source.id||e.source)===id||(e.target.id||e.target)===id).length}
function nodeName(ref){if(!ref)return'';const id=typeof ref==='object'?ref.id:ref;const n=nodes.value.find(x=>x.id===id);return n?.data?.label||id}
function toggleLink(){linkMode.value=!linkMode.value;if(linkMode.value)message.info('请点击目标实体完成连线')}
function flushNode(){if(selNode.value){selNode.value.data={...selNode.value.data,label:selNode.value.data.label,type:selNode.value.type,desc:selNode.value.data.desc};pushHistory();initD3()}}
function flushEdge(){if(selEdge.value){selEdge.value.data={...selEdge.value.data,relationType:selEdge.value.data.relationType,label:selEdge.value.data.relationType,desc:selEdge.value.data.desc};pushHistory();initD3()}}
function removeNode(){const id=selNode.value.id;nodes.value=nodes.value.filter(n=>n.id!==id);edges.value=edges.value.filter(e=>(e.source.id||e.source)!==id&&(e.target.id||e.target)!==id);closePanel();pushHistory();initD3()}
function removeEdge(){const id=selEdge.value.id;edges.value=edges.value.filter(e=>e.id!==id);closePanel();pushHistory();initD3()}
function copyId(id){navigator.clipboard.writeText(id);message.success('已复制')}

const ctx=reactive({show:false,x:0,y:0}),ctxNid=ref(null),ctxEid=ref(null)
function bgClick(){ctx.show=false}
function svgCtx(ev,nid,eid){ctx.show=true;ctx.x=ev.clientX;ctx.y=ev.clientY;ctxNid.value=nid;ctxEid.value=eid}
function ctxNewNode(){ctx.show=false;newEntity()}function ctxLink(){ctx.show=false;if(ctxNid.value){selN(ctxNid.value);toggleLink()}}
function ctxDel(){ctx.show=false;if(ctxEid.value){selE(ctxEid.value);removeEdge()}else if(ctxNid.value){selN(ctxNid.value);removeNode()}}
function newEntity(){const id=`n${Date.now()}`;nodes.value.push({id,type:'concept',data:{type:'concept',label:'新实体',desc:''},x:0,y:0});pushHistory();initD3()}

const h=ref([]),hi=ref(-1),canUndo=computed(()=>hi.value>0),canRedo=computed(()=>hi.value<h.value.length-1)
function pushHistory(){h.value=h.value.slice(0,hi.value+1);h.value.push(JSON.stringify({ns:JSON.parse(JSON.stringify(nodes.value)),es:JSON.parse(JSON.stringify(edges.value))}));if(h.value.length>50)h.value.shift();hi.value=h.value.length-1}
function undo(){if(canUndo.value){hi.value--;restore()}}function redo(){if(canRedo.value){hi.value++;restore()}}
function restore(){const s=JSON.parse(h.value[hi.value]);nodes.value=s.ns;edges.value=s.es;selNode.value=null;selEdge.value=null;initD3()}

async function fetchList(){try{const r=await apiListKg();kgList.value=(r.data||[]).map(k=>({id:k.id,name:k.name}))}catch(_){}}
async function loadKg(id){if(!id)return;try{const r=await apiGetKg(id);const kg=r.data;if(kg.graphData){const g=JSON.parse(kg.graphData);nodes.value=(g.nodes||[]).map(n=>({...n,x:n.x||n.position?.x||0,y:n.y||n.position?.y||0,data:{type:n.type||'concept',label:n.data?.label||'',desc:n.data?.desc||''}}));edges.value=g.edges||[]}else{nodes.value=[];edges.value=[]};closePanel();h.value=[JSON.stringify({ns:JSON.parse(JSON.stringify(nodes.value)),es:JSON.parse(JSON.stringify(edges.value))})];hi.value=0;initD3()}catch(_){message.error('加载失败')}}
async function save(){if(saving.value)return;saving.value=true;try{const gd=JSON.stringify({nodes:JSON.parse(JSON.stringify(nodes.value)),edges:JSON.parse(JSON.stringify(edges.value))});if(currentId.value){await apiUpdateKg(currentId.value,{graphData:gd});message.success('已保存')}else{const r=await apiCreateKg({name:'知识图谱',graphData:gd});currentId.value=r.data.id;message.success('已创建')};await fetchList()}catch(_){}saving.value=false}
async function dup(){if(!currentId.value)return;await apiDupKg(currentId.value);message.success('已复制');await fetchList()}
async function del(){if(!currentId.value)return;await apiDeleteKg(currentId.value);message.success('已删除');await fetchList();handleNew()}
function handleNew(){currentId.value=null;nodes.value=[];edges.value=[];closePanel();initD3();h.value=['[]'];hi.value=0}

const extracting=ref(false),extractOpen=ref(false),extractText=ref(''),extractTopic=ref('')
const exPreview=ref(false),exImportMode=ref('merge')
const exEntityTypes=ref(E.map(e=>e.type)),exRelationTypes=ref(R.map(e=>e.type)),exConf=ref(0.7)
const exEntities=ref([]),exRelations=ref([])
const exInputTab=ref('text'),exFileResult=ref(null)
const exSelectedCount=computed(()=>exEntities.value.filter(e=>e._sel!==false).length+exRelations.value.filter(r=>r._sel!==false).length)
function exSelectAllEntities(){const all=exEntities.value.every(e=>e._sel!==false);exEntities.value.forEach(e=>e._sel=!all)}
function exEntityName(id){const e=exEntities.value.find(x=>x._id===id);return e?e.name:id}
function openExtract(){extractText.value='';extractTopic.value='';exPreview.value=false;exEntityTypes.value=E.map(e=>e.type);exRelationTypes.value=R.map(e=>e.type);exConf.value=0.7;exEntities.value=[];exRelations.value=[];extractOpen.value=true}
function closeExtract(){extractOpen.value=false;exPreview.value=false;exFileResult.value=null;exInputTab.value='text'}

// 文件上传（PDF / Word / Markdown）
async function handleFileUpload(file) {
  const ext = file.name.split('.').pop().toLowerCase()
  if (!['pdf','docx','md','txt','markdown'].includes(ext)) {
    message.warn('不支持的文件格式，支持 PDF/Word/Markdown/TXT')
    return false
  }
  extracting.value = true
  try {
    const res = await extractKgFromFile(file)
    if (res.code === 200 && res.data) {
      exFileResult.value = res.data
      extractText.value = res.data.text || ''
      message.success('成功提取 ' + res.data.charCount + ' 字')
    } else { message.error(res.message || '提取失败') }
  } catch (e) { message.error('上传失败: ' + (e.message || '未知错误')) }
  finally { extracting.value = false }
  return false
}

async function doExtract(reExtract){
  if(!reExtract&&!extractText.value.trim()){message.warn('请输入文本或上传文件');return}
  extracting.value=true
  try{
    const body={text:extractText.value,topic:extractTopic.value,entityTypes:exEntityTypes.value,relationTypes:exRelationTypes.value,confidence:exConf.value}
    const r=await apiExtractKg(body)
    if(r.data){
      const d=JSON.parse(r.data)
      exEntities.value=(d.entities||[]).map(e=>({_id:e.id,_sel:true,...e,confidence:e.confidence||0.7}))
      exRelations.value=(d.relations||[]).map(r=>({_sel:true,...r,confidence:r.confidence||0.7}))
      exPreview.value=true
      message.success(`抽取 ${exEntities.value.length} 实体, ${exRelations.value.length} 关系`)
    }
  }catch(_){message.error('抽取失败')}
  extracting.value=false
}
async function doImport(){
  const es=exEntities.value.filter(e=>e._sel!==false)
  const rs=exRelations.value.filter(r=>r._sel!==false)
  if(es.length===0){message.warn('没有选中的实体');return}
  if(exImportMode.value==='new'){
    const ns=es.map((e,i)=>({id:`imp${Date.now()}-${i}`,type:e.type||'concept',data:{type:e.type||'concept',label:e.name,desc:e.desc||''},x:Math.random()*300,y:Math.random()*300}))
    const em={};es.forEach((e,i)=>{em[e._id]=ns[i].id})
    const es2=rs.map(r=>{const s=em[r.source],t=em[r.target];if(!s||!t)return null;return{id:`imp-r-${s}-${t}`,source:s,target:t,data:{label:r.type,relationType:r.type,desc:r.desc||''}}})
    const gd=JSON.stringify({nodes:ns,edges:es2.filter(Boolean)})
    try{const r2=await apiCreateKg({name:extractTopic.value||'知识图谱',graphData:gd});currentId.value=r2.data.id;await loadKg(r2.data.id);message.success('已创建新图谱')}catch(_){message.error('创建失败')}
  }else{
    const em={};es.forEach((e,i)=>{const id=`imp${Date.now()}-${i}`;em[e._id]=id;nodes.value.push({id,type:e.type||'concept',data:{type:e.type||'concept',label:e.name,desc:e.desc||''},x:Math.random()*300,y:Math.random()*300})})
    rs.forEach(r=>{const s=em[r.source],t=em[r.target];if(s&&t)edges.value.push({id:`imp-r-${s}-${t}`,source:s,target:t,data:{label:r.type,relationType:r.type,desc:r.desc||''}})})
    pushHistory();initD3();message.success(`已导入 ${es.length} 实体, ${rs.filter(r=>em[r.source]&&em[r.target]).length} 关系`)
  }
  extractOpen.value=false;exPreview.value=false
}

// ==================== D3 力导向（精简可靠版） ====================
const svgRef=ref(null),mainRef=ref(null)
let sim=null,zoom=null,svg=null,g=null

function initD3(){nextTick(()=>initD3Now())}
function initD3Now(){
  if(!svgRef.value||!mainRef.value)return
  const W=mainRef.value.clientWidth,H=mainRef.value.clientHeight
  if(sim)sim.stop()

  const ns=nodes.value.filter(n=>ef.value.includes(n.type||'concept'))
  const nids=new Set(ns.map(n=>n.id))
  const es=edges.value.filter(e=>{const s=typeof e.source==='object'?e.source.id:e.source;const t=typeof e.target==='object'?e.target.id:e.target;return rf.value.includes(e.data?.relationType||'related_to')&&nids.has(s)&&nids.has(t)})

  const simNodes=ns.map(n=>({id:n.id,type:n.type||'concept',data:{...n.data},x:n.x||Math.random()*W,y:n.y||Math.random()*H}))
  const nm=new Map(simNodes.map(n=>[n.id,n]))
  const simLinks=es.map(e=>({id:e.id,source:nm.get(typeof e.source==='object'?e.source.id:e.source),target:nm.get(typeof e.target==='object'?e.target.id:e.target),data:{...e.data}})).filter(l=>l.source&&l.target)

  svg=d3.select(svgRef.value);svg.selectAll('*').remove()
  svg.attr('viewBox',`0 0 ${W} ${H}`).style('width',W+'px').style('height',H+'px')
  g=svg.append('g')
  zoom=d3.zoom().scaleExtent([0.15,4]).on('zoom',ev=>g.attr('transform',ev.transform))
  svg.call(zoom)
  svg.on('dblclick',ev=>{if(ev.target===svg.node()||ev.target.tagName==='svg')newEntity()})
  svg.on('contextmenu',ev=>{ev.preventDefault();svgCtx(ev,null,null)})

  // defs
  const defs=svg.append('defs')
  R.forEach(r=>{defs.append('marker').attr('id','ar-'+r.type).attr('viewBox','0 0 10 10').attr('refX',8).attr('refY',5).attr('markerWidth',5).attr('markerHeight',5).attr('orient','auto-start-reverse').append('path').attr('d','M 0 0 L 10 5 L 0 10 z').attr('fill',r.color)})
  defs.append('filter').attr('id','glow').attr('x','-60%').attr('y','-60%').attr('width','220%').attr('height','220%').html('<feGaussianBlur stdDeviation="4" result="b"/><feMerge><feMergeNode in="b"/><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge>')

  // 边
  const linkG=g.append('g')
  const lEnter=linkG.selectAll('line').data(simLinks).join('line')
  lEnter.attr('x1',d=>d.source.x).attr('y1',d=>d.source.y).attr('x2',d=>d.target.x).attr('y2',d=>d.target.y)
    .attr('stroke',d=>RM[d.data?.relationType]?.color||'#6b7280').attr('stroke-width',d=>{const w={proposes:3,cites:2.5,extends:2,uses:2,contradicts:2,part_of:1.5,related_to:1,evaluates:2};return w[d.data?.relationType]||1.5}).attr('opacity',0.7)
    .attr('stroke-dasharray',d=>d.data?.relationType==='contradicts'?'6 3':null)
    .attr('marker-end',d=>'url(#ar-'+d.data?.relationType+')')
    .on('click',(ev,d)=>{ev.stopPropagation();selE(d.id)})
    .on('contextmenu',(ev,d)=>{ev.preventDefault();svgCtx(ev,null,d.id)})
  // 隐形宽点击区
  const hEnter=linkG.selectAll('line.hit').data(simLinks).join('line').attr('class','hit')
  hEnter.attr('x1',d=>d.source.x).attr('y1',d=>d.source.y).attr('x2',d=>d.target.x).attr('y2',d=>d.target.y)
    .attr('stroke','transparent').attr('stroke-width',14)
    .on('mouseenter',function(){d3.select(this.previousSibling).attr('stroke-width',3).attr('opacity',1)})
    .on('mouseleave',function(){d3.select(this.previousSibling).attr('stroke-width',1.5).attr('opacity',0.7)})
    .on('click',(ev,d)=>{ev.stopPropagation();selE(d.id)})
  linkG.selectAll('text').data(simLinks).join('text')
    .text(d=>RM[d.data?.relationType]?.name||'')
    .attr('x',d=>(d.source.x+d.target.x)/2).attr('y',d=>(d.source.y+d.target.y)/2)
    .attr('text-anchor','middle').attr('font-size',9).attr('dy',-6)
    .attr('fill',d=>RM[d.data?.relationType]?.color||'#6b7280')
    .on('click',(ev,d)=>{ev.stopPropagation();selE(d.id)})

  // 节点
  const nodeG=g.append('g')
  const nEnter=nodeG.selectAll('g.n').data(simNodes).join('g').attr('class','n')
  nEnter.attr('transform',d=>`translate(${d.x},${d.y})`)
    .on('click',(ev,d)=>{ev.stopPropagation()
      if(linkMode.value&&selNode.value&&selNode.value.id!==d.id){edges.value.push({id:`e${Date.now()}`,source:selNode.value.id,target:d.id,data:{label:'related_to',relationType:'related_to',desc:''}});linkMode.value=false;initD3();message.success('关系已创建');return}
      selN(d.id);applyStyles()
    })
    .on('contextmenu',(ev,d)=>{ev.preventDefault();svgCtx(ev,d.id,null)})
    .call(d3.drag().on('start',function(ev,d){sim.alphaTarget(0.3).restart();d.fx=d.x;d.fy=d.y}).on('drag',function(ev,d){d.fx=ev.x;d.fy=ev.y}).on('end',function(ev,d){sim.alpha(0.06).alphaTarget(0);d.fx=null;d.fy=null}))

  nEnter.each(function(d){const g2=d3.select(this);const e=EM[d.type]||EM.concept;const deg=relCnt(d.id);const sz=Math.min(36,Math.max(20,22+deg*2))
    g2.append('circle').attr('class','ring').attr('r',sz+5).attr('fill','none').attr('stroke',e.color).attr('stroke-width',0).attr('opacity',0)
    g2.append('circle').attr('class','body').attr('r',sz).attr('fill','#1A1D27').attr('stroke',e.color).attr('stroke-width',2)
    g2.append('text').attr('class','icon').text(e.name.charAt(0)).attr('text-anchor','middle').attr('dy','0.1em').attr('fill',e.color).attr('font-size',14).attr('font-weight','bold').attr('pointer-events','none')
    if(deg>1){g2.append('circle').attr('class','badge').attr('r',9).attr('cx',sz*0.7).attr('cy',-sz*0.7).attr('fill',e.color);g2.append('text').attr('class','btxt').text(deg).attr('x',sz*0.7).attr('y',-sz*0.7).attr('dy','0.35em').attr('text-anchor','middle').attr('fill','#fff').attr('font-size',8).attr('font-weight','bold').attr('pointer-events','none')}
    g2.append('text').attr('class','lbl').text(d.data?.label||'').attr('text-anchor','middle').attr('dy',sz+16).attr('fill','#E5E7EB').attr('font-size',10).attr('pointer-events','none')
  })

  // 仿真
  sim=d3.forceSimulation(simNodes)
    .force('link',d3.forceLink(simLinks).id(d=>d.id).distance(150).strength(0.2))
    .force('charge',d3.forceManyBody().strength(-300))
    .force('center',d3.forceCenter(W/2,H/2).strength(0.08))
    .force('collide',d3.forceCollide(40))
    .alphaDecay(0.008)
    .on('tick',()=>{
      linkG.selectAll('line').attr('x1',d=>d.source.x).attr('y1',d=>d.source.y).attr('x2',d=>d.target.x).attr('y2',d=>d.target.y)
      linkG.selectAll('text').attr('x',d=>(d.source.x+d.target.x)/2).attr('y',d=>(d.source.y+d.target.y)/2)
      nEnter.attr('transform',d=>`translate(${d.x},${d.y})`)
      simNodes.forEach(sn=>{const n=nodes.value.find(x=>x.id===sn.id);if(n){n.x=sn.x;n.y=sn.y}})
    })

  applyStyles()
}

function applyStyles(){
  if(!g)return
  const sid=selNode.value?.id
  g.selectAll('g.n circle.ring').attr('stroke-width',d=>sid===d.id?8:0).attr('opacity',d=>sid===d.id?1:0).attr('filter',d=>sid===d.id?'url(#glow)':null)
  g.selectAll('g.n circle.body').attr('stroke-width',d=>sid===d.id?3:2)
  g.selectAll('g.n text.lbl').attr('fill',d=>sid===d.id?(EM[d.type]?.color||'#fff'):'#E5E7EB')
  // 连线：选中节点的关联边高亮，其他变淡
  g.selectAll('line:not(.hit)').attr('opacity',d=>{if(!sid)return 0.7;const s=d.source.id||d.source;const t=d.target.id||d.target;return s===sid||t===sid?1:0.1})
  g.selectAll('text').filter((d,i,els)=>els[i].parentNode.tagName==='g'&&els[i].parentNode.classList.contains('kg-links'))  // edge labels only
    .attr('opacity',d=>{if(!sid)return 1;const s=d.source.id||d.source;const t=d.target.id||d.target;return s===sid||t===sid?1:0})
}

function fitView(){if(svg&&zoom)svg.transition().duration(500).call(zoom.transform,d3.zoomIdentity)}
function focusSel(){if(!selNode.value||!svg||!zoom)return;const n=selNode.value;const W=mainRef.value.clientWidth,H=mainRef.value.clientHeight;svg.transition().duration(500).call(zoom.transform,d3.zoomIdentity.translate(W/2-n.x,H/2-n.y).scale(1.5))}
function toggleFS(){const el=mainRef.value;if(!el)return;document.fullscreenElement?document.exitFullscreen():el.requestFullscreen()}
function exportPNG(){const svgEl=svgRef.value;const s=new XMLSerializer().serializeToString(svgEl);const img=new Image();img.onload=()=>{const c=document.createElement('canvas');c.width=svgEl.clientWidth*2;c.height=svgEl.clientHeight*2;c.getContext('2d').drawImage(img,0,0,c.width,c.height);const a=document.createElement('a');a.href=c.toDataURL('image/png');a.download='knowledge-graph.png';a.click()};img.src='data:image/svg+xml;base64,'+btoa(unescape(encodeURIComponent(s)))}
function searchNode(text){if(!text){fitView();closePanel();return};const n=nodes.value.find(x=>x.data?.label?.includes(text));if(n){const W=mainRef.value.clientWidth,H=mainRef.value.clientHeight;svg.transition().duration(500).call(zoom.transform,d3.zoomIdentity.translate(W/2-n.x,H/2-n.y).scale(2));selN(n.id);applyStyles()}}
// 导出
const fileInput=ref(null)
function exportJSON(){const gd=JSON.stringify({nodes:nodes.value.map(n=>({id:n.id,type:n.type,data:n.data})),edges:edges.value.map(e=>({id:e.id,source:typeof e.source==='object'?e.source.id:e.source,target:typeof e.target==='object'?e.target.id:e.target,data:e.data}))},null,2);downloadFile(gd,'knowledge-graph.json','application/json')}
function exportCSV(){let csv='id,name,type,desc\n';nodes.value.forEach(n=>csv+=`${n.id},"${n.data?.label||''}",${n.type},"${n.data?.desc||''}"\n`);csv+='\nsource,target,type,desc\n';edges.value.forEach(e=>csv+=`${typeof e.source==='object'?e.source.id:e.source},${typeof e.target==='object'?e.target.id:e.target},${e.data?.relationType||''},"${e.data?.desc||''}"\n`);downloadFile(csv,'knowledge-graph.csv','text/csv')}
function downloadFile(content,filename,type){const b=new Blob(['﻿'+content],{type});const a=document.createElement('a');a.href=URL.createObjectURL(b);a.download=filename;a.click();URL.revokeObjectURL(a.href)}
function openImport(){fileInput.value?.click()}
async function handleImportFile(ev){const f=ev.target.files?.[0];if(!f)return;try{const text=await f.text();const d=JSON.parse(text);if(d.nodes&&d.edges){nodes.value=d.nodes;edges.value=d.edges;pushHistory();initD3();message.success(`已导入 ${d.nodes.length} 实体, ${d.edges.length} 关系`)}else{message.error('JSON 格式不正确，需要 {nodes:[],edges:[]}')}}catch(_){message.error('文件解析失败')}finally{ev.target.value=''}}

onMounted(async()=>{await fetchList();if(kgList.value.length>0){currentId.value=kgList.value[0].id;await loadKg(kgList.value[0].id)}else initD3();window.addEventListener('resize',()=>{if(svg&&mainRef.value){const W=mainRef.value.clientWidth,H=mainRef.value.clientHeight;svg.attr('viewBox',`0 0 ${W} ${H}`).style('width',W+'px').style('height',H+'px');sim.force('center',d3.forceCenter(W/2,H/2).strength(0.08));sim.alpha(0.1).restart()}});document.addEventListener('keydown',onKey)})
onUnmounted(()=>{if(sim)sim.stop()})
function onKey(ev){if(ev.ctrlKey&&ev.key==='z'){ev.preventDefault();undo()};if(ev.ctrlKey&&ev.key==='y'){ev.preventDefault();redo()};if(ev.key==='f'||ev.key==='F'){ev.preventDefault();focusSel()};if((ev.key==='Delete'||ev.key==='Backspace')&&selNode.value){if(ev.target.tagName==='INPUT'||ev.target.tagName==='TEXTAREA')return;removeNode()}}
</script>

<style scoped>
.kg-app{--bg:#12141A;--panel:#1A1D27;--accent:#7C3AED;--text:#E5E7EB;--muted:#9CA3AF;--border:rgba(255,255,255,0.06);display:flex;flex-direction:column;height:calc(100vh - 90px);background:var(--bg);border-radius:10px;overflow:hidden;border:1px solid var(--border);color:var(--text);font-size:13px}
.kg-top{display:flex;align-items:center;gap:10px;padding:8px 14px;background:var(--panel);border-bottom:1px solid var(--border);flex-shrink:0;z-index:3;flex-wrap:wrap;font-size:12px}
.kg-logo{font-size:15px;font-weight:700;background:linear-gradient(135deg,#7C3AED,#60a5fa);-webkit-background-clip:text;-webkit-text-fill-color:transparent;white-space:nowrap}
.kg-stat{color:var(--muted);font-size:11px;cursor:default;white-space:nowrap}
.kg-body{flex:1;display:flex;overflow:hidden}
.kg-left{width:160px;min-width:160px;background:var(--panel);border-right:1px solid var(--border);padding:10px;overflow-y:auto;flex-shrink:0}
.left-hd{display:flex;justify-content:space-between;align-items:center;font-size:11px;color:var(--muted);text-transform:uppercase;letter-spacing:1px;margin-bottom:8px}
.left-sec{margin-bottom:10px;padding:6px 8px;border-radius:6px;background:rgba(124,58,237,.06)}
.left-label{font-size:10px;color:rgba(255,255,255,.2);text-transform:uppercase;margin-bottom:4px;display:flex;align-items:center;gap:6px}
.left-badge{font-size:9px;color:var(--accent)}
.left-row{display:flex;align-items:center;gap:6px;padding:3px 6px;border-radius:4px;cursor:pointer;font-size:11px;color:var(--muted);transition:all .15s}.left-row:hover{background:rgba(255,255,255,.04);color:var(--text)}.left-row.on{background:rgba(124,58,237,.12);color:var(--text)}
.left-dot{width:6px;height:6px;border-radius:50%;flex-shrink:0}
.kg-main{flex:1;position:relative;overflow:hidden;background:radial-gradient(ellipse at center,#111827 0%,#0a0e17 80%)}
.kg-svg{width:100%;height:100%;display:block}
.kg-ctx{position:fixed;z-index:100;background:var(--panel);border:1px solid var(--border);border-radius:8px;padding:4px;min-width:140px;box-shadow:0 8px 24px rgba(0,0,0,.6)}
.ctx-item{padding:6px 10px;border-radius:5px;cursor:pointer;font-size:12px;color:var(--text)}.ctx-item:hover{background:rgba(124,58,237,.12)}.ctx-item.danger{color:#ef4444}.ctx-item.danger:hover{background:rgba(239,68,68,.12)}
.kg-empty{position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center}
.empty-icon{font-size:56px;margin-bottom:12px}.empty-title{font-size:16px;font-weight:600;color:var(--text);margin-bottom:4px}.empty-desc{font-size:13px;color:var(--muted)}
.kg-right{width:220px;min-width:220px;background:var(--panel);border-left:1px solid var(--border);padding:14px;overflow-y:auto;flex-shrink:0}
.right-hd{display:flex;justify-content:space-between;align-items:center;margin-bottom:14px;font-size:13px;font-weight:600;color:var(--text)}
.right-close{cursor:pointer;font-size:14px;color:var(--muted)}.right-close:hover{color:var(--text)}
.right-field{margin-bottom:12px}.right-k{display:block;font-size:10px;color:var(--muted);margin-bottom:4px;text-transform:uppercase;letter-spacing:.5px}.right-val{font-size:12px;color:var(--text)}
.kg-foot{display:flex;align-items:center;gap:16px;padding:4px 14px;background:var(--panel);border-top:1px solid var(--border);flex-shrink:0;font-size:11px;color:var(--muted);flex-wrap:wrap}.fstat{white-space:nowrap}
.legend{display:flex;flex-direction:column;gap:3px}.legend-r{display:flex;align-items:center;gap:6px;font-size:11px;color:var(--text)}.legend-d{width:8px;height:8px;border-radius:50%;flex-shrink:0}
:deep(.ant-form-item-label>label){color:var(--muted)!important;font-size:11px!important}
:deep(.ant-input),:deep(.ant-select-selector),:deep(.ant-input-affix-wrapper){background:rgba(255,255,255,.03)!important;border-color:rgba(255,255,255,.06)!important;color:var(--text)!important;font-size:12px!important}
:deep(.ant-select-arrow){color:rgba(255,255,255,.15)!important}
:deep(.ant-btn){font-size:12px!important}
.ex-label{font-size:11px;color:var(--muted);margin-bottom:6px;text-transform:uppercase;letter-spacing:0.5px}
.ex-list{max-height:300px;overflow-y:auto;border:1px solid var(--border);border-radius:6px;padding:4px;background:rgba(0,0,0,0.15)}
.ex-item{display:flex;align-items:center;padding:3px 6px;border-radius:4px;cursor:pointer;font-size:12px;gap:4px;transition:background 0.1s}
.ex-item:hover{background:rgba(255,255,255,0.03)}
.ex-item.sel{background:rgba(124,58,237,0.08)}
.ex-conf{font-size:10px;margin-left:auto;white-space:nowrap}
</style>
<style>
.kg-select-popup .ant-select-item-option{font-size:12px!important;padding:4px 10px!important;min-height:unset!important;line-height:22px!important}
.kg-select-popup .ant-select-item-option-selected{background:rgba(124,58,237,.15)!important;color:#d4c5ff!important}
.kg-select-popup .ant-select-item-option:hover{background:rgba(255,255,255,.05)!important}
.kg-select-popup{box-shadow:0 8px 32px rgba(0,0,0,.6),0 0 0 1px rgba(255,255,255,.06)!important;background:#1A1D27!important;border-radius:8px!important;padding:4px!important}
</style>
