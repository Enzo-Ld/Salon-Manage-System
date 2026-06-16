package com.bfsm.controller;

import com.bfsm.common.ApiResponse;
import com.bfsm.dto.AppointmentReassignReq;
import com.bfsm.dto.AppointmentCreateReq;
import com.bfsm.dto.CommentReq;
import com.bfsm.dto.OrderPayReq;
import com.bfsm.dto.StatusReq;
import com.bfsm.service.AppointmentAdminService;
import com.bfsm.service.AppointmentService;
import com.bfsm.vo.AppointmentAvailableSlotVo;
import com.bfsm.vo.AppointmentDetailVo;
import com.bfsm.vo.AppointmentSummaryVo;
import com.bfsm.vo.PageResultVo;
import com.bfsm.vo.TechnicianRecommendationVo;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/appointments")
@Tag(name = "预约模块")
public class AppointmentController {
    private final AppointmentService appointmentService;
    private final AppointmentAdminService appointmentAdminService;

    public AppointmentController(AppointmentService appointmentService, AppointmentAdminService appointmentAdminService) {
        this.appointmentService = appointmentService;
        this.appointmentAdminService = appointmentAdminService;
    }

    @PostMapping
    @Operation(summary = "提交预约")
    public ApiResponse<AppointmentDetailVo> create(@Valid @RequestBody AppointmentCreateReq req) {
        return ApiResponse.ok(appointmentService.create(req));
    }

    @GetMapping
    @Operation(summary = "按当前角色查询预约列表")
    public ApiResponse<List<AppointmentSummaryVo>> list() {
        return ApiResponse.ok(appointmentService.listForCurrentUser());
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询当前角色预约列表")
    public ApiResponse<PageResultVo<AppointmentSummaryVo>> page(@RequestParam(required = false) Long pageNum,
                                                                @RequestParam(required = false) Long pageSize) {
        return ApiResponse.ok(appointmentService.pageForCurrentUser(pageNum, pageSize));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询预约详情")
    public ApiResponse<AppointmentDetailVo> detail(@PathVariable Long id) {
        return ApiResponse.ok(appointmentService.detail(id));
    }

    @GetMapping("/recommend-technician")
    @Operation(summary = "推荐技师")
    public ApiResponse<TechnicianRecommendationVo> recommend(@RequestParam Long serviceItemId,
                                                             @RequestParam LocalDateTime appointmentTime) {
        return ApiResponse.ok(appointmentService.recommend(serviceItemId, appointmentTime));
    }

    @GetMapping("/available-slots")
    @Operation(summary = "查询可预约时间段")
    public ApiResponse<List<AppointmentAvailableSlotVo>> availableSlots(@RequestParam Long serviceItemId,
                                                                        @RequestParam LocalDate appointmentDate,
                                                                        @RequestParam(required = false) Long technicianId) {
        return ApiResponse.ok(appointmentService.availableSlots(serviceItemId, appointmentDate, technicianId));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/reassign")
    @Operation(summary = "管理员转派预约技师")
    public ApiResponse<Void> reassign(@PathVariable Long id, @RequestBody AppointmentReassignReq req) {
        appointmentAdminService.reassign(id, req.getTechnicianId(), req.getRemark());
        return ApiResponse.ok();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/auto-assign")
    @Operation(summary = "管理员一键自动分配技师")
    public ApiResponse<AppointmentDetailVo> autoAssign(@PathVariable Long id) {
        return ApiResponse.ok(appointmentService.autoAssign(id));
    }

    @PreAuthorize("hasAnyRole('ADMIN','TECH')")
    @PostMapping("/{id}/status")
    @Operation(summary = "审核或流转预约状态")
    public ApiResponse<Void> changeStatus(@PathVariable Long id, @Valid @RequestBody StatusReq req) {
        appointmentService.moveStatus(id, req.getStatus(), req.getRemark());
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "取消预约")
    public ApiResponse<Void> cancel(@PathVariable Long id) {
        appointmentService.cancel(id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/pay")
    @Operation(summary = "支付预约")
    public ApiResponse<AppointmentDetailVo> pay(@PathVariable Long id, @Valid @RequestBody OrderPayReq req) {
        return ApiResponse.ok(appointmentService.payForCurrentUser(id, req));
    }

    @PostMapping("/{id}/comment")
    @Operation(summary = "提交预约评价")
    public ApiResponse<Void> comment(@PathVariable Long id, @Valid @RequestBody CommentReq req) {
        appointmentService.submitComment(id, req);
        return ApiResponse.ok();
    }
}
