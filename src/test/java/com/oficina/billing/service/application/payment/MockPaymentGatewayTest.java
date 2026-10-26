package com.oficina.billing.service.application.payment;

import com.oficina.billing.service.domain.Orcamento;
import com.oficina.billing.service.domain.StatusOrcamento;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MockPaymentGateway - Provedor simulado")
class MockPaymentGatewayTest {

    private final MockPaymentGateway gateway = new MockPaymentGateway();

    @Test
    @DisplayName("Deve criar checkout ficticio para o orcamento")
    void shouldCreateMockCheckout() {
        Orcamento orcamento = new Orcamento(1L, "ORC-1", "12345678909", 100.0,
            StatusOrcamento.PENDENTE);

        CheckoutResult result = gateway.criarCheckout(orcamento);

        assertTrue(result.mock());
        assertEquals("MOCK-PREF-1", result.preferenceId());
        assertTrue(result.initPoint().contains("MOCK-PREF-1"));
        assertEquals(result.initPoint(), result.sandboxInitPoint());
    }

    @Test
    @DisplayName("Deve aprovar qualquer pagamento consultado no mock")
    void shouldApprovePayment() {
        PaymentResult result = gateway.consultarPagamento("MP-1");

        assertTrue(result.approved());
        assertEquals("approved", result.status());
        assertEquals("MP-1", result.paymentId());
        assertNull(result.externalReference());
    }

    @Test
    @DisplayName("Deve aceitar qualquer assinatura no mock")
    void shouldAcceptAnySignature() {
        assertTrue(gateway.validarAssinatura("1", null, null));
        assertTrue(gateway.isMock());
    }
}
