package com.bfsm.dto;

import lombok.Data;

import java.util.List;

@Data
public class AiConsultAnalyzeReq {
    private String imageUrl;
    private String requestText;
    private String styleGoal;
    private String budgetText;
    private List<String> concerns;
}
