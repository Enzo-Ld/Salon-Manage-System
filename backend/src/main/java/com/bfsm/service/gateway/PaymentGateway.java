package com.bfsm.service.gateway;

import com.bfsm.entity.PaymentOrder;
import com.bfsm.entity.PaymentRefund;
import com.bfsm.vo.PaymentCreateVo;

public interface PaymentGateway {
    boolean supports(String mode);

    PaymentCreateVo create(PaymentOrder order);

    default PaymentGatewayStatus query(PaymentOrder order) {
        return null;
    }

    default PaymentGatewayStatus close(PaymentOrder order) {
        PaymentGatewayStatus status = new PaymentGatewayStatus();
        status.setStatus("CLOSED");
        status.setNotifyContent("Payment closed locally");
        return status;
    }

    default PaymentGatewayRefundResult refund(PaymentOrder order, PaymentRefund refund) {
        return PaymentGatewayRefundResult.manualRequired("Gateway refund is not implemented for mode " + order.getPayMode());
    }
}
