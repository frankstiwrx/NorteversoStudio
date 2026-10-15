package com.frankstiwrx.norteversostudio.model;

public class Clube {

    // Identificação
    private int idPes;
    private String nomeCompleto;
    private String nomePes;
    private String sigla;

    // Características
    private int divisao;
    private String categoria;
    private String formacao;

    // Regras de geração
    private int quantidadeJogadores;
    private int overallMinimo;
    private int overallMaximo;

    // Construtor
    public Clube(int idPes, String nomeCompleto, String nomePes,
                 String sigla, int divisao, String categoria,
                 String formacao, int quantidadeJogadores,
                 int overallMinimo, int overallMaximo) {

        this.idPes = idPes;
        this.nomeCompleto = nomeCompleto;
        this.nomePes = nomePes;
        this.sigla = sigla;
        this.divisao = divisao;
        this.categoria = categoria;
        this.formacao = formacao;
        this.quantidadeJogadores = quantidadeJogadores;
        this.overallMinimo = overallMinimo;
        this.overallMaximo = overallMaximo;
    }

    // Getters

    public int getIdPes() {
        return idPes;
    }

    public String getNomeCompleto() {
        return nomeCompleto;
    }

    public String getNomePes() {
        return nomePes;
    }

    public String getSigla() {
        return sigla;
    }

    public int getDivisao() {
        return divisao;
    }

    public String getCategoria() {
        return categoria;
    }

    public String getFormacao() {
        return formacao;
    }

    public int getQuantidadeJogadores() {
        return quantidadeJogadores;
    }

    public int getOverallMinimo() {
        return overallMinimo;
    }

    public int getOverallMaximo() {
        return overallMaximo;
    }

    // Setters

    public void setCategoria(String categoria) {
        this.categoria = categoria;
    }

    public void setFormacao(String formacao) {
        this.formacao = formacao;
    }

    public void setQuantidadeJogadores(int quantidadeJogadores) {
        this.quantidadeJogadores = quantidadeJogadores;
    }

    public void setOverallMinimo(int overallMinimo) {
        this.overallMinimo = overallMinimo;
    }

    public void setOverallMaximo(int overallMaximo) {
        this.overallMaximo = overallMaximo;
    }

    // Representação textual

    @Override
    public String toString() {

        return "Clube: " + nomeCompleto +
               "\nID PES: " + idPes +
               "\nNome no PES: " + nomePes +
               "\nSigla: " + sigla +
               "\nDivisão: " + divisao +
               "\nCategoria: " + categoria +
               "\nFormação: " + formacao +
               "\nJogadores: " + quantidadeJogadores +
               "\nOverall: " + overallMinimo + "-" + overallMaximo;
    }
}