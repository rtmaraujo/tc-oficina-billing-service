package com.oficina.billing.service.application.payment;

import java.util.Locale;
import java.util.Set;

/**
 * Resultado da consulta de um pagamento no provedor.
 */
public record PaymentResult(String paymentId,
                            String status,
                            boolean approved,
                            String externalReference,
                            String message) {

    /** Status que encerram o pagamento; apenas esses podem mudar o orcamento. */
    private static final Set<String> STATUS_FINAIS = Set.of(
        "approved", "rejected", "cancelled", "refunded", "charged_back");

    /**
     * Indica se o pagamento ja esta em estado definitivo.
     *
     * <p>Estados intermediarios (pending, in_process, authorized) nao devem
     * disparar payment-failed, sob pena de a Saga compensar uma OS que ainda
     * pode ser paga.</p>
     */
    public boolean finalizado() {
        if (approved) {
            return true;
        }
        return status != null && STATUS_FINAIS.contains(status.toLowerCase(Locale.ROOT));
    }
}
