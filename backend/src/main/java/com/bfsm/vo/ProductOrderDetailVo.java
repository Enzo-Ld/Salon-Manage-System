package com.bfsm.vo;

import lombok.EqualsAndHashCode;
import lombok.Data;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProductOrderDetailVo extends ProductOrderSummaryVo {
    private List<ProductOrderItemVo> items;
}
