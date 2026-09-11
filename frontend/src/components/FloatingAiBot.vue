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
import { agentChat } from '@/api/agent'

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
  }
  return map[name] || name
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

.loading-text { color: #999; }

.chat-input { display: flex; gap: 8px; padding: 10px; border-top: 1px solid #f0f0f0; background: #fff; }
.chat-input input { flex: 1; padding: 8px 12px; border: 1px solid #ddd; border-radius: 8px; font-size: 14px; font-family: inherit; outline: none; }
.chat-input input:focus { border-color: #000; }
.chat-input button { padding: 8px 16px; background: #000; color: #fff; border: none; border-radius: 8px; cursor: pointer; font-size: 14px; font-family: inherit; }
.chat-input button:disabled { background: #ccc; cursor: not-allowed; }
@media (max-width: 480px) { .floating-ai-bot { bottom: 20px; right: 20px; } .chat-window { width: 320px; height: 440px; } }
</style>
