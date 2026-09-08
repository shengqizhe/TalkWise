<template>
  <div class="home-page">
    <!-- 头部（与新版 header 一致：logo 方块 + 登录） -->
    <header class="home-header">
      <div class="header-inner">
        <div class="nav-left">
          <div class="logo-icon">S</div>
          <span class="logo-text">知讲</span>
        </div>
        <nav class="nav-center">
          <a class="nav-link active" @click.prevent>首页</a>
          <router-link class="nav-link" to="/login">讲座列表</router-link>
          <router-link class="nav-link" to="/login">我的预约</router-link>
          <router-link class="nav-link" to="/login">个人中心</router-link>
        </nav>
        <router-link to="/login" class="btn-login">登录</router-link>
      </div>
    </header>

    <main class="container">
      <!-- 主视觉区 -->
      <section class="hero">
        <div class="hero-text">
          <h1>发现下一场<br />值得参加的讲座</h1>
          <p>按时间、主题与讲师快速筛选，一键预约</p>
          <div class="search-box">
            <input
              v-model="keyword"
              type="text"
              class="search-input"
              placeholder="搜索讲座、讲师或关键词"
              @keyup.enter="goLogin"
            />
            <button class="search-btn" @click="goLogin">浏览讲座</button>
          </div>
        </div>
        <div class="illustration-container">
          <img :src="illustration" alt="知讲插画" class="illustration-img" />
        </div>
      </section>

      <!-- 功能卡片 -->
      <section class="cards-section">
        <div class="card">
          <div class="card-header">
            <h3>即将开始</h3>
            <svg class="card-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"></path><circle cx="12" cy="10" r="3"></circle></svg>
          </div>
          <p>通过冗余的页面运营设计稿，平面无法透视设计稿</p>
        </div>
        <div class="card">
          <div class="card-header">
            <h3>热门推荐</h3>
            <svg class="card-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"></rect><line x1="3" y1="9" x2="21" y2="9"></line><line x1="9" y1="21" x2="9" y2="9"></line></svg>
          </div>
          <p>搜合无法透视设计稿涓流信息网络</p>
        </div>
        <div class="card">
          <div class="card-header">
            <h3>线上直播</h3>
            <svg class="card-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path><circle cx="12" cy="7" r="4"></circle></svg>
          </div>
          <p>平面无法透视设计稿，平面无透视设计稿</p>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup>
import { ref } from "vue";
import { useRouter } from "vue-router";
import illustration from "../img/讲座列表.png";

const router = useRouter();
const keyword = ref("");

function goLogin() {
  router.push("/login");
}
</script>

<style scoped>
/* ============ 黑白极简风格（与新版设计稿一致） ============ */

.home-page {
  min-height: 100vh;
  background: #fff;
  font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
  color: #333;
  line-height: 1.6;
}

a {
  text-decoration: none;
  color: inherit;
  transition: color 0.2s;
}

/* ---------- 头部 ---------- */
.home-header {
  background: #fff;
}

.header-inner {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px 20px;
  border-bottom: 1px solid #f0f0f0;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.nav-left {
  display: flex;
  align-items: center;
  font-weight: bold;
}

.logo-icon {
  width: 24px;
  height: 24px;
  background-color: #000;
  color: #fff;
  border-radius: 4px;
  display: flex;
  justify-content: center;
  align-items: center;
  margin-right: 10px;
  font-size: 14px;
}

.nav-center {
  display: flex;
  gap: 40px;
  color: #555;
}

.nav-link {
  color: #555;
  cursor: pointer;
}

.nav-link:hover,
.nav-link.active {
  color: #000;
  font-weight: 600;
}

.btn-login {
  padding: 8px 24px;
  border: 1px solid #dcdcdc;
  border-radius: 20px;
  font-size: 14px;
  color: #333;
}

.btn-login:hover {
  background-color: #f9f9f9;
  color: #000;
}

/* ---------- 内容 ---------- */
.container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 20px;
}

.hero {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 60px;
  padding: 80px 0;
  align-items: center;
}

.hero-text h1 {
  font-size: 48px;
  font-weight: 800;
  margin-bottom: 16px;
  line-height: 1.2;
  color: #333;
}

.hero-text p {
  font-size: 18px;
  color: #666;
  margin-bottom: 40px;
}

.search-box {
  display: flex;
  width: 100%;
  max-width: 480px;
  border: 1px solid #e0e0e0;
  border-radius: 24px;
  overflow: hidden;
  background-color: #fff;
}

.search-input {
  flex: 1;
  padding: 12px 20px;
  border: none;
  outline: none;
  font-size: 14px;
  font-family: inherit;
}

.search-btn {
  padding: 0 24px;
  background-color: transparent;
  border: none;
  border-left: 1px solid #e0e0e0;
  color: #555;
  font-size: 14px;
  cursor: pointer;
  font-family: inherit;
}

.search-btn:hover {
  background-color: #f9f9f9;
}

.illustration-container {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100%;
}

.illustration-img {
  max-width: 100%;
  height: auto;
  object-fit: contain;
}

/* ---------- 功能卡片 ---------- */
.cards-section {
  display: flex;
  gap: 30px;
  padding-bottom: 80px;
}

.card {
  flex: 1;
  background-color: #ffffff;
  border-radius: 16px;
  padding: 30px;
  display: flex;
  flex-direction: column;
  border: 1px solid #f0f0f0;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.02);
  transition: transform 0.3s ease, box-shadow 0.3s ease;
}

.card:hover {
  transform: translateY(-5px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.05);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 20px;
}

.card h3 {
  font-size: 22px;
  font-weight: bold;
  margin: 0;
}

.card p {
  font-size: 14px;
  color: #666;
  margin-bottom: auto;
}

.card-icon {
  width: 48px;
  height: 48px;
  color: #333;
}

/* ---------- 响应式 ---------- */
@media (max-width: 768px) {
  .nav-center {
    display: none;
  }

  .hero {
    grid-template-columns: 1fr;
    padding: 40px 0;
  }

  .hero-text h1 {
    font-size: 36px;
  }

  .illustration-container {
    margin-bottom: 40px;
  }

  .cards-section {
    flex-direction: column;
  }
}
</style>
