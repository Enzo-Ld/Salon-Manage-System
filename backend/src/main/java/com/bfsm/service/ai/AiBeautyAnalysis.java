package com.bfsm.service.ai;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AiBeautyAnalysis {
    private String assistantMessage;
    private String stage;
    private String summaryText;
    private String faceSummary;
    private List<HairStyleAdvice> hairStyles = new ArrayList<>();
    private List<ServiceRecommendation> serviceRecommendations = new ArrayList<>();
    private List<ProductRecommendation> productRecommendations = new ArrayList<>();
    private List<String> careTips = new ArrayList<>();
    private List<String> nextQuestions = new ArrayList<>();
    private List<String> intentTags = new ArrayList<>();
    private String disclaimer;
    private String analysisMode;
    private String rawResponse;

    @Data
    public static class HairStyleAdvice {
        private String name;
        private String reason;
        private String maintenanceLevel;
        private Integer fitScore;
    }

    @Data
    public static class ServiceRecommendation {
        private Long serviceItemId;
        private String reason;
        private Integer fitScore;
    }

    @Data
    public static class ProductRecommendation {
        private Long productId;
        private String reason;
        private Integer fitScore;
    }
}
