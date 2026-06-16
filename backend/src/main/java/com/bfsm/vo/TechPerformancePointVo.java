package com.bfsm.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TechPerformancePointVo {
    private String label;
    private Long serviceCount;
    private BigDecimal amount;
}
