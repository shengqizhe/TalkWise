<template>
  <div class="student-registrations">
    <div class="container">
      <div class="page-title">
        <h1>我的预约</h1>
      </div>

      <div v-if="bookings.length" class="booking-grid">
        <div v-for="booking in bookings" :key="booking.lectureId" class="booking-card">
          <div class="card-head">
            <router-link :to="`/student/lectures/${booking.lectureId}`" class="card-title">
              {{ booking.lectureTitle }}
            </router-link>
            <span class="status-dot" :style="{ color: dotColor(booking.status) }">●</span>
          </div>
          <p>主讲人：{{ booking.speaker }}</p>
          <p>时间：{{ formatTime(booking.startTime) }}</p>
          <p>地点：{{ booking.location || '未设置' }}</p>
          <div class="card-bottom">
            <button class="btn-status" @click="viewDetail(booking)">已预约</button>
            <button class="btn-cancel" @click="handleCancel(booking)">取消预约</button>
          </div>
        </div>
      </div>

      <p v-else class="empty-tip">还没有预约任何讲座，去讲座列表看看吧</p>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import { useUserStore } from "../../stores/user";
import { getUserRegistrations, cancelRegistration } from "../../api/registration";

const router = useRouter();
const userStore = useUserStore();
const bookings = ref([]);

// 加载我的预约
async function loadBookings() {
  if (!userStore.userInfo?.id) return;
  try {
    const res = await getUserRegistrations(userStore.userInfo.id, 1);
    if (res.code === 200) {
      bookings.value = res.data || [];
    }
  } catch (error) {
    console.error("加载我的预约失败:", error);
    ElMessage.error("加载我的预约失败");
  }
}

// 状态圆点颜色：待确认灰 / 已确认绿
function dotColor(status) {
  return status === "confirmed" ? "#52c41a" : "#999";
}

// 查看讲座详情
function viewDetail(booking) {
  router.push(`/student/lectures/${booking.lectureId}`);
}

// 取消预约
async function handleCancel(booking) {
  try {
    await ElMessageBox.confirm(
      `确定要取消《${booking.lectureTitle}》的预约吗？取消后如需参加请重新预约。`,
      "取消预约",
      { type: "warning" },
    );
  } catch {
    return;
  }
  try {
    const response = await cancelRegistration(userStore.userInfo.id, booking.lectureId);
    if (response.code === 200) {
      ElMessage.success("已取消预约");
      bookings.value = bookings.value.filter((b) => b.lectureId !== booking.lectureId);
    } else {
      ElMessage.error(response.message || "取消失败");
    }
  } catch (error) {
    console.error("取消预约失败:", error);
    ElMessage.error("取消预约失败");
  }
}

// 短时间格式：06月15日 19:30
function formatTime(dateString) {
  if (!dateString) return "时间待定";
  const date = new Date(dateString);
  return date
    .toLocaleString("zh-CN", {
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
      hour12: false,
    })
    .replace(/\//g, "月")
    .replace(",", "日 ");
}

onMounted(loadBookings);
</script>

<style scoped>
/* ============ 以下样式数值与 student-booking.html 逐行一致 ============ */

.student-registrations {
  font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
  line-height: 1.6;
  color: #333;
}

.container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 60px 20px;
}

.page-title {
  margin-bottom: 40px;
}

.page-title h1 {
  font-size: 36px;
  font-weight: 800;
  line-height: 1.2;
  color: #333;
}

.booking-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 30px;
}

.booking-card {
  background-color: #ffffff;
  border-radius: 16px;
  padding: 30px;
  display: flex;
  flex-direction: column;
  border: 1px solid #f0f0f0;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.02);
  transition: transform 0.3s ease, box-shadow 0.3s ease;
}

.booking-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.05);
}

.card-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 20px;
}

.card-title {
  font-size: 22px;
  font-weight: bold;
  color: #333;
  text-decoration: none;
  line-height: 1.4;
}

.status-dot {
  font-size: 12px;
  line-height: 1.4;
}

.booking-card p {
  font-size: 14px;
  color: #666;
  margin: 8px 0;
}

.card-bottom {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid #f9f9f9;
}

.btn-status {
  padding: 6px 16px;
  background: #f5f5f5;
  color: #333;
  border: none;
  border-radius: 20px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  font-family: inherit;
}

.btn-cancel {
  padding: 8px 20px;
  border: 1px solid #e0e0e0;
  color: #666;
  background: #fff;
  border-radius: 20px;
  font-size: 13px;
  cursor: pointer;
  font-family: inherit;
  transition: all 0.2s;
}

.btn-cancel:hover {
  border-color: #ff4d4f;
  color: #ff4d4f;
  background-color: #fffaf9;
}

.empty-tip {
  text-align: center;
  color: #999;
  font-size: 15px;
  padding: 60px 0;
}

/* 响应式 */
@media (max-width: 768px) {
  .booking-grid {
    grid-template-columns: 1fr;
  }
}
</style>
