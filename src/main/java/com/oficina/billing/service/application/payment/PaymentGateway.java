package com.oficina.billing.service.application.payment;

import com.oficina.billing.service.domain.Orcamento;

/**
 * Porta (anti-corruption layer) para o provedor de pagamentos.
 *
 * <p>O restante do Billing e da Saga depende apenas desta interface. A
 * implementacao real do Mercado Pago e a implementacao mock sao intercambiaveis
 * por configuracao, sem alterar o fluxo transacional.</p>
 */
public interface PaymentGateway {

    CheckoutResult criarCheckout(Orcamento orcamento);

    PaymentResult consultarPagamento(String paymentId);

    boolean validarAssinatura(String dataId, String signature, String requestId);

    boolean isMock();
}
