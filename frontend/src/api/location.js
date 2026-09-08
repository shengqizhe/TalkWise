import request from "../utils/request";

// 获取所有地点列表
export function getAllLocations() {
  return request({
    url: "/api/location/all",
    method: "get",
  });
}

// 根据名称查询地点
export function getLocationByName(name) {
  return request({
    url: `/api/location/name/${name}`,
    method: "get",
  });
}

// 根据类型查询地点
export function getLocationByType(type) {
  return request({
    url: `/api/location/type/${type}`,
    method: "get",
  });
}

// 创建地点
export function createLocation(data) {
  return request({
    url: "/api/location/create",
    method: "post",
    data,
  });
}

// 更新地点
export function updateLocation(data) {
  return request({
    url: "/api/location/update",
    method: "put",
    data,
  });
}

// 删除地点
export function deleteLocation(id) {
  return request({
    url: `/api/location/delete/${id}`,
    method: "delete",
  });
}