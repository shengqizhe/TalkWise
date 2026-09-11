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
            <div class="bottom-left">
              <button class="btn-status" @click="viewDetail(booking)">已预约</button>
              <span v-if="booking.checkinStatus === 1" class="checkin-badge">✓ 已签到</span>
            </div>
            <div class="bottom-right">
              <button v-if="canCheckin(booking)" class="btn-primary-sm" @click="handleCheckin(booking)">
                签到
              </button>
              <button v-if="canEvaluate(booking)" class="btn-primary-sm" @click="openEvaluate(booking)">
                {{ booking.evaluation ? '修改评价' : '评价' }}
              </button>
              <button class="btn-cancel" @click="handleCancel(booking)">取消预约</button>
            </div>
          </div>
        </div>
      </div>

      <p v-else class="empty-tip">还没有预约任何讲座，去讲座列表看看吧</p>
    </div>

    <!-- 评价对话框 -->
    <el-dialog v-model="evaluateVisible" title="评价讲座" width="480px">
      <p class="eval-lecture">《{{ evalForm.lectureTitle }}》</p>
      <div class="eval-row">
        <label>评分</label>
        <el-rate v-model="evalForm.score" show-text :texts="['很差', '较差', '一般', '不错', '很棒']" />
      </div>
      <div class="eval-row eval-content-row">
        <label>评价内容</label>
        <el-input
          v-model="evalForm.content"
          type="textarea"
          :rows="4"
          maxlength="500"
          show-word-limit
          placeholder="说说你的感受与建议（可选）"
        />
      </div>
      <template #footer>
        <el-button @click="evaluateVisible = false">取消</el-button>
        <el-button type="primary" :loading="evalSubmitting" @click="submitEvaluate">提交评价</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import { useUserStore } from "../../stores/user";
import {
  getUserRegistrations,
  cancelRegistration,
  checkinRegistration,
} from "../../api/registration";
import {
  getEvaluationsByUserId,
  createEvaluation,
  updateEvaluation,
} from "../../api/evaluation";

const router = useRouter();
const userStore = useUserStore();
const bookings = ref([]);

// 评价对话框
const evaluateVisible = ref(false);
const evalSubmitting = ref(false);
const evalForm = reactive({
  id: null,
  lectureId: null,
  lectureTitle: "",
  score: 5,
  content: "",
});

// 加载我的预约（附带：我的评价记录，用于判断"评价/修改评价"）
async function loadBookings() {
  if (!userStore.userInfo?.id) return;
  try {
    const [regRes, evalRes] = await Promise.all([
      getUserRegistrations(userStore.userInfo.id, 1),
      getEvaluationsByUserId(userStore.userInfo.id).catch(() => ({ data: [] })),
    ]);
    const evalMap = {};
    (evalRes.data || []).forEach((ev) => {
      evalMap[ev.lectureId] = ev;
    });
    bookings.value = (regRes.data || []).map((item) => ({
      ...item,
      evaluation: evalMap[item.lectureId] || null,
    }));
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

// 可签到：已确认报名且未签到
function canCheckin(booking) {
  return booking.status === "confirmed" && booking.checkinStatus !== 1;
}

// 签到
async function handleCheckin(booking) {
  try {
    const res = await checkinRegistration(booking.id);
    if (res.code === 200) {
      ElMessage.success("签到成功");
      booking.checkinStatus = 1;
    } else {
      ElMessage.error(res.message || "签到失败");
    }
  } catch (error) {
    console.error("签到失败:", error);
    ElMessage.error("签到失败，请稍后重试");
  }
}

// 可评价：已确认报名且讲座已结束（lectureStatus = 3）
function canEvaluate(booking) {
  return booking.status === "confirmed" && booking.lectureStatus === 3;
}

// 打开评价对话框
function openEvaluate(booking) {
  evalForm.id = booking.evaluation?.id || null;
  evalForm.lectureId = booking.lectureId;
  evalForm.lectureTitle = booking.lectureTitle;
  evalForm.score = Number(booking.evaluation?.score) || 5;
  evalForm.content = booking.evaluation?.content || "";
  evaluateVisible.value = true;
}

// 提交评价（新建或修改）
async function submitEvaluate() {
  if (!evalForm.score) {
    ElMessage.warning("请先选择评分");
    return;
  }
  evalSubmitting.value = true;
  try {
    const payload = {
      id: evalForm.id,
      lectureId: evalForm.lectureId,
      userId: userStore.userInfo?.id,
      score: evalForm.score,
      content: evalForm.content,
    };
    const res = evalForm.id ? await updateEvaluation(payload) : await createEvaluation(payload);
    if (res.code === 200) {
      ElMessage.success(evalForm.id ? "评价已更新" : "评价成功，感谢反馈");
      evaluateVisible.value = false;
      loadBookings();
    } else {
      ElMessage.error(res.message || "评价提交失败");
    }
  } catch (error) {
    console.error("评价提交失败:", error);
    ElMessage.error("评价提交失败");
  } finally {
    evalSubmitting.value = false;
  }
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
/* ============ 黑白极简风格（与全局一致） ============ */

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
  gap: 10px;
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid #f9f9f9;
  flex-wrap: wrap;
}

.bottom-left,
.bottom-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
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

.checkin-badge {
  font-size: 13px;
  color: #00b42a;
  font-weight: 500;
}

.btn-primary-sm {
  padding: 6px 18px;
  background: #000;
  color: #fff;
  border: none;
  border-radius: 20px;
  font-size: 13px;
  font-family: inherit;
  cursor: pointer;
  transition: background 0.2s;
}

.btn-primary-sm:hover {
  background: #333;
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

/* 评价对话框 */
.eval-lecture {
  font-size: 15px;
  font-weight: 600;
  color: #333;
  margin-bottom: 18px;
}

.eval-row {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 18px;
}

.eval-row > label {
  width: 70px;
  flex-shrink: 0;
  font-size: 14px;
  color: #333;
}

.eval-content-row {
  align-items: flex-start;
}

.eval-content-row :deep(.el-textarea) {
  flex: 1;
}

@media (max-width: 768px) {
  .booking-grid {
    grid-template-columns: 1fr;
  }
}
</style>
