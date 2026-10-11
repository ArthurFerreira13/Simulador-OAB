# Especificação de Requisitos de Software — Simulador-OAB

| Campo | Valor |
|---|---|
| **Produto** | Simulador-OAB (nome de trabalho) — plataforma de preparação para a 1ª fase do Exame de Ordem |
| **Documento** | 01 — Especificação de Requisitos (ERS) |
| **Versão** | 6.0 |
| **Data** | 09/10/2026 |
| **Responsável** | Arthur |
| **Status** | Em revisão |
| **Documentos relacionados** | `02-arquitetura-simulador-oab.md` (arquitetura e ADRs) · `03-status-roadmap-riscos.md` (status, roadmap, riscos e decisões pendentes) |

> **Escopo deste documento:** define **o quê** o produto deve fazer e **com que qualidade**. Decisões de implementação estão em `02`; estado de andamento, riscos e decisões em aberto estão em `03`. Este documento consolida e substitui todas as versões anteriores da análise de requisitos (v1 a v5).

> **Propriedade intelectual:** o produto é inspirado em padrões de experiência observados no mercado, mas possui marca, textos, ilustrações, banco de questões, identidade verbal e ativos próprios. Não se copia código, nome, logotipo nem conteúdo proprietário de terceiros. Dados pessoais de terceiros não integram nenhum artefato do projeto.

---

## 1. Introdução

### 1.1 Propósito

Especificar os requisitos funcionais, regras de negócio e requisitos não funcionais do Simulador-OAB, servindo de referência única para desenvolvimento, testes e validação com o responsável pelo produto.

### 1.2 Visão do produto

Plataforma web de preparação para a 1ª fase do Exame de Ordem (OAB/FGV), com banco de questões oficiais, simulados no formato real da prova, correção automática com estatísticas de desempenho e um tutor de IA (RAG) que explica erros e responde dúvidas ancoradas em legislação. O produto evolui a base existente (`agente-rag`: Spring Modulith + langchain4j) para uma plataforma completa com frontend próprio.

### 1.3 Objetivos e indicadores de sucesso

| Objetivo | Indicador | Meta inicial (hipótese a calibrar) |
|---|---|---|
| Permitir prática massiva no formato real da prova | Simulados concluídos por usuário ativo/mês | ≥ 1 |
| Converter visitantes em usuários | Conversão do Desafio de Aquisição → cadastro | A definir após primeiras 500 sessões |
| Entregar valor pedagógico | Usuários que usam o Tutor após errar uma questão | A definir após MVP 2 |
| Reter usuários | Retenção D7 / D30 | A definir após MVP 4 |
| Manter viabilidade econômica | Custo de LLM por usuário ativo/mês | Teto definido antes do MVP 2 (ver decisão D08 em `03`) |

### 1.4 Escopo

Banco de questões; simulados cronometrados; tutor de IA; legislação; planejamento de estudos; ranking; anotações; desafio de aquisição sem cadastro; gamificação; assinatura e monetização; dashboard; administração de conteúdo.

### 1.5 Fora de escopo (versão atual)

- 2ª fase do Exame (peças e questões discursivas, correção por IA).
- Videoaulas e produção de conteúdo audiovisual.
- Comunidade, fóruns e qualquer funcionalidade que exija moderação.
- Aplicativo nativo (o frontend é web responsivo/PWA).

### 1.6 Personas

| Persona | Descrição | Necessidade principal |
|---|---|---|
| **Candidato** | Estuda para a OAB | Prática massiva de questões e feedback rápido |
| **Visitante** | Chegou por anúncio ou indicação e ainda não tem conta | Valor imediato antes de decidir cadastrar |
| **Administrador de conteúdo** | Cura provas, disciplinas, legislação e correções (inicialmente o próprio responsável pelo produto) | Manter a base confiável e atualizada |
| **Professor/parceiro** *(futuro)* | Produz comentários e videoaulas | Publicar conteúdo autoral |

### 1.7 Glossário

| Termo | Definição |
|---|---|
| **Exame de Ordem / OAB / FGV** | Exame nacional de ingresso na advocacia, organizado pela banca FGV |
| **1ª fase** | Prova objetiva de 80 questões de múltipla escolha, 5 horas de duração |
| **Simulado** | Sessão de prova cronometrada composta por questões do banco |
| **Gabarito** | Alternativa correta de cada questão |
| **Questão anulada** | Questão invalidada pela banca; não possui alternativa correta marcada |
| **Disciplina / Subtópico** | Matéria do edital / recorte específico dentro da disciplina |
| **Lei seca** | Texto legal sem comentários, organizado por artigo |
| **RAG** | *Retrieval-Augmented Generation*: resposta gerada por LLM com base em trechos recuperados de uma base de conhecimento |
| **Tutor** | Assistente de IA do produto, baseado em RAG |
| **Streak** | Sequência de dias consecutivos com atividade de estudo |
| **Freemium / Trial** | Modelo com camada gratuita e planos pagos / período de teste gratuito |
| **MVP** | Incremento mínimo entregável do produto (ver roadmap em `03`) |

### 1.8 Convenções

- **Identificadores:** `RFnn.m` (requisito funcional), `RNnn` (regra de negócio), `RNFnn` (requisito não funcional), `CA-…` (critério de aceite), `Dnn` (decisão pendente, em `03`).
- **Prioridade (MoSCoW):** **M**ust (obrigatório para o MVP indicado) · **S**hould (importante, não bloqueante) · **C**ould (desejável) · **W**on't (fora de escopo agora).
- **Alocação de MVP:** os itens de Dashboard (RF00) e de conta (RF01.3 a RF01.6) não constavam no planejamento anterior; sua alocação nesta versão é **proposta** e depende de validação.
- **Critérios de aceite:** detalhados para os MVPs 1 a 3 (formato Dado/Quando/Então). Os demais serão detalhados antes do início do respectivo MVP.

---

## 2. Requisitos Funcionais

### RF00 — Dashboard

| ID | Requisito | Prior. | MVP |
|---|---|---|---|
| RF00.1 | Exibir contagem regressiva até a data da prova-alvo do usuário | S | 4 |
| RF00.2 | Bloco "Foco de agora": até N disciplinas sugeridas (plano ativo ou desempenho recente), com atalho para praticar e acesso ao plano completo | S | 4 |
| RF00.3 | Métricas agregadas: questões resolvidas, taxa de acerto média, horas de estudo no mês e cobertura (disciplinas praticadas / total) | S | 4 |
| RF00.4 | Mensagem motivacional rotativa e atualizável | C | 4 |
| RF00.5 | Acesso rápido às ações mais frequentes (praticar questões, iniciar simulado, perguntar ao tutor) | S | 4 |
| RF00.6 | Indicador de sequência de estudo (streak) com calendário semanal e recorde histórico, a partir do dado de RF11.4 | C | 4 |
| RF00.7 | Painel "Desempenho por matéria", com status "não praticado" quando não houver dados suficientes | S | 4 |
| RF00.8 | "Atividade recente": últimas interações com resultado resumido e tempo relativo | C | 4 |

### RF01 — Autenticação e Conta

| ID | Requisito | Prior. | MVP |
|---|---|---|---|
| RF01.1 | Cadastro por e-mail e senha; suporte a provedores externos (Google, Facebook, link por e-mail) | M | 1 |
| RF01.2 | Login/logout com JWT (token de acesso + refresh token) | M | Fase final |
| RF01.3 | Recuperação de senha por e-mail | M | Fase final |
| RF01.4 | Perfil do usuário: nome, data da prova-alvo, disciplinas de maior dificuldade | S | 1 |
| RF01.5 | Área de configurações com abas: perfil, assinatura, preferências (incluindo privacidade), notificações e suporte | S | 7 |
| RF01.6 | Encerramento de conta com duas opções: **reiniciar estatísticas** (preserva conta, assinatura, anotações e favoritos) e **exclusão permanente** de dados (LGPD) | M | 1 (exclusão) · 4 (reinício) |

**Critérios de aceite — MVP 1**

- **CA-RF01.1-01** — *Dado* um e-mail inédito, *quando* o visitante envia cadastro válido, *então* a conta é criada, a senha é armazenada apenas como hash e o perfil pode ser consultado.
- **CA-RF01.1-02** — *Dado* um e-mail já cadastrado, *quando* há nova tentativa de cadastro, *então* o sistema retorna erro de conflito sem expor dados da conta existente.
- **CA-RF01.1-03** — *Dado* uma conta existente, *quando* se tenta alterar o e-mail, *então* a alteração é recusada (o e-mail é imutável); a troca de senha só é permitida para contas de provedor local.
- **CA-RF01.6-01** — *Dado* um usuário que confirma a exclusão permanente, *quando* a operação conclui, *então* seus dados pessoais e de estudo deixam de existir em todos os módulos, e a remoção é propagada por evento (ver `02`, seção 4).

### RF02 — Banco de Questões

| ID | Requisito | Prior. | MVP |
|---|---|---|---|
| RF02.1 | Listar questões com filtros combináveis: exame/ano, disciplina, banca, dificuldade e situação do usuário (respondida, errada, não respondida) | M | 1 |
| RF02.2 | Responder questão avulsa com feedback imediato (correta/incorreta) e gabarito | M | 1 |
| RF02.3 | Exibir o comentário da questão, indicando sua origem (curadoria ou IA); se não houver, oferecer "Explicar com IA" | M | 2 |
| RF02.4 | Favoritar questão e marcar para revisão | S | 4 |
| RF02.5 | Gerar lista de questões por filtro para revisão dirigida | C | 4 |
| RF02.6 | Modo estudo com curtir, compartilhar, reportar erro e pular questão | C | 4 |
| RF02.7 | Vincular a questão a artigos de legislação relacionados | S | 5 |
| RF02.8 | Registrar subtópico por questão (campo próprio, para filtro e estatística) e exibi-lo no cabeçalho junto com exame, matéria e dificuldade | C | Pós-5 |
| RF02.9 | **Dificuldade calculada por desempenho real** dos usuários (ver RN06), usada como base do filtro de dificuldade de RF02.1 | S | Pós-4 |

**Critérios de aceite — MVP 1**

- **CA-RF02.1-01** — *Dado* o banco de questões, *quando* o usuário combina filtros (por exemplo, exame e disciplina), *então* o resultado respeita todos os filtros simultaneamente (operador E) e é paginado.
- **CA-RF02.1-02** — *Dado* o filtro por disciplina, *quando* aplicado, *então* a classificação utilizada é a do edital (RN05).
- **CA-RF02.2-01** — *Dado* uma questão e uma alternativa escolhida, *quando* o usuário responde, *então* recebe imediatamente o resultado e a alternativa correta.
- **CA-RF02.2-02** — *Dado* uma questão anulada, *quando* respondida, *então* o sistema sinaliza a anulação e aplica a regra RN04.

### RF03 — Simulados

| ID | Requisito | Prior. | MVP |
|---|---|---|---|
| RF03.1 | Iniciar simulado completo (80 questões, formato oficial) ou personalizado (N questões, por disciplina ou mix) | M | 1 |
| RF03.2 | Cronômetro do simulado (referência: 5 h), com pausa; estados da sessão: em andamento, abandonado, concluído | M | 1 |
| RF03.3 | Navegação livre entre questões e marcação para revisão antes de finalizar | M | 1 |
| RF03.4 | Correção automática ao finalizar: acertos, erros, em branco, percentual e situação (aprovado/reprovado) pela nota de corte (RN03) | M | 1 |
| RF03.5 | Desempenho detalhado por disciplina, com gráfico de evolução histórica | M | 1 |
| RF03.6 | Retomar simulado abandonado | M | 1 |
| RF03.7 | Listagem de simulados com estatísticas agregadas: realizados, média de acertos e tempo médio | S | 1 |
| RF03.8 | Durante o simulado, a barra de ações da questão é reduzida: manter compartilhar, anotar, estatísticas e reportar; **remover "pular" e "favoritar"** | M | 1 |

**Critérios de aceite — MVP 1**

- **CA-RF03.1-01** — *Dado* um simulado completo, *quando* iniciado, *então* a sessão é criada em andamento com exatamente 80 questões; em simulado personalizado, com exatamente N questões respeitando os filtros escolhidos.
- **CA-RF03.2-01** *(proposto)* — *Dado* um simulado em andamento, *quando* o tempo se esgota, *então* a sessão é finalizada e corrigida automaticamente com as respostas já registradas.
- **CA-RF03.4-01** — *Dado* um simulado finalizado, *quando* corrigido, *então* acertos + erros + em branco = total de questões consideradas, e a situação é "aprovado" somente se acertos ≥ nota de corte configurada.
- **CA-RF03.6-01** — *Dado* uma resposta registrada, *quando* a página é recarregada ou a conexão cai, *então* nenhuma resposta já enviada é perdida e o simulado pode ser retomado do ponto em que parou.
- **CA-RF03.8-01** — *Dado* um simulado em andamento, *quando* a questão é exibida, *então* as ações "pular" e "favoritar" não estão disponíveis.

### RF04 — Tutor de IA (RAG)

| ID | Requisito | Prior. | MVP |
|---|---|---|---|
| RF04.1 | Chat contextual a partir de uma questão ("por que a alternativa C está errada?") | M | 2 |
| RF04.2 | Chat livre para dúvidas de conteúdo jurídico, com respostas ancoradas em fontes via RAG | S | 2 |
| RF04.3 | Histórico de conversas por usuário, com criação e exclusão | S | 2 |
| RF04.4 | Rastreabilidade: cada resposta indica o artigo de lei utilizado, com link direto para o módulo Legislação | S | 5 |
| RF04.5 | Perguntas sugeridas para iniciar a conversa | C | 2 |
| RF04.6 | Aviso permanente de que o tutor é apoio ao estudo e pode errar | M | 2 |
| RF04.7 | Ações sobre a resposta: copiar e interromper geração em andamento | S | 2 |

**Critérios de aceite — MVP 2**

- **CA-RF04.1-01** — *Dado* uma questão aberta, *quando* o usuário pergunta ao tutor, *então* a resposta considera o enunciado, as alternativas e o gabarito da questão.
- **CA-RF04.6-01** — *Dado* qualquer tela do tutor, *então* o aviso de que a IA pode errar permanece visível, sem exigir interação.
- **CA-RF04.7-01** — *Dado* uma resposta em geração, *quando* o usuário interrompe, *então* a geração cessa imediatamente e o texto parcial é mantido.
- **CA-RF04-02** — *Dado* que o limite diário de mensagens foi atingido (D08), *quando* o usuário envia nova mensagem, *então* recebe aviso claro do limite, sem erro técnico.

### RF05 — Legislação

| ID | Requisito | Prior. | MVP |
|---|---|---|---|
| RF05.1 | Repositório de leis navegável por artigo | S | 5 |
| RF05.2 | Busca textual com filtro por categoria | S | 5 |
| RF05.3 | Vínculo entre artigos e questões relacionadas (base do RAG) | S | 5 |
| RF05.4 | Favoritar artigo/lei, sincronizado para usuários autenticados | C | 5 |
| RF05.5 | Súmulas de tribunais superiores como alternância (aba) na mesma tela | C | Pós-5 |
| RF05.6 | Listagem agrupada por matéria, com nome do diploma, quantidade de artigos, data da última atualização e matéria associada | S | 5 |
| RF05.7 | Cobertura mínima: CF/88, CC, CPC, CP, CDC, CLT e legislação especial por disciplina (Estatuto da OAB e Código de Ética, ECA, Lei de Migração, LEP, Marco Civil da Internet, legislação eleitoral, LINDB, Improbidade Administrativa, Processo Administrativo, Licitações, Recuperação e Falência, CTN, entre outras) | S | 5 |

### RF06 — Planejamento de Estudos

| ID | Requisito | Prior. | MVP |
|---|---|---|---|
| RF06.1 | Definir data da prova e disponibilidade semanal (horas e dias) | S | 6 |
| RF06.2 | Gerar cronograma por disciplina ponderado pelo peso no exame e pelo desempenho atual do usuário | C | Pós-6 |
| RF06.3 | Checklist diário/semanal de tarefas de estudo | S | 6 |
| RF06.4 | Presets prontos (Iniciante, Equilibrado, Intensivo, Fim de Semana, Foco em Questões) como alternativa à configuração manual; parâmetros no Apêndice A | S | 6 |
| RF06.5 | Painel com foco atual por matéria, progresso do dia, por módulo e semanal/mensal frente à meta | S | 6 |
| RF06.6 | Contagem regressiva até a prova-alvo | S | 6 |
| RF06.7 | Trocar, salvar ou limpar o plano ativo | S | 6 |
| RF06.8 | A configuração manual é apresentada com o mesmo peso visual dos presets | C | 6 |

### RF07 — Ranking

| ID | Requisito | Prior. | MVP |
|---|---|---|---|
| RF07.1 | Ranking geral por pontuação em simulados, com participação opt-in | S | 4 |
| RF07.2 | Ranking por disciplina | S | 4 |
| RF07.3 | Filtro por período (semana, mês, geral) | S | 4 |
| RF07.4 | Elegibilidade mínima de questões respondidas (RN07) | S | 4 |
| RF07.5 | Critério de desempate (RN07) | S | 4 |
| RF07.6 | Exibir a posição individual mesmo fora do topo público | S | 4 |
| RF07.7 | Para usuários abaixo da elegibilidade, exibir progresso rumo à entrada ("faltam N questões") | S | 4 |
| RF07.8 | Privacidade por padrão: exibir apelido ou iniciais; nome completo apenas por opt-in explícito (RF01.5) | M | 4 |

### RF08 — Anotações

| ID | Requisito | Prior. | MVP |
|---|---|---|---|
| RF08.1 | Criar nota de texto livre vinculada a questão, artigo ou disciplina | S | 4 |
| RF08.2 | Listar e buscar anotações do usuário | S | 4 |
| RF08.3 | Editar e excluir anotação | S | 4 |
| RF08.4 | Suportar anotação vinculada a timestamp de videoaula *(condicionado a módulo de vídeo futuro)* | W | — |
| RF08.5 | Tipo de referência admite "AVULSA" (sem vínculo); listagem com filtro por categoria (Todas, Questões, Avulsas), contador e estado vazio orientativo | S | 4 |

### RF09 — Administração de Conteúdo

| ID | Requisito | Prior. | MVP |
|---|---|---|---|
| RF09.1 | Ingestão de novas provas (scraping, parsing de PDF e de gabarito) | M | 1 |
| RF09.2 | Acompanhamento do status de ingestão | M | 1 |
| RF09.3 | CRUD manual de questões e disciplinas para correção de erros de parsing | S | 1 |
| RF09.4 | Cadastro e atualização da base de legislação | S | 5 |
| RF09.5 | Cadastro de administrador disponível **somente** em ambiente de desenvolvimento; inexistente em produção | M | 1 |

### RF10 — Desafio de Aquisição

| ID | Requisito | Prior. | MVP |
|---|---|---|---|
| RF10.1 | Fluxo público, sem cadastro, com N questões reais (referência: 3) | S | 3 |
| RF10.2 | Resultado imediato e explicação de cada questão ao final | S | 3 |
| RF10.3 | Convite para criar conta gratuita ao concluir (CTA de conversão) | S | 3 |
| RF10.4 | Registro de eventos de funil: início, cada resposta e conclusão | S | 3 |
| RF10.5 | Funcionamento integral sem dependência de autenticação | S | 3 |
| RF10.6 | "Simulado cortesia" anônimo (1 por dia): tamanho e método de rastreamento **pendentes** (decisões D01 e D02) | C | 3 |

**Critérios de aceite — MVP 3**

- **CA-RF10.1-01** — *Dado* um visitante sem conta, *quando* acessa o desafio, *então* responde às N questões sem qualquer solicitação de login.
- **CA-RF10.3-01** — *Dado* um desafio concluído, *quando* o resultado é exibido, *então* o convite de cadastro é apresentado e o evento de conclusão é registrado.
- **CA-RF10.4-01** — *Dado* que o visitante se cadastra após o desafio, *quando* a conta é criada, *então* a sessão anônima fica associada ao novo usuário para fins de métrica de conversão.
- **CA-RF10.5-01** — *Dado* tráfego acima do limite por origem, *quando* excedido, *então* o sistema responde com limitação de taxa (HTTP 429) sem consumir recursos de LLM.

### RF11 — Gamificação

| ID | Requisito | Prior. | MVP |
|---|---|---|---|
| RF11.1 | Sala de jogos com mini-jogos de conteúdo jurídico (ex.: quiz rápido, forca de termos) | C | 8 |
| RF11.2 | Pontuação própria, vitórias, sequência atual e melhor sequência histórica | C | 8 |
| RF11.3 | A pontuação dos jogos **não** conta para o Ranking de estudo (RF07) | M | 8 |
| RF11.4 | Streak diário de estudo como gatilho de retenção, exibido no perfil e no dashboard (RN08) | C | 8 |

### RF12 — Assinatura e Monetização

| ID | Requisito | Prior. | MVP |
|---|---|---|---|
| RF12.1 | Página de planos destacando a melhor opção de custo-benefício e o valor mensal equivalente | M | 7 |
| RF12.2 | Pagamento recorrente por cartão e pagamento avulso por PIX/boleto | M | 7 |
| RF12.3 | Período de teste gratuito e garantia de reembolso em N dias (RN11) | S | 7 |
| RF12.4 | Gestão de estados de pagamento (pendente, aprovado, recusado, cancelado, estornado) integrada a gateway | M | 7 |
| RF12.5 | Modelo freemium com definição explícita do que é gratuito e do que é exclusivo | M | 7 |
| RF12.6 | Cancelamento autoatendido na área de configurações | M | 7 |
| RF12.7 | Avaliar pagamento dividido em dois cartões, para quem não usa conta no gateway | C | Pós-7 |

---

## 3. Regras de Negócio

| ID | Regra |
|---|---|
| **RN01** | A prova oficial da 1ª fase tem 80 questões; cada questão tem 4 alternativas e uma única correta, exceto as anuladas. |
| **RN02** | A duração de referência do simulado completo é de 5 horas. |
| **RN03** | A nota de corte é **parametrizável** (referência: 40 acertos em 80 questões). |
| **RN04** | Tratamento de questões anuladas na correção: **pendente (D03)**. Proposta: contabilizar como acerto para todos e exibir sinalização de anulação. |
| **RN05** | A disciplina de cada questão é definida por faixas numéricas determinísticas do edital, sem uso de IA. |
| **RN06** | A dificuldade calculada considera taxa de acerto e tempo médio de resposta (acerto baixo + tempo alto → mais difícil, e vice-versa). Exige mínimo de respostas (referência: 30); abaixo disso, vale a dificuldade padrão. O recálculo é assíncrono e periódico, nunca a cada resposta. |
| **RN07** | Ranking: elegibilidade a partir de um mínimo de questões respondidas (referência: 20); desempate por número de acertos e, em seguida, taxa de acerto; exibição por apelido ou iniciais por padrão; pontuação de jogos não entra. |
| **RN08** | Um dia conta para o streak quando há ao menos uma questão respondida *(proposto)*. |
| **RN09** | Toda resposta do tutor exibe o aviso de limitação e, quando houver, as fontes utilizadas; resposta sem fonte recuperada deve ser sinalizada como tal. |
| **RN10** | Limites de uso do tutor no plano gratuito: **pendente (D08)**. |
| **RN11** | A garantia de reembolso é política própria do produto, comunicada de forma igual para qualquer método de pagamento. |
| **RN12** | "Reiniciar estatísticas" zera progresso e desempenho, preservando conta, assinatura, anotações e favoritos. "Excluir conta" remove dados de forma permanente. |

---

## 4. Requisitos Não Funcionais

| ID | Categoria | Requisito | Verificação |
|---|---|---|---|
| **RNF01** | Desempenho | Listagem e resolução de questão com latência p95 < 300 ms, excluídas chamadas ao LLM | Teste de carga; métrica por endpoint |
| **RNF02** | Desempenho (IA) | Resposta do tutor em *streaming* (SSE ou WebSocket); tempo até o primeiro token como métrica monitorada (meta inicial a calibrar) | Métrica de latência do tutor |
| **RNF03** | Escalabilidade | Backend *stateless*, permitindo múltiplas instâncias atrás de balanceador | Teste com 2+ instâncias |
| **RNF04** | Disponibilidade e integridade | Cada resposta de simulado é persistida no momento do envio, nunca apenas ao final | CA-RF03.6-01 |
| **RNF05** | Resiliência | Chamadas a LLM, gateway de pagamento e fontes externas com *timeout*, *circuit breaker* e mensagem de contingência ao usuário; falha de um módulo não derruba os demais | Testes de falha injetada |
| **RNF06** | Segurança (autenticação) | Senhas com hash BCrypt; JWT de curta duração + refresh token; *rate limiting* em autenticação e no chat. Implementação completa na fase final (ADR-003) | Revisão de segurança antes de qualquer exposição pública |
| **RNF07** | Segurança (pagamentos) | Nenhum dado de cartão trafega ou é armazenado no backend próprio (tokenização no gateway); webhooks validados por assinatura | Revisão de integração |
| **RNF08** | Segurança (credenciais) | Chaves privilegiadas nunca no frontend nem no controle de versão; o client usa apenas chaves públicas | Varredura de segredos no CI |
| **RNF09** | Privacidade (LGPD) | Consentimento explícito para exibição no ranking; dados de estudo não compartilhados sem opt-in; exclusão permanente a pedido; rastreamento anônimo (RF10.6) minimizado, com base legal e retenção definidas | Revisão de privacidade |
| **RNF10** | Observabilidade | Logs estruturados e métricas por módulo (Actuator + Micrometer/Prometheus), com identificação do módulo em logs e métricas | Painel e alertas |
| **RNF11** | Manutenibilidade | Fronteiras entre módulos verificadas automaticamente; nenhum módulo acessa internals de outro | `ApplicationModules.verify()` no CI |
| **RNF12** | Schema de dados | Flyway como dono exclusivo do schema (`ddl-auto: validate`); nenhuma alteração fora de migração versionada | Falha de inicialização se houver divergência |
| **RNF13** | Custo | Monitorar tokens de LLM por usuário/mês e aplicar limites de uso no plano gratuito | Relatório mensal de custo |
| **RNF14** | Compatibilidade e UX | Frontend responsivo (mobile-first). Prompt de instalação de PWA não deve cobrir conteúdo de estudo; se existir, aparece em momento de baixa fricção (ex.: fim de sessão) com dispensa persistente | Revisão de UX |
| **RNF15** | Acessibilidade *(proposto)* | Conformidade com WCAG 2.1 nível AA nas telas de estudo | Auditoria automatizada + revisão manual |
| **RNF16** | Idioma | Interface e mensagens em português do Brasil | Revisão de conteúdo |

---

## 5. Premissas, Restrições e Dependências

**Premissas**
- O volume inicial de usuários é baixo o bastante para operar em um único banco PostgreSQL com pgvector.
- A base de questões atual foi validada em 12/09/2026 (detalhes em `03`).

**Restrições**
- Backend em Java (Spring Boot / Spring Modulith); frontend em Angular (planejado).
- Schema gerido exclusivamente por Flyway.
- Autenticação temporária até a fase final; o ambiente não pode ser exposto publicamente nem conter dados reais de usuários antes da entrega de RF01.2 (ver R05 em `03`).
- Conteúdo, marca e ativos próprios (ver nota de propriedade intelectual).

**Dependências externas**
- Fonte das provas: oab.fgv.br (ingestão).
- Provedor de LLM (via langchain4j).
- Gateway de pagamento com cartão recorrente e PIX/boleto.
- *(Futuro)* e-mail transacional e *push notification*.
- *(Candidato, não confirmado)* serviço de jurisprudência para enriquecimento do tutor.

---

## 6. Rastreabilidade (requisito → MVP → módulo)

| MVP | Escopo | Requisitos | Módulos (ver `02`) |
|---|---|---|---|
| 1 | Questões, simulados e conta | RF01.1, 01.4, 01.6 (exclusão), RF02.1–02.2, RF03, RF09.1–09.3, 09.5 | questoes, usuarios, administradores, simulados |
| 2 | Tutor de IA | RF02.3, RF04.1–04.3, 04.5–04.7 | tutor-ia |
| 3 | Desafio de aquisição | RF10 | aquisicao |
| 4 | Engajamento | RF00, RF01.6 (reinício), RF02.4–02.6, RF07, RF08, RF02.9 (pós) | dashboard, ranking, anotacoes |
| 5 | Legislação | RF02.7, RF04.4, RF05, RF09.4 | legislacao, tutor-ia |
| 6 | Planejamento | RF06 | planejamento |
| 7 | Monetização | RF01.5, RF12 | assinatura, usuarios |
| 8 | Gamificação | RF11 | gamificacao |
| Fase final | Segurança de acesso | RF01.2, RF01.3 | usuarios |

---

## Apêndice A — Parâmetros de referência

**A.1 Presets de planejamento (RF06.4):** parâmetros para calibrar os presets próprios; nomes e textos são originais do produto.

| Preset | Horas/dia | Questões/dia | Frequência | Observação |
|---|---|---|---|---|
| Iniciante | 1,5 h | 15 | dias úteis | Maior peso para disciplinas de maior peso no exame |
| Equilibrado | 3 h | 30 | dias úteis | Revisão no fim de semana |
| Intensivo | 6 h | 60 | todos os dias | Múltiplas sessões por dia |
| Fim de Semana | 2 h/dia (8 h no fds) | 20/dia (40 no fds) | fds + carga reduzida em dia útil | Revisão integrada |
| Foco em Questões | 4 h | 80 | — | Simulado semanal + revisão de erros |

Cada preset expõe, de forma resumida, ao menos tempo/dia e questões/dia.

**A.2 Referências de mercado (observação de set/2026; não são metas):**

| Item | Observação |
|---|---|
| Planos do concorrente de referência (ordemai.com.br) | Mensal R$ 49,99 · Trimestral R$ 39,99/mês (R$ 119,99) · Anual R$ 24,99/mês (R$ 299,99 à vista); conteúdo idêntico nos três, o diferencial é prazo e desconto |
| Recomendação para este produto | Iniciar com estrutura simples (plano único + anual com desconto), em vez de três níveis (ver D07 em `03`) |
| Volume de conteúdo do concorrente | ~3.040 questões comentadas, 38 simulados completos, ~313 videoaulas |
| Volume atual do produto | 1.520 questões (≈ metade); considerar na ingestão e no posicionamento até equilibrar |
| 2ª fase do concorrente | Trilha numerada (guia da prova, guias de peça, provas e espelhos, correção por IA, simulado de 5 h) com liberação futura; apenas registro, sem requisito |
