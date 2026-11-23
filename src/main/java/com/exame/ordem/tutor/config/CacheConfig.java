package com.exame.ordem.tutor.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

/**
 * Configuração de cache em memória do módulo Tutor.
 *
 * <p><b>Fluxo de dados:</b> o banco é consultado primeiro (contexto da
 * questão, alternativas e comentários). Se o RAG não encontrar nada
 * relevante, o LLM entra como fallback explicativo.
 *
 * <p><b>Por que {@link SimpleCacheManager} e não {@code CaffeineCacheManager}:</b>
 * cada cache tem TTL e capacidade próprios. O {@code CaffeineCacheManager}
 * só expõe <em>uma</em> especificação de Caffeine por manager; para políticas
 * por cache, a documentação oficial recomenda {@code SimpleCacheManager} com
 * instâncias individuais de {@link CaffeineCache}. Ver
 * <a href="https://docs.spring.io/spring-boot/reference/io/caching.html">
 * Spring Boot – Caching</a>.
 *
 * <p><b>Política de invalidação:</b> todos os caches usam apenas
 * {@code expireAfterWrite}. Os três primeiros ({@code comentarios-questao},
 * {@code contexto-questao}, {@code comentario-individual}) são derivados
 * de dados do banco imutáveis por questão ({@code questoes}, {@code provas},
 * {@code alternativas}), então o TTL é defesa contra edição manual.
 *
 * <p>Os dois últimos ({@code rag-embedding} e {@code llm-resposta}) NÃO
 * são imutáveis: {@code rag-embedding} muda quando novos comentários são
 * indexados, e {@code llm-resposta} depende do estado do RAG no momento
 * da geração. A chave de {@code llm-resposta} não inclui as fontes RAG
 * recuperadas (ver {@code TutorPromptFormatter}), então a resposta em
 * cache pode ficar defasada até o TTL de 24 h expirar. É uma dívida
 * consciente — corrigir exige incluir o hash das fontes na chave ou
 * limpar o cache após a indexação.
 *
 * <p>Esta configuração substitui a auto-configuração do Spring Boot
 * (que criaria um único {@code CaffeineCacheManager} global).
 */
@Configuration(proxyBeanMethods = false)
@EnableCaching
public class CacheConfig {

    public static final String COMENTARIOS_QUESTAO = "comentarios-questao";
    public static final String CONTEXTO_QUESTAO = "contexto-questao";
    public static final String COMENTARIO_INDIVIDUAL = "comentario-individual";
    public static final String LLM_RESPOSTA = "llm-resposta";
    public static final String RAG_EMBEDDING = "rag-embedding";

    @Bean
    public CacheManager cacheManager() {
        var comentarios = criarCache(COMENTARIOS_QUESTAO, 4_000, Duration.ofMinutes(10));
        var contexto = criarCache(CONTEXTO_QUESTAO, 8_000, Duration.ofMinutes(30));
        var individual = criarCache(COMENTARIO_INDIVIDUAL, 12_000, Duration.ofMinutes(10));

        // 24h reduz custo de tokens; trade-off aceito contra staleness
        // do RAG descrita no Javadoc da classe.
        var llmResposta = criarCache(LLM_RESPOSTA, 10_000, Duration.ofHours(24));

        var ragEmbedding = criarCache(RAG_EMBEDDING, 8_000, Duration.ofHours(2));

        var manager = new SimpleCacheManager();
        manager.setCaches(List.of(comentarios, contexto, individual, llmResposta, ragEmbedding));
        return manager;
    }

    /**
     * Cria um {@link CaffeineCache} com política própria de expiração
     * ({@code expireAfterWrite}) e capacidade ({@code maximumSize}).
     *
     * @param nome          nome do cache (deve bater com {@code cacheNames} das anotações)
     * @param tamanhoMaximo número máximo de entradas
     * @param ttl           tempo de vida após a escrita
     */
    private CaffeineCache criarCache(String nome, long tamanhoMaximo, Duration ttl) {
        return new CaffeineCache(
                nome,
                Caffeine.newBuilder()
                        .maximumSize(tamanhoMaximo)
                        .expireAfterWrite(ttl)
                        .recordStats() // expõe métricas via Micrometer
                        .build()
        );
    }
}