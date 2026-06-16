package com.bfsm.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AuthRegisterReq {
    @NotBlank
    private String username;
    @NotBlank
    private String password;

    private String nickname;
    private String phone;

    /**
     * USER / TECH / ADMIN
     */
    @NotBlank
    private String roleCode;
}

