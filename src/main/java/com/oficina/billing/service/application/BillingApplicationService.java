package com.oficina.billing.service.application;

import com.oficina.billing.service.application.event.SagaEvent;
import com.oficina.billing.service.application.payment.CheckoutResult;
import com.oficina.billing.service.application.payment.PaymentResult;
import com.oficina.billing.service.domain.Orcamento;
import com.oficina.billing.service.domain.StatusOrcamento;

import java.util.List;

public interface BillingApplicationService {
    Orcamento gerarOrcamento(String clienteCpf, double valor);
    Orcamento gerarOrcamentoParaOs(SagaEvent event);
    Orcamento atualizarStatus(Long id, StatusOrcamento novoStatus);
    Orcamento cancelarPorSaga(SagaEvent event);
    CheckoutResult criarCheckout(Long id);
    Orcamento processarPagamento(Long orcamentoId, PaymentResult resultado);
    Orcamento consultarPorId(Long id);
    List<Orcamento> listarPorCliente(String clienteCpf);
}
