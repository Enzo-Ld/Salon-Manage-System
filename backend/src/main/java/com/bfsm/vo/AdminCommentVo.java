package com.bfsm.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminCommentVo {
    private Long id;
    private String commentType;
    private String commentTypeLabel;
    private Long userId;
    private String username;
    private Integer rating;
    private String content;
    private String targetTitle;
    private LocalDateTime createdAt;
}
