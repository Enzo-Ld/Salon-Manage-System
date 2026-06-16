package com.bfsm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("technician")
public class Technician {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String name;
    private String levelName;
    private BigDecimal score;
    private BigDecimal ratingAvg;
    private Integer ratingCount;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal scoreOverride;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

