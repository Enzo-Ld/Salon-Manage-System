package com.bfsm.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ProductOrderStatusReq {
    @NotBlank
    private String status;
}
