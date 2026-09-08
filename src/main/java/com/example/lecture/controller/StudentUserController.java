package com.example.lecture.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.lecture.entity.Department;
import com.example.lecture.entity.User;
import com.example.lecture.entity.UserRole;
import com.example.lecture.service.DepartmentService;
import com.example.lecture.service.UserRoleService;
import com.example.lecture.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@SaCheckLogin // 登录校验注解，保证接口需要登录
@RestController
@RequestMapping("/student")
public class StudentUserController {
    @Autowired
    private UserService userService;

    @Autowired
    private DepartmentService departmentService;

    @Autowired
    private UserRoleService userRoleService;

    /**
     * 学生名单导入接口（只允许导入学生）
     * 支持数据校验、重复检测、详细反馈
     */
    @PostMapping("/import")
    public Map<String, Object> importStudentLiscdt(@RequestParam("file") MultipartFile file) {
        Map<String, Object> result = new java.util.HashMap<>();
        int successCount = 0;
        int failCount = 0;
        java.util.List<String> failMessages = new java.util.ArrayList<>();
        try {
            // 解析Excel为User对象列表
            List<User> list = EasyExcel.read(file.getInputStream(), User.class, null).sheet().doReadSync();
            java.util.Set<String> excelIdSet = new java.util.HashSet<>(); // 用于Excel内查重
            for (User user : list) {
                // 校验必填项
                if (user.getStudentTeacherId() == null || user.getStudentTeacherId().trim().isEmpty() ||
                        user.getRealName() == null || user.getRealName().trim().isEmpty()) {
                    failCount++;
                    failMessages.add("学号或姓名为空，这些是必填项");
                    continue;
                }

                // 如果没有设置用户名，则使用学号作为用户名
                if (user.getUsername() == null || user.getUsername().trim().isEmpty()) {
                    user.setUsername(user.getStudentTeacherId());
                    System.out.println("使用学号作为用户名: " + user.getStudentTeacherId());
                }

                // 验证手机号格式
                if (user.getPhone() != null && !user.getPhone().isEmpty()) {
                    if (!user.getPhone().matches("^1[3-9]\\d{9}$")) {
                        failCount++;
                        failMessages.add("手机号格式不正确：" + user.getPhone());
                        continue;
                    }
                    // 检查手机号是否已存在
                    if (userService.getByPhone(user.getPhone()) != null) {
                        failCount++;
                        failMessages.add("手机号已存在：" + user.getPhone());
                        continue;
                    }
                }

                // Excel内查重
                if (excelIdSet.contains(user.getStudentTeacherId())) {
                    failCount++;
                    failMessages.add("Excel中存在重复学号：" + user.getStudentTeacherId());
                    continue;
                }
                excelIdSet.add(user.getStudentTeacherId());
                // 检查学号是否已存在
                if (userService.existsByStudentTeacherId(user.getStudentTeacherId())) {
                    failCount++;
                    failMessages.add("学号已存在：" + user.getStudentTeacherId());
                    continue;
                }
                // 设置默认密码
                user.setPassword(com.example.lecture.util.PasswordUtil.encode("123456"));
                // 保存到数据库
                userService.save(user);

                // 为用户添加学生角色关联
                UserRole userRole = new UserRole();
                userRole.setUserId(user.getId());
                userRole.setRoleId(2L); // 学生角色ID为2
                userRoleService.save(userRole);

                successCount++;
            }
            result.put("success", true);
            result.put("successCount", successCount);
            result.put("failCount", failCount);
            result.put("failMessages", failMessages);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "导入失败：" + e.getMessage());
        }
        return result;
    }

    /**
     * 学生名单导出接口
     * 支持按关键字和系别筛选，导出为Excel
     */
    @GetMapping("/export")
    public void exportStudentList(HttpServletResponse response,
                                  @RequestParam(required = false) String keyword,
                                  @RequestParam(required = false) Long departmentId) {
        try {
            // 构建查询条件
            QueryWrapper<User> wrapper = new QueryWrapper<>();
            if (keyword != null && !keyword.isEmpty()) {
                wrapper.and(w -> w.like("real_name", keyword).or().like("student_teacher_id", keyword));
            }
            if (departmentId != null) {
                wrapper.eq("department_id", departmentId);
            }
            List<User> list = userService.list(wrapper);

            // 设置响应头，支持中文文件名
            response.setContentType("application/vnd.ms-excel");
            response.setCharacterEncoding("utf-8");
            String fileName = URLEncoder.encode("学生名单", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");

            // 写入Excel到输出流
            EasyExcel.write(response.getOutputStream(), User.class).sheet("学生名单").doWrite(list);
        } catch (Exception e) {
            e.printStackTrace();
            // 导出失败时返回错误信息
            try {
                response.setContentType("text/plain;charset=utf-8");
                response.getWriter().write("导出失败：" + e.getMessage());
            } catch (Exception ignored) {}
        }
    }

    /**
     * 新的学生名单导入接口 - 处理JSON数据
     * 接收前端解析的Excel数据，已转换为User对象列表
     */
    @PostMapping("/import-json")
    public Map<String, Object> importStudentListJson(@RequestBody Map<String, Object> requestData) {
        Map<String, Object> result = new java.util.HashMap<>();
        int successCount = 0;
        int failCount = 0;
        java.util.List<String> failMessages = new java.util.ArrayList<>();

        try {
            // 增强日志记录，便于调试
            System.out.println("=== 开始处理学生导入请求 ====");
            System.out.println("请求数据类型: " + requestData.getClass().getName());
            System.out.println("请求数据键集: " + requestData.keySet());
            if (requestData.containsKey("users")) {
                Object usersObj = requestData.get("users");
                System.out.println("users字段类型: " + (usersObj != null ? usersObj.getClass().getName() : "null"));
                if (usersObj instanceof List) {
                    System.out.println("users列表大小: " + ((List<?>) usersObj).size());
                    if (!((List<?>) usersObj).isEmpty()) {
                        Object firstUser = ((List<?>) usersObj).get(0);
                        System.out.println("第一条用户数据类型: " + (firstUser != null ? firstUser.getClass().getName() : "null"));
                        if (firstUser instanceof Map) {
                            System.out.println("第一条用户数据键集: " + ((Map<?, ?>) firstUser).keySet());
                        }
                    }
                }
            }

            // 从请求中获取用户列表
            List<Map<String, Object>> userMapList = (List<Map<String, Object>>) requestData.get("users");
            if (userMapList == null || userMapList.isEmpty()) {
                result.put("success", false);
                result.put("message", "导入失败：没有找到有效的用户数据");
                return result;
            }

            java.util.Set<String> excelIdSet = new java.util.HashSet<>(); // 用于Excel内查重

            for (Map<String, Object> userMap : userMapList) {
                User user = new User();

                // 设置用户属性
                if (userMap.containsKey("用户名")) {
                    user.setUsername((String) userMap.get("用户名"));
                }
                if (userMap.containsKey("姓名")) {
                    user.setRealName((String) userMap.get("姓名"));
                }
                if (userMap.containsKey("学号")) {
                    user.setStudentTeacherId((String) userMap.get("学号"));
                }
                if (userMap.containsKey("邮箱")) {
                    user.setEmail((String) userMap.get("邮箱"));
                }
                if (userMap.containsKey("手机号")) {
                    String phone = (String) userMap.get("手机号");
                    // 验证手机号格式
                    if (phone != null && !phone.isEmpty()) {
                        // 严格验证中国大陆手机号格式
                        if (!phone.matches("^1[3-9]\\d{9}$")) {
                            String userName = userMap.containsKey("姓名") ? (String) userMap.get("姓名") : "未知";
                            failCount++;
                            failMessages.add("【" + userName + "】的手机号格式不符合国内要求：" + phone);
                            continue;
                        }
                        // 检查手机号是否已存在
                        if (userService.getByPhone(phone) != null) {
                            String userName = userMap.containsKey("姓名") ? (String) userMap.get("姓名") : "未知";
                            failCount++;
                            failMessages.add("【" + userName + "】的手机号已存在：" + phone);
                            continue;
                        }
                        user.setPhone(phone);
                    }
                }
                if (userMap.containsKey("系别")) {
                    // 处理系别名称，根据系别名称查找系别ID
                    Object deptNameObj = userMap.get("系别");
                    if (deptNameObj != null) {
                        String deptName = String.valueOf(deptNameObj);
                        if (!deptName.trim().isEmpty()) {
                            try {
                                // 尝试将系别名称转换为数字ID（兼容直接输入ID的情况）
                                user.setDepartmentId(Long.parseLong(deptName));
                                System.out.println("系别名称是数字，直接设置为ID: " + user.getDepartmentId());
                            } catch (NumberFormatException e) {
                                // 如果不是数字，则通过系别名称查询系别ID
                                System.out.println("系别名称不是数字，尝试查询系别: " + deptName);
                                // 通过系别名称查询系别ID
                                Department department = departmentService.getByDepartmentName(deptName);
                                if (department != null) {
                                    user.setDepartmentId(department.getId());
                                    System.out.println("根据系别名称找到系别ID: " + department.getId());
                                } else {
                                    System.out.println("未找到对应系别: " + deptName);
                                    // 添加到失败消息并继续处理下一条记录
                                    failCount++;
                                    failMessages.add("未找到系别名称对应的系别: " + deptName + "，请检查系别名称是否正确");
                                    continue;
                                }
                            }
                        }
                    }
                }

                // 校验必填项
                if (user.getStudentTeacherId() == null || user.getStudentTeacherId().trim().isEmpty() ||
                        user.getRealName() == null || user.getRealName().trim().isEmpty()) {
                    failCount++;
                    failMessages.add("学号或姓名为空，这些是必填项");
                    continue;
                }

                // Excel内查重
                if (excelIdSet.contains(user.getStudentTeacherId())) {
                    failCount++;
                    failMessages.add("Excel中存在重复学号：" + user.getStudentTeacherId());
                    continue;
                }
                excelIdSet.add(user.getStudentTeacherId());

                // 检查学号是否已存在
                if (userService.existsByStudentTeacherId(user.getStudentTeacherId())) {
                    failCount++;
                    failMessages.add("学号已存在：" + user.getStudentTeacherId());
                    continue;
                }

                // 设置默认密码
                user.setPassword(com.example.lecture.util.PasswordUtil.encode("123456"));

                // 设置默认的兴趣标签（空数组）
                user.setInterestTags("[]");

                // 保存到数据库
                userService.save(user);

                // 为用户添加学生角色关联
                UserRole userRole = new UserRole();
                userRole.setUserId(user.getId());
                userRole.setRoleId(2L); // 学生角色ID为2
                userRoleService.save(userRole);

                successCount++;
            }

            result.put("success", true);
            result.put("successCount", successCount);
            result.put("failCount", failCount);
            result.put("failMessages", failMessages);
        } catch (Exception e) {
            // 增强错误日志记录
            System.err.println("=== 学生导入异常 ====");
            System.err.println("异常类型: " + e.getClass().getName());
            System.err.println("异常消息: " + e.getMessage());
            e.printStackTrace(); // 打印详细错误堆栈

            // 提供更详细的错误信息
            result.put("success", false);
            result.put("message", "导入失败：" + e.getMessage());

            // 添加异常类型信息，便于前端区分处理
            result.put("errorType", e.getClass().getSimpleName());

            // 如果是数据格式问题，提供更具体的提示
            if (e instanceof ClassCastException) {
                result.put("message", "导入失败：数据格式不正确，请检查Excel模板是否匹配");
            } else if (e instanceof NullPointerException) {
                result.put("message", "导入失败：必填字段缺失，请确保学号和姓名等必填字段已填写");
            }
        }

        return result;
    }
}