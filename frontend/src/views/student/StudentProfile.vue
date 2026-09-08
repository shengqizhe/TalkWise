<template>
  <div class="student-profile">
    <div class="container">
      <div class="page-title">
        <h1>个人中心</h1>
      </div>

      <div class="profile-wrap">
        <!-- 左侧基本信息卡片（新设计稿 info-card） -->
        <div class="info-card">
          <div class="big-avatar">
            <img v-if="userInfo?.avatar" :src="userInfo.avatar" alt="头像" />
            <span v-else>{{ initial }}</span>
          </div>
          <h3>{{ userInfo?.realName || userInfo?.username }}</h3>
          <p>学号：{{ userInfo?.studentTeacherId }}</p>
          <p>{{ departmentName }}</p>
        </div>

        <!-- 右侧修改表单（新设计稿 form-card） -->
        <div class="form-card">
          <div class="form-item">
            <label>姓名</label>
            <input v-model="form.realName" type="text" placeholder="请输入姓名" />
          </div>
          <div class="form-item">
            <label>学号</label>
            <input :value="userInfo?.studentTeacherId" type="text" disabled />
          </div>
          <div class="form-item">
            <label>所在院系</label>
            <input :value="departmentName" type="text" disabled />
          </div>
          <div class="form-item">
            <label>联系邮箱</label>
            <input v-model="form.email" type="text" placeholder="请输入联系邮箱" />
          </div>
          <button class="btn-solid" :disabled="saving" @click="saveProfile">
            {{ saving ? "保存中..." : "保存修改" }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { useUserStore } from "../../stores/user";
import { updateUser } from "../../api/user";
import { fetchAllDepartments } from "../../api/department";

const userStore = useUserStore();
const userInfo = computed(() => userStore.userInfo);
const saving = ref(false);
const departmentList = ref([]);

const form = reactive({ realName: "", email: "" });

const initial = computed(() => {
  const name = userInfo.value?.realName || userInfo.value?.username || "";
  return name ? name.charAt(0).toUpperCase() : "用";
});

const departmentName = computed(() => {
  if (!userInfo.value?.departmentId) return "未设置院系";
  const dept = departmentList.value.find((d) => d.id === userInfo.value.departmentId);
  return dept ? dept.departmentName : "未设置院系";
});

async function loadDepartments() {
  try {
    const res = await fetchAllDepartments();
    if (res.code === 200) departmentList.value = res.data || [];
  } catch (error) {
    console.error("获取系别信息失败:", error);
  }
}

async function saveProfile() {
  if (!form.realName.trim()) {
    ElMessage.warning("姓名不能为空");
    return;
  }
  saving.value = true;
  try {
    const response = await updateUser({
      ...userInfo.value,
      realName: form.realName.trim(),
      email: form.email.trim(),
    });
    if (response.code === 200) {
      ElMessage.success("保存成功");
      await userStore.getUserInfoAction();
    } else {
      ElMessage.error(response.message || "保存失败");
    }
  } catch (error) {
    console.error("保存个人信息失败:", error);
    ElMessage.error("保存失败");
  } finally {
    saving.value = false;
  }
}

onMounted(() => {
  form.realName = userInfo.value?.realName || "";
  form.email = userInfo.value?.email || "";
  loadDepartments();
});
</script>

<style scoped>
/* ============ 以下样式数值与 student-profile.html 逐行一致 ============ */

.student-profile {
  font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
  line-height: 1.6;
  color: #333;
}

.container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 60px 20px;
}

.page-title {
  margin-bottom: 40px;
}

.page-title h1 {
  font-size: 36px;
  font-weight: 800;
  line-height: 1.2;
  color: #333;
}

/* 双栏布局 */
.profile-wrap {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 40px;
}

/* 左侧信息卡 */
.info-card {
  background-color: #ffffff;
  border-radius: 16px;
  padding: 40px 30px;
  border: 1px solid #f0f0f0;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.02);
  text-align: center;
  height: fit-content;
}

.big-avatar {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background-color: #eee;
  margin: 0 auto 20px;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  font-weight: 600;
  color: #666;
}

.big-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.info-card h3 {
  font-size: 20px;
  font-weight: bold;
  margin-bottom: 8px;
  color: #333;
}

.info-card p {
  color: #666;
  font-size: 14px;
  margin: 4px 0;
}

/* 右侧表单卡 */
.form-card {
  background-color: #ffffff;
  border-radius: 16px;
  padding: 40px;
  border: 1px solid #f0f0f0;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.02);
}

.form-item {
  margin-bottom: 24px;
}

.form-item label {
  display: block;
  font-size: 14px;
  font-weight: 500;
  margin-bottom: 8px;
  color: #333;
}

.form-item input {
  width: 100%;
  padding: 12px 16px;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  font-size: 14px;
  font-family: inherit;
  outline: none;
  background-color: #fafafa;
  box-sizing: border-box;
  color: #333;
  transition: border-color 0.2s, background-color 0.2s;
}

.form-item input:focus {
  border-color: #000;
  background-color: #fff;
}

.form-item input:disabled {
  cursor: not-allowed;
}

.btn-solid {
  padding: 12px 32px;
  background-color: #000;
  color: #fff;
  border: none;
  border-radius: 24px;
  font-size: 14px;
  font-family: inherit;
  cursor: pointer;
  transition: background 0.2s;
}

.btn-solid:hover {
  background-color: #333;
}

.btn-solid:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* 响应式 */
@media (max-width: 768px) {
  .profile-wrap {
    grid-template-columns: 1fr;
  }
}
</style>
