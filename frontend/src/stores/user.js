import { defineStore } from "pinia";
import { ref } from "vue";
import { login, getUserInfo } from "../api/user";
import webSocketService from "../utils/websocket";

export const useUserStore = defineStore("user", () => {
  const token = ref(localStorage.getItem("token") || "");
  const userInfo = ref(null);

  // 登录
  async function loginAction(username, password) {
    try {
      const res = await login(username, password);
      token.value = res.data;
      localStorage.setItem("token", res.data);
      await getUserInfoAction();
      
      // 登录成功后初始化WebSocket连接
      if (userInfo.value) {
        try {
          await webSocketService.connect();
          console.log('WebSocket连接已建立');
        } catch (error) {
          console.error('WebSocket连接失败:', error);
        }
      }
      
      return true;
    } catch (error) {
      return false;
    }
  }

  // 获取用户信息
  async function getUserInfoAction() {
    try {
      const res = await getUserInfo();
      userInfo.value = res.data;
      return true;
    } catch (error) {
      return false;
    }
  }

  // 退出登录
  function logout() {
    token.value = "";
    userInfo.value = null;
    localStorage.removeItem("token");
    
    // 断开WebSocket连接
    webSocketService.disconnect();
    console.log('WebSocket连接已断开');
  }

  return {
    token,
    userInfo,
    loginAction,
    getUserInfoAction,
    logout,
  };
});
