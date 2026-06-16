package com.bfsm.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ProductOrderSummaryVo {
    private Long id;
    private Long userId;
    private String username;
    private Long productId;
    private String productName;
    private Long categoryId;
    private String categoryName;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalAmount;
    private BigDecimal payAmount;
    private Integer itemCount;
    private String orderSummary;
    private String status;
    private String statusLabel;
    private String payStatus;
    private String payStatusLabel;
    private String payMethod;
    private String payMethodLabel;
    private LocalDateTime payTime;
    private String latestPaymentNo;
    private String latestPaymentChannel;
    private String latestPaymentChannelLabel;
    private String latestPaymentStatus;
    private String latestPaymentStatusLabel;
    private LocalDateTime latestPaymentTime;
    private String nextActionHint;
    private boolean canCancel;
    private boolean canPay;
    private boolean canConfirmReceipt;
    private boolean canComment;
    private boolean hasCommented;
    private boolean canRequestAfterSale;
    private String receiverName;
    private String receiverPhone;
    private String fullAddress;
    private String shippingCompany;
    private String trackingNo;
    private LocalDateTime shippedTime;
    private String afterSaleStatus;
    private String afterSaleStatusLabel;
    private String afterSaleReason;
    private String afterSaleRemark;
    private LocalDateTime afterSaleAppliedTime;
    private LocalDateTime afterSaleHandledTime;
    private String refundNo;
    private BigDecimal refundAmount;
    private String refundStatus;
    private String refundStatusLabel;
    private LocalDateTime refundTime;
    private LocalDateTime createdAt;
}
