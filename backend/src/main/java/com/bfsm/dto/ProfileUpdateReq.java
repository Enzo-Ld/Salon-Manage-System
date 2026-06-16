package com.bfsm.dto;

import lombok.Data;

@Data
public class ProfileUpdateReq {
    private String nickname;
    private String phone;
    private String avatarUrl;
    private String currentPassword;
    private String newPassword;
}
