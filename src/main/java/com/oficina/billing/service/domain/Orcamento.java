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
