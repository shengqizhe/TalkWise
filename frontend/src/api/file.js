import request from "../utils/request";

// 上传宣讲图片
export function uploadLectureImage(formData) {
  return request({
    url: "/file/upload/lecture",
    method: "post",
    data: formData,
    headers: {
      "Content-Type": "multipart/form-data",
    },
  });
}