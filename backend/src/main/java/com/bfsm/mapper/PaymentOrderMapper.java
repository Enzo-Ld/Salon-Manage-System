package com.bfsm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bfsm.entity.PaymentOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface PaymentOrderMapper extends BaseMapper<PaymentOrder> {
    @Update("""
            UPDATE payment_order
            SET status = 'SUCCESS',
                paid_time = #{paidTime},
                platform_trade_no = #{platformTradeNo},
                notify_content = #{notifyContent},
                failure_reason = NULL,
                close_reason = NULL,
                updated_at = NOW()
            WHERE id = #{id} AND status IN ('CREATED', 'PAYING')
            """)
    int markSuccessIfPending(@Param("id") Long id,
                             @Param("platformTradeNo") String platformTradeNo,
                             @Param("paidTime") LocalDateTime paidTime,
                             @Param("notifyContent") String notifyContent);

    @Update("""
            UPDATE payment_order
            SET status = #{status},
                close_reason = #{reason},
                notify_content = #{notifyContent},
                updated_at = NOW()
            WHERE id = #{id} AND status IN ('CREATED', 'PAYING')
            """)
    int closeIfPending(@Param("id") Long id,
                       @Param("status") String status,
                       @Param("reason") String reason,
                       @Param("notifyContent") String notifyContent);
}
