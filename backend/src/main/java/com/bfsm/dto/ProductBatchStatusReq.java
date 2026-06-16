package com.bfsm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class ProductBatchStatusReq {
    @NotEmpty
    private List<Long> ids;
    @NotBlank
    private String status;
}
