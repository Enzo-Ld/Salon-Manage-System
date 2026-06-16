package com.bfsm.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatSessionSummaryVo {
    private Long id;
    private Long otherUserId;
    private String otherUserName;
    private String lastMessage;
    private LocalDateTime updatedAt;
}
