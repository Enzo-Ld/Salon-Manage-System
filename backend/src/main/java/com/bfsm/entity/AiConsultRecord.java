package com.bfsm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_consult_record")
public class AiConsultRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String title;
    private String stage;
    private String imageUrl;
    private String inputText;
    private String summaryText;
    private String faceSummary;
    private String hairstyleJson;
    private String serviceJson;
    private String productJson;
    private String careTipsJson;
    private String intentTagsJson;
    private String preferenceJson;
    private String recommendationSnapshotJson;
    private LocalDateTime lastMessageAt;
    private String disclaimer;
    private String analysisMode;
    private String rawResponse;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
