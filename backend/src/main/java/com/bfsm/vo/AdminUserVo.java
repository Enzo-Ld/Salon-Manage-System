package com.bfsm.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminUserVo {
    private Long id;
    private String username;
    private String nickname;
    private String phone;
    private String avatarUrl;
    private Integer enabled;
    private String roleCode;
    private String roleName;
    private LocalDateTime createdAt;
}
