package com.oficina.billing.service.domain;

public class Orcamento {
    private Long id;
    private String codigoOrcamento;
    private String clienteCpf;
    private double valor;
    private StatusOrcamento status;
    private String dataCriacao;
    private String dataAtualizacao;
    private String pagamentoId; // ID do pagamento no Mercado Pago
    private String sagaId;
    private Long osId;
    private String preferenceId;
    private String initPoint;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCodigoOrcamento() { return codigoOrcamento; }
    public void setCodigoOrcamento(String codigoOrcamento) { this.codigoOrcamento = codigoOrcamento; }
    public String getClienteCpf() { return clienteCpf; }
    public void setClienteCpf(String clienteCpf) { this.clienteCpf = clienteCpf; }
    public double getValor() { return valor; }
    public void setValor(double valor) { this.valor = valor; }
    public StatusOrcamento getStatus() { return status; }
    public void setStatus(StatusOrcamento status) { this.status = status; }
    public String getDataCriacao() { return dataCriacao; }
    public void setDataCriacao(String dataCriacao) { this.dataCriacao = dataCriacao; }
    public String getDataAtualizacao() { return dataAtualizacao; }
    public void setDataAtualizacao(String dataAtualizacao) { this.dataAtualizacao = dataAtualizacao; }
    public String getPagamentoId() { return pagamentoId; }
    public void setPagamentoId(String pagamentoId) { this.pagamentoId = pagamentoId; }
    public String getSagaId() { return sagaId; }
    public void setSagaId(String sagaId) { this.sagaId = sagaId; }
    public Long getOsId() { return osId; }
    public void setOsId(Long osId) { this.osId = osId; }
    public String getPreferenceId() { return preferenceId; }
    public void setPreferenceId(String preferenceId) { this.preferenceId = preferenceId; }
    public String getInitPoint() { return initPoint; }
    public void setInitPoint(String initPoint) { this.initPoint = initPoint; }

    // Constructors
    public Orcamento() {}

    public Orcamento(Long id, String codigoOrcamento, String clienteCpf, double valor, StatusOrcamento status) {
        this.id = id;
        this.codigoOrcamento = codigoOrcamento;
        this.clienteCpf = clienteCpf;
        this.valor = valor;
        this.status = status;
        this.dataCriacao = java.time.LocalDateTime.now().toString();
        this.dataAtualizacao = this.dataCriacao;
    }
}
