package com.bfsm.service.gateway;

import com.bfsm.common.BizException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PaymentGatewayRouter {
    private final List<PaymentGateway> gateways;

    public PaymentGatewayRouter(List<PaymentGateway> gateways) {
        this.gateways = gateways;
    }

    public PaymentGateway resolve(String mode) {
        return gateways.stream()
                .filter(gateway -> gateway.supports(mode))
                .findFirst()
                .orElseThrow(() -> new BizException(4710, "Unsupported payment mode: " + mode));
    }
}
