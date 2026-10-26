package com.oficina.billing.service.application.payment;

import com.oficina.billing.service.domain.Orcamento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Gateway de pagamento simulado.
 *
 * <p>Usado enquanto as credenciais do Mercado Pago nao estao disponiveis ou
 * quando {@code pagamento.provider=mock}. Gera um checkout ficticio e permite
 * simular aprovacao/recusa sem chamadas externas, mantendo o mesmo contrato
 * de eventos consumido pela Saga.</p>
 */
@Component
@ConditionalOnProperty(name = "pagamento.provider", havingValue = "mock", matchIfMissing = true)
public class MockPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(MockPaymentGateway.class);

    @Override
    public CheckoutResult criarCheckout(Orcamento orcamento) {
        String preferenceId = "MOCK-PREF-" + orcamento.getId();
        String initPoint = "https://sandbox.mercadopago.com.br/checkout/v1/redirect?pref_id=" + preferenceId;
        log.info("Checkout mock criado para o orcamento {}: {}", orcamento.getCodigoOrcamento(), preferenceId);
        return new CheckoutResult(preferenceId, initPoint, initPoint, true);
    }

    @Override
    public PaymentResult consultarPagamento(String paymentId) {
        return new PaymentResult(paymentId, "approved", true, null,
            "Pagamento simulado aprovado");
    }

    @Override
    public boolean validarAssinatura(String dataId, String signature, String requestId) {
        return true;
    }

    @Override
    public boolean isMock() {
        return true;
    }
}
