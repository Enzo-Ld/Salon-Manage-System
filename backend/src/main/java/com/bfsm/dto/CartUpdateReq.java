package com.bfsm.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class CartUpdateReq {
    @Min(1)
    private Integer quantity;
    private Boolean selected;
}
