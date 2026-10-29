package com.oficina.billing.service.application.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.oficina.billing.service.domain.Orcamento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Provedor real do Mercado Pago (Checkout Pro).
 *
 * <p>Ativo quando {@code pagamento.provider=mercadopago}. Cria a preferencia de
 * checkout, consulta o pagamento pelo id e valida a assinatura HMAC-SHA256 dos
 * webhooks. Nenhuma credencial e registrada em log.</p>
 */
@Component
@ConditionalOnProperty(name = "pagamento.provider", havingValue = "mercadopago")
public class MercadoPagoPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoPaymentGateway.class);

    private static final String STATUS_APPROVED = "approved";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 10000;

    private final RestClient restClient;
    private final String accessToken;
    private final String webhookSecret;
    private final String notificationUrl;
    private final String backUrl;
    private final String baseUrl;

    @Autowired
    public MercadoPagoPaymentGateway(RestClient.Builder builder,
                                     @Value("${mercadopago.access-token:}") String accessToken,
                                     @Value("${mercadopago.webhook-secret:}") String webhookSecret,
                                     @Value("${mercadopago.notification-url:}") String notificationUrl,
                                     @Value("${mercadopago.back-url:http://localhost:8082/}") String backUrl,
                                     @Value("${mercadopago.base-url:https://api.mercadopago.com}") String baseUrl) {
        this(construirRestClient(builder), accessToken, webhookSecret, notificationUrl, backUrl, baseUrl);
    }

    MercadoPagoPaymentGateway(RestClient restClient,
                              String accessToken,
                              String webhookSecret,
                              String notificationUrl,
                              String backUrl,
                              String baseUrl) {
        this.restClient = restClient;
        this.accessToken = accessToken;
        this.webhookSecret = webhookSecret;
        this.notificationUrl = notificationUrl;
        this.backUrl = backUrl;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private static RestClient construirRestClient(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        requestFactory.setReadTimeout(READ_TIMEOUT_MS);
        return builder.requestFactory(requestFactory).build();
    }

    @Override
    public CheckoutResult criarCheckout(Orcamento orcamento) {
        String externalReference = orcamento.getId() != null
            ? String.valueOf(orcamento.getId())
            : orcamento.getCodigoOrcamento();

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("title", "Orcamento " + orcamento.getCodigoOrcamento());
        item.put("description", "Servicos da oficina mecanica");
        item.put("quantity", 1);
        item.put("currency_id", "BRL");
        item.put("unit_price", orcamento.getValor());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", List.of(item));
        body.put("external_reference", externalReference);
        if (notificationUrl != null && !notificationUrl.isBlank()) {
            body.put("notification_url", notificationUrl);
        }
        if (backUrl != null && !backUrl.isBlank()) {
            body.put("back_urls", Map.of(
                "success", backUrl,
                "failure", backUrl,
                "pending", backUrl));
            // O Mercado Pago so aceita auto_return quando a back_url de sucesso e https
            if (backUrl.startsWith("https://")) {
                body.put("auto_return", STATUS_APPROVED);
            }
        }

        JsonNode response = exchange(HttpMethod.POST, "/checkout/preferences", body);
        String preferenceId = response.path("id").asText(null);
        if (preferenceId == null || preferenceId.isBlank()) {
            throw new IllegalStateException("Mercado Pago nao retornou o id da preferencia");
        }
        String initPoint = response.path("init_point").asText(null);
        String sandboxInitPoint = response.path("sandbox_init_point").asText(null);
        String checkoutUrl = sandboxInitPoint != null && !sandboxInitPoint.isBlank()
            ? sandboxInitPoint
            : initPoint;

        log.info("Preferencia {} criada no Mercado Pago para o orcamento {}",
            preferenceId, orcamento.getCodigoOrcamento());
        return new CheckoutResult(preferenceId, initPoint, checkoutUrl, false);
    }

    @Override
    public PaymentResult consultarPagamento(String paymentId) {
        JsonNode response = exchange(HttpMethod.GET, "/v1/payments/" + paymentId, null);
        String status = response.path("status").asText(null);
        String externalReference = response.path("external_reference").asText(null);
        String statusDetail = response.path("status_detail").asText(null);
        String id = response.path("id").asText(paymentId);

        log.info("Pagamento {} consultado no Mercado Pago: status {}", id, status);
        return new PaymentResult(id, status, STATUS_APPROVED.equals(status),
            externalReference, statusDetail);
    }

    @Override
    public boolean validarAssinatura(String dataId, String signature, String requestId) {
        if (dataId == null || signature == null || signature.isBlank()) {
            return false;
        }
        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.warn("Webhook recebido sem mercadopago.webhook-secret configurado");
            return false;
        }

        String ts = null;
        String v1 = null;
        for (String part : signature.split(",")) {
            int separator = part.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String key = part.substring(0, separator).trim();
            String value = part.substring(separator + 1).trim();
            if ("ts".equals(key)) {
                ts = value;
            } else if ("v1".equals(key)) {
                v1 = value;
            }
        }
        if (ts == null || v1 == null) {
            log.warn("Assinatura do webhook sem ts ou v1");
            return false;
        }

        String manifest = "id:" + dataId.toLowerCase(Locale.ROOT)
            + ";request-id:" + (requestId == null ? "" : requestId)
            + ";ts:" + ts + ";";
        String expected = hmacSha256(manifest);
        boolean valid = MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8),
            v1.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));

        if (!valid) {
            log.warn("Assinatura HMAC invalida para o pagamento {}", dataId);
        }
        return valid;
    }

    @Override
    public boolean isMock() {
        return false;
    }

    private JsonNode exchange(HttpMethod method, String path, Object body) {
        try {
            RestClient.RequestBodySpec request = restClient.method(method)
                .uri(baseUrl + path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .accept(MediaType.APPLICATION_JSON);
            if (body != null) {
                request.contentType(MediaType.APPLICATION_JSON);
                request.body(body);
            }
            JsonNode response = request.retrieve().body(JsonNode.class);
            if (response == null) {
                throw new IllegalStateException("Resposta vazia do Mercado Pago em " + path);
            }
            return response;
        } catch (RestClientException e) {
            log.warn("Falha na chamada ao Mercado Pago em {}", path);
            throw new IllegalStateException("Falha na chamada ao Mercado Pago em " + path, e);
        }
    }

    private String hmacSha256(String manifest) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao calcular a assinatura do webhook", e);
        }
    }
}
