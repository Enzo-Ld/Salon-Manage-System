package com.bfsm.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AiConsultProductRecommendationVo {
    private Long productId;
    private String productName;
    private String categoryName;
    private BigDecimal price;
    private Integer stock;
    private String reason;
    private Integer fitScore;
}
