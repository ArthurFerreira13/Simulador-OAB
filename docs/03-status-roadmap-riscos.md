# Status, Roadmap, Riscos e Decisões — Simulador-OAB

| Campo | Valor |
|---|---|
| **Documento** | 03 — Status, Roadmap, Riscos e Decisões Pendentes |
| **Versão** | 6.4.2 |
| **Data** | 12/10/2026 |
| **Responsável** | Arthur |
| **Relacionados** | `01-requisitos-simulador-oab.md` · `02-arquitetura-simulador-oab.md` · `04-arquitetura-mobile.md` |

> **Escopo:** documento vivo. Referencia IDs; não contém requisitos nem decisões de arquitetura.

---

## 1. Estado de implementação

| Módulo | Status | Detalhe |
|---|---|---|
| `questoes` | Implementado (leitura e resposta avulsa) | Consultas por exame, disciplina e nº de prova sem N+1; verificação de resposta; resposta avulsa (V12). Falta: paginação, `EstatisticaQuestao` e dificuldade (RN06) |
| `usuarios` | Implementado (parcial) | Cadastro, perfil, troca de senha (LOCAL), exclusão por cascata (V13). Falta: trial reverso (`app.trial.dias` configurado, sem uso), evento `UsuarioExcluido`, `PoliticaAcessoService` |
| `administradores` | Implementado (bootstrap) | Entidade separada; cadastro com chave, BCrypt, login `/api/admin/auth/login` com JWT `role=ADMIN`. Falta: cadastro fechado em homologação (ver §1.1), painel RF14, auditoria |
| `auth` | Implementado (acesso apenas) | JWT de acesso de 240 min (RF01.2a); *rate limit* em memória (login 5/min, cadastro 3/h por IP; desligado em dev). Falta: RF01.2b, `correlationId` no erro |
| `simulados` | Código na `dev`; validação pendente | Iniciar, sessão ativa, responder, revisão, abandonar, finalizar (idempotente) e resultado, com testes de service. Limite diário para todos os usuários (padrão 1/dia, dia em UTC, conta sessão abandonada). Compilação e testes a confirmar na homologação (R17) |
| Classificação de disciplinas | Implementado | V7: 1.520 questões / 20 disciplinas |
| `tutor-ia` | Não iniciado | langchain4j já é dependência |
| `notificacoes` | Não iniciado | Depende de FCM |
| `sincronizacao` *(proposto)* | Não iniciado | Mobile 2A; compõe portas dos módulos donos (Doc 02 §4.1) |
| `legislacao`, `planejamento`, `ranking`, `anotacoes`, `aquisicao`, `gamificacao`, `assinatura`, `dashboard` | Não iniciados | — |
| Frontend Angular | Não iniciado | Após maturidade da API |
| App mobile Kotlin | Não iniciado | D15; arquitetura em `04` |

**Verificação do código:** leitura da branch `dev` em 12/10/2026 (último commit `f565670`), sem build nem execução de testes. 127 arquivos Java, 15 de teste; `ApplicationModules.verify()` presente no `ApplicationTests`. Nenhum evento de domínio é publicado ainda (só o job de limpeza de publicações).

### 1.1 Pendências para a branch de homologação

| Item | O que fazer | Fecha |
|---|---|---|
| Cadastro de admin | Remover o valor padrão de `ADMIN_CADASTRO_CHAVE` (a aplicação deve falhar sem a variável) e aplicar `@Profile("dev")` ao endpoint `POST /api/admin/administradores` | R05 |
| Banco novo | Rodar o Flyway em banco vazio: a pasta de migrações começa na V2, sem a V1 (baseline) com as tabelas de provas e questões | RNF12 |
| Simulados | `mvnw test` limpo | R17 |
| Limite diário | Definir o fuso do "dia" (hoje UTC; reinicia às 21h em Brasília) e a regra de sessão abandonada (hoje conta qualquer sessão iniciada) | D13, RN14 |

**Migrações Flyway:** V1 baseline · V2 `event_publication` · V3 extensão pgvector · V4 `usuarios` · V5 `administradores` · V6 `simulados` e `disciplinas` · V7 classificação · V8 `comentario` em alternativas · V9 congelamento de conteúdo · V10 índices · V11 autovacuum · V12 `resposta_avulsa` · V13 cascade de exclusão de usuário.

**Qualidade da base de questões (12/09/2026):** 19 provas (29º–47º), 1.520 questões, 6.080 alternativas; 14 anuladas sem alternativa correta. Ponto de verificação: R07.

## 2. Roadmap

| MVP | Objetivo | Entregáveis | Critério de saída |
|---|---|---|---|
| 1 *(em andamento)* | Prática de questões e simulado | API de questões, conta, simulado, estado de acesso | CAs de RF01, RF02, RF03 e RF12.8–12.9; simulados compilando e testados (R17) |
| 2 | Tutor de IA (versão limitada: RAG só com comentários de questões; legislação entra no MVP 5) | Chat, histórico, cotas | CAs de RF04; custo por usuário medido; D04 decidida; respostas sem fonte sinalizadas (RN09) |
| 3 | Aquisição sem cadastro | Desafio público, funil, CTA | CAs de RF10; D01 e D02 decididas |
| 4 | Engajamento | Dashboard, ranking, anotações, favoritos, reinício | Retenção D7 medida |
| 5 | Legislação | Leis por artigo, busca, vínculo | Cobertura mínima de RF05.7 |
| 6 | Planejamento | Presets e checklist | Plano ativo utilizável |
| 7 | Monetização e admin | Planos, gateway, painel RF14 | Pagamento validado; admin auditado |
| 8 *(opcional)* | Gamificação | Mini-jogos e streak | Pode entrar após MVP 4 |
| Mobile 1 | Núcleo do app Kotlin | Login, questões, simulado, configurações | CAs de RF13.1–13.3, 13.5, 13.8, 13.10 |
| Mobile 2A | Offline e push base | Sincronização, offline controlado, push de simulado retomável, versão mínima | CAs de RF13.7, 13.9, 13.11 e RF15.1–15.5 (simulado retomável) |
| Mobile 2B | Favoritos e dashboard | Favoritos, revisão de erros, dashboard | CAs de RF13.4 e RF13.6 (dashboard) |
| Mobile 2C | Plano e lembretes | Plano de estudos, tarefas, lembrete de tarefa | CAs de RF13.6 (plano) e RF15.3 (lembrete) |
| Fase final | Segurança de acesso | Refresh/revogação, recuperação de senha | Revisão de segurança antes de exposição |
| Fora de escopo | 2ª fase e videoaulas | — | Exigem produção de conteúdo |

**Paralelismo:** Mobile 1 depende de MVP 1 estável. Mobile 2A depende de MVP 1 + Mobile 1; Mobile 2B depende do MVP 4; Mobile 2C depende do MVP 6. O alerta de streak (RF15.3) depende do MVP 8.

## 3. Decisões pendentes

| ID | Decisão | Opções / proposta | Impacto | Bloqueia |
|---|---|---|---|---|
| D01 | Tamanho do simulado cortesia anônimo | 80 (fidelidade) vs. 20–30 (menor fricção) | RF10.6 | MVP 3 |
| D02 | Rastreamento do limite anônimo | IP vs. cookie; LGPD | RF10.6, RNF09 | MVP 3 |
| D03 | Tratamento de anuladas na correção | Acerto para todos (proposta) vs. branco | RN04, RF03.4 | MVP 1 |
| D04 | Origem dos comentários | Curadoria, IA, ou combinação | RF02.3 | MVP 2 |
| D05 | Gateway de pagamento | Mercado Pago, Stripe ou similar | RF12 | MVP 7 |
| D06 | FKs físicas entre módulos | ADR-006 ou manter; decidir junto com *locations* do Flyway por módulo | Arquitetura | Antes do MVP 2 (o `tutor-ia` já cria tabelas com `usuarioId`) |
| D08 | Limites do tutor no trial e no plano pago | Mensagens/dia e teto | RN10, RNF13 | MVP 2 |
| D09 | Cadernos (tipo/cor) | Confirmar 1 caderno por exame | RF02.9, RF07 | MVP 4 |
| D10 | Direitos de uso do conteúdo da banca | Assessoria jurídica | Viabilidade | MVP 7 |
| D13 | Sessão abandonada conta no limite? | Conta, exceto sem resposta (proposta) | RN14 | MVP 1 |
| D14 | Antiabuso do trial | Verificação de e-mail + hash | RN13, RF12.12 | MVP 7 |
| D16 | Alcance mobile (Android nativo vs. KMP desde o início) | Android nativo com núcleo KMP; iOS depois | Arquitetura | Mobile 1 |
| D17 | Versão mínima de Android e política de atualização | Definir com base no público | Operação | Mobile 1 |
| D18 | Cobrança dentro do app | Faturamento da loja vs. fora | RF12 mobile | Mobile 2 / MVP 7 |
| D24 | Papéis no admin | **Decidida: apenas `ADMIN`** | RF14 | MVP 7 |
| D25 | Efeito do cancelamento pelo admin | Efeito imediato, com log | RF14.3 | MVP 7 |
| D26 | Conceder trial manualmente | Sim, com justificativa e log | RF14.4 | MVP 7 |
| D27 | Excluir usuário pelo admin | Sim, com dupla confirmação | RF14.5 | MVP 7 |
| D28 | Log de auditoria | Tabela `admin_auditoria` | RF14.6, RN16 | MVP 7 |
| D29 | Admin vê conteúdo do usuário? | Não; apenas contagens e estado | RF14 | MVP 7 |

### 3.1 Decisões tomadas

| ID | Decisão | Data | Reflexo |
|---|---|---|---|
| D07 | **Estrutura de planos:** Mensal R$ 9,99 · Trimestral R$ 26,97 · Semestral R$ 47,94 · Anual R$ 79,99. Ladder 10% → 20% → 33% | 12/10/2026 | RF12.1, RF12.2, A.2 do Doc 01 |
| D11 | Trial reverso: 7 dias + 1 simulado/dia no gratuito | 11/10/2026 | RF12.8–12.12, RN13–RN15, RNF17, ADR-011 |
| D12 | Plano gratuito = 1 simulado/dia + básico; avulsas/tutor/plano/ranking/anotações indisponíveis | 12/10/2026 | RF12.9, RN14 |
| D15 | Cliente mobile em Kotlin | 12/10/2026 | Doc 01 §1.2/§1.4; RF13; RNF18–RNF20; Doc 04 |

## 4. Riscos

| ID | Risco | Prob. | Imp. | Mitigação |
|---|---|---|---|---|
| R01 | Custo de LLM com uso livre do tutor | A | A | Cotas (D08), métrica de tokens, teto |
| R02 | Alucinação jurídica | M | A | RAG com fonte, sinalização, aviso (RF04.6, RN09); MVP 2 limitado a comentários de questões até o MVP 5 |
| R03 | Abuso do módulo público | A | M | Rate limiting, captcha, desafio sem LLM |
| R04 | Complexidade de pagamentos | M | A | Gateway, webhooks, sandbox |
| R05 | API sem JWT completo no desenvolvimento | A | A | Ambiente restrito, sem exposição (ADR-003) |
| R06 | Direitos sobre questões | M | A | Validação jurídica antes da monetização |
| R07 | Duplicidade por variantes de caderno | B | M | Confirmar em D09 |
| R08 | Dependência de LLM único | M | M | Porta `LlmProvider` |
| R09 | Volume de conteúdo inferior ao concorrente | A | M | Comentários, simulados preparatórios, 48º Exame |
| R10 | Privacidade em ranking | M | M | Opt-in, apelido, minimização |
| R11 | Comentários IA sem revisão | M | M | Sinalizar origem, revisar mais acessadas |
| R12 | Abuso do trial | A | M | Verificação de e-mail (D14) |
| R13 | Custo de LLM no trial | M | A | Cotas (D08), monitoramento |
| R14 | Comentários possivelmente trocados | M | M | Conteúdo imutável, reportar, correção auditada |
| R15 *(proposto)* | Política da loja sobre cobrança dentro do app pode exigir faturamento da loja ou limitar o CTA de pagamento | M | M | Decidir D18 antes do Mobile 2; manter assinatura fora do app até lá |
| R16 | Ação administrativa sem rastro | M | M | Auditoria obrigatória (RN16) |
| R17 | Atraso na validação do módulo `simulados` bloqueia MVP 1 e Mobile 1 | M | A | Priorizar compilação/testes na homologação (§1.1); fallback: escopo reduzido |
| R18 | `excluirConta` sem `UsuarioExcluido` viola LGPD | A | A | Publicar evento antes da publicação pública (ADR-010, aceita em 12/10/2026, garante a entrega). Hoje a exclusão cobre os módulos atuais por cascata (V13); o evento é necessário para os módulos futuros |
| R19 | Rate limit em memória não escala horizontalmente | M | M | Migrar para Redis ao escalar (RNF03) |
| R20 | `correlationId` ausente no formato de erro | A | M | Adicionar filtro e campo no `ErroDTO` (Doc 04 §7) |

## 5. Decisão de exclusão e agregados

A exclusão de conta remove os dados vinculados ao usuário em todos os módulos por meio do evento `UsuarioExcluido`. `EstatisticaQuestao` permanece porque armazena apenas agregados por questão, sem `usuarioId`. O registro mínimo de auditoria administrativa (RN16) também é preservado.

## 6. Backlog de evolução — módulos e funcionalidades futuras

### 6.1 Módulos nomeados ainda não iniciados

| Módulo | Previsto em | MVP alvo | Gatilho |
|---|---|---|---|
| `tutor-ia` | Doc 01 RF04 | MVP 2 | MVP 1 estável; D08 decidida |
| `legislacao` | Doc 01 RF05 | MVP 5 | Tutor em produção exigindo RAG |
| `planejamento` | Doc 01 RF06 | MVP 6 | Retenção D7 indicando necessidade |
| `ranking` | Doc 01 RF07 | MVP 4 | Base de respostas suficiente (RN07) |
| `anotacoes` | Doc 01 RF08 | MVP 4 | Engajamento inicial validado |
| `aquisicao` | Doc 01 RF10 | MVP 3 | D01/D02 resolvidas |
| `gamificacao` | Doc 01 RF11 | MVP 8 | Retenção estagnada após MVP 4 |
| `assinatura` | Doc 01 RF12 | MVP 7 | D05 decidida |
| `dashboard` | Doc 01 RF00 | MVP 4 | Dados suficientes |
| `notificacoes` | Doc 01 RF15 | Mobile 2A | FCM configurado; Mobile 1 estável |
| `sincronizacao` | Doc 02 §4.1 | Mobile 2A | Mobile 1 estável; portas de delta dos módulos donos |
| Frontend Angular | Doc 02 §9 | Após maturidade | API estável |
| App mobile Kotlin | Doc 04 | Mobile 1–2 | MVP 1 estável |

### 6.2 Fora de escopo (versão atual)

| Item | Requisito | Gatilho para reavaliação |
|---|---|---|
| 2ª fase do Exame | — | Demanda; capacidade editorial |
| Videoaulas | — | Parceria; orçamento |
| Comunidade e fóruns | — | Base que justifique moderação |
| Anotação por timestamp de videoaula | RF08.4 (W) | Módulo de vídeo em desenvolvimento |
| Edição manual de questões | RF09.3 (W) | Revisão jurídica |
| Ingestão automática de PDF | RF09.1 | Ferramenta interna validada |

### 6.3 Funcionalidades condicionadas ou propostas

| Item | Requisito / RN | Dependência | Gatilho |
|---|---|---|---|
| Súmulas como aba | RF05.5 | Pós-MVP 5 | Ingestão + demanda |
| Subtópico por questão | RF02.8 | Pós-MVP 5 | Classificação disponível |
| Dificuldade calculada | RF02.9 / RN06 | Pós-MVP 4 | Volume mínimo (30 respostas) |
| Geração dinâmica de cronograma | RF06.2 | Pós-MVP 6 | Presets validados |
| Pagamento em dois cartões | RF12.7 | Pós-MVP 7 | Demanda |
| WCAG 2.1 AA | RNF15 | Auditoria | Público-alvo |
| Modo offline expandido | RF13.9 | Mobile 2A | Fila de sync estável |
| iOS via KMP | D16 | D16 decidida | Demanda iOS |

### 6.4 Decisões que podem criar escopo

| ID | Pode gerar | Gatilho |
|---|---|---|
| D04 | Pipeline de curadoria ou geração | Custo vs. qualidade medidos |
| D05 | Integração de gateway | Comparativo de taxas |
| D08 | Política de cotas do tutor | Custo por usuário no MVP 2 |
| D14 | Verificação de e-mail + hash | R12 confirmado em produção |
| D16 | Estrutura KMP | Demanda iOS |
| D17 | Tela de update forçado | Primeira mudança incompatível |
| D18 | Faturamento Google Play | MVP 7 em preparação |
| D07.1 (proposta) | Desconto por indicação | Pós-MVP 7 |
| D07.2 (proposta) | Preço regionalizado no PIX | Pós-MVP 7 |
| D07.3 (proposta) | Revisão de preço após 90 dias | Pós-MVP 7 |

### 6.5 Candidatos externos

| Item | Onde | Status | Gatilho |
|---|---|---|---|
| E-mail transacional | Doc 01 §5 | Futuro | Fase final (RF01.3) |
| Jurisprudência | Doc 01 §5 | Candidato | RAG insuficiente |
| Firebase Cloud Messaging | Doc 01 §5 | Confirmado | Mobile 2A |
| Google Play Console | Doc 04 §9 | Confirmado | Mobile 1 |
| Provedor alternativo de LLM | R08 | Candidato | Instabilidade/custo |

### 6.6 Backlog pós-MVP 8

| Item | Justificativa | Gatilho |
|---|---|---|
| 2ª fase com correção por IA | Diferencial | Base ativa na 1ª fase |
| Videoaulas curtas | Permanência e preço | Parceria; orçamento |
| iOS via KMP | Cobertura | D16; demanda |
| Curadoria editorial | Qualidade percebida; mitigar R11 | Volume mapeado |
| Jurisprudência integrada | Confiança do RAG; mitigar R02 | Bases públicas |
| Certificados e relatórios exportáveis | Valor percebido | Demanda em suporte |
| Integração com calendário | Retenção via hábito | Mobile 2 estável |
| Modo escuro / temas | Conforto; baixo custo | Design system consolidado |
| Offline expandido (leis) | Estudo sem conexão | RF13.9 validado |
| Trilha adaptativa com IA | Personalização | Dados (RN06) |
| Parcerias com faculdades | Canal de aquisição | Produto estável |
| Plano integrado para 2ª fase | Consistência | 2ª fase em escopo |

### 6.7 Roadmap consolidado *(proposto; seção reconstruída, a validar)*

Visão por trilha, na ordem de dependência. As datas ficam fora de propósito: dependem da conclusão do MVP 1 (R17).

| Trilha | Sequência | Depende de | Porta de saída |
|---|---|---|---|
| API e web | MVP 1 → 2 → 3 → 4 → 5 → 6 → 7 → (8) | Anterior estável; decisões do bloco indicado em §3 | CAs do marco; D01/D02 antes do MVP 3, D05/D10/D14 antes do MVP 7 |
| Mobile | Mobile 1 → 2A; 2B após MVP 4; 2C após MVP 6 | MVP 1 estável (Mobile 1); MVP 1 + Mobile 1 (2A); MVP 4 (2B); MVP 6 (2C) | CAs de RF13/RF15; D16/D17 antes do Mobile 1, D18 antes do Mobile 2 |
| Segurança | Fase final | Funcionalidade do MVP 1 em diante | Revisão de segurança concluída (RF01.2b, RF01.3) |

**Pontos de atenção:**

1. Qualquer exposição pública (web, app em produção ou testes abertos) exige a Fase final concluída (R05). Testes internos do app só em ambiente restrito.
2. R17 trava MVP 1 e Mobile 1; o app só começa depois que `simulados` estiver compilando e testado.
3. R18 (`UsuarioExcluido`) precisa ser resolvido antes de qualquer dado real entrar no sistema.
4. D18 e R15 podem exigir mudar o desenho do MVP 7 se o app oferecer assinatura.
5. Como a Fase final fica por último, avaliar antecipar RF01.2b caso o Mobile 2 chegue a usuários reais antes do MVP 7.
6. ADR-010 foi aceita: eventos persistidos garantem a entrega do `UsuarioExcluido` (R18).
7. D06 e as *locations* do Flyway por módulo precisam ser decididas antes do MVP 2, porque o `tutor-ia` cria as primeiras tabelas novas com referência a usuário.
8. O limite diário e o gate seguem a porta pública de acesso em `usuarios` (ADR-011, ADR-017); nenhum módulo reimplementa a regra.
9. O streak sai do dashboard do MVP 4 e passa a depender do MVP 8 (RF00.6). Se quiser o streak antes, a alternativa é mover só o RF11.4 para o MVP 4.
10. O MVP 2 é uma versão limitada do tutor: o RAG só tem comentários de questões e a D04 precisa estar decidida; a legislação como fonte chega no MVP 5.

## 7. Arquivos relacionados e descarte *(mantido da v6.0)*

- `diferenciacao-competitiva-oab-prep.md` — análise competitiva (continua válida; revisar para garantir que não contenha dados pessoais de terceiros).
- `Inspeção do site de referência — ORDEM AÍ.md` — **mover para fora do repositório ou excluir**, pois contém material bruto de inspeção do concorrente.
- Arquivos substituídos e removíveis: todas as versões anteriores de `analise-requisitos-plataforma-oab*.md`, incluindo `-v4` e `-v5`.
