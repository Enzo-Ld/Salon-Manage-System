package com.bfsm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bfsm.entity.UserWallet;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

@Mapper
public interface UserWalletMapper extends BaseMapper<UserWallet> {
    @Update("""
            UPDATE user_wallet
            SET balance = balance - #{amount}, updated_at = NOW()
            WHERE user_id = #{userId} AND balance >= #{amount}
            """)
    int decreaseBalanceIfEnough(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    @Update("""
            UPDATE user_wallet
            SET balance = balance + #{amount}, updated_at = NOW()
            WHERE user_id = #{userId}
            """)
    int increaseBalance(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    @Update("""
            UPDATE user_wallet
            SET balance = balance + #{rechargeAmount} + #{giftAmount},
                total_recharge = total_recharge + #{rechargeAmount},
                total_gift = total_gift + #{giftAmount},
                updated_at = NOW()
            WHERE user_id = #{userId}
            """)
    int creditRecharge(@Param("userId") Long userId,
                       @Param("rechargeAmount") BigDecimal rechargeAmount,
                       @Param("giftAmount") BigDecimal giftAmount);
}
