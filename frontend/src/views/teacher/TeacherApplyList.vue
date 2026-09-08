<template>
  <div class="teacher-apply-list">
    <div class="container">
      <div class="page-head">
        <h1>学生预约与签到管理</h1>
      </div>

      <div class="select-box">
        <select v-model="selectedId" class="lecture-select">
          <option v-for="lecture in lectures" :key="lecture.id" :value="lecture.id">
            {{ lecture.title }}（{{ formatFull(lecture.lectureTime) }}）
          </option>
        </select>
      </div>

      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>序号</th>
              <th>姓名</th>
              <th>学号</th>
              <th>班级</th>
              <th>预约时间</th>
              <th>签到状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(reg, index) in registrations" :key="reg.id">
              <td>{{ index + 1 }}</td>
              <td>{{ reg.realName || '-' }}</td>
              <td>{{ reg.studentTeacherId || '-' }}</td>
              <td>{{ reg.departmentName || '-' }}</td>
              <td>{{ formatFull(reg.registerTime) }}</td>
              <td>
                <span v-if="reg.checkinStatus === 1" class="signed">已签到</span>
                <span v-else class="no-sign">未签到</span>
              </td>
              <td>
                <button
                  v-if="reg.checkinStatus === 1"
                  class="btn-outline"
                  @click="handleCheckin(reg, false)"
                >
                  取消签到
                </button>
                <button v-else class="btn-sign" @click="handleCheckin(reg, true)">
                  标记签到
                </button>
              </td>
            </tr>
          </tbody>
        </table>
        <p v-if="!loading && registrations.length === 0" class="empty-tip">
          该讲座暂无预约学生
        </p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { ElMessage } from "element-plus";
import { useUserStore } from "../../stores/user";
import { getLecturePage } from "../../api/lecture";
import {
  getLectureRegistrations,
  checkinRegistration,
  cancelCheckin,
} from "../../api/registration";

const route = useRoute();
const userStore = useUserStore();

const lectures = ref([]);
const selectedId = ref(null);
const registrations = ref([]);
const loading = ref(false);

// 加载我的讲座（下拉选项）
async function loadLectures() {
  try {
    const response = await getLecturePage({
      current: 1,
      size: 100,
      organizerId: userStore.userInfo?.id,
    });
    if (response.code === 200) {
      lectures.value = response.data.records || [];
      const queryId = route.query.lectureId;
      if (queryId && lectures.value.some((l) => String(l.id) === String(queryId))) {
        selectedId.value = Number(queryId);
      } else if (lectures.value.length > 0) {
        selectedId.value = lectures.value[0].id;
      }
    }
  } catch (error) {
    console.error("加载讲座失败:", error);
    ElMessage.error("加载讲座失败");
  }
}

// 加载选中讲座的预约名单
async function loadRegistrations(lectureId) {
  if (!lectureId) {
    registrations.value = [];
    return;
  }
  loading.value = true;
  try {
    const res = await getLectureRegistrations(lectureId);
    if (res.code === 200) {
      registrations.value = (res.data || []).filter((reg) => reg.status === 1);
    }
  } catch (error) {
    console.error("加载预约名单失败:", error);
    ElMessage.error("加载预约名单失败");
  } finally {
    loading.value = false;
  }
}

watch(selectedId, (val) => loadRegistrations(val));

// 签到 / 取消签到
async function handleCheckin(reg, doCheckin) {
  const action = doCheckin ? checkinRegistration : cancelCheckin;
  const successMsg = doCheckin ? "签到成功" : "已取消签到";
  try {
    const res = await action(reg.id);
    if (res.code === 200) {
      ElMessage.success(successMsg);
      reg.checkinStatus = doCheckin ? 1 : 0;
    } else {
      ElMessage.error(res.message || "操作失败");
    }
  } catch (error) {
    console.error("签到操作失败:", error);
    ElMessage.error("操作失败，请重试");
  }
}

function formatFull(dateString) {
  if (!dateString) return "时间待定";
  const date = new Date(dateString);
  const pad = (n) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

onMounted(loadLectures);
</script>

<style scoped>
/* ============ 黑白极简风格（与学生端统一） ============ */

.teacher-apply-list {
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

.select-box {
  margin-bottom: 20px;
}

.lecture-select {
  padding: 10px 14px;
  min-width: 320px;
  border-radius: 10px;
  border: 1px solid #ddd;
  font-size: 15px;
  font-family: inherit;
  background: #fff;
  color: #333;
  outline: none;
}

.lecture-select:focus {
  border-color: #000;
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

.signed {
  color: #00b42a;
  font-weight: 500;
}

.no-sign {
  color: #999;
}

.btn-sign {
  padding: 6px 14px;
  background: #000;
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 13px;
  cursor: pointer;
  font-family: inherit;
  transition: background 0.2s;
}

.btn-sign:hover {
  background: #333;
}

.btn-outline {
  padding: 6px 14px;
  border: 1px solid #e0e0e0;
  color: #666;
  background: #fff;
  border-radius: 8px;
  font-size: 13px;
  cursor: pointer;
  font-family: inherit;
  transition: all 0.2s;
}

.btn-outline:hover {
  border-color: #ff4d4f;
  color: #ff4d4f;
}

.empty-tip {
  text-align: center;
  color: #999;
  font-size: 15px;
  padding: 60px 0;
}
</style>
