package com.oficina.billing.service.infrastructure.repository;

import com.oficina.billing.service.domain.Orcamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrcamentoRepository extends JpaRepository<Orcamento, Long> {
    Orcamento findByCodigoOrcamento(String codigoOrcamento);
    java.util.List<Orcamento> findByClienteCpf(String clienteCpf);
}
