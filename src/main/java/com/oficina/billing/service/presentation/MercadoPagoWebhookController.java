package com.oficina.billing.service.presentation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oficina.billing.service.application.BillingApplicationService;
import com.oficina.billing.service.application.payment.PaymentGateway;
import com.oficina.billing.service.application.payment.PaymentResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Webhook do provedor de pagamento.
 *
 * <p>Rota publica (Traefik encaminha /webhook sem strip). O provider valida a
 * assinatura e consulta o pagamento; o resultado e traduzido para os eventos
 * payment-completed/payment-failed consumidos pela Saga.</p>
 */
@RestController
@RequestMapping("/webhook")
public class MercadoPagoWebhookController {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoWebhookController.class);

    private final PaymentGateway paymentGateway;
    private final BillingApplicationService billingService;
    private final ObjectMapper objectMapper;

    public MercadoPagoWebhookController(PaymentGateway paymentGateway,
                                        BillingApplicationService billingService,
                                        ObjectMapper objectMapper) {
        this.paymentGateway = paymentGateway;
        this.billingService = billingService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/mercadopago")
    public ResponseEntity<String> receber(
            @RequestHeader(name = "x-signature", required = false) String signature,
            @RequestHeader(name = "x-request-id", required = false) String requestId,
            @RequestParam(name = "data.id", required = false) String dataIdParam,
            @RequestParam(name = "orcamentoId", required = false) Long orcamentoIdParam,
            @RequestBody(required = false) String body) {

        JsonNode json = ler(body);
        String dataId = dataIdParam != null ? dataIdParam
            : json != null && json.has("data") ? json.get("data").path("id").asText(null) : null;

        if (dataId == null) {
            log.warn("Webhook recebido sem data.id (corpo com {} caracteres)",
                body == null ? 0 : body.length());
            return ResponseEntity.ok("ignorado");
        }

        if (!paymentGateway.validarAssinatura(dataId, signature, requestId)) {
            log.warn("Assinatura invalida no webhook do pagamento {}", dataId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("assinatura invalida");
        }

        PaymentResult resultado = paymentGateway.consultarPagamento(dataId);
        Long orcamentoId = orcamentoIdParam;
        if (orcamentoId == null && resultado.externalReference() != null) {
            try {
                orcamentoId = Long.valueOf(resultado.externalReference());
            } catch (NumberFormatException e) {
                log.warn("external_reference invalido: {}", resultado.externalReference());
            }
        }
        if (orcamentoId == null && json != null && json.hasNonNull("external_reference")) {
            orcamentoId = json.get("external_reference").asLong();
        }

        billingService.processarPagamento(orcamentoId, resultado);
        return ResponseEntity.ok("ok");
    }

    private JsonNode ler(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            log.warn("Webhook com corpo invalido ({} caracteres)",
                body.length());
            return null;
        }
    }
}
