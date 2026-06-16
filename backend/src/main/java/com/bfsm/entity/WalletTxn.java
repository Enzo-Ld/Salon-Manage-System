package com.bfsm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("wallet_txn")
public class WalletTxn {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String txnNo;
    private Long userId;
    private String txnType;
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private String relatedType;
    private Long relatedId;
    private String remark;
    private LocalDateTime createdAt;
}
