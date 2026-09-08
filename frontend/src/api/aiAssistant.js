// frontend/src/api/aiAssistant.js
import request from '../utils/request';

// AI助手对话
export function askAiAssistant(data) {
  return request({
    url: "/api/ai/assistant",
    method: "post",
    data,
  });
}

// 获取用户已报名的讲座
export function getUserRegistrations(userId) {
  return request({
    url: `/api/ai/registrations/${userId}`,
    method: "get",
  });
}

// 取消用户报名
export function cancelUserRegistration(userId, lectureId) {
  return request({
    url: "/api/ai/cancel-registration",
    method: "post",
    params: {
      userId,
      lectureId,
    },
  });
}