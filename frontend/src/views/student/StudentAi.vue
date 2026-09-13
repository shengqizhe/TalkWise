<script setup>
import { ref } from "vue";
import { ElMessage } from "element-plus";
import { agentChat, confirmAgentAction, rejectAgentAction } from "@/api/agent";

const inputText = ref("");
const loading = ref(false);
const history = ref([
  {
    role: "ai",
    content:
      "你好！我是知讲 AI 助手，可以帮你查讲座、看报名记录、直接报名或取消报名。试试对我说：推荐一场 AI 相关的讲座。",
    tools: [],
    timestamp: new Date(),
  },
]);

const toolLabels = {
  searchLectures: "查询讲座",
  getMyRegistrations: "查询我的报名",
  registerLecture: "报名讲座",
  cancelRegistration: "取消报名",
  queryStatistics: "查询统计",
  generatePromotion: "生成宣传文案",
  analyzeEvaluations: "分析评价",
  getAnalysisTaskStatus: "查询分析进度",
  estimateCapacity: "估算建议容量",
};

async function confirmAction(actionId) {
  try {
    const res = await confirmAgentAction(actionId);
    history.value.push({ role: "ai", content: res.data?.status === "CONFIRMED" ? `讲座已创建，编号：${res.data.resultId}` : "讲座创建状态已更新", tools: [], timestamp: new Date() });
  } catch (error) {
    ElMessage.error(error.response?.data?.message || "确认创建失败");
  }
}

async function rejectAction(actionId) {
  try {
    await rejectAgentAction(actionId);
    history.value.push({ role: "ai", content: "已取消本次讲座创建。", tools: [], timestamp: new Date() });
  } catch (error) {
    ElMessage.error(error.response?.data?.message || "取消创建失败");
  }
}

async function handleAnalyze() {
  const message = inputText.value.trim();
  if (!message || loading.value) return;

  history.value.push({
    role: "user",
    content: message,
    tools: [],
    timestamp: new Date(),
  });
  inputText.value = "";
  loading.value = true;

  try {
    const res = await agentChat(message);
    history.value.push({
      role: "ai",
      content: res.data?.reply || "（未返回内容）",
      tools: res.data?.tools || [],
      action: res.data?.action || null,
      timestamp: new Date(),
    });
  } catch (error) {
    console.error("Agent 请求失败:", error);
    ElMessage.error("AI助手请求失败");
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="student-ai">
    <el-row :gutter="20">
      <el-col :span="24">
        <el-card class="ai-analysis-card">
          <div class="card-header">
            <h2>AI智能助手</h2>
            <p class="ai-description">
              基于人工智能的智能对话系统，为您推荐个性化讲座，解答问题，管理报名，让学习体验更智能、更高效
            </p>
          </div>
          <div class="chat-container">
            <div class="chat-messages">
              <div
                v-for="(item, idx) in history"
                :key="idx"
                :class="['chat-bubble', item.role]"
              >
                <div class="message-header">
                  <b>{{ item.role === "user" ? "我：" : "AI：" }}</b>
                  <span class="message-time">{{ item.timestamp.toLocaleString() }}</span>
                </div>
                <div class="message-content">{{ item.content }}</div>
                <div v-if="item.action?.status === 'PENDING'" class="action-card">
                  <div>讲座草稿待确认（动作 #{{ item.action.actionId }}）</div>
                  <el-button size="small" type="primary" @click="confirmAction(item.action.actionId)">确认创建</el-button>
                  <el-button size="small" @click="rejectAction(item.action.actionId)">取消</el-button>
                </div>
                <div v-if="item.tools?.length" class="tool-trail">
                  <span v-for="(tool, toolIndex) in item.tools" :key="toolIndex" class="tool-chip">
                    {{ toolLabel(tool.name) }}
                  </span>
                </div>
              </div>
              <div v-if="loading" class="chat-bubble ai">
                <div class="message-content loading-text">思考中…</div>
              </div>
            </div>
            <div class="chat-input">
              <el-input
                v-model="inputText"
                placeholder="与AI对话，例如：推荐AI相关讲座、查看我的报名或取消报名"
                @keyup.enter="handleAnalyze"
                :disabled="loading"
              >
                <template #append>
                  <el-button
                    type="primary"
                    @click="handleAnalyze"
                    :loading="loading"
                    :disabled="!inputText.trim()"
                  >
                    发送
                  </el-button>
                </template>
              </el-input>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.student-ai { max-width: 1200px; margin: 0 auto; padding: 32px 20px; }
.ai-analysis-card { box-shadow: 0 4px 16px rgba(0, 0, 0, 0.06); }
.card-header { text-align: center; margin-bottom: 20px; }
.card-header h2 { color: #333; margin-bottom: 8px; }
.ai-description { color: #666; font-size: 14px; margin: 0; }
.chat-container { max-height: 600px; display: flex; flex-direction: column; }
.chat-messages { flex: 1; overflow-y: auto; padding: 20px; background: #f8f9fa; border-radius: 8px; margin-bottom: 20px; max-height: 500px; }
.chat-bubble { margin-bottom: 20px; max-width: 80%; }
.chat-bubble.user { margin-left: auto; text-align: right; }
.chat-bubble.ai { margin-right: auto; text-align: left; }
.message-header { margin-bottom: 8px; font-size: 14px; font-weight: 600; }
.message-time { font-size: 12px; color: #999; margin-left: 10px; font-weight: normal; }
.message-content { padding: 12px 16px; border-radius: 12px; line-height: 1.5; white-space: pre-wrap; word-break: break-word; }
.chat-bubble.user .message-content { background: #000; color: white; border-bottom-right-radius: 4px; }
.chat-bubble.ai .message-content { background: white; color: #333; border: 1px solid #e0e0e0; border-bottom-left-radius: 4px; }
.tool-trail { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 8px; }
.action-card { margin-top: 10px; padding: 12px; background: #fff7e6; border: 1px solid #ffd591; border-radius: 8px; }
.action-card .el-button { margin-top: 8px; }

.tool-chip { font-size: 12px; color: #666; background: #f2f2f2; border-radius: 20px; padding: 3px 10px; }
.loading-text { color: #999; }
.chat-input { margin-top: 20px; }
@media (max-width: 768px) {
  .student-ai { padding: 10px; }
  .chat-bubble { max-width: 90%; }
}
</style>
