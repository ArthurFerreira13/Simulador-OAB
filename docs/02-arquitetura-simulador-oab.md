# Arquitetura — Simulador-OAB

| Campo | Valor |
|---|---|
| **Documento** | 02 — Arquitetura e Decisões Arquiteturais |
| **Versão** | 6.0 |
| **Data** | 09/10/2026 |
| **Responsável** | Arthur |
| **Status** | Em revisão |
| **Relacionados** | `01-requisitos-simulador-oab.md` · `03-status-roadmap-riscos.md` |

> **Escopo:** define **como** o sistema é estruturado. Os requisitos estão em `01`; o estado de implementação e as decisões em aberto estão em `03`. Itens marcados como **Proposto** ainda dependem de validação.

---

## 1. Direcionadores arquiteturais

| Direcionador | Origem | Implicação |
|---|---|---|
| Evolução incremental por MVPs, com módulos de maturidade diferente | `01` §6 | Fronteiras claras entre módulos para entregar e testar de forma independente |
| Custo variável do LLM e superfície pública sem autenticação | RNF05, RNF13 | Isolamento de falhas, limites de uso e *rate limiting* por módulo |
| Integridade de dados do simulado | RNF04 | Persistência por resposta; estado sempre recuperável |
| Equipe pequena, volume inicial baixo | `01` §5 | Monólito modular com banco único; sem infraestrutura de IA dedicada |
| Privacidade (LGPD) | RNF09 | Dados pessoais com dono único e exclusão propagada por evento |

## 2. Estilo arquitetural

**Monólito modular** com Spring Boot 4.1 e Spring Modulith 2.1, banco PostgreSQL 18 com pgvector e frontend Angular desacoplado (SPA). Cada módulo é um contexto delimitado (DDD) que se comunica com os demais apenas por **API pública** ou **eventos de domínio** (ADR-001).

```
Angular SPA (planejado)
      │ REST/JSON + SSE (streaming do tutor)
      ▼
┌─────────────────────── Spring Boot / Spring Modulith ───────────────────────┐
│ questoes  usuarios  administradores  simulados  tutor-ia  legislacao        │
│ planejamento  ranking  gamificacao  anotacoes  aquisicao  assinatura        │
│ dashboard (read-model)                                                      │
│        ▲ ── comunicação somente por API pública e eventos de domínio ── ▲   │
└──────────────┬───────────────────────────────┬───────────────┬──────────────┘
               ▼                               ▼               ▼
      PostgreSQL 18 + pgvector        Provedor de LLM     Gateway de pagamento
      (Flyway: dono do schema)        (via langchain4j)   (webhooks + tokenização)
```

## 3. Módulos

| Módulo | Responsabilidade | Dados próprios | Eventos publicados |
|---|---|---|---|
| `questoes` | Catálogo de provas, questões, alternativas e disciplinas; ingestão FGV; verificação de resposta; estatística por questão e dificuldade calculada | Prova, Questao, Alternativa, Disciplina, EstatisticaQuestao | `RespostaRegistrada` (resposta avulsa) |
| `usuarios` | Identidade, perfil, preferências de privacidade, exclusão de conta | Usuario | `UsuarioCriado`, `UsuarioExcluido` |
| `administradores` | Identidade e operações administrativas, isoladas de `usuarios` | Administrador | — |
| `simulados` | Sessões, respostas, resultado e desempenho por disciplina | SimuladoSessao, SessaoQuestao, RespostaSimulado, ResultadoSimulado | `RespostaRegistrada`, `SimuladoConcluido` |
| `tutor-ia` | Conversas, RAG, controles de qualidade e cotas de uso | ConversaTutor, MensagemTutor | — |
| `legislacao` | Diplomas, artigos, súmulas e embeddings | Legislacao, ArtigoLei | `LegislacaoAtualizada` |
| `planejamento` | Planos, tarefas e presets | PlanoDeEstudo, TarefaEstudo | — |
| `ranking` | Pontuação, elegibilidade e snapshots | RankingSnapshot | — |
| `gamificacao` | Jogos, pontuação e **streak** (dono do dado) | PontuacaoJogo, Streak | `StreakAtualizado` |
| `anotacoes` | Notas do usuário | Anotacao | — |
| `aquisicao` | Desafio anônimo, funil e conversão | SessaoDesafio | `DesafioConcluido` |
| `assinatura` | Planos, assinaturas, pagamentos e webhooks | Assinatura | `AssinaturaAlterada` |
| `dashboard` | *Read-model* agregado para a tela inicial (sem regra de negócio) | Projeções próprias | — |

**Estrutura de pacotes (padrão por módulo):** `internal/` (entidades e repositórios *package-private*) · interface/porta pública · `service/` (implementação *package-private*) · `dto/` (*records* públicos) · `controller/` (REST).

## 4. Comunicação entre módulos

| Evento | Publicador | Consumidores | Finalidade |
|---|---|---|---|
| `RespostaRegistrada` | questoes, simulados | questoes (estatística), gamificacao, ranking, dashboard, planejamento | Atualizar métricas, streak e dificuldade |
| `SimuladoConcluido` | simulados | ranking, planejamento, dashboard, gamificacao | Pontuação, sugestão de foco, métricas |
| `DesafioConcluido` | aquisicao | aquisicao | Disparar CTA de cadastro e registrar funil |
| `UsuarioCriado` | usuarios | aquisicao | Associar sessão anônima ao usuário (conversão) |
| `UsuarioExcluido` | usuarios | todos os módulos com dado pessoal | Exclusão propagada (LGPD) |
| `AssinaturaAlterada` | assinatura | tutor-ia, usuarios | Aplicar cotas e liberar/bloquear recursos |
| `LegislacaoAtualizada` | legislacao | tutor-ia | Reindexar embeddings |
| `StreakAtualizado` | gamificacao | dashboard | Exibir sequência |

**Diretrizes:** consultas síncronas entre módulos passam sempre pela porta pública do módulo dono; eventos relevantes são persistidos no registro de publicações do Spring Modulith, para não se perderem em caso de falha (ADR-010).

## 5. Aplicação dos 10 princípios de arquitetura modular

| # | Princípio | Diretriz neste projeto | Situação / ação |
|---|---|---|---|
| 1 | Encapsulamento por contexto delimitado | Cada módulo é um contexto DDD; internals *package-private* | Adotado |
| 2 | Composabilidade | Módulos dependem apenas de APIs públicas versionadas, permitindo reuso em outras aplicações (ex.: plataforma ENEM) | Documentar contratos públicos; verificar com `ApplicationModules.verify()` |
| 3 | Independência | Cada módulo contém domínio, endpoints, testes e migrações | **Lacuna:** migrações em sequência única (V1–V7). Avaliar *locations* do Flyway por módulo (ADR-006) |
| 4 | Isolamento de estado | Cada módulo é dono exclusivo de suas tabelas | **Lacuna:** `simulados` mantém FKs físicas para `usuarios` e `questoes` (ADR-006) |
| 5 | Comunicação explícita | Sem chamadas a internals; usar API, eventos ou Façade | Adotado; reforçar com testes de arquitetura |
| 6 | Substituibilidade | Dependências externas atrás de portas (`LlmProvider`, `PaymentGateway`, `EmbeddingStore`), trocáveis por configuração | A implementar nos módulos `tutor-ia` e `assinatura` |
| 7 | Deploy independente | Preservar a possibilidade de build orientado a módulo (monorepo) | Fora do MVP; não introduzir acoplamentos que inviabilizem |
| 8 | Escalabilidade independente | Backend *stateless*; `tutor-ia` e `aquisicao` são os primeiros candidatos a execução isolada (custo e tráfego público) | Planejado, sem ação imediata |
| 9 | Monitoramento por módulo | Tag `module` em métricas Micrometer e no MDC dos logs; *health checks* por módulo | A implementar (RNF10) |
| 10 | Falhas isoladas | *Timeouts*, *circuit breaker*, *bulkhead* e encerramento gracioso nas integrações; dashboard por projeção, não por chamadas em cascata | **Lacuna:** não especificado antes desta revisão (RNF05) |

## 6. Modelo de dados

**Implementado:** `Prova`, `Questao` (com disciplina classificada), `Alternativa`, `Disciplina`, `Usuario` (e-mail imutável, `ProvedorAuth`: LOCAL, GOOGLE, FACEBOOK, EMAIL_LINK), `Administrador`; schema de simulados (sessão, questões da sessão, respostas, resultado).

**Planejado:**

| Entidade | Atributos principais | Módulo |
|---|---|---|
| Legislacao / ArtigoLei | nome; legislacaoId, número, texto, referência de embedding | legislacao |
| Anotacao | usuarioId, referenciaTipo (QUESTAO, ARTIGO, DISCIPLINA, VIDEO, AVULSA), referenciaId (nulo se avulsa), texto, criadoEm | anotacoes |
| PlanoDeEstudo / TarefaEstudo | usuarioId, dataProva, horasSemanais, preset; planoId, disciplinaId, data, concluída | planejamento |
| RankingSnapshot | usuarioId, período, pontuação, posição, totalQuestoesRespondidas | ranking |
| ConversaTutor / MensagemTutor | usuarioId, questaoId opcional, papel (USER/ASSISTANT), conteúdo, fontes, criadoEm | tutor-ia |
| Assinatura | usuarioId, plano, status (TRIAL, ATIVA, CANCELADA, INADIMPLENTE), datas, referência do gateway | assinatura |
| SessaoDesafio | identificador anônimo, respondidas, acertos, convertidoEmUsuarioId opcional, criadoEm (modelo deve acomodar D01/D02) | aquisicao |
| PontuacaoJogo / Streak | usuarioId, jogo, pontuação, sequência atual, melhor sequência, atualizadoEm | gamificacao |
| EstatisticaQuestao | questaoId, totalRespostas, totalAcertos, taxaAcerto, tempoMédio, dificuldadeCalculada, atualizadoEm | questoes |

Regra de projeto: preferir resolver na camada de serviço a adicionar complexidade de schema (ex.: busca do número da prova em memória, sem coluna extra).

## 7. IA e RAG (módulo `tutor-ia`)

- **Orquestração:** langchain4j; provedor de LLM atrás de porta substituível (princípio 6).
- **Base de conhecimento:** pgvector com embeddings de artigos de lei e comentários de questões; pipeline de ingestão de legislação reaproveita o padrão de parsing usado nas provas.
- **Qualidade:** resposta ancorada em trechos recuperados; sem trecho relevante, a resposta é sinalizada como sem fonte (RN09); aviso permanente de limitação (RF04.6).
- **Rastreabilidade:** a resposta guarda as fontes usadas e linka o artigo (RF04.4).
- **Custo e abuso:** cotas por usuário/plano, *rate limiting* e métrica de tokens por usuário (RNF13).
- **Resiliência:** *timeout*, *circuit breaker* e mensagem de contingência (RNF05); falha do tutor não afeta simulados.
- **Entrega:** *streaming* por SSE, com cancelamento de geração.

## 8. Segurança

- Autenticação temporária (e-mail e senha, hash BCrypt) até a fase final; JWT com refresh token e *rate limiting* na entrega de RF01.2 (ADR-003).
- Endpoints do módulo `aquisicao` públicos, com limitação de taxa agressiva por origem e proteção anti-bot (captcha se necessário).
- Webhooks do gateway validados por assinatura/segredo; nenhum dado de cartão no backend.
- Segredos fora do repositório; varredura de segredos no CI; nenhuma chave privilegiada no client.
- Administradores isolados de usuários; cadastro de administrador apenas com perfil `dev`.

## 9. Frontend (Angular, planejado)

- *Standalone components* e Signals; gerenciamento de estado leve (Signals/RxJS), sem NgRx salvo necessidade comprovada.
- Rotas espelhando os módulos: `/questoes`, `/simulados`, `/tutor`, `/legislacao`, `/planejamento`, `/ranking`, `/anotacoes`, `/desafio`, `/jogos`, `/planos`.
- Simulado: cronômetro no cliente sincronizado com o servidor, navegação sem recarga e *autosave* a cada resposta.
- Interceptor HTTP para JWT e *refresh* automático (quando RF01.2 existir); rota `/desafio` sem *guard* de autenticação.

## 10. Integrações externas

| Integração | Uso | Situação |
|---|---|---|
| oab.fgv.br | Ingestão de provas e gabaritos | Implementada |
| Provedor de LLM | Tutor de IA | Dependência presente; módulo não construído |
| Gateway de pagamento | Assinatura (cartão recorrente, PIX/boleto) | A escolher (D05) |
| Serviço de jurisprudência | Enriquecimento do tutor | Candidato, não confirmado |
| E-mail transacional / push | Recuperação de senha, lembretes e streak | Futuro |

## 11. Registro de decisões arquiteturais (ADR)

| ID | Decisão | Status | Consequência principal |
|---|---|---|---|
| ADR-001 | Monólito modular (Spring Modulith) com banco único | Aceito | Simplicidade operacional; exige disciplina nas fronteiras |
| ADR-002 | Flyway como dono exclusivo do schema (`ddl-auto: validate`) | Aceito | Mudanças de schema sempre versionadas |
| ADR-003 | Autenticação temporária; JWT e refresh token na fase final | Aceito (com risco R05) | Desenvolvimento sem bloqueio; ambiente não pode ser exposto |
| ADR-004 | Classificação de disciplinas determinística, sem IA | Aceito | Elimina erro de classificação por modelo |
| ADR-005 | pgvector no PostgreSQL para o RAG | Aceito | Sem banco vetorial separado no início |
| ADR-006 | Sem FK física entre tabelas de módulos diferentes; referência por ID e integridade via API/eventos | **Proposto** | Requer migração para remover FKs de `simulados` (V6). Alternativa: manter FKs como trade-off consciente de banco único, documentando o acoplamento |
| ADR-007 | `dashboard` como módulo *read-model* alimentado por eventos | **Proposto** | Evita chamadas em cascata e acoplamento entre cinco módulos |
| ADR-008 | Dificuldade calculada por desempenho real, em job assíncrono | Aceito | Depende de volume mínimo de respostas |
| ADR-009 | Administradores em módulo separado, cadastro apenas em `dev` | Aceito | Isolamento de segurança |
| ADR-010 | Eventos persistidos no registro de publicações do Spring Modulith | **Proposto** | Entrega garantida de eventos como `UsuarioExcluido` |
