import request from "../utils/request";

// 获取角色列表
export function getRoleList() {
  return request({
    url: "/role/list",
    method: "get",
  });
}

// 获取用户角色列表
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
    data: roleIds,
  });
}

// 删除用户角色
export function removeUserRoles(userId, roleIds) {
  return request({
    url: `/role/user/${userId}`,
    method: "delete",
    data: roleIds,
  });
}
