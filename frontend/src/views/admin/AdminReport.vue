<template>
  <div class="adm-report">
    <div class="adm-title-row">
      <h1 class="adm-page-h1">预约数据报表</h1>
      <button class="adm-btn adm-btn-primary" @click="exportCsv">导出Excel报表</button>
    </div>

    <div class="adm-search-row">
      <span class="hint">展示最近 {{ rows.length }} 场讲座的预约与签到统计（按讲座时间倒序）</span>
    </div>

    <div class="adm-table-block">
      <table class="adm-table">
        <thead>
          <tr>
            <th>讲座名称</th>
            <th>预约人数</th>
            <th>签到人数</th>
            <th>取消预约</th>
            <th>统计周期</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in rows" :key="row.id">
            <td>{{ row.title }}</td>
            <td>{{ row.applied }}</td>
            <td>{{ row.checked }}</td>
            <td>{{ row.cancelled }}</td>
            <td>{{ row.period }}</td>
          </tr>
        </tbody>
      </table>
      <p v-if="!loading && rows.length === 0" class="adm-empty">暂无讲座数据</p>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import { getLecturePage } from "../../api/lecture";
import { getLectureRegistrations } from "../../api/registration";

const loading = ref(false);
const rows = ref([]);

async function loadData() {
  loading.value = true;
  try {
    const res = await getLecturePage({ current: 1, size: 500 });
    if (res.code !== 200) return;

    const lectures = (res.data.records || [])
      .filter((l) => l.lectureTime)
      .sort((a, b) => new Date(b.lectureTime) - new Date(a.lectureTime))
      .slice(0, 15);

    const result = [];
    for (const lecture of lectures) {
      try {
        const regRes = await getLectureRegistrations(lecture.id);
        const all = regRes.code === 200 ? regRes.data || [] : [];
        const applied = all.filter((r) => r.status === 1);
        const checked = applied.filter((r) => r.checkinStatus === 1).length;
        const cancelled = all.length - applied.length;
        result.push({
          id: lecture.id,
          title: lecture.title,
          applied: applied.length,
          checked,
          cancelled: cancelled < 0 ? 0 : cancelled,
          period: periodText(lecture.lectureTime),
        });
      } catch (e) {
        /* ignore */
      }
    }
    rows.value = result;
  } catch (error) {
    console.error("加载报表数据失败:", error);
    ElMessage.error("加载报表数据失败");
  } finally {
    loading.value = false;
  }
}

function periodText(dateString) {
  if (!dateString) return "-";
  const date = new Date(dateString);
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}`;
}

// 导出当前报表为 CSV
function exportCsv() {
  if (rows.value.length === 0) {
    ElMessage.warning("暂无数据可导出");
    return;
  }
  const header = ["讲座名称", "预约人数", "签到人数", "取消预约", "统计周期"];
  const lines = rows.value.map((r) =>
    [r.title, r.applied, r.checked, r.cancelled, r.period]
      .map((v) => `"${String(v).replace(/"/g, '""')}"`)
      .join(","),
  );
  const csv = "\uFEFF" + [header.join(","), ...lines].join("\n");
  const blob = new Blob([csv], { type: "text/csv;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = `讲座预约报表_${new Date().toISOString().slice(0, 10)}.csv`;
  a.click();
  URL.revokeObjectURL(url);
  ElMessage.success("报表已导出");
}

onMounted(loadData);
</script>

<style scoped>
.hint {
  font-size: 13px;
  color: #999;
}
</style>
