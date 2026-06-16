package com.bfsm.service.ai;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AiProductCatalogItem {
    private Long productId;
    private String productName;
    private String categoryName;
    private BigDecimal price;
    private Integer stock;
}
