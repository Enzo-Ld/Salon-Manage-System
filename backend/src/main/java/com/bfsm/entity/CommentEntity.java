package com.bfsm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("comment")
public class CommentEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long productId;
    private Long appointmentId;
    private Long userId;
    private Integer rating;
    private String content;
    private String status;
    private LocalDateTime createdAt;
}

