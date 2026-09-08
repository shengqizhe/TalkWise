<template>
  <div class="teacher-profile">
    <div class="container">
      <div class="page-title">
        <h1>讲师个人中心</h1>
      </div>

      <div class="profile-wrap">
        <!-- 左侧信息卡 -->
        <div class="info-card">
          <div class="big-avatar">
            <img v-if="userInfo?.avatar" :src="userInfo.avatar" alt="头像" />
            <span v-else>{{ initial }}</span>
          </div>
          <h3>{{ userInfo?.realName || userInfo?.username }}</h3>
          <p>工号：{{ userInfo?.studentTeacherId }}</p>
          <p>所属部门：{{ departmentName }}</p>
          <p>累计讲座：{{ lectureCount }}场</p>
        </div>

        <!-- 右侧编辑表单 -->
        <div class="form-card">
          <div class="form-item">
            <label>讲师姓名</label>
            <input v-model="form.realName" type="text" placeholder="请输入姓名" />
          </div>
          <div class="form-item">
            <label>工号</label>
            <input :value="userInfo?.studentTeacherId" type="text" disabled />
          </div>
          <div class="form-item">
            <label>所属部门</label>
            <input :value="departmentName" type="text" disabled />
          </div>
          <div class="form-item">
            <label>个人简介</label>
            <textarea
              :value="introPlaceholder"
              disabled
              placeholder="个人简介功能暂未开放（后端暂无字段），后续版本支持"
            ></textarea>
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
import { getLecturePage } from "../../api/lecture";

const userStore = useUserStore();
const userInfo = computed(() => userStore.userInfo);
const saving = ref(false);
const departmentList = ref([]);
const lectureCount = ref(0);

const form = reactive({ realName: "" });

const initial = computed(() => {
  const name = userInfo.value?.realName || userInfo.value?.username || "";
  return name ? name.charAt(0).toUpperCase() : "用";
});

const departmentName = computed(() => {
  if (!userInfo.value?.departmentId) return "未设置部门";
  const dept = departmentList.value.find((d) => d.id === userInfo.value.departmentId);
  return dept ? dept.departmentName : "未设置部门";
});

const introPlaceholder =
  "个人简介功能暂未开放（后端暂无该字段），待后端支持后可在此维护。";

async function loadDepartments() {
  try {
    const res = await fetchAllDepartments();
    if (res.code === 200) departmentList.value = res.data || [];
  } catch (error) {
    console.error("获取部门信息失败:", error);
  }
}

async function loadLectureCount() {
  try {
    const res = await getLecturePage({
      current: 1,
      size: 1,
      organizerId: userInfo.value?.id,
    });
    if (res.code === 200) lectureCount.value = res.data.total || 0;
  } catch (error) {
    console.error("获取讲座数失败:", error);
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
  loadDepartments();
  loadLectureCount();
});
</script>

<style scoped>
/* ============ 黑白极简风格（与学生端统一） ============ */

.teacher-profile {
  font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
  line-height: 1.6;
  color: #333;
}

.container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 40px 20px;
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

.profile-wrap {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 40px;
  align-items: start;
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

.form-item input,
.form-item textarea {
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

.form-item textarea {
  min-height: 100px;
  resize: none;
}

.form-item input:disabled,
.form-item textarea:disabled {
  color: #888;
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

@media (max-width: 768px) {
  .profile-wrap {
    grid-template-columns: 1fr;
  }
}
</style>
