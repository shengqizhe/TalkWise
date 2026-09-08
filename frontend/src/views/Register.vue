<template>
  <div class="register-container">
    <div class="main-box">
      <div class="left-illustration"></div>
      <div class="right-form">
        <div class="form-card">
          <h2>用户注册</h2>
          <form @submit.prevent="handleSubmit">
            <div class="form-group">
              <input
                  type="text"
                  v-model="form.username"
                  required
                  placeholder="请输入用户名"
                  @blur="validateField('username')"
              />
              <label>用户名</label>
              <div v-if="errors.username" class="error-message">
                {{ errors.username }}
              </div>
            </div>
            <div class="form-group">
              <input
                  type="email"
                  v-model="form.email"
                  required
                  placeholder="请输入邮箱地址"
                  @blur="validateField('email')"
              />
              <label>邮箱</label>
              <div v-if="errors.email" class="error-message">
                {{ errors.email }}
              </div>
            </div>
            <div class="form-group">
              <input
                  :type="passwordVisible ? 'text' : 'password'"
                  v-model="form.password"
                  required
                  placeholder="请输入密码"
                  @blur="validateField('password')"
              />
              <label>密码</label>
              <span class="password-toggle" @click="togglePassword">
                <img
                    :src="
                    passwordVisible ? '/src/img/眼睛.png' : '/src/img/闭眼.png'
                  "
                    alt="toggle password"
                />
              </span>
              <div v-if="errors.password" class="error-message">
                {{ errors.password }}
              </div>
            </div>
            <div class="form-group">
              <input
                  :type="confirmPasswordVisible ? 'text' : 'password'"
                  v-model="form.confirmPassword"
                  required
                  placeholder="请再次输入密码"
                  @blur="validateField('confirmPassword')"
              />
              <label>确认密码</label>
              <span class="password-toggle" @click="toggleConfirmPassword">
                <img
                    :src="
                    confirmPasswordVisible
                      ? '/src/img/眼睛.png'
                      : '/src/img/闭眼.png'
                  "
                    alt="toggle password"
                />
              </span>
              <div v-if="errors.confirmPassword" class="error-message">
                {{ errors.confirmPassword }}
              </div>
            </div>
            <button type="submit" class="btn" :disabled="loading">
              {{ loading ? "注册中..." : "注册用户账号" }}
            </button>
            <div class="form-links">
              <button
                  type="button"
                  class="switch-btn"
                  @click="$router.push('/login')"
              >
                已有账号？去登录
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { register } from "../api/user";

const router = useRouter();

// 注册表单
const form = reactive({
  username: "",
  email: "",
  password: "",
  confirmPassword: "",
});
const loading = ref(false);
const passwordVisible = ref(false);
const confirmPasswordVisible = ref(false);
const errors = reactive({
  username: "",
  email: "",
  password: "",
  confirmPassword: "",
});

// 密码可见性切换
const togglePassword = () => {
  passwordVisible.value = !passwordVisible.value;
};

const toggleConfirmPassword = () => {
  confirmPasswordVisible.value = !confirmPasswordVisible.value;
};

// 表单验证
const validateField = (field) => {
  errors[field] = "";

  if (field === "username") {
    if (!form.username) {
      errors.username = "请输入用户名";
    } else if (form.username.length < 3 || form.username.length > 20) {
      errors.username = "用户名长度在 3 到 20 个字符";
    }
  }

  if (field === "email") {
    if (!form.email) {
      errors.email = "请输入邮箱地址";
    } else {
      const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
      if (!emailRegex.test(form.email)) {
        errors.email = "请输入正确的邮箱格式";
      }
    }
  }

  if (field === "password") {
    if (!form.password) {
      errors.password = "请输入密码";
    } else if (form.password.length < 6) {
      errors.password = "密码长度不能小于6位";
    }
  }

  if (field === "confirmPassword") {
    if (!form.confirmPassword) {
      errors.confirmPassword = "请再次输入密码";
    } else if (form.confirmPassword !== form.password) {
      errors.confirmPassword = "两次输入密码不一致";
    }
  }
};

// 验证整个表单
const validateForm = () => {
  validateField("username");
  validateField("email");
  validateField("password");
  validateField("confirmPassword");
  return (
      !errors.username &&
      !errors.email &&
      !errors.password &&
      !errors.confirmPassword
  );
};

// 提交注册
const handleSubmit = async () => {
  if (!validateForm()) {
    return;
  }

  loading.value = true;
  try {
    await register({
      username: form.username,
      email: form.email,
      password: form.password,
    });
    ElMessage.success("学生账号注册成功！");
    router.push("/login");
  } catch (error) {
    console.error("注册失败:", error);
    ElMessage.error(error.message || "注册失败");
  } finally {
    loading.value = false;
  }
};
</script>

<style scoped>
.register-container {
  margin: 0;
  padding: 0;
  min-height: 100vh;
  background: #fafafa;
  font-family: "Microsoft YaHei", sans-serif;
  display: flex;
  justify-content: center;
  align-items: center;
}

.main-box {
  width: 900px;
  height: 580px;
  background: #fff;
  border-radius: 18px;
  box-shadow: 0 8px 32px 0 rgba(0, 0, 0, 0.08);
  display: flex;
  overflow: hidden;
  opacity: 1;
  transition: opacity 0.4s;
}

.left-illustration {
  width: 50%;
  height: 100%;
  position: relative;
  background: url("/src/img/大连东软.png") center/cover
  no-repeat;
  display: flex;
  align-items: center;
  justify-content: center;
}

.left-illustration::before {
  content: "";
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.35);
}

.left-illustration::after {
  content: "知讲 TalkWise";
  position: absolute;
  bottom: 60px;
  left: 50%;
  transform: translateX(-50%);
  color: white;
  font-size: 18px;
  font-weight: 600;
  text-shadow: 0 2px 4px rgba(0, 0, 0, 0.3);
  z-index: 1;
}

.right-form {
  width: 50%;
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  background: #ffffff;
  position: relative;
}

.form-card {
  width: 340px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 4px 16px 0 rgba(0, 0, 0, 0.04);
  padding: 38px 32px 32px 32px;
  display: flex;
  flex-direction: column;
  align-items: stretch;
}

.form-card h2 {
  margin: 0 0 28px 0;
  color: #222;
  font-size: 24px;
  font-weight: 600;
  text-align: center;
}

.form-group {
  margin-bottom: 40px;
  position: relative;
}

.form-group input {
  width: 100%;
  padding: 12px 30px 12px 0;
  font-size: 15px;
  color: #222;
  border: none;
  border-bottom: 1.5px solid #ddd;
  outline: none;
  background: transparent;
  transition: border-color 0.3s;
  box-sizing: border-box;
}

.form-group input::placeholder {
  color: #bbb;
  font-size: 14px;
  opacity: 0.8;
  transition: opacity 0.3s;
}

.form-group input:focus::placeholder {
  opacity: 0.5;
}

.password-toggle {
  position: absolute;
  right: 5px;
  top: 12px;
  cursor: pointer;
  user-select: none;
  transition: opacity 0.3s;
  width: 20px;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.password-toggle img {
  width: 18px;
  height: 18px;
  object-fit: contain;
}

.password-toggle:hover {
  opacity: 0.7;
}

.form-group input:focus {
  border-bottom: 1.5px solid #000;
}

.form-group label {
  position: absolute;
  top: -16px;
  left: 0;
  font-size: 13px;
  color: #000;
  pointer-events: none;
  transition: all 0.5s cubic-bezier(0.25, 0.8, 0.25, 1);
  opacity: 1;
  transform: translateX(0) scale(0.9);
  background: #fff;
  padding: 0 4px;
  animation: slideInFromLeft 0.8s ease-out;
}

@keyframes slideInFromLeft {
  0% {
    opacity: 0;
    transform: translateX(-30px) scale(0.9);
  }
  100% {
    opacity: 1;
    transform: translateX(0) scale(0.9);
  }
}

.form-group input:focus ~ label,
.form-group input:valid ~ label {
  top: -16px;
  left: 0;
  color: #000;
  font-size: 13px;
  background: #fff;
  padding: 0 4px;
  opacity: 1;
  transform: translateX(0) scale(0.9);
}

.error-message {
  color: #e74c3c;
  font-size: 12px;
  margin-top: 5px;
  position: absolute;
  bottom: -22px;
  left: 0;
  z-index: 10;
  background: #fff;
  padding: 2px 0;
  width: 100%;
}

.btn {
  width: 100%;
  padding: 13px 0;
  background: linear-gradient(90deg, #000 0%, #333 100%);
  border: none;
  border-radius: 24px;
  color: #fff;
  font-size: 16px;
  font-weight: bold;
  cursor: pointer;
  margin-top: 10px;
  margin-bottom: 8px;
  transition: all 0.3s;
  box-shadow: 0 4px 15px 0 rgba(0, 0, 0, 0.15);
}

.btn:hover:not(:disabled) {
  background: linear-gradient(90deg, #333 0%, #444 100%);
  transform: translateY(-2px);
  box-shadow: 0 6px 20px 0 rgba(0, 0, 0, 0.2);
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  transform: none;
}

.form-links {
  display: flex;
  justify-content: center;
  margin-top: 15px;
}

.switch-btn {
  background: transparent;
  color: #000;
  border: none;
  font-size: 14px;
  cursor: pointer;
  text-align: center;
  transition: color 0.3s;
  padding: 5px;
}

.switch-btn:hover {
  color: #333;
  text-decoration: underline;
}

@media (max-width: 1000px) {
  .main-box {
    width: 98vw;
    height: 98vw;
    min-height: 580px;
    min-width: 340px;
  }
}

@media (max-width: 768px) {
  .main-box {
    flex-direction: column;
    width: 98vw;
    height: auto;
    min-height: 0;
  }
  .left-illustration,
  .right-form {
    width: 100%;
    height: 220px;
  }
  .right-form {
    height: auto;
    padding: 20px 0;
  }
  .left-illustration::after {
    bottom: 20px;
    font-size: 16px;
  }
}
</style>
