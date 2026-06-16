package com.bfsm.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class AiConsultSessionVo {
    private Long id;
    private String title;
    private String stage;
    private String imageUrl;
    private String inputText;
    private String analysisMode;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastMessageAt;
    private String previewText;
    private Integer messageCount;
    private AiRecommendationSnapshotVo snapshot;
    private List<AiConsultMessageVo> messages = new ArrayList<>();
}
