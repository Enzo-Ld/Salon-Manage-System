package com.bfsm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("product_order")
public class ProductOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private BigDecimal totalAmount;
    private BigDecimal payAmount;
    private Integer itemCount;
    private String status;
    private String payStatus;
    private String payMethod;
    private String payNo;
    private LocalDateTime payTime;
    private LocalDateTime closeTime;
    private String receiverName;
    private String receiverPhone;
    private String province;
    private String city;
    private String district;
    private String detailAddress;
    private String addressTag;
    private String shippingCompany;
    private String trackingNo;
    private LocalDateTime shippedTime;
    private String afterSaleStatus;
    private String afterSaleReason;
    private LocalDateTime afterSaleAppliedTime;
    private LocalDateTime afterSaleHandledTime;
    private String afterSaleRemark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
