import request from "../utils/request";

// 用户登录
export function login(data) {
  return request({
    url: "/auth/login",
    method: "post",
    data,
  });
}

// 用户注册
export function register(data) {
  return request({
    url: "/auth/register",
    method: "post",
    data,
  });
}

// 重置密码
export function resetPassword(data) {
  return request({
    url: "/auth/reset-password",
    method: "post",
    data,
  });
}

// 获取用户列表
export function getUserList() {
  return request({
    url: "/user/list",
    method: "get",
  });
}

// 分页查询用户
export function getUserPage(params) {
  return request({
    url: "/user/page",
    method: "post",
    data: params,
  });
}

// 根据系别获取用户列表
export function getUsersByDepartment(departmentId) {
  return request({
    url: `/user/department/${departmentId}`,
    method: "get",
  });
}

// 更新用户信息
export function updateUser(data) {
  return request({
    url: `/user/update`,
    method: "put",
    data,
  });
}

// 删除用户
export function deleteUser(id) {
  return request({
    url: `/user/${id}`,
    method: "delete",
  });
}

// 获取用户角色
export function getUserRoles(userId) {
  return request({
    url: `/role/user/${userId}`,
    method: "get",
  });
}

// 分配用户角色
export function assignUserRoles(userId, roleIds) {
  return request({
    url: `/role/user/${userId}`,
    method: "post",
    data: { roleIds },
  });
}

// 获取用户信息
export function getUserInfo() {
  return request({
    url: "/user/info",
    method: "get",
  });
}

// 修改密码
export function updatePassword(data) {
  return request({
    url: "/user/password",
    method: "put",
    data,
  });
}

// 退出登录
export function logout() {
  return request({
    url: "/user/logout",
    method: "post",
  });
}

// 上传头像
export function uploadAvatar(formData) {
  return request({
    url: "/user/upload/avatar",
    method: "post",
    data: formData,
    headers: {
      "Content-Type": "multipart/form-data",
    },
  });
}
