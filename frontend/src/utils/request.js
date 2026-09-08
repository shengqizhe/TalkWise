import axios from "axios";
import {ElMessage} from "element-plus";
import router from "../router";

// 创建axios实例
const service = axios.create({
  baseURL: "/api",
  timeout: 10000, // 设置合理的超时时间，10秒足够大多数操作
  withCredentials: true, // 允许携带cookie
  maxContentLength: 10 * 1024 * 1024, // 最大内容长度10MB
  maxBodyLength: 10 * 1024 * 1024, // 最大请求体长度10MB
});

// 请求拦截器
service.interceptors.request.use(
    (config) => {
      const token = localStorage.getItem("token");
      console.log("请求携带的token:", token);

      if (token) {
        config.headers["Authorization"] = token;
      }

      // 统一设置Content-Type
      if (!config.headers["Content-Type"]) {
        config.headers["Content-Type"] = "application/json;charset=UTF-8";
      }

      // 过滤GET请求中的空参数
      if (config.method?.toLowerCase() === "get" && config.params) {
        config.params = Object.fromEntries(
            Object.entries(config.params).filter(
                ([_, value]) => value !== null && value !== undefined,
            ),
        );
      }

      // 不再手动转换data为JSON字符串，axios会自动处理
      // 移除了对POST/PUT请求的JSON.stringify操作

      return config;
    },
    (error) => {
      console.log(error);
      return Promise.reject(error);
    },
);

// 响应拦截器
// 用于防止重复错误提示的标志
let isShowingError = false;
let errorTimer = null;

service.interceptors.response.use(
    (response) => {
      const res = response.data;
      // 检查响应是否包含code字段，有些接口可能不返回code字段
      if (res.code !== undefined && res.code !== 200) {
        // 避免重复显示错误消息
        if (!isShowingError) {
          isShowingError = true;

          ElMessage({
            message: res.message || "系统错误",
            type: "error",
            duration: 5 * 1000,
            onClose: () => {
              // 消息关闭后重置标志
              isShowingError = false;
            }
          });

          // 设置超时以防止onClose未被触发
          clearTimeout(errorTimer);
          errorTimer = setTimeout(() => {
            isShowingError = false;
          }, 5000);
        }

        if (res.code === 401) {
          localStorage.removeItem("token");
          router.push("/login");
        }

        return Promise.reject(new Error(res.message || "系统错误"));
      } else {
        // 直接返回响应数据
        return res;
      }
    },
    (error) => {
      console.log("请求错误:", error);

      // 增强错误日志，打印更多信息
      console.error('错误详情:', {
        message: error.message,
        stack: error.stack,
        config: error.config,
        response: error.response,
        request: error.request
      });

      // 默认错误消息
      let errorMessage = "系统错误，请稍后重试";
      let errorDetail = "";

      if (error.response) {
        const {status, data} = error.response;

        // 根据状态码处理不同错误
        if (status === 400) {
          errorMessage = "请求参数错误";
        } else if (status === 401) {
          errorMessage = "未授权，请重新登录";
          localStorage.removeItem("token");
          router.push("/login");
          return Promise.reject(error); // 直接返回，避免显示多个错误消息
        } else if (status === 403) {
          errorMessage = "没有权限执行此操作";
        } else if (status === 404) {
          errorMessage = "请求地址不存在，请检查API路径";
        } else if (status === 413) {
          errorMessage = "请求内容过大，请减小数据量";
        } else if (status === 429) {
          errorMessage = "请求过于频繁，请稍后再试";
        } else if (status >= 500) {
          errorMessage = "服务器内部错误，请联系管理员";
        }

        // 尝试从响应中提取更详细的错误信息
        if (data) {
          if (typeof data === 'string') {
            errorDetail = data;
          } else if (data.message) {
            errorDetail = data.message;
          } else if (data.error) {
            errorDetail = data.error;
          }
        }
      } else if (error.request) {
        // 请求已发送，但没有收到响应
        errorMessage = "服务器未响应，请检查网络连接或服务器状态";
      } else if (error.message) {
        // 根据错误消息类型处理
        if (error.message.includes("Network Error")) {
          errorMessage = "网络错误，请检查网络连接";
        } else if (error.message.includes("timeout")) {
          errorMessage = "请求超时，服务器可能正忙或处理大量数据";
        } else {
          errorMessage = error.message;
        }
      }

      // 避免重复显示错误消息
      if (!isShowingError) {
        isShowingError = true;

        // 显示错误消息，如果有详细信息则一并显示
        if (errorDetail) {
          ElMessage({
            type: "error",
            dangerouslyUseHTMLString: true,
            message: `<strong>${errorMessage}</strong><br/>${errorDetail}`,
            duration: 8000,
            showClose: true,
            onClose: () => {
              isShowingError = false;
            }
          });
        } else {
          ElMessage({
            message: errorMessage,
            type: "error",
            duration: 5 * 1000,
            showClose: true,
            onClose: () => {
              isShowingError = false;
            }
          });
        }

        // 设置超时以防止onClose未被触发
        clearTimeout(errorTimer);
        errorTimer = setTimeout(() => {
          isShowingError = false;
        }, 8000);
      }

      return Promise.reject(error);
    },
);

export default service;
