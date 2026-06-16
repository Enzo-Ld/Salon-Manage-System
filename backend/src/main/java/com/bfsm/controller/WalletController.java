package com.bfsm.controller;

import com.bfsm.common.ApiResponse;
import com.bfsm.dto.RechargeReq;
import com.bfsm.entity.RechargeActivity;
import com.bfsm.entity.WalletTxn;
import com.bfsm.service.WalletService;
import com.bfsm.vo.PageResultVo;
import com.bfsm.vo.WalletSummaryVo;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@RestController
@RequestMapping("/api/wallet")
public class WalletController {
    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @GetMapping("/summary")
    public ApiResponse<WalletSummaryVo> summary() {
        return ApiResponse.ok(walletService.currentSummary());
    }

    @GetMapping("/transactions")
    public ApiResponse<List<WalletTxn>> transactions() {
        return ApiResponse.ok(walletService.currentTransactions());
    }

    @GetMapping("/transactions/page")
    public ApiResponse<PageResultVo<WalletTxn>> transactionsPage(@RequestParam(required = false) Long pageNum,
                                                                 @RequestParam(required = false) Long pageSize) {
        return ApiResponse.ok(walletService.pageCurrentTransactions(pageNum, pageSize));
    }

    @GetMapping("/activities")
    public ApiResponse<List<RechargeActivity>> activities() {
        return ApiResponse.ok(walletService.activeActivities());
    }

    @PostMapping("/recharge")
    public ApiResponse<WalletSummaryVo> recharge(@Valid @RequestBody RechargeReq req) {
        return ApiResponse.ok(walletService.rechargeCurrentUser(req));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/activities/all")
    public ApiResponse<List<RechargeActivity>> allActivities() {
        return ApiResponse.ok(walletService.allActivities());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/activities")
    public ApiResponse<Void> saveActivity(@RequestBody RechargeActivity body) {
        walletService.saveActivity(body);
        return ApiResponse.ok();
    }
}
