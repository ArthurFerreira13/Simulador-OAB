# Arquitetura — Simulador-OAB

| Campo | Valor |
|---|---|
| **Documento** | 02 — Arquitetura e Decisões Arquiteturais |
| **Versão** | 6.3.2 |
| **Data** | 12/10/2026 |
| **Responsável** | Arthur |
| **Status** | Em revisão |
| **Relacionados** | `01-requisitos-simulador-oab.md` · `03-status-roadmap-riscos.md` · `04-arquitetura-mobile.md` |

> **Escopo:** define **como** o sistema é estruturado. Os requisitos estão em `01`; o estado de implementação e as decisões em aberto estão em `03`. Itens marcados como **Proposto** ainda dependem de validação.

---

## 1. Direcionadores arquiteturais

| Direcionador | Origem | Implicação |
|---|---|---|
| Evolução incremental por MVPs, com módulos de maturidade diferente | `01` §6 | Fronteiras claras entre módulos para entregar e testar de forma independente |
| Custo variável do LLM e superfície pública sem autenticação | RNF05, RNF13 | Isolamento de falhas, limites de uso e *rate limiting* por módulo |
| Integridade de dados do simulado | RNF04 | Persistência por resposta; estado sempre recuperável |
| Equipe pequena, volume inicial baixo | `01` §5 | Monólito modular com banco único; sem infraestrutura de IA dedicada |
| LGPD | RNF09, RNF20 | Dados pessoais com dono único; exclusão por evento |
| Dois clientes sobre a mesma API | RF13, RNF18, ADR-013 | Contrato OpenAPI versionado |
| Push e canal mobile | RF15, RNF19 | Provedor isolado; sem conteúdo sensível |
| Feature gate por estado de acesso | RNF21, RN14, RN15 | Enforcement no servidor |

## 2. Estilo arquitetural

**Monólito modular** com Spring Boot 4.1 e Spring Modulith 2.1, banco PostgreSQL 18 com pgvector e dois clientes desacoplados (Angular SPA e app Kotlin). Cada módulo é um contexto delimitado (DDD) que se comunica com os demais apenas por **API pública** ou **eventos de domínio** (ADR-001).

```
 Angular SPA (planejado)          App Android Kotlin (planejado)
        │ REST/JSON + SSE                  │ REST/JSON (OpenAPI /api/v1) + push
        └────────────────┬─────────────────┘
                         ▼
┌─────────────────── Spring Boot / Spring Modulith ───────────────────┐
│ questoes  usuarios  auth  administradores  simulados  tutor-ia      │
│ legislacao  planejamento  ranking  gamificacao  anotacoes           │
│ aquisicao  assinatura  notificacoes  sincronizacao  dashboard       │
│  ▲ ── comunicação somente por API pública e eventos de domínio ── ▲ │
└────────┬─────────────────┬──────────────┬───────────────┬───────────┘
         ▼                 ▼              ▼               ▼
  PostgreSQL 18      Provedor de LLM  Gateway de    Firebase Cloud
  + pgvector         (langchain4j)    pagamento     Messaging (FCM)
  (Flyway: dono do schema)            (webhooks)
```

## 3. Módulos

| Módulo | Responsabilidade | Dados próprios | Eventos publicados |
|---|---|---|---|
| `questoes` | Catálogo de provas, questões, alternativas e disciplinas; ingestão FGV; verificação de resposta; estatística por questão e dificuldade calculada | Prova, Questao, Alternativa, Disciplina, EstatisticaQuestao | `RespostaRegistrada` (resposta avulsa) |
| `usuarios` | Identidade, perfil, preferências de privacidade, exclusão de conta; trial reverso e **porta pública de acesso** (`PoliticaAcessoService`: estado, recursos permitidos, limite diário; RN13–RN15) | Usuario | `UsuarioCriado`, `UsuarioExcluido` |
| `auth` | Login/logout e emissão de JWT de acesso; futuramente refresh token rotativo e revogação (RF01.2b) | RefreshToken *(fase final)* | — |
| `administradores` | Identidade e operações administrativas (painel RF14), isoladas de `usuarios`; auditoria de ações (RN16) | Administrador, AdminAuditoria | — |
| `simulados` | Sessões, respostas, resultado e desempenho por disciplina; expõe a contagem de simulados iniciados no dia (para o limite do plano gratuito) | SimuladoSessao, SessaoQuestao, RespostaSimulado, ResultadoSimulado | `RespostaRegistrada`, `SimuladoConcluido`, `SimuladoAbandonado` |
| `tutor-ia` | Conversas, RAG, controles de qualidade e cotas de uso (consulta a porta de acesso, não duplica a regra) | ConversaTutor, MensagemTutor | — |
| `legislacao` | Diplomas, artigos, súmulas e embeddings | Legislacao, ArtigoLei | `LegislacaoAtualizada` |
| `planejamento` | Planos, tarefas e presets | PlanoDeEstudo, TarefaEstudo | `TarefaAgendada` |
| `ranking` | Pontuação, elegibilidade e snapshots | RankingSnapshot | — |
| `gamificacao` | Jogos, pontuação e **streak** (dono do dado) | PontuacaoJogo, Streak | `StreakAtualizado` |
| `anotacoes` | Notas do usuário | Anotacao | — |
| `aquisicao` | Desafio anônimo, funil e conversão | SessaoDesafio | `DesafioConcluido` |
| `assinatura` | Planos, assinaturas, pagamentos e webhooks | Assinatura | `AssinaturaAlterada` |
| `notificacoes` | Registro de dispositivos, preferências e envio de push via porta de provedor (RF15) | Dispositivo, PreferenciaNotificacao | — |
| `sincronizacao` *(proposto, Mobile 2A)* | Orquestra o `/me/sync` do app: agrega deltas das portas dos módulos donos e aplica a fila de operações idempotentes delegando às portas de escrita; sem regra de negócio nem dados de domínio | Fila/cursor de sincronização | — |
| `dashboard` | *Read-model* agregado para a tela inicial (sem regra de negócio) | Projeções próprias | — |

**Estrutura de pacotes (padrão por módulo):** `internal/` (entidades e repositórios *package-private*) · interface/porta pública · `service/` (implementação *package-private*) · `dto/` (*records* públicos) · `controller/` (REST).

## 4. Comunicação entre módulos

| Evento | Publicador | Consumidores | Finalidade |
|---|---|---|---|
| `RespostaRegistrada` | questoes, simulados | questoes (estatística), gamificacao, ranking, dashboard, planejamento | Atualizar métricas, streak e dificuldade |
| `SimuladoConcluido` | simulados | ranking, planejamento, dashboard, gamificacao | Pontuação, sugestão de foco, métricas |
| `DesafioConcluido` | aquisicao | aquisicao | Disparar CTA de cadastro e registrar funil |
| `UsuarioCriado` | usuarios | aquisicao | Associar sessão anônima ao usuário (conversão) |
| `UsuarioExcluido` | usuarios | todos os módulos com dado pessoal, incluindo `notificacoes` (dispositivos) | Exclusão propagada (LGPD); a auditoria administrativa mínima é preservada |
| `AssinaturaAlterada` | assinatura | usuarios | Atualizar o estado de acesso; os demais módulos consultam a porta de acesso |
| `SimuladoAbandonado` | simulados | notificacoes | Aviso de simulado retomável |
| `TarefaAgendada` | planejamento | notificacoes | Lembrete de tarefa do plano |
| `LegislacaoAtualizada` | legislacao | tutor-ia | Reindexar embeddings |
| `StreakAtualizado` | gamificacao | dashboard, notificacoes | Exibir sequência; alerta de streak se habilitado |

**Diretrizes:** consultas síncronas entre módulos passam sempre pela porta pública do módulo dono; eventos relevantes são persistidos no registro de publicações do Spring Modulith, para não se perderem em caso de falha (ADR-010). Os clientes (web e mobile) nunca se comunicam por eventos: usam apenas a API REST.

### 4.1 Portas de acesso e leituras entre módulos *(proposto)*

| Necessidade | Quem precisa | Mecanismo |
|---|---|---|
| Estado de acesso, recurso permitido, limite diário | `simulados`, `questoes`, `tutor-ia`, `planejamento`, `ranking`, `anotacoes` | Porta pública do `PoliticaAcessoService` em `usuarios` (único ponto de decisão, ADR-011). O módulo que serve o recurso consulta a porta e aplica o bloqueio; nenhum módulo reimplementa a regra |
| Contagem de simulados do dia | `usuarios` (para decidir o limite) | `simulados` expõe uma consulta pública de contagem; `usuarios` decide, `simulados` só informa |
| Contagens de uso por usuário (RF14.1) | `administradores` | Projeção própria alimentada por eventos, no mesmo padrão do `dashboard` (ADR-007); sem leitura de conteúdo (RN17) |
| Deltas e fila offline do app (`/me/sync`) | cliente mobile | Módulo `sincronizacao`: cada módulo dono expõe `alteracoesDesde(usuarioId, cursor)` e portas de escrita idempotentes; `sincronizacao` só compõe |
| Lembretes e avisos push | `notificacoes` | Eventos `TarefaAgendada` e `SimuladoAbandonado` alimentam a agenda; `notificacoes` não consulta o plano nem as sessões diretamente |

Regra geral (ADR-017): leitura que cruza módulos usa porta pública do dono ou projeção alimentada por eventos; nunca consulta ao banco de outro módulo nem cadeia de chamadas síncronas.

## 5. Aplicação dos 10 princípios de arquitetura modular

| # | Princípio | Diretriz neste projeto | Situação / ação |
|---|---|---|---|
| 1 | Encapsulamento por contexto delimitado | Cada módulo é um contexto DDD; internals *package-private* | Adotado |
| 2 | Composabilidade | Módulos dependem apenas de APIs públicas versionadas, permitindo reuso em outras aplicações (ex.: plataforma ENEM) | Documentar contratos públicos; verificar com `ApplicationModules.verify()` |
| 3 | Independência | Cada módulo contém domínio, endpoints, testes e migrações | **Lacuna:** migrações em sequência única (V1–V13). Decidir D06 e adotar *locations* do Flyway por módulo antes do MVP 2, antes de o `tutor-ia` criar tabelas (ADR-006) |
| 4 | Isolamento de estado | Cada módulo é dono exclusivo de suas tabelas | **Lacuna:** `simulados` mantém FKs físicas para `usuarios` e `questoes`; a V13 usa *cascade* na exclusão de usuário, o que reforça o acoplamento (ADR-006, D06) |
| 5 | Comunicação explícita | Sem chamadas a internals; usar API, eventos ou Façade | Adotado; reforçar com testes de arquitetura |
| 6 | Substituibilidade | Dependências externas atrás de portas (`LlmProvider`, `PaymentGateway`, `EmbeddingStore`), trocáveis por configuração | A implementar nos módulos `tutor-ia` e `assinatura` |
| 7 | Deploy independente | Preservar a possibilidade de build orientado a módulo (monorepo) | Fora do MVP; não introduzir acoplamentos que inviabilizem |
| 8 | Escalabilidade independente | Backend *stateless*; `tutor-ia` e `aquisicao` são os primeiros candidatos a execução isolada (custo e tráfego público) | Planejado, sem ação imediata |
| 9 | Monitoramento por módulo | Tag `module` em métricas Micrometer e no MDC dos logs; `correlationId` em toda resposta de erro; *health checks* por módulo | A implementar (RNF10, R20) |
| 10 | Falhas isoladas | *Timeouts*, *circuit breaker*, *bulkhead* e encerramento gracioso nas integrações; dashboard por projeção, não por chamadas em cascata | **Lacuna:** *rate limit* hoje em memória; migrar para Redis ao escalar (RNF03, R19); demais mecanismos em RNF05 |

## 6. Modelo de dados

**Implementado:** `Prova`, `Questao` (com disciplina classificada), `Alternativa`, `Disciplina`, `Usuario` (e-mail imutável, `ProvedorAuth`: LOCAL, GOOGLE, FACEBOOK, EMAIL_LINK), `Administrador`; schema de simulados (sessão, questões da sessão, respostas, resultado), comentário em alternativas (V8), congelamento de conteúdo (V9), `resposta_avulsa` (V12) e *cascade* de exclusão de usuário (V13).

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
| EstatisticaQuestao | questaoId, totalRespostas, totalAcertos, taxaAcerto, tempoMédio (só de avulsas), dificuldadeCalculada (nula abaixo do mínimo), atualizadoEm | questoes |
| Estado de acesso (campos em Usuario) | inícioTrial, fimTrial, origem do trial (automático ou concedido pelo admin); o estado é derivado, não gravado | usuarios |
| AdminAuditoria (`admin_auditoria`) | adminId, usuárioAlvo, ação, justificativa, criadoEm; sem conteúdo de estudo | administradores |
| Dispositivo / PreferenciaNotificacao | usuarioId, token de push, plataforma, últimoUso; tipos habilitados, horário permitido | notificacoes |
| RefreshToken | usuarioId, hash, dispositivo, expiraEm, revogadoEm | auth |

Regra de projeto: preferir resolver na camada de serviço a adicionar complexidade de schema (ex.: busca do número da prova em memória, sem coluna extra).

## 7. IA e RAG (módulo `tutor-ia`)

- **Orquestração:** langchain4j; provedor de LLM atrás de porta substituível (princípio 6).
- **Base de conhecimento:** pgvector com embeddings de comentários de questões (MVP 2, dependente de D04) e, a partir do MVP 5, de artigos de lei; no MVP 2 respostas sem fonte são sinalizadas e o aviso é reforçado (RN09, R02); pipeline de ingestão de legislação reaproveita o padrão de parsing usado nas provas.
- **Qualidade:** resposta ancorada em trechos recuperados; sem trecho relevante, a resposta é sinalizada como sem fonte (RN09); aviso permanente de limitação (RF04.6).
- **Rastreabilidade:** a resposta guarda as fontes usadas e linka o artigo (RF04.4).
- **Custo e abuso:** cotas por usuário PAGO, *rate limiting* e métrica de tokens por usuário (RNF13); TRIAL e GRATUITO não acessam o tutor.
- **Resiliência:** *timeout*, *circuit breaker* e mensagem de contingência (RNF05); falha do tutor não afeta simulados.
- **Entrega:** *streaming* por SSE, com cancelamento de geração.

## 8. Segurança

- Autenticação: e-mail e senha (BCrypt) e JWT de acesso com papel `USUARIO` ou `ADMIN` já implementados (RF01.2a). Refresh token rotativo, revogação e *rate limiting* completo na fase final (RF01.2b, ADR-003); até lá, ambiente restrito (R05).
- **Feature gate no servidor:** o estado de acesso (TRIAL, GRATUITO, PAGO) é derivado por `PoliticaAcessoService` e verificado em todo recurso restrito; clientes só exibem (RNF21, ADR-011).
- Endpoints do módulo `aquisicao` públicos, com limitação de taxa agressiva por origem e proteção anti-bot (captcha se necessário). O *rate limit* atual é em memória e não escala horizontalmente (R19).
- Erros seguem formato único com `correlationId`, a ser adicionado por filtro e no `ErroDTO` (R20; formato em `04`, seção 7).
- Webhooks do gateway validados por assinatura/segredo; nenhum dado de cartão no backend.
- Segredos fora do repositório; varredura de segredos no CI; nenhuma chave privilegiada nos clientes.
- Administradores isolados de usuários; cadastro de administrador apenas com perfil `dev` (pendente: `@Profile("dev")`); ações do painel auditadas em `admin_auditoria` e sem acesso a conteúdo do usuário (RN16, RN17).
- A exclusão de conta deve publicar `UsuarioExcluido` antes de qualquer exposição pública (R18).

## 9. Clientes

### 9.1 Frontend web (Angular, planejado)

- *Standalone components* e Signals; gerenciamento de estado leve (Signals/RxJS), sem NgRx salvo necessidade comprovada.
- Rotas espelhando os módulos: `/questoes`, `/simulados`, `/tutor`, `/legislacao`, `/planejamento`, `/ranking`, `/anotacoes`, `/desafio`, `/jogos`, `/planos`.
- Simulado: cronômetro no cliente sincronizado com o servidor, navegação sem recarga e *autosave* a cada resposta.
- Interceptor HTTP para JWT e *refresh* automático (quando RF01.2 existir); rota `/desafio` sem *guard* de autenticação.

### 9.2 App mobile (Kotlin, planejado)

Resumo; detalhes em `04`.

- Android nativo (Kotlin + Jetpack Compose), MVVM e banco local como fonte de verdade da interface (*offline-first* controlado); núcleo organizado para eventual extração via KMP (D16, ADR-012).
- O app nunca acessa o PostgreSQL: usa a API `/api/v1`, com cliente gerado do contrato OpenAPI (ADR-013).
- Operações offline entram em fila idempotente e são sincronizadas depois (ADR-014); push via FCM (ADR-015); sessão com refresh rotativo e armazenamento cifrado (ADR-016).

## 10. Integrações externas

| Integração | Uso | Situação |
|---|---|---|
| oab.fgv.br | Ingestão de provas e gabaritos | Implementada |
| Provedor de LLM | Tutor de IA | Dependência presente; módulo não construído |
| Gateway de pagamento | Assinatura (cartão recorrente, PIX/boleto) | A escolher (D05) |
| Serviço de jurisprudência | Enriquecimento do tutor | Candidato, não confirmado |
| E-mail transacional | Recuperação de senha (RF01.3) | Futuro (fase final) |
| Firebase Cloud Messaging | Push do app (RF15) | Confirmado; Mobile 2A |
| Google Play Console | Distribuição do app Android | Confirmado; Mobile 1 |

## 11. Registro de decisões arquiteturais (ADR)

| ID | Decisão | Status | Consequência principal |
|---|---|---|---|
| ADR-001 | Monólito modular (Spring Modulith) com banco único | Aceito | Simplicidade operacional; exige disciplina nas fronteiras |
| ADR-002 | Flyway como dono exclusivo do schema (`ddl-auto: validate`) | Aceito | Mudanças de schema sempre versionadas |
| ADR-003 | JWT de acesso implementado (RF01.2a); refresh token e revogação (RF01.2b) na fase final | Aceito (com risco R05) | Desenvolvimento sem bloqueio; ambiente não pode ser exposto antes da fase final |
| ADR-004 | Classificação de disciplinas determinística, sem IA | Aceito | Elimina erro de classificação por modelo |
| ADR-005 | pgvector no PostgreSQL para o RAG | Aceito | Sem banco vetorial separado no início |
| ADR-006 | Sem FK física entre tabelas de módulos diferentes; referência por ID e integridade via API/eventos | **Proposto** | Requer migração para remover FKs de `simulados` (V6). Alternativa: manter FKs como trade-off consciente de banco único, documentando o acoplamento |
| ADR-007 | `dashboard` como módulo *read-model* alimentado por eventos | **Proposto** | Evita chamadas em cascata e acoplamento entre cinco módulos |
| ADR-008 | Dificuldade calculada por desempenho real, em job assíncrono; fonte: `RespostaSimulado` e `resposta_avulsa`; só taxa de acerto no início; sem classificação abaixo do mínimo de respostas (RN06) | Aceito (redação revisada) | Depende de volume mínimo de respostas; estatística agregada, sem `usuarioId` |
| ADR-009 | Administradores em módulo separado, cadastro apenas em `dev`; ações do painel auditadas em `admin_auditoria` | Aceito | Isolamento de segurança e rastreabilidade (RN16) |
| ADR-010 | Eventos persistidos no registro de publicações do Spring Modulith | **Aceito** *(12/10/2026)* | Entrega garantida de eventos como `UsuarioExcluido` |
| ADR-011 | Estado de acesso (TRIAL, GRATUITO, PAGO) derivado no servidor por `PoliticaAcessoService`; trial reverso de 7 dias e plano gratuito de 1 simulado/dia (D11, D12) | **Proposto** *(redação nova)* | Um ponto único de enforcement, exposto como porta pública em `usuarios` e consultado pelos demais módulos (§4.1; RNF21); exige testes de fronteira de data (RNF17) |
| ADR-012 | Cliente mobile em Android nativo (Kotlin + Jetpack Compose), com núcleo organizado para extração via KMP (D15, D16) | **Proposto** *(redação nova)* | Melhor integração com a plataforma; iOS fica para depois |
| ADR-013 | Contrato OpenAPI versionado (`/api/v1`) como fonte do cliente Kotlin e verificação do Angular | **Proposto** *(redação nova)* | Evita divergência entre clientes; exige verificação do contrato no CI |
| ADR-014 | Mobile *offline-first* controlado: banco local, fila de operações com `operationId` e `Idempotency-Key`, conflito resolvido por tipo de operação | **Proposto** *(redação nova)* | Resposta de simulado idempotente no servidor; conclusão oficial só após confirmação da API |
| ADR-015 | Push via FCM atrás de porta (`PushProvider`), sem conteúdo sensível no payload (RN19) | **Proposto** *(redação nova)* | Provedor substituível; falha do push não afeta o estudo |
| ADR-016 | Sessão mobile com access token curto e refresh token rotativo; segredos em armazenamento cifrado do Android | **Proposto** *(redação nova)* | Depende de RF01.2b; os endpoints atuais com e-mail e senha não vão para produção |
| ADR-017 | Leituras que cruzam módulos usam porta pública do dono ou projeção alimentada por eventos (inclui `PoliticaAcessoService`, projeção do painel admin e módulo `sincronizacao`) | **Proposto** *(redação nova)* | Preserva o princípio 5 sem chamadas em cascata; exige contratos de porta documentados |
