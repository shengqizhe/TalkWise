import request from "../utils/request";

// 获取学生统计数据
export function getStudentStatistics(userId) {
  return request({
    url: `/statistics/student/${userId}`,
    method: "get",
  });
}
