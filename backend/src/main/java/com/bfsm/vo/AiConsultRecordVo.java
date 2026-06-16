package com.bfsm.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AiConsultRecordVo {
    private Long id;
    private String title;
    private String stage;
    private String imageUrl;
    private String inputText;
    private String summaryText;
    private String faceSummary;
    private List<AiConsultHairStyleVo> hairStyles;
    private List<AiConsultServiceRecommendationVo> serviceRecommendations;
    private List<AiConsultProductRecommendationVo> productRecommendations;
    private List<String> careTips;
    private List<String> nextQuestions;
    private String disclaimer;
    private String analysisMode;
    private LocalDateTime createdAt;
}
