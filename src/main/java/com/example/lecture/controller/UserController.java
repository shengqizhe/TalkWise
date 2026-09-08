package com.example.lecture.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import com.example.lecture.common.api.Result;
import com.example.lecture.entity.User;
import com.example.lecture.dto.UserInfoDTO;
import com.example.lecture.dto.RegisterDTO;
import com.example.lecture.dto.UserUpdateDTO;
import com.example.lecture.dto.PasswordUpdateDTO;
import com.example.lecture.dto.UserQueryDTO;
import com.example.lecture.dto.FileUploadResult;
import com.example.lecture.service.UserService;
import com.example.lecture.util.FileUploadUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 用户Controller
 */
@Tag(name = "用户管理", description = "用户相关接口")
@RestController
@RequestMapping("/user")
public class UserController {
    
    private final UserService userService;
    
    @Value("${file.upload.avatar}")
    private String avatarPath;
    
    @Value("${server.servlet.context-path}")
    private String contextPath;
    
    public UserController(UserService userService) {
        this.userService = userService;
    }
    
    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterDTO registerDTO) {
        // 将DTO转换为实体
        User user = new User();
        BeanUtils.copyProperties(registerDTO, user);
        userService.register(user);
        return Result.success();
    }
    
    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<String> login(@RequestParam String username, @RequestParam String password) {
        String token = userService.login(username, password);
        return Result.success(token);
    }
    
    @Operation(summary = "获取用户信息")
    @GetMapping("/info")
    public Result<UserInfoDTO> getUserInfo() {
        // 获取当前登录用户ID
        Long userId = StpUtil.getLoginIdAsLong();
        UserInfoDTO userInfo = userService.getUserInfo(userId);
        return Result.success(userInfo);
    }
    
    @Operation(summary = "更新用户信息")
    @PutMapping("/update")
    public Result<Void> updateUser(@Valid @RequestBody UserUpdateDTO userUpdateDTO) {
        // 获取当前用户ID
        Long userId = StpUtil.getLoginIdAsLong();
        
        // 将DTO转换为实体
        User user = new User();
        BeanUtils.copyProperties(userUpdateDTO, user);
        user.setId(userId);
        
        userService.updateUser(user);
        return Result.success();
    }
    
    @Operation(summary = "修改密码")
    @PutMapping("/password")
    public Result<Void> updatePassword(@Valid @RequestBody PasswordUpdateDTO passwordUpdateDTO) {
        // 验证新密码和确认密码是否一致
        if (!passwordUpdateDTO.getNewPassword().equals(passwordUpdateDTO.getConfirmPassword())) {
            return Result.failed("新密码和确认密码不一致");
        }
        
        userService.updatePassword(StpUtil.getLoginIdAsLong(), 
                                 passwordUpdateDTO.getOldPassword(), 
                                 passwordUpdateDTO.getNewPassword());
        return Result.success();
    }
    
    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public Result<Void> logout() {
        StpUtil.logout();
        return Result.success();
    }
    
    @Operation(summary = "获取用户列表")
    @GetMapping("/list")
    @SaCheckRole("admin")
    public Result<List<User>> list() {
        return Result.success(userService.list());
    }
    
    @Operation(summary = "根据系别获取用户列表")
    @GetMapping("/department/{departmentId}")
    @SaCheckRole("admin")
    public Result<List<User>> getUsersByDepartment(@PathVariable Long departmentId) {
        List<User> users = userService.getUsersByDepartmentId(departmentId);
        return Result.success(users);
    }
    
    @Operation(summary = "分页查询用户")
    @PostMapping("/page")
    @SaCheckRole("admin")
    public Result<Page<User>> getUserPage(@RequestBody UserQueryDTO userQueryDTO) {
        Page<User> userPage = userService.getUserPage(userQueryDTO);
        return Result.success(userPage);
    }
    
    @Operation(summary = "删除用户")
    @DeleteMapping("/{id}")
    @SaCheckRole("admin")
    public Result<Void> delete(@PathVariable Long id) {
        userService.removeById(id);
        return Result.success();
    }
    
    @Operation(summary = "上传头像")
    @PostMapping("/upload/avatar")
    public Result<FileUploadResult> uploadAvatar(@RequestParam("file") MultipartFile file) {
        // 获取当前登录用户ID
        Long userId = StpUtil.getLoginIdAsLong();
        
        // 检查文件是否为空
        if (file.isEmpty()) {
            return Result.failed("请选择文件");
        }
        
        // 检查文件类型
        String originalFilename = file.getOriginalFilename();
        if (!FileUploadUtil.isAllowedExtension(originalFilename)) {
            return Result.failed("只支持jpg、jpeg、png、gif格式的图片");
        }
        
        // 保存文件
        String fileName = FileUploadUtil.saveFile(file, avatarPath);
        if (fileName == null) {
            return Result.failed("文件上传失败");
        }
        
        // 构建URL
        String url = contextPath + "/uploads/avatar/" + fileName;
        
        // 更新用户头像
        User user = new User();
        user.setId(userId);
        user.setAvatar(url);
        userService.updateById(user);
        
        // 返回结果
        FileUploadResult result = new FileUploadResult(fileName, url);
        return Result.success(result);
    }
} 