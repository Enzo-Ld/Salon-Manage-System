package com.bfsm.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminCreateUserReq {
    @NotBlank
    private String username;

    @NotBlank
    private String password;

    private String nickname;
    private String phone;

    @NotBlank
    private String roleCode;
}
