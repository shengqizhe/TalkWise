<template>
  <div class="floating-ai-bot">
    <!-- 机器人按钮：未打开或最小化时显示 -->
    <div class="bot-trigger" v-if="!isVisible || isMinimized" @click="restoreBot">
      <div class="bot-icon">🤖</div>
    </div>
    <!-- 聊天窗口：仅在可见且未最小化时显示 -->
    <div class="chat-window" v-if="isVisible && !isMinimized">
      <div class="chat-header">
        <span>AI智能助手</span>
        <div>
          <button class="min-btn" @click="minimizeBot">最小化</button>
          <button class="close-btn" @click="closeBot">×</button>
        </div>
      </div>
      <div class="chat-content">
        <div class="messages-container" ref="messagesContainer">
          <div v-for="(msg, idx) in chatHistory" :key="idx" :class="['msg', msg.role]">
            <div class="msg-meta">
              <b>{{ msg.role === 'user' ? '我' : 'AI' }}</b>
              <span class="msg-time">{{ msg.timestamp ? msg.timestamp.toLocaleString() : '' }}</span>
            </div>
            <div class="msg-content">{{ msg.content }}</div>
            <div v-if="msg.lectures && msg.lectures.length" class="lecture-cards">
              <div v-for="lecture in msg.lectures" :key="lecture.id" class="lecture-card">
                <div><b>{{ lecture.title }}</b> - {{ lecture.speaker }}</div>
                <div>时间: {{ formatLectureTime(lecture.lectureTime) }}</div>
                <div v-if="lecture.locationName">地点: {{ lecture.locationName }}</div>
                <div v-if="lecture.summary">简介: {{ lecture.summary }}</div>
                <div class="lecture-actions">
                  <button v-if="!lecture.registered" @click="registerLecture(lecture)">立即报名</button>
                  <button v-else @click="cancelLectureRegistration(lecture)">取消报名</button>
                  <button @click="viewLectureDetail(lecture.id)">查看详情</button>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div class="chat-input">
          <input v-model="inputMessage" @keyup.enter="sendMessage" :disabled="loading" placeholder="输入内容..." />
          <button @click="sendMessage" :disabled="!inputMessage.trim() || loading">发送</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, watch, onMounted, onUnmounted } from 'vue'
import { askAiAssistant, getUserRegistrations, cancelUserRegistration } from '@/api/aiAssistant'
import { registerLecture as registerLectureApi, checkRegistration } from '@/api/registration'
import { useUserStore } from '@/stores/user'
import { useRouter } from 'vue-router'

const userStore = useUserStore()
const router = useRouter()
const isVisible = ref(false)
const isMinimized = ref(false)
const inputMessage = ref('')
const chatHistory = ref([
  { role: 'ai', content: '你好！我是AI智能助手，可以帮你查找讲座信息、推荐讲座、管理报名等。', timestamp: new Date() }
])
const loading = ref(false)
const messagesContainer = ref(null)
const isUserAtBottom = ref(true)

const isLoggedIn = () => userStore.token && userStore.userInfo

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
  isVisible.value = true
  isMinimized.value = false
  nextTick(() => scrollToBottom(true))
}

const handleScroll = () => {
  if (!messagesContainer.value) return
  const el = messagesContainer.value
  // 允许2px误差
  isUserAtBottom.value = el.scrollTop + el.clientHeight >= el.scrollHeight - 2
}

const scrollToBottom = (force = false) => {
  if (!messagesContainer.value) return
  const el = messagesContainer.value
  if (force || isUserAtBottom.value) {
    el.scrollTop = el.scrollHeight
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

watch(isMinimized, async (val) => {
  if (!val && isVisible.value) {
    await nextTick(); scrollToBottom(true)
  }
})
watch(isVisible, async (val) => {
  if (val && !isMinimized.value) {
    await nextTick(); scrollToBottom(true)
  }
})

const sendMessage = async () => {
  if (!inputMessage.value.trim()) return
  const userMessage = inputMessage.value
  inputMessage.value = ''
  chatHistory.value.push({ role: 'user', content: userMessage, timestamp: new Date() })
  await nextTick(); scrollToBottom(true)
  loading.value = true
  try {
    const context = chatHistory.value.map(h => (h.role === 'user' ? '用户：' : 'AI：') + h.content).join('\n')
    const res = await askAiAssistant({
      message: userMessage,
      userId: userStore.userInfo?.id,
      context
    })
    const aiMessage = { role: 'ai', content: res.data.reply, timestamp: new Date() }
    if (res.data.lectures && res.data.lectures.length > 0) {
      // 关键：如果是“我的报名”/“已报名”/“报名记录”关键词，全部设为已报名可取消
      if (/我的报名|已报名|报名记录/.test(userMessage)) {
        aiMessage.lectures = res.data.lectures.map(lecture => ({
          ...lecture,
          registering: false,
          registered: true,
          canCancel: true
        }))
      } else {
        aiMessage.lectures = res.data.lectures.map(lecture => ({
          ...lecture,
          registering: false,
          registered: false
        }))
        if (isLoggedIn()) await checkUserRegistrations(aiMessage.lectures)
      }
    }
    chatHistory.value.push(aiMessage)
    await nextTick(); scrollToBottom()
  } catch (e) {
    chatHistory.value.push({ role: 'ai', content: 'AI助手请求失败', timestamp: new Date() })
    await nextTick(); scrollToBottom()
  } finally {
    loading.value = false
  }
}

const checkUserRegistrations = async (lectures) => {
  const userId = userStore.userInfo?.id
  if (!userId) return
  for (const lecture of lectures) {
    try {
      const response = await checkRegistration(userId, lecture.id)
      lecture.registered = response.data.registered
    } catch {}
  }
}
const registerLecture = async (lecture) => {
  if (!isLoggedIn()) { router.push('/login'); return }
  const userId = userStore.userInfo?.id
  if (!userId) return
  lecture.registering = true
  try {
    await registerLectureApi(userId, lecture.id)
    lecture.registered = true
    chatHistory.value.forEach(msg => {
      if (msg.lectures) msg.lectures.forEach(l => { if (l.id === lecture.id) l.registered = true })
    })
  } finally { lecture.registering = false }
}
const cancelLectureRegistration = async (lecture) => {
  if (!isLoggedIn()) { router.push('/login'); return }
  const userId = userStore.userInfo?.id
  if (!userId) return
  try {
    await cancelUserRegistration(userId, lecture.id)
    lecture.registered = false
    lecture.canCancel = false
    chatHistory.value.forEach(msg => {
      if (msg.lectures) msg.lectures.forEach(l => { if (l.id === lecture.id) { l.registered = false; l.canCancel = false } })
    })
  } catch {}
}
const viewLectureDetail = (lectureId) => {
  router.push(`/student/lectures/${lectureId}`)
}
const formatLectureTime = (timeStr) => {
  if (!timeStr) return ''
  const date = new Date(timeStr)
  return date.toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}
</script>

<style scoped>
.floating-ai-bot { position: fixed; bottom: 30px; right: 30px; z-index: 1000; }
.bot-trigger { width: 60px; height: 60px; border-radius: 50%; background: #000; color: #fff; display: flex; align-items: center; justify-content: center; cursor: pointer; box-shadow: 0 8px 32px rgba(0,0,0,0.25); font-size: 28px; }
.chat-window { position: absolute; bottom: 80px; right: 0; width: 350px; height: 500px; background: #fff; border-radius: 12px; box-shadow: 0 20px 40px rgba(0,0,0,0.15); display: flex; flex-direction: column; overflow: hidden; }
.chat-header { background: linear-gradient(135deg,#000 0%,#333 100%); color: #fff; padding: 15px 20px; display: flex; justify-content: space-between; align-items: center; }
.min-btn, .close-btn { background: none; border: none; color: #fff; font-size: 16px; cursor: pointer; margin-left: 8px; }

.chat-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0; /* 关键，防止flex塌陷 */
}

.messages-container {
  flex: 1;
  min-height: 0; /* 关键，防止flex塌陷 */
  max-height: 350px;
  overflow-y: auto;
  padding: 20px;
  background: #f8f9fa;
}

.msg { margin-bottom: 15px; max-width: 85%; }
.msg.user { margin-left: auto; text-align: right; }
.msg.ai { margin-right: auto; text-align: left; }
.msg-meta { font-size: 13px; color: #888; margin-bottom: 2px; }
.msg-content { background: #fff; padding: 10px 14px; border-radius: 10px; box-shadow: 0 2px 8px rgba(0,0,0,0.08); margin-bottom: 2px; }
.msg.user .msg-content { background: #000; color: #fff; }
.lecture-cards { margin-top: 8px; display: flex; flex-direction: column; gap: 8px; }
.lecture-card { background: #fff; border: 1px solid #e0e0e0; border-radius: 8px; padding: 8px; box-shadow: 0 2px 6px rgba(0,0,0,0.08); }
.lecture-actions { margin-top: 6px; display: flex; gap: 6px; }
.lecture-actions button { font-size: 12px; padding: 2px 8px; border-radius: 4px; border: 1px solid #ccc; background: #f5f5f5; cursor: pointer; }
.lecture-actions button[disabled] { background: #eee; color: #aaa; cursor: not-allowed; }
.chat-input { display: flex; gap: 8px; padding: 10px; border-top: 1px solid #e0e0e0; background: #fff; }
.chat-input input { flex: 1; padding: 8px; border: 1px solid #ddd; border-radius: 6px; font-size: 14px; }
.chat-input button { padding: 8px 16px; background: #667eea; color: #fff; border: none; border-radius: 6px; cursor: pointer; font-size: 14px; }
.chat-input button:disabled { background: #ccc; cursor: not-allowed; }
@media (max-width: 480px) { .floating-ai-bot { bottom: 20px; right: 20px; } .chat-window { width: 300px; height: 400px; } }
</style> 