package com.oficina.billing.service.application.payment;

import com.oficina.billing.service.domain.Orcamento;
import com.oficina.billing.service.domain.StatusOrcamento;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@DisplayName("MercadoPagoPaymentGateway - Integracao com o provedor real")
class MercadoPagoPaymentGatewayTest {

    private static final String TOKEN = "TEST-token";
    private static final String SECRET = "webhook-secret";
    private static final String BASE_URL = "https://api.mercadopago.com";

    private MockRestServiceServer server;
    private MercadoPagoPaymentGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = criarGateway(BASE_URL, SECRET, "http://localhost/webhook/mercadopago",
            "http://localhost:8082/", TOKEN);
    }

    private MercadoPagoPaymentGateway criarGateway(String baseUrl, String secret, String notificationUrl,
                                                   String backUrl, String token) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        return new MercadoPagoPaymentGateway(restClient, token, secret, notificationUrl, backUrl, baseUrl);
    }

    @Test
    @DisplayName("Deve criar checkout com external_reference e mapear os pontos de acesso")
    void shouldCreateCheckout() {
        server.expect(requestTo(BASE_URL + "/checkout/preferences"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer " + TOKEN))
            .andExpect(jsonPath("$.external_reference").value("1"))
            .andExpect(jsonPath("$.items[0].unit_price").value(500.0))
            .andExpect(jsonPath("$.notification_url").value("http://localhost/webhook/mercadopago"))
            .andExpect(jsonPath("$.back_urls.success").value("http://localhost:8082/"))
            .andExpect(jsonPath("$.auto_return").doesNotExist())
            .andRespond(withSuccess("""
                {"id":"123456","init_point":"https://mp.com/init","sandbox_init_point":"https://mp.com/sandbox"}
                """, MediaType.APPLICATION_JSON));

        CheckoutResult result = gateway.criarCheckout(orcamento(1L));

        assertEquals("123456", result.preferenceId());
        assertEquals("https://mp.com/init", result.initPoint());
        assertEquals("https://mp.com/sandbox", result.sandboxInitPoint());
        assertFalse(result.mock());
        server.verify();
    }

    @Test
    @DisplayName("Deve usar init_point quando o sandbox nao vier na resposta")
    void shouldFallbackToInitPoint() {
        server.expect(requestTo(BASE_URL + "/checkout/preferences"))
            .andRespond(withSuccess("""
                {"id":"1","init_point":"https://mp.com/init"}
                """, MediaType.APPLICATION_JSON));

        CheckoutResult result = gateway.criarCheckout(orcamento(1L));

        assertEquals("https://mp.com/init", result.sandboxInitPoint());
    }

    @Test
    @DisplayName("Deve enviar auto_return quando a back_url for https")
    void shouldSendAutoReturnForHttps() {
        gateway = criarGateway(BASE_URL, SECRET, "https://example.com/webhook",
            "https://example.com/billing/", TOKEN);
        server.expect(requestTo(BASE_URL + "/checkout/preferences"))
            .andExpect(jsonPath("$.back_urls.success").value("https://example.com/billing/"))
            .andExpect(jsonPath("$.auto_return").value("approved"))
            .andRespond(withSuccess("""
                {"id":"4","init_point":"https://mp.com/init","sandbox_init_point":"https://mp.com/sb"}
                """, MediaType.APPLICATION_JSON));

        CheckoutResult result = gateway.criarCheckout(orcamento(4L));

        assertEquals("4", result.preferenceId());
        server.verify();
    }

    @Test
    @DisplayName("Deve omitir notification e back_urls quando nao configurados e remover barra final da base")
    void shouldOmitOptionalUrls() {
        gateway = criarGateway(BASE_URL + "/", SECRET, "", "", TOKEN);
        server.expect(requestTo(BASE_URL + "/checkout/preferences"))
            .andExpect(jsonPath("$.notification_url").doesNotExist())
            .andExpect(jsonPath("$.back_urls").doesNotExist())
            .andRespond(withSuccess("""
                {"id":"2","init_point":"https://mp.com/init","sandbox_init_point":"https://mp.com/sb"}
                """, MediaType.APPLICATION_JSON));

        CheckoutResult result = gateway.criarCheckout(orcamento(9L));

        assertEquals("2", result.preferenceId());
        server.verify();
    }

    @Test
    @DisplayName("Deve usar o codigo do orcamento quando o id ainda nao existe")
    void shouldUseCodigoWhenIdIsNull() {
        server.expect(requestTo(BASE_URL + "/checkout/preferences"))
            .andExpect(jsonPath("$.external_reference").value("ORC-1"))
            .andRespond(withSuccess("""
                {"id":"3","init_point":"https://mp.com/init"}
                """, MediaType.APPLICATION_JSON));

        CheckoutResult result = gateway.criarCheckout(orcamento(null));

        assertEquals("3", result.preferenceId());
    }

    @Test
    @DisplayName("Deve falhar quando o Mercado Pago nao devolve o id da preferencia")
    void shouldFailWithoutPreferenceId() {
        server.expect(requestTo(BASE_URL + "/checkout/preferences"))
            .andRespond(withSuccess("{\"init_point\":\"x\"}", MediaType.APPLICATION_JSON));

        IllegalStateException erro = assertThrows(IllegalStateException.class,
            () -> gateway.criarCheckout(orcamento(1L)));

        assertTrue(erro.getMessage().contains("preferencia"));
    }

    @Test
    @DisplayName("Deve encapsular erro HTTP ao criar checkout")
    void shouldWrapCheckoutHttpError() {
        server.expect(requestTo(BASE_URL + "/checkout/preferences"))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThrows(IllegalStateException.class, () -> gateway.criarCheckout(orcamento(1L)));
    }

    @Test
    @DisplayName("Deve falhar quando a resposta vier sem corpo")
    void shouldFailOnEmptyResponse() {
        server.expect(requestTo(BASE_URL + "/checkout/preferences"))
            .andRespond(withSuccess());

        assertThrows(IllegalStateException.class, () -> gateway.criarCheckout(orcamento(1L)));
    }

    @Test
    @DisplayName("Deve traduzir pagamento aprovado")
    void shouldTranslateApprovedPayment() {
        server.expect(requestTo(BASE_URL + "/v1/payments/777"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "Bearer " + TOKEN))
            .andRespond(withSuccess("""
                {"id":777,"status":"approved","status_detail":"accredited","external_reference":"1"}
                """, MediaType.APPLICATION_JSON));

        PaymentResult result = gateway.consultarPagamento("777");

        assertEquals("777", result.paymentId());
        assertEquals("approved", result.status());
        assertTrue(result.approved());
        assertEquals("1", result.externalReference());
        assertEquals("accredited", result.message());
    }

    @Test
    @DisplayName("Deve traduzir pagamento recusado")
    void shouldTranslateRejectedPayment() {
        server.expect(requestTo(BASE_URL + "/v1/payments/888"))
            .andRespond(withSuccess("""
                {"status":"rejected","status_detail":"cc_rejected_other_reason"}
                """, MediaType.APPLICATION_JSON));

        PaymentResult result = gateway.consultarPagamento("888");

        assertEquals("888", result.paymentId());
        assertFalse(result.approved());
        assertNull(result.externalReference());
    }

    @Test
    @DisplayName("Deve encapsular erro ao consultar pagamento")
    void shouldWrapPaymentQueryError() {
        server.expect(requestTo(BASE_URL + "/v1/payments/999"))
            .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThrows(IllegalStateException.class, () -> gateway.consultarPagamento("999"));
    }

    @Test
    @DisplayName("Deve validar assinatura HMAC correta")
    void shouldValidateSignature() {
        String signature = assinatura("42", "req-1", "1704908010", SECRET);

        assertTrue(gateway.validarAssinatura("42", signature, "req-1"));
    }

    @Test
    @DisplayName("Deve normalizar data.id em maiusculas antes de assinar")
    void shouldNormalizeDataId() {
        String signature = assinatura("abc123", "req-1", "1", SECRET);

        assertTrue(gateway.validarAssinatura("ABC123", signature, "req-1"));
    }

    @Test
    @DisplayName("Deve validar assinatura quando nao ha request-id")
    void shouldValidateWithoutRequestId() {
        String signature = assinatura("42", "", "1", SECRET);

        assertTrue(gateway.validarAssinatura("42", signature, null));
    }

    @Test
    @DisplayName("Deve rejeitar assinatura divergente")
    void shouldRejectWrongSignature() {
        String signature = assinatura("42", "req-1", "1", "outro-secret");

        assertFalse(gateway.validarAssinatura("42", signature, "req-1"));
    }

    @Test
    @DisplayName("Deve rejeitar assinatura nula, vazia ou sem ts/v1")
    void shouldRejectMalformedSignature() {
        assertFalse(gateway.validarAssinatura(null, "ts=1,v1=abc", "req"));
        assertFalse(gateway.validarAssinatura("42", null, "req"));
        assertFalse(gateway.validarAssinatura("42", "  ", "req"));
        assertFalse(gateway.validarAssinatura("42", "lixo,ts=1", "req"));
        assertFalse(gateway.validarAssinatura("42", "=,v1=abc", "req"));
        assertFalse(gateway.validarAssinatura("42", "ts=1,v1=", "req"));
    }

    @Test
    @DisplayName("Deve rejeitar webhook quando o secret nao esta configurado")
    void shouldRejectWhenSecretMissing() {
        MercadoPagoPaymentGateway semSecret = criarGateway(BASE_URL, "", "", "", TOKEN);

        assertFalse(semSecret.validarAssinatura("42", "ts=1,v1=abc", "req"));
    }

    @Test
    @DisplayName("Nao deve ser identificado como mock")
    void shouldNotBeMock() {
        assertFalse(gateway.isMock());
    }

    @Test
    @DisplayName("Deve ser construivel com o builder do Spring e normalizar a base URL")
    void shouldBuildWithSpringBuilder() {
        MercadoPagoPaymentGateway doBuilder = new MercadoPagoPaymentGateway(
            RestClient.builder(), TOKEN, SECRET, "http://localhost/webhook", "http://localhost/",
            BASE_URL + "/");

        assertFalse(doBuilder.isMock());
    }

    private String assinatura(String dataId, String requestId, String ts, String secret) {
        String manifest = "id:" + dataId + ";request-id:" + requestId + ";ts:" + ts + ";";
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "ts=" + ts + ",v1="
                + HexFormat.of().formatHex(mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Orcamento orcamento(Long id) {
        return new Orcamento(id, "ORC-1", "12345678909", 500.0, StatusOrcamento.PENDENTE);
    }
}
