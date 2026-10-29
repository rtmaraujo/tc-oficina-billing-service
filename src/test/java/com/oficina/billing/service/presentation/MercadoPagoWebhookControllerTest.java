package com.oficina.billing.service.presentation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oficina.billing.service.application.BillingApplicationService;
import com.oficina.billing.service.application.payment.MockPaymentGateway;
import com.oficina.billing.service.application.payment.PaymentGateway;
import com.oficina.billing.service.domain.Orcamento;
import com.oficina.billing.service.domain.StatusOrcamento;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MercadoPagoWebhookController - Recebimento de webhooks")
class MercadoPagoWebhookControllerTest {

    @Mock
    private BillingApplicationService billingService;

    private MercadoPagoWebhookController controller;
    private PaymentGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = mock(PaymentGateway.class);
        controller = new MercadoPagoWebhookController(gateway, billingService, new ObjectMapper());
    }

    @Test
    @DisplayName("Deve ignorar webhook sem data.id")
    void shouldIgnoreWebhookWithoutDataId() {
        ResponseEntity<String> response = controller.receber(null, null, null, null, "{}");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("ignorado", response.getBody());
        verifyNoInteractions(billingService);
    }

    @Test
    @DisplayName("Deve ignorar corpo nulo")
    void shouldIgnoreNullBody() {
        assertEquals("ignorado", controller.receber(null, null, null, null, null).getBody());
    }

    @Test
    @DisplayName("Deve rejeitar assinatura invalida")
    void shouldRejectInvalidSignature() {
        when(gateway.validarAssinatura(eq("123"), any(), any())).thenReturn(false);

        ResponseEntity<String> response = controller.receber("assinatura", "req-1",
            "123", 1L, "{}");

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("assinatura invalida", response.getBody());
        verifyNoInteractions(billingService);
    }

    @Test
    @DisplayName("Deve processar pagamento aprovado usando orcamentoId da query")
    void shouldProcessApprovedPayment() {
        when(gateway.validarAssinatura(eq("123"), any(), any())).thenReturn(true);
        when(gateway.consultarPagamento("123"))
            .thenReturn(new com.oficina.billing.service.application.payment.PaymentResult(
                "123", "approved", true, "1", "ok"));
        when(billingService.processarPagamento(eq(1L), any())).thenReturn(orcamento());

        ResponseEntity<String> response = controller.receber("assinatura", "req-1",
            "123", 1L, "{}");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(billingService).processarPagamento(eq(1L), any());
    }

    @Test
    @DisplayName("Deve obter data.id e external_reference do corpo JSON")
    void shouldReadDataIdAndExternalReferenceFromBody() {
        when(gateway.validarAssinatura(eq("999"), any(), any())).thenReturn(true);
        when(gateway.consultarPagamento("999"))
            .thenReturn(new com.oficina.billing.service.application.payment.PaymentResult(
                "999", "approved", true, null, "ok"));
        when(billingService.processarPagamento(eq(7L), any())).thenReturn(orcamento());

        String body = "{\"data\":{\"id\":\"999\"},\"external_reference\":7}";
        ResponseEntity<String> response = controller.receber("assinatura", "req-1",
            null, null, body);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(billingService).processarPagamento(eq(7L), any());
    }

    @Test
    @DisplayName("Deve usar externalReference do provedor quando a query nao informar")
    void shouldUseExternalReferenceFromGateway() {
        when(gateway.validarAssinatura(eq("555"), any(), any())).thenReturn(true);
        when(gateway.consultarPagamento("555"))
            .thenReturn(new com.oficina.billing.service.application.payment.PaymentResult(
                "555", "approved", true, "3", "ok"));
        when(billingService.processarPagamento(eq(3L), any())).thenReturn(orcamento());

        controller.receber(null, null, "555", null, null);

        verify(billingService).processarPagamento(eq(3L), any());
    }

    @Test
    @DisplayName("Deve tolerar externalReference nao numerica")
    void shouldTolerateInvalidExternalReference() {
        when(gateway.validarAssinatura(eq("777"), any(), any())).thenReturn(true);
        when(gateway.consultarPagamento("777"))
            .thenReturn(new com.oficina.billing.service.application.payment.PaymentResult(
                "777", "approved", true, "abc", "ok"));

        controller.receber(null, null, "777", null,
            "{\"data\":{\"id\":\"777\"}}");

        verify(billingService).processarPagamento(isNull(), any());
    }

    @Test
    @DisplayName("Deve processar corpo invalido como ignorado")
    void shouldIgnoreMalformedBody() {
        ResponseEntity<String> response = controller.receber(null, null, null, null,
            "{nao-e-json");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("ignorado", response.getBody());
    }

    @Test
    @DisplayName("Deve funcionar com o gateway mock completo")
    void shouldWorkWithMockGateway() {
        MercadoPagoWebhookController mockController = new MercadoPagoWebhookController(
            new MockPaymentGateway(), billingService, new ObjectMapper());
        when(billingService.processarPagamento(eq(2L), any())).thenReturn(orcamento());

        ResponseEntity<String> response = mockController.receber(null, null, "2", 2L, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(billingService).processarPagamento(eq(2L), any());
    }

    @Test
    @DisplayName("Deve ignorar status intermediario sem compensar a OS")
    void shouldIgnorePendingPayment() {
        when(gateway.validarAssinatura(eq("123"), any(), any())).thenReturn(true);
        when(gateway.consultarPagamento("123"))
            .thenReturn(new com.oficina.billing.service.application.payment.PaymentResult(
                "123", "pending", false, "1", "pending"));

        ResponseEntity<String> response = controller.receber("assinatura", "req-1",
            "123", 1L, "{}");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("pendente", response.getBody());
        verify(billingService, never()).processarPagamento(any(), any());
    }

    @Test
    @DisplayName("Deve ignorar webhook de tipo diferente de payment")
    void shouldIgnoreNonPaymentType() {
        ResponseEntity<String> response = controller.receber(null, null, null, null,
            "{\"type\":\"merchant_order\",\"data\":{\"id\":\"999\"}}");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("ignorado", response.getBody());
        verifyNoInteractions(billingService);
        verifyNoInteractions(gateway);
    }

    private Orcamento orcamento() {
        return new Orcamento(1L, "ORC-1", "12345678909", 250.0, StatusOrcamento.PAGO);
    }
}
