package com.bfsm.controller;

import com.bfsm.common.ApiResponse;
import com.bfsm.entity.SystemNotification;
import com.bfsm.service.NotificationService;
import com.bfsm.vo.PageResultVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notification")
public class NotificationController {
    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @Operation(summary = "List current user notifications")
    public ApiResponse<PageResultVo<SystemNotification>> list(
            @RequestParam(required = false) Long pageNum,
            @RequestParam(required = false) Long pageSize,
            @RequestParam(required = false) String readStatus,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.ok(notificationService.listCurrentUser(pageNum, pageSize, readStatus, keyword));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Get current user unread notification count")
    public ApiResponse<Long> unreadCount() {
        return ApiResponse.ok(notificationService.unreadCountCurrentUser());
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "Mark one notification as read")
    public ApiResponse<Void> markRead(@PathVariable Long id) {
        notificationService.markRead(id);
        return ApiResponse.ok();
    }

    @PostMapping("/read-all")
    @Operation(summary = "Mark all notifications as read")
    public ApiResponse<Void> markAllRead() {
        notificationService.markAllRead();
        return ApiResponse.ok();
    }

    @PostMapping("/clear-read")
    @Operation(summary = "Delete all read notifications for current user")
    public ApiResponse<Void> clearRead() {
        notificationService.clearRead();
        return ApiResponse.ok();
    }
}
