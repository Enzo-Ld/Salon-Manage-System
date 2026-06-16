package com.bfsm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bfsm.common.BizException;
import com.bfsm.common.PageUtils;
import com.bfsm.common.SecurityUtils;
import com.bfsm.dto.RechargeReq;
import com.bfsm.entity.RechargeActivity;
import com.bfsm.entity.UserWallet;
import com.bfsm.entity.WalletTxn;
import com.bfsm.mapper.RechargeActivityMapper;
import com.bfsm.mapper.UserWalletMapper;
import com.bfsm.mapper.WalletTxnMapper;
import com.bfsm.vo.PageResultVo;
import com.bfsm.vo.WalletSummaryVo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class WalletService {
    private static final Set<String> RECHARGE_PAY_METHODS = Set.of("WECHAT", "ALIPAY");

    private final UserWalletMapper userWalletMapper;
    private final WalletTxnMapper walletTxnMapper;
    private final RechargeActivityMapper rechargeActivityMapper;

    public WalletService(UserWalletMapper userWalletMapper, WalletTxnMapper walletTxnMapper,
                         RechargeActivityMapper rechargeActivityMapper) {
        this.userWalletMapper = userWalletMapper;
        this.walletTxnMapper = walletTxnMapper;
        this.rechargeActivityMapper = rechargeActivityMapper;
    }

    public WalletSummaryVo currentSummary() {
        Long userId = SecurityUtils.currentUserOrThrow().getUserId();
        return toSummary(ensureWallet(userId));
    }

    public List<WalletTxn> currentTransactions() {
        Long userId = SecurityUtils.currentUserOrThrow().getUserId();
        ensureWallet(userId);
        return walletTxnMapper.selectList(new LambdaQueryWrapper<WalletTxn>()
                .eq(WalletTxn::getUserId, userId)
                .orderByDesc(WalletTxn::getCreatedAt)
                .orderByDesc(WalletTxn::getId));
    }

    public PageResultVo<WalletTxn> pageCurrentTransactions(Long pageNum, Long pageSize) {
        Long userId = SecurityUtils.currentUserOrThrow().getUserId();
        ensureWallet(userId);
        Page<WalletTxn> page = new Page<>(
                PageUtils.normalizePageNum(pageNum),
                PageUtils.normalizePageSize(pageSize)
        );
        return PageResultVo.from(walletTxnMapper.selectPage(page, new LambdaQueryWrapper<WalletTxn>()
                .eq(WalletTxn::getUserId, userId)
                .orderByDesc(WalletTxn::getCreatedAt)
                .orderByDesc(WalletTxn::getId)));
    }

    public List<RechargeActivity> activeActivities() {
        LocalDateTime now = LocalDateTime.now();
        return rechargeActivityMapper.selectList(new LambdaQueryWrapper<RechargeActivity>()
                .eq(RechargeActivity::getStatus, "ACTIVE")
                .and(wrapper -> wrapper.isNull(RechargeActivity::getStartTime)
                        .or()
                        .le(RechargeActivity::getStartTime, now))
                .and(wrapper -> wrapper.isNull(RechargeActivity::getEndTime)
                        .or()
                        .ge(RechargeActivity::getEndTime, now))
                .orderByAsc(RechargeActivity::getRechargeAmount)
                .orderByAsc(RechargeActivity::getId));
    }

    public List<RechargeActivity> allActivities() {
        return rechargeActivityMapper.selectList(new LambdaQueryWrapper<RechargeActivity>()
                .orderByDesc(RechargeActivity::getStatus)
                .orderByAsc(RechargeActivity::getRechargeAmount)
                .orderByDesc(RechargeActivity::getId));
    }

    @Transactional
    public void saveActivity(RechargeActivity body) {
        normalizeActivity(body);
        if (body.getId() == null) {
            rechargeActivityMapper.insert(body);
        } else {
            rechargeActivityMapper.updateById(body);
        }
    }

    @Transactional
    public WalletSummaryVo rechargeCurrentUser(RechargeReq req) {
        if (!RECHARGE_PAY_METHODS.contains(req.getPayMethod())) {
            throw new BizException(4201, "充值仅支持微信支付或支付宝");
        }
        Long userId = SecurityUtils.currentUserOrThrow().getUserId();
        UserWallet wallet = ensureWallet(userId);

        BigDecimal rechargeAmount = scale(req.getAmount());
        BigDecimal giftAmount = scale(BigDecimal.ZERO);
        RechargeActivity activity = null;
        if (req.getActivityId() != null) {
            activity = rechargeActivityMapper.selectById(req.getActivityId());
            if (activity == null || !isActivityActive(activity)) {
                throw new BizException(4202, "充值活动当前不可用");
            }
            if (activity.getRechargeAmount() == null || activity.getGiftAmount() == null
                    || scale(activity.getRechargeAmount()).compareTo(rechargeAmount) != 0) {
                throw new BizException(4203, "充值金额与所选活动不匹配");
            }
            giftAmount = scale(activity.getGiftAmount());
        }

        int credited = userWalletMapper.creditRecharge(userId, rechargeAmount, giftAmount);
        if (credited == 0) {
            BigDecimal balanceAfterRecharge = wallet.getBalance().add(rechargeAmount);
            wallet.setBalance(balanceAfterRecharge.add(giftAmount));
            wallet.setTotalRecharge(wallet.getTotalRecharge().add(rechargeAmount));
            wallet.setTotalGift(wallet.getTotalGift().add(giftAmount));
            userWalletMapper.updateById(wallet);
        } else {
            wallet = ensureWallet(userId);
        }

        BigDecimal balanceAfterRecharge = wallet.getBalance().subtract(giftAmount);
        recordTxn(buildTxnNo("DIRECT_RECHARGE", userId), userId, "RECHARGE", rechargeAmount, balanceAfterRecharge, "RECHARGE_ACTIVITY",
                activity == null ? null : activity.getId(), "模拟" + ("WECHAT".equals(req.getPayMethod()) ? "微信" : "支付宝") + "充值");
        if (giftAmount.compareTo(BigDecimal.ZERO) > 0) {
            recordTxn(buildTxnNo("DIRECT_GIFT", userId), userId, "GIFT", giftAmount, wallet.getBalance(), "RECHARGE_ACTIVITY", activity.getId(),
                    activity.getTitle());
        }
        return toSummary(wallet);
    }

    @Transactional
    public void consumeForOrder(Long userId, Long orderId, BigDecimal amount, String remark) {
        String txnNo = "ORDER_PAY:" + orderId + ":" + userId;
        if (walletTxnMapper.countByTxnNo(txnNo) > 0) {
            return;
        }
        ensureWallet(userId);
        BigDecimal debitAmount = scale(amount);
        if (userWalletMapper.decreaseBalanceIfEnough(userId, debitAmount) == 0) {
            throw new BizException(4204, "钱包余额不足");
        }
        UserWallet wallet = ensureWallet(userId);
        recordTxn(txnNo, userId, "ORDER_PAY", debitAmount.negate(), wallet.getBalance(), "PRODUCT_ORDER", orderId, remark);
    }

    @Transactional
    public void consumeForAppointment(Long userId, Long appointmentId, BigDecimal amount, String remark) {
        String txnNo = "APPOINTMENT_PAY:" + appointmentId + ":" + userId;
        if (walletTxnMapper.countByTxnNo(txnNo) > 0) {
            return;
        }
        ensureWallet(userId);
        BigDecimal debitAmount = scale(amount);
        if (userWalletMapper.decreaseBalanceIfEnough(userId, debitAmount) == 0) {
            throw new BizException(4204, "钱包余额不足");
        }
        UserWallet wallet = ensureWallet(userId);
        recordTxn(txnNo, userId, "APPOINTMENT_PAY", debitAmount.negate(), wallet.getBalance(), "APPOINTMENT", appointmentId, remark);
    }

    @Transactional
    public void refundForOrder(Long userId, Long orderId, BigDecimal amount, String remark) {
        String txnNo = "REFUND:PRODUCT_ORDER:" + orderId + ":" + userId;
        if (walletTxnMapper.countByTxnNo(txnNo) > 0) {
            return;
        }
        ensureWallet(userId);
        BigDecimal refundAmount = scale(amount);
        userWalletMapper.increaseBalance(userId, refundAmount);
        UserWallet wallet = ensureWallet(userId);
        recordTxn(txnNo, userId, "REFUND", refundAmount, wallet.getBalance(), "PRODUCT_ORDER", orderId, remark);
    }

    @Transactional
    public void refundForAppointment(Long userId, Long appointmentId, BigDecimal amount, String remark) {
        String txnNo = "REFUND:APPOINTMENT:" + appointmentId + ":" + userId;
        if (walletTxnMapper.countByTxnNo(txnNo) > 0) {
            return;
        }
        ensureWallet(userId);
        BigDecimal refundAmount = scale(amount);
        userWalletMapper.increaseBalance(userId, refundAmount);
        UserWallet wallet = ensureWallet(userId);
        recordTxn(txnNo, userId, "REFUND", refundAmount, wallet.getBalance(), "APPOINTMENT", appointmentId, remark);
    }

    private void normalizeActivity(RechargeActivity body) {
        if (body.getTitle() == null || body.getTitle().isBlank()) {
            throw new BizException(4205, "活动名称不能为空");
        }
        if (body.getRechargeAmount() == null || body.getRechargeAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(4206, "充值金额必须大于0");
        }
        if (body.getGiftAmount() == null || body.getGiftAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException(4207, "赠送金额不能为负数");
        }
        if (body.getStatus() == null || body.getStatus().isBlank()) {
            body.setStatus("ACTIVE");
        }
        body.setRechargeAmount(scale(body.getRechargeAmount()));
        body.setGiftAmount(scale(body.getGiftAmount()));
    }

    private boolean isActivityActive(RechargeActivity activity) {
        LocalDateTime now = LocalDateTime.now();
        boolean started = activity.getStartTime() == null || !activity.getStartTime().isAfter(now);
        boolean notEnded = activity.getEndTime() == null || !activity.getEndTime().isBefore(now);
        return "ACTIVE".equals(activity.getStatus()) && started && notEnded;
    }

    private UserWallet ensureWallet(Long userId) {
        UserWallet wallet = userWalletMapper.selectById(userId);
        if (wallet != null) {
            wallet.setBalance(scale(wallet.getBalance() == null ? BigDecimal.ZERO : wallet.getBalance()));
            wallet.setTotalRecharge(scale(wallet.getTotalRecharge() == null ? BigDecimal.ZERO : wallet.getTotalRecharge()));
            wallet.setTotalGift(scale(wallet.getTotalGift() == null ? BigDecimal.ZERO : wallet.getTotalGift()));
            return wallet;
        }

        UserWallet created = new UserWallet();
        created.setUserId(userId);
        created.setBalance(scale(BigDecimal.ZERO));
        created.setTotalRecharge(scale(BigDecimal.ZERO));
        created.setTotalGift(scale(BigDecimal.ZERO));
        userWalletMapper.insert(created);
        return created;
    }

    private void recordTxn(String txnNo, Long userId, String txnType, BigDecimal amount, BigDecimal balanceAfter,
                           String relatedType, Long relatedId, String remark) {
        if (walletTxnMapper.countByTxnNo(txnNo) > 0) {
            return;
        }
        WalletTxn txn = new WalletTxn();
        txn.setTxnNo(txnNo);
        txn.setUserId(userId);
        txn.setTxnType(txnType);
        txn.setAmount(amount);
        txn.setBalanceAfter(balanceAfter);
        txn.setRelatedType(relatedType);
        txn.setRelatedId(relatedId);
        txn.setRemark(remark);
        walletTxnMapper.insert(txn);
    }

    private WalletSummaryVo toSummary(UserWallet wallet) {
        WalletSummaryVo summary = new WalletSummaryVo();
        summary.setBalance(wallet.getBalance());
        summary.setTotalRecharge(wallet.getTotalRecharge());
        summary.setTotalGift(wallet.getTotalGift());
        return summary;
    }

    private BigDecimal scale(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private String buildTxnNo(String prefix, Long userId) {
        return prefix
                + ":"
                + userId
                + ":"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
                + ThreadLocalRandom.current().nextInt(100, 1000);
    }
}
