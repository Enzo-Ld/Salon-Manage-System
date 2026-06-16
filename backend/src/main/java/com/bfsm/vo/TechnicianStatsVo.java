package com.bfsm.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TechnicianStatsVo {
    private Long technicianId;
    private String technicianName;
    private long completedCount;
    private BigDecimal amount;
}
