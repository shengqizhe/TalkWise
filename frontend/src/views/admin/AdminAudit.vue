<template>
  <div class="adm-audit">
    <div class="adm-title-row">
      <h1 class="adm-page-h1">讲座审核队列</h1>
    </div>

    <div class="adm-search-row">
      <input v-model="keyword" class="adm-input" placeholder="搜索讲座名称" @keyup.enter="applyFilter" />
      <select v-model="statusFilter" class="adm-select">
        <option value="">全部状态</option>
        <option value="pending">待审核</option>
        <option value="passed">审核通过</option>
      </select>
      <button class="adm-btn adm-btn-primary" @click="applyFilter">查询</button>
    </div>

    <div class="adm-table-block">
      <table class="adm-table">
        <thead>
          <tr>
            <th>讲座标题</th>
            <th>讲师</th>
            <th>时间地点</th>
            <th>讲座时间</th>
            <th>审核状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="lecture in filteredLectures" :key="lecture.id">
            <td>{{ lecture.title }}</td>
            <td>{{ lecture.speaker || '-' }}</td>
            <td>{{ lecture.extendData?.locationName || '未设置' }}</td>
            <td>{{ formatFull(lecture.lectureTime) }}</td>
            <td>
              <span v-if="isPending(lecture)" class="adm-tag-orange">待审核</span>
              <span v-else-if="lecture.publishStatus === 1" class="adm-tag-green">审核通过</span>
              <span v-else class="adm-tag-gray">已驳回/过期</span>
            </td>
            <td>
              <template v-if="isPending(lecture)">
                <button class="adm-btn adm-btn-primary" @click="approve(lecture)">通过</button>
                <button class="adm-btn adm-btn-red" @click="reject(lecture)">驳回</button>
              </template>
              <span v-else class="adm-tag-gray">-</span>
            </td>
          </tr>
        </tbody>
      </table>
      <p v-if="!loading && filteredLectures.length === 0" class="adm-empty">暂无符合条件的讲座</p>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { getLecturePage, updateLecturePublishStatus, cancelLecture } from "../../api/lecture";

const loading = ref(false);
const lectures = ref([]);
const keyword = ref("");
const statusFilter = ref("");

function isPending(l) {
  return l.publishStatus === 0 && l.status !== 3 && l.status !== 4;
}

const filteredLectures = computed(() => {
  let list = lectures.value;
  if (statusFilter.value === "pending") list = list.filter(isPending);
  else if (statusFilter.value === "passed") list = list.filter((l) => l.publishStatus === 1);
  if (keyword.value.trim()) {
    list = list.filter((l) => (l.title || "").includes(keyword.value.trim()));
  }
  return list;
});

function applyFilter() {
  // 前端过滤，保持分页状态一致
}

async function loadData() {
  loading.value = true;
  try {
    const res = await getLecturePage({ current: 1, size: 500 });
    if (res.code === 200) lectures.value = res.data.records || [];
  } catch (error) {
    console.error("加载数据失败:", error);
    ElMessage.error("加载数据失败");
  } finally {
    loading.value = false;
  }
}

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
