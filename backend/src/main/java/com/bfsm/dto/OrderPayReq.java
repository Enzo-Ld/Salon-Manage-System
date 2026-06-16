package com.bfsm.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OrderPayReq {
    @NotBlank
    private String payMethod;
}
