<template>
  <div class="adm-category">
    <div class="adm-title-row">
      <h1 class="adm-page-h1">讲座分类管理</h1>
    </div>

    <div class="adm-search-row">
      <button class="adm-btn adm-btn-primary" disabled title="后端暂未提供分类新增接口">
        + 新增分类
      </button>
      <span class="hint">（分类新增/编辑接口后端暂未提供，当前为只读视图）</span>
    </div>

    <div class="adm-table-block">
      <table class="adm-table">
        <thead>
          <tr>
            <th>分类编号</th>
            <th>分类名称</th>
            <th>讲座数量</th>
            <th>排序权重</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="cat in categories" :key="cat.id">
            <td>{{ cat.id }}</td>
            <td>{{ cat.categoryName }}</td>
            <td>{{ lectureCounts[cat.id] || 0 }}</td>
            <td>-</td>
            <td>
              <button class="adm-btn" disabled title="后端暂未提供分类编辑接口">编辑</button>
            </td>
          </tr>
        </tbody>
      </table>
      <p v-if="!loading && categories.length === 0" class="adm-empty">暂无分类数据</p>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import { getAllCategories } from "../../api/category";
import { getLecturePage } from "../../api/lecture";

const loading = ref(false);
const categories = ref([]);
const lectureCounts = ref({});

async function loadData() {
  loading.value = true;
  try {
    const catRes = await getAllCategories();
    if (catRes.code === 200) categories.value = catRes.data || [];

    // 统计每个分类下的讲座数量
    const lecRes = await getLecturePage({ current: 1, size: 500 });
    if (lecRes.code === 200) {
      const counts = {};
      (lecRes.data.records || []).forEach((l) => {
        if (l.categoryId) counts[l.categoryId] = (counts[l.categoryId] || 0) + 1;
      });
      lectureCounts.value = counts;
    }
  } catch (error) {
    console.error("加载分类数据失败:", error);
    ElMessage.error("加载分类数据失败");
  } finally {
    loading.value = false;
  }
}

onMounted(loadData);
</script>

<style scoped>
.hint {
  font-size: 13px;
  color: #999;
}
</style>
