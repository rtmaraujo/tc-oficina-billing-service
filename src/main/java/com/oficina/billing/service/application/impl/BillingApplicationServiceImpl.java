package com.oficina.billing.service.application.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oficina.billing.service.application.BillingApplicationService;
import com.oficina.billing.service.application.event.SagaEvent;
import com.oficina.billing.service.application.payment.CheckoutResult;
import com.oficina.billing.service.application.payment.PaymentGateway;
import com.oficina.billing.service.application.payment.PaymentResult;
import com.oficina.billing.service.domain.Orcamento;
import com.oficina.billing.service.domain.StatusOrcamento;
import com.oficina.billing.service.infrastructure.persistence.OrcamentoEntity;
import com.oficina.billing.service.infrastructure.repository.OrcamentoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class BillingApplicationServiceImpl implements BillingApplicationService {

    private static final Logger log = LoggerFactory.getLogger(BillingApplicationServiceImpl.class);

    private final OrcamentoRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final PaymentGateway paymentGateway;
    private final double valorPadrao;

    public BillingApplicationServiceImpl(OrcamentoRepository repository,
                                         KafkaTemplate<String, String> kafkaTemplate,
                                         ObjectMapper objectMapper,
                                         PaymentGateway paymentGateway,
                                         @Value("${billing.valor-padrao:500.0}") double valorPadrao) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.paymentGateway = paymentGateway;
        this.valorPadrao = valorPadrao;
    }

    @Override
    @Transactional
    public Orcamento gerarOrcamento(String clienteCpf, double valor) {
        Orcamento orcamento = new Orcamento(null, "ORC-" + System.currentTimeMillis(),
            clienteCpf, valor, StatusOrcamento.PENDENTE);
        Orcamento saved = repository.save(new OrcamentoEntity(orcamento)).toDomain();
        publicar("budget-generated", saved, "BUDGET_GENERATED");
        return saved;
    }

    @Override
    @Transactional
    public Orcamento gerarOrcamentoParaOs(SagaEvent event) {
        Optional<OrcamentoEntity> existente = repository.findByOsId(event.osId);
        if (existente.isPresent()) {
            log.info("Orcamento ja existe para a OS {}: {}", event.osNumero,
                existente.get().getCodigoOrcamento());
            return existente.get().toDomain();
        }

        double valor = event.valor != null && event.valor > 0 ? event.valor : valorPadrao;
        Orcamento orcamento = new Orcamento(null, "ORC-" + System.currentTimeMillis(),
            event.clienteCpf, valor, StatusOrcamento.PENDENTE);
        orcamento.setSagaId(event.sagaId);
        orcamento.setOsId(event.osId);

        Orcamento saved = repository.save(new OrcamentoEntity(orcamento)).toDomain();
        publicar("budget-generated", saved, "BUDGET_GENERATED");
        return saved;
    }

    @Override
    @Transactional
    public Orcamento atualizarStatus(Long id, StatusOrcamento novoStatus) {
        Optional<OrcamentoEntity> optional = repository.findById(id);
        if (optional.isEmpty()) {
            return null;
        }
        OrcamentoEntity entity = optional.get();
        entity.setStatus(novoStatus);
        entity.setDataAtualizacao(LocalDateTime.now());
        if (novoStatus == StatusOrcamento.PAGO && entity.getPagamentoId() == null) {
            entity.setPagamentoId("PAG-" + System.currentTimeMillis());
        }
        Orcamento updated = repository.save(entity).toDomain();

        switch (novoStatus) {
            case APROVADO -> publicar("budget-approved", updated, "BUDGET_APPROVED");
            case PAGO -> publicar("payment-completed", updated, "PAYMENT_COMPLETED");
            case REJEITADO -> publicar("budget-rejected", updated, "BUDGET_REJECTED");
            default -> {
                // status intermediarios nao publicam evento
            }
        }
        return updated;
    }

    @Override
    @Transactional
    public Orcamento cancelarPorSaga(SagaEvent event) {
        Optional<OrcamentoEntity> optional = event.osId != null
            ? repository.findByOsId(event.osId)
            : repository.findById(event.orcamentoId != null ? event.orcamentoId : -1L);
        if (optional.isEmpty()) {
            log.warn("Nenhum orcamento encontrado para compensacao da saga {}", event.sagaId);
            return null;
        }
        OrcamentoEntity entity = optional.get();
        entity.setStatus(StatusOrcamento.CANCELADO);
        entity.setDataAtualizacao(LocalDateTime.now());
        Orcamento updated = repository.save(entity).toDomain();
        log.info("Orcamento {} cancelado pela compensacao da saga {}",
            updated.getCodigoOrcamento(), event.sagaId);
        return updated;
    }

    @Override
    @Transactional
    public CheckoutResult criarCheckout(Long id) {
        OrcamentoEntity entity = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Orcamento nao encontrado: " + id));
        CheckoutResult result = paymentGateway.criarCheckout(entity.toDomain());
        entity.setPreferenceId(result.preferenceId());
        entity.setInitPoint(result.initPoint());
        entity.setDataAtualizacao(LocalDateTime.now());
        repository.save(entity);
        return result;
    }

    @Override
    @Transactional
    public Orcamento processarPagamento(Long orcamentoId, PaymentResult resultado) {
        Optional<OrcamentoEntity> optional = orcamentoId != null
            ? repository.findById(orcamentoId)
            : repository.findByPagamentoId(resultado.paymentId());
        if (optional.isEmpty()) {
            log.warn("Orcamento nao encontrado para o pagamento {}", resultado.paymentId());
            return null;
        }

        OrcamentoEntity entity = optional.get();
        if (resultado.paymentId() != null && resultado.paymentId().equals(entity.getPagamentoId())
            && (entity.getStatus() == StatusOrcamento.PAGO
                || entity.getStatus() == StatusOrcamento.REJEITADO)) {
            log.info("Pagamento {} ja processado para o orcamento {}",
                resultado.paymentId(), entity.getCodigoOrcamento());
            return entity.toDomain();
        }

        entity.setPagamentoId(resultado.paymentId());
        entity.setDataAtualizacao(LocalDateTime.now());
        if (resultado.approved()) {
            entity.setStatus(StatusOrcamento.PAGO);
            Orcamento updated = repository.save(entity).toDomain();
            publicar("payment-completed", updated, "PAYMENT_COMPLETED");
            return updated;
        }

        entity.setStatus(StatusOrcamento.REJEITADO);
        Orcamento updated = repository.save(entity).toDomain();
        publicar("payment-failed", updated, "PAYMENT_FAILED");
        return updated;
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

    private void publicar(String topico, Orcamento orcamento, String tipo) {
        SagaEvent event = SagaEvent.of(tipo, orcamento.getSagaId());
        event.osId = orcamento.getOsId();
        event.orcamentoId = orcamento.getId();
        event.codigoOrcamento = orcamento.getCodigoOrcamento();
        event.clienteCpf = orcamento.getClienteCpf();
        event.valor = orcamento.getValor();
        event.status = orcamento.getStatus() != null ? orcamento.getStatus().name() : null;
        event.paymentId = orcamento.getPagamentoId();
        event.message = "Orcamento " + orcamento.getCodigoOrcamento() + " - " + tipo;
        try {
            kafkaTemplate.send(topico, String.valueOf(orcamento.getId()),
                objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar evento " + tipo, e);
        }
    }
}
