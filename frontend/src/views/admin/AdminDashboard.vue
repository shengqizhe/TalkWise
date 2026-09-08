<template>
  <div class="adm-dashboard">
    <div class="adm-title-row">
      <h1 class="adm-page-h1">平台概览</h1>
    </div>

    <!-- 统计卡（admin.html stat-row ×4） -->
    <div class="stat-row">
      <div class="stat-card">
        <div>
          <div class="stat-label">总讲座</div>
          <div class="num">{{ totalLectures }}</div>
        </div>
        <span class="stat-mark">📚</span>
      </div>
      <div class="stat-card">
        <div>
          <div class="stat-label">活跃用户</div>
          <div class="num">{{ totalUsers }}</div>
        </div>
        <span class="stat-mark">👥</span>
      </div>
      <div class="stat-card">
        <div>
          <div class="stat-label">待审核</div>
          <div class="num">{{ pendingCount }}</div>
        </div>
        <span class="stat-mark">📝</span>
      </div>
      <div class="stat-card">
        <div>
          <div class="stat-label">已发布讲座</div>
          <div class="num">{{ publishedCount }}</div>
        </div>
        <span class="stat-mark">📢</span>
      </div>
    </div>

    <!-- 待审核列表 + 系统通知（admin.html content-row） -->
    <div class="content-row">
      <div class="adm-table-block">
        <table class="adm-table">
          <thead>
            <tr>
              <th>讲座名称</th>
              <th>讲师</th>
              <th>讲座时间</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="lecture in pendingLectures" :key="lecture.id">
              <td>{{ lecture.title }}</td>
              <td>{{ lecture.speaker || '-' }}</td>
              <td>{{ formatFull(lecture.lectureTime) }}</td>
              <td><span class="adm-tag-orange">待审核</span></td>
              <td>
                <button class="adm-btn adm-btn-primary" @click="approve(lecture)">通过</button>
                <button class="adm-btn adm-btn-red" @click="reject(lecture)">驳回</button>
              </td>
            </tr>
          </tbody>
        </table>
        <p v-if="!loading && pendingLectures.length === 0" class="adm-empty">
          暂无待审核讲座
        </p>
      </div>

      <div class="notify-card">
        <div class="notify-head">
          <h3>系统通知</h3>
          <span>🔔</span>
        </div>
        <p class="notify-tip">
          实时消息请查看右上角铃铛。<br />
          后台通知列表功能待接入消息服务后开放。
        </p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { getLecturePage, updateLecturePublishStatus, cancelLecture } from "../../api/lecture";
import { fetchStudentUsers } from "../../api/studentUser";
import { fetchTeacherUsers } from "../../api/teacherUser";

const loading = ref(false);
const lectures = ref([]);
const studentTotal = ref(0);
const teacherTotal = ref(0);
const totalLectures = ref(0);

const totalUsers = computed(() => studentTotal.value + teacherTotal.value);
const pendingCount = computed(
  () => lectures.value.filter((l) => l.publishStatus === 0 && l.status !== 3 && l.status !== 4).length,
);
const publishedCount = computed(() => lectures.value.filter((l) => l.publishStatus === 1).length);

const pendingLectures = computed(() =>
  lectures.value
    .filter((l) => l.publishStatus === 0 && l.status !== 3 && l.status !== 4)
    .slice(0, 8),
);

async function loadData() {
  loading.value = true;
  try {
    const res = await getLecturePage({ current: 1, size: 500 });
    if (res.code === 200) {
      lectures.value = res.data.records || [];
      totalLectures.value = res.data.total || 0;
    }
    const stu = await fetchStudentUsers({ current: 1, size: 1 });
    if (stu.code === 200) studentTotal.value = stu.data.total || 0;
    const tea = await fetchTeacherUsers({ current: 1, size: 1 });
    if (tea.code === 200) teacherTotal.value = tea.data.total || 0;
  } catch (error) {
    console.error("加载数据失败:", error);
    ElMessage.error("加载数据失败");
  } finally {
    loading.value = false;
  }
}

// 通过 = 发布
async function approve(lecture) {
  try {
    const res = await updateLecturePublishStatus(lecture.id, 1);
    if (res.code === 200) {
      ElMessage.success(`已通过《${lecture.title}》`);
      loadData();
    }
  } catch (e) {
    ElMessage.error("操作失败");
  }
}

// 驳回 = 删除
async function reject(lecture) {
  try {
    await ElMessageBox.confirm(`确定驳回并删除《${lecture.title}》吗？`, "驳回确认", {
      type: "warning",
    });
  } catch {
    return;
  }
  try {
    const res = await cancelLecture(lecture.id, "管理员驳回");
    if (res.code === 200) {
      ElMessage.success("已驳回");
      loadData();
    }
  } catch (e) {
    ElMessage.error("操作失败");
  }
}

function formatFull(dateString) {
  if (!dateString) return "-";
  const date = new Date(dateString);
  const pad = (n) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

onMounted(loadData);
</script>

<style scoped>
.stat-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  margin-bottom: 24px;
}

.stat-card {
  background: #fff;
  padding: 24px;
  border-radius: 16px;
  border: 1px solid #f0f0f0;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.02);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.stat-label {
  font-size: 14px;
  color: #777;
  margin-bottom: 6px;
}

.stat-card .num {
  font-size: 36px;
  font-weight: 800;
  color: #000;
}

.stat-mark {
  font-size: 22px;
}

.content-row {
  display: grid;
  grid-template-columns: 1fr 300px;
  gap: 24px;
  align-items: start;
}

.notify-card {
  background: #fff;
  padding: 24px;
  border-radius: 16px;
  border: 1px solid #f0f0f0;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.02);
}

.notify-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.notify-head h3 {
  font-size: 16px;
  font-weight: 600;
  color: #333;
  margin: 0;
}

.notify-tip {
  font-size: 13px;
  color: #999;
  line-height: 1.8;
  margin: 0;
}

@media (max-width: 1100px) {
  .content-row {
    grid-template-columns: 1fr;
  }

  .stat-row {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
