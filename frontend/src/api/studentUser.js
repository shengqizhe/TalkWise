// 学生用户相关API
import request from "../utils/request";

// 获取学生用户列表（支持分页和搜索）
export function fetchStudentUsers(params) {
  return request({
    url: "/student/list", // 后端接口路径，根据实际情况调整
    method: "get",
    params,
  });
}

// 编辑学生用户
export function updateStudentUser(data) {
  return request({
    url: `/student/update`,
    method: "put",
    data,
  });
}

// 删除学生用户
export function deleteStudentUser(id) {
  return request({
    url: `/student/${id}`,
    method: "delete",
  });
}

// 设置学生为老师
export function setStudentAsTeacher(id) {
  return request({
    url: `/student/setTeacher/${id}`,
    method: "post",
  });
}

export const importStudentList = "/student/import";

// 新的导入接口 - 使用JSON数据
export function importStudentListJson(data) {
  return request({
    url: "/student/import-json",
    method: "post",
    data,
  });
}

export function exportStudentList(params) {
  return request({
    url: "/student/export",
    method: "get",
    params,
    responseType: "blob", // 关键：返回文件流
  });
}

// 获取所有学生用户列表（不分页，用于导出）
export function fetchAllStudentUsers(params) {
    return request({
        url: "/student/all",
        method: "get",
        params,
    });
}
