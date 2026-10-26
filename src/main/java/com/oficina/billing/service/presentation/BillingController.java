package com.oficina.billing.service.presentation;

import com.oficina.billing.service.application.BillingApplicationService;
import com.oficina.billing.service.application.payment.CheckoutResult;
import com.oficina.billing.service.application.payment.PaymentGateway;
import com.oficina.billing.service.application.payment.PaymentResult;
import com.oficina.billing.service.domain.Orcamento;
import com.oficina.billing.service.domain.StatusOrcamento;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/billing")
public class BillingController {

    private final BillingApplicationService billingService;
    private final PaymentGateway paymentGateway;

    public BillingController(BillingApplicationService billingService, PaymentGateway paymentGateway) {
        this.billingService = billingService;
        this.paymentGateway = paymentGateway;
    }

    @PostMapping("/orcamento")
    public ResponseEntity<Orcamento> gerarOrcamento(@RequestParam String clienteCpf, @RequestParam double valor) {
        Orcamento orcamento = billingService.gerarOrcamento(clienteCpf, valor);
        return ResponseEntity.ok(orcamento);
    }

    @GetMapping("/orcamento/{id}")
    public ResponseEntity<Orcamento> consultar(@PathVariable Long id) {
        Orcamento orcamento = billingService.consultarPorId(id);
        if (orcamento == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(orcamento);
    }

    @GetMapping("/cliente/{clienteCpf}")
    public ResponseEntity<List<Orcamento>> listarPorCliente(@PathVariable String clienteCpf) {
        List<Orcamento> orcamentoList = billingService.listarPorCliente(clienteCpf);
        return ResponseEntity.ok(orcamentoList);
    }

    @PutMapping("/orcamento/{id}/status")
    public ResponseEntity<Orcamento> atualizarStatus(@PathVariable Long id, @RequestParam StatusOrcamento novoStatus) {
        Orcamento orcamento = billingService.atualizarStatus(id, novoStatus);
        if (orcamento == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(orcamento);
    }

    @PostMapping("/orcamento/{id}/checkout")
    public ResponseEntity<CheckoutResult> checkout(@PathVariable Long id) {
        if (billingService.consultarPorId(id) == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(billingService.criarCheckout(id));
    }

    /**
     * Atalho de demonstracao usado apenas quando o provedor mock esta ativo.
     * Com o Mercado Pago real, o pagamento deve vir pelo webhook.
     */
    @PostMapping("/orcamento/{id}/simular-pagamento")
    public ResponseEntity<Object> simularPagamento(@PathVariable Long id,
                                                   @RequestParam(defaultValue = "true") boolean aprovado) {
        if (!paymentGateway.isMock()) {
            return ResponseEntity.badRequest().body(
                "Simulacao disponivel apenas com pagamento.provider=mock");
        }
        if (billingService.consultarPorId(id) == null) return ResponseEntity.notFound().build();
        PaymentResult resultado = new PaymentResult(
            "MOCK-PAY-" + id,
            aprovado ? "approved" : "rejected",
            aprovado,
            String.valueOf(id),
            aprovado ? "Pagamento simulado aprovado" : "Pagamento simulado recusado");
        return ResponseEntity.ok(billingService.processarPagamento(id, resultado));
    }
}
