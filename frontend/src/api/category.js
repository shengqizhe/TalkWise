import request from "../utils/request";

// 获取所有讲座分类
export function getAllCategories() {
  return request({
    url: "/lecture/category/list",
    method: "get",
  });
}
