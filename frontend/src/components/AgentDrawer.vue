<template>
  <!-- 顶栏 AI 助手入口：常驻可见，不遮挡页面内容 -->
  <button class="ai-entry" type="button" @click="drawerVisible = true">
    <span class="ai-entry-icon">✨</span>
    <span class="ai-entry-text">AI 助手</span>
  </button>

  <!-- 右侧抽屉：展开时只占右侧，主内容仍可见 -->
  <el-drawer
    v-model="drawerVisible"
    :with-header="false"
    size="420px"
    :append-to-body="true"
    class="ai-drawer"
  >
    <div class="drawer-inner">
      <div class="drawer-header">
        <span>知讲 AI 助手</span>
        <button class="drawer-close" type="button" @click="drawerVisible = false">✕</button>
      </div>
      <AgentChatPanel />
    </div>
  </el-drawer>
</template>

<script setup>
import { ref } from 'vue'
import AgentChatPanel from './AgentChatPanel.vue'

const drawerVisible = ref(false)

// 供外部（如首页引导按钮）主动打开
defineExpose({ open: () => (drawerVisible.value = true) })
</script>

<style scoped>
.ai-entry {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 34px;
  padding: 0 14px;
  margin-right: 12px;
  background: #000;
  color: #fff;
  border: none;
  border-radius: 17px;
  font-size: 13px;
  font-family: inherit;
  cursor: pointer;
  transition: opacity 0.2s, transform 0.2s;
}
.ai-entry:hover { opacity: 0.82; }
.ai-entry:active { transform: scale(0.97); }
.ai-entry-icon { font-size: 14px; }
.ai-entry-text { white-space: nowrap; }

.drawer-inner {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.drawer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 18px;
  background: linear-gradient(135deg, #000 0%, #333 100%);
  color: #fff;
  font-size: 15px;
  flex-shrink: 0;
}

.drawer-close {
  background: none;
  border: none;
  color: #fff;
  font-size: 16px;
  cursor: pointer;
  font-family: inherit;
  line-height: 1;
  padding: 2px 4px;
}
.drawer-close:hover { opacity: 0.7; }

@media (max-width: 480px) {
  .ai-entry-text { display: none; }
  .ai-entry { padding: 0 10px; }
}
</style>

<!-- 抽屉通过 teleport 挂到 body，需用非 scoped 样式重置其默认内边距 -->
<style>
.ai-drawer {
  --el-drawer-padding-primary: 0;
}
.ai-drawer .el-drawer__body {
  padding: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
</style>
