package com.oficina.billing.service.presentation;

import com.oficina.billing.service.domain.Orcamento;
import com.oficina.billing.service.domain.StatusOrcamento;
import com.oficina.billing.service.application.BillingApplicationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/billing")
public class BillingController {

    @Autowired
    private BillingApplicationService billingService;

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
}
