<template>
  <div class="student-lectures">
    <div class="container">
      <div class="page-title">
        <h1>可预约讲座</h1>
        <p>按时间、主题与形式快速选择感兴趣的讲座</p>
      </div>

      <div class="page-layout">
        <!-- 侧边栏筛选（新设计稿：灰底圆角容器 + 分组） -->
        <div class="filter-side">
          <h3>讲座主题</h3>
          <button
            class="tag-btn"
            :class="{ active: !activeTheme }"
            @click="activeTheme = ''"
          >
            全部主题
          </button>
          <button
            v-for="tag in THEME_TAGS"
            :key="tag"
            class="tag-btn"
            :class="{ active: activeTheme === tag }"
            @click="activeTheme = tag"
          >
            {{ tag }}
          </button>

          <h3 class="filter-sub">形式分类</h3>
          <button
            v-for="tag in FORM_TAGS"
            :key="tag"
            class="tag-btn"
            :class="{ active: activeForm === tag }"
            @click="toggleForm(tag)"
          >
            {{ tag }}
          </button>
        </div>

        <!-- 右侧讲座卡片 -->
        <div class="main-content-right">
          <div class="lecture-grid">
            <div v-for="lecture in filteredLectures" :key="lecture.id" class="lecture-card">
              <div class="card-head">
                <router-link :to="`/student/lectures/${lecture.id}`" class="card-title">
                  {{ lecture.title }}
                </router-link>
                <span class="card-mark">✦</span>
              </div>
              <p>主讲人：{{ lecture.speaker }}</p>
              <p>时间：{{ formatTime(lecture.lectureTime) }}</p>
              <p>地点：{{ lecture.extendData?.locationName || '未设置' }}</p>
              <div class="card-bottom">
                <span>{{ remainText(lecture) }}</span>
                <button
                  class="btn-solid"
                  :class="{ booked: lecture.isRegistered }"
                  :disabled="lecture.isRegistered"
                  @click="handleRegister(lecture)"
                >
                  {{ lecture.isRegistered ? "已预约" : "立即预约" }}
                </button>
              </div>
            </div>
          </div>

          <p v-if="!loading && filteredLectures.length === 0" class="empty-tip">
            暂无符合条件的讲座
          </p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from "vue";
import { ElMessage } from "element-plus";
import { useUserStore } from "../../stores/user";
import { getStudentLecturePage } from "../../api/lecture";
import {
  checkRegistration,
  getLectureRegistrations,
  registerLecture,
} from "../../api/registration";

const userStore = useUserStore();

const loading = ref(false);
const lectures = ref([]);
const activeTheme = ref("");
const activeForm = ref("");

// 主题与形式标签（与设计稿一致）
const THEME_TAGS = ["科技前沿", "设计美学", "商业创新"];
const FORM_TAGS = ["线上直播", "线下教室"];

// 标签匹配：主题按分类名；形式按地点文案
function matchLecture(lecture) {
  if (activeTheme.value && lecture.categoryName !== activeTheme.value) {
    return false;
  }
  if (activeForm.value) {
    const loc = lecture.extendData?.locationName || "";
    const form = activeForm.value;
    const hit = form === "线上直播" ? /线上|直播/.test(loc) : /线下|教室/.test(loc);
    if (!hit) return false;
  }
  return true;
}

const filteredLectures = computed(() => lectures.value.filter(matchLecture));

function toggleForm(tag) {
  activeForm.value = activeForm.value === tag ? "" : tag;
}

// 加载讲座（全量）
async function loadLectures() {
  if (!userStore.userInfo) return;
  loading.value = true;
  try {
    const response = await getStudentLecturePage({ current: 1, size: 1000, keyword: "" });
    if (response.code === 200) {
      lectures.value = response.data.records || [];
      lastDataTimestamp.value = Date.now();

      await Promise.all(
        lectures.value.map(async (lecture) => {
          try {
            const checkRes = await checkRegistration(userStore.userInfo.id, lecture.id);
            if (checkRes.code === 200) lecture.isRegistered = checkRes.data;
            const regsRes = await getLectureRegistrations(lecture.id);
            if (regsRes.code === 200) {
              lecture.registrations = regsRes.data.filter((reg) => reg.status === 1) || [];
              lecture.registeredCount = lecture.registrations.length;
            }
          } catch (err) {
            console.error(`获取讲座 ${lecture.id} 信息失败:`, err);
          }
        }),
      );
    }
  } catch (error) {
    console.error("加载讲座数据失败:", error);
  } finally {
    loading.value = false;
  }
}

// 报名
async function handleRegister(lecture) {
  if (!userStore.userInfo) {
    ElMessage.warning("请先登录");
    return;
  }
  if (lecture.status !== 1) {
    ElMessage.warning("该讲座已经开始或已结束，不可以报名");
    return;
  }
  if ((lecture.registeredCount || 0) >= lecture.capacity) {
    ElMessage.warning("该讲座已满员");
    return;
  }
  try {
    const response = await registerLecture(userStore.userInfo.id, lecture.id);
    if (response.code === 200) {
      ElMessage.success("预约成功");
      lecture.isRegistered = true;
      refreshCount(lecture);
    }
  } catch (error) {
    console.error("报名失败:", error);
    ElMessage.error("报名失败");
  }
}

async function refreshCount(lecture) {
  try {
    const regsRes = await getLectureRegistrations(lecture.id);
    if (regsRes.code === 200) {
      lecture.registeredCount = (regsRes.data || []).filter((reg) => reg.status === 1).length;
    }
  } catch (e) {
    /* ignore */
  }
}

function remainText(lecture) {
  if (!lecture.capacity) return "剩余名额：—";
  const remain = lecture.capacity - (lecture.registeredCount || 0);
  if (remain <= 0) return "已满员";
  return `剩余名额：${remain}`;
}

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

// ---------- 静默轮询（30s） ----------
const lastDataTimestamp = ref(Date.now());
let autoRefreshTimer = null;
const AUTO_REFRESH_INTERVAL = 30000;

async function autoRefreshData() {
  try {
    const response = await getStudentLecturePage({
      current: 1,
      size: 1000,
      checkDataChange: true,
      lastUpdatedTimestamp: lastDataTimestamp.value,
    });
    if (response.code === 200) {
      const dataChanged =
        response.data.records.length > 0 &&
        response.data.records[0].extendData?.dataChanged;
      lastDataTimestamp.value = Date.now();
      if (dataChanged) {
        console.log("检测到数据变化，更新界面");
        loadLectures();
      }
    }
  } catch (error) {
    console.error("自动刷新讲座数据失败:", error);
  }
}

onMounted(() => {
  loadLectures();
  autoRefreshTimer = setInterval(autoRefreshData, AUTO_REFRESH_INTERVAL);
});

onUnmounted(() => {
  if (autoRefreshTimer) {
    clearInterval(autoRefreshTimer);
    autoRefreshTimer = null;
  }
});
</script>

<style scoped>
/* ============ 以下样式数值与 student-list.html 逐行一致 ============ */

.student-lectures {
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
  margin-bottom: 8px;
  color: #333;
}

.page-title p {
  color: #666;
  font-size: 16px;
}

/* 布局：侧边栏 + 列表 */
.page-layout {
  display: grid;
  grid-template-columns: 220px 1fr;
  gap: 40px;
}

.filter-side {
  background: #fafafa;
  padding: 24px;
  border-radius: 16px;
  border: 1px solid #f0f0f0;
  height: fit-content;
}

.filter-side h3 {
  font-size: 15px;
  margin-bottom: 16px;
  color: #333;
  font-weight: 600;
}

.filter-side .filter-sub {
  margin-top: 24px;
}

.tag-btn {
  display: block;
  width: 100%;
  padding: 10px 16px;
  margin-bottom: 8px;
  border: 1px solid #e0e0e0;
  border-radius: 20px;
  background: #fff;
  color: #555;
  cursor: pointer;
  font-size: 14px;
  font-family: inherit;
  text-align: left;
  transition: all 0.2s;
}

.tag-btn:hover,
.tag-btn.active {
  background: #000;
  color: #fff;
  border-color: #000;
}

/* 讲座卡片网格 */
.lecture-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 30px;
}

.lecture-card {
  background-color: #ffffff;
  border-radius: 16px;
  padding: 30px;
  display: flex;
  flex-direction: column;
  border: 1px solid #f0f0f0;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.02);
  transition: transform 0.3s ease, box-shadow 0.3s ease;
}

.lecture-card:hover {
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

.card-mark {
  color: #ccc;
  font-size: 16px;
}

.lecture-card p {
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

.card-bottom span {
  font-size: 13px;
  color: #888;
}

.btn-solid {
  padding: 8px 24px;
  background-color: #000;
  color: #fff;
  border: none;
  border-radius: 20px;
  font-size: 13px;
  cursor: pointer;
  font-family: inherit;
  transition: background 0.2s;
}

.btn-solid:hover {
  background-color: #333;
}

.btn-solid.booked {
  background-color: #555;
  cursor: default;
}

.btn-solid.booked:hover {
  background-color: #555;
}

.empty-tip {
  text-align: center;
  color: #999;
  font-size: 15px;
  padding: 60px 0;
}

/* 响应式 */
@media (max-width: 768px) {
  .page-layout {
    grid-template-columns: 1fr;
  }

  .lecture-grid {
    grid-template-columns: 1fr;
  }
}
</style>
