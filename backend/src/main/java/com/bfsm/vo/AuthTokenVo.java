package com.bfsm.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthTokenVo {
    private String accessToken;
    private long expiresInSeconds;
}

