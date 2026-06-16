package com.bfsm.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ShoppingCartItemVo {
    private Long id;
    private Long productId;
    private String productName;
    private String categoryName;
    private BigDecimal price;
    private Integer stock;
    private Integer quantity;
    private boolean selected;
    private String status;
    private BigDecimal amount;
}
