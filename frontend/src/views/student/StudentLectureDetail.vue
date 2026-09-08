<template>
  <div class="lecture-detail">
    <el-card v-loading="loading" class="detail-card" shadow="hover">
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <span class="header-title">讲座详情</span>
          </div>
          <el-button @click="goBack" type="primary" plain class="back-button">
            <el-icon><ArrowLeft /></el-icon> 返回列表
          </el-button>
        </div>
      </template>

      <div v-if="lecture" class="lecture-content">
        <div class="lecture-header">
          <h1 class="lecture-title">{{ lecture.title }}</h1>
          <div class="lecture-meta">
            <el-tag
              :type="getStatusType(lecture.status)"
              class="status-tag"
              effect="dark"
              size="large"
            >
              <el-icon class="status-icon"><Clock /></el-icon>
              {{ getStatusText(lecture.status) }}
            </el-tag>
            <span class="lecture-capacity">
              <el-icon><User /></el-icon> 已报名:
              {{ lecture.registeredCount }}/{{ lecture.capacity }}
              <el-progress
                :percentage="
                  Math.round((lecture.registeredCount / lecture.capacity) * 100)
                "
                :status="
                  lecture.registeredCount >= lecture.capacity ? 'success' : ''
                "
                class="capacity-progress"
                :stroke-width="10"
              />
            </span>
          </div>
        </div>

        <el-divider />

        <div class="lecture-info-card">
          <div class="info-grid">
            <div class="info-item">
              <el-icon class="info-icon"><User /></el-icon>
              <div class="info-content">
                <div class="info-label">主讲人</div>
                <div class="info-value">{{ lecture.speaker }}</div>
              </div>
            </div>
            <div class="info-item">
              <el-icon class="info-icon"><Location /></el-icon>
              <div class="info-content">
                <div class="info-label">地点</div>
                <div class="info-value">
                  {{ lecture.extendData?.locationName || lecture.location || '未设置' }}
                </div>
              </div>
            </div>
            <div class="info-item">
              <el-icon class="info-icon"><Calendar /></el-icon>
              <div class="info-content">
                <div class="info-label">时间</div>
                <div class="info-value">
                  {{ formatDate(lecture.lectureTime) }}
                </div>
              </div>
            </div>
            <div class="info-item">
              <el-icon class="info-icon"><Collection /></el-icon>
              <div class="info-content">
                <div class="info-label">分类</div>
                <div class="info-value">
                  {{ lecture.categoryName || "未分类" }}
                </div>
              </div>
            </div>
          </div>
        </div>

        <el-divider />

        <div class="lecture-sections">
          <el-card class="section-card" shadow="hover">
            <template #header>
              <div class="section-header">
                <el-icon><InfoFilled /></el-icon>
                <span>讲座简介</span>
              </div>
            </template>
            <div class="section-content">
              <p class="summary-text">{{ lecture.summary }}</p>
            </div>
          </el-card>

          <el-card class="section-card" shadow="hover">
            <template #header>
              <div class="section-header">
                <el-icon><Document /></el-icon>
                <span>讲座内容</span>
              </div>
            </template>
            <div
              class="section-content content-html"
              v-html="lecture.content"
            ></div>
          </el-card>

          <!-- 宣讲内容卡片 -->
          <el-card 
            v-if="lecture.promotionContent && lecture.promotionContent.trim()"
            class="section-card" 
            shadow="hover"
          >
            <template #header>
              <div class="section-header">
                <el-icon><Promotion /></el-icon>
                <span>宣讲内容</span>
              </div>
            </template>
            <div class="section-content">
              <!-- 宣讲图片 -->
              <div class="promotion-images">
                <h4>宣讲图片</h4>
                <div class="image-gallery">
                  <img 
                    v-for="(imageUrl, index) in getPromotionImages(lecture.promotionContent)"
                    :key="index"
                    :src="imageUrl"
                    :alt="`宣讲图片${index + 1}`"
                    class="promotion-image"
                    @click="previewImage(imageUrl)"
                  />
                </div>
              </div>
            </div>
          </el-card>
        </div>

        <div class="action-area">
          <el-button
            :type="isRegistered ? 'danger' : 'primary'"
            :disabled="
              lecture.status === 2 ||
              lecture.status === 3 ||
              lecture.status === 4 ||
              (!isRegistered && lecture.registeredCount >= lecture.capacity) ||
              (isRegistered && lecture.status !== 1)
            "
            size="large"
            round
            class="action-button"
            @click="handleRegistration"
          >
            <el-icon class="action-icon" v-if="isRegistered"><Close /></el-icon>
            <el-icon class="action-icon" v-else><Check /></el-icon>
            {{ isRegistered ? "取消报名" : "立即报名" }}
          </el-button>
          <div
            class="action-hint"
            v-if="lecture.status === 3 || lecture.status === 4"
          >
            <el-icon><Warning /></el-icon>
            <span>{{
              lecture.status === 3 ? "讲座已结束" : "讲座已取消"
            }}</span>
          </div>
          <div
            class="action-hint"
            v-else-if="
              !isRegistered && lecture.registeredCount >= lecture.capacity
            "
          >
            <el-icon><Warning /></el-icon>
            <span>讲座名额已满</span>
          </div>
        </div>
      </div>

      <el-empty v-else description="讲座不存在或已被删除" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, getCurrentInstance } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage, ElLoading } from "element-plus";
import {
  ArrowLeft,
  User,
  Clock,
  Calendar,
  Location,
  Collection,
  InfoFilled,
  Document,
  Warning,
  Check,
  Close,
  Promotion,
} from "@element-plus/icons-vue";
import { useUserStore } from "../../stores/user";
import { getLectureById } from "../../api/lecture";
import {
  registerLecture,
  cancelRegistration,
  checkRegistration,
  getLectureRegistrations,
} from "../../api/registration";
import { useWebSocket } from "../../composables/useWebSocket";


// 获取全局事件总线
const { proxy: app } = getCurrentInstance();

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const { connect } = useWebSocket();

const lectureId = route.params.id;
const lecture = ref(null);
const loading = ref(false);
const isRegistered = ref(false);

// 处理宣讲图片URL
function getPromotionImages(promotionContent) {
  if (!promotionContent || !promotionContent.trim()) {
    return [];
  }
  return promotionContent.split(',').filter(url => url.trim()).map(url => url.trim());
}

// 预览图片
function previewImage(imageUrl) {
  window.open(imageUrl, '_blank');
}

// 获取讲座详情
async function fetchLectureDetail() {
  if (!lectureId) {
    ElMessage.error("讲座ID不能为空");
    return;
  }

  loading.value = true;
  try {
    const response = await getLectureById(lectureId);
    if (response.code === 200) {
      lecture.value = response.data;

      // 检查当前用户是否已报名
      if (userStore.userInfo) {
        const checkRes = await checkRegistration(
          userStore.userInfo.id,
          lectureId,
        );
        if (checkRes.code === 200) {
          isRegistered.value = checkRes.data;
        }
      }

      // 获取讲座的实际报名列表
      const registrationsRes = await getLectureRegistrations(lectureId);
      if (registrationsRes.code === 200) {
        // 只计算状态为1（已报名）的记录
        const activeRegistrations =
          registrationsRes.data.filter((reg) => reg.status === 1) || [];
        // 使用报名列表长度作为已报名人数
        lecture.value.registeredCount = activeRegistrations.length;
      }
    }
  } catch (error) {
    console.error("获取讲座详情失败:", error);
    ElMessage.error("获取讲座详情失败");
  } finally {
    loading.value = false;
  }
}

// 处理报名/取消报名
async function handleRegistration() {
  if (!userStore.userInfo) {
    ElMessage.warning("请先登录");
    return;
  }

  const userId = userStore.userInfo.id;

  const loadingInstance = ElLoading.service({
    text: isRegistered.value ? "取消报名中..." : "报名中...",
  });

  try {
    if (isRegistered.value) {
      // 检查讲座状态，只有未开始的讲座才能取消报名
      if (lecture.value.status !== 1) {
        if (lecture.value.status === 2) {
          ElMessage.warning("讲座正在进行中，无法取消报名");
        } else {
          ElMessage.warning("只能取消未开始状态的讲座报名");
        }
        return;
      }
      
      // 取消报名
      const response = await cancelRegistration(userId, lectureId);
      if (response.code === 200) {
        ElMessage.success("取消报名成功");
        isRegistered.value = false;

        // 重新获取讲座的报名列表
        const registrationsRes = await getLectureRegistrations(lectureId);
        if (registrationsRes.code === 200) {
          // 只计算状态为1（已报名）的记录
          const activeRegistrations =
            registrationsRes.data.filter((reg) => reg.status === 1) || [];
          // 使用报名列表长度作为已报名人数
          lecture.value.registeredCount = activeRegistrations.length;
        }
      }
    } else {
      // 检查讲座是否已满
      if (lecture.value.registeredCount >= lecture.value.capacity) {
        ElMessage.warning("该讲座已满员");
        return;
      }

      // 报名讲座
      const response = await registerLecture(userId, lectureId);
      if (response.code === 200) {
        ElMessage.success("报名成功");
        isRegistered.value = true;

        // 重新获取讲座的报名列表
        const registrationsRes = await getLectureRegistrations(lectureId);
        if (registrationsRes.code === 200) {
          // 只计算状态为1（已报名）的记录
          const activeRegistrations =
            registrationsRes.data.filter((reg) => reg.status === 1) || [];
          // 使用报名列表长度作为已报名人数
          lecture.value.registeredCount = activeRegistrations.length;
        }
        
        // 触发全局事件，通知主页面刷新数据
        app.$bus.emit('lecture-registration-changed');
      }
    }
  } catch (error) {
    console.error("操作失败:", error);
    ElMessage.error(isRegistered.value ? "取消报名失败" : "报名失败");
  } finally {
    loadingInstance.close();
  }
}

// 格式化日期
function formatDate(dateString) {
  const date = new Date(dateString);
  return date.toLocaleString("zh-CN");
}

// 获取状态类型
function getStatusType(status) {
  const statusMap = {
    1: "success", // 未开始
    2: "warning", // 进行中
    3: "info", // 已结束
    4: "danger", // 已取消
  };
  return statusMap[status] || "info";
}

// 获取状态文本
function getStatusText(status) {
  const statusMap = {
    1: "即将开始",
    2: "进行中",
    3: "已结束",
    4: "已取消",
  };
  return statusMap[status] || "未知";
}

// 返回列表页
function goBack() {
  router.push("/student/lectures");
}

// WebSocket消息监听函数
function handleRegistrationSuccess(event) {
  const message = event.detail;
  console.log('收到报名成功通知:', message);
  
  // 显示成功通知
  ElMessage({
    message: `报名成功！讲座：${message.content}`,
    type: 'success',
    duration: 5000,
    showClose: true
  });
  
  // 刷新讲座详情
  fetchLectureDetail();
}

// 组件挂载时获取讲座详情并监听WebSocket消息
onMounted(async () => {
  // 初始化WebSocket连接
  try {
    await connect();
    console.log('WebSocket连接成功');
  } catch (error) {
    console.error('WebSocket连接失败:', error);
  }
  
  fetchLectureDetail();
  
  // 监听报名成功通知
  window.addEventListener('websocket-registration-success', handleRegistrationSuccess);
});

// 组件卸载时移除事件监听
onUnmounted(() => {
  window.removeEventListener('websocket-registration-success', handleRegistrationSuccess);
});
</script>

<style scoped>
.lecture-detail {
  max-width: 1200px;
  margin: 0 auto;
  padding: 32px 20px;
  font-family:
    "Helvetica Neue", Helvetica, "PingFang SC", "Hiragino Sans GB",
    "Microsoft YaHei", Arial, sans-serif;
}

.detail-card {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.06);
  transition: all 0.3s ease;
}

.detail-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 0;
}

.header-left {
  display: flex;
  align-items: center;
}

.header-title {
  font-size: 18px;
  font-weight: 600;
  color: #000;
}

.back-button {
  transition: all 0.3s ease;
}

.back-button:hover {
  transform: translateX(-5px);
}

.lecture-header {
  margin-bottom: 30px;
  padding: 20px;
  background: linear-gradient(to right, #f5f5f5, #e8e8e8);
  border-radius: 16px;
}

.lecture-title {
  font-size: 28px;
  font-weight: 700;
  margin-bottom: 15px;
  color: #303133;
  text-shadow: 1px 1px 2px rgba(0, 0, 0, 0.1);
}

.lecture-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 20px;
  margin-top: 15px;
}

.status-tag {
  font-size: 14px;
  padding: 8px 16px;
  border-radius: 20px;
  display: flex;
  align-items: center;
  gap: 5px;
}

.status-icon {
  margin-right: 5px;
}

.lecture-capacity {
  display: flex;
  flex-direction: column;
  gap: 8px;
  color: #606266;
  flex-grow: 1;
  max-width: 300px;
}

.capacity-progress {
  margin-top: 5px;
}

.lecture-info-card {
  background-color: #fafafa;
  border-radius: 16px;
  padding: 20px;
  margin-bottom: 30px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
  gap: 20px;
}

.info-item {
  display: flex;
  align-items: flex-start;
  background-color: white;
  padding: 15px;
  border-radius: 8px;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.05);
  transition: all 0.3s ease;
}

.info-item:hover {
  transform: translateY(-3px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.info-icon {
  font-size: 24px;
  color: #000;
  margin-right: 15px;
  margin-top: 5px;
}

.info-content {
  flex-grow: 1;
}

.info-label {
  font-size: 14px;
  color: #909399;
  margin-bottom: 5px;
}

.info-value {
  font-size: 16px;
  font-weight: 500;
  color: #303133;
}

.location-value {
  color: #000;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 5px;
  transition: all 0.3s ease;
}

.location-value:hover {
  text-decoration: underline;
  transform: translateY(-2px);
}

.location-link-icon {
  font-size: 14px;
  margin-left: 5px;
}

.lecture-sections {
  display: flex;
  flex-direction: column;
  gap: 30px;
  margin-bottom: 30px;
}

.section-card {
  overflow: hidden;
  transition: all 0.3s ease;
}

.section-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.1);
}

.section-header {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 16px;
  font-weight: 600;
  color: #000;
}

.section-content {
  padding: 10px 0;
}

.summary-text {
  color: #606266;
  line-height: 1.8;
  font-size: 15px;
  text-align: justify;
}

.content-html {
  color: #303133;
  line-height: 1.8;
}

.content-html :deep(h1),
.content-html :deep(h2),
.content-html :deep(h3),
.content-html :deep(h4),
.content-html :deep(h5),
.content-html :deep(h6) {
  margin-top: 20px;
  margin-bottom: 10px;
  color: #303133;
}

.content-html :deep(p) {
  margin-bottom: 15px;
  line-height: 1.8;
}

.content-html :deep(ul),
.content-html :deep(ol) {
  padding-left: 20px;
  margin-bottom: 15px;
}

.content-html :deep(li) {
  margin-bottom: 5px;
}

.content-html :deep(img) {
  max-width: 100%;
  height: auto;
  border-radius: 4px;
  margin: 10px 0;
}

.action-area {
  margin-top: 40px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 15px;
}

.action-button {
  padding: 12px 30px;
  font-size: 16px;
  font-weight: 500;
  transition: all 0.3s ease;
  min-width: 180px;
}

.action-button:hover:not(:disabled) {
  transform: translateY(-3px);
  box-shadow: 0 6px 12px rgba(0, 0, 0, 0.1);
}

.action-icon {
  margin-right: 8px;
}

.action-hint {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #e6a23c;
  font-size: 14px;
}

/* 宣讲内容样式 */
.promotion-text {
  margin-bottom: 20px;
}

.promotion-text h4 {
  color: #000;
  margin-bottom: 10px;
  font-size: 16px;
  font-weight: 600;
}

.promotion-images h4 {
  margin-bottom: 15px;
  color: #303133;
  font-size: 16px;
}

.image-gallery {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.promotion-image {
  width: 200px;
  height: 150px;
  object-fit: cover;
  border-radius: 8px;
  cursor: pointer;
  transition: transform 0.3s ease;
  border: 1px solid #e4e7ed;
}

.promotion-image:hover {
  transform: scale(1.05);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.promotion-images h4 {
  color: #000;
  margin-bottom: 15px;
  font-size: 16px;
  font-weight: 600;
}

.image-gallery {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 15px;
  margin-top: 10px;
}

.image-item {
  position: relative;
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.3s ease;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.image-item:hover {
  transform: translateY(-5px);
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.15);
}

.promotion-image {
  width: 100%;
  height: 150px;
  object-fit: cover;
  display: block;
  transition: transform 0.3s ease;
}

.image-item:hover .promotion-image {
  transform: scale(1.05);
}

/* 响应式调整 */
@media (max-width: 768px) {
  .lecture-detail {
    padding: 10px;
  }

  .info-grid {
    grid-template-columns: 1fr;
  }

  .lecture-title {
    font-size: 22px;
  }

  .lecture-meta {
    flex-direction: column;
    align-items: flex-start;
  }

  .lecture-capacity {
    max-width: 100%;
  }

  .image-gallery {
    grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
    gap: 10px;
  }

  .promotion-image {
    height: 120px;
  }
}
</style>
