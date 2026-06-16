package com.bfsm.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiConsultMessageVo {
    private Long id;
    private Long recordId;
    private String role;
    private String content;
    private String imageUrl;
    private LocalDateTime createdAt;
}
