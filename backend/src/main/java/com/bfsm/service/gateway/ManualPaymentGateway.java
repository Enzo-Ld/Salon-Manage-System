package com.bfsm.service.gateway;

import com.bfsm.entity.PaymentOrder;
import com.bfsm.entity.PaymentRefund;
import com.bfsm.vo.PaymentCreateVo;
import org.springframework.stereotype.Component;

@Component
public class ManualPaymentGateway implements PaymentGateway {
    @Override
    public boolean supports(String mode) {
        return "MANUAL_CASHIER".equalsIgnoreCase(mode);
    }

    @Override
    public PaymentCreateVo create(PaymentOrder order) {
        PaymentCreateVo vo = new PaymentCreateVo();
        vo.setPaymentNo(order.getPaymentNo());
        vo.setBusinessType(order.getBusinessType());
        vo.setBusinessId(order.getBusinessId());
        vo.setPayChannel(order.getPayChannel());
        vo.setPayMode(order.getPayMode());
        vo.setAmount(order.getAmount());
        vo.setStatus(order.getStatus());
        vo.setStatusLabel("待支付");
        vo.setSubject(order.getSubject());
        vo.setExpireTime(order.getExpireTime());
        vo.setCheckoutType("MANUAL");
        vo.setPayUrl("cashier://payment/" + order.getPaymentNo());
        vo.setQrCodeText("BFSM-" + order.getPayChannel() + "-" + order.getPaymentNo());
        vo.setFormHtml(null);
        return vo;
    }

    @Override
    public PaymentGatewayRefundResult refund(PaymentOrder order, PaymentRefund refund) {
        return PaymentGatewayRefundResult.success(
                "MANUAL-REFUND-" + refund.getRefundNo(),
                "Manual cashier refund recorded to wallet"
        );
    }
}
