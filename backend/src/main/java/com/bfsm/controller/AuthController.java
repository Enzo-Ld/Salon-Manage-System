package com.bfsm.controller;

import com.bfsm.common.ApiResponse;
import com.bfsm.dto.AuthLoginReq;
import com.bfsm.dto.AuthRegisterReq;
import com.bfsm.dto.ProfileUpdateReq;
import com.bfsm.service.AuthService;
import com.bfsm.service.FileStorageService;
import com.bfsm.vo.AuthTokenVo;
import com.bfsm.vo.CurrentUserVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "认证模块")
public class AuthController {
    private final AuthService authService;
    private final FileStorageService fileStorageService;

    public AuthController(AuthService authService, FileStorageService fileStorageService) {
        this.authService = authService;
        this.fileStorageService = fileStorageService;
    }

    @PostMapping("/register")
    @Operation(summary = "用户注册")
    public ApiResponse<Void> register(@Valid @RequestBody AuthRegisterReq req) {
        authService.register(req);
        return ApiResponse.ok();
    }

    @PostMapping("/login")
    @Operation(summary = "用户登录")
    public ApiResponse<AuthTokenVo> login(@Valid @RequestBody AuthLoginReq req) {
        return ApiResponse.ok(authService.login(req));
    }

    @GetMapping("/me")
    @Operation(summary = "获取当前用户信息")
    public ApiResponse<CurrentUserVo> me() {
        return ApiResponse.ok(authService.currentUser());
    }

    @PutMapping("/profile")
    @Operation(summary = "更新当前用户资料")
    public ApiResponse<CurrentUserVo> updateProfile(@RequestBody ProfileUpdateReq req) {
        return ApiResponse.ok(authService.updateProfile(req));
    }

    @PostMapping("/avatar")
    @Operation(summary = "上传头像")
    public ApiResponse<String> uploadAvatar(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(fileStorageService.storeAvatar(file));
    }

    @PostMapping("/logout")
    @Operation(summary = "退出登录")
    public ApiResponse<Void> logout() {
        authService.logout();
        return ApiResponse.ok();
    }
}
