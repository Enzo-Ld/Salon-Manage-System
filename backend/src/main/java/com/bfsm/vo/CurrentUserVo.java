package com.bfsm.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CurrentUserVo {
    private Long userId;
    private String username;
    private String nickname;
    private String phone;
    private String avatarUrl;
    private String roleCode;
}
