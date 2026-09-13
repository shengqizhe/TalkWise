<template>
  <div class="floating-ai-bot">
    <!-- 机器人按钮：未打开或最小化时显示 -->
    <div class="bot-trigger" v-if="!isVisible || isMinimized" @click="restoreBot">
      <div class="bot-icon">🤖</div>
    </div>
    <!-- 聊天窗口 -->
    <div class="chat-window" v-if="isVisible && !isMinimized">
      <div class="chat-header">
        <span>知讲 AI 助手</span>
        <div>
          <button class="min-btn" @click="minimizeBot">最小化</button>
          <button class="close-btn" @click="closeBot">×</button>
        </div>
      </div>
      <div class="chat-content">
        <div class="messages-container" ref="messagesContainer" @scroll="handleScroll">
          <div v-for="(msg, idx) in chatHistory" :key="idx" :class="['msg', msg.role]">
            <div class="msg-content">{{ msg.content }}</div>
            <!-- 工具调用轨迹：让用户看到 AI 做了什么 -->
            <div v-if="msg.tools && msg.tools.length" class="tool-trail">
              <span v-for="(t, i) in msg.tools" :key="i" class="tool-chip">
                🔧 {{ toolLabel(t.name) }}
              </span>
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
                <button class="confirm-btn" @click="confirmAction(msg.action)">确认执行</button>
                <button class="cancel-btn" @click="rejectAction(msg.action)">放弃</button>
              </div>
            </div>
          </div>
          <div v-if="loading" class="msg ai">
            <div class="msg-content loading-text">思考中…</div>
          </div>
        </div>
        <div class="chat-input">
          <input
            v-model="inputMessage"
            @keyup.enter="sendMessage"
            :disabled="loading"
            placeholder="试试：推荐一场讲座 / 帮我报名《…》"
          />
          <button @click="sendMessage" :disabled="!inputMessage.trim() || loading">发送</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { agentChat, confirmAgentAction, rejectAgentAction } from '@/api/agent'

const isVisible = ref(false)
const isMinimized = ref(false)
const inputMessage = ref('')
const loading = ref(false)
const messagesContainer = ref(null)
const isUserAtBottom = ref(true)
const chatHistory = ref([
  {
    role: 'ai',
    content: '你好！我是知讲 AI 助手，可以帮你查讲座、看报名记录、直接报名或取消报名。试试对我说：推荐一场 AI 相关的讲座。',
  },
])

// 工具名 → 中文展示
function toolLabel(name) {
  const map = {
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
  return map[name] || name
}

// 确认卡标题与回执文案按动作类型区分
const actionTitles = {
  CREATE_LECTURE: '新建讲座待确认',
  UPDATE_LECTURE: '修改讲座待确认',
  CANCEL_LECTURE: '取消讲座待确认',
  PUBLISH_LECTURE: '发布状态变更待确认',
}

function actionTitle(type) {
  return actionTitles[type] || '待确认操作'
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

async function confirmAction(action) {
  try {
    const res = await confirmAgentAction(action.actionId)
    chatHistory.value.push({ role: 'ai', content: confirmedMessage(res.data) })
    action.status = res.data?.status || 'CONFIRMED'
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '确认操作失败')
  }
}

async function rejectAction(action) {
  try {
    await rejectAgentAction(action.actionId)
    chatHistory.value.push({ role: 'ai', content: '已放弃本次操作。' })
    action.status = 'REJECTED'
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '放弃操作失败')
  }
}

const openBot = () => {
  isVisible.value = true
  isMinimized.value = false
  nextTick(() => scrollToBottom(true))
}
const closeBot = () => {
  isVisible.value = false
  isMinimized.value = false
}
const minimizeBot = () => {
  isMinimized.value = true
}
const restoreBot = () => {
  openBot()
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

const sendMessage = async () => {
  if (!inputMessage.value.trim() || loading.value) return
  const userMessage = inputMessage.value.trim()
  inputMessage.value = ''
  chatHistory.value.push({ role: 'user', content: userMessage })
  loading.value = true
  await nextTick()
  scrollToBottom(true)
  try {
    const res = await agentChat(userMessage)
    chatHistory.value.push({
      role: 'ai',
      content: res.data?.reply || '（未返回内容）',
      tools: res.data?.tools || [],
      action: res.data?.action || null,
    })
  } catch (e) {
    console.error('Agent 请求失败:', e)
    chatHistory.value.push({ role: 'ai', content: '抱歉，AI 服务暂时不可用，请稍后再试。' })
  } finally {
    loading.value = false
    await nextTick()
    scrollToBottom()
  }
}

onMounted(() => {
  if (messagesContainer.value) {
    messagesContainer.value.addEventListener('scroll', handleScroll)
  }
})
onUnmounted(() => {
  if (messagesContainer.value) {
    messagesContainer.value.removeEventListener('scroll', handleScroll)
  }
})

// 暴露给外部（如未来首页按钮可主动打开）
defineExpose({ openBot })
</script>

<style scoped>
.floating-ai-bot { position: fixed; bottom: 30px; right: 30px; z-index: 1000; }
.bot-trigger { width: 60px; height: 60px; border-radius: 50%; background: #000; color: #fff; display: flex; align-items: center; justify-content: center; cursor: pointer; box-shadow: 0 8px 32px rgba(0,0,0,0.25); font-size: 28px; }
.chat-window { position: absolute; bottom: 80px; right: 0; width: 380px; height: 520px; background: #fff; border-radius: 12px; border: 1px solid #f0f0f0; box-shadow: 0 20px 40px rgba(0,0,0,0.12); display: flex; flex-direction: column; overflow: hidden; }
.chat-header { background: linear-gradient(135deg,#000 0%,#333 100%); color: #fff; padding: 15px 20px; display: flex; justify-content: space-between; align-items: center; font-size: 15px; }
.min-btn, .close-btn { background: none; border: none; color: #fff; font-size: 14px; cursor: pointer; margin-left: 8px; font-family: inherit; }

.chat-content {
  flex: 1;
  display: flex;
  flex-direction: column;
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
.msg-content { background: #fff; padding: 10px 14px; border-radius: 10px; border: 1px solid #f0f0f0; box-shadow: 0 2px 8px rgba(0,0,0,0.04); margin-bottom: 2px; font-size: 14px; line-height: 1.6; white-space: pre-wrap; word-break: break-word; text-align: left; }
.msg.user .msg-content { background: #000; color: #fff; border-color: #000; }

/* 工具调用轨迹 */
.tool-trail { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 6px; }
.tool-chip { font-size: 12px; color: #666; background: #f2f2f2; border-radius: 20px; padding: 3px 10px; }

/* 写操作确认卡 */
.action-card { margin-top: 8px; padding: 10px 12px; background: #fff7e6; border: 1px solid #ffd591; border-radius: 8px; text-align: left; }
.action-card-title { font-weight: 600; font-size: 13px; margin-bottom: 4px; }
.action-card-summary { font-size: 12px; color: #666; margin-bottom: 6px; }
.action-card-fields { display: flex; flex-direction: column; gap: 2px; font-size: 12px; color: #333; margin-bottom: 8px; }
.action-card-actions { display: flex; gap: 8px; }
.confirm-btn { background: #000; color: #fff; border: none; border-radius: 6px; padding: 5px 12px; font-size: 12px; cursor: pointer; font-family: inherit; }
.cancel-btn { background: #fff; color: #666; border: 1px solid #ddd; border-radius: 6px; padding: 5px 12px; font-size: 12px; cursor: pointer; font-family: inherit; }

.loading-text { color: #999; }

.chat-input { display: flex; gap: 8px; padding: 10px; border-top: 1px solid #f0f0f0; background: #fff; }
.chat-input input { flex: 1; padding: 8px 12px; border: 1px solid #ddd; border-radius: 8px; font-size: 14px; font-family: inherit; outline: none; }
.chat-input input:focus { border-color: #000; }
.chat-input button { padding: 8px 16px; background: #000; color: #fff; border: none; border-radius: 8px; cursor: pointer; font-size: 14px; font-family: inherit; }
.chat-input button:disabled { background: #ccc; cursor: not-allowed; }
@media (max-width: 480px) { .floating-ai-bot { bottom: 20px; right: 20px; } .chat-window { width: 320px; height: 440px; } }
</style>
