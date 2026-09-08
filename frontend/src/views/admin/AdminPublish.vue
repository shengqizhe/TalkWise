<template>
  <div class="adm-publish">
    <div class="adm-title-row">
      <h1 class="adm-page-h1">讲座发布管理</h1>
    </div>

    <div class="adm-search-row">
      <input v-model="keyword" class="adm-input" placeholder="搜索讲座名称" />
      <select v-model="statusFilter" class="adm-select">
        <option value="">全部发布状态</option>
        <option value="published">已发布</option>
        <option value="draft">未发布</option>
        <option value="ended">已结束/取消</option>
      </select>
      <button class="adm-btn adm-btn-primary" @click="refresh">查询</button>
    </div>

    <div class="adm-table-block">
      <table class="adm-table">
        <thead>
          <tr>
            <th>讲座标题</th>
            <th>讲师</th>
            <th>讲座时间</th>
            <th>发布状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="lecture in filteredLectures" :key="lecture.id">
            <td>{{ lecture.title }}</td>
            <td>{{ lecture.speaker || '-' }}</td>
            <td>{{ formatFull(lecture.lectureTime) }}</td>
            <td>
              <span v-if="lecture.publishStatus === 1 && lecture.status !== 3 && lecture.status !== 4" class="adm-tag-green">已发布</span>
              <span v-else-if="lecture.publishStatus === 0 && lecture.status !== 3 && lecture.status !== 4" class="adm-tag-orange">未发布</span>
              <span v-else class="adm-tag-gray">已结束/已取消</span>
            </td>
            <td>
              <button
                v-if="lecture.publishStatus === 1 && lecture.status !== 3 && lecture.status !== 4"
                class="adm-btn"
                @click="togglePublish(lecture, 0)"
              >
                下架
              </button>
              <button
                v-else-if="canPublish(lecture)"
                class="adm-btn adm-btn-primary"
                @click="togglePublish(lecture, 1)"
              >
                立即发布
              </button>
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
import { ElMessage } from "element-plus";
import { getLecturePage, updateLecturePublishStatus } from "../../api/lecture";

const loading = ref(false);
const lectures = ref([]);
const keyword = ref("");
const statusFilter = ref("");

function canPublish(l) {
  return (
    l.publishStatus === 0 &&
    l.status !== 3 &&
    l.status !== 4 &&
    new Date(l.lectureTime) > new Date()
  );
}

const filteredLectures = computed(() => {
  let list = lectures.value;
  if (statusFilter.value === "published") {
    list = list.filter((l) => l.publishStatus === 1 && l.status !== 3 && l.status !== 4);
  } else if (statusFilter.value === "draft") {
    list = list.filter((l) => l.publishStatus === 0 && l.status !== 3 && l.status !== 4);
  } else if (statusFilter.value === "ended") {
    list = list.filter((l) => l.status === 3 || l.status === 4);
  }
  if (keyword.value.trim()) {
    list = list.filter((l) => (l.title || "").includes(keyword.value.trim()));
  }
  return list;
});

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

function refresh() {
  loadData();
}

async function togglePublish(lecture, publishStatus) {
  try {
    const res = await updateLecturePublishStatus(lecture.id, publishStatus);
    if (res.code === 200) {
      ElMessage.success(publishStatus === 1 ? "已发布" : "已下架");
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
