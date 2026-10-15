package com.oficina.billing.service.infrastructure.persistence;

import com.oficina.billing.service.domain.Orcamento;
import com.oficina.billing.service.domain.StatusOrcamento;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "orcamento")
public class OrcamentoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo_orcamento", unique = true, nullable = false)
    private String codigoOrcamento;

    @Column(name = "cliente_cpf", nullable = false, length = 14)
    private String clienteCpf;

    @Column(name = "valor", nullable = false)
    private Double valor;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StatusOrcamento status;

    @Column(name = "data_criacao", nullable = false)
    private LocalDateTime dataCriacao;

    @Column(name = "data_atualizacao")
    private LocalDateTime dataAtualizacao;

    @Column(name = "pagamento_id")
    private String pagamentoId;

    // Default constructor
    public OrcamentoEntity() {
        this.dataCriacao = LocalDateTime.now();
        this.dataAtualizacao = this.dataCriacao;
    }

    // Constructor from domain
    public OrcamentoEntity(Orcamento dominio) {
        this.id = dominio.getId();
        this.codigoOrcamento = dominio.getCodigoOrcamento();
        this.clienteCpf = dominio.getClienteCpf();
        this.valor = dominio.getValor();
        this.status = dominio.getStatus();
        this.dataCriacao = LocalDateTime.now();
        this.dataAtualizacao = this.dataCriacao;
        this.pagamentoId = dominio.getPagamentoId();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCodigoOrcamento() { return codigoOrcamento; }
    public void setCodigoOrcamento(String codigoOrcamento) { this.codigoOrcamento = codigoOrcamento; }
    public String getClienteCpf() { return clienteCpf; }
    public void setClienteCpf(String clienteCpf) { this.clienteCpf = clienteCpf; }
    public Double getValor() { return valor; }
    public void setValor(Double valor) { this.valor = valor; }
    public StatusOrcamento getStatus() { return status; }
    public void setStatus(StatusOrcamento status) { this.status = status; }
    public String getDataCriacao() { return dataCriacao.toString(); }
    public void setDataCriacao(LocalDateTime dataCriacao) { this.dataCriacao = dataCriacao; }
    public String getDataAtualizacao() { return dataAtualizacao.toString(); }
    public void setDataAtualizacao(LocalDateTime dataAtualizacao) { this.dataAtualizacao = dataAtualizacao; }
    public String getPagamentoId() { return pagamentoId; }
    public void setPagamentoId(String pagamentoId) { this.pagamentoId = pagamentoId; }

    // Convert to domain
    public Orcamento toDomain() {
        Orcamento dominio = new Orcamento();
        dominio.setId(this.id);
        dominio.setCodigoOrcamento(this.codigoOrcamento);
        dominio.setClienteCpf(this.clienteCpf);
        dominio.setValor(this.valor);
        dominio.setStatus(this.status);
        dominio.setDataCriacao(this.dataCriacao.toString());
        dominio.setDataAtualizacao(this.dataAtualizacao.toString());
        dominio.setPagamentoId(this.pagamentoId);
        return dominio;
    }
}
