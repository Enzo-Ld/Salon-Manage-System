package com.bfsm.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductDetailVo {
    private Long id;
    private Long categoryId;
    private String categoryName;
    private String name;
    private String intro;
    private String imageUrl;
    private BigDecimal price;
    private Integer stock;
    private String status;
    private String statusLabel;
    private Integer sortNo;
    private java.time.LocalDateTime createdAt;
    private java.time.LocalDateTime updatedAt;
    private Integer favoriteCount;
    private Integer commentCount;
    private BigDecimal averageRating;
    private boolean favorited;
    private boolean canComment;
    private boolean hasCommented;
    private Long consultantUserId;
    private String consultantName;
}
