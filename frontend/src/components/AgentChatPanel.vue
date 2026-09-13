<template>
  <div class="agent-chat-panel">
    <div class="messages-container" ref="messagesContainer" @scroll="handleScroll">
      <div v-for="(msg, idx) in chatHistory" :key="idx" :class="['msg', msg.role]">
        <div class="msg-content">{{ msg.content }}</div>

        <!-- 工具调用轨迹：让用户看到 AI 做了什么 -->
        <div v-if="msg.tools && msg.tools.length" class="tool-trail">
          <span v-for="(t, i) in msg.tools" :key="i" class="tool-chip">
            🔧 {{ toolLabel(t.name) }}
          </span>
        </div>

        <!-- 讲座卡片：由后端结构化数据渲染，可点击查看详情 -->
        <div v-if="msg.lectures && msg.lectures.length" class="lecture-list">
          <div
            v-for="lec in msg.lectures"
            :key="lec.id"
            class="lecture-card"
            @click="openLecture(lec)"
          >
            <div class="lecture-head">
              <span class="lecture-title">{{ lec.title }}</span>
              <span class="lecture-status" :class="statusClass(lec.statusText)">{{ lec.statusText }}</span>
            </div>
            <div class="lecture-meta">
              <span>主讲人：{{ lec.speaker }}</span>
              <span>时间：{{ lec.lectureTime }}</span>
            </div>
            <div class="lecture-meta">
              <span>地点：{{ lec.locationName }}</span>
              <span v-if="lec.categoryName">分类：{{ lec.categoryName }}</span>
            </div>
            <div v-if="lec.capacity" class="lecture-meta">
              <span>报名：{{ lec.registeredCount || 0 }}/{{ lec.capacity }}</span>
            </div>
          </div>
        </div>

        <!-- 写操作确认卡：创建/修改/取消/发布都需用户明确确认 -->
        <div v-if="msg.action && msg.action.status === 'PENDING'" class="action-card">
          <div class="action-card-title">{{ actionTitle(msg.action.type) }}</div>
          <div class="action-card-summary">{{ msg.action.summary }}</div>
          <div class="action-card-fields">
            <span v-if="msg.action.lecture?.title">标题：{{ msg.action.lecture.title }}</span>
            <span v-if="msg.action.lecture?.speaker">主讲人：{{ msg.action.lecture.speaker }}</span>
            <span v-if="msg.action.lecture?.lectureTime">时间：{{ msg.action.lecture.lectureTime }}</span>
            <span v-if="msg.action.lecture?.durationMinutes">时长：{{ msg.action.lecture.durationMinutes }} 分钟</span>
            <span v-if="msg.action.lecture?.capacity">容量：{{ msg.action.lecture.capacity }} 人</span>
            <span v-if="msg.action.reason">原因：{{ msg.action.reason }}</span>
          </div>
          <div class="action-card-actions">
            <el-button size="small" type="primary" @click="confirmAction(msg.action)">确认执行</el-button>
            <el-button size="small" @click="rejectAction(msg.action)">放弃</el-button>
          </div>
        </div>
      </div>
      <div v-if="loading" class="msg ai">
        <div class="msg-content loading-text">思考中…</div>
      </div>
    </div>

    <div class="panel-input">
      <el-input
        v-model="inputMessage"
        :disabled="loading"
        placeholder="试试：推荐这周末的讲座 / 帮我报名《…》"
        @keyup.enter="sendMessage"
      >
        <template #append>
          <el-button :disabled="!inputMessage.trim() || loading" :loading="loading" @click="sendMessage">
            发送
          </el-button>
        </template>
      </el-input>
    </div>
  </div>
</template>

<script setup>
import { nextTick, onMounted, onUnmounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { agentChat, confirmAgentAction, rejectAgentAction } from '@/api/agent'

const router = useRouter()
const userStore = useUserStore()

const inputMessage = ref('')
const loading = ref(false)
const messagesContainer = ref(null)
const isUserAtBottom = ref(true)
const chatHistory = ref([
  {
    role: 'ai',
    content: '你好！我是知讲 AI 助手，可以帮你查讲座、看报名记录、直接报名或取消报名，教师还可以让我估算容量、推荐教室、起草讲座。试试对我说：推荐这周末的讲座。',
  },
])

// 工具名 → 中文展示
const TOOL_LABELS = {
  searchLectures: '查询讲座',
  getMyRegistrations: '查询我的报名',
  registerLecture: '报名讲座',
  cancelRegistration: '取消报名',
  queryStatistics: '查询统计',
  generatePromotion: '生成宣传文案',
  analyzeEvaluations: '分析评价',
  getAnalysisTaskStatus: '查询分析进度',
  estimateCapacity: '估算建议容量',
  recommendRoom: '推荐教室',
  prepareLectureCreation: '创建讲座草稿',
  prepareLectureUpdate: '修改讲座草稿',
  prepareLectureCancel: '取消讲座草稿',
  prepareLecturePublish: '发布讲座草稿',
}

function toolLabel(name) {
  return TOOL_LABELS[name] || name
}

// 确认卡标题与回执文案按动作类型区分
const ACTION_TITLES = {
  CREATE_LECTURE: '新建讲座待确认',
  UPDATE_LECTURE: '修改讲座待确认',
  CANCEL_LECTURE: '取消讲座待确认',
  PUBLISH_LECTURE: '发布状态变更待确认',
}

function actionTitle(type) {
  return ACTION_TITLES[type] || '待确认操作'
}

// 状态标签配色
function statusClass(statusText) {
  switch (statusText) {
    case '即将开始': return 'status-soon'
    case '进行中': return 'status-ongoing'
    case '已结束': return 'status-ended'
    case '已取消': return 'status-cancelled'
    default: return ''
  }
}

// 点击讲座卡片跳转：学生进详情页，其他角色跳到各自讲座列表
function openLecture(lec) {
  if (!lec?.id) return
  const roles = userStore.userInfo?.roles || []
  if (roles.includes('student')) {
    router.push(`/student/lectures/${lec.id}`)
  } else if (roles.includes('teacher')) {
    router.push('/teacher/lectures')
  } else if (roles.includes('admin')) {
    router.push('/admin/publish')
  }
}

function confirmedMessage(data) {
  if (!data) return '操作已确认。'
  const id = data.resultId ? `，编号：${data.resultId}` : ''
  switch (data.type) {
    case 'CREATE_LECTURE':
      return `讲座已创建${id}。如需面向学生公开，请再让我发布该讲座。`
    case 'UPDATE_LECTURE':
      return `讲座修改已生效${id}。`
    case 'CANCEL_LECTURE':
      return `讲座已取消${id}。`
    case 'PUBLISH_LECTURE':
      return data.lecture?.publishStatus === 1 ? '讲座已发布。' : '讲座已下架。'
    default:
      return `操作已确认${id}。`
  }
}

const handleScroll = () => {
  if (!messagesContainer.value) return
  const el = messagesContainer.value
  isUserAtBottom.value = el.scrollTop + el.clientHeight >= el.scrollHeight - 2
}

const scrollToBottom = (force = false) => {
  if (!messagesContainer.value) return
  const el = messagesContainer.value
  if (force || isUserAtBottom.value) {
    el.scrollTop = el.scrollHeight
  }
}

async function sendMessage() {
  const message = inputMessage.value.trim()
  if (!message || loading.value) return

  chatHistory.value.push({ role: 'user', content: message })
  inputMessage.value = ''
  loading.value = true
  await nextTick()
  scrollToBottom(true)

  try {
    const res = await agentChat(message)
    chatHistory.value.push({
      role: 'ai',
      content: res.data?.reply || '（未返回内容）',
      tools: res.data?.tools || [],
      lectures: res.data?.lectures || [],
      action: res.data?.action || null,
    })
  } catch (error) {
    console.error('Agent 请求失败:', error)
    // 后端已对模型故障统一返回固定文案；这里兜底网络/网关层失败
    chatHistory.value.push({ role: 'ai', content: '抱歉！功能失效，请联系管理员修复。' })
  } finally {
    loading.value = false
    await nextTick()
    scrollToBottom()
  }
}

async function confirmAction(action) {
  try {
    const res = await confirmAgentAction(action.actionId)
    chatHistory.value.push({ role: 'ai', content: confirmedMessage(res.data) })
    action.status = res.data?.status || 'CONFIRMED'
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '确认操作失败')
  } finally {
    await nextTick()
    scrollToBottom()
  }
}

async function rejectAction(action) {
  try {
    await rejectAgentAction(action.actionId)
    chatHistory.value.push({ role: 'ai', content: '已放弃本次操作。' })
    action.status = 'REJECTED'
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '放弃操作失败')
  } finally {
    await nextTick()
    scrollToBottom()
  }
}

onMounted(() => {
  if (messagesContainer.value) {
    messagesContainer.value.addEventListener('scroll', handleScroll)
  }
  nextTick(() => scrollToBottom(true))
})

onUnmounted(() => {
  if (messagesContainer.value) {
    messagesContainer.value.removeEventListener('scroll', handleScroll)
  }
})
</script>

<style scoped>
.agent-chat-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.messages-container {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 18px;
  background: #fafafa;
}

.msg { margin-bottom: 14px; max-width: 88%; }
.msg.user { margin-left: auto; text-align: right; }
.msg.ai { margin-right: auto; text-align: left; }

.msg-content {
  background: #fff;
  padding: 10px 14px;
  border-radius: 10px;
  border: 1px solid #f0f0f0;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  margin-bottom: 2px;
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  text-align: left;
}

.msg.user .msg-content { background: #000; color: #fff; border-color: #000; }

/* 工具调用轨迹 */
.tool-trail { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 6px; }
.tool-chip { font-size: 12px; color: #666; background: #f2f2f2; border-radius: 20px; padding: 3px 10px; }

/* 讲座卡片：结构化数据渲染，点击查看详情 */
.lecture-list { display: flex; flex-direction: column; gap: 8px; margin-top: 8px; }
.lecture-card {
  background: #fff;
  border: 1px solid #e8e8e8;
  border-radius: 10px;
  padding: 10px 12px;
  cursor: pointer;
  transition: border-color 0.2s, box-shadow 0.2s, transform 0.2s;
}
.lecture-card:hover {
  border-color: #000;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  transform: translateY(-1px);
}
.lecture-head { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-bottom: 6px; }
.lecture-title { font-size: 14px; font-weight: 600; color: #222; line-height: 1.4; }
.lecture-status { font-size: 11px; padding: 2px 8px; border-radius: 10px; white-space: nowrap; background: #f2f2f2; color: #666; }
.status-soon { background: #e8f5e9; color: #2e7d32; }
.status-ongoing { background: #e3f2fd; color: #1565c0; }
.status-ended { background: #f5f5f5; color: #999; }
.status-cancelled { background: #fdecea; color: #c62828; }
.lecture-meta { display: flex; flex-wrap: wrap; gap: 12px; font-size: 12px; color: #777; line-height: 1.7; }

/* 写操作确认卡 */
.action-card {
  margin-top: 8px;
  padding: 10px 12px;
  background: #fff7e6;
  border: 1px solid #ffd591;
  border-radius: 8px;
  text-align: left;
}
.action-card-title { font-weight: 600; font-size: 13px; margin-bottom: 4px; }
.action-card-summary { font-size: 12px; color: #666; margin-bottom: 6px; }
.action-card-fields { display: flex; flex-direction: column; gap: 2px; font-size: 12px; color: #333; margin-bottom: 8px; }
.action-card-actions { display: flex; gap: 8px; }

.loading-text { color: #999; }

.panel-input {
  border-top: 1px solid #f0f0f0;
  background: #fff;
  padding: 12px 14px;
}
</style>
