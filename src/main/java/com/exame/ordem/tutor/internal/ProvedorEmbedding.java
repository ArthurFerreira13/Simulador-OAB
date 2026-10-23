package com.exame.ordem.tutor.internal;

/**
 * Provedores de embedding do RAG. Fonte única do nome da tabela pgvector e do prefixo de log:
 * antes, as strings das tabelas estavam espalhadas em 4 classes. Como o enum é fechado, ele
 * também substitui a whitelist manual do {@link ComentariosPendentesRepository} (o nome da
 * tabela vai concatenado no SQL, então só pode vir de uma lista fechada).
 *
 * <p>Cada modelo gera vetores em espaço diferente: a pergunta tem que ser embutida pelo mesmo
 * provedor cuja tabela está sendo consultada.
 */
public enum ProvedorEmbedding {

    GEMINI("comentario_embedding_gemini", "[Gemini]"),
    OLLAMA("comentario_embedding_ollama", "[Ollama]");

    private final String tabela;
    private final String prefixoLog;

    ProvedorEmbedding(String tabela, String prefixoLog) {
        this.tabela = tabela;
        this.prefixoLog = prefixoLog;
    }

    public String tabela() { return tabela; }
    public String prefixoLog() { return prefixoLog; }
}