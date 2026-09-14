// 学校画像相关API
import request from "../utils/request";

// 获取所有学校画像
export function getAllSchoolProfiles() {
  return request({
    url: "/school-profile/all",
    method: "get",
  });
}

// 按学校名称查询
export function getSchoolProfileByName(schoolName) {
  return request({
    url: `/school-profile/school/${encodeURIComponent(schoolName)}`,
    method: "get",
  });
}

// 新增学校画像
export function createSchoolProfile(data) {
  return request({
    url: "/school-profile",
    method: "post",
    data,
  });
}

// 更新学校画像（仅学校名称与画像内容；容量范围由地点增删自动重算）
export function updateSchoolProfile(id, data) {
  return request({
    url: `/school-profile/${id}`,
    method: "put",
    data,
  });
}

// 删除学校画像
export function deleteSchoolProfile(id) {
  return request({
    url: `/school-profile/${id}`,
    method: "delete",
  });
}
