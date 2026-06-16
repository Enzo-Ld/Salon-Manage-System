package com.bfsm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "bfsm.ai")
public class AiConsultProperties {
    private boolean enabled = false;
    private String provider = "openai-compatible";
    private String baseUrl;
    private String chatCompletionsPath = "/chat/completions";
    private String apiKey;
    private String model;
    private Double temperature = 0.4;
    private boolean fallbackToLocal = false;
    private boolean allowDegradedResponse = true;
}
