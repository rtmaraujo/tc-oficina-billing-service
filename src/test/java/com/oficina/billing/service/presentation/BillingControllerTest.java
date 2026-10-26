package com.oficina.billing.service.presentation;

import com.oficina.billing.service.application.BillingApplicationService;
import com.oficina.billing.service.application.payment.CheckoutResult;
import com.oficina.billing.service.application.payment.MockPaymentGateway;
import com.oficina.billing.service.domain.Orcamento;
import com.oficina.billing.service.domain.StatusOrcamento;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BillingController - Endpoints REST")
class BillingControllerTest {

    @Mock
    private BillingApplicationService billingService;

    private BillingController controller;

    @BeforeEach
    void setUp() {
        controller = new BillingController(billingService, new MockPaymentGateway());
    }

    private Orcamento orcamento() {
        return new Orcamento(1L, "ORC-1", "12345678909", 250.0, StatusOrcamento.PENDENTE);
    }

    @Test
    @DisplayName("Deve gerar orcamento")
    void shouldGenerateBudget() {
        when(billingService.gerarOrcamento("12345678909", 250.0)).thenReturn(orcamento());

        ResponseEntity<Orcamento> response = controller.gerarOrcamento("12345678909", 250.0);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("ORC-1", response.getBody().getCodigoOrcamento());
    }

    @Test
    @DisplayName("Deve consultar orcamento existente")
    void shouldFindBudget() {
        when(billingService.consultarPorId(1L)).thenReturn(orcamento());

        assertEquals(HttpStatus.OK, controller.consultar(1L).getStatusCode());
    }

    @Test
    @DisplayName("Deve retornar 404 ao consultar orcamento inexistente")
    void shouldReturnNotFoundWhenBudgetDoesNotExist() {
        when(billingService.consultarPorId(99L)).thenReturn(null);

        assertEquals(HttpStatus.NOT_FOUND, controller.consultar(99L).getStatusCode());
    }

    @Test
    @DisplayName("Deve listar orcamentos por cliente")
    void shouldListByClient() {
        when(billingService.listarPorCliente("12345678909")).thenReturn(List.of(orcamento()));

        ResponseEntity<List<Orcamento>> response = controller.listarPorCliente("12345678909");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
    }

    @Test
    @DisplayName("Deve atualizar status do orcamento")
    void shouldUpdateStatus() {
        Orcamento aprovado = orcamento();
        aprovado.setStatus(StatusOrcamento.APROVADO);
        when(billingService.atualizarStatus(1L, StatusOrcamento.APROVADO)).thenReturn(aprovado);

        ResponseEntity<Orcamento> response = controller.atualizarStatus(1L, StatusOrcamento.APROVADO);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(StatusOrcamento.APROVADO, response.getBody().getStatus());
    }

    @Test
    @DisplayName("Deve retornar 404 ao atualizar status de orcamento inexistente")
    void shouldReturnNotFoundOnStatusUpdate() {
        when(billingService.atualizarStatus(99L, StatusOrcamento.APROVADO)).thenReturn(null);

        assertEquals(HttpStatus.NOT_FOUND,
            controller.atualizarStatus(99L, StatusOrcamento.APROVADO).getStatusCode());
    }

    @Test
    @DisplayName("Deve criar checkout de orcamento existente")
    void shouldCreateCheckout() {
        when(billingService.consultarPorId(1L)).thenReturn(orcamento());
        when(billingService.criarCheckout(1L))
            .thenReturn(new CheckoutResult("PREF-1", "https://checkout", "https://sandbox", true));

        ResponseEntity<CheckoutResult> response = controller.checkout(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("PREF-1", response.getBody().preferenceId());
    }

    @Test
    @DisplayName("Deve retornar 404 no checkout de orcamento inexistente")
    void shouldReturnNotFoundOnCheckout() {
        when(billingService.consultarPorId(99L)).thenReturn(null);

        assertEquals(HttpStatus.NOT_FOUND, controller.checkout(99L).getStatusCode());
        verify(billingService, never()).criarCheckout(anyLong());
    }

    @Test
    @DisplayName("Deve simular pagamento aprovado com provider mock")
    void shouldSimulateApprovedPayment() {
        Orcamento pago = orcamento();
        pago.setStatus(StatusOrcamento.PAGO);
        when(billingService.consultarPorId(1L)).thenReturn(orcamento());
        when(billingService.processarPagamento(eq(1L), any())).thenReturn(pago);

        ResponseEntity<Object> response = controller.simularPagamento(1L, true);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(pago, response.getBody());
    }

    @Test
    @DisplayName("Deve simular pagamento recusado com provider mock")
    void shouldSimulateRejectedPayment() {
        Orcamento rejeitado = orcamento();
        rejeitado.setStatus(StatusOrcamento.REJEITADO);
        when(billingService.consultarPorId(1L)).thenReturn(orcamento());
        when(billingService.processarPagamento(eq(1L), any())).thenReturn(rejeitado);

        assertEquals(HttpStatus.OK, controller.simularPagamento(1L, false).getStatusCode());
    }

    @Test
    @DisplayName("Deve retornar 404 ao simular pagamento de orcamento inexistente")
    void shouldReturnNotFoundOnSimulation() {
        when(billingService.consultarPorId(99L)).thenReturn(null);

        assertEquals(HttpStatus.NOT_FOUND, controller.simularPagamento(99L, true).getStatusCode());
        verify(billingService, never()).processarPagamento(any(), any());
    }

    @Test
    @DisplayName("Deve recusar simulacao quando o provider nao for mock")
    void shouldRejectSimulationWhenProviderIsReal() {
        com.oficina.billing.service.application.payment.PaymentGateway real =
            mock(com.oficina.billing.service.application.payment.PaymentGateway.class);
        when(real.isMock()).thenReturn(false);
        BillingController comProviderReal = new BillingController(billingService, real);

        ResponseEntity<Object> response = comProviderReal.simularPagamento(1L, true);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verifyNoInteractions(billingService);
    }

    @Test
    @DisplayName("Deve repassar o resultado do pagamento simulado para o servico")
    void shouldPassPaymentResultToService() {
        when(billingService.consultarPorId(1L)).thenReturn(orcamento());
        when(billingService.processarPagamento(eq(1L), any())).thenReturn(orcamento());

        controller.simularPagamento(1L, true);

        ArgumentCaptor<com.oficina.billing.service.application.payment.PaymentResult> captor =
            ArgumentCaptor.forClass(com.oficina.billing.service.application.payment.PaymentResult.class);
        verify(billingService).processarPagamento(eq(1L), captor.capture());
        assertEquals("MOCK-PAY-1", captor.getValue().paymentId());
        assertTrue(captor.getValue().approved());
    }
}
