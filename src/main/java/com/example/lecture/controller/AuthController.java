package com.example.lecture.controller;

import com.example.lecture.common.api.Result;
import com.example.lecture.dto.LoginDTO;
import com.example.lecture.dto.RegisterDTO;
import com.example.lecture.dto.ResetPasswordDTO;
import com.example.lecture.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
//http://localhost:8080/api/auth/login
@RestController
@RequestMapping("/auth")
public class AuthController {
    @Autowired
    private AuthService authService;

    @PostMapping("/login")
    public Result<Map<String, String>> login(@Validated @RequestBody LoginDTO loginDTO) {
        String token = authService.login(loginDTO.getUsername(), loginDTO.getPassword());
        Map<String, String> data = new HashMap<>();
        data.put("token", token);
        return Result.success(data);
    }

    @PostMapping("/register")
    public Result<Void> register(@Validated @RequestBody RegisterDTO registerDTO) {
        System.out.println("收到注册请求：" + registerDTO);
        authService.register(registerDTO.getUsername(), registerDTO.getEmail(), registerDTO.getPassword());
        return Result.success();
    }

    @PostMapping("/reset-password")
    public Result<Void> resetPassword(@Validated @RequestBody ResetPasswordDTO resetPasswordDTO) {
        authService.resetPassword(resetPasswordDTO.getEmail(), resetPasswordDTO.getNewPassword());
        return Result.success();
    }
}
