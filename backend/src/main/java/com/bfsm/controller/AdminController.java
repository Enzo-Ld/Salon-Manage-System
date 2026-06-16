package com.bfsm.controller;

import com.bfsm.common.ApiResponse;
import com.bfsm.dto.AdminCreateUserReq;
import com.bfsm.dto.UserRoleReq;
import com.bfsm.dto.UserStatusReq;
import com.bfsm.service.AdminUserService;
import com.bfsm.vo.AdminUserVo;
import com.bfsm.vo.PageResultVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "管理端辅助模块")
public class AdminController {
    private final AdminUserService adminUserService;

    public AdminController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @PostMapping("/users")
    @Operation(summary = "创建后台用户")
    public ApiResponse<Void> createUser(@Valid @RequestBody AdminCreateUserReq req) {
        adminUserService.createUser(req);
        return ApiResponse.ok();
    }

    @GetMapping("/users")
    @Operation(summary = "查询用户列表")
    public ApiResponse<List<AdminUserVo>> users(@RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) String roleCode,
                                                @RequestParam(required = false) Boolean enabled) {
        return ApiResponse.ok(adminUserService.listUsers(keyword, roleCode, enabled));
    }

    @GetMapping("/users/page")
    @Operation(summary = "分页查询用户列表")
    public ApiResponse<PageResultVo<AdminUserVo>> usersPage(@RequestParam(required = false) Long pageNum,
                                                            @RequestParam(required = false) Long pageSize,
                                                            @RequestParam(required = false) String keyword,
                                                            @RequestParam(required = false) String roleCode,
                                                            @RequestParam(required = false) Boolean enabled) {
        return ApiResponse.ok(adminUserService.pageUsers(pageNum, pageSize, keyword, roleCode, enabled));
    }

    @PostMapping("/users/{id}/status")
    @Operation(summary = "启用或禁用用户")
    public ApiResponse<Void> updateUserStatus(@PathVariable Long id, @Valid @RequestBody UserStatusReq req) {
        adminUserService.updateUserEnabled(id, Boolean.TRUE.equals(req.getEnabled()));
        return ApiResponse.ok();
    }

    @PostMapping("/users/{id}/role")
    @Operation(summary = "调整用户角色")
    public ApiResponse<Void> updateUserRole(@PathVariable Long id, @Valid @RequestBody UserRoleReq req) {
        adminUserService.updateUserRole(id, req.getRoleCode().trim().toUpperCase());
        return ApiResponse.ok();
    }

    @PostMapping("/users/{id}/reset-password")
    @Operation(summary = "重置用户密码")
    public ApiResponse<Void> resetPassword(@PathVariable Long id) {
        adminUserService.resetPassword(id);
        return ApiResponse.ok();
    }
}
