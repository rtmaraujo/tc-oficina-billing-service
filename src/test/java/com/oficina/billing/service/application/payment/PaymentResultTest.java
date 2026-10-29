package com.oficina.billing.service.application.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("PaymentResult - Estados finais do pagamento")
class PaymentResultTest {

    @Test
    @DisplayName("Aprovado e sempre final")
    void approvedIsFinal() {
        assertTrue(new PaymentResult("1", "approved", true, "1", "ok").finalizado());
    }

    @Test
    @DisplayName("Estados intermediarios nao sao finais")
    void pendingIsNotFinal() {
        assertFalse(new PaymentResult("1", "pending", false, "1", "pending").finalizado());
        assertFalse(new PaymentResult("1", "in_process", false, "1", "in_process").finalizado());
        assertFalse(new PaymentResult("1", "authorized", false, "1", "authorized").finalizado());
        assertFalse(new PaymentResult("1", null, false, "1", null).finalizado());
    }

    @Test
    @DisplayName("Estados de recusa, cancelamento e estorno sao finais")
    void failureStatesAreFinal() {
        assertTrue(new PaymentResult("1", "rejected", false, "1", "rejected").finalizado());
        assertTrue(new PaymentResult("1", "REJECTED", false, "1", "rejected").finalizado());
        assertTrue(new PaymentResult("1", "cancelled", false, "1", "cancelled").finalizado());
        assertTrue(new PaymentResult("1", "refunded", false, "1", "refunded").finalizado());
        assertTrue(new PaymentResult("1", "charged_back", false, "1", "charged_back").finalizado());
    }
}
