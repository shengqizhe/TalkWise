<template>
  <div class="teacher-dashboard">
    <div class="container">
      <!-- 欢迎横幅（teacher.html banner-card） -->
      <div class="banner-card">
        <h1>欢迎回来，{{ userStore.userInfo?.realName || userStore.userInfo?.username }}老师</h1>
        <p>在这里管理讲座发布、查看学生预约与签到情况</p>
      </div>

      <!-- 统计卡片（teacher.html stat-row ×4） -->
      <div class="stat-row">
        <div class="stat-card">
          <h2>{{ ongoingCount }}</h2>
          <span>进行中讲座</span>
        </div>
        <div class="stat-card">
          <h2>{{ totalRegistrations }}</h2>
          <span>累计预约人数</span>
        </div>
        <div class="stat-card">
          <h2>{{ finishedCount }}</h2>
          <span>已完结讲座</span>
        </div>
        <div class="stat-card">
          <h2>{{ totalCheckins }}</h2>
          <span>签到完成</span>
        </div>
      </div>

      <!-- 近期待开展讲座（teacher.html section + lecture-grid ×3） -->
      <h2 class="section-title">近期待开展讲座</h2>
      <div v-if="upcomingLectures.length" class="lecture-grid">
        <div v-for="lecture in upcomingLectures" :key="lecture.id" class="lecture-card">
          <h3>{{ lecture.title }}</h3>
          <p>时间：{{ formatFull(lecture.lectureTime) }}</p>
          <p>预约人数：{{ lecture.registrations?.length || 0 }}人</p>
          <button class="btn-solid" @click="viewApply(lecture)">查看报名</button>
        </div>
      </div>
      <p v-else class="empty-tip">暂无待开展的讲座，去"我的讲座"发布一场吧</p>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { useUserStore } from "../../stores/user";
import { getLecturePage } from "../../api/lecture";
import { getLectureRegistrations } from "../../api/registration";

const router = useRouter();
const userStore = useUserStore();
const myLectures = ref([]);

// ---------- 统计（本地计算，对应稿子四卡） ----------
const ongoingCount = computed(
  () => myLectures.value.filter((l) => l.status === 2).length,
);
const finishedCount = computed(
  () => myLectures.value.filter((l) => l.status === 3).length,
);
const allRegistrations = computed(() =>
  myLectures.value.flatMap((l) => l.registrations || []),
);
const totalRegistrations = computed(() => allRegistrations.value.length);
const totalCheckins = computed(
  () => allRegistrations.value.filter((r) => r.checkinStatus === 1).length,
);

// 近期待开展：未开始状态，按时间升序前 3
const upcomingLectures = computed(() =>
  myLectures.value
    .filter((l) => l.status === 1)
    .sort((a, b) => new Date(a.lectureTime) - new Date(b.lectureTime))
    .slice(0, 3),
);

// ---------- 数据加载 ----------
async function loadMyLectures() {
  try {
    const response = await getLecturePage({
      current: 1,
      size: 100,
      organizerId: userStore.userInfo?.id,
    });
    if (response.code === 200) {
      myLectures.value = response.data.records || [];
      await Promise.all(
        myLectures.value.map(async (lecture) => {
          try {
            const regsRes = await getLectureRegistrations(lecture.id);
            if (regsRes.code === 200) {
              lecture.registrations =
                regsRes.data.filter((reg) => reg.status === 1) || [];
            }
          } catch (err) {
            /* ignore */
          }
        }),
      );
    }
  } catch (error) {
    console.error("加载讲座数据失败:", error);
    ElMessage.error("加载讲座数据失败");
  }
}

// ---------- 工具 ----------
function formatFull(dateString) {
  if (!dateString) return "时间待定";
  const date = new Date(dateString);
  const pad = (n) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function viewApply(lecture) {
  router.push({ path: "/teacher/apply-list", query: { lectureId: lecture.id } });
}

onMounted(loadMyLectures);
</script>

<style scoped>
/* ============ 黑白极简风格（与学生端统一） ============ */

.teacher-dashboard {
  font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
  line-height: 1.6;
  color: #333;
}

.container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 40px 20px;
}

/* 欢迎横幅 */
.banner-card {
  background: #fff;
  padding: 40px 32px;
  border-radius: 16px;
  border: 1px solid #f0f0f0;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.02);
  margin-bottom: 32px;
}

.banner-card h1 {
  font-size: 32px;
  font-weight: 800;
  margin-bottom: 12px;
  color: #333;
}

.banner-card p {
  color: #666;
  font-size: 16px;
  margin: 0;
}

/* 统计卡 */
.stat-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  margin: 32px 0;
}

.stat-card {
  background: #fff;
  padding: 24px;
  border-radius: 16px;
  border: 1px solid #f0f0f0;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.02);
  text-align: center;
}

.stat-card h2 {
  font-size: 36px;
  font-weight: 800;
  color: #000;
  margin-bottom: 8px;
  margin-top: 0;
}

.stat-card span {
  color: #666;
  font-size: 14px;
}

/* 近期讲座 */
.section-title {
  font-size: 22px;
  font-weight: 600;
  margin: 36px 0 20px;
  color: #333;
}

.lecture-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 24px;
}

.lecture-card {
  background: #fff;
  padding: 24px;
  border-radius: 16px;
  border: 1px solid #f0f0f0;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.02);
  transition: transform 0.3s ease, box-shadow 0.3s ease;
}

.lecture-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.05);
}

.lecture-card h3 {
  font-size: 20px;
  font-weight: bold;
  margin-bottom: 12px;
  color: #333;
  margin-top: 0;
}

.lecture-card p {
  font-size: 14px;
  color: #444;
  margin: 6px 0;
}

.btn-solid {
  margin-top: 16px;
  padding: 9px 22px;
  background: #000;
  color: #fff;
  border: none;
  border-radius: 20px;
  cursor: pointer;
  font-size: 13px;
  font-family: inherit;
  transition: background 0.2s;
}

.btn-solid:hover {
  background: #333;
}

.empty-tip {
  text-align: center;
  color: #999;
  font-size: 15px;
  padding: 40px 0;
}

/* 响应式 */
@media (max-width: 900px) {
  .stat-row {
    grid-template-columns: 1fr 1fr;
  }

  .lecture-grid {
    grid-template-columns: 1fr;
  }
}
</style>
