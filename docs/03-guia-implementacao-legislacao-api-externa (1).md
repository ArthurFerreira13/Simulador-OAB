# Guia de implementação — Legislação via API externa

## Simulador-OAB · Java, Spring Boot e Spring Modulith

**Versão:** 1.1  
**Data:** 17/11/2026  
**Escopo:** módulo `legislacao`, sincronização com API externa oficial, cache local, versionamento, embeddings e integração com o `tutor-ia`.

---

## 1. Objetivo

Este guia descreve como implementar o módulo de legislação do Simulador-OAB usando uma API externa oficial como origem dos dados, sem acoplar o Tutor IA diretamente ao provedor externo.

A solução deverá:

- consultar uma API externa de legislação;
- armazenar localmente os dados necessários para desempenho, auditoria e continuidade;
- detectar alterações por hash e versionamento;
- normalizar normas em artigos e trechos pesquisáveis;
- gerar embeddings para uso no RAG;
- permitir consulta textual e semântica;
- informar as fontes usadas pelo Tutor IA;
- tolerar indisponibilidade, lentidão, limite de requisições e mudanças do provedor;
- respeitar as fronteiras do monólito modular Spring Modulith.

A API de Dados Abertos Legislativos do Senado Federal é a fonte externa inicial recomendada. Ela possui acesso público, endpoints de legislação, formatos como JSON/XML/CSV e limite documentado de requisições. A integração deve tratar respostas `429` e `503` e observar cabeçalhos de depreciação. Consulte a [documentação oficial da API do Senado](https://legis.senado.leg.br/dadosabertos/api-docs/swagger-ui/index.html) e o [contrato OpenAPI](https://legis.senado.leg.br/dadosabertos/v3/api-docs).

---

## 2. Decisão arquitetural

A decisão é usar o padrão **Ports and Adapters**, também conhecido como Arquitetura Hexagonal, dentro de um monólito modular.

O usuário continuará utilizando um único Tutor IA. Porém, no código, `tutor` e `legislacao` serão **módulos irmãos**: o `tutor` orquestra a conversa e o `legislacao` é dono do domínio jurídico, da sincronização externa, das versões, dos artigos e dos embeddings.

Essa escolha segue o modelo padrão do Spring Modulith: o pacote-base de cada módulo é sua API pública e os subpacotes `internal` não podem ser referenciados por outros módulos. A documentação oficial também permite módulos aninhados, mas essa alternativa será reservada para uma implementação temporária ou para uma parte estritamente interna do Tutor. Como `legislacao` terá dados, ciclo de vida e consumidores próprios, não será criado como `tutor.legislacao`.

```text
br.com.simuladoroab
├── tutor              # conversa, histórico, prompt, LLM e streaming
└── legislacao         # normas, artigos, API externa, versões e embeddings
```

O `tutor` poderá depender da API pública de `legislacao`, mas `legislacao` não deve depender do `tutor`. Essa dependência unidirecional evita ciclo entre módulos.

```text
tutor ───────────────► legislacao
  │                       │
  │                       └──► API externa oficial
  └──► LlmProvider
```

O Spring Modulith documenta essa organização, as interfaces nomeadas, módulos aninhados e a verificação automática das fronteiras em [Fundamentals](https://docs.spring.io/spring-modulith/reference/fundamentals.html) e [Verifying Application Module Structure](https://docs.spring.io/spring-modulith/reference/verification.html).

### 2.1 Estrutura de módulos e contratos públicos

No módulo `legislacao`, exponha apenas os contratos que outros módulos precisam consumir:

```text
legislacao/
├── LegislacaoQuery.java       # contrato usado pelo tutor
├── LegislacaoSync.java        # contrato usado por administração/job
├── dto/                       # DTOs públicos do módulo
├── internal/                  # entidades, repositórios e detalhes internos
└── ...
```

No módulo `tutor`, a dependência deve apontar somente para `LegislacaoQuery`:

```java
public interface LegislacaoQuery {

    List<TrechoLegislacao> buscarTrechosRelevantes(
        String pergunta,
        String materia,
        int limite
    );
}
```

Não exponha `RestClient`, DTOs da API do Senado, entidades de persistência, repositórios ou serviços de indexação para o `tutor`.

Declare as dependências permitidas em `package-info.java`:

```java
@org.springframework.modulith.ApplicationModule(
    allowedDependencies = "legislacao"
)
package br.com.simuladoroab.tutor;
```

```java
@org.springframework.modulith.ApplicationModule
package br.com.simuladoroab.legislacao;
```

Se, no futuro, a API for colocada em um pacote separado, ele poderá ser declarado como uma interface nomeada, por exemplo `legislacao.api`, e a dependência poderá ser restringida para `legislacao :: api`. Para o primeiro incremento, o pacote-base de `legislacao` já é suficiente.

### 2.2 Alternativa de submódulo

O Spring Modulith suporta módulos aninhados, portanto esta estrutura é tecnicamente válida:

```text
tutor/
└── legislacao/
```

Não será a estrutura adotada neste projeto. Ela só deve ser usada se a legislação for exclusivamente uma implementação interna do Tutor, sem API de legislação, tela própria, administração, sincronização independente ou uso por outros módulos. Se for usada temporariamente, deve ser marcada com `@ApplicationModule` em `tutor/legislacao/package-info.java` e migrada para módulo irmão antes do crescimento do domínio.

### 2.3 Verificação obrigatória

Adicionar ao CI:

```java
class ArquiteturaModularTest {

    @Test
    void modulosRespeitamAsFronteiras() {
        ApplicationModules.of(SimuladorOabApplication.class).verify();
    }
}
```

Esse teste deve impedir ciclos e acessos do `tutor` aos internals de `legislacao`. Para testes de integração isolados, usar `@ApplicationModuleTest`, simulando a porta pública de `legislacao` quando o foco for o módulo `tutor`. A documentação oficial descreve esse mecanismo em [Integration Testing Application Modules](https://docs.spring.io/spring-modulith/reference/testing.html).

```java
@ApplicationModuleTest
class TutorIaModuleTest {

    @MockitoBean
    LegislacaoQuery legislacaoQuery;
}
```

### 2.4 Documentação gerada

Gerar diagramas e canvases de módulos no teste de documentação:

```java
class DocumentationTest {

    @Test
    void gerarDocumentacaoDosModulos() {
        var modules = ApplicationModules.of(SimuladorOabApplication.class);

        new Documenter(modules)
            .writeModulesAsPlantUml()
            .writeIndividualModulesAsPlantUml()
            .writeModuleCanvases();
    }
}
```

Assim, o relacionamento `tutor → legislacao` fica visível e revisável no repositório.

### 2.5 Responsabilidade de cada módulo

```text
tutor
├── recebe a pergunta
├── recupera o contexto da questão
├── consulta LegislacaoQuery
├── monta o contexto do LLM
├── controla streaming e cancelamento
├── salva conversa e fontes
└── apresenta o aviso de limitação

legislacao
├── consulta a API externa
├── normaliza normas e artigos
├── mantém versões e hashes
├── publica somente conteúdo validado
├── gera embeddings
├── pesquisa no pgvector
└── devolve trechos e fontes ao tutor
```

O usuário verá apenas um Tutor IA. A separação existe para proteger o domínio e facilitar testes, manutenção e evolução.

### 2.6 Regra para indexadores

O indexador existente do `tutor` deve continuar responsável apenas pelas fontes que pertencem ao Tutor, como comentários de questões. O indexador jurídico fica em `legislacao`:

```text
tutor/
└── IndexadorComentariosRunner.java

legislacao/
└── IndexadorLegislacaoRunner.java
```

Os dois podem compartilhar uma porta técnica de embeddings, mas não podem compartilhar repositórios ou tabelas. O `IndexadorLegislacaoRunner` é acionado pelo evento `LegislacaoAtualizada` e é idempotente.

### 2.7 Migração caso o código atual esteja dentro de tutor

Se os arquivos atuais de legislação estiverem em `tutor`, migrar em etapas:

1. criar o pacote-base `legislacao`;
2. mover primeiro os DTOs jurídicos e o contrato `LegislacaoQuery`;
3. mover provider externo, parser e configurações;
4. mover entidades, repositórios e indexador jurídico;
5. ajustar o `TutorIaService` para depender somente da API pública;
6. remover imports de `tutor` nos componentes de legislação;
7. adicionar `allowedDependencies` e `ApplicationModules.verify()`;
8. executar testes do módulo `tutor` e do módulo `legislacao` separadamente;
9. apagar o código duplicado somente após a compilação e os testes passarem.

Não fazer a migração copiando as mesmas classes para os dois pacotes. Durante a transição, manter uma única implementação e concluir a mudança em um commit controlado.

```text
Antes:  tutor.IndexadorLegislacaoRunner
Depois: legislacao.IndexadorLegislacaoRunner
```

Depois da migração, o Tutor continuará sendo um único produto e um único fluxo de conversa; apenas a responsabilidade jurídica terá sido isolada corretamente.

### 2.8 Continuação da arquitetura Ports and Adapters

```text
                    ┌────────────────────┐
                    │  API REST interna  │
                    └─────────┬──────────┘
                              │
┌─────────────────────────────▼─────────────────────────────┐
│                       módulo legislacao                    │
│                                                             │
│  Casos de uso     Domínio       Portas       Adapters      │
│  Sincronizar      Norma         Query         Senado API    │
│  Publicar         Artigo        Command       PostgreSQL    │
│  Buscar            Versão       Embedding     LLM/embeddings│
└───────────┬───────────────────────────────────────┬─────────┘
            │                                       │
            ▼                                       ▼
      PostgreSQL + pgvector                 API externa oficial
            │
            ▼
          tutor-ia, somente pela porta pública
```

### Regras obrigatórias

1. O `tutor-ia` não chama a API do Senado.
2. O `tutor-ia` não acessa tabelas de `legislacao` diretamente.
3. O módulo `legislacao` é o dono de suas tabelas e migrações.
4. A API externa é acessada somente por um adapter.
5. O conteúdo externo é salvo localmente antes de ser usado no RAG.
6. Nenhuma resposta do Tutor IA deve citar uma fonte que não foi recuperada e armazenada.
7. A aplicação deve continuar funcionando para questões e simulados se a API externa estiver indisponível.

---

## 3. Estrutura de pacotes

Seguir a convenção modular já definida no projeto:

```text
src/main/java/br/com/simuladoroab/legislacao/
├── LegislacaoModule.java
├── api/
│   ├── LegislacaoQuery.java
│   ├── LegislacaoSync.java
│   └── dto/
│       ├── TrechoLegislacao.java
│       ├── NormaResumo.java
│       └── ArtigoDetalhe.java
├── controller/
│   ├── LegislacaoController.java
│   └── LegislacaoAdminController.java
├── domain/
│   ├── Norma.java
│   ├── NormaVersao.java
│   ├── Artigo.java
│   ├── TrechoIndexado.java
│   ├── StatusNorma.java
│   └── StatusImportacao.java
├── application/
│   ├── BuscarLegislacaoService.java
│   ├── SincronizarLegislacaoService.java
│   ├── PublicarVersaoService.java
│   └── ReindexarLegislacaoService.java
├── port/
│   ├── in/
│   │   ├── BuscarLegislacaoUseCase.java
│   │   └── SincronizarLegislacaoUseCase.java
│   └── out/
│       ├── LegislacaoProvider.java
│       ├── EmbeddingProvider.java
│       └── ClockProvider.java
├── adapter/
│   ├── senado/
│   │   ├── SenadoLegislacaoProvider.java
│   │   ├── SenadoProperties.java
│   │   ├── SenadoClientConfig.java
│   │   └── dto/
│   ├── persistence/
│   │   ├── NormaRepository.java
│   │   ├── NormaVersaoRepository.java
│   │   └── ArtigoRepository.java
│   └── embedding/
│       └── LangChain4jEmbeddingAdapter.java
└── exception/
    ├── LegislacaoNotFoundException.java
    └── ProvedorLegislacaoUnavailableException.java
```

Os pacotes `domain` e `application` não devem depender de classes específicas do Senado, WebClient, JPA ou do provedor de embeddings.

---

## 4. Dependências

No `pom.xml`, adicionar apenas as dependências realmente utilizadas. Exemplo:

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-jdbc</artifactId>
    </dependency>

    <dependency>
        <groupId>org.springframework.modulith</groupId>
        <artifactId>spring-modulith-starter-core</artifactId>
    </dependency>

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>

    <dependency>
        <groupId>io.github.resilience4j</groupId>
        <artifactId>resilience4j-spring-boot3</artifactId>
    </dependency>

    <dependency>
        <groupId>org.flywaydb</groupId>
        <artifactId>flyway-core</artifactId>
    </dependency>

    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <scope>runtime</scope>
    </dependency>

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>

    <dependency>
        <groupId>org.springframework.modulith</groupId>
        <artifactId>spring-modulith-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

Se o projeto já usa JPA, pode-se manter JPA para o domínio existente. Para este módulo, recomenda-se usar JDBC ou jOOQ quando a busca vetorial exigir SQL específico. A decisão deve ser uniforme no projeto. Não misture JPA e JDBC na mesma operação sem uma necessidade clara.

---

## 5. Configuração externa

Nunca coloque URL, token, limite ou modelo de embedding diretamente no código.

```yaml
legislacao:
  provider: senado
  sincronizacao:
    habilitada: false
    cron: "0 20 3 * * *"
    pagina-tamanho: 50
  senado:
    base-url: https://legis.senado.leg.br/dadosabertos
    timeout-conexao: 2s
    timeout-leitura: 10s
    max-tentativas: 3
  rag:
    dimensao-embedding: 1536
    minimo-relevancia: 0.72
```

Para produção, separar configurações por ambiente:

```yaml
# application-dev.yml
legislacao:
  sincronizacao:
    habilitada: true

# application-test.yml
legislacao:
  sincronizacao:
    habilitada: false

# application-prod.yml
legislacao:
  sincronizacao:
    habilitada: true
```

A sincronização não deve iniciar automaticamente em testes. Use `@ConditionalOnProperty` para impedir jobs acidentais.

---

## 6. Modelagem do banco

O Flyway será o único dono do schema. Criar a migration, por exemplo:

```text
db/migration/V14__criar_modulo_legislacao.sql
```

### 6.1 Tabelas principais

```sql
CREATE TABLE legislacao_norma (
    id UUID PRIMARY KEY,
    provedor VARCHAR(40) NOT NULL,
    identificador_externo VARCHAR(160) NOT NULL,
    tipo VARCHAR(80) NOT NULL,
    numero VARCHAR(80),
    ano INTEGER,
    nome VARCHAR(500) NOT NULL,
    ementa TEXT,
    materia VARCHAR(120),
    fonte_url TEXT NOT NULL,
    status VARCHAR(30) NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL,
    atualizado_em TIMESTAMPTZ NOT NULL,
    UNIQUE (provedor, identificador_externo)
);

CREATE TABLE legislacao_versao (
    id UUID PRIMARY KEY,
    norma_id UUID NOT NULL,
    hash_conteudo CHAR(64) NOT NULL,
    texto_bruto TEXT NOT NULL,
    fonte_consultada_em TIMESTAMPTZ NOT NULL,
    vigencia_inicio DATE,
    vigencia_fim DATE,
    status VARCHAR(30) NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL,
    UNIQUE (norma_id, hash_conteudo)
);

CREATE TABLE legislacao_artigo (
    id UUID PRIMARY KEY,
    versao_id UUID NOT NULL,
    identificador VARCHAR(80) NOT NULL,
    titulo VARCHAR(300),
    texto TEXT NOT NULL,
    ordem INTEGER NOT NULL,
    revogado BOOLEAN NOT NULL DEFAULT FALSE,
    criado_em TIMESTAMPTZ NOT NULL,
    UNIQUE (versao_id, identificador)
);

CREATE TABLE legislacao_trecho_embedding (
    id UUID PRIMARY KEY,
    artigo_id UUID NOT NULL,
    trecho TEXT NOT NULL,
    hash_trecho CHAR(64) NOT NULL,
    modelo VARCHAR(160) NOT NULL,
    embedding vector(1536) NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL,
    UNIQUE (artigo_id, hash_trecho, modelo)
);

CREATE INDEX idx_leg_norma_materia
    ON legislacao_norma (materia);

CREATE INDEX idx_leg_artigo_versao_ordem
    ON legislacao_artigo (versao_id, ordem);

CREATE INDEX idx_leg_embedding_hnsw
    ON legislacao_trecho_embedding
    USING hnsw (embedding vector_cosine_ops);
```

A dimensão `1536` é apenas um exemplo. Ela deve ser igual à dimensão real do modelo configurado. Se o modelo mudar, usar uma nova tabela ou estratégia de versionamento de índice; não misturar vetores de dimensões incompatíveis.

### 6.2 Integridade entre módulos

Não criar FK física para tabelas de `usuarios`, `questoes` ou `tutor-ia`. O módulo `legislacao` pode guardar referências externas como UUID ou código, mas não deve acessar tabelas de outro módulo diretamente.

---

## 7. Domínio e estados

Definir estados explícitos:

```java
public enum StatusNorma {
    ATIVA,
    REVOGADA,
    ARQUIVADA
}

public enum StatusImportacao {
    RECEBIDA,
    VALIDADA,
    PUBLICADA,
    REJEITADA,
    FALHA
}
```

A versão deve ter ciclo de vida claro:

```text
RECEBIDA → VALIDADA → PUBLICADA
              └──────→ REJEITADA
```

Apenas versões `PUBLICADA` e vigentes podem ser retornadas ao RAG. Uma versão antiga não deve ser apagada quando uma nova versão for publicada.

Evitar anemic domain model. As transições de estado devem ser protegidas por métodos de domínio ou por serviços de aplicação com invariantes testadas.

---

## 8. Porta para a API externa

Criar uma interface interna ao módulo:

```java
public interface LegislacaoProvider {

    Page<NormaExterna> listarNormas(
        LocalDate atualizadaDepoisDe,
        int pagina,
        int tamanho
    );

    NormaExterna obterNorma(String identificadorExterno);

    String obterTextoIntegral(String identificadorExterno);
}
```

Os DTOs externos devem ser próprios do adapter:

```java
public record NormaExterna(
    String identificador,
    String tipo,
    String numero,
    Integer ano,
    String nome,
    String ementa,
    String url,
    String texto
) {}
```

Não retornar `JsonNode`, DTO do Senado ou objeto do `WebClient` para a camada de domínio. Isso impede que o contrato do fornecedor se espalhe pelo sistema.

---

## 9. Cliente HTTP com `RestClient` ou `WebClient`

Para chamadas síncronas simples, `RestClient` é suficiente. Para streaming ou alto volume, usar `WebClient`. Exemplo com `RestClient`:

```java
@Configuration
class SenadoClientConfig {

    @Bean
    RestClient senadoRestClient(
        RestClient.Builder builder,
        SenadoProperties properties
    ) {
        return builder
            .baseUrl(properties.baseUrl())
            .requestFactory(new HttpComponentsClientHttpRequestFactory())
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }
}
```

```java
@Component
final class SenadoLegislacaoProvider implements LegislacaoProvider {

    private final RestClient client;
    private final SenadoProperties properties;

    SenadoLegislacaoProvider(RestClient client, SenadoProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public NormaExterna obterNorma(String identificadorExterno) {
        try {
            return client.get()
                .uri("/legislacao/{codigo}.json", identificadorExterno)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError,
                    (request, response) -> {
                        throw new NormaExternaNaoEncontradaException(
                            identificadorExterno
                        );
                    })
                .onStatus(HttpStatusCode::is5xxServerError,
                    (request, response) -> {
                        throw new ProvedorLegislacaoUnavailableException();
                    })
                .body(NormaExterna.class);
        } catch (ResourceAccessException ex) {
            throw new ProvedorLegislacaoUnavailableException(ex);
        }
    }
}
```

### Boas práticas do cliente HTTP

- definir timeout de conexão e leitura;
- usar `Accept` explicitamente;
- validar o `Content-Type` recebido;
- limitar tamanho máximo da resposta;
- registrar `correlationId` e identificador externo;
- nunca registrar texto jurídico integral em logs de erro;
- não fazer retry de erros `4xx` que indicam requisição inválida;
- respeitar `Retry-After` quando informado;
- tratar `429` com backoff;
- detectar cabeçalhos `Deprecation` e `Sunset`;
- usar circuit breaker e bulkhead;
- não chamar o provider dentro de uma requisição do Tutor IA.

---

## 10. Resiliência e rate limiting

Configurar Resilience4j para o adapter externo:

```yaml
resilience4j:
  retry:
    instances:
      senadoLegislacao:
        max-attempts: 3
        wait-duration: 2s
        enable-exponential-backoff: true
        exponential-backoff-multiplier: 2
  circuitbreaker:
    instances:
      senadoLegislacao:
        sliding-window-size: 20
        failure-rate-threshold: 50
        wait-duration-in-open-state: 60s
  ratelimiter:
    instances:
      senadoLegislacao:
        limit-for-period: 8
        limit-refresh-period: 1s
        timeout-duration: 0
```

Usar limite interno menor que o limite publicado pelo fornecedor. Assim existe margem para múltiplas instâncias e variações de tráfego.

```java
@Retry(name = "senadoLegislacao")
@CircuitBreaker(name = "senadoLegislacao", fallbackMethod = "fallback")
@RateLimiter(name = "senadoLegislacao")
public NormaExterna obterNorma(String id) {
    return client.obterNorma(id);
}
```

O fallback não deve inventar legislação. Para sincronização, deve registrar a falha e agendar nova tentativa. Para leitura, deve responder usando o cache local ou informar que a fonte está temporariamente indisponível.

---

## 11. Sincronização e idempotência

A sincronização deve ser um caso de uso explícito:

```java
@Service
@Transactional
final class SincronizarLegislacaoService {

    private final LegislacaoProvider provider;
    private final NormaRepository normaRepository;
    private final HashService hashService;
    private final Clock clock;

    public ResultadoSincronizacao executar(SolicitacaoSincronizacao solicitacao) {
        // 1. Consultar páginas da API externa.
        // 2. Normalizar cada norma.
        // 3. Calcular hash do texto normalizado.
        // 4. Ignorar conteúdo sem alteração.
        // 5. Criar nova versão para conteúdo novo.
        // 6. Persistir importação como VALIDADA.
        // 7. Publicar somente após validação.
        return null;
    }
}
```

A operação deve ser idempotente:

```text
mesma norma + mesmo hash → nenhuma nova versão
mesma norma + hash diferente → nova versão
mesma execução repetida → sem duplicidade
```

O hash deve ser calculado depois da normalização determinística. Remover diferenças irrelevantes, como espaços duplicados e quebras de linha, mas não alterar o conteúdo jurídico.

Usar `UNIQUE (provedor, identificador_externo)` e `UNIQUE (norma_id, hash_conteudo)` para reforçar a idempotência no banco.

---

## 12. Parsing dos artigos

A API externa pode retornar texto integral em formatos diferentes. O parser deve ficar isolado:

```java
public interface TextoLegalParser {
    List<ArtigoNormalizado> extrairArtigos(String textoIntegral);
}
```

Regras do parser:

- identificar `Art. 1º`, `Art. 1°`, `Art. 1o` e variações equivalentes;
- preservar parágrafos, incisos e alíneas;
- não eliminar notas de revogação;
- rejeitar texto sem nenhum artigo reconhecido;
- preservar a ordem original;
- manter o texto integral bruto para auditoria;
- registrar avisos quando houver trecho não interpretado;
- cobrir cada formato com testes de contrato.

Nunca publicar automaticamente uma versão cuja quantidade de artigos caiu de forma anormal sem revisão. Exemplo de regra de segurança: se a nova versão tiver menos de 70% dos artigos anteriores, interromper a publicação e marcar como `REVISÃO_MANUAL`.

---

## 13. Cache local e atualização

O cache local é parte da solução, não apenas uma otimização. Ele permite:

- continuar respondendo quando a API externa falhar;
- controlar a versão usada pelo Tutor IA;
- indexar uma vez e reutilizar muitas vezes;
- auditar a origem da resposta;
- reduzir o número de requisições externas.

Não usar apenas `@Cacheable` para guardar o conteúdo jurídico. O cache jurídico precisa ser persistente, versionado e consultável. O cache de aplicação pode ser adicionado depois para listas e consultas frequentes.

Para dados de leitura, usar uma estratégia como:

```text
Banco local publicado e vigente → resposta normal
Banco local sem dado → sincronização administrativa ou job
API externa indisponível → usar dado local; nunca fabricar dado
```

---

## 14. Publicação e evento de domínio

Depois da validação e persistência da versão, publicar um evento:

```java
public record LegislacaoAtualizada(
    UUID normaId,
    UUID versaoId,
    Instant ocorridoEm
) {}
```

O evento deve ser publicado somente depois do commit da transação, usando o mecanismo de eventos persistidos do Spring Modulith. O consumidor de indexação deve ser idempotente.

```java
@Component
class LegislacaoAtualizadaListener {

    @ApplicationModuleListener
    void aoAtualizar(LegislacaoAtualizada evento) {
        reindexarLegislacaoService.reindexar(evento.versaoId());
    }
}
```

A reindexação não deve bloquear a publicação da norma. O estado da indexação precisa ser observável:

```text
PENDENTE → PROCESSANDO → CONCLUÍDA
                    └──→ FALHA
```

---

## 15. Embeddings e pgvector

O texto do artigo deve ser dividido em trechos quando ultrapassar o limite recomendado pelo modelo. Cada trecho precisa guardar:

- `artigoId`;
- texto original do trecho;
- hash do trecho;
- modelo usado;
- dimensão do vetor;
- data de indexação.

O adapter de embedding deve ser uma porta:

```java
public interface EmbeddingProvider {
    List<float[]> embed(List<String> textos);
    String modelo();
    int dimensao();
}
```

Fluxo de indexação:

```text
artigo publicado
    → normalizar trechos
    → calcular hash
    → verificar trechos já indexados
    → gerar embeddings apenas dos novos
    → persistir em lote
    → marcar indexação como concluída
```

Busca semântica conceitual:

```sql
SELECT
    e.artigo_id,
    e.trecho,
    1 - (e.embedding <=> :consulta) AS relevancia
FROM legislacao_trecho_embedding e
JOIN legislacao_artigo a ON a.id = e.artigo_id
JOIN legislacao_versao v ON v.id = a.versao_id
WHERE v.status = 'PUBLICADA'
  AND a.revogado = FALSE
ORDER BY e.embedding <=> :consulta
LIMIT :limite;
```

Filtrar sempre por versão publicada e vigente. A busca não deve retornar artigo revogado como fundamento atual, embora seu histórico possa continuar disponível para consulta administrativa.

---

## 16. Porta pública para o `tutor-ia`

A API pública do módulo deve ser pequena e orientada ao caso de uso:

```java
public interface LegislacaoQuery {

    List<TrechoLegislacao> buscarTrechosRelevantes(
        String consulta,
        String materia,
        int limite
    );

    Optional<ArtigoDetalhe> buscarArtigo(UUID artigoId);
}
```

DTO público:

```java
public record TrechoLegislacao(
    UUID artigoId,
    String norma,
    String identificadorArtigo,
    String texto,
    String fonteUrl,
    double relevancia,
    Instant atualizadoEm
) {}
```

O `tutor-ia` recebe somente esse contrato. Não deve conhecer `NormaEntity`, `ArtigoEntity`, `RestClient` ou o formato da API do Senado.

---

## 17. Orquestração do RAG

O fluxo recomendado do Tutor IA será:

1. validar o acesso do usuário e a cota;
2. receber a pergunta;
3. incluir o contexto da questão, quando existir;
4. consultar `LegislacaoQuery`;
5. aplicar um limiar mínimo de relevância;
6. montar o contexto com os trechos recuperados;
7. solicitar resposta ao LLM;
8. armazenar as fontes retornadas;
9. transmitir a resposta por SSE;
10. exibir o aviso de que o Tutor IA pode errar.

Se nenhum trecho superar o limiar, a resposta deve informar que não houve fundamento legislativo recuperado. Não fazer uma chamada “sem contexto” e apresentá-la como resposta fundamentada.

Exemplo de resposta:

```json
{
  "texto": "A mora do devedor produz as consequências previstas nos dispositivos recuperados.",
  "possuiFonte": true,
  "fontes": [
    {
      "norma": "Código Civil",
      "artigo": "Art. 394",
      "url": "/api/v1/legislacao/artigos/2f..."
    }
  ],
  "aviso": "O Tutor IA é uma ferramenta de apoio aos estudos e pode cometer erros."
}
```

---

## 18. Endpoints REST do módulo

### Consulta do usuário

```http
GET /api/v1/legislacao?materia=CIVIL&page=0&size=20
GET /api/v1/legislacao/{normaId}
GET /api/v1/legislacao/{normaId}/artigos
GET /api/v1/legislacao/artigos/{artigoId}
GET /api/v1/legislacao/busca?q=mora&materia=CIVIL
```

### Administração

```http
POST /api/v1/admin/legislacao/sincronizacoes
GET  /api/v1/admin/legislacao/sincronizacoes/{id}
POST /api/v1/admin/legislacao/{normaId}/publicar
POST /api/v1/admin/legislacao/{normaId}/reindexar
```

Boas práticas dos controllers:

- usar DTOs de entrada e saída, nunca entidades;
- validar paginação e tamanho máximo;
- retornar `ProblemDetail` padronizado;
- incluir `correlationId` nas falhas;
- proteger endpoints administrativos com papel `ADMIN`;
- não expor texto bruto de importação para qualquer usuário;
- não retornar detalhes de stack trace;
- documentar o contrato no OpenAPI.

---

## 19. Agendamento

O job de sincronização deve ser separado da requisição HTTP:

```java
@Component
@ConditionalOnProperty(
    name = "legislacao.sincronizacao.habilitada",
    havingValue = "true"
)
final class LegislacaoSyncJob {

    private final SincronizarLegislacaoUseCase useCase;

    @Scheduled(cron = "${legislacao.sincronizacao.cron}")
    void executar() {
        useCase.sincronizarDesdeUltimaExecucao();
    }
}
```

Em múltiplas instâncias, não permitir que todas executem o job ao mesmo tempo. Usar um mecanismo de lock distribuído, como ShedLock, ou mover a execução para um worker dedicado.

O job deve possuir:

- timeout total;
- checkpoint por página;
- retentativa por item;
- registro da última execução;
- possibilidade de execução manual pelo administrador;
- métricas de itens consultados, alterados, ignorados e falhos.

---

## 20. Observabilidade

Criar métricas com a tag `module=legislacao`:

```text
legislacao_provider_requests_total
legislacao_provider_request_duration_seconds
legislacao_provider_errors_total
legislacao_sync_items_total
legislacao_sync_versions_created_total
legislacao_indexing_items_total
legislacao_indexing_failures_total
legislacao_rag_search_duration_seconds
```

Logs estruturados devem conter:

- `correlationId`;
- `module`;
- `operacao`;
- `identificadorExterno`;
- `versaoId`, quando aplicável;
- duração;
- resultado.

Não registrar:

- tokens ou segredos;
- cabeçalhos de autenticação;
- dados pessoais;
- texto integral em cada requisição;
- prompts completos do usuário em logs de produção.

Criar health indicators separados para:

- banco local;
- API externa;
- embeddings;
- fila/eventos;
- última sincronização bem-sucedida.

A indisponibilidade do provedor externo não deve fazer o health check geral derrubar a aplicação se o cache local estiver operacional. O status deve aparecer como dependência degradada.

---

## 21. Testes

### 21.1 Testes unitários

Cobrir:

- normalização do texto;
- parsing de artigos;
- cálculo de hash;
- detecção de alteração;
- transições de status;
- filtro de artigo revogado;
- limiar de relevância;
- criação de fontes;
- idempotência do caso de uso.

### 21.2 Testes de contrato do provider

Usar WireMock ou MockWebServer para simular a API do Senado:

- resposta válida JSON;
- `404` para norma inexistente;
- `429` com `Retry-After`;
- `503` temporário;
- payload incompleto;
- campo alterado;
- cabeçalho `Deprecation`;
- resposta acima do tamanho permitido.

### 21.3 Testes de integração

Usar Testcontainers com PostgreSQL e pgvector para validar:

- migrations Flyway;
- gravação de norma, versão e artigo;
- índice vetorial;
- consulta semântica;
- publicação de eventos;
- reindexação idempotente.

### 21.4 Testes de arquitetura

```java
class ArquiteturaTest {

    @Test
    void modulosDevemRespeitarFronteiras() {
        ApplicationModules.of(Application.class).verify();
    }
}
```

Adicionar regras para impedir que `tutor-ia` dependa de classes internas de `legislacao`.

### 21.5 Testes de segurança

Verificar que:

- somente `ADMIN` pode iniciar sincronização manual;
- usuário comum pode consultar legislação publicada;
- versões em rascunho não aparecem publicamente;
- a API externa não é chamada pelo frontend;
- segredos não aparecem no bundle nem no repositório.

---

## 22. Ordem recomendada de implementação

### Fase 1 — Contrato e infraestrutura

1. criar o módulo `legislacao`;
2. registrar o módulo no Spring Modulith;
3. criar configuração por ambiente;
4. criar migration Flyway;
5. criar entidades e repositórios;
6. adicionar Testcontainers;
7. validar `ApplicationModules.verify()`.

### Fase 2 — Provider externo

1. definir `LegislacaoProvider`;
2. criar DTOs do Senado dentro do adapter;
3. implementar o cliente HTTP;
4. configurar timeout;
5. configurar retry, circuit breaker e rate limiter;
6. criar testes WireMock;
7. tratar `429`, `503` e depreciação.

### Fase 3 — Sincronização

1. criar caso de uso de sincronização;
2. implementar paginação;
3. normalizar conteúdo;
4. calcular hash;
5. persistir versões;
6. implementar idempotência;
7. criar job agendado e execução manual;
8. adicionar métricas.

### Fase 4 — Artigos e consulta

1. implementar parser;
2. persistir artigos;
3. filtrar versões publicadas e vigentes;
4. criar endpoints REST;
5. adicionar busca textual;
6. documentar OpenAPI.

### Fase 5 — RAG

1. configurar `EmbeddingProvider`;
2. criar trechos;
3. gerar embeddings em lote;
4. persistir no pgvector;
5. implementar busca semântica;
6. publicar `LegislacaoAtualizada`;
7. integrar a porta `LegislacaoQuery` ao `tutor-ia`;
8. salvar fontes na resposta.

### Fase 6 — Operação

1. criar dashboard de sincronização;
2. adicionar alertas;
3. validar atualização de normas reais;
4. executar carga controlada;
5. revisar precisão das respostas;
6. documentar procedimento de rollback;
7. habilitar produção somente após a revisão dos critérios de aceite.

---

## 23. Critérios de aceite

A implementação será considerada pronta quando:

- o provider externo puder ser substituído sem alterar o domínio;
- uma norma puder ser sincronizada e persistida localmente;
- a mesma execução repetida não criar duplicidades;
- uma alteração de texto criar nova versão;
- a versão antiga permanecer auditável;
- os artigos puderem ser consultados por API;
- artigos revogados não forem usados como fonte vigente;
- a API externa não for chamada a cada pergunta do Tutor IA;
- indisponibilidade externa não interromper simulados e questões;
- o RAG retornar apenas trechos publicados;
- cada resposta fundamentada registrar norma, artigo, trecho e URL;
- resposta sem fonte for explicitamente sinalizada;
- `429` e `503` forem tratados com resiliência;
- jobs concorrentes não duplicarem sincronizações;
- migrations forem executadas pelo Flyway;
- `ApplicationModules.verify()` passar no CI;
- logs, métricas e health checks estiverem disponíveis;
- testes de unidade, integração, contrato e segurança passarem.

---

## 24. Checklist de produção

- [ ] URL do provider configurada por variável de ambiente.
- [ ] Segredos fora do Git.
- [ ] Timeouts configurados.
- [ ] Retry limitado e com backoff.
- [ ] Circuit breaker configurado.
- [ ] Rate limit abaixo do limite do fornecedor.
- [ ] Cache local preenchido antes de habilitar o Tutor IA.
- [ ] Backup do banco configurado.
- [ ] Reindexação idempotente validada.
- [ ] Alertas para falha de sincronização.
- [ ] Alertas para norma desatualizada.
- [ ] Teste de restauração executado.
- [ ] API pública sem acesso a rascunhos.
- [ ] Fonte exibida nas respostas do Tutor IA.
- [ ] Aviso de limitação da IA sempre visível.
- [ ] Contrato OpenAPI publicado.
- [ ] Verificação de módulos no CI.
- [ ] Procedimento de rollback documentado.

---

## 25. Resumo da decisão

A API externa será usada como **fonte de atualização**, não como banco de dados do Tutor IA. O módulo `legislacao` funcionará como uma camada de proteção e inteligência: consulta o fornecedor, normaliza e versiona o conteúdo, armazena uma cópia local, gera embeddings e oferece uma porta estável para o `tutor-ia`.

Essa abordagem atende às boas práticas de Spring Boot porque separa configuração, domínio, casos de uso, adapters, persistência e controllers. Também preserva as fronteiras do Spring Modulith, reduz a dependência operacional do fornecedor externo e permite trocar a API no futuro sem reescrever o RAG.
