package com.oficina.billing.service.application.impl;

import com.oficina.billing.service.domain.Orcamento;
import com.oficina.billing.service.domain.StatusOrcamento;
import com.oficina.billing.service.application.BillingApplicationService;
import com.oficina.billing.service.infrastructure.persistence.OrcamentoEntity;
import com.oficina.billing.service.infrastructure.repository.OrcamentoRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class BillingApplicationServiceImpl implements BillingApplicationService {

    private final OrcamentoRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public BillingApplicationServiceImpl(OrcamentoRepository repository, KafkaTemplate<String, String> kafkaTemplate) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public Orcamento gerarOrcamento(String clienteCpf, double valor) {
        Orcamento orcamento = new Orcamento(null, "ORC-" + System.currentTimeMillis(), clienteCpf, valor, StatusOrcamento.PENDENTE);
        Orcamento saved = repository.save(new OrcamentoEntity(orcamento)).toDomain();

        // Publicar evento Kafka: orçamento gerado
        kafkaTemplate.send("budget-generated", saved.getId().toString(),
            "Orcamento{" +
            "id=" + saved.getId() +
            ", codigo='" + saved.getCodigoOrcamento() + '\'' +
            ", clienteCpf='" + saved.getClienteCpf() + '\'' +
            ", valor=" + saved.getValor() +
            ", status=" + saved.getStatus() +
            '}');

        return saved;
    }

    @Override
    public Orcamento atualizarStatus(Long id, StatusOrcamento novoStatus) {
        Optional<OrcamentoEntity> optional = repository.findById(id);
        if (optional.isPresent()) {
            OrcamentoEntity entity = optional.get();
            entity.setStatus(novoStatus);
            entity.setDataAtualizacao(LocalDateTime.now());
            if (novoStatus == StatusOrcamento.PAGO) {
                entity.setPagamentoId("PAG-" + System.currentTimeMillis());
            }
            Orcamento updated = repository.save(entity).toDomain();

            // Publicar evento conforme status
            switch (novoStatus) {
                case APROVADO:
                    kafkaTemplate.send("budget-approved", updated.getId().toString(),
                        "Budget approved: " + updated.getCodigoOrcamento());
                    break;
                case PAGO:
                    kafkaTemplate.send("payment-completed", updated.getId().toString(),
                        "Payment completed for: " + updated.getCodigoOrcamento());
                    break;
                case REJEITADO:
                    kafkaTemplate.send("budget-rejected", updated.getId().toString(),
                        "Budget rejected: " + updated.getCodigoOrcamento());
                    break;
                default:
                    break;
            }

            return updated;
        }
        return null;
    }

    @Override
    public Orcamento consultarPorId(Long id) {
        return repository.findById(id).map(OrcamentoEntity::toDomain).orElse(null);
    }

    @Override
    public List<Orcamento> listarPorCliente(String clienteCpf) {
        return repository.findByClienteCpf(clienteCpf).stream()
            .map(OrcamentoEntity::toDomain)
            .toList();
    }
}
