package com.bfsm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bfsm.entity.WalletTxn;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WalletTxnMapper extends BaseMapper<WalletTxn> {
    @Select("SELECT COUNT(1) FROM wallet_txn WHERE txn_no = #{txnNo}")
    int countByTxnNo(@Param("txnNo") String txnNo);
}
