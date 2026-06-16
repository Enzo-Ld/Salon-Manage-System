package com.bfsm.controller;

import com.bfsm.common.ApiResponse;
import com.bfsm.common.BizException;
import com.bfsm.dto.LeaveConflictResolveReq;
import com.bfsm.dto.LeaveReviewReq;
import com.bfsm.entity.LeaveRecord;
import com.bfsm.service.AppointmentAdminService;
import com.bfsm.service.LeaveRecordService;
import com.bfsm.vo.LeaveConflictPreviewVo;
import com.bfsm.vo.PageResultVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@RestController
@RequestMapping("/api/leave-records")
@PreAuthorize("hasAnyRole('ADMIN','TECH')")
@Tag(name = "Leave Record Module")
public class LeaveRecordController {
    private final LeaveRecordService leaveRecordService;
    private final AppointmentAdminService appointmentAdminService;

    public LeaveRecordController(LeaveRecordService leaveRecordService, AppointmentAdminService appointmentAdminService) {
        this.leaveRecordService = leaveRecordService;
        this.appointmentAdminService = appointmentAdminService;
    }

    @GetMapping
    @Operation(summary = "List leave records")
    public ApiResponse<List<LeaveRecord>> list() {
        return ApiResponse.ok(leaveRecordService.listForCurrentRole());
    }

    @GetMapping("/page")
    @Operation(summary = "Paginated leave records")
    public ApiResponse<PageResultVo<LeaveRecord>> page(@RequestParam(required = false) Long pageNum,
                                                       @RequestParam(required = false) Long pageSize) {
        return ApiResponse.ok(leaveRecordService.pageForCurrentRole(pageNum, pageSize));
    }

    @PostMapping
    @Operation(summary = "Create or update a leave record")
    public ApiResponse<LeaveRecord> save(@RequestBody LeaveRecord body) {
        return ApiResponse.ok(leaveRecordService.save(body));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a leave record")
    public ApiResponse<Void> cancel(@PathVariable Long id) {
        leaveRecordService.cancel(id);
        return ApiResponse.ok();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}/conflicts")
    @Operation(summary = "Preview appointment conflicts before approving leave")
    public ApiResponse<LeaveConflictPreviewVo> conflicts(@PathVariable Long id) {
        return ApiResponse.ok(leaveRecordService.conflictPreview(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve a leave record")
    public ApiResponse<LeaveRecord> approve(@PathVariable Long id, @RequestBody(required = false) LeaveReviewReq body) {
        return ApiResponse.ok(leaveRecordService.approve(id, body == null ? null : body.getRemark()));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/resolve-and-approve")
    @Operation(summary = "Resolve appointment conflicts and approve leave")
    public ApiResponse<LeaveRecord> resolveAndApprove(@PathVariable Long id, @RequestBody LeaveConflictResolveReq body) {
        LeaveConflictPreviewVo preview = leaveRecordService.conflictPreview(id);
        if (Boolean.TRUE.equals(preview.getHasConflicts())) {
            if (body == null || body.getItems() == null || body.getItems().isEmpty()) {
                throw new BizException(4321, "请先处理请假时段内的冲突预约");
            }
            for (LeaveConflictResolveReq.Item item : body.getItems()) {
                if ("REASSIGN".equalsIgnoreCase(item.getAction())) {
                    appointmentAdminService.reassign(item.getAppointmentId(), item.getTargetTechnicianId(), body.getRemark());
                } else if ("CANCEL".equalsIgnoreCase(item.getAction())) {
                    appointmentAdminService.cancelForLeave(item.getAppointmentId(), body.getRemark());
                } else {
                    throw new BizException(4322, "不支持的冲突处理方式");
                }
            }
        }
        return ApiResponse.ok(leaveRecordService.approve(id, body == null ? null : body.getRemark()));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject a leave record")
    public ApiResponse<LeaveRecord> reject(@PathVariable Long id, @RequestBody LeaveReviewReq body) {
        return ApiResponse.ok(leaveRecordService.reject(id, body == null ? null : body.getRemark()));
    }
}
