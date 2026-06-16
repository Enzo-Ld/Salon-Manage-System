package com.bfsm.controller;

import com.bfsm.common.ApiResponse;
import com.bfsm.entity.AttendanceRecord;
import com.bfsm.service.AttendanceService;
import com.bfsm.vo.AttendanceSummaryVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/attendance")
@Tag(name = "Attendance Module")
public class AttendanceController {
    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @PreAuthorize("hasRole('TECH')")
    @GetMapping("/today")
    @Operation(summary = "Get today's attendance summary")
    public ApiResponse<AttendanceSummaryVo> today() {
        return ApiResponse.ok(attendanceService.todaySummary());
    }

    @PreAuthorize("hasAnyRole('TECH','ADMIN')")
    @GetMapping("/records")
    @Operation(summary = "Get recent attendance records")
    public ApiResponse<List<AttendanceRecord>> records() {
        return ApiResponse.ok(attendanceService.recentRecords());
    }

    @PreAuthorize("hasRole('TECH')")
    @PostMapping("/check-in")
    @Operation(summary = "Check in for work")
    public ApiResponse<AttendanceSummaryVo> checkIn() {
        return ApiResponse.ok(attendanceService.checkIn());
    }

    @PreAuthorize("hasRole('TECH')")
    @PostMapping("/check-out")
    @Operation(summary = "Check out from work")
    public ApiResponse<AttendanceSummaryVo> checkOut() {
        return ApiResponse.ok(attendanceService.checkOut());
    }
}
