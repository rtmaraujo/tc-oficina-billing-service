package com.oficina.billing.service.application.payment;

/**
 * Resultado da consulta de um pagamento no provedor.
 */
public record PaymentResult(String paymentId,
                            String status,
                            boolean approved,
                            String externalReference,
                            String message) {
}
