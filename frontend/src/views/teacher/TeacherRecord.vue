<template>
  <div class="teacher-record">
    <div class="container">
      <div class="page-head">
        <h1>已完结讲座归档</h1>
      </div>

      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>讲座名称</th>
              <th>举办时间</th>
              <th>地点</th>
              <th>预约人数</th>
              <th>实际签到</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="lecture in finishedLectures" :key="lecture.id">
              <td class="name-cell">{{ lecture.title }}</td>
              <td>{{ formatFull(lecture.lectureTime) }}</td>
              <td>{{ locationName(lecture.locationId) }}</td>
              <td>{{ lecture.registrations?.length || 0 }}</td>
              <td>
                {{
                  (lecture.registrations || []).filter((r) => r.checkinStatus === 1)
                    .length
                }}
              </td>
              <td>
                <button class="btn-detail" @click="viewDetail(lecture)">查看详情</button>
              </td>
            </tr>
          </tbody>
        </table>
        <p v-if="!loading && finishedLectures.length === 0" class="empty-tip">
          暂无已完结的讲座
        </p>
      </div>
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
import { getAllLocations } from "../../api/location";

const router = useRouter();
const userStore = useUserStore();

const myLectures = ref([]);
const locations = ref([]);
const loading = ref(false);

// 已完结（状态 3）
const finishedLectures = computed(() =>
  myLectures.value
    .filter((l) => l.status === 3)
    .sort((a, b) => new Date(b.lectureTime) - new Date(a.lectureTime)),
);

async function loadData() {
  loading.value = true;
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
    const locRes = await getAllLocations();
    if (locRes.code === 200) locations.value = locRes.data || [];
  } catch (error) {
    console.error("加载数据失败:", error);
    ElMessage.error("加载数据失败");
  } finally {
    loading.value = false;
  }
}

function locationName(id) {
  const loc = locations.value.find((l) => l.id === id);
  return loc ? loc.name : "未设置";
}

function formatFull(dateString) {
  if (!dateString) return "时间待定";
  const date = new Date(dateString);
  const pad = (n) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function viewDetail(lecture) {
  router.push({ path: "/teacher/apply-list", query: { lectureId: lecture.id } });
}

onMounted(loadData);
</script>

<style scoped>
/* ============ 黑白极简风格（与学生端统一） ============ */

.teacher-record {
  font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
  line-height: 1.6;
  color: #333;
}

.container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 40px 20px;
}

.page-head h1 {
  font-size: 36px;
  font-weight: 800;
  margin-bottom: 24px;
  color: #333;
}

.table-wrap {
  background: #fff;
  border-radius: 16px;
  border: 1px solid #f0f0f0;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.02);
  overflow: hidden;
}

table {
  width: 100%;
  border-collapse: collapse;
}

th,
td {
  padding: 16px 20px;
  text-align: left;
  border-bottom: 1px solid #f0f0f0;
  font-size: 14px;
  color: #444;
}

th {
  background: #fafafa;
  color: #666;
  font-weight: 500;
}

tbody tr:last-child td {
  border-bottom: none;
}

.name-cell {
  font-weight: 600;
  color: #333;
}

.btn-detail {
  padding: 6px 14px;
  border: 1px solid #e0e0e0;
  color: #555;
  background: #fff;
  border-radius: 8px;
  font-size: 13px;
  cursor: pointer;
  font-family: inherit;
  transition: all 0.2s;
}

.btn-detail:hover {
  border-color: #000;
  color: #000;
}

.empty-tip {
  text-align: center;
  color: #999;
  font-size: 15px;
  padding: 60px 0;
}
</style>
