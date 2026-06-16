package com.bfsm.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class WalletSummaryVo {
    private BigDecimal balance;
    private BigDecimal totalRecharge;
    private BigDecimal totalGift;
}
