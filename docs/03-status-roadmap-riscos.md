# Status, Roadmap, Riscos e Decisões — Simulador-OAB

| Campo | Valor |
|---|---|
| **Documento** | 03 — Status, Roadmap, Riscos e Decisões Pendentes |
| **Versão** | 6.0 |
| **Data** | 09/10/2026 |
| **Responsável** | Arthur |
| **Posição do código** | Última verificação em 15/09/2026; **reconfirmar antes de usar como base de planejamento** |
| **Relacionados** | `01-requisitos-simulador-oab.md` · `02-arquitetura-simulador-oab.md` |

> **Escopo:** documento vivo, atualizado a cada marco. Não contém requisitos nem decisões de arquitetura; apenas referencia seus IDs.

---

## 1. Estado de implementação

| Módulo | Status | Detalhe |
|---|---|---|
| `questoes` | Implementado (leitura) | Entidades somente leitura; consultas sem N+1; busca de nº de prova em memória; serviço de consulta, verificação de resposta e gabarito. **Falta:** camada web (controller/DTO) |
| `usuarios` | Implementado | Cadastro, consulta/atualização de perfil e exclusão de conta; e-mail imutável; troca de senha restrita a contas locais. **Falta:** campos de meta de prova (a confirmar no modelo) |
| `administradores` | Implementado | Entidade separada; cadastro apenas com perfil `dev` |
| `simulados` | Schema pronto; código em validação | Migração V6 aplicada; classes Java escritas, ainda não confirmadas como compiladas e testadas |
| Classificação de disciplinas | Implementado | V7: 1.520 questões em 20 disciplinas por faixas do edital |
| Autenticação JWT | Adiada deliberadamente | Ver ADR-003 e risco R05 |
| `tutor-ia` | Não iniciado | langchain4j já é dependência |
| `legislacao`, `planejamento`, `ranking`, `anotacoes`, `aquisicao`, `gamificacao`, `assinatura`, `dashboard` | Não iniciados | — |
| Frontend Angular | Não iniciado | Planejado após maturidade da API |

**Migrações Flyway:** V1 baseline restaurada · V2 ajustes · V3 extensão pgvector · V4 `usuarios` · V5 `administradores` · V6 `simulados` e `disciplinas` · V7 classificação de disciplina das 1.520 questões.

**Qualidade da base de questões (validada em 12/09/2026):** 19 provas (39º ao 47º Exame de Ordem), todas com 80 questões; 1.520 questões, todas com 4 alternativas; 6.080 alternativas; zero registros órfãos e zero campos vazios. As 14 questões sem alternativa correta correspondem exatamente às 14 anuladas. Ponto de verificação: ver R07.

## 2. Roadmap

| MVP | Objetivo | Entregáveis | Critério de saída |
|---|---|---|---|
| **1** *(em andamento)* | Prática de questões e simulado completo | API de questões, conta, simulado com correção e desempenho por disciplina | CAs de RF01, RF02, RF03 aprovados; simulados compilando e testados |
| **2** | Tutor de IA acoplado à questão (maior diferencial) | Chat contextual e livre, histórico, aviso e cotas | CAs de RF04 aprovados; custo por usuário medido |
| **3** | Aquisição sem cadastro | Desafio público, funil, CTA, limitação de taxa | CAs de RF10 aprovados; D01 e D02 decididos |
| **4** | Engajamento | Dashboard, ranking, anotações, favoritos, reinício de estatísticas | Retenção D7 medida |
| **5** | Legislação como fonte do RAG | Leis por artigo, busca, vínculo com questões, rastreabilidade do tutor | Cobertura mínima de RF05.7 ingerida |
| **6** | Planejamento de estudos | Presets e checklist; geração dinâmica depois | Plano ativo utilizável ponta a ponta |
| **7** | Monetização | Planos, gateway, trial, cancelamento | Fluxo de pagamento validado em ambiente de teste, incluindo webhooks |
| **8** *(opcional)* | Gamificação | Mini-jogos e streak | Pode entrar após o MVP 4 |
| **Fase final** | Segurança de acesso | JWT, refresh token, recuperação de senha, *rate limiting* | Revisão de segurança concluída **antes de qualquer exposição pública** |
| **Fora de escopo** | 2ª fase e videoaulas | — | Exigem produção de conteúdo |

## 3. Decisões pendentes

| ID | Decisão | Opções / proposta | Impacto | Bloqueia |
|---|---|---|---|---|
| **D01** | Tamanho do simulado cortesia anônimo | 80 questões (fidelidade) vs. 20–30 (menor fricção) | RF10.6 | MVP 3 |
| **D02** | Rastreamento do limite diário anônimo | IP (simples, falsos positivos em NAT) vs. cookie (preciso, burlável); avaliar combinação com retenção mínima e base legal (LGPD) | RF10.6, RNF09 | MVP 3 |
| **D03** | Tratamento de questões anuladas na correção | Proposta: contar como acerto para todos e sinalizar | RN04, RF03.4 | MVP 1 |
| **D04** | Origem dos comentários das questões | Curadoria própria, geração por IA com sinalização, ou combinação; custo e revisão | RF02.3 | MVP 2 |
| **D05** | Gateway de pagamento | Mercado Pago, Stripe ou similar; exigência de cartão recorrente e PIX/boleto | RF12 | MVP 7 |
| **D06** | FKs físicas entre módulos | Aceitar ADR-006 (remover FKs de `simulados`) ou manter como trade-off documentado | Arquitetura | Antes do MVP 4 |
| **D07** | Estrutura de planos | Proposta: plano único + anual com desconto, em vez de três níveis | RF12.1 | MVP 7 |
| **D08** | Limites do tutor no plano gratuito | Mensagens/dia e teto de custo por usuário | RN10, RNF13 | MVP 2 |
| **D09** | Variantes de caderno | Verificar se as 19 provas incluem variantes do mesmo exame e deduplicar questões equivalentes antes de estatísticas e ranking | RF02.9, RF07 | MVP 4 |
| **D10** | Direitos de uso do conteúdo da banca | Validar termos de uso de oab.fgv.br e uso comercial das questões; consultar assessoria jurídica | Viabilidade comercial | MVP 7 |

## 4. Riscos

Classificação: probabilidade e impacto em **A**lto, **M**édio ou **B**aixo.

| ID | Risco | Prob. | Imp. | Mitigação |
|---|---|---|---|---|
| **R01** | Custo de LLM cresce com uso livre do tutor | A | A | Cotas por plano (D08), métrica de tokens por usuário, teto de custo |
| **R02** | Respostas jurídicas incorretas (alucinação) com dano reputacional | M | A | Base de legislação estruturada por artigo, RAG com fonte, sinalização de ausência de fonte, aviso permanente (RF04.6, RN09) |
| **R03** | Abuso do módulo público de aquisição (bots consumindo API/LLM) | A | M | *Rate limiting* por origem, captcha se necessário, desafio sem uso de LLM em tempo real |
| **R04** | Complexidade de pagamentos (estados assíncronos, conformidade) | M | A | Delegar cartão ao gateway, validar webhooks, testes em sandbox, não subestimar esforço do MVP 7 |
| **R05** | API sem JWT durante o desenvolvimento | A | A | Ambiente restrito, sem dados reais e sem exposição pública até a conclusão da fase final (ADR-003) |
| **R06** | Direitos sobre questões e termos de uso da fonte (D10) | M | A | Validação jurídica antes da monetização; manter ativos próprios |
| **R07** | Duplicidade de questões por variantes de caderno distorce estatísticas | M | M | Verificação e deduplicação (D09) |
| **R08** | Dependência de provedor único de LLM | M | M | Porta `LlmProvider` substituível; avaliar fallback |
| **R09** | Volume de conteúdo inferior ao do concorrente | A | M | Priorizar ingestão e comunicar posicionamento com transparência |
| **R10** | Privacidade: rastreamento anônimo e exposição em ranking | M | M | Opt-in no ranking, exibição por apelido, minimização e retenção definidas (RNF09) |
| **R11** | Qualidade dos comentários gerados por IA sem revisão | M | M | Sinalizar origem (RF02.3) e priorizar revisão das questões mais acessadas |

## 5. Histórico de revisões

| Versão | Data | Alteração |
|---|---|---|
| 1.0–3.0 | — | Versões iniciais da análise de requisitos |
| 4.0 | 15/09/2026 | Consolidação em documento único com estado real de implementação |
| 5.0 | 09/10/2026 | Remoção de dados de terceiros e de material sensível da inspeção do concorrente |
| 6.0 | 09/10/2026 | Reestruturação em três documentos (requisitos, arquitetura, status); prioridades MoSCoW, critérios de aceite e RNFs mensuráveis; revisão pelos princípios de arquitetura modular; novos riscos e decisões (D03, D04, D09, D10) |

## 6. Documentos relacionados

- `01-requisitos-simulador-oab.md` e `02-arquitetura-simulador-oab.md` (esta revisão).
- `diferenciacao-competitiva-oab-prep.md` — análise competitiva (continua válido; revisar para garantir que não contenha dados pessoais de terceiros).
- `Inspeção do site de referência — ORDEM AÍ.md` — **mover para fora do repositório ou excluir**, pois contém material bruto de inspeção do concorrente.
- Arquivos substituídos e removíveis: todas as versões anteriores de `analise-requisitos-plataforma-oab*.md`, incluindo `-v4` e `-v5`.
