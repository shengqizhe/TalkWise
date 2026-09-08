<template>
  <div class="bell-notification">
    <!-- 小铃铛图标 -->
    <el-badge :value="unreadCount" :hidden="unreadCount === 0" class="notification-badge">
      <el-button 
        :icon="Bell" 
        circle 
        size="large"
        class="bell-button"
        @click="toggleDropdown"
        :class="{ 'has-unread': unreadCount > 0 }"
      />
    </el-badge>
    
    <!-- 消息下拉框 -->
    <el-dropdown 
      ref="dropdownRef"
      :visible="dropdownVisible"
      placement="bottom-end"
      trigger="manual"
      @visible-change="handleDropdownVisibleChange"
    >
      <span></span>
      <template #dropdown>
        <el-dropdown-menu class="notification-dropdown">
          <div class="dropdown-header">
            <span class="header-title">消息通知</span>
            <div class="header-actions">
              <el-button 
                :icon="Delete" 
                size="small" 
                text 
                @click="clearAllMessages" 
                v-if="messages.length > 0"
                title="清空所有消息"
              />
              <el-button 
                size="small" 
                text 
                @click="addTestMessage"
                title="添加测试消息"
              >
                测试
              </el-button>
            </div>
          </div>
          
          <el-divider style="margin: 8px 0;" />
          
          <div class="message-list" v-if="messages.length > 0">
            <div 
              v-for="message in displayMessages" 
              :key="message.id"
              class="message-item"
              :class="{ 'unread': !message.read }"
              @click="markAsRead(message)"
            >
              <div class="message-header">
                <div class="message-type-info">
                  <el-tag :type="getMessageTypeColor(message.type)" size="small">
                    {{ getMessageTypeText(message.type) }}
                  </el-tag>
                  <el-tag v-if="message.data?.source === 'email'" type="info" size="small" class="source-tag">
                    📧 邮件
                  </el-tag>
                  <el-tag v-else type="success" size="small" class="source-tag">
                    🔔 实时
                  </el-tag>
                </div>
                <span class="message-time">{{ formatTime(message.timestamp) }}</span>
              </div>
              <div class="message-content">{{ message.content }}</div>
              <el-button 
                :icon="Close" 
                size="small" 
                text
                class="delete-btn"
                @click.stop="removeMessage(message.id)"
              />
            </div>
          </div>
          
          <div class="empty-state" v-else>
            <el-empty description="暂无消息" :image-size="60" />
          </div>
          
          <div class="pagination" v-if="messages.length > pageSize">
            <el-pagination
              v-model:current-page="currentPage"
              :page-size="pageSize"
              :total="messages.length"
              layout="prev, pager, next"
              small
              @current-change="handlePageChange"
            />
          </div>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { Bell, Delete, Close } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

// 响应式数据
const messages = ref([])
const dropdownVisible = ref(false)
const dropdownRef = ref(null)
const currentPage = ref(1)
const pageSize = ref(5)
let messageIdCounter = 0

// 计算属性
const unreadCount = computed(() => {
  return messages.value.filter(msg => !msg.read).length
})

const displayMessages = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return messages.value.slice(start, end)
})

// 消息处理函数
function addMessage(type, content, data = {}) {
  const message = {
    id: ++messageIdCounter,
    type,
    content,
    data,
    timestamp: Date.now(),
    read: false
  }
  
  messages.value.unshift(message)
  
  // 限制消息数量，最多保留50条
  if (messages.value.length > 50) {
    messages.value = messages.value.slice(0, 50)
  }
}

function markAsRead(message) {
  message.read = true
}

function removeMessage(messageId) {
  const index = messages.value.findIndex(msg => msg.id === messageId)
  if (index > -1) {
    messages.value.splice(index, 1)
  }
}

function clearAllMessages() {
  messages.value = []
  currentPage.value = 1
  dropdownVisible.value = false
  ElMessage.success('已清空所有消息')
}

function toggleDropdown() {
  dropdownVisible.value = !dropdownVisible.value
}

function handleDropdownVisibleChange(visible) {
  dropdownVisible.value = visible
}

function handlePageChange(page) {
  currentPage.value = page
}

function addTestMessage() {
  const testMessages = [
    { type: 'REGISTRATION_SUCCESS', content: '测试报名成功通知 - 您已成功报名《人工智能基础》讲座' },
    { type: 'STUDENT_REGISTRATION', content: '测试学生报名通知 - 学生张三已报名您的讲座' },
    { type: 'LECTURE_NOTIFICATION', content: '测试讲座通知 - 《机器学习实践》讲座将于明天下午2点开始' },
    { type: 'BROADCAST', content: '测试系统通知 - 系统将于今晚10点进行维护' },
    { type: 'USER_MESSAGE', content: '测试用户消息 - 这是一条测试消息' }
  ]
  
  const randomMessage = testMessages[Math.floor(Math.random() * testMessages.length)]
  addMessage(randomMessage.type, randomMessage.content)
  
  ElMessage.success('已添加测试通知')
}

// 工具函数
function getMessageTypeText(type) {
  const textMap = {
    'REGISTRATION_SUCCESS': '报名成功',
    'STUDENT_REGISTRATION': '学生报名',
    'LECTURE_NOTIFICATION': '讲座通知',
    'BROADCAST': '系统通知',
    'USER_MESSAGE': '用户消息'
  }
  return textMap[type] || '未知消息'
}

function getMessageTypeColor(type) {
  const colorMap = {
    'REGISTRATION_SUCCESS': 'success',
    'STUDENT_REGISTRATION': 'info', 
    'LECTURE_NOTIFICATION': 'warning',
    'BROADCAST': 'info',
    'USER_MESSAGE': 'primary'
  }
  return colorMap[type] || 'info'
}

function formatTime(timestamp) {
  const date = new Date(timestamp)
  const now = new Date()
  const diff = now - date
  
  if (diff < 60000) { // 1分钟内
    return '刚刚'
  } else if (diff < 3600000) { // 1小时内
    return `${Math.floor(diff / 60000)}分钟前`
  } else if (diff < 86400000) { // 24小时内
    return `${Math.floor(diff / 3600000)}小时前`
  } else {
    return date.toLocaleDateString() + ' ' + date.toLocaleTimeString()
  }
}

// WebSocket事件监听
function handleRegistrationSuccess(event) {
  const message = event.detail
  addMessage('REGISTRATION_SUCCESS', `您已成功报名讲座：${message.content}`, message)
}

function handleStudentRegistration(event) {
  const message = event.detail
  addMessage('STUDENT_REGISTRATION', message.content, message)
}

function handleLectureNotification(event) {
  const message = event.detail
  addMessage('LECTURE_NOTIFICATION', message.content, message)
}

function handleBroadcastMessage(event) {
  const message = event.detail
  addMessage('BROADCAST', message.content, message)
}

function handleUserMessage(event) {
  const message = event.detail
  addMessage('USER_MESSAGE', message.content, message)
}

// 邮箱通知事件监听
function handleEmailRegistrationSuccess(event) {
  const message = event.detail
  addMessage('REGISTRATION_SUCCESS', `您已成功报名讲座：${message.content}`, { ...message, source: 'email' })
}

function handleEmailStudentRegistration(event) {
  const message = event.detail
  addMessage('STUDENT_REGISTRATION', message.content, { ...message, source: 'email' })
}

function handleEmailLectureNotification(event) {
  const message = event.detail
  addMessage('LECTURE_NOTIFICATION', message.content, { ...message, source: 'email' })
}

function handleEmailUpcomingLecture(event) {
  const message = event.detail
  addMessage('LECTURE_NOTIFICATION', message.content, { ...message, source: 'email' })
}

function handleEmailBroadcast(event) {
  const message = event.detail
  addMessage('BROADCAST', message.content, { ...message, source: 'email' })
}

function handleEmailGeneral(event) {
  const message = event.detail
  addMessage('USER_MESSAGE', message.content, { ...message, source: 'email' })
}

// 点击外部关闭下拉框
function handleClickOutside(event) {
  if (dropdownRef.value && !dropdownRef.value.$el.contains(event.target)) {
    dropdownVisible.value = false
  }
}

// 生命周期
onMounted(() => {
  // 监听WebSocket事件
  window.addEventListener('websocket-registration-success', handleRegistrationSuccess)
  window.addEventListener('websocket-student-registration', handleStudentRegistration)
  window.addEventListener('websocket-lecture-notification', handleLectureNotification)
  window.addEventListener('websocket-broadcast', handleBroadcastMessage)
  window.addEventListener('websocket-user-message', handleUserMessage)
  
  // 监听邮箱通知事件
  window.addEventListener('email-registration-success', handleEmailRegistrationSuccess)
  window.addEventListener('email-student-registration', handleEmailStudentRegistration)
  window.addEventListener('email-lecture-notification', handleEmailLectureNotification)
  window.addEventListener('email-upcoming-lecture', handleEmailUpcomingLecture)
  window.addEventListener('email-broadcast', handleEmailBroadcast)
  window.addEventListener('email-general', handleEmailGeneral)
  
  // 监听点击外部事件
  document.addEventListener('click', handleClickOutside)
})

onUnmounted(() => {
  // 清理WebSocket事件监听
  window.removeEventListener('websocket-registration-success', handleRegistrationSuccess)
  window.removeEventListener('websocket-student-registration', handleStudentRegistration)
  window.removeEventListener('websocket-lecture-notification', handleLectureNotification)
  window.removeEventListener('websocket-broadcast', handleBroadcastMessage)
  window.removeEventListener('websocket-user-message', handleUserMessage)
  
  // 清理邮箱通知事件监听
  window.removeEventListener('email-registration-success', handleEmailRegistrationSuccess)
  window.removeEventListener('email-student-registration', handleEmailStudentRegistration)
  window.removeEventListener('email-lecture-notification', handleEmailLectureNotification)
  window.removeEventListener('email-upcoming-lecture', handleEmailUpcomingLecture)
  window.removeEventListener('email-broadcast', handleEmailBroadcast)
  window.removeEventListener('email-general', handleEmailGeneral)
  
  document.removeEventListener('click', handleClickOutside)
})

// 暴露方法给父组件或外部调用
defineExpose({
  addMessage,
  markAsRead,
  removeMessage,
  clearAllMessages
})
</script>

<style scoped>
.bell-notification {
  position: relative;
  display: inline-block;
}

.notification-badge {
  position: relative;
}

.bell-button {
  background: linear-gradient(135deg, #000 0%, #333 100%);
  border: none;
  color: white;
  transition: all 0.3s ease;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}

.bell-button:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
}

.bell-button.has-unread {
  animation: bellShake 0.5s ease-in-out;
}

@keyframes bellShake {
  0%, 100% { transform: rotate(0deg); }
  25% { transform: rotate(-10deg); }
  75% { transform: rotate(10deg); }
}

.notification-dropdown {
  width: 350px;
  max-height: 500px;
  padding: 0;
  border-radius: 8px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
}

.dropdown-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%);
  border-radius: 8px 8px 0 0;
}

.header-title {
  font-weight: 600;
  color: #303133;
  font-size: 14px;
}

.header-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.message-list {
  max-height: 300px;
  overflow-y: auto;
  padding: 8px;
}

.message-item {
  padding: 12px;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  background-color: #fafafa;
  cursor: pointer;
  transition: all 0.3s;
  position: relative;
  margin-bottom: 8px;
}

.message-item:last-child {
  margin-bottom: 0;
}

.message-item:hover {
  background-color: #f0f9ff;
  border-color: #000;
}

.message-item.unread {
  background-color: #ecf5ff;
  border-color: #000;
  border-left: 4px solid #000;
}

.message-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.message-type-info {
  display: flex;
  gap: 6px;
  align-items: center;
}

.source-tag {
  font-size: 11px;
  padding: 2px 6px;
}

.message-time {
  font-size: 12px;
  color: #909399;
}

.message-content {
  font-size: 13px;
  color: #303133;
  line-height: 1.4;
  word-break: break-word;
  padding-right: 20px;
}

.delete-btn {
  position: absolute;
  top: 8px;
  right: 8px;
  opacity: 0;
  transition: opacity 0.3s;
  color: #909399;
}

.message-item:hover .delete-btn {
  opacity: 1;
}

.empty-state {
  padding: 20px;
  text-align: center;
  color: #909399;
}

.pagination {
  padding: 8px 16px;
  display: flex;
  justify-content: center;
  border-top: 1px solid #e4e7ed;
  background-color: #fafafa;
  border-radius: 0 0 8px 8px;
}
</style>