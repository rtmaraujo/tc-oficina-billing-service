package com.oficina.billing.service.application;

import com.oficina.billing.service.domain.Orcamento;
import com.oficina.billing.service.domain.StatusOrcamento;
import java.util.List;

public interface BillingApplicationService {
    Orcamento gerarOrcamento(String clienteCpf, double valor);
    Orcamento atualizarStatus(Long id, StatusOrcamento novoStatus);
    Orcamento consultarPorId(Long id);
    List<Orcamento> listarPorCliente(String clienteCpf);
}
