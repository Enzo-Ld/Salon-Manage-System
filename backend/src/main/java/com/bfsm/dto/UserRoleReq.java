package com.bfsm.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserRoleReq {
    @NotBlank
    private String roleCode;
}
