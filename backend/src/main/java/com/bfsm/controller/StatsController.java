package com.bfsm.controller;

import com.bfsm.common.ApiResponse;
import com.bfsm.service.StatsService;
import com.bfsm.vo.AdminSalesOverviewVo;
import com.bfsm.vo.CategorySalesStatsVo;
import com.bfsm.vo.OverviewStatsVo;
import com.bfsm.vo.ProductSalesRankVo;
import com.bfsm.vo.SalesTrendPointVo;
import com.bfsm.vo.ServiceStatsVo;
import com.bfsm.vo.TechAttendanceChartVo;
import com.bfsm.vo.TechMonthStatsVo;
import com.bfsm.vo.TechPerformancePointVo;
import com.bfsm.vo.TechnicianStatsVo;
import com.bfsm.vo.TrendPointVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/stats")
@Tag(name = "统计模块")
public class StatsController {
    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/overview")
    @Operation(summary = "后台概览统计")
    public ApiResponse<OverviewStatsVo> overview() {
        return ApiResponse.ok(statsService.overview());
    }

    @GetMapping("/trend")
    @Operation(summary = "近七天预约趋势")
    public ApiResponse<List<TrendPointVo>> trend() {
        return ApiResponse.ok(statsService.appointmentTrend());
    }

    @GetMapping("/services")
    @Operation(summary = "服务项目预约统计")
    public ApiResponse<List<ServiceStatsVo>> services() {
        return ApiResponse.ok(statsService.serviceStats());
    }

    @GetMapping("/technicians")
    @Operation(summary = "技师服务排行统计")
    public ApiResponse<List<TechnicianStatsVo>> technicians() {
        return ApiResponse.ok(statsService.technicianStats());
    }

    @GetMapping("/tech-month")
    @Operation(summary = "技师个人月度统计")
    public ApiResponse<TechMonthStatsVo> techMonth(@RequestParam(required = false) String month) {
        return ApiResponse.ok(statsService.techMonth(month));
    }

    @GetMapping("/tech-performance-trend")
    @Operation(summary = "技师当月业绩趋势")
    public ApiResponse<List<TechPerformancePointVo>> techPerformanceTrend(@RequestParam(required = false) String month) {
        return ApiResponse.ok(statsService.techPerformanceTrend(month));
    }

    @GetMapping("/tech-attendance-chart")
    @Operation(summary = "技师当月考勤图表")
    public ApiResponse<TechAttendanceChartVo> techAttendanceChart(@RequestParam(required = false) String month) {
        return ApiResponse.ok(statsService.techAttendanceChart(month));
    }

    @GetMapping("/admin-sales-overview")
    @Operation(summary = "管理端当月商城总览")
    public ApiResponse<AdminSalesOverviewVo> adminSalesOverview(@RequestParam(required = false) String month) {
        return ApiResponse.ok(statsService.adminSalesOverview(month));
    }

    @GetMapping("/admin-sales-trend")
    @Operation(summary = "管理端当月销售额趋势")
    public ApiResponse<List<SalesTrendPointVo>> adminSalesTrend(@RequestParam(required = false) String month) {
        return ApiResponse.ok(statsService.adminSalesTrend(month));
    }

    @GetMapping("/admin-product-ranking")
    @Operation(summary = "管理端当月商品销量排行")
    public ApiResponse<List<ProductSalesRankVo>> adminProductRanking(@RequestParam(required = false) String month) {
        return ApiResponse.ok(statsService.adminProductRanking(month));
    }

    @GetMapping("/admin-category-stats")
    @Operation(summary = "管理端当月商品分类统计")
    public ApiResponse<List<CategorySalesStatsVo>> adminCategoryStats(@RequestParam(required = false) String month) {
        return ApiResponse.ok(statsService.adminCategoryStats(month));
    }
}
