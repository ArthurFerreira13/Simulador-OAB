package com.exame.ordem.legislacao.internal;

/**
 * Perfil de embedding: cada um tem a sua tabela (espaços vetoriais diferentes nunca se misturam).
 * O nome da tabela vem SEMPRE deste enum fechado, por isso pode ser concatenado ao SQL.
 */
public enum PerfilEmbedding {
    GEMINI("legislacao_embedding_gemini", "GEMINI", "gemini-embedding-001", "geminiEmbeddingModel"),
    OLLAMA("legislacao_embedding_ollama", "OLLAMA", "nomic-embed-text", "ollamaEmbeddingModel");

    public static final int DIMENSAO = 768;

    private final String tabela;
    private final String provedor;
    private final String modeloPadrao;
    private final String beanEmbeddingModel;

    PerfilEmbedding(String tabela, String provedor, String modeloPadrao, String beanEmbeddingModel) {
        this.tabela = tabela;
        this.provedor = provedor;
        this.modeloPadrao = modeloPadrao;
        this.beanEmbeddingModel = beanEmbeddingModel;
    }

    public String tabela() { return tabela; }
    public String provedor() { return provedor; }
    public String modeloPadrao() { return modeloPadrao; }
    /** Nome do bean EmbeddingModel já existente na aplicação (definido hoje na config do tutor). */
    public String beanEmbeddingModel() { return beanEmbeddingModel; }
}
