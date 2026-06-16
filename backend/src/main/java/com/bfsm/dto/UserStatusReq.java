package com.bfsm.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UserStatusReq {
    @NotNull
    private Boolean enabled;
}
