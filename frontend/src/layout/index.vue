<template>
  <div class="app-layout">
    <!-- 顶部栏（新设计稿 header：1200 容器内 20px 上下留白 + logo 方块） -->
    <header class="topbar">
      <div class="header-inner">
        <div class="nav-left">
          <div class="logo-icon">S</div>
          <span class="logo-text">知讲</span>
        </div>
        <nav class="nav-center">
          <router-link
            v-for="item in menus"
            :key="item.label"
            :to="item.to"
            class="nav-link"
            :class="{ active: isActive(item) }"
          >
            {{ item.label }}
          </router-link>
        </nav>
        <div class="user-area">
          <BellNotification />
          <el-dropdown trigger="click" @command="handleCommand">
            <div class="user-trigger">
              <div class="avatar">
                <img v-if="userStore.userInfo?.avatar" :src="userStore.userInfo.avatar" alt="头像" />
                <span v-else>{{ avatarText }}</span>
              </div>
              <span class="user-name">
                {{ userStore.userInfo?.realName || userStore.userInfo?.username }}
              </span>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人信息</el-dropdown-item>
                <el-dropdown-item command="password">修改密码</el-dropdown-item>
                <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
    </header>

    <!-- 内容区 -->
    <main class="page-main">
      <router-view />
    </main>

    <!-- 浮动AI机器人 -->
    <FloatingAiBot />

    <!-- 个人信息对话框 -->
    <el-dialog v-model="profileDialogVisible" title="个人信息" width="500px">
      <el-form ref="profileFormRef" :model="profileForm" :rules="profileRules" label-width="100px">
        <el-form-item label="头像">
          <div class="avatar-upload-container">
            <el-avatar :size="100" :src="profileForm.avatar || userStore.userInfo?.avatar">
              {{ userStore.userInfo?.realName?.charAt(0) || userStore.userInfo?.username?.charAt(0) }}
            </el-avatar>
            <el-upload
              class="avatar-uploader"
              action=""
              :http-request="uploadAvatar"
              :show-file-list="false"
              :on-success="handleAvatarSuccess"
              :before-upload="beforeAvatarUpload"
            >
              <el-button type="primary" size="small">更换头像</el-button>
            </el-upload>
          </div>
        </el-form-item>
        <el-form-item label="用户名" prop="username">
          <el-input v-model="profileForm.username" placeholder="请输入用户名" disabled />
        </el-form-item>
        <el-form-item label="真实姓名" prop="realName">
          <el-input v-model="profileForm.realName" placeholder="请输入真实姓名" />
        </el-form-item>
        <el-form-item label="学号/工号" prop="studentTeacherId">
          <el-input v-model="profileForm.studentTeacherId" placeholder="请输入学号/工号" disabled />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="profileForm.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="profileForm.phone" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="系别" prop="departmentId">
          <el-select v-model="profileForm.departmentId" placeholder="请选择系别" clearable>
            <el-option
              v-for="dept in departmentList"
              :key="dept.id"
              :label="dept.departmentName"
              :value="dept.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="profileDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="profileLoading" @click="submitProfileForm">保存</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 修改密码对话框 -->
    <el-dialog v-model="passwordDialogVisible" title="修改密码" width="500px">
      <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-width="100px">
        <el-form-item label="原密码" prop="oldPassword">
          <el-input v-model="passwordForm.oldPassword" type="password" placeholder="请输入原密码" show-password />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="passwordForm.newPassword" type="password" placeholder="请输入新密码" show-password />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="passwordForm.confirmPassword" type="password" placeholder="请确认新密码" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="passwordDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="passwordLoading" @click="submitPasswordForm">保存</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useUserStore } from "../stores/user";
import { updatePassword, updateUser, uploadAvatar as uploadAvatarAPI } from "../api/user";
import { fetchAllDepartments } from "../api/department";
import { ElMessage, ElMessageBox } from "element-plus";
import FloatingAiBot from "../components/FloatingAiBot.vue";
import BellNotification from "../components/BellNotification.vue";

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();

const userRole = computed(() =>
  ["admin", "teacher", "student"].find((r) => (userStore.userInfo?.roles || []).includes(r)) || "student"
);

// 各角色顶部菜单（纯文本链接，样式与原型 header 一致）
const MENUS = {
  admin: [
    { label: "仪表板", to: "/admin/dashboard" },
    { label: "学生用户", to: "/admin/student-user" },
    { label: "教师用户", to: "/admin/teacher-user" },
    { label: "地点管理", to: "/admin/location" },
    { label: "系别管理", to: "/admin/department" },
  ],
  teacher: [
    { label: "工作台", to: "/teacher/dashboard" },
    { label: "我的讲座", to: "/teacher/lectures" },
    { label: "预约名单", to: "/teacher/apply-list" },
    { label: "历史记录", to: "/teacher/record" },
    { label: "个人中心", to: "/teacher/profile" },
  ],
  student: [
    { label: "首页", to: "/student/dashboard" },
    { label: "讲座列表", to: "/student/lectures" },
    { label: "我的预约", to: "/student/registrations" },
    { label: "个人中心", to: "/student/profile" },
  ],
};

const menus = computed(() => MENUS[userRole.value] || []);

function isActive(item) {
  if (item.command) return false;
  const to = typeof item.to === "string" ? { path: item.to } : item.to;
  if (route.path !== to.path) return false;
  if (to.query && to.query.dialog) return route.query.dialog === to.query.dialog;
  return true;
}

const avatarText = computed(() => {
  const name = userStore.userInfo?.realName || userStore.userInfo?.username || "";
  return name ? name.charAt(0).toUpperCase() : "用";
});

function handleCommand(command) {
  if (command === "profile") {
    // 学生端已有独立个人中心页面，其余角色使用弹窗
    if (userRole.value === "student") {
      router.push("/student/profile");
    } else {
      openProfileDialog();
    }
  } else if (command === "password") {
    openPasswordDialog();
  } else if (command === "logout") {
    ElMessageBox.confirm("确定要退出登录吗？", "提示", { type: "warning" }).then(async () => {
      await userStore.logout();
      router.push("/login");
      ElMessage.success("退出成功");
    });
  }
}

// ---------- 个人信息 ----------
const profileDialogVisible = ref(false);
const profileForm = reactive({
  id: "",
  username: "",
  realName: "",
  studentTeacherId: "",
  email: "",
  phone: "",
  avatar: "",
  departmentId: undefined,
});
const profileFormRef = ref(null);
const profileLoading = ref(false);
const profileRules = {};
const departmentList = ref([]);

const openProfileDialog = () => {
  Object.assign(profileForm, userStore.userInfo);
  profileDialogVisible.value = true;
  if (departmentList.value.length === 0) loadDepartments();
};

const beforeAvatarUpload = (file) => {
  const isImage = ["image/jpeg", "image/png", "image/gif"].includes(file.type);
  const isLt2M = file.size / 1024 / 1024 < 2;
  if (!isImage) {
    ElMessage.error("头像只能是JPG/PNG/GIF格式!");
    return false;
  }
  if (!isLt2M) {
    ElMessage.error("头像大小不能超过2MB!");
    return false;
  }
  return true;
};

const uploadAvatar = async (options) => {
  const formData = new FormData();
  formData.append("file", options.file);
  try {
    profileLoading.value = true;
    const response = await uploadAvatarAPI(formData);
    if (response.code === 200) {
      options.onSuccess(response);
    } else {
      options.onError(new Error(response.message || "上传失败"));
    }
  } catch (error) {
    options.onError(error);
  } finally {
    profileLoading.value = false;
  }
};

const handleAvatarSuccess = (response) => {
  if (response.code === 200 && response.data) {
    profileForm.avatar = response.data.url;
    ElMessage.success("头像上传成功");
  }
};

const submitProfileForm = async () => {
  if (!profileFormRef.value) return;
  await profileFormRef.value.validate(async (valid) => {
    if (!valid) return;
    try {
      profileLoading.value = true;
      const response = await updateUser(profileForm);
      if (response.code === 200) {
        ElMessage.success("个人信息更新成功");
        profileDialogVisible.value = false;
        await userStore.getUserInfoAction();
      } else {
        ElMessage.error(response.message || "个人信息更新失败");
      }
    } catch (error) {
      console.error("更新个人信息失败:", error);
      ElMessage.error("更新个人信息失败");
    } finally {
      profileLoading.value = false;
    }
  });
};

const loadDepartments = async () => {
  try {
    const res = await fetchAllDepartments();
    if (res.code === 200) {
      departmentList.value = res.data || [];
    }
  } catch (error) {
    console.error("获取系别信息失败:", error);
  }
};

// ---------- 修改密码 ----------
const passwordDialogVisible = ref(false);
const passwordForm = reactive({ oldPassword: "", newPassword: "", confirmPassword: "" });
const passwordFormRef = ref(null);
const passwordLoading = ref(false);
const passwordRules = {
  oldPassword: [{ required: true, message: "请输入原密码", trigger: "blur" }],
  newPassword: [
    { required: true, message: "请输入新密码", trigger: "blur" },
    { min: 6, message: "密码长度不能少于6位", trigger: "blur" },
  ],
  confirmPassword: [
    { required: true, message: "请确认新密码", trigger: "blur" },
    {
      validator: (rule, value, callback) => {
        if (value !== passwordForm.newPassword) callback(new Error("两次输入的密码不一致"));
        else callback();
      },
      trigger: "blur",
    },
  ],
};

const openPasswordDialog = () => {
  passwordDialogVisible.value = true;
  if (passwordFormRef.value) passwordFormRef.value.resetFields();
};

const submitPasswordForm = async () => {
  if (!passwordFormRef.value) return;
  await passwordFormRef.value.validate(async (valid) => {
    if (!valid) return;
    try {
      passwordLoading.value = true;
      const response = await updatePassword(passwordForm);
      if (response.code === 200) {
        ElMessage.success("密码修改成功，请重新登录");
        passwordDialogVisible.value = false;
        await userStore.logout();
        router.push("/login");
      } else {
        ElMessage.error(response.message || "密码修改失败");
      }
    } catch (error) {
      console.error("修改密码失败:", error);
      ElMessage.error("修改密码失败");
    } finally {
      passwordLoading.value = false;
    }
  });
};
</script>

<style scoped>
.app-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

/* ---------- 顶部栏（新设计稿：全宽白底，内部 1200 容器） ---------- */
.topbar {
  background: #ffffff;
}

.header-inner {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px 0;
  border-bottom: 1px solid #f0f0f0;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

/* logo：黑色方块 S + 加粗系统名 */
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

.logo-text {
  color: #333;
}

/* 居中导航 */
.nav-center {
  display: flex;
  gap: 40px;
  color: #555;
}

.nav-link {
  color: #555;
  text-decoration: none;
  transition: color 0.2s;
  cursor: pointer;
}

.nav-link:hover,
.nav-link.active {
  color: #000;
  font-weight: 600;
}

/* ---------- 用户区 ---------- */
.user-area {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  color: #333;
}

.user-trigger {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  outline: none;
}

.avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background-color: #eee;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 600;
  color: #666;
}

.avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.user-name {
  font-size: 14px;
  color: #333;
}

/* ---------- 内容区（页面内自行使用 1200 容器） ---------- */
.page-main {
  flex: 1;
  background: #ffffff;
  min-height: calc(100vh - 80px);
}

/* ---------- 弹窗内公共样式 ---------- */
.avatar-upload-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

.avatar-uploader {
  margin-top: 10px;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>
