package com.oficina.billing.service.infrastructure.repository;

import com.oficina.billing.service.infrastructure.persistence.OrcamentoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrcamentoRepository extends JpaRepository<OrcamentoEntity, Long> {
    OrcamentoEntity findByCodigoOrcamento(String codigoOrcamento);
    List<OrcamentoEntity> findByClienteCpf(String clienteCpf);
}
