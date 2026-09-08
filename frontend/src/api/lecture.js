import request from "../utils/request";

// 分页查询讲座列表
export function getLecturePage(params) {
    // 如果没有提供lastUpdatedTimestamp，添加当前时间戳
    if (!params.lastUpdatedTimestamp && params.checkDataChange) {
        params.lastUpdatedTimestamp = Date.now();
    }
  
  return request({
    url: "/lecture/page",
    method: "get",
    params,
  });
}

// 获取讲座详情
export function getLectureById(id) {
  return request({
    url: `/lecture/${id}`,
    method: "get",
  });
}

// 发布讲座
export function publishLecture(data) {
  return request({
    url: "/lecture/publish",
    method: "post",
    data,
  });
}

// 更新讲座
export function updateLecture(data) {
  return request({
    url: "/lecture/update",
    method: "put",
    data,
  });
}

// 取消讲座
export function cancelLecture(id, reason) {
  return request({
    url: `/lecture/cancel/${id}`,
    method: "put",
    params: { reason },
  });
}

// 更新讲座状态
export function updateLectureStatus(id, status) {
  return request({
    url: `/lecture/status/${id}`,
    method: "put",
    params: { status },
  });
}

// 更新讲座发布状态
export function updateLecturePublishStatus(id, publishStatus) {
  return request({
    url: `/lecture/publish-status/${id}`,
    method: "put",
    params: { publishStatus },
  });
}

// 获取教师的讲座列表
export function getTeacherLectures(teacherId, params) {
  return request({
    url: `/lecture/teacher/${teacherId}`,
    method: "get",
    params,
  });
}

// 获取讲座统计信息
export function getLectureStats(teacherId) {
  return request({
    url: `/lecture/stats/${teacherId}`,
    method: "get",
  });
}

// 学生端分页查询讲座列表（只返回已发布的讲座）
export function getStudentLecturePage(params) {
    // 如果没有提供lastUpdatedTimestamp，添加当前时间戳
    if (!params.lastUpdatedTimestamp && params.checkDataChange) {
        params.lastUpdatedTimestamp = Date.now();
    }
  
  return request({
    url: "/lecture/student/page",
    method: "get",
    params,
  });
}
