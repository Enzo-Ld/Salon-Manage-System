package com.bfsm.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AuthLoginReq {
    @NotBlank
    private String username;
    @NotBlank
    private String password;
}

