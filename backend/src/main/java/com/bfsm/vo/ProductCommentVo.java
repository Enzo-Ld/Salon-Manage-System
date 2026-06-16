package com.bfsm.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProductCommentVo {
    private Long id;
    private Long userId;
    private String username;
    private Integer rating;
    private String content;
    private String status;
    private LocalDateTime createdAt;
    private boolean mine;
}
