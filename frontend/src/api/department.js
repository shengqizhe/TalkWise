// 系别相关API
import request from "../utils/request";

// 获取所有系别列表
export function fetchAllDepartments() {
  return request({
    url: "/department/list",
    method: "get",
  });
}

// 搜索系别
export function searchDepartments(keyword) {
  return request({
    url: "/department/search",
    method: "get",
    params: { keyword }
  });
}

// 根据ID获取系别
export function fetchDepartmentById(id) {
  return request({
    url: `/department/${id}`,
    method: "get",
  });
}

// 创建系别
export function createDepartment(data) {
  return request({
    url: "/department/create",
    method: "post",
    data,
  });
}

// 更新系别
export function updateDepartment(data) {
  return request({
    url: "/department/update",
    method: "put",
    data,
  });
}

// 删除系别
export function deleteDepartment(id) {
  return request({
    url: `/department/${id}`,
    method: "delete",
  });
}
