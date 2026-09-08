import request from "../utils/request";

// 获取权限列表
export function getPermissionList() {
  return request({
    url: "/permission/list",
    method: "get",
  });
}

// 获取角色权限列表
export function getRolePermissions(roleId) {
  return request({
    url: `/permission/role/${roleId}`,
    method: "get",
  });
}

// 获取用户权限列表
export function getUserPermissions(userId) {
  return request({
    url: `/permission/user/${userId}`,
    method: "get",
  });
}

// 分配角色权限
export function assignRolePermissions(roleId, permissionIds) {
  return request({
    url: `/permission/role/${roleId}`,
    method: "post",
    data: permissionIds,
  });
}

// 删除角色权限
export function removeRolePermissions(roleId, permissionIds) {
  return request({
    url: `/permission/role/${roleId}`,
    method: "delete",
    data: permissionIds,
  });
}
