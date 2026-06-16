package com.bfsm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ChatSendReq {
    @NotNull
    private Long receiverId;
    @NotBlank
    private String content;
}

