<script setup>
import { ref, computed } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { getUserRegistrations, cancelUserRegistration } from "@/api/aiAssistant";
import { agentChat } from "@/api/agent";
import { registerLecture as registerLectureApi, checkRegistration } from "@/api/registration";
import { useUserStore } from "@/stores/user";
import { useRouter } from "vue-router";

const userStore = useUserStore();
const router = useRouter();

const inputText = ref("");
const result = ref("");
const lectures = ref([]);
const loading = ref(false);
const history = ref([
  {
    role: 'ai',
    content: '你好！我是AI智能助手，可以帮你查找讲座信息、推荐合适的讲座、管理报名等。有什么可以帮你的吗？',
    timestamp: new Date()
  }
]);

// 通知弹窗相关
const notificationVisible = ref(false);
const notificationConfig = ref({
  title: '提示',
  message: '',
  description: '',
  type: 'info',
  showCancel: true,
  cancelText: '取消',
  confirmText: '确定',
  confirmButtonType: 'primary',
  loading: false
});

const isLoggedIn = computed(() => {
  return userStore.token && userStore.userInfo;
});

function getUserId() {
  let uid = localStorage.getItem('ai_user_id');
  if (!uid) {
    uid = 'u_' + Math.random().toString(36).slice(2, 10);
    localStorage.setItem('ai_user_id', uid);
  }
  return uid;
}

// 显示通知弹窗
const showNotification = (config) => {
  notificationConfig.value = { ...notificationConfig.value, ...config };
  notificationVisible.value = true;
};

// 处理通知确认
const handleNotificationConfirm = async () => {
  if (notificationConfig.value.onConfirm) {
    notificationConfig.value.loading = true;
    try {
      await notificationConfig.value.onConfirm();
      notificationVisible.value = false;
    } catch (error) {
      console.error('操作失败:', error);
    } finally {
      notificationConfig.value.loading = false;
    }
  } else {
    notificationVisible.value = false;
  }
};

// 处理通知取消
const handleNotificationCancel = () => {
  notificationVisible.value = false;
};

async function handleAnalyze() {
  if (!inputText.value.trim()) {
    ElMessage.warning("请输入你的需求");
    return;
  }
  
  // 检查是否是报名管理相关的查询
  const message = inputText.value.toLowerCase();
  if (message.includes('我的报名') || message.includes('已报名') || message.includes('报名记录')) {
    if (!isLoggedIn.value) {
      showNotification({
        title: '登录提示',
        message: '请先登录',
        description: '查看报名记录需要先登录您的账户',
        type: 'warning',
        showCancel: false,
        confirmText: '去登录'
      });
      return;
    }
    await handleGetUserRegistrations();
    return;
  }
  
  loading.value = true;
  try {
    // 添加用户消息
    history.value.push({ 
      role: 'user', 
      content: inputText.value,
      timestamp: new Date()
    });
    const res = await agentChat(inputText.value.trim());
    // 添加AI回复
    const aiMessage = {
      role: 'ai',
      content: res.data.reply,
      timestamp: new Date()
    };
    if (res.data.lectures && res.data.lectures.length > 0) {
      aiMessage.lectures = res.data.lectures.map(lecture => ({
        ...lecture,
        registering: false,
        registered: false
      }));
      if (isLoggedIn.value) {
        await checkUserRegistrations(aiMessage.lectures);
      }
    }
    history.value.push(aiMessage);
    result.value = res.data.reply;
    lectures.value = res.data.lectures || [];
    inputText.value = "";
  } catch (error) {
    ElMessage.error("AI助手请求失败");
  } finally {
    loading.value = false;
  }
}

// 获取用户已报名的讲座
const handleGetUserRegistrations = async () => {
  if (!isLoggedIn.value) {
    ElMessage.warning('请先登录');
    return;
  }
  
  loading.value = true;
  try {
    const userId = userStore.userInfo?.id;
    const res = await getUserRegistrations(userId);
    
    // 添加用户消息
    history.value.push({ 
      role: 'user', 
      content: inputText.value,
      timestamp: new Date()
    });
    
    // 添加AI回复
    const aiMessage = {
      role: 'ai',
      content: res.data.reply,
      timestamp: new Date()
    };
    
    if (res.data.lectures && res.data.lectures.length > 0) {
      aiMessage.lectures = res.data.lectures.map(lecture => ({
        ...lecture,
        registered: true, // 这些都是已报名的讲座
        canCancel: true
      }));
    }
    
    history.value.push(aiMessage);
    inputText.value = "";
  } catch (error) {
    ElMessage.error("获取报名记录失败");
  } finally {
    loading.value = false;
  }
};

const checkUserRegistrations = async (lectures) => {
  const userId = userStore.userInfo?.id;
  if (!userId) return;
  for (const lecture of lectures) {
    try {
      const response = await checkRegistration(userId, lecture.id);
      lecture.registered = response.data.registered;
    } catch (error) {
      console.error('检查报名状态失败:', error);
    }
  }
};

const registerLecture = async (lecture) => {
  if (!isLoggedIn.value) {
    showNotification({
      title: '登录提示',
      message: '请先登录',
      description: '报名讲座需要先登录您的账户',
      type: 'warning',
      showCancel: false,
      confirmText: '去登录',
      onConfirm: () => goToLogin()
    });
    return;
  }
  
  const userId = userStore.userInfo?.id;
  if (!userId) {
    ElMessage.warning('用户信息获取失败，请重新登录');
    return;
  }
  
  lecture.registering = true;
  try {
    await registerLectureApi(userId, lecture.id);
    lecture.registered = true;
    
    showNotification({
      title: '报名成功',
      message: '讲座报名成功！',
      description: `您已成功报名《${lecture.title}》，请按时参加。`,
      type: 'success',
      showCancel: false,
      confirmText: '确定'
    });
    
    // 更新历史记录中的讲座状态
    history.value.forEach(message => {
      if (message.lectures) {
        message.lectures.forEach(l => {
          if (l.id === lecture.id) {
            l.registered = true;
          }
        });
      }
    });
  } catch (error) {
    showNotification({
      title: '报名失败',
      message: '讲座报名失败',
      description: error.response?.data?.message || '请稍后再试',
      type: 'error',
      showCancel: false,
      confirmText: '确定'
    });
  } finally {
    lecture.registering = false;
  }
};

// 取消报名
const cancelLectureRegistration = async (lecture) => {
  if (!isLoggedIn.value) {
    ElMessage.warning('请先登录');
    return;
  }
  
  const userId = userStore.userInfo?.id;
  if (!userId) {
    ElMessage.warning('用户信息获取失败，请重新登录');
    return;
  }
  
  showNotification({
    title: '确认取消报名',
    message: `确定要取消报名《${lecture.title}》吗？`,
    description: '取消报名后，您将无法参加该讲座。',
    type: 'warning',
    showCancel: true,
    cancelText: '再想想',
    confirmText: '确定取消',
    confirmButtonType: 'danger',
    onConfirm: async () => {
      try {
        await cancelUserRegistration(userId, lecture.id);
        lecture.registered = false;
        lecture.canCancel = false;
        
        showNotification({
          title: '取消成功',
          message: '报名已取消',
          description: `您已成功取消报名《${lecture.title}》。`,
          type: 'success',
          showCancel: false,
          confirmText: '确定'
        });
        
        // 更新历史记录中的讲座状态
        history.value.forEach(message => {
          if (message.lectures) {
            message.lectures.forEach(l => {
              if (l.id === lecture.id) {
                l.registered = false;
                l.canCancel = false;
              }
            });
          }
        });
      } catch (error) {
        showNotification({
          title: '取消失败',
          message: '取消报名失败',
          description: error.response?.data?.message || '请稍后再试',
          type: 'error',
          showCancel: false,
          confirmText: '确定'
        });
      }
    }
  });
};

const goToLogin = () => {
  router.push('/login');
};

// 格式化讲座时间
const formatLectureTime = (timeStr) => {
  if (!timeStr) return '';
  const date = new Date(timeStr);
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  });
};

// 查看讲座详情
const viewLectureDetail = (lectureId) => {
  router.push(`/student/lectures/${lectureId}`);
};
</script>

<template>
  <div class="student-ai">
    <el-row :gutter="20">
      <el-col :span="24">
        <el-card class="ai-analysis-card">
          <div class="card-header">
            <h2>AI智能助手</h2>
            <p class="ai-description">基于人工智能的智能对话系统，为您推荐个性化讲座，解答问题，管理报名，让学习体验更智能、更高效</p>
          </div>
          <!-- 聊天区域 -->
          <div class="chat-container">
            <div class="chat-messages" v-if="history.length">
              <div v-for="(item, idx) in history" :key="idx" :class="['chat-bubble', item.role]">
                <div class="message-header">
                  <b v-if="item.role==='user'">我：</b>
                  <b v-else>AI：</b>
                  <span class="message-time">{{ item.timestamp ? item.timestamp.toLocaleString() : '' }}</span>
                </div>
                <div class="message-content">{{ item.content }}</div>
                <!-- 讲座推荐卡片 -->
                <div v-if="item.lectures && item.lectures.length > 0" class="lecture-cards">
                  <div v-for="lecture in item.lectures" :key="lecture.id" class="lecture-card">
                    <div class="lecture-header">
                      <h4 class="lecture-title">{{ lecture.title }}</h4>
                      <span class="lecture-speaker">{{ lecture.speaker }}</span>
                    </div>
                    <div class="lecture-info">
                      <div class="lecture-time">
                        📅 {{ formatLectureTime(lecture.lectureTime) }}
                      </div>
                      <div class="lecture-location" v-if="lecture.locationName">
                        📍 {{ lecture.locationName }}
                      </div>
                      <div class="lecture-summary" v-if="lecture.summary">
                        {{ lecture.summary }}
                      </div>
                    </div>
                    <div class="lecture-actions">
                      <el-button 
                        v-if="!lecture.registered" 
                        type="primary" 
                        size="small" 
                        @click="registerLecture(lecture)" 
                        :loading="lecture.registering"
                      >
                        立即报名
                      </el-button>
                      <el-button 
                        v-else-if="lecture.canCancel" 
                        type="danger" 
                        size="small" 
                        @click="cancelLectureRegistration(lecture)"
                      >
                        取消报名
                      </el-button>
                      <el-button 
                        v-else 
                        type="success" 
                        size="small" 
                        disabled
                      >
                        已报名
                      </el-button>
                      <el-button 
                        link 
                        size="small" 
                        @click="viewLectureDetail(lecture.id)"
                      >
                        查看详情
                      </el-button>
                    </div>
                    <div v-if="!isLoggedIn && !lecture.registered" class="login-tip">
                      <el-button link size="small" @click="goToLogin">请先登录后报名</el-button>
                    </div>
                  </div>
                </div>
              </div>
            </div>
            <!-- 输入区域 -->
            <div class="chat-input">
              <el-input
                v-model="inputText"
                placeholder="与AI对话，例如：推荐AI相关讲座、我想学习编程、查看我的报名、取消报名等..."
                @keyup.enter="handleAnalyze"
                :disabled="loading"
              >
                <template #append>
                  <el-button 
                    type="primary" 
                    @click="handleAnalyze" 
                    :loading="loading"
                    :disabled="!inputText.trim()"
                  >
                    发送
                  </el-button>
                </template>
              </el-input>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
    
    <!-- 通知弹窗组件 -->
    <el-dialog
      v-model="notificationVisible"
      :title="notificationConfig.title"
      width="400px"
      :close-on-click-modal="false"
    >
      <div class="notification-content">
        <p class="notification-message">{{ notificationConfig.message }}</p>
        <p v-if="notificationConfig.description" class="notification-description">
          {{ notificationConfig.description }}
        </p>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button
            v-if="notificationConfig.showCancel"
            @click="handleNotificationCancel"
          >
            {{ notificationConfig.cancelText }}
          </el-button>
          <el-button
            :type="notificationConfig.confirmButtonType"
            @click="handleNotificationConfirm"
            :loading="notificationConfig.loading"
          >
            {{ notificationConfig.confirmText }}
          </el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.student-ai {
  max-width: 1200px;
  margin: 0 auto;
  padding: 32px 20px;
}

.ai-analysis-card {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.06);
}

.card-header {
  text-align: center;
  margin-bottom: 20px;
}

.card-header h2 {
  color: #333;
  margin-bottom: 8px;
}

.ai-description {
  color: #666;
  font-size: 14px;
  margin: 0;
}

.chat-container {
  max-height: 600px;
  display: flex;
  flex-direction: column;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  background: #f8f9fa;
  border-radius: 8px;
  margin-bottom: 20px;
  max-height: 500px;
}

.chat-bubble {
  margin-bottom: 20px;
  max-width: 80%;
}

.chat-bubble.user {
  margin-left: auto;
  text-align: right;
}

.chat-bubble.ai {
  margin-right: auto;
  text-align: left;
}

.message-header {
  margin-bottom: 8px;
  font-size: 14px;
  font-weight: 600;
}

.message-time {
  font-size: 12px;
  color: #999;
  margin-left: 10px;
  font-weight: normal;
}

.message-content {
  padding: 12px 16px;
  border-radius: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
}

.chat-bubble.user .message-content {
  background: #000;
  color: white;
  border-bottom-right-radius: 4px;
}

.chat-bubble.ai .message-content {
  background: white;
  color: #333;
  border: 1px solid #e0e0e0;
  border-bottom-left-radius: 4px;
}

/* 讲座卡片样式 */
.lecture-cards {
  margin-top: 15px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.lecture-card {
  background: white;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  padding: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  transition: all 0.3s ease;
}

.lecture-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  transform: translateY(-1px);
}

.lecture-header {
  margin-bottom: 12px;
}

.lecture-title {
  font-size: 16px;
  font-weight: 600;
  color: #333;
  margin: 0 0 6px 0;
  line-height: 1.3;
}

.lecture-speaker {
  font-size: 13px;
  color: #666;
  background: #f0f0f0;
  padding: 3px 8px;
  border-radius: 4px;
}

.lecture-info {
  margin-bottom: 12px;
}

.lecture-time, .lecture-location {
  font-size: 13px;
  color: #666;
  margin-bottom: 6px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.lecture-summary {
  font-size: 13px;
  color: #888;
  line-height: 1.4;
  margin-top: 8px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.lecture-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

.login-tip {
  margin-top: 8px;
  text-align: center;
}

.chat-input {
  margin-top: 20px;
}

/* 通知对话框样式 */
.notification-content {
  text-align: center;
  padding: 20px 0;
}

.notification-message {
  font-size: 16px;
  font-weight: 600;
  color: #333;
  margin-bottom: 10px;
}

.notification-description {
  font-size: 14px;
  color: #666;
  line-height: 1.5;
  margin: 0;
}

.dialog-footer {
  display: flex;
  justify-content: center;
  gap: 12px;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .student-ai {
    padding: 10px;
  }
  
  .chat-bubble {
    max-width: 90%;
  }
  
  .lecture-actions {
    flex-direction: column;
    gap: 8px;
  }
  
  .lecture-actions .el-button {
    width: 100%;
  }
}
</style>