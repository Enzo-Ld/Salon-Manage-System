package com.bfsm.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class StatusReq {
    @NotBlank
    private String status;
    private String remark;
}

