<template>
  <div class="admin-layout">
    <!-- 顶部栏（控制台样式：系统名 + 顶部导航 + 用户区） -->
    <header class="adm-topbar">
      <div class="topbar-left">
        <span class="sys-name">知讲 · 管理后台</span>
        <nav class="nav-top">
          <router-link
            v-for="item in topMenus"
            :key="item.path"
            :to="item.path"
            class="top-link"
            :class="{ active: isActive(item.path) }"
          >
            {{ item.label }}
          </router-link>
        </nav>
      </div>
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
    </header>

    <div class="adm-wrap">
      <!-- 左侧栏 -->
      <aside class="adm-aside">
        <router-link
          v-for="item in sideMenus"
          :key="item.path"
          :to="item.path"
          class="side-item"
          :class="{ active: isActive(item.path) }"
        >
          <span>{{ item.icon }}</span>
          <span>{{ item.label }}</span>
        </router-link>
      </aside>

      <!-- 主内容 -->
      <main class="adm-main">
        <router-view />
      </main>
    </div>

    <!-- 浮动 AI 助手（管理员可用自然语言查数据） -->
    <FloatingAiBot />

    <!-- 个人信息对话框 -->
    <el-dialog v-model="profileDialogVisible" title="个人信息" width="500px">
      <el-form ref="profileFormRef" :model="profileForm" label-width="100px">
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
        <el-form-item label="用户名">
          <el-input v-model="profileForm.username" disabled />
        </el-form-item>
        <el-form-item label="真实姓名">
          <el-input v-model="profileForm.realName" placeholder="请输入真实姓名" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="profileForm.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="profileForm.phone" placeholder="请输入手机号" />
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
    <el-dialog v-model="passwordDialogVisible" title="修改密码" width="480px">
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
import { ElMessage, ElMessageBox } from "element-plus";
import BellNotification from "../components/BellNotification.vue";
import FloatingAiBot from "../components/FloatingAiBot.vue";

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();

// 顶部导航（与设计稿一致）
const topMenus = [
  { label: "讲座审核", path: "/admin/audit" },
  { label: "用户管理", path: "/admin/student-user" },
  { label: "讲师管理", path: "/admin/teacher-user" },
  { label: "分类管理", path: "/admin/category" },
  { label: "数据报表", path: "/admin/report" },
];

// 左侧栏（与设计稿一致 + 系统原有管理入口）
const sideMenus = [
  { label: "仪表盘", path: "/admin/dashboard", icon: "📊" },
  { label: "审核队列", path: "/admin/audit", icon: "📝" },
  { label: "发布管理", path: "/admin/publish", icon: "📦" },
  { label: "权限设置", path: "/admin/permission", icon: "⚙️" },
  { label: "系统通知", path: "/admin/notice", icon: "🔔" },
  { label: "地点管理", path: "/admin/location", icon: "📍" },
  { label: "系别管理", path: "/admin/department", icon: "🏛" },
];

function isActive(path) {
  return route.path === path;
}

const avatarText = computed(() => {
  const name = userStore.userInfo?.realName || userStore.userInfo?.username || "";
  return name ? name.charAt(0).toUpperCase() : "管";
});

function handleCommand(command) {
  if (command === "profile") {
    openProfileDialog();
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
const profileForm = reactive({ username: "", realName: "", email: "", phone: "", avatar: "" });
const profileFormRef = ref(null);
const profileLoading = ref(false);

const openProfileDialog = () => {
  Object.assign(profileForm, userStore.userInfo);
  profileDialogVisible.value = true;
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
  try {
    profileLoading.value = true;
    const response = await updateUser({
      ...userStore.userInfo,
      realName: profileForm.realName,
      email: profileForm.email,
      phone: profileForm.phone,
      avatar: profileForm.avatar,
    });
    if (response.code === 200) {
      ElMessage.success("个人信息更新成功");
      profileDialogVisible.value = false;
      await userStore.getUserInfoAction();
    }
  } catch (error) {
    console.error("更新个人信息失败:", error);
    ElMessage.error("更新个人信息失败");
  } finally {
    profileLoading.value = false;
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
.admin-layout {
  min-height: 100vh;
  background: #fff;
  font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
}

/* ---------- 顶部栏 ---------- */
.adm-topbar {
  height: 60px;
  padding: 0 24px 0 32px;
  border-bottom: 1px solid #eee;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  position: sticky;
  top: 0;
  z-index: 900;
}

.topbar-left {
  display: flex;
  align-items: center;
  gap: 48px;
  height: 100%;
}

.sys-name {
  font-weight: bold;
  font-size: 18px;
  color: #111;
  white-space: nowrap;
}

.nav-top {
  display: flex;
  align-items: center;
  gap: 4px;
  height: 100%;
}

.top-link {
  margin: 0 14px;
  text-decoration: none;
  color: #333;
  font-size: 15px;
  display: inline-flex;
  align-items: center;
  height: 100%;
  border-bottom: 2px solid transparent;
  box-sizing: border-box;
}

.top-link:hover {
  color: #000;
}

.top-link.active {
  color: #000;
  font-weight: 600;
  border-bottom-color: #000;
}

/* ---------- 用户区 ---------- */
.user-area {
  display: flex;
  align-items: center;
  gap: 14px;
}

.user-trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  outline: none;
  font-size: 15px;
}

.avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: #eee;
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
  color: #333;
}

/* ---------- 左侧栏 ---------- */
.adm-wrap {
  display: flex;
  min-height: calc(100vh - 60px);
}

.adm-aside {
  width: 200px;
  flex-shrink: 0;
  border-right: 1px solid #eee;
  padding: 20px 12px;
  background: #fff;
}

.side-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 14px;
  border-radius: 10px;
  margin-bottom: 6px;
  cursor: pointer;
  font-size: 14px;
  color: #333;
  text-decoration: none;
  transition: all 0.2s;
}

.side-item:hover {
  background: #f5f5f5;
}

.side-item.active {
  background: #000;
  color: #fff;
  font-weight: 500;
}

/* ---------- 主内容 ---------- */
.adm-main {
  flex: 1;
  padding: 28px 32px;
  min-width: 0;
  background: #fff;
}

/* ---------- 弹窗公共 ---------- */
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

@media (max-width: 900px) {
  .nav-top {
    display: none;
  }
}
</style>
