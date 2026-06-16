package com.bfsm.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AiConsultServiceRecommendationVo {
    private Long serviceItemId;
    private String serviceName;
    private String categoryName;
    private Integer durationMin;
    private BigDecimal price;
    private String reason;
    private Integer fitScore;
}
