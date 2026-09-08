package com.example.lecture.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.lecture.common.api.Result;
import com.example.lecture.entity.Role;
import com.example.lecture.entity.User;
import com.example.lecture.entity.UserRole;
import com.example.lecture.service.RoleService;
import com.example.lecture.service.UserRoleService;
import com.example.lecture.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 学生用户管理Controller
 */
@Tag(name = "学生用户管理", description = "学生用户相关接口")
@RestController
@RequestMapping("/student")
//@SaCheckRole("admin")
public class StudentController {
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private RoleService roleService;
    
    @Autowired
    private UserRoleService userRoleService;
    
    @Operation(summary = "获取学生用户列表")
    @GetMapping("/list")
    public Result<Page<User>> list(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "10") Integer size) {
        
        // 查询学生角色ID
        Role studentRole = roleService.getOne(new LambdaQueryWrapper<Role>()
                .eq(Role::getRoleName, "student"));
        
        if (studentRole == null) {
            return Result.success(new Page<>());
        }
        
        // 查询具有学生角色的用户ID列表
        List<Long> studentUserIds = userRoleService.list(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getRoleId, studentRole.getId()))
                .stream()
                .map(UserRole::getUserId)
                .toList();
        
        if (studentUserIds.isEmpty()) {
            return Result.success(new Page<>());
        }
        
        // 构建查询条件
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<User>()
                .in(User::getId, studentUserIds);
        
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
    
    @Operation(summary = "更新学生用户")
    @PutMapping("/update")
    public Result<Void> update(@RequestBody User user) {
        // 不更新密码字段
        user.setPassword(null);
        
        // 更新用户
        userService.updateById(user);
        
        return Result.success();
    }
    
    @Operation(summary = "删除学生用户")
    @DeleteMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> delete(@PathVariable Long id) {
        try {
            // 逻辑删除用户，实际是将user表的deleted字段设为1
            userService.removeById(id); // 逻辑删除
            
            // 删除用户角色关联（逻辑删除user_role表数据）
            userRoleService.remove(new LambdaQueryWrapper<UserRole>()
                    .eq(UserRole::getUserId, id));
            return Result.success();
        } catch (Exception e) {
            // 记录异常并返回错误信息
            e.printStackTrace();
            return Result.failed("删除学生用户失败: " + e.getMessage());
        }
    }
    
    @Operation(summary = "设置学生为教师")
    @PostMapping("/setTeacher/{id}")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> setAsTeacher(@PathVariable Long id) {
        // 查询教师角色
        Role teacherRole = roleService.getOne(new LambdaQueryWrapper<Role>()
                .eq(Role::getRoleName, "teacher"));
        
        if (teacherRole == null) {
            return Result.failed("教师角色不存在");
        }
        
        // 获取用户信息
        User user = userService.getById(id);
        if (user == null) {
            return Result.failed("用户不存在");
        }
        
        // 修改学号为教师编号（将S改为T，其他不变）
        String studentId = user.getStudentTeacherId();
        if (studentId != null && !studentId.isEmpty()) {
            if (studentId.startsWith("S")) {
                // 将S改为T，其他部分保持不变
                String teacherId = "T" + studentId.substring(1);
                user.setStudentTeacherId(teacherId);
                
                // 同时修改用户名，如果用户名以S开头，也将其改为T开头
                String username = user.getUsername();
                if (username != null && !username.isEmpty() && username.startsWith("S")) {
                    String newUsername = "T" + username.substring(1);
                    user.setUsername(newUsername);
                }
                
                userService.updateById(user);
            }
        }
        
        // 检查用户是否已有教师角色
        boolean hasTeacherRole = userRoleService.count(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getUserId, id)
                .eq(UserRole::getRoleId, teacherRole.getId())) > 0;
        
        if (!hasTeacherRole) {
            // 分配教师角色
            UserRole userRole = new UserRole();
            userRole.setUserId(id);
            userRole.setRoleId(teacherRole.getId());
            userRoleService.save(userRole);
        }
        // 新增：移除学生角色（即删除 user_role 表中该用户的学生角色关联）
        Role studentRole = roleService.getOne(new LambdaQueryWrapper<Role>()
                .eq(Role::getRoleName, "student"));
        if (studentRole != null) {
            userRoleService.remove(new LambdaQueryWrapper<UserRole>()
                    .eq(UserRole::getUserId, id)
                    .eq(UserRole::getRoleId, studentRole.getId()));
        }
        
        return Result.success();
    }

    @Operation(summary = "获取所有学生用户列表（不分页，用于导出）")
    @GetMapping("/all")
    public Result<List<User>> getAllStudents(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "departmentId", required = false) Long departmentId) {

        // 查询学生角色ID
        Role studentRole = roleService.getOne(new LambdaQueryWrapper<Role>()
                .eq(Role::getRoleName, "student"));

        if (studentRole == null) {
            return Result.success(List.of());
        }

        // 查询具有学生角色的用户ID列表
        List<Long> studentUserIds = userRoleService.list(new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getRoleId, studentRole.getId()))
                .stream()
                .map(UserRole::getUserId)
                .toList();

        if (studentUserIds.isEmpty()) {
            return Result.success(List.of());
        }

        // 构建查询条件
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<User>()
                .in(User::getId, studentUserIds);

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

        // 查询所有符合条件的学生
        List<User> studentList = userService.list(queryWrapper);

        return Result.success(studentList);
    }
}