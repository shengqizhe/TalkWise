<template>
  <div class="teacher-evaluations">
    <div class="container">
      <!-- 页头 + 筛选 -->
      <div class="page-head">
        <h1>评价反馈</h1>
        <div class="head-actions">
          <select v-model="lectureFilter" class="adm-select" @change="handleFilterChange">
            <option value="">全部讲座</option>
            <option v-for="lecture in lectures" :key="lecture.id" :value="lecture.id">
              {{ lecture.title }}
            </option>
          </select>
          <select v-model="ratingFilter" class="adm-select" @change="handleFilterChange">
            <option value="">评分排序</option>
            <option value="asc">评分由低到高</option>
            <option value="desc">评分由高到低</option>
          </select>
        </div>
      </div>

      <!-- 统计卡 -->
      <div class="stat-row">
        <div class="stat-card">
          <div class="stat-number">{{ stats.totalEvaluations }}</div>
          <div class="stat-label">总评价数</div>
        </div>
        <div class="stat-card">
          <div class="stat-number">{{ stats.avgRating }}</div>
          <div class="stat-label">平均评分</div>
        </div>
        <div class="stat-card">
          <div class="stat-number">{{ stats.recentEvaluations }}</div>
          <div class="stat-label">最近评价</div>
        </div>
      </div>

      <!-- 评价表格 -->
      <div class="adm-table-block">
        <table class="adm-table">
          <thead>
            <tr>
              <th>讲座标题</th>
              <th>学生姓名</th>
              <th>评分</th>
              <th>评价内容</th>
              <th>评价时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in evaluations" :key="row.id">
              <td>{{ row.lectureTitle }}</td>
              <td>{{ row.userName }}</td>
              <td><span class="score-text">{{ row.score }} / 5</span></td>
              <td class="content-cell">{{ row.content }}</td>
              <td>{{ formatDate(row.createdTime) }}</td>
              <td>
                <button class="adm-btn" @click="viewEvaluation(row)">查看详情</button>
              </td>
            </tr>
          </tbody>
        </table>
        <p v-if="!loading && evaluations.length === 0" class="adm-empty">
          暂无评价数据
        </p>

        <!-- 分页 -->
        <div v-if="total > 0" class="pagination-container">
          <el-pagination
            v-model:current-page="currentPage"
            v-model:page-size="pageSize"
            :page-sizes="[10, 20, 30, 50]"
            layout="total, sizes, prev, pager, next, jumper"
            :total="total"
            @size-change="handleSizeChange"
            @current-change="handleCurrentChange"
            background
          />
        </div>
      </div>
    </div>

    <!-- 评价详情对话框 -->
    <el-dialog v-model="evaluationDialogVisible" title="评价详情" width="600px">
      <div v-if="selectedEvaluation" class="evaluation-details">
        <div class="detail-item">
          <label>讲座标题：</label>
          <span>{{ selectedEvaluation.lectureTitle }}</span>
        </div>
        <div class="detail-item">
          <label>学生姓名：</label>
          <span>{{ selectedEvaluation.userName }}</span>
        </div>
        <div class="detail-item">
          <label>评分：</label>
          <span>{{ selectedEvaluation.score }} / 5</span>
        </div>
        <div class="detail-item">
          <label>评价时间：</label>
          <span>{{ formatDate(selectedEvaluation.createdTime) }}</span>
        </div>
        <div class="detail-item">
          <label>评价内容：</label>
          <div class="content-box">
            {{ selectedEvaluation.content }}
          </div>
        </div>
        <div class="detail-item" v-if="selectedEvaluation.improvementSuggestion">
          <label>改进建议：</label>
          <div class="content-box suggestion">
            {{ selectedEvaluation.improvementSuggestion }}
          </div>
        </div>
      </div>

      <template #footer>
        <el-button @click="evaluationDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from "vue";
import { useRoute } from "vue-router";
import { ElMessage } from "element-plus";
import { useUserStore } from "../../stores/user";
import { getEvaluationPage } from "../../api/evaluation";
import { getLecturePage } from "../../api/lecture";

const route = useRoute();
const userStore = useUserStore();

// 筛选条件
const lectureFilter = ref("");
const ratingFilter = ref("");

// 评价详情对话框
const evaluationDialogVisible = ref(false);
const selectedEvaluation = ref(null);

// 加载状态
const loading = ref(false);

// 分页参数
const currentPage = ref(1);
const pageSize = ref(10);
const total = ref(0);

// 统计数据
const stats = reactive({
  totalEvaluations: 0,
  avgRating: 0.0,
  fiveStarCount: 0,
  recentEvaluations: 0,
});

// 讲座列表
const lectures = ref([]);

// 评价数据
const evaluations = ref([]);

// 格式化日期
function formatDate(dateString) {
  if (!dateString) return "";
  const date = new Date(dateString);
  const pad = (n) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

// 加载讲座列表
async function loadLectures() {
  try {
    if (userStore.userInfo?.id) {
      const response = await getLecturePage({
        current: 1,
        size: 100,
        organizerId: userStore.userInfo.id,
      });
      if (response.code === 200) {
        lectures.value = response.data.records || [];
      }
    }
  } catch (error) {
    console.error("加载讲座列表失败:", error);
    ElMessage.error("加载讲座列表失败");
  }
}

// 加载评价数据
async function loadEvaluations() {
  try {
    loading.value = true;

    const params = {
      current: currentPage.value,
      size: pageSize.value,
      teacherId: userStore.userInfo?.id,
    };

    if (lectureFilter.value) {
      params.lectureId = lectureFilter.value;
    }
    if (ratingFilter.value) {
      params.sort = "score";
      params.order = ratingFilter.value;
    }
    const response = await getEvaluationPage(params);

    if (response.code === 200) {
      evaluations.value = response.data.records || [];
      total.value = response.data.total || 0;

      // 更新统计数据
      stats.totalEvaluations = total.value;
      stats.recentEvaluations = evaluations.value.length;

      // 计算平均评分
      if (evaluations.value.length > 0) {
        const totalScore = evaluations.value.reduce(
          (sum, item) => sum + Number(item.score),
          0,
        );
        stats.avgRating = (totalScore / evaluations.value.length).toFixed(1);
      }
    }
  } catch (error) {
    console.error("加载评价数据失败:", error);
    ElMessage.error("加载评价数据失败");
  } finally {
    loading.value = false;
  }
}

// 筛选条件变化处理
function handleFilterChange() {
  currentPage.value = 1;
  loadEvaluations();
}

// 分页处理
function handleSizeChange(val) {
  pageSize.value = val;
  currentPage.value = 1;
  loadEvaluations();
}

function handleCurrentChange(val) {
  currentPage.value = val;
  loadEvaluations();
}

// 查看评价详情
function viewEvaluation(evaluation) {
  selectedEvaluation.value = evaluation;
  evaluationDialogVisible.value = true;
}

onMounted(async () => {
  // 支持从其他页面带讲座 ID 直达（?lectureId=x）
  if (route.query.lectureId) {
    lectureFilter.value = Number(route.query.lectureId);
  }
  await Promise.all([loadLectures(), loadEvaluations()]);
});
</script>

<style scoped>
/* ============ 黑白极简风格（与全局一致） ============ */

.teacher-evaluations {
  font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
  line-height: 1.6;
  color: #333;
}

.container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 40px 20px;
}

.page-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 28px;
  flex-wrap: wrap;
  gap: 12px;
}

.page-head h1 {
  font-size: 36px;
  font-weight: 800;
  color: #333;
  margin: 0;
}

.head-actions {
  display: flex;
  gap: 10px;
}

.adm-select {
  min-width: 180px;
}

/* 统计卡 */
.stat-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
  margin-bottom: 24px;
}

.stat-card {
  background: #fff;
  padding: 20px;
  border-radius: 16px;
  border: 1px solid #f0f0f0;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.02);
  text-align: center;
}

.stat-number {
  font-size: 28px;
  font-weight: 800;
  color: #000;
  margin-bottom: 4px;
}

.stat-label {
  color: #666;
  font-size: 14px;
}

/* 表格补充 */
.score-text {
  font-weight: 600;
  color: #000;
}

.content-cell {
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
  padding: 0 16px 16px;
}

/* 详情 */
.evaluation-details {
  padding: 10px 0;
}

.detail-item {
  margin-bottom: 15px;
  display: flex;
  align-items: flex-start;
}

.detail-item label {
  font-weight: 500;
  width: 100px;
  color: #666;
  flex-shrink: 0;
  font-size: 14px;
}

.detail-item span {
  color: #333;
  font-size: 14px;
}

.content-box {
  flex: 1;
  color: #444;
  line-height: 1.6;
  background: #fafafa;
  padding: 12px;
  border-radius: 8px;
  border: 1px solid #f0f0f0;
  font-size: 14px;
}

.content-box.suggestion {
  background: #fffaf0;
  border-color: #ffe7ba;
}

@media (max-width: 900px) {
  .stat-row {
    grid-template-columns: 1fr;
  }
}
</style>
