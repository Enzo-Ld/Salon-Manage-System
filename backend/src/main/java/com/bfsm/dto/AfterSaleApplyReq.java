package com.bfsm.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AfterSaleApplyReq {
    @NotBlank
    private String reason;
}
