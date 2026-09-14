// Agent 主动任务相关API
import request from "../utils/request";

// 分页查询我的任务
export function getAgentTaskPage(params) {
  return request({
    url: "/agent/tasks",
    method: "get",
    params,
  });
}

// 查询任务详情
export function getAgentTask(taskId) {
  return request({
    url: `/agent/tasks/${taskId}`,
    method: "get",
  });
}

// 创建任务
export function createAgentTask(data) {
  return request({
    url: "/agent/tasks",
    method: "post",
    data,
  });
}

// 取消任务
export function cancelAgentTask(taskId) {
  return request({
    url: `/agent/tasks/${taskId}/cancel`,
    method: "post",
  });
}

// 重试失败任务
export function retryAgentTask(taskId) {
  return request({
    url: `/agent/tasks/${taskId}/retry`,
    method: "post",
  });
}
