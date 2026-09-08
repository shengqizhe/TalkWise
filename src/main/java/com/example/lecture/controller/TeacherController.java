package com.example.lecture.controller;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.lecture.common.api.Result;
import com.example.lecture.entity.Department;
import com.example.lecture.entity.Role;
import com.example.lecture.entity.User;
import com.example.lecture.entity.UserRole;
import com.example.lecture.service.DepartmentService;
import com.example.lecture.service.RoleService;
import com.example.lecture.service.UserRoleService;
import com.example.lecture.service.UserService;
import com.example.lecture.util.PasswordUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 教师用户管理Controller
 */
@Tag(name = "教师用户管理", description = "教师用户相关接口")
@RestController
@RequestMapping("/teacher")
//@SaCheckRole("admin")
public class TeacherController {
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private RoleService roleService;
    
    @Autowired
    private UserRoleService userRoleService;

    @Autowired
    private DepartmentService departmentService;
    
    // 默认密码常量
    private static final String DEFAULT_PASSWORD = "123456";
    
    @Operation(summary = "获取教师用户列表")
    @GetMapping("/list")
    public Result<Page<User>> list(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "10") Integer size) {
        
        // 查询教师角色ID
        Role teacherRole = roleService.getOne(new LambdaQueryWrapper<Role>()
                .eq(Role::getRoleName, "teacher"));
        
        if (teacherRole == null) {
            return Result.success(new Page<>());
        }
        
        // 查询具有教师角色的用户ID列表
        List<Long> teacherUserIds = userRoleService.list(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getRoleId, teacherRole.getId()))
                .stream()
                .map(UserRole::getUserId)
                .toList();
        
        if (teacherUserIds.isEmpty()) {
            return Result.success(new Page<>());
        }
        
        // 构建查询条件
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<User>()
                .in(User::getId, teacherUserIds);
        
        // 添加关键字搜索条件
        if (StringUtils.hasText(keyword)) {
            // 优化模糊查询，添加%以确保更好的匹配
            String likeKeyword = "%" + keyword + "%";
            queryWrapper.and(wrapper -> wrapper
                    .like(User::getUsername, likeKeyword)
                    .or()
                    .like(User::getRealName, likeKeyword)
                    .or()
                    .like(User::getStudentTeacherId, likeKeyword));
        }
        
        // 分页查询
        Page<User> userPage = userService.page(new Page<>(page, size), queryWrapper);
        
        return Result.success(userPage);
    }
    
    @Operation(summary = "添加教师用户")
    @PostMapping("/add")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> add(@RequestBody User user) {
        // 如果密码为空，设置默认密码
        if (user.getPassword() == null || user.getPassword().isEmpty()) {
            user.setPassword(DEFAULT_PASSWORD);
        }
        
        // 设置密码加密
        user.setPassword(PasswordUtil.encode(user.getPassword()));
        
        // 自动生成教师工号
        if (user.getStudentTeacherId() == null || user.getStudentTeacherId().isEmpty()) {
            long sevenDigitTimestamp = System.currentTimeMillis() % 10000000L;
            String formattedTimestamp = String.format("%07d", sevenDigitTimestamp);
            user.setStudentTeacherId("T" + formattedTimestamp);
        }
        
        // 保存用户
        userService.save(user);
        
        // 查询教师角色
        Role teacherRole = roleService.getOne(new LambdaQueryWrapper<Role>()
                .eq(Role::getRoleName, "teacher"));
        
        if (teacherRole != null) {
            // 分配教师角色
            UserRole userRole = new UserRole();
            userRole.setUserId(user.getId());
            userRole.setRoleId(teacherRole.getId());
            userRoleService.save(userRole);
        }
        
        return Result.success();
    }
    
    @Operation(summary = "更新教师用户")
    @PutMapping("/update")
    public Result<Void> update(@RequestBody User user) {
        // 不更新密码字段
        user.setPassword(null);
        
        // 更新用户
        userService.updateById(user);
        
        return Result.success();
    }
    
    @Operation(summary = "删除教师用户")
    @DeleteMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> delete(@PathVariable Long id) {
        // 删除用户
        userService.removeById(id);
        
        // 删除用户角色关联
        userRoleService.remove(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getUserId, id));
        
        return Result.success();
    }
    
    @Operation(summary = "重置教师密码")
    @PostMapping("/resetPassword/{id}")
    public Result<Void> resetPassword(@PathVariable Long id) {
        // 获取用户
        User user = userService.getById(id);
        if (user == null) {
            return Result.failed("用户不存在");
        }
        
        // 重置密码为默认密码
        user.setPassword(PasswordUtil.encode(DEFAULT_PASSWORD));
        userService.updateById(user);
        
        return Result.success();
    }

    @Operation(summary = "获取所有教师用户列表（不分页，用于导出）")
    @GetMapping("/all")
    public Result<List<User>> getAllTeachers(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "departmentId", required = false) Long departmentId) {

        // 查询教师角色ID
        Role teacherRole = roleService.getOne(new LambdaQueryWrapper<Role>()
                .eq(Role::getRoleName, "teacher"));

        if (teacherRole == null) {
            return Result.success(List.of());
        }

        // 查询具有教师角色的用户ID列表
        List<Long> teacherUserIds = userRoleService.list(new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getRoleId, teacherRole.getId()))
                .stream()
                .map(UserRole::getUserId)
                .toList();

        if (teacherUserIds.isEmpty()) {
            return Result.success(List.of());
        }

        // 构建查询条件
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<User>()
                .in(User::getId, teacherUserIds);

        // 添加关键字搜索条件
        if (StringUtils.hasText(keyword)) {
            String likeKeyword = "%" + keyword + "%";
            queryWrapper.and(wrapper -> wrapper
                    .like(User::getUsername, likeKeyword)
                    .or()
                    .like(User::getRealName, likeKeyword)
                    .or()
                    .like(User::getStudentTeacherId, likeKeyword));
        }

        // 添加系别筛选条件
        if (departmentId != null) {
            queryWrapper.eq(User::getDepartmentId, departmentId);
        }

        // 查询所有符合条件的教师
        List<User> teacherList = userService.list(queryWrapper);

        return Result.success(teacherList);
    }

    @Operation(summary = "导出教师用户列表")
    @GetMapping("/export")
    public void exportTeacherList(HttpServletResponse response,
                                  @RequestParam(required = false) String keyword,
                                  @RequestParam(required = false) Long departmentId) {
        try {
            // 查询教师角色ID
            Role teacherRole = roleService.getOne(new LambdaQueryWrapper<Role>()
                    .eq(Role::getRoleName, "teacher"));

            if (teacherRole == null) {
                response.setContentType("text/plain;charset=utf-8");
                response.getWriter().write("导出失败：未找到教师角色");
                return;
            }

            // 查询具有教师角色的用户ID列表
            List<Long> teacherUserIds = userRoleService.list(new LambdaQueryWrapper<UserRole>()
                            .eq(UserRole::getRoleId, teacherRole.getId()))
                    .stream()
                    .map(UserRole::getUserId)
                    .toList();

            if (teacherUserIds.isEmpty()) {
                response.setContentType("text/plain;charset=utf-8");
                response.getWriter().write("导出失败：没有教师用户数据");
                return;
            }

            // 构建查询条件
            QueryWrapper<User> wrapper = new QueryWrapper<>();
            wrapper.in("id", teacherUserIds);

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
            String fileName = URLEncoder.encode("教师名单", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");

            // 写入Excel到输出流
            EasyExcel.write(response.getOutputStream(), User.class).sheet("教师名单").doWrite(list);
        } catch (Exception e) {
            e.printStackTrace();
            // 导出失败时返回错误信息
            try {
                response.setContentType("text/plain;charset=utf-8");
                response.getWriter().write("导出失败：" + e.getMessage());
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 导入教师名单 - 处理Excel文件上传
     */
    @Operation(summary = "导入教师名单（Excel文件）")
    @PostMapping("/import")
    public Map<String, Object> importTeacherList(@RequestParam("file") MultipartFile file) {
        Map<String, Object> result = new java.util.HashMap<>();
        int successCount = 0;
        int failCount = 0;
        java.util.List<String> failMessages = new java.util.ArrayList<>();
        try {
            // 解析Excel为User对象列表
            List<User> list = EasyExcel.read(file.getInputStream(), User.class, null).sheet().doReadSync();
            java.util.Set<String> excelIdSet = new java.util.HashSet<>(); // 用于Excel内查重

            // 查询教师角色ID
            Role teacherRole = roleService.getOne(new LambdaQueryWrapper<Role>()
                    .eq(Role::getRoleName, "teacher"));

            if (teacherRole == null) {
                result.put("success", false);
                result.put("message", "导入失败：未找到教师角色");
                return result;
            }

            for (User user : list) {
                // 校验必填项
                if ((user.getStudentTeacherId() == null || user.getStudentTeacherId().trim().isEmpty()) &&
                        (user.getRealName() == null || user.getRealName().trim().isEmpty())) {
                    failCount++;
                    failMessages.add("教师编号或姓名为空，这些是必填项");
                    continue;
                }

                // 如果没有提供教师编号，则自动生成一个
                if (user.getStudentTeacherId() == null || user.getStudentTeacherId().trim().isEmpty()) {
                    // 生成教师编号：T + 时间戳后7位
                    String timestamp = String.valueOf(System.currentTimeMillis());
                    String teacherId = "T" + timestamp.substring(timestamp.length() - 7);
                    user.setStudentTeacherId(teacherId);
                }

                // Excel内查重
                if (excelIdSet.contains(user.getStudentTeacherId())) {
                    failCount++;
                    failMessages.add("Excel中存在重复教师编号：" + user.getStudentTeacherId());
                    continue;
                }
                excelIdSet.add(user.getStudentTeacherId());

                // 检查教师编号是否已存在
                if (userService.existsByStudentTeacherId(user.getStudentTeacherId())) {
                    failCount++;
                    failMessages.add("教师编号已存在：" + user.getStudentTeacherId());
                    continue;
                }

                // 如果没有提供用户名，则使用教师编号作为用户名
                if (user.getUsername() == null || user.getUsername().trim().isEmpty()) {
                    user.setUsername(user.getStudentTeacherId());
                }

                // 检查用户名是否已存在
                if (userService.getByUsername(user.getUsername()) != null) {
                    failCount++;
                    failMessages.add("用户名已存在：" + user.getUsername());
                    continue;
                }

                // 设置默认密码
                user.setPassword(PasswordUtil.encode(DEFAULT_PASSWORD));

                // 设置默认的兴趣标签（空数组）
                user.setInterestTags("[]");

                // 保存到数据库
                userService.save(user);

                // 为用户添加教师角色关联
                UserRole userRole = new UserRole();
                userRole.setUserId(user.getId());
                userRole.setRoleId(teacherRole.getId()); // 教师角色ID
                userRoleService.save(userRole);

                successCount++;
            }

            result.put("success", true);
            result.put("successCount", successCount);
            result.put("failCount", failCount);
            result.put("failMessages", failMessages);
        } catch (Exception e) {
            e.printStackTrace();
            result.put("success", false);
            result.put("message", "导入失败：" + e.getMessage());
        }
        return result;
    }

    /**
     * 新的教师名单导入接口 - 处理JSON数据
     * 接收前端解析的Excel数据，已转换为User对象列表
     */
    @Operation(summary = "导入教师名单（JSON数据）")
    @PostMapping("/import-json")
    public Map<String, Object> importTeacherListJson(@RequestBody Map<String, Object> requestData) {
        Map<String, Object> result = new java.util.HashMap<>();
        int successCount = 0;
        int failCount = 0;
        java.util.List<String> failMessages = new java.util.ArrayList<>();

        try {
            // 增强日志记录，便于调试
            System.out.println("=== 开始处理教师导入请求 ====");
            System.out.println("请求数据类型: " + requestData.getClass().getName());
            System.out.println("请求数据键集: " + requestData.keySet());

            // 从请求中获取用户列表
            List<Map<String, Object>> userMapList = (List<Map<String, Object>>) requestData.get("users");
            if (userMapList == null || userMapList.isEmpty()) {
                result.put("success", false);
                result.put("message", "导入失败：没有找到有效的用户数据");
                return result;
            }

            // 查询教师角色ID
            Role teacherRole = roleService.getOne(new LambdaQueryWrapper<Role>()
                    .eq(Role::getRoleName, "teacher"));

            if (teacherRole == null) {
                result.put("success", false);
                result.put("message", "导入失败：未找到教师角色");
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
                            } catch (NumberFormatException e) {
                                // 如果不是数字，则通过系别名称查询系别ID
                                Department department = departmentService.getByDepartmentName(deptName);
                                if (department != null) {
                                    user.setDepartmentId(department.getId());
                                } else {
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
                if ((user.getStudentTeacherId() == null || user.getStudentTeacherId().trim().isEmpty()) &&
                        (user.getRealName() == null || user.getRealName().trim().isEmpty())) {
                    failCount++;
                    failMessages.add("教师编号或姓名为空，这些是必填项");
                    continue;
                }

                // 如果没有提供教师编号，则自动生成一个
                if (user.getStudentTeacherId() == null || user.getStudentTeacherId().trim().isEmpty()) {
                    // 生成教师编号：T + 时间戳后7位
                    String timestamp = String.valueOf(System.currentTimeMillis());
                    String teacherId = "T" + timestamp.substring(timestamp.length() - 7);
                    user.setStudentTeacherId(teacherId);
                }

                // Excel内查重
                if (excelIdSet.contains(user.getStudentTeacherId())) {
                    failCount++;
                    failMessages.add("导入数据中存在重复教师编号：" + user.getStudentTeacherId());
                    continue;
                }
                excelIdSet.add(user.getStudentTeacherId());

                // 检查教师编号是否已存在
                if (userService.existsByStudentTeacherId(user.getStudentTeacherId())) {
                    failCount++;
                    failMessages.add("教师编号已存在：" + user.getStudentTeacherId());
                    continue;
                }

                // 如果没有提供用户名，则使用教师编号作为用户名
                if (user.getUsername() == null || user.getUsername().trim().isEmpty()) {
                    user.setUsername(user.getStudentTeacherId());
                }

                // 检查用户名是否已存在
                if (userService.getByUsername(user.getUsername()) != null) {
                    failCount++;
                    failMessages.add("用户名已存在：" + user.getUsername());
                    continue;
                }

                // 设置默认密码
                user.setPassword(PasswordUtil.encode(DEFAULT_PASSWORD));

                // 设置默认的兴趣标签（空数组）
                user.setInterestTags("[]");

                // 保存到数据库
                userService.save(user);

                // 为用户添加教师角色关联
                UserRole userRole = new UserRole();
                userRole.setUserId(user.getId());
                userRole.setRoleId(teacherRole.getId()); // 教师角色ID
                userRoleService.save(userRole);

                successCount++;
            }

            result.put("success", true);
            result.put("successCount", successCount);
            result.put("failCount", failCount);
            result.put("failMessages", failMessages);
        } catch (Exception e) {
            // 增强错误日志记录
            System.err.println("=== 教师导入异常 ====");
            System.err.println("异常类型: " + e.getClass().getName());
            System.err.println("异常消息: " + e.getMessage());
            e.printStackTrace(); // 打印详细错误堆栈

            // 提供更详细的错误信息
            result.put("success", false);
            result.put("message", "导入失败：" + e.getMessage());

            // 添加异常类型信息，便于前端区分处理
            result.put("errorType", e.getClass().getSimpleName());
        }
        return result;
    }
}