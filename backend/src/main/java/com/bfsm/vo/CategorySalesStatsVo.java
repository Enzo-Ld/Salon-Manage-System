package com.bfsm.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategorySalesStatsVo {
    private Long categoryId;
    private String categoryName;
    private Integer quantity;
    private BigDecimal amount;
}
