package com.bfsm.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminSalesOverviewVo {
    private String month;
    private BigDecimal totalSales;
    private Long paidOrderCount;
    private Long soldItemCount;
    private Long productCount;
}
