import request from "../utils/request";

// 分页查询评价列表
export function getEvaluationPage(params) {
  return request({
    url: "/evaluation/page",
    method: "get",
    params: {
      ...params,
      sort: params.sort || undefined,
      order: params.order || undefined,
    },
  });
}

// 获取讲座的评价列表
export function getEvaluationsByLectureId(lectureId) {
  return request({
    url: `/evaluation/lecture/${lectureId}`,
    method: "get",
  });
}

// 获取用户的评价列表
export function getEvaluationsByUserId(userId) {
  return request({
    url: `/evaluation/user/${userId}`,
    method: "get",
  });
}

// 获取讲座的平均评分
export function getAverageScore(lectureId) {
  return request({
    url: `/evaluation/lecture/${lectureId}/average-score`,
    method: "get",
  });
}

// 获取讲座的评价统计
export function getEvaluationStats(lectureId) {
  return request({
    url: `/evaluation/lecture/${lectureId}/stats`,
    method: "get",
  });
}

// 创建评价
export function createEvaluation(data) {
  return request({
    url: "/evaluation/create",
    method: "post",
    data,
  });
}

// 更新评价
export function updateEvaluation(data) {
  return request({
    url: "/evaluation/update",
    method: "put",
    data,
  });
}

// 删除评价
export function deleteEvaluation(id) {
  return request({
    url: `/evaluation/${id}`,
    method: "delete",
  });
}

// 获取评价详情
export function getEvaluationById(id) {
  return request({
    url: `/evaluation/${id}`,
    method: "get",
  });
}
