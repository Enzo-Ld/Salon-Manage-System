package com.bfsm.service.ai;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AiServiceCatalogItem {
    private Long serviceItemId;
    private String serviceName;
    private String categoryName;
    private Integer durationMin;
    private BigDecimal price;
}
