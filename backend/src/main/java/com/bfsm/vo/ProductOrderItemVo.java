package com.bfsm.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductOrderItemVo {
    private Long productId;
    private String productName;
    private Long categoryId;
    private String categoryName;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal amount;
    private boolean commented;
}
