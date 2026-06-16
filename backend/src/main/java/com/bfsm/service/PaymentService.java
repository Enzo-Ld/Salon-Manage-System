package com.bfsm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bfsm.common.BizException;
import com.bfsm.common.SecurityUtils;
import com.bfsm.config.PaymentProperties;
import com.bfsm.dto.PaymentCreateReq;
import com.bfsm.dto.RechargeReq;
import com.bfsm.entity.Appointment;
import com.bfsm.entity.PaymentOrder;
import com.bfsm.entity.PaymentRefund;
import com.bfsm.entity.Product;
import com.bfsm.entity.ProductOrder;
import com.bfsm.entity.ProductOrderItem;
import com.bfsm.entity.RechargeActivity;
import com.bfsm.entity.ServiceItem;
import com.bfsm.entity.Technician;
import com.bfsm.entity.UserWallet;
import com.bfsm.entity.WalletTxn;
import com.bfsm.mapper.AppointmentMapper;
import com.bfsm.mapper.PaymentOrderMapper;
import com.bfsm.mapper.PaymentRefundMapper;
import com.bfsm.mapper.ProductMapper;
import com.bfsm.mapper.ProductOrderItemMapper;
import com.bfsm.mapper.ProductOrderMapper;
import com.bfsm.mapper.RechargeActivityMapper;
import com.bfsm.mapper.ServiceItemMapper;
import com.bfsm.mapper.TechnicianMapper;
import com.bfsm.mapper.UserWalletMapper;
import com.bfsm.mapper.WalletTxnMapper;
import com.bfsm.security.SecurityUser;
import com.bfsm.service.gateway.PaymentGateway;
import com.bfsm.service.gateway.PaymentGatewayRefundResult;
import com.bfsm.service.gateway.PaymentGatewayRouter;
import com.bfsm.service.gateway.PaymentGatewayStatus;
import com.bfsm.vo.PaymentCapabilityVo;
import com.bfsm.vo.PaymentChannelCapabilityVo;
import com.bfsm.vo.PaymentCreateVo;
import com.bfsm.vo.PaymentStatusVo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class PaymentService {
    private static final Set<String> ONLINE_PAY_CHANNELS = Set.of("WECHAT", "ALIPAY");
    private static final Set<String> PENDING_STATUSES = Set.of("CREATED", "PAYING");
    private static final Set<String> REFUNDABLE_PAYMENT_STATUSES = Set.of("SUCCESS", "REFUNDING", "MANUAL_REQUIRED", "REFUNDED");

    private final PaymentOrderMapper paymentOrderMapper;
    private final PaymentRefundMapper paymentRefundMapper;
    private final ProductOrderMapper productOrderMapper;
    private final ProductOrderItemMapper productOrderItemMapper;
    private final ProductMapper productMapper;
    private final AppointmentMapper appointmentMapper;
    private final ServiceItemMapper serviceItemMapper;
    private final TechnicianMapper technicianMapper;
    private final UserWalletMapper userWalletMapper;
    private final WalletTxnMapper walletTxnMapper;
    private final RechargeActivityMapper rechargeActivityMapper;
    private final NotificationService notificationService;
    private final PaymentGatewayRouter paymentGatewayRouter;
    private final PaymentProperties paymentProperties;
    private final ScheduleConflictChecker scheduleConflictChecker;

    public PaymentService(PaymentOrderMapper paymentOrderMapper,
                          PaymentRefundMapper paymentRefundMapper,
                          ProductOrderMapper productOrderMapper,
                          ProductOrderItemMapper productOrderItemMapper,
                          ProductMapper productMapper,
                          AppointmentMapper appointmentMapper,
                          ServiceItemMapper serviceItemMapper,
                          TechnicianMapper technicianMapper,
                          UserWalletMapper userWalletMapper,
                          WalletTxnMapper walletTxnMapper,
                          RechargeActivityMapper rechargeActivityMapper,
                          NotificationService notificationService,
                          PaymentGatewayRouter paymentGatewayRouter,
                          PaymentProperties paymentProperties,
                          ScheduleConflictChecker scheduleConflictChecker) {
        this.paymentOrderMapper = paymentOrderMapper;
        this.paymentRefundMapper = paymentRefundMapper;
        this.productOrderMapper = productOrderMapper;
        this.productOrderItemMapper = productOrderItemMapper;
        this.productMapper = productMapper;
        this.appointmentMapper = appointmentMapper;
        this.serviceItemMapper = serviceItemMapper;
        this.technicianMapper = technicianMapper;
        this.userWalletMapper = userWalletMapper;
        this.walletTxnMapper = walletTxnMapper;
        this.rechargeActivityMapper = rechargeActivityMapper;
        this.notificationService = notificationService;
        this.paymentGatewayRouter = paymentGatewayRouter;
        this.paymentProperties = paymentProperties;
        this.scheduleConflictChecker = scheduleConflictChecker;
    }

    @Transactional
    public PaymentCreateVo createProductOrderPayment(Long orderId, PaymentCreateReq req) {
        SecurityUser currentUser = SecurityUtils.currentUserOrThrow();
        String payChannel = normalizeCheckoutChannel(req.getPayChannel());
        String payMode = resolveSupportedPayMode(payChannel);
        ProductOrder order = requireCurrentUserOrder(orderId, currentUser.getUserId());
        if (!"CREATED".equals(order.getStatus()) || !"UNPAID".equals(order.getPayStatus())) {
            throw new BizException(4701, "当前订单不可发起支付");
        }

        closePendingPayments("PRODUCT_ORDER", order.getId(), "A new payment was created");

        PaymentOrder paymentOrder = new PaymentOrder();
        paymentOrder.setBusinessType("PRODUCT_ORDER");
        paymentOrder.setBusinessId(order.getId());
        paymentOrder.setPaymentNo(buildPaymentNo("P"));
        paymentOrder.setOutTradeNo(buildOutTradeNo("PO", order.getId()));
        paymentOrder.setPayChannel(payChannel);
        paymentOrder.setPayMode(payMode);
        paymentOrder.setAmount(scale(order.getPayAmount()));
        paymentOrder.setStatus("PAYING");
        paymentOrder.setSubject("商品订单支付 #" + order.getId());
        paymentOrder.setBodyText("用于商品订单的统一支付收银台流程");
        paymentOrder.setExpireTime(LocalDateTime.now().plusMinutes(15));
        paymentOrderMapper.insert(paymentOrder);

        return gatewayForMode(payMode).create(paymentOrder);
    }

    @Transactional
    public PaymentCreateVo createAppointmentPayment(Long appointmentId, PaymentCreateReq req) {
        SecurityUser currentUser = SecurityUtils.currentUserOrThrow();
        String payChannel = normalizeCheckoutChannel(req.getPayChannel());
        String payMode = resolveSupportedPayMode(payChannel);
        Appointment appointment = requireCurrentUserAppointment(appointmentId, currentUser.getUserId());
        if (!"CREATED".equals(appointment.getStatus()) || !"UNPAID".equals(normalizeAppointmentPayStatus(appointment))) {
            throw new BizException(4711, "当前预约不可发起支付");
        }

        closePendingPayments("APPOINTMENT", appointment.getId(), "A new payment was created");

        PaymentOrder paymentOrder = new PaymentOrder();
        paymentOrder.setBusinessType("APPOINTMENT");
        paymentOrder.setBusinessId(appointment.getId());
        paymentOrder.setPaymentNo(buildPaymentNo("A"));
        paymentOrder.setOutTradeNo(buildOutTradeNo("AP", appointment.getId()));
        paymentOrder.setPayChannel(payChannel);
        paymentOrder.setPayMode(payMode);
        paymentOrder.setAmount(scale(appointment.getAmount()));
        paymentOrder.setStatus("PAYING");
        paymentOrder.setSubject(appointmentSubject(appointment));
        paymentOrder.setBodyText("用于服务预约的统一支付收银台流程");
        paymentOrder.setExpireTime(LocalDateTime.now().plusMinutes(15));
        paymentOrderMapper.insert(paymentOrder);

        return gatewayForMode(payMode).create(paymentOrder);
    }

    @Transactional
    public PaymentCreateVo createWalletRechargePayment(RechargeReq req) {
        SecurityUser currentUser = SecurityUtils.currentUserOrThrow();
        String payChannel = normalizeCheckoutChannel(req.getPayMethod());
        String payMode = resolveSupportedPayMode(payChannel);
        BigDecimal amount = scale(req.getAmount());
        Long activityId = validateRechargeActivity(req.getActivityId(), amount);

        closePendingPayments("WALLET_RECHARGE", currentUser.getUserId(), "A new payment was created");

        PaymentOrder paymentOrder = new PaymentOrder();
        paymentOrder.setBusinessType("WALLET_RECHARGE");
        paymentOrder.setBusinessId(currentUser.getUserId());
        paymentOrder.setPaymentNo(buildPaymentNo("R"));
        paymentOrder.setOutTradeNo(buildOutTradeNo("WR", currentUser.getUserId()));
        paymentOrder.setPayChannel(payChannel);
        paymentOrder.setPayMode(payMode);
        paymentOrder.setAmount(amount);
        paymentOrder.setStatus("PAYING");
        paymentOrder.setSubject("钱包充值");
        paymentOrder.setBodyText("用于钱包充值的统一支付收银台流程");
        paymentOrder.setExtraInfo(activityId == null ? null : String.valueOf(activityId));
        paymentOrder.setExpireTime(LocalDateTime.now().plusMinutes(15));
        paymentOrderMapper.insert(paymentOrder);

        return gatewayForMode(payMode).create(paymentOrder);
    }

    public PaymentCapabilityVo capabilities() {
        PaymentCapabilityVo vo = new PaymentCapabilityVo();
        vo.setMode(paymentProperties.resolvedMode());
        vo.setGatewayLabel(paymentProperties.gatewayLabel());
        vo.setSupportedChannels(paymentProperties.supportedChannels());
        vo.setExternalCashier(paymentProperties.usesExternalCashier());
        vo.setManualCompletionEnabled(paymentProperties.isManualCompletionEnabled());
        vo.setChannels(List.of(
                channelCapability("WECHAT", "微信支付"),
                channelCapability("ALIPAY", "支付宝")
        ));
        return vo;
    }

    public PaymentStatusVo status(String paymentNo) {
        PaymentOrder paymentOrder = requireAccessiblePayment(paymentNo);
        paymentOrder = refreshPaymentOrder(paymentOrder);
        return toStatusVo(paymentOrder);
    }

    @Transactional
    public PaymentOrder recordBalanceProductOrderPayment(ProductOrder order) {
        PaymentOrder existing = latestPaymentOrder("PRODUCT_ORDER", order.getId(), Set.of("BALANCE"), REFUNDABLE_PAYMENT_STATUSES);
        if (existing != null) {
            return existing;
        }

        closePendingPayments("PRODUCT_ORDER", order.getId(), "Balance payment completed");

        PaymentOrder paymentOrder = new PaymentOrder();
        paymentOrder.setBusinessType("PRODUCT_ORDER");
        paymentOrder.setBusinessId(order.getId());
        paymentOrder.setPaymentNo(buildPaymentNo("B"));
        paymentOrder.setOutTradeNo(buildOutTradeNo("PB", order.getId()));
        paymentOrder.setPayChannel("BALANCE");
        paymentOrder.setPayMode("BALANCE");
        paymentOrder.setAmount(scale(order.getPayAmount()));
        paymentOrder.setStatus("SUCCESS");
        paymentOrder.setSubject("商品订单余额支付 #" + order.getId());
        paymentOrder.setBodyText("用于商品订单余额支付的站内记账记录");
        paymentOrder.setPaidTime(order.getPayTime() == null ? LocalDateTime.now() : order.getPayTime());
        paymentOrder.setPlatformTradeNo("BALANCE-" + order.getId());
        paymentOrderMapper.insert(paymentOrder);
        return paymentOrder;
    }

    @Transactional
    public PaymentOrder recordBalanceAppointmentPayment(Appointment appointment) {
        PaymentOrder existing = latestPaymentOrder("APPOINTMENT", appointment.getId(), Set.of("BALANCE"), REFUNDABLE_PAYMENT_STATUSES);
        if (existing != null) {
            return existing;
        }

        closePendingPayments("APPOINTMENT", appointment.getId(), "Balance payment completed");

        PaymentOrder paymentOrder = new PaymentOrder();
        paymentOrder.setBusinessType("APPOINTMENT");
        paymentOrder.setBusinessId(appointment.getId());
        paymentOrder.setPaymentNo(buildPaymentNo("AB"));
        paymentOrder.setOutTradeNo(buildOutTradeNo("APB", appointment.getId()));
        paymentOrder.setPayChannel("BALANCE");
        paymentOrder.setPayMode("BALANCE");
        paymentOrder.setAmount(scale(appointment.getAmount()));
        paymentOrder.setStatus("SUCCESS");
        paymentOrder.setSubject(appointmentSubject(appointment));
        paymentOrder.setBodyText("用于服务预约余额支付的站内记账记录");
        paymentOrder.setPaidTime(appointment.getPayTime() == null ? LocalDateTime.now() : appointment.getPayTime());
        paymentOrder.setPlatformTradeNo("BALANCE-APPOINTMENT-" + appointment.getId());
        paymentOrderMapper.insert(paymentOrder);
        return paymentOrder;
    }

    @Transactional
    public PaymentRefund recordProductOrderRefund(ProductOrder order, String reason) {
        PaymentOrder paymentOrder = latestPaymentOrder("PRODUCT_ORDER", order.getId(), null, REFUNDABLE_PAYMENT_STATUSES);
        if (paymentOrder == null) {
            return null;
        }
        return createOrLoadRefund(paymentOrder, scale(order.getPayAmount()),
                reason == null || reason.isBlank() ? "商品订单售后退款" : reason.trim());
    }

    @Transactional
    public PaymentRefund recordAppointmentRefund(Appointment appointment, String reason) {
        PaymentOrder paymentOrder = latestPaymentOrder("APPOINTMENT", appointment.getId(), null, REFUNDABLE_PAYMENT_STATUSES);
        if (paymentOrder == null) {
            return null;
        }
        return createOrLoadRefund(paymentOrder, scale(appointment.getAmount()),
                reason == null || reason.isBlank() ? "服务预约取消退款" : reason.trim());
    }

    @Transactional
    public PaymentStatusVo completePayment(String paymentNo) {
        PaymentOrder paymentOrder = requireAccessiblePayment(paymentNo);
        if (!manualActionAllowed(paymentOrder)) {
            throw new BizException(4715, "Current payment mode does not support manual payment confirmation");
        }
        markPaymentSuccess(paymentOrder, paymentOrder.getPayMode() + "-" + paymentOrder.getPaymentNo(),
                LocalDateTime.now(), "Manual cashier completion");
        return toStatusVo(paymentOrderMapper.selectById(paymentOrder.getId()));
    }

    @Transactional
    public PaymentStatusVo cancelPayment(String paymentNo) {
        PaymentOrder paymentOrder = requireAccessiblePayment(paymentNo);
        if (!manualActionAllowed(paymentOrder)) {
            throw new BizException(4716, "Current payment mode does not support manual payment cancellation");
        }
        closePaymentOrderIfPending(paymentOrder, "FAILED", "User cancelled payment", "用户已取消本次支付");
        return toStatusVo(paymentOrderMapper.selectById(paymentOrder.getId()));
    }

    @Transactional
    public void handleGatewaySuccess(String outTradeNo, String platformTradeNo, LocalDateTime paidTime, String notifyContent) {
        PaymentOrder paymentOrder = paymentOrderMapper.selectOne(new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getOutTradeNo, outTradeNo)
                .last("limit 1"));
        if (paymentOrder == null) {
            return;
        }
        markPaymentSuccess(paymentOrder, platformTradeNo, paidTime, notifyContent);
    }

    @Transactional
    public void handleGatewayClosed(String outTradeNo, String notifyContent) {
        PaymentOrder paymentOrder = paymentOrderMapper.selectOne(new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getOutTradeNo, outTradeNo)
                .last("limit 1"));
        if (paymentOrder == null) {
            return;
        }
        closePaymentOrderIfPending(paymentOrder, "CLOSED", "Gateway closed payment", notifyContent);
    }

    @Transactional
    public void handleGatewayRefundSuccess(String refundNo, String platformRefundNo, LocalDateTime refundedTime, String notifyContent) {
        PaymentRefund refund = paymentRefundMapper.selectOne(new LambdaQueryWrapper<PaymentRefund>()
                .eq(PaymentRefund::getRefundNo, refundNo)
                .last("limit 1"));
        if (refund == null || "SUCCESS".equals(refund.getStatus())) {
            return;
        }
        refund.setStatus("SUCCESS");
        refund.setPlatformRefundNo(platformRefundNo);
        refund.setNotifyContent(notifyContent);
        refund.setRefundedTime(refundedTime == null ? LocalDateTime.now() : refundedTime);
        paymentRefundMapper.updateById(refund);

        PaymentOrder paymentOrder = paymentOrderMapper.selectById(refund.getPaymentOrderId());
        if (paymentOrder != null) {
            syncPaymentAfterRefund(paymentOrder, refund);
        }
    }

    @Transactional
    public void handleGatewayRefundFailed(String refundNo, String notifyContent) {
        PaymentRefund refund = paymentRefundMapper.selectOne(new LambdaQueryWrapper<PaymentRefund>()
                .eq(PaymentRefund::getRefundNo, refundNo)
                .last("limit 1"));
        if (refund == null || "SUCCESS".equals(refund.getStatus())) {
            return;
        }
        refund.setStatus("MANUAL_REQUIRED");
        refund.setNotifyContent(notifyContent);
        paymentRefundMapper.updateById(refund);

        PaymentOrder paymentOrder = paymentOrderMapper.selectById(refund.getPaymentOrderId());
        if (paymentOrder != null) {
            syncPaymentAfterRefund(paymentOrder, refund);
        }
    }

    @Transactional
    public PaymentRefund markManualRefundComplete(String refundNo, String remark) {
        PaymentRefund refund = requireRefund(refundNo);
        PaymentOrder paymentOrder = paymentOrderMapper.selectById(refund.getPaymentOrderId());
        if (paymentOrder == null) {
            throw new BizException(4724, "关联支付单不存在");
        }
        if (!"SUCCESS".equals(refund.getStatus())) {
            refund.setStatus("SUCCESS");
            refund.setNotifyContent(remark == null || remark.isBlank() ? "管理员确认人工退款完成" : remark);
            refund.setRefundedTime(LocalDateTime.now());
            refund.setPlatformRefundNo("MANUAL-COMPLETE-" + refund.getRefundNo());
            paymentRefundMapper.updateById(refund);
        }
        syncPaymentAfterRefund(paymentOrder, refund);
        return refund;
    }

    @Transactional
    public PaymentRefund completeManualRefundToWallet(String refundNo, String remark) {
        PaymentRefund refund = requireRefund(refundNo);
        PaymentOrder paymentOrder = paymentOrderMapper.selectById(refund.getPaymentOrderId());
        if (paymentOrder == null) {
            throw new BizException(4724, "关联支付单不存在");
        }
        if (!"SUCCESS".equals(refund.getStatus())) {
            refundToWallet(paymentOrder, refund, remark == null || remark.isBlank() ? "管理员确认人工退款完成" : remark);
            refund.setStatus("SUCCESS");
            refund.setNotifyContent(remark);
            refund.setRefundedTime(LocalDateTime.now());
            refund.setPlatformRefundNo("MANUAL-WALLET-" + refund.getRefundNo());
            paymentRefundMapper.updateById(refund);
        }
        syncPaymentAfterRefund(paymentOrder, refund);
        return refund;
    }

    @Transactional
    public PaymentRefund markManualRefundFailed(String refundNo, String remark) {
        PaymentRefund refund = requireRefund(refundNo);
        refund.setStatus("FAILED");
        refund.setNotifyContent(remark == null || remark.isBlank() ? "管理员标记人工退款失败" : remark);
        paymentRefundMapper.updateById(refund);
        PaymentOrder paymentOrder = paymentOrderMapper.selectById(refund.getPaymentOrderId());
        if (paymentOrder != null) {
            syncPaymentAfterRefund(paymentOrder, refund);
        }
        return refund;
    }

    @Transactional
    public int closeExpiredPayments() {
        List<PaymentOrder> expired = paymentOrderMapper.selectList(new LambdaQueryWrapper<PaymentOrder>()
                .in(PaymentOrder::getStatus, PENDING_STATUSES)
                .isNotNull(PaymentOrder::getExpireTime)
                .le(PaymentOrder::getExpireTime, LocalDateTime.now())
                .orderByAsc(PaymentOrder::getExpireTime));
        int closed = 0;
        for (PaymentOrder paymentOrder : expired) {
            PaymentGatewayStatus remoteStatus = refreshRemoteStatus(paymentOrder);
            if (remoteStatus != null && "SUCCESS".equals(remoteStatus.getStatus())) {
                markPaymentSuccess(paymentOrder, remoteStatus.getPlatformTradeNo(), remoteStatus.getPaidTime(),
                        remoteStatus.getNotifyContent());
                continue;
            }
            try {
                gatewayFor(paymentOrder).close(paymentOrder);
            } catch (Exception ignored) {
                // Local expiry remains authoritative for stale unpaid BFSM orders.
            }
            if (closePaymentOrderIfPending(paymentOrder, "CLOSED", "Payment expired", "支付单已超时关闭")) {
                expireBusiness(paymentOrder);
                closed += 1;
            }
        }
        return closed;
    }

    private void applyBusinessSuccess(PaymentOrder paymentOrder) {
        if ("PRODUCT_ORDER".equals(paymentOrder.getBusinessType())) {
            applyProductOrderPaymentSuccess(paymentOrder);
            return;
        }
        if ("APPOINTMENT".equals(paymentOrder.getBusinessType())) {
            applyAppointmentPaymentSuccess(paymentOrder);
            return;
        }
        if ("WALLET_RECHARGE".equals(paymentOrder.getBusinessType())) {
            applyWalletRechargeSuccess(paymentOrder);
        }
    }

    private void markPaymentSuccess(PaymentOrder paymentOrder,
                                    String platformTradeNo,
                                    LocalDateTime paidTime,
                                    String notifyContent) {
        if (!PENDING_STATUSES.contains(paymentOrder.getStatus())) {
            return;
        }
        LocalDateTime actualPaidTime = paidTime == null ? LocalDateTime.now() : paidTime;
        int updated = paymentOrderMapper.markSuccessIfPending(
                paymentOrder.getId(),
                platformTradeNo,
                actualPaidTime,
                notifyContent
        );
        if (updated == 0) {
            return;
        }
        PaymentOrder successOrder = paymentOrderMapper.selectById(paymentOrder.getId());
        if (successOrder == null) {
            successOrder = paymentOrder;
            successOrder.setStatus("SUCCESS");
            successOrder.setPaidTime(actualPaidTime);
            successOrder.setPlatformTradeNo(platformTradeNo);
            successOrder.setNotifyContent(notifyContent);
        }
        applyBusinessSuccess(successOrder);
    }

    private PaymentOrder refreshPaymentOrder(PaymentOrder paymentOrder) {
        if (!PENDING_STATUSES.contains(paymentOrder.getStatus())) {
            return paymentOrder;
        }
        PaymentGatewayStatus gatewayStatus = refreshRemoteStatus(paymentOrder);
        if (gatewayStatus == null || gatewayStatus.getStatus() == null) {
            return paymentOrder;
        }
        if ("SUCCESS".equals(gatewayStatus.getStatus())) {
            markPaymentSuccess(paymentOrder, gatewayStatus.getPlatformTradeNo(), gatewayStatus.getPaidTime(), gatewayStatus.getNotifyContent());
            return paymentOrderMapper.selectById(paymentOrder.getId());
        }
        if ("CLOSED".equals(gatewayStatus.getStatus()) || "FAILED".equals(gatewayStatus.getStatus())) {
            closePaymentOrderIfPending(paymentOrder, gatewayStatus.getStatus(), "Gateway status is " + gatewayStatus.getStatus(),
                    gatewayStatus.getNotifyContent());
            return paymentOrderMapper.selectById(paymentOrder.getId());
        }
        return paymentOrder;
    }

    private PaymentGatewayStatus refreshRemoteStatus(PaymentOrder paymentOrder) {
        try {
            return gatewayFor(paymentOrder).query(paymentOrder);
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean closePaymentOrderIfPending(PaymentOrder paymentOrder, String status, String reason, String notifyContent) {
        if (!PENDING_STATUSES.contains(paymentOrder.getStatus())) {
            return false;
        }
        int updated = paymentOrderMapper.closeIfPending(paymentOrder.getId(), status, reason, notifyContent);
        if (updated == 0) {
            return false;
        }
        paymentOrder.setStatus(status);
        paymentOrder.setCloseReason(reason);
        paymentOrder.setNotifyContent(notifyContent);
        return true;
    }

    private void applyProductOrderPaymentSuccess(PaymentOrder paymentOrder) {
        ProductOrder order = productOrderMapper.selectById(paymentOrder.getBusinessId());
        if (order == null) {
            throw new BizException(4702, "关联订单不存在");
        }
        if (!"UNPAID".equals(order.getPayStatus())) {
            return;
        }

        order.setPayMethod(paymentOrder.getPayChannel());
        order.setPayStatus("PAID");
        order.setStatus("PAID");
        order.setPayTime(paymentOrder.getPaidTime());
        order.setPayNo(paymentOrder.getPaymentNo());
        productOrderMapper.updateById(order);

        notificationService.notifyUser(
                order.getUserId(),
                "ORDER",
                "订单支付成功",
                "订单 #" + order.getId() + " 已支付成功，等待商家发货。",
                "/shop/orders/" + order.getId()
        );
        notificationService.notifyAdmins(
                "ORDER",
                "有新订单待发货",
                "订单 #" + order.getId() + " 已完成支付，请及时处理发货。",
                "/admin/product-orders"
        );
    }

    private void applyAppointmentPaymentSuccess(PaymentOrder paymentOrder) {
        Appointment appointment = appointmentMapper.selectById(paymentOrder.getBusinessId());
        if (appointment == null) {
            throw new BizException(4707, "关联预约不存在");
        }
        if (!"UNPAID".equals(normalizeAppointmentPayStatus(appointment)) || !"CREATED".equals(appointment.getStatus())) {
            return;
        }

        appointment.setPayMethod(paymentOrder.getPayChannel());
        appointment.setPayStatus("PAID");
        appointment.setPayTime(paymentOrder.getPaidTime());
        appointment.setPayNo(paymentOrder.getPaymentNo());

        String blockReason = appointmentPaymentBlockReason(appointment);
        if (blockReason != null) {
            appointment.setStatus("CANCELLED");
            appointment.setCancelTime(LocalDateTime.now());
            appointmentMapper.updateById(appointment);
            PaymentRefund refund = createOrLoadRefund(paymentOrder, scale(appointment.getAmount()),
                    "预约支付后档期不可用自动退款：" + blockReason);
            if (refund != null && "SUCCESS".equals(refund.getStatus())) {
                appointment.setPayStatus("REFUNDED");
                appointmentMapper.updateById(appointment);
            }
            notificationService.notifyUser(
                    appointment.getUserId(),
                    "APPOINTMENT",
                    "预约已自动退款",
                    "预约 #" + appointment.getId() + " 支付成功后检测到档期不可用，系统已发起原路退款。",
                    "/appointments"
            );
            return;
        }

        appointment.setStatus("PENDING");
        appointmentMapper.updateById(appointment);

        ServiceItem serviceItem = serviceItemMapper.selectById(appointment.getServiceItemId());
        String serviceName = serviceItem == null ? "服务项目" : serviceItem.getName();
        LocalDateTime endTime = appointmentEndTimeOf(appointment);

        notificationService.notifyUser(
                appointment.getUserId(),
                "APPOINTMENT",
                "预约支付成功",
                "预约 #" + appointment.getId() + " 已支付成功，等待技师确认预约。",
                "/appointments"
        );
        notificationService.notifyUser(
                technicianUserId(appointment.getTechnicianId()),
                "APPOINTMENT",
                "有新的预约待确认",
                "你有一条新的“" + serviceName + "”预约待确认，服务时间为 "
                        + appointment.getAppointmentTime() + " - " + endTime + "。",
                "/tech/appointments"
        );
    }

    private String appointmentPaymentBlockReason(Appointment appointment) {
        if (appointment.getAppointmentTime() == null || appointment.getAppointmentTime().isBefore(LocalDateTime.now())) {
            return "预约时间已过期";
        }
        LocalDateTime endTime = appointmentEndTimeOf(appointment);
        try {
            scheduleConflictChecker.validateTechnicianForAppointment(
                    appointment.getTechnicianId(),
                    appointment.getServiceItemId(),
                    appointment.getAppointmentTime(),
                    endTime
            );
            if (scheduleConflictChecker.hasConflictInRangeExclude(
                    appointment.getTechnicianId(),
                    appointment.getAppointmentTime(),
                    endTime,
                    appointment.getId()
            )) {
                return "该时间段已被其他用户预约";
            }
            return null;
        } catch (BizException ex) {
            return ex.getMessage();
        }
    }

    private void applyWalletRechargeSuccess(PaymentOrder paymentOrder) {
        Long userId = paymentOrder.getBusinessId();
        BigDecimal rechargeAmount = scale(paymentOrder.getAmount());
        BigDecimal giftAmount = loadGiftAmount(paymentOrder.getExtraInfo());
        String rechargeTxnNo = "PAYMENT:RECHARGE:" + paymentOrder.getId();
        if (walletTxnMapper.countByTxnNo(rechargeTxnNo) > 0) {
            return;
        }

        UserWallet wallet = ensureWallet(userId);
        int credited = userWalletMapper.creditRecharge(userId, rechargeAmount, giftAmount);
        if (credited == 0) {
            wallet.setBalance(wallet.getBalance().add(rechargeAmount).add(giftAmount));
            wallet.setTotalRecharge(wallet.getTotalRecharge().add(rechargeAmount));
            wallet.setTotalGift(wallet.getTotalGift().add(giftAmount));
            userWalletMapper.updateById(wallet);
        }
        UserWallet updatedWallet = ensureWallet(userId);
        BigDecimal balanceAfterRecharge = updatedWallet.getBalance().subtract(giftAmount);

        recordTxn(
                rechargeTxnNo,
                userId,
                "RECHARGE",
                rechargeAmount,
                balanceAfterRecharge,
                "PAYMENT_ORDER",
                paymentOrder.getId(),
                ("WECHAT".equals(paymentOrder.getPayChannel()) ? "微信" : "支付宝") + "充值"
        );

        if (giftAmount.compareTo(BigDecimal.ZERO) > 0) {
            Long activityId = parseLong(paymentOrder.getExtraInfo());
            RechargeActivity activity = activityId == null ? null : rechargeActivityMapper.selectById(activityId);
            recordTxn(
                    "PAYMENT:GIFT:" + paymentOrder.getId(),
                    userId,
                    "GIFT",
                    giftAmount,
                    updatedWallet.getBalance(),
                    "PAYMENT_ORDER",
                    paymentOrder.getId(),
                    activity == null ? "充值赠送" : activity.getTitle()
            );
        }

        notificationService.notifyUser(
                userId,
                "WALLET",
                "钱包充值成功",
                "充值单 " + paymentOrder.getPaymentNo() + " 已支付成功，金额已到账。",
                "/wallet"
        );
    }

    private PaymentRefund createOrLoadRefund(PaymentOrder paymentOrder, BigDecimal amount, String reason) {
        PaymentRefund existingRefund = paymentRefundMapper.selectOne(new LambdaQueryWrapper<PaymentRefund>()
                .eq(PaymentRefund::getPaymentOrderId, paymentOrder.getId())
                .orderByDesc(PaymentRefund::getId)
                .last("limit 1"));
        if (existingRefund != null) {
            syncPaymentAfterRefund(paymentOrder, existingRefund);
            return existingRefund;
        }

        PaymentRefund refund = new PaymentRefund();
        refund.setPaymentOrderId(paymentOrder.getId());
        refund.setRefundNo(buildRefundNo(paymentOrder.getBusinessId()));
        refund.setAmount(amount);
        refund.setStatus("CREATED");
        refund.setReason(reason);
        refund.setRefundChannel(paymentOrder.getPayChannel());
        refund.setRefundMode(paymentOrder.getPayMode());
        paymentRefundMapper.insert(refund);

        if (refundsToWallet(paymentOrder)) {
            refundToWallet(paymentOrder, refund, reason);
            refund.setStatus("SUCCESS");
            refund.setPlatformRefundNo("WALLET-REFUND-" + refund.getRefundNo());
            refund.setNotifyContent("Refund credited to wallet");
            refund.setRefundedTime(LocalDateTime.now());
        } else {
            PaymentGatewayRefundResult result = gatewayFor(paymentOrder).refund(paymentOrder, refund);
            refund.setStatus(result.getStatus());
            refund.setPlatformRefundNo(result.getPlatformRefundNo());
            refund.setNotifyContent(result.getNotifyContent());
            refund.setRefundedTime(result.getRefundedTime());
        }
        paymentRefundMapper.updateById(refund);
        syncPaymentAfterRefund(paymentOrder, refund);
        return refund;
    }

    private void syncPaymentAfterRefund(PaymentOrder paymentOrder, PaymentRefund refund) {
        if ("SUCCESS".equals(refund.getStatus())) {
            paymentOrder.setStatus("REFUNDED");
            paymentOrder.setNotifyContent("对应款项已完成原路退款");
            paymentOrder.setFailureReason(null);
            paymentOrderMapper.updateById(paymentOrder);
            markBusinessRefunded(paymentOrder);
            return;
        }
        if ("PROCESSING".equals(refund.getStatus())) {
            paymentOrder.setStatus("REFUNDING");
            paymentOrder.setNotifyContent(refund.getNotifyContent());
            paymentOrderMapper.updateById(paymentOrder);
            return;
        }
        if ("MANUAL_REQUIRED".equals(refund.getStatus())) {
            paymentOrder.setStatus("MANUAL_REQUIRED");
            paymentOrder.setFailureReason(refund.getNotifyContent());
            paymentOrder.setNotifyContent(refund.getNotifyContent());
            paymentOrderMapper.updateById(paymentOrder);
            return;
        }
        if ("FAILED".equals(refund.getStatus())) {
            paymentOrder.setFailureReason(refund.getNotifyContent());
            paymentOrder.setNotifyContent(refund.getNotifyContent());
            paymentOrderMapper.updateById(paymentOrder);
        }
    }

    private void markBusinessRefunded(PaymentOrder paymentOrder) {
        if ("PRODUCT_ORDER".equals(paymentOrder.getBusinessType())) {
            ProductOrder order = productOrderMapper.selectById(paymentOrder.getBusinessId());
            if (order == null) {
                return;
            }
            order.setStatus("REFUNDED");
            order.setPayStatus("REFUNDED");
            if (order.getCloseTime() == null) {
                order.setCloseTime(LocalDateTime.now());
            }
            productOrderMapper.updateById(order);
            return;
        }
        if ("APPOINTMENT".equals(paymentOrder.getBusinessType())) {
            Appointment appointment = appointmentMapper.selectById(paymentOrder.getBusinessId());
            if (appointment == null) {
                return;
            }
            appointment.setPayStatus("REFUNDED");
            if ("PENDING".equals(appointment.getStatus()) || "APPROVED".equals(appointment.getStatus())) {
                appointment.setStatus("CANCELLED");
                appointment.setCancelTime(LocalDateTime.now());
            }
            appointmentMapper.updateById(appointment);
        }
    }

    private boolean refundsToWallet(PaymentOrder paymentOrder) {
        return "BALANCE".equals(paymentOrder.getPayChannel())
                || "BALANCE".equals(paymentOrder.getPayMode())
                || "MANUAL_CASHIER".equals(paymentOrder.getPayMode());
    }

    private void refundToWallet(PaymentOrder paymentOrder, PaymentRefund refund, String remark) {
        String txnNo = "REFUND:" + refund.getRefundNo();
        if (walletTxnMapper.countByTxnNo(txnNo) > 0) {
            return;
        }
        Long userId = refundUserId(paymentOrder);
        if (userId == null) {
            throw new BizException(4725, "无法识别退款收款用户");
        }
        ensureWallet(userId);
        int updated = userWalletMapper.increaseBalance(userId, scale(refund.getAmount()));
        if (updated == 0) {
            UserWallet wallet = ensureWallet(userId);
            wallet.setBalance(wallet.getBalance().add(scale(refund.getAmount())));
            userWalletMapper.updateById(wallet);
        }
        UserWallet updatedWallet = ensureWallet(userId);
        recordTxn(
                txnNo,
                userId,
                "REFUND",
                scale(refund.getAmount()),
                updatedWallet.getBalance(),
                paymentOrder.getBusinessType(),
                paymentOrder.getBusinessId(),
                remark
        );
    }

    private Long refundUserId(PaymentOrder paymentOrder) {
        if ("PRODUCT_ORDER".equals(paymentOrder.getBusinessType())) {
            ProductOrder order = productOrderMapper.selectById(paymentOrder.getBusinessId());
            return order == null ? null : order.getUserId();
        }
        if ("APPOINTMENT".equals(paymentOrder.getBusinessType())) {
            Appointment appointment = appointmentMapper.selectById(paymentOrder.getBusinessId());
            return appointment == null ? null : appointment.getUserId();
        }
        if ("WALLET_RECHARGE".equals(paymentOrder.getBusinessType())) {
            return paymentOrder.getBusinessId();
        }
        return null;
    }

    private void expireBusiness(PaymentOrder paymentOrder) {
        if ("PRODUCT_ORDER".equals(paymentOrder.getBusinessType())) {
            ProductOrder order = productOrderMapper.selectById(paymentOrder.getBusinessId());
            if (order != null && "CREATED".equals(order.getStatus()) && "UNPAID".equals(order.getPayStatus())) {
                restoreStock(order.getId());
                order.setStatus("CANCELLED");
                order.setPayStatus("CANCELLED");
                order.setCloseTime(LocalDateTime.now());
                productOrderMapper.updateById(order);
            }
            return;
        }
        if ("APPOINTMENT".equals(paymentOrder.getBusinessType())) {
            Appointment appointment = appointmentMapper.selectById(paymentOrder.getBusinessId());
            if (appointment != null && "CREATED".equals(appointment.getStatus())
                    && "UNPAID".equals(normalizeAppointmentPayStatus(appointment))) {
                appointment.setStatus("CANCELLED");
                appointment.setPayStatus("CANCELLED");
                appointment.setCancelTime(LocalDateTime.now());
                appointmentMapper.updateById(appointment);
            }
        }
    }

    private void restoreStock(Long orderId) {
        List<ProductOrderItem> items = productOrderItemMapper.selectList(new LambdaQueryWrapper<ProductOrderItem>()
                .eq(ProductOrderItem::getOrderId, orderId));
        for (ProductOrderItem item : items) {
            Product product = productMapper.selectById(item.getProductId());
            if (product == null) {
                continue;
            }
            product.setStock((product.getStock() == null ? 0 : product.getStock()) + item.getQuantity());
            productMapper.updateById(product);
        }
    }

    private PaymentOrder requireAccessiblePayment(String paymentNo) {
        PaymentOrder paymentOrder = paymentOrderMapper.selectOne(new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getPaymentNo, paymentNo)
                .last("limit 1"));
        if (paymentOrder == null) {
            throw new BizException(404, "支付单不存在");
        }

        SecurityUser currentUser = SecurityUtils.currentUserOrThrow();
        if ("ADMIN".equals(currentUser.getRoleCode())) {
            return paymentOrder;
        }

        if ("PRODUCT_ORDER".equals(paymentOrder.getBusinessType())) {
            ProductOrder order = productOrderMapper.selectById(paymentOrder.getBusinessId());
            if (order == null || !currentUser.getUserId().equals(order.getUserId())) {
                throw new BizException(403, "无权访问该支付单");
            }
            return paymentOrder;
        }

        if ("APPOINTMENT".equals(paymentOrder.getBusinessType())) {
            Appointment appointment = appointmentMapper.selectById(paymentOrder.getBusinessId());
            if (appointment == null || !currentUser.getUserId().equals(appointment.getUserId())) {
                throw new BizException(403, "无权访问该支付单");
            }
            return paymentOrder;
        }

        if ("WALLET_RECHARGE".equals(paymentOrder.getBusinessType())
                && currentUser.getUserId().equals(paymentOrder.getBusinessId())) {
            return paymentOrder;
        }
        throw new BizException(403, "无权访问该支付单");
    }

    private PaymentRefund requireRefund(String refundNo) {
        PaymentRefund refund = paymentRefundMapper.selectOne(new LambdaQueryWrapper<PaymentRefund>()
                .eq(PaymentRefund::getRefundNo, refundNo)
                .last("limit 1"));
        if (refund == null) {
            throw new BizException(404, "退款单不存在");
        }
        return refund;
    }

    private ProductOrder requireCurrentUserOrder(Long orderId, Long userId) {
        ProductOrder order = productOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(404, "订单不存在");
        }
        if (!userId.equals(order.getUserId())) {
            throw new BizException(403, "只能为自己的订单发起支付");
        }
        return order;
    }

    private Appointment requireCurrentUserAppointment(Long appointmentId, Long userId) {
        Appointment appointment = appointmentMapper.selectById(appointmentId);
        if (appointment == null) {
            throw new BizException(404, "预约不存在");
        }
        if (!userId.equals(appointment.getUserId())) {
            throw new BizException(403, "只能为自己的预约发起支付");
        }
        return appointment;
    }

    private void closePendingPayments(String businessType, Long businessId, String reason) {
        List<PaymentOrder> payments = paymentOrderMapper.selectList(new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getBusinessType, businessType)
                .eq(PaymentOrder::getBusinessId, businessId)
                .in(PaymentOrder::getStatus, PENDING_STATUSES));
        for (PaymentOrder item : payments) {
            closePaymentOrderIfPending(item, "CLOSED", reason, reason);
        }
    }

    private Long validateRechargeActivity(Long activityId, BigDecimal amount) {
        if (activityId == null) {
            return null;
        }

        RechargeActivity activity = rechargeActivityMapper.selectById(activityId);
        if (activity == null || !isActivityActive(activity)) {
            throw new BizException(4703, "充值活动当前不可用");
        }
        if (activity.getRechargeAmount() == null || activity.getGiftAmount() == null
                || scale(activity.getRechargeAmount()).compareTo(amount) != 0) {
            throw new BizException(4704, "充值金额与所选活动不匹配");
        }
        return activityId;
    }

    private boolean isActivityActive(RechargeActivity activity) {
        LocalDateTime now = LocalDateTime.now();
        boolean started = activity.getStartTime() == null || !activity.getStartTime().isAfter(now);
        boolean notEnded = activity.getEndTime() == null || !activity.getEndTime().isBefore(now);
        return "ACTIVE".equals(activity.getStatus()) && started && notEnded;
    }

    private BigDecimal loadGiftAmount(String extraInfo) {
        Long activityId = parseLong(extraInfo);
        if (activityId == null) {
            return scale(BigDecimal.ZERO);
        }
        RechargeActivity activity = rechargeActivityMapper.selectById(activityId);
        if (activity == null || activity.getGiftAmount() == null) {
            return scale(BigDecimal.ZERO);
        }
        return scale(activity.getGiftAmount());
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private UserWallet ensureWallet(Long userId) {
        UserWallet wallet = userWalletMapper.selectById(userId);
        if (wallet != null) {
            wallet.setBalance(scale(wallet.getBalance()));
            wallet.setTotalRecharge(scale(wallet.getTotalRecharge()));
            wallet.setTotalGift(scale(wallet.getTotalGift()));
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

    private PaymentOrder latestPaymentOrder(String businessType,
                                            Long businessId,
                                            Collection<String> channels,
                                            Collection<String> statuses) {
        LambdaQueryWrapper<PaymentOrder> wrapper = new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getBusinessType, businessType)
                .eq(PaymentOrder::getBusinessId, businessId)
                .orderByDesc(PaymentOrder::getId)
                .last("limit 1");
        if (channels != null && !channels.isEmpty()) {
            wrapper.in(PaymentOrder::getPayChannel, channels);
        }
        if (statuses != null && !statuses.isEmpty()) {
            wrapper.in(PaymentOrder::getStatus, statuses);
        }
        return paymentOrderMapper.selectOne(wrapper);
    }

    private String normalizeCheckoutChannel(String payChannel) {
        if (payChannel == null) {
            throw new BizException(4705, "支付渠道不能为空");
        }
        String normalized = payChannel.trim().toUpperCase();
        if (!ONLINE_PAY_CHANNELS.contains(normalized)) {
            throw new BizException(4706, "当前仅支持微信支付和支付宝支付渠道");
        }
        return normalized;
    }

    private String resolveSupportedPayMode(String payChannel) {
        String payMode = paymentProperties.resolveModeForChannel(payChannel);
        if (payMode != null) {
            return payMode;
        }
        if ("ALIPAY_PAGE".equalsIgnoreCase(paymentProperties.resolvedMode())) {
            throw new BizException(4714, "当前环境仅接入支付宝收银台，请选择支付宝支付");
        }
        if ("WECHAT_NATIVE".equalsIgnoreCase(paymentProperties.resolvedMode())) {
            throw new BizException(4714, "当前环境仅接入微信收银台，请选择微信支付");
        }
        throw new BizException(4714, "当前支付通道暂不支持所选支付方式");
    }

    private PaymentGateway gatewayForMode(String mode) {
        return paymentGatewayRouter.resolve(mode);
    }

    private PaymentGateway gatewayFor(PaymentOrder paymentOrder) {
        String mode = paymentOrder.getPayMode() == null ? paymentProperties.resolveModeForChannel(paymentOrder.getPayChannel()) : paymentOrder.getPayMode();
        return paymentGatewayRouter.resolve(mode);
    }

    private PaymentChannelCapabilityVo channelCapability(String channel, String label) {
        String mode = paymentProperties.resolveModeForChannel(channel);
        PaymentChannelCapabilityVo vo = new PaymentChannelCapabilityVo();
        vo.setChannel(channel);
        vo.setLabel(label);
        vo.setPayMode(mode);
        vo.setEnabled(mode != null);
        vo.setExternalCashier(paymentProperties.usesExternalCashier(mode));
        vo.setQrCode(paymentProperties.usesQrCode(mode));
        vo.setManualCompletion(paymentProperties.allowsManualCompletion(mode));
        return vo;
    }

    private PaymentStatusVo toStatusVo(PaymentOrder paymentOrder) {
        PaymentStatusVo vo = new PaymentStatusVo();
        vo.setPaymentNo(paymentOrder.getPaymentNo());
        vo.setBusinessType(paymentOrder.getBusinessType());
        vo.setBusinessId(paymentOrder.getBusinessId());
        vo.setPayChannel(paymentOrder.getPayChannel());
        vo.setPayMode(paymentOrder.getPayMode());
        vo.setAmount(paymentOrder.getAmount());
        vo.setStatus(paymentOrder.getStatus());
        vo.setStatusLabel(statusLabel(paymentOrder.getStatus()));
        vo.setSubject(paymentOrder.getSubject());
        vo.setNextAction(nextAction(paymentOrder));
        vo.setNotifyContent(paymentOrder.getNotifyContent());
        vo.setCloseReason(paymentOrder.getCloseReason());
        vo.setFailureReason(paymentOrder.getFailureReason());
        vo.setExpireTime(paymentOrder.getExpireTime());
        vo.setPaidTime(paymentOrder.getPaidTime());
        boolean manualActionAllowed = manualActionAllowed(paymentOrder);
        boolean canOperate = manualActionAllowed && PENDING_STATUSES.contains(paymentOrder.getStatus());
        vo.setCanComplete(canOperate);
        vo.setCanCancel(canOperate);
        return vo;
    }

    private boolean manualActionAllowed(PaymentOrder paymentOrder) {
        return paymentProperties.allowsManualCompletion(paymentOrder.getPayMode());
    }

    private String nextAction(PaymentOrder paymentOrder) {
        if ("PAYING".equals(paymentOrder.getStatus()) || "CREATED".equals(paymentOrder.getStatus())) {
            if ("MANUAL_CASHIER".equals(paymentOrder.getPayMode())) {
                return "MANUAL_CONFIRM";
            }
            if ("WECHAT_NATIVE".equals(paymentOrder.getPayMode())) {
                return "SCAN_QR";
            }
            if ("ALIPAY_PAGE".equals(paymentOrder.getPayMode())) {
                return "OPEN_REDIRECT_FORM";
            }
            return "WAITING_PAYMENT";
        }
        if ("SUCCESS".equals(paymentOrder.getStatus())) {
            return "REFRESH_BUSINESS";
        }
        if ("REFUNDING".equals(paymentOrder.getStatus())) {
            return "WAIT_REFUND";
        }
        if ("MANUAL_REQUIRED".equals(paymentOrder.getStatus())) {
            return "CONTACT_ADMIN";
        }
        if ("CLOSED".equals(paymentOrder.getStatus()) || "FAILED".equals(paymentOrder.getStatus())) {
            return "RECREATE_PAYMENT";
        }
        return "NONE";
    }

    private String buildPaymentNo(String prefix) {
        return prefix
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
                + ThreadLocalRandom.current().nextInt(100, 1000);
    }

    private String buildOutTradeNo(String prefix, Long businessId) {
        return prefix
                + businessId
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
                + ThreadLocalRandom.current().nextInt(10, 100);
    }

    private String buildRefundNo(Long businessId) {
        return "RF"
                + businessId
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
                + ThreadLocalRandom.current().nextInt(10, 100);
    }

    private String statusLabel(String status) {
        return switch (status) {
            case "CREATED" -> "已创建";
            case "PAYING" -> "待支付";
            case "SUCCESS" -> "支付成功";
            case "FAILED" -> "支付失败";
            case "CLOSED" -> "已关闭";
            case "REFUNDING" -> "退款处理中";
            case "MANUAL_REQUIRED" -> "需人工处理";
            case "REFUNDED" -> "已退款";
            default -> status;
        };
    }

    private String normalizeAppointmentPayStatus(Appointment appointment) {
        if (appointment.getPayStatus() == null || appointment.getPayStatus().isBlank()) {
            return "UNPAID";
        }
        return appointment.getPayStatus();
    }

    private Long technicianUserId(Long technicianId) {
        if (technicianId == null) {
            return null;
        }
        Technician technician = technicianMapper.selectById(technicianId);
        return technician == null ? null : technician.getUserId();
    }

    private String appointmentSubject(Appointment appointment) {
        ServiceItem item = appointment.getServiceItemId() == null ? null : serviceItemMapper.selectById(appointment.getServiceItemId());
        String serviceName = item == null ? "服务预约" : item.getName();
        return serviceName + " 预约支付 #" + appointment.getId();
    }

    private LocalDateTime appointmentEndTimeOf(Appointment appointment) {
        if (appointment.getAppointmentEndTime() != null) {
            return appointment.getAppointmentEndTime();
        }
        int duration = appointment.getDurationMinSnapshot() == null || appointment.getDurationMinSnapshot() <= 0
                ? 60 : appointment.getDurationMinSnapshot();
        return appointment.getAppointmentTime().plusMinutes(duration);
    }

    private BigDecimal scale(BigDecimal value) {
        BigDecimal safeValue = value == null ? BigDecimal.ZERO : value;
        return safeValue.setScale(2, RoundingMode.HALF_UP);
    }
}
