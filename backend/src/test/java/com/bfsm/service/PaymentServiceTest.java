package com.bfsm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bfsm.common.BizException;
import com.bfsm.config.PaymentProperties;
import com.bfsm.dto.PaymentCreateReq;
import com.bfsm.dto.RechargeReq;
import com.bfsm.entity.PaymentOrder;
import com.bfsm.entity.PaymentRefund;
import com.bfsm.entity.ProductOrder;
import com.bfsm.entity.RechargeActivity;
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
import com.bfsm.service.gateway.ManualPaymentGateway;
import com.bfsm.service.gateway.PaymentGatewayRouter;
import com.bfsm.vo.PaymentCreateVo;
import com.bfsm.vo.PaymentStatusVo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {
    @Mock
    private PaymentOrderMapper paymentOrderMapper;
    @Mock
    private PaymentRefundMapper paymentRefundMapper;
    @Mock
    private ProductOrderMapper productOrderMapper;
    @Mock
    private ProductOrderItemMapper productOrderItemMapper;
    @Mock
    private ProductMapper productMapper;
    @Mock
    private AppointmentMapper appointmentMapper;
    @Mock
    private ServiceItemMapper serviceItemMapper;
    @Mock
    private TechnicianMapper technicianMapper;
    @Mock
    private UserWalletMapper userWalletMapper;
    @Mock
    private ScheduleConflictChecker scheduleConflictChecker;
    @Mock
    private WalletTxnMapper walletTxnMapper;
    @Mock
    private RechargeActivityMapper rechargeActivityMapper;
    @Mock
    private NotificationService notificationService;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        PaymentProperties properties = new PaymentProperties();
        paymentService = new PaymentService(
                paymentOrderMapper,
                paymentRefundMapper,
                productOrderMapper,
                productOrderItemMapper,
                productMapper,
                appointmentMapper,
                serviceItemMapper,
                technicianMapper,
                userWalletMapper,
                walletTxnMapper,
                rechargeActivityMapper,
                notificationService,
                new PaymentGatewayRouter(List.of(new ManualPaymentGateway())),
                properties,
                scheduleConflictChecker
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createWalletRechargePayment_shouldInsertPendingPaymentOrder() {
        setAuthentication(5L, "q", "USER");
        when(paymentOrderMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        RechargeReq req = new RechargeReq();
        req.setAmount(new BigDecimal("100.00"));
        req.setPayMethod("WECHAT");

        PaymentCreateVo result = paymentService.createWalletRechargePayment(req);

        ArgumentCaptor<PaymentOrder> captor = ArgumentCaptor.forClass(PaymentOrder.class);
        verify(paymentOrderMapper).insert(captor.capture());
        PaymentOrder inserted = captor.getValue();
        assertEquals("WALLET_RECHARGE", inserted.getBusinessType());
        assertEquals(5L, inserted.getBusinessId());
        assertEquals("WECHAT", inserted.getPayChannel());
        assertEquals("MANUAL_CASHIER", inserted.getPayMode());
        assertEquals("PAYING", inserted.getStatus());
        assertEquals(new BigDecimal("100.00"), inserted.getAmount());
        assertNotNull(inserted.getExpireTime());
        assertEquals(inserted.getPaymentNo(), result.getPaymentNo());
        assertEquals("MANUAL", result.getCheckoutType());
        assertEquals("待支付", result.getStatusLabel());
    }

    @Test
    void createProductOrderPayment_shouldRejectPaidOrder() {
        setAuthentication(5L, "q", "USER");
        ProductOrder order = new ProductOrder();
        order.setId(20L);
        order.setUserId(5L);
        order.setStatus("PAID");
        order.setPayStatus("PAID");
        when(productOrderMapper.selectById(20L)).thenReturn(order);

        PaymentCreateReq req = new PaymentCreateReq();
        req.setPayChannel("ALIPAY");

        BizException ex = assertThrows(BizException.class, () -> paymentService.createProductOrderPayment(20L, req));
        assertEquals(4701, ex.getCode());
    }

    @Test
    void completePayment_shouldApplyWalletRechargeOnceAndNotifyUser() {
        setAuthentication(5L, "q", "USER");

        PaymentOrder paymentOrder = pendingPayment(1L, "R202604150001", "WALLET_RECHARGE", 5L, "WECHAT", "100.00");
        paymentOrder.setExtraInfo("2");

        RechargeActivity activity = new RechargeActivity();
        activity.setId(2L);
        activity.setTitle("充100送10");
        activity.setRechargeAmount(new BigDecimal("100.00"));
        activity.setGiftAmount(new BigDecimal("10.00"));
        activity.setStatus("ACTIVE");

        UserWallet wallet = new UserWallet();
        wallet.setUserId(5L);
        wallet.setBalance(new BigDecimal("200.00"));
        wallet.setTotalRecharge(new BigDecimal("300.00"));
        wallet.setTotalGift(new BigDecimal("20.00"));

        PaymentOrder successOrder = successPayment(paymentOrder);

        when(paymentOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(paymentOrder);
        when(paymentOrderMapper.markSuccessIfPending(eq(1L), any(), any(), any())).thenReturn(1);
        when(paymentOrderMapper.selectById(1L)).thenReturn(successOrder);
        when(userWalletMapper.selectById(5L)).thenReturn(wallet);
        when(rechargeActivityMapper.selectById(2L)).thenReturn(activity);

        PaymentStatusVo result = paymentService.completePayment("R202604150001");

        ArgumentCaptor<UserWallet> walletCaptor = ArgumentCaptor.forClass(UserWallet.class);
        verify(userWalletMapper).updateById(walletCaptor.capture());
        UserWallet updatedWallet = walletCaptor.getValue();
        assertEquals(new BigDecimal("310.00"), updatedWallet.getBalance());
        assertEquals(new BigDecimal("400.00"), updatedWallet.getTotalRecharge());
        assertEquals(new BigDecimal("30.00"), updatedWallet.getTotalGift());

        ArgumentCaptor<WalletTxn> txnCaptor = ArgumentCaptor.forClass(WalletTxn.class);
        verify(walletTxnMapper, times(2)).insert(txnCaptor.capture());
        List<WalletTxn> txns = txnCaptor.getAllValues();
        assertEquals("RECHARGE", txns.get(0).getTxnType());
        assertEquals(new BigDecimal("100.00"), txns.get(0).getAmount());
        assertEquals("GIFT", txns.get(1).getTxnType());
        assertEquals(new BigDecimal("10.00"), txns.get(1).getAmount());

        verify(notificationService).notifyUser(
                eq(5L),
                eq("WALLET"),
                eq("钱包充值成功"),
                eq("充值单 R202604150001 已支付成功，金额已到账。"),
                eq("/wallet")
        );
        assertEquals("支付成功", result.getStatusLabel());
    }

    @Test
    void completePayment_shouldApplyProductOrderPaymentAndNotify() {
        setAuthentication(5L, "q", "USER");

        PaymentOrder paymentOrder = pendingPayment(2L, "P202604150001", "PRODUCT_ORDER", 20L, "ALIPAY", "299.00");

        ProductOrder order = new ProductOrder();
        order.setId(20L);
        order.setUserId(5L);
        order.setPayStatus("UNPAID");
        order.setStatus("CREATED");

        PaymentOrder successOrder = successPayment(paymentOrder);

        when(paymentOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(paymentOrder);
        when(paymentOrderMapper.markSuccessIfPending(eq(2L), any(), any(), any())).thenReturn(1);
        when(paymentOrderMapper.selectById(2L)).thenReturn(successOrder);
        when(productOrderMapper.selectById(20L)).thenReturn(order);

        PaymentStatusVo result = paymentService.completePayment("P202604150001");

        ArgumentCaptor<ProductOrder> orderCaptor = ArgumentCaptor.forClass(ProductOrder.class);
        verify(productOrderMapper).updateById(orderCaptor.capture());
        ProductOrder updatedOrder = orderCaptor.getValue();
        assertEquals("PAID", updatedOrder.getStatus());
        assertEquals("PAID", updatedOrder.getPayStatus());
        assertEquals("ALIPAY", updatedOrder.getPayMethod());
        assertEquals("P202604150001", updatedOrder.getPayNo());

        verify(notificationService).notifyUser(
                eq(5L),
                eq("ORDER"),
                eq("订单支付成功"),
                eq("订单 #20 已支付成功，等待商家发货。"),
                eq("/shop/orders/20")
        );
        verify(notificationService).notifyAdmins(
                eq("ORDER"),
                eq("有新订单待发货"),
                eq("订单 #20 已完成支付，请及时处理发货。"),
                eq("/admin/product-orders")
        );
        verify(walletTxnMapper, never()).insert(any(WalletTxn.class));
        assertEquals("支付成功", result.getStatusLabel());
    }

    @Test
    void recordBalanceProductOrderPayment_shouldInsertBalancePaymentOrder() {
        ProductOrder order = new ProductOrder();
        order.setId(88L);
        order.setPayAmount(new BigDecimal("129.00"));
        order.setPayTime(LocalDateTime.now());

        when(paymentOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(paymentOrderMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        PaymentOrder result = paymentService.recordBalanceProductOrderPayment(order);

        ArgumentCaptor<PaymentOrder> captor = ArgumentCaptor.forClass(PaymentOrder.class);
        verify(paymentOrderMapper).insert(captor.capture());
        PaymentOrder inserted = captor.getValue();
        assertEquals("BALANCE", inserted.getPayChannel());
        assertEquals("BALANCE", inserted.getPayMode());
        assertEquals("SUCCESS", inserted.getStatus());
        assertEquals(new BigDecimal("129.00"), inserted.getAmount());
        assertEquals(inserted.getPaymentNo(), result.getPaymentNo());
    }

    @Test
    void recordProductOrderRefund_shouldRefundBalancePaymentToWalletAndMarkPaymentRefunded() {
        ProductOrder order = new ProductOrder();
        order.setId(66L);
        order.setUserId(5L);
        order.setPayAmount(new BigDecimal("39.00"));

        PaymentOrder paymentOrder = new PaymentOrder();
        paymentOrder.setId(9L);
        paymentOrder.setPaymentNo("P202604150099");
        paymentOrder.setBusinessType("PRODUCT_ORDER");
        paymentOrder.setBusinessId(66L);
        paymentOrder.setPayChannel("BALANCE");
        paymentOrder.setPayMode("BALANCE");
        paymentOrder.setStatus("SUCCESS");

        UserWallet wallet = new UserWallet();
        wallet.setUserId(5L);
        wallet.setBalance(new BigDecimal("20.00"));
        wallet.setTotalRecharge(BigDecimal.ZERO);
        wallet.setTotalGift(BigDecimal.ZERO);

        when(paymentOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(paymentOrder);
        when(paymentRefundMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(productOrderMapper.selectById(66L)).thenReturn(order);
        when(userWalletMapper.selectById(5L)).thenReturn(wallet);

        PaymentRefund refund = paymentService.recordProductOrderRefund(order, "同意售后退款");

        ArgumentCaptor<PaymentRefund> refundCaptor = ArgumentCaptor.forClass(PaymentRefund.class);
        verify(paymentRefundMapper).insert(refundCaptor.capture());
        PaymentRefund insertedRefund = refundCaptor.getValue();
        assertEquals(9L, insertedRefund.getPaymentOrderId());
        assertEquals(new BigDecimal("39.00"), insertedRefund.getAmount());
        assertEquals("同意售后退款", insertedRefund.getReason());

        verify(paymentRefundMapper).updateById(any(PaymentRefund.class));
        verify(userWalletMapper).updateById(any(UserWallet.class));
        verify(paymentOrderMapper).updateById(any(PaymentOrder.class));
        assertEquals("SUCCESS", refund.getStatus());
    }

    private PaymentOrder pendingPayment(Long id, String paymentNo, String businessType, Long businessId,
                                        String channel, String amount) {
        PaymentOrder paymentOrder = new PaymentOrder();
        paymentOrder.setId(id);
        paymentOrder.setPaymentNo(paymentNo);
        paymentOrder.setBusinessType(businessType);
        paymentOrder.setBusinessId(businessId);
        paymentOrder.setPayChannel(channel);
        paymentOrder.setPayMode("MANUAL_CASHIER");
        paymentOrder.setAmount(new BigDecimal(amount));
        paymentOrder.setStatus("PAYING");
        return paymentOrder;
    }

    private PaymentOrder successPayment(PaymentOrder paymentOrder) {
        PaymentOrder successOrder = new PaymentOrder();
        successOrder.setId(paymentOrder.getId());
        successOrder.setPaymentNo(paymentOrder.getPaymentNo());
        successOrder.setBusinessType(paymentOrder.getBusinessType());
        successOrder.setBusinessId(paymentOrder.getBusinessId());
        successOrder.setPayChannel(paymentOrder.getPayChannel());
        successOrder.setPayMode(paymentOrder.getPayMode());
        successOrder.setAmount(paymentOrder.getAmount());
        successOrder.setStatus("SUCCESS");
        successOrder.setPaidTime(LocalDateTime.now());
        successOrder.setSubject(paymentOrder.getSubject());
        successOrder.setExtraInfo(paymentOrder.getExtraInfo());
        return successOrder;
    }

    private void setAuthentication(Long userId, String username, String roleCode) {
        SecurityUser principal = new SecurityUser(userId, username, "", roleCode, true);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
