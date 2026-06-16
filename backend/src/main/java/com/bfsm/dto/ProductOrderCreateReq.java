package com.bfsm.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class ProductOrderCreateReq {
    @NotNull
    private Long addressId;
    @Valid
    private List<ProductOrderSubmitItemReq> items;
    private List<Long> cartItemIds;
}
