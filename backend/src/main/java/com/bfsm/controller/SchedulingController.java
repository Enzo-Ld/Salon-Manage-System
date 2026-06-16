package com.bfsm.controller;

import com.bfsm.common.ApiResponse;
import com.bfsm.service.SchedulingService;
import com.bfsm.vo.TechnicianRecommendationVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/scheduling")
@Tag(name = "排班推荐模块")
public class SchedulingController {
    private final SchedulingService schedulingService;

    public SchedulingController(SchedulingService schedulingService) {
        this.schedulingService = schedulingService;
    }

    @PreAuthorize("hasAnyRole('ADMIN','TECH','USER')")
    @GetMapping("/recommend")
    @Operation(summary = "查询推荐技师")
    public ApiResponse<TechnicianRecommendationVo> recommend(@RequestParam Long serviceItemId,
                                                             @RequestParam LocalDateTime appointmentTime) {
        return ApiResponse.ok(schedulingService.recommendTechnician(serviceItemId, appointmentTime));
    }
}
