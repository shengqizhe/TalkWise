<template>
  <div class="task-center">
    <!-- 欢迎卡片 -->
    <el-row :gutter="20">
      <el-col :span="24">
        <el-card class="welcome-card">
          <div class="welcome-content">
            <div class="welcome-text">
              <h2>任务中心</h2>
              <p>AI 后台任务：报表生成与讲座提醒会在后台执行，完成后通过通知栏告知你</p>
            </div>
            <div class="welcome-avatar">
              <el-avatar :size="80" :src="userStore.userInfo?.avatar">
                {{
                  userStore.userInfo?.realName?.charAt(0) ||
                  userStore.userInfo?.username?.charAt(0)
                }}
              </el-avatar>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 筛选与新建 -->
    <el-row :gutter="20" style="margin: 20px 0">
      <el-col :span="5">
        <el-select v-model="query.type" placeholder="全部类型" clearable @change="handleSearch">
          <el-option label="报表生成" value="report_generation" />
          <el-option label="提醒推送" value="reminder_notification" />
          <el-option label="评价分析" value="evaluation_analysis" />
        </el-select>
      </el-col>
      <el-col :span="5">
        <el-select v-model="query.status" placeholder="全部状态" clearable @change="handleSearch">
          <el-option label="排队中" value="PENDING" />
          <el-option label="执行中" value="RUNNING" />
          <el-option label="已完成" value="SUCCESS" />
          <el-option label="已失败" value="FAILED" />
          <el-option label="已取消" value="CANCELLED" />
        </el-select>
      </el-col>
      <el-col :span="6">
        <el-button @click="handleReset">重置</el-button>
        <el-button type="primary" @click="openReportDialog">新建报表</el-button>
        <el-button type="primary" @click="openReminderDialog">新建提醒</el-button>
      </el-col>
    </el-row>

    <!-- 任务表格 -->
    <el-table :data="taskList" v-loading="loading" border>
      <el-table-column prop="id" label="任务号" width="90" />
      <el-table-column label="类型" width="110">
        <template #default="scope">{{ typeLabel(scope.row.type) }}</template>
      </el-table-column>
      <el-table-column prop="name" label="任务名称" min-width="200" show-overflow-tooltip />
      <el-table-column label="状态" width="100">
        <template #default="scope">
          <el-tag :type="statusTagType(scope.row.status)" size="small">
            {{ statusLabel(scope.row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="进度" width="150">
        <template #default="scope">
          <el-progress
            :percentage="scope.row.progress || 0"
            :status="scope.row.status === 'FAILED' ? 'exception' : undefined"
            :stroke-width="10"
          />
        </template>
      </el-table-column>
      <el-table-column prop="progressText" label="进度说明" min-width="160" show-overflow-tooltip />
      <el-table-column prop="createdTime" label="创建时间" width="170" />
      <el-table-column label="操作" width="220">
        <template #default="scope">
          <el-button
            v-if="scope.row.status === 'SUCCESS'"
            size="small"
            type="primary"
            @click="openResultDialog(scope.row)"
          >查看结果</el-button>
          <el-button
            v-if="scope.row.status === 'FAILED'"
            size="small"
            type="warning"
            @click="handleRetry(scope.row)"
          >重试</el-button>
          <el-button
            v-if="scope.row.status === 'PENDING' || scope.row.status === 'RUNNING'"
            size="small"
            type="danger"
            @click="handleCancel(scope.row)"
          >取消</el-button>
          <el-button
            v-if="scope.row.status === 'FAILED'"
            size="small"
            @click="openErrorDialog(scope.row)"
          >失败原因</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <el-pagination
      style="margin-top: 20px; text-align: right"
      background
      layout="prev, pager, next, jumper"
      :total="total"
      :page-size="query.size"
      :current-page="query.current"
      @current-change="handleCurrentChange"
    />

    <!-- 新建报表 -->
    <el-dialog v-model="reportDialogVisible" title="新建报表任务" width="560px" destroy-on-close>
      <el-form :model="reportForm" label-width="90px">
        <el-form-item label="报表标题">
          <el-input v-model="reportForm.title" placeholder="例如：9 月运营周报" />
        </el-form-item>
        <el-form-item label="统计指标">
          <el-checkbox-group v-model="reportForm.metrics">
            <el-checkbox label="overview">总体概览</el-checkbox>
            <el-checkbox label="categories">分类热度</el-checkbox>
            <el-checkbox label="departments">院系分布</el-checkbox>
            <el-checkbox label="top_lectures">热门讲座</el-checkbox>
          </el-checkbox-group>
          <div class="field-hint">不勾选则生成全部指标；管理员统计全平台，教师仅统计自己的讲座。</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reportDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitReport">创建任务</el-button>
      </template>
    </el-dialog>

    <!-- 新建提醒 -->
    <el-dialog v-model="reminderDialogVisible" title="新建提醒推送任务" width="560px" destroy-on-close>
      <el-form :model="reminderForm" label-width="90px">
        <el-form-item label="选择讲座">
          <el-select
            v-model="reminderForm.lectureId"
            placeholder="请选择要推送提醒的讲座"
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="lec in lectureOptions"
              :key="lec.id"
              :label="`${lec.title}（${lec.lectureTime}）`"
              :value="lec.id"
            />
          </el-select>
          <div class="field-hint">将向该讲座所有已确认报名的学生发送开始提醒（站内通知 + 邮件）。</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reminderDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitReminder">创建任务</el-button>
      </template>
    </el-dialog>

    <!-- 结果查看 -->
    <el-dialog v-model="resultDialogVisible" title="任务结果" width="720px">
      <pre class="result-content">{{ currentTask?.result || "（无结果）" }}</pre>
      <template #footer>
        <el-button type="primary" @click="resultDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 失败原因 -->
    <el-dialog v-model="errorDialogVisible" title="失败原因" width="560px">
      <el-alert type="error" :closable="false" show-icon>
        <template #title>任务执行失败</template>
        <div class="error-detail">{{ currentTask?.error || "未知原因" }}</div>
      </el-alert>
      <div class="field-hint" style="margin-top: 12px">
        可点击列表中的“重试”重新排队执行。
      </div>
      <template #footer>
        <el-button @click="errorDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { useUserStore } from "../../stores/user";
import {
  getAgentTaskPage,
  createAgentTask,
  cancelAgentTask,
  retryAgentTask,
} from "../../api/agentTask";
import { getLecturePage } from "../../api/lecture";

const userStore = useUserStore();

const loading = ref(false);
const submitting = ref(false);
const taskList = ref([]);
const total = ref(0);
const query = reactive({
  current: 1,
  size: 10,
  type: "",
  status: "",
});

const currentTask = ref(null);
const resultDialogVisible = ref(false);
const errorDialogVisible = ref(false);

const reportDialogVisible = ref(false);
const reportForm = reactive({ title: "", metrics: [] });

const reminderDialogVisible = ref(false);
const reminderForm = reactive({ lectureId: null });
const lectureOptions = ref([]);

const TYPE_LABELS = {
  report_generation: "报表生成",
  reminder_notification: "提醒推送",
  evaluation_analysis: "评价分析",
};

const STATUS_LABELS = {
  PENDING: "排队中",
  RUNNING: "执行中",
  SUCCESS: "已完成",
  FAILED: "已失败",
  CANCELLED: "已取消",
};

function typeLabel(type) {
  return TYPE_LABELS[type] || type;
}

function statusLabel(status) {
  return STATUS_LABELS[status] || status;
}

function statusTagType(status) {
  switch (status) {
    case "SUCCESS": return "success";
    case "FAILED": return "danger";
    case "RUNNING": return "primary";
    case "CANCELLED": return "info";
    default: return "warning";
  }
}

const getTaskList = async () => {
  loading.value = true;
  try {
    const res = await getAgentTaskPage({
      current: query.current,
      size: query.size,
      type: query.type || undefined,
      status: query.status || undefined,
    });
    taskList.value = res.data?.records || [];
    total.value = res.data?.total || 0;
  } catch (error) {
    console.error("获取任务列表失败:", error);
    ElMessage.error("获取任务列表失败");
  } finally {
    loading.value = false;
  }
};

const handleSearch = () => {
  query.current = 1;
  getTaskList();
};

const handleReset = () => {
  query.type = "";
  query.status = "";
  query.current = 1;
  getTaskList();
};

const handleCurrentChange = (page) => {
  query.current = page;
  getTaskList();
};

// ---------- 弹窗互斥：同一时刻只保留一个，避免叠加遮挡 ==========
const closeAllDialogs = () => {
  reportDialogVisible.value = false;
  reminderDialogVisible.value = false;
  resultDialogVisible.value = false;
  errorDialogVisible.value = false;
};

// ---------- 新建报表 ----------
const openReportDialog = () => {
  closeAllDialogs();
  reportForm.title = "";
  reportForm.metrics = [];
  reportDialogVisible.value = true;
};

const submitReport = async () => {
  submitting.value = true;
  try {
    await createAgentTask({
      type: "report_generation",
      name: reportForm.title.trim() || "平台数据报表",
      params: JSON.stringify({
        title: reportForm.title.trim() || "平台数据报表",
        metrics: reportForm.metrics,
      }),
    });
    ElMessage.success("报表任务已创建，可在列表中查看进度");
    reportDialogVisible.value = false;
    handleSearch();
  } catch (error) {
    console.error("创建报表任务失败:", error);
    ElMessage.error(error.response?.data?.message || "创建任务失败");
  } finally {
    submitting.value = false;
  }
};

// ---------- 新建提醒 ----------
const openReminderDialog = async () => {
  closeAllDialogs();
  reminderForm.lectureId = null;
  reminderDialogVisible.value = true;
  if (lectureOptions.value.length === 0) {
    try {
      const res = await getLecturePage({ current: 1, size: 100 });
      lectureOptions.value = (res.data?.records || []).map((l) => ({
        id: l.id,
        title: l.title,
        lectureTime: l.lectureTime || "待定",
      }));
    } catch (error) {
      console.error("获取讲座列表失败:", error);
      ElMessage.error("获取讲座列表失败");
    }
  }
};

const submitReminder = async () => {
  if (!reminderForm.lectureId) {
    ElMessage.warning("请选择要推送提醒的讲座");
    return;
  }
  submitting.value = true;
  try {
    const selected = lectureOptions.value.find((l) => l.id === reminderForm.lectureId);
    await createAgentTask({
      type: "reminder_notification",
      name: `《${selected?.title || reminderForm.lectureId}》开讲提醒`,
      params: JSON.stringify({ lectureId: reminderForm.lectureId }),
    });
    ElMessage.success("提醒任务已创建，可在列表中查看进度");
    reminderDialogVisible.value = false;
    handleSearch();
  } catch (error) {
    console.error("创建提醒任务失败:", error);
    ElMessage.error(error.response?.data?.message || "创建任务失败");
  } finally {
    submitting.value = false;
  }
};

// ---------- 结果与失败原因 ----------
const openResultDialog = (row) => {
  closeAllDialogs();
  currentTask.value = row;
  resultDialogVisible.value = true;
};

const openErrorDialog = (row) => {
  closeAllDialogs();
  currentTask.value = row;
  errorDialogVisible.value = true;
};

// ---------- 取消与重试 ----------
const handleCancel = (row) => {
  ElMessageBox.confirm(`确定要取消任务 #${row.id} 吗？`, "提示", { type: "warning" }).then(
    async () => {
      try {
        await cancelAgentTask(row.id);
        ElMessage.success("任务已取消");
        await getTaskList();
      } catch (error) {
        console.error("取消失败:", error);
        ElMessage.error(error.response?.data?.message || "取消失败");
      }
    }
  );
};

const handleRetry = async (row) => {
  try {
    await retryAgentTask(row.id);
    ElMessage.success("任务已重新排队");
    await getTaskList();
  } catch (error) {
    console.error("重试失败:", error);
    ElMessage.error(error.response?.data?.message || "重试失败");
  }
};

onMounted(() => {
  getTaskList();
});
</script>

<style scoped>
.task-center {
  font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
  color: #333;
}

.welcome-card {
  border-radius: 16px;
  border: 1px solid #f0f0f0;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.02);
}

.welcome-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.welcome-text h2 {
  margin: 0 0 8px 0;
  font-size: 24px;
  font-weight: 800;
  color: #222;
}

.welcome-text p {
  margin: 0;
  font-size: 14px;
  color: #888;
}

.field-hint {
  margin-top: 6px;
  font-size: 12px;
  color: #999;
  line-height: 1.6;
}

.result-content {
  max-height: 460px;
  overflow: auto;
  margin: 0;
  padding: 16px;
  background: #fafafa;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: "PingFang SC", "Microsoft YaHei", monospace;
}

.error-detail {
  margin-top: 6px;
  font-size: 13px;
  line-height: 1.7;
  word-break: break-word;
}
</style>
