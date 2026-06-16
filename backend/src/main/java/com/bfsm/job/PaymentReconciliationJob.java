package com.bfsm.job;

import com.bfsm.service.PaymentService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PaymentReconciliationJob {
    private final PaymentService paymentService;

    public PaymentReconciliationJob(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Scheduled(fixedDelayString = "${bfsm.payment.reconcile-fixed-delay-ms:60000}")
    public void closeExpiredPayments() {
        paymentService.closeExpiredPayments();
    }
}
