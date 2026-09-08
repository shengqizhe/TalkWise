<template>
  <div class="demo-page">
    <div class="demo-header">
      <h1>AI浮动机器人演示</h1>
      <p>这是一个演示页面，展示浮动机器人的功能</p>
    </div>
    
    <div class="demo-content">
      <el-card class="demo-card">
        <template #header>
          <div class="card-header">
            <span>功能说明</span>
          </div>
        </template>
        <div class="feature-list">
          <div class="feature-item">
            <el-icon><ChatDotRound /></el-icon>
            <div class="feature-text">
              <h4>智能对话</h4>
              <p>点击右下角的机器人图标，可以开始与AI助手对话</p>
            </div>
          </div>
          <div class="feature-item">
            <el-icon><Search /></el-icon>
            <div class="feature-text">
              <h4>讲座推荐</h4>
              <p>AI助手可以根据您的需求推荐合适的讲座</p>
            </div>
          </div>
          <div class="feature-item">
            <el-icon><Location /></el-icon>
            <div class="feature-text">
              <h4>位置查询</h4>
              <p>可以询问讲座地点和校园位置信息</p>
            </div>
          </div>
          <div class="feature-item">
            <el-icon><Clock /></el-icon>
            <div class="feature-text">
              <h4>时间查询</h4>
              <p>可以查询特定时间的讲座安排</p>
            </div>
          </div>
        </div>
      </el-card>
      
      <el-card class="demo-card">
        <template #header>
          <div class="card-header">
            <span>使用示例</span>
          </div>
        </template>
        <div class="example-list">
          <div class="example-item">
            <h4>查询讲座</h4>
            <p>"今天有什么讲座？"</p>
            <p>"推荐一些关于人工智能的讲座"</p>
          </div>
          <div class="example-item">
            <h4>查询讲师</h4>
            <p>"张教授有什么讲座？"</p>
            <p>"李老师最近的讲座安排"</p>
          </div>
          <div class="example-item">
            <h4>查询地点</h4>
            <p>"图书馆在哪里？"</p>
            <p>"教学楼A101怎么走？"</p>
          </div>
        </div>
      </el-card>
    </div>
    
    <div class="demo-footer">
      <p>点击右下角的机器人开始体验！</p>
    </div>
  </div>
</template>

<script setup>
import { ChatDotRound, Search, Location, Clock } from '@element-plus/icons-vue'
</script>

<style scoped>
.demo-page {
  min-height: 100vh;
  background: linear-gradient(135deg, #000 0%, #333 100%);
  padding: 20px;
}

.demo-header {
  text-align: center;
  color: white;
  margin-bottom: 40px;
}

.demo-header h1 {
  font-size: 2.5rem;
  margin-bottom: 10px;
  text-shadow: 0 2px 4px rgba(0, 0, 0, 0.3);
}

.demo-header p {
  font-size: 1.1rem;
  opacity: 0.9;
}

.demo-content {
  max-width: 1200px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 30px;
}

.demo-card {
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  border: none;
  border-radius: 15px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
}

.card-header {
  font-size: 1.2rem;
  font-weight: 600;
  color: #333;
}

.feature-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 15px;
  padding: 15px;
  background: #f8f9fa;
  border-radius: 10px;
  transition: all 0.3s ease;
}

.feature-item:hover {
  background: #e9ecef;
  transform: translateY(-2px);
}

.feature-item .el-icon {
  font-size: 2rem;
  color: #000;
  background: rgba(0, 0, 0, 0.05);
  padding: 10px;
  border-radius: 50%;
}

.feature-text h4 {
  margin: 0 0 5px 0;
  color: #333;
  font-size: 1.1rem;
}

.feature-text p {
  margin: 0;
  color: #666;
  line-height: 1.5;
}

.example-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.example-item {
  padding: 15px;
  background: #f8f9fa;
  border-radius: 10px;
  border-left: 4px solid #000;
}

.example-item h4 {
  margin: 0 0 10px 0;
  color: #333;
  font-size: 1rem;
}

.example-item p {
  margin: 5px 0;
  color: #666;
  font-style: italic;
  background: white;
  padding: 8px 12px;
  border-radius: 6px;
  border: 1px solid #e9ecef;
}

.demo-footer {
  text-align: center;
  margin-top: 40px;
  color: white;
  font-size: 1.1rem;
  opacity: 0.9;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .demo-content {
    grid-template-columns: 1fr;
    gap: 20px;
  }
  
  .demo-header h1 {
    font-size: 2rem;
  }
  
  .feature-item {
    flex-direction: column;
    text-align: center;
  }
}
</style> 