import request from "../utils/request";

// Agent 对话（多轮工具调用可能较慢，单独放宽超时）
export function agentChat(message) {
  return request({
    url: "/agent/chat",
    method: "post",
    data: { message },
    timeout: 60000,
  });
}
