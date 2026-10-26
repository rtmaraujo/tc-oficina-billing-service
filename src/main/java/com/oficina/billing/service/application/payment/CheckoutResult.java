package com.oficina.billing.service.application.payment;

/**
 * Resultado da criacao de um checkout no provedor de pagamento.
 */
public record CheckoutResult(String preferenceId,
                             String initPoint,
                             String sandboxInitPoint,
                             boolean mock) {
}
