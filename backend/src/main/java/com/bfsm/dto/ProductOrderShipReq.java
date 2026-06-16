package com.bfsm.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ProductOrderShipReq {
    @NotBlank
    private String shippingCompany;
    @NotBlank
    private String trackingNo;
}
