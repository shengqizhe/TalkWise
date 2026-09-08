import request from "../utils/request";

// 报名讲座
export function registerLecture(userId, lectureId) {
  return request({
    url: "/registration/register",
    method: "post",
    params: {
      userId,
      lectureId,
    },
  });
}

// 取消报名
export function cancelRegistration(userId, lectureId) {
  return request({
    url: "/registration/cancel",
    method: "post",
    params: {
      userId,
      lectureId,
    },
  });
}

// 检查是否已报名
export function checkRegistration(userId, lectureId) {
  return request({
    url: "/registration/check",
    method: "get",
    params: {
      userId,
      lectureId,
    },
  });
}

// 获取讲座报名列表
export function getLectureRegistrations(lectureId) {
  return request({
    url: `/registration/lecture/${lectureId}`,
    method: "get",
  });
}

// 获取用户报名列表
export function getUserRegistrations(userId, status) {
  return request({
    url: `/registration/user/${userId}`,
    method: "get",
    params: status !== undefined ? { status } : {},
  });
}

// 签到
export function checkinRegistration(registrationId) {
  return request({
    url: "/registration/checkin",
    method: "post",
    params: {
      registrationId,
    },
  });
}

// 取消签到
export function cancelCheckin(registrationId) {
  return request({
    url: "/registration/cancel-checkin",
    method: "post",
    params: {
      registrationId,
    },
  });
}
