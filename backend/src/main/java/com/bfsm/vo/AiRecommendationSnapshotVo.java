package com.bfsm.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AiRecommendationSnapshotVo {
    private String stage;
    private String summaryText;
    private String faceHairObservation;
    private List<AiStyleDirectionVo> styleDirections = new ArrayList<>();
    private List<String> careTips = new ArrayList<>();
    private List<AiConsultServiceRecommendationVo> serviceRecommendations = new ArrayList<>();
    private List<AiConsultProductRecommendationVo> productRecommendations = new ArrayList<>();
    private List<String> nextQuestions = new ArrayList<>();
    private List<String> intentTags = new ArrayList<>();
    private String disclaimer;
    private String analysisMode;
}
