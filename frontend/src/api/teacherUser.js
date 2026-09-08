// 教师用户相关API
import request from "../utils/request";

// 获取教师用户列表（支持分页和搜索）
export function fetchTeacherUsers(params) {
  return request({
    url: "/teacher/list", // 后端接口路径，根据实际情况调整
    method: "get",
    params,
  });
}

// 新增教师用户
export function addTeacherUser(data) {
  return request({
    url: "/teacher/add",
    method: "post",
    data,
  });
}

// 编辑教师用户
export function updateTeacherUser(data) {
  return request({
    url: `/teacher/update`,
    method: "put",
    data,
  });
}

// 删除教师用户
export function deleteTeacherUser(id) {
  return request({
    url: `/teacher/${id}`,
    method: "delete",
  });
}

// 重置教师密码
export function resetTeacherPassword(id) {
  return request({
    url: `/teacher/resetPassword/${id}`,
    method: "post",
  });
}

// 导入教师列表的URL
export const importTeacherList = "/teacher/import";

// 使用JSON数据导入教师列表
export function importTeacherListJson(data) {
    return request({
        url: "/teacher/import-json",
        method: "post",
        data,
    });
}

// 获取所有教师用户列表（不分页，用于导出）
export function fetchAllTeacherUsers(params) {
    return request({
        url: "/teacher/all",
        method: "get",
        params,
    });
}

// 导出教师列表
export function exportTeacherList(params) {
    return request({
        url: "/teacher/export",
        method: "get",
        params,
        responseType: "blob", // 关键：返回文件流
    });
}
