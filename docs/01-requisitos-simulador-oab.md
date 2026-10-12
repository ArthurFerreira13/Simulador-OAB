# Especificação de Requisitos de Software — Simulador-OAB

| Campo | Valor |
|---|---|
| **Produto** | Simulador-OAB (nome de trabalho) — plataforma de preparação para a 1ª fase do Exame de Ordem |
| **Documento** | 01 — Especificação de Requisitos (ERS) |
| **Versão** | 6.5.2 |
| **Data** | 12/10/2026 |
| **Responsável** | Arthur |
| **Status** | Em revisão |
| **Documentos relacionados** | `02-arquitetura-simulador-oab.md` (arquitetura e ADRs) · `03-status-roadmap-riscos.md` (status, roadmap, riscos e decisões pendentes) · `04-arquitetura-mobile.md` (cliente mobile Kotlin) |

> **Escopo deste documento:** define **o quê** o produto deve fazer e **com que qualidade**. Decisões de implementação estão em `02`; estado de andamento, riscos e decisões em aberto estão em `03`. Este documento consolida e substitui todas as versões anteriores da análise de requisitos (v1 a v6.4); o histórico está no final.

> **Propriedade intelectual:** o produto é inspirado em padrões de experiência observados no mercado, mas possui marca, textos, ilustrações, banco de questões, identidade verbal e ativos próprios. Não se copia código, nome, logotipo nem conteúdo proprietário de terceiros. Dados pessoais de terceiros não integram nenhum artefato do projeto.

---

## 1. Introdução

### 1.1 Propósito

Especificar os requisitos funcionais, regras de negócio e requisitos não funcionais do Simulador-OAB, servindo de referência única para desenvolvimento, testes e validação com o responsável pelo produto.

### 1.2 Visão do produto

Plataforma de preparação para a 1ª fase do Exame de Ordem (OAB/FGV), com banco de questões oficiais, simulados no formato real da prova, correção automática com estatísticas de desempenho e um tutor de IA (RAG) que explica erros e responde dúvidas ancoradas em legislação. Atende dois clientes sobre a mesma API: **web (Angular)** e **aplicativo mobile (Kotlin, Android primeiro)**. O produto evolui a base existente (`agente-rag`: Spring Modulith + langchain4j) para uma plataforma completa.

Modelo de acesso: todo novo usuário entra em **trial reverso** (7 dias com acesso completo) e, ao fim dele, passa ao **plano gratuito** limitado ou a um **plano pago** (RF12).

### 1.3 Objetivos e indicadores de sucesso

| Objetivo | Indicador | Meta inicial (hipótese a calibrar) |
|---|---|---|
| Permitir prática massiva no formato real da prova | Simulados concluídos por usuário ativo/mês | ≥ 1 |
| Converter visitantes em usuários | Conversão do Desafio de Aquisição → cadastro | A definir após primeiras 500 sessões |
| Entregar valor pedagógico | Usuários que usam o Tutor após errar uma questão | A definir após MVP 2 |
| Reter usuários | Retenção D7 / D30 | A definir após MVP 4 |
| Manter viabilidade econômica | Custo de LLM por usuário ativo/mês | Teto definido antes do MVP 2 (ver decisão D08 em `03`) |
| Converter trial em assinatura | Conversão trial → plano pago ao fim dos 7 dias | A definir após MVP 7 |
| Gerar receita recorrente | Ticket médio por assinante pago e mix de planos (mensal, trimestral, semestral, anual) | A definir após MVP 7 |

### 1.4 Escopo

Banco de questões; simulados cronometrados; tutor de IA; legislação; planejamento de estudos; ranking; anotações; desafio de aquisição sem cadastro; gamificação; assinatura e monetização (trial reverso e plano gratuito); dashboard; administração de conteúdo; painel administrativo de usuários e assinaturas; aplicativo mobile Android com notificações push.

### 1.5 Fora de escopo (versão atual)

- 2ª fase do Exame (peças e questões discursivas, correção por IA).
- Videoaulas e produção de conteúdo audiovisual.
- Comunidade, fóruns e qualquer funcionalidade que exija moderação.
- Aplicativo iOS (avaliado em D16; Android é a primeira plataforma mobile).

### 1.6 Personas

| Persona | Descrição | Necessidade principal |
|---|---|---|
| **Candidato** | Estuda para a OAB, pelo web ou pelo app | Prática massiva de questões e feedback rápido |
| **Visitante** | Chegou por anúncio ou indicação e ainda não tem conta | Valor imediato antes de decidir cadastrar |
| **Administrador de conteúdo** | Cura provas, disciplinas, legislação e correções; gerencia usuários e assinaturas pelo painel (RF14). Papel único `ADMIN` (D24); inicialmente o próprio responsável pelo produto | Manter a base confiável e atualizada, com ações rastreáveis |
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
| **Trial reverso** | Período inicial de acesso completo concedido ao criar a conta (7 dias), sem cartão; depois, o usuário cai no plano gratuito ou assina |
| **Estado de acesso** | Situação do usuário (TRIAL, GRATUITO, PAGO) derivada no servidor, que define o que ele pode usar |
| **Feature gate** | Bloqueio de funcionalidade conforme o estado de acesso, aplicado no servidor |
| **FCM** | Firebase Cloud Messaging, serviço de *push* do Android |
| **OpenAPI** | Contrato versionado da API, fonte para gerar o cliente Kotlin e validar o Angular |

### 1.8 Convenções

- **Identificadores:** `RFnn.m` (requisito funcional), `RNnn` (regra de negócio), `RNFnn` (requisito não funcional), `CA-…` (critério de aceite), `Dnn` (decisão pendente, em `03`).
- **Prioridade (MoSCoW):** **M**ust (obrigatório para o MVP indicado) · **S**hould (importante, não bloqueante) · **C**ould (desejável) · **W**on't (fora de escopo agora).
- **Alocação de MVP:** os itens de Dashboard (RF00) e de conta (RF01.3 a RF01.6) não constavam no planejamento anterior; sua alocação nesta versão é **proposta** e depende de validação. Os requisitos RF13 a RF15 (mobile, painel administrativo, *push*) entraram nas versões 6.2 e 6.3; **Mobile 1**, **Mobile 2A**, **2B** e **2C** são marcos próprios, definidos em `03`.
- **Critérios de aceite:** detalhados para os MVPs 1 a 3 e para o Mobile 1 (formato Dado/Quando/Então). Os demais serão detalhados antes do início do respectivo MVP.

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
| RF00.6 | Indicador de sequência de estudo (streak) com calendário semanal e recorde histórico, a partir do dado de RF11.4 (o dono do dado é `gamificacao`; o dashboard do MVP 4 não exibe streak) | C | 8 |
| RF00.7 | Painel "Desempenho por matéria", com status "não praticado" quando não houver dados suficientes | S | 4 |
| RF00.8 | "Atividade recente": últimas interações com resultado resumido e tempo relativo | C | 4 |

### RF01 — Autenticação e Conta

| ID | Requisito | Prior. | MVP |
|---|---|---|---|
| RF01.1 | Cadastro por e-mail e senha; suporte a provedores externos (Google, Facebook, link por e-mail) | M | 1 |
| RF01.2a | Login/logout com JWT de acesso de curta duração, com papel (`USUARIO` ou `ADMIN`) | M | 1 |
| RF01.2b | Refresh token rotativo, revogação de sessões e de dispositivos | M | Fase final |
| RF01.3 | Recuperação de senha por e-mail | M | Fase final |
| RF01.4 | Perfil do usuário: nome, data da prova-alvo, disciplinas de maior dificuldade | S | 1 |
| RF01.5 | Área de configurações com abas: perfil, assinatura, preferências (incluindo privacidade), notificações e suporte | S | 7 |
| RF01.6 | Encerramento de conta com duas opções: **reiniciar estatísticas** (preserva conta, assinatura, anotações e favoritos) e **exclusão permanente** de dados (LGPD) | M | 1 (exclusão) · 4 (reinício) |

**Critérios de aceite — MVP 1**

- **CA-RF01.1-01** — *Dado* um e-mail inédito, *quando* o visitante envia cadastro válido, *então* a conta é criada, a senha é armazenada apenas como hash e o perfil pode ser consultado.
- **CA-RF01.1-02** — *Dado* um e-mail já cadastrado, *quando* há nova tentativa de cadastro, *então* o sistema retorna erro de conflito sem expor dados da conta existente.
- **CA-RF01.1-03** — *Dado* uma conta existente, *quando* se tenta alterar o e-mail, *então* a alteração é recusada (o e-mail é imutável); a troca de senha só é permitida para contas de provedor local.
- **CA-RF01.2a-01** — *Dado* credenciais válidas, *quando* o login é enviado, *então* a API devolve token de acesso de curta duração com o papel do usuário; *dado* credenciais inválidas, *então* o erro é genérico e não revela se o e-mail existe.
- **CA-RF01.6-01** — *Dado* um usuário que confirma a exclusão permanente, *quando* a operação conclui, *então* seus dados pessoais e de estudo deixam de existir em todos os módulos, e a remoção é propagada por evento (ver `02`, seção 4).

### RF02 — Banco de Questões

| ID | Requisito | Prior. | MVP |
|---|---|---|---|
| RF02.1 | Listar questões com filtros combináveis: exame/ano, disciplina, banca e situação do usuário (respondida, errada, não respondida). O filtro de dificuldade entra com RF02.9 | M | 1 |
| RF02.2 | Responder questão avulsa com feedback imediato (correta/incorreta) e gabarito | M | 1 |
| RF02.3 | Exibir o comentário da questão, indicando sua origem (curadoria ou IA); se não houver, oferecer "Explicar com IA" | M | 2 |
| RF02.4 | Favoritar questão e marcar para revisão | S | 4 |
| RF02.5 | Gerar lista de questões por filtro para revisão dirigida | C | 4 |
| RF02.6 | Modo estudo com curtir, compartilhar, reportar erro e pular questão | C | 4 |
| RF02.7 | Vincular a questão a artigos de legislação relacionados | S | 5 |
| RF02.8 | Registrar subtópico por questão (campo próprio, para filtro e estatística) e exibi-lo no cabeçalho junto com exame, matéria e dificuldade | C | Pós-5 |
| RF02.9 | **Dificuldade calculada por desempenho real** dos usuários (RN06) e filtro de dificuldade em RF02.1; questões abaixo do mínimo de respostas aparecem como "sem classificação" e podem ser filtradas assim | S | Pós-4 |

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
| RF04.2 | Chat livre para dúvidas de conteúdo jurídico, com respostas ancoradas em fontes via RAG. No MVP 2 a única fonte são os comentários de questões; sem fonte recuperada, a resposta é sinalizada (RN09) e o aviso é reforçado. Artigos de lei entram como fonte no MVP 5 (RF04.4) | S | 2 |
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
| RF12.1 | Página de planos com Mensal, Trimestral, Semestral e Anual (D07; valores em A.3), destacando a melhor opção de custo-benefício e o valor mensal equivalente | M | 7 |
| RF12.2 | Pagamento recorrente por cartão e pagamento avulso por PIX/boleto | M | 7 |
| RF12.3 | Garantia de reembolso em N dias (RN11). O período de teste é o trial reverso (RF12.8) | S | 7 |
| RF12.4 | Gestão de estados de pagamento (pendente, aprovado, recusado, cancelado, estornado) integrada a gateway | M | 7 |
| RF12.5 | Modelo freemium com matriz explícita do que cada estado de acesso inclui (tabela abaixo) | M | 1 (gate) · 7 (planos) |
| RF12.6 | Cancelamento autoatendido na área de configurações | M | 7 |
| RF12.7 | Avaliar pagamento dividido em dois cartões, para quem não usa conta no gateway | C | Pós-7 |
| RF12.8 | **Trial reverso:** ao criar a conta, 7 dias de acesso completo, sem cartão (D11, RN13) | M | 1 |
| RF12.9 | **Plano gratuito** após o trial: 1 simulado por dia e funcionalidades básicas; questões avulsas, tutor, plano de estudos, ranking e anotações indisponíveis (D12, RN14) | M | 1 |
| RF12.10 | Estado de acesso (TRIAL, GRATUITO, PAGO) determinado e aplicado **no servidor** em todo recurso restrito (RN15, RNF21) | M | 1 |
| RF12.11 | Exibir o estado de acesso, os dias restantes do trial e o que muda ao fim dele; mensagem clara ao atingir o limite diário | S | 1 |
| RF12.12 | Antiabuso do trial: verificação de e-mail e identificador por *hash* para impedir trials repetidos (D14, R12) | S | 7 |

**Matriz de acesso (RF12.5)** — derivada de D11 e D12; os demais recursos são definidos ao detalhar o MVP correspondente.

| Recurso | Trial (7 dias) | Gratuito | Pago |
|---|---|---|---|
| Simulado (completo ou personalizado) | Sem limite diário | 1 por dia | Sem limite diário |
| Resultado e desempenho por disciplina | Sim | Sim | Sim |
| Questões avulsas (RF02) | Sim | Não | Sim |
| Tutor de IA (RF04) | Sim, com cotas (D08) | Não | Sim, com cotas (D08) |
| Plano de estudos (RF06) | Sim | Não | Sim |
| Ranking (RF07) | Sim | Não | Sim |
| Anotações (RF08) | Sim | Não | Sim |

**Critérios de aceite — MVP 1**

- **CA-RF12.8-01** — *Dado* um cadastro válido, *quando* a conta é criada, *então* o estado de acesso é TRIAL, com término 7 dias depois, sem solicitação de cartão.
- **CA-RF12.9-01** — *Dado* um usuário GRATUITO sem simulado no dia, *quando* inicia um simulado, *então* a sessão é criada.
- **CA-RF12.9-02** — *Dado* um usuário GRATUITO que já usou o simulado do dia, *quando* tenta iniciar outro, *então* o servidor recusa com mensagem clara do limite e do próximo horário disponível (ver D13 para sessão abandonada).
- **CA-RF12.9-03** — *Dado* um usuário em TRIAL, *quando* inicia simulados, *então* não há limite diário.
- **CA-RF12.9-04** — *Dado* um usuário GRATUITO, *quando* chama diretamente a API de recurso restrito (avulsas, tutor, plano, ranking, anotações), *então* o servidor recusa, independentemente do cliente.
- **CA-RF12.9-05** — *Dado* um trial que expira, *quando* o prazo termina sem plano pago, *então* o estado passa a GRATUITO sem perda de histórico, resultados ou estatísticas.

### RF13 — Aplicativo Mobile (Kotlin)

Arquitetura em `04`. Alocação em **Mobile 1** (núcleo) e **Mobile 2** (engajamento e offline), este fatiado pelo que cada parte exige do backend: **2A** (offline, sincronização e push base; depende de MVP 1 + Mobile 1), **2B** (favoritos e dashboard; depende do MVP 4) e **2C** (plano e lembretes; depende do MVP 6).

| ID | Requisito | Prior. | Marco |
|---|---|---|---|
| RF13.1 | Login, cadastro e encerramento de conta no app | M | Mobile 1 |
| RF13.2 | Resolver questões por disciplina, com feedback imediato e comentário | M | Mobile 1 |
| RF13.3 | Simulado com persistência de cada resposta, resultado e desempenho por disciplina | M | Mobile 1 |
| RF13.4 | Favoritos e revisão de erros | S | Mobile 2B |
| RF13.5 | Credenciais e dados sensíveis em armazenamento cifrado; nenhuma senha ou token em texto puro | M | Mobile 1 |
| RF13.6 | Dashboard (foco do dia, progresso; streak quando existir, MVP 8) e plano de estudos com tarefas do dia | S | Mobile 2B (dashboard) · 2C (plano) |
| RF13.7 | Sincronização de operações feitas sem conexão, idempotente (sem duplicar resposta ou favorito) | S | Mobile 2A |
| RF13.8 | Configurações, lista de dispositivos autorizados e exclusão de conta | M | Mobile 1 |
| RF13.9 | Modo offline controlado: questões baixadas pelo usuário e respostas pendentes; não é cópia do banco | S | Mobile 2A |
| RF13.10 | O app exibe e respeita o estado de acesso informado pela API; não decide acesso localmente (RNF21) | M | Mobile 1 |
| RF13.11 | Versão mínima de Android e tela de atualização obrigatória quando a API exigir (D17) | S | Mobile 2A |

**Critérios de aceite — Mobile 1** *(propostos; os critérios técnicos estão em `04`, seção 10)*

- **CA-RF13.3-01** — *Dado* um simulado em andamento no app, *quando* a conexão cai, *então* a sessão local não é apagada e a conclusão oficial só é exibida após confirmação da API.
- **CA-RF13.10-01** — *Dado* um usuário GRATUITO, *quando* abre um recurso restrito no app, *então* vê o aviso de acesso e a API recusa o pedido se for feito mesmo assim.
- **CA-RF13-11** — *Dado* um app abaixo da versão mínima, *quando* a API sinaliza incompatibilidade, *então* o app exibe a tela de atualização sem perder dados locais pendentes.

### RF14 — Painel Administrativo

Papel único `ADMIN` (D24). O administrador vê contagens e estado, nunca conteúdo do usuário (D29, RN17).

| ID | Requisito | Prior. | MVP |
|---|---|---|---|
| RF14.1 | Listar e buscar usuários com estado de acesso, plano e contagens agregadas de uso | M | 7 |
| RF14.2 | Consultar assinatura e histórico de pagamentos de um usuário | M | 7 |
| RF14.3 | Cancelar assinatura com efeito imediato, com registro (D25) | M | 7 |
| RF14.4 | Conceder trial manualmente, com justificativa e registro (D26) | S | 7 |
| RF14.5 | Excluir usuário, com dupla confirmação (D27); propaga `UsuarioExcluido` | S | 7 |
| RF14.6 | Auditoria de toda ação administrativa na tabela `admin_auditoria` (D28, RN16) | M | 7 |

### RF15 — Notificações Push

| ID | Requisito | Prior. | Marco |
|---|---|---|---|
| RF15.1 | Registrar e remover o token de *push* por usuário e dispositivo | M | Mobile 2A |
| RF15.2 | Preferências de notificação e horário permitido, por usuário | S | Mobile 2A |
| RF15.3 | Tipos iniciais: simulado retomável (2A), lembrete de tarefa do plano (2C) e alerta de streak, opt-in (após MVP 8) | S | Mobile 2A · 2C |
| RF15.4 | Conteúdo sem informação sensível (RN19) | M | Mobile 2A |
| RF15.5 | Remover tokens inválidos devolvidos pelo provedor | S | Mobile 2A |

---

## 3. Regras de Negócio

| ID | Regra |
|---|---|
| **RN01** | A prova oficial da 1ª fase tem 80 questões; cada questão tem 4 alternativas e uma única correta, exceto as anuladas. |
| **RN02** | A duração de referência do simulado completo é de 5 horas. |
| **RN03** | A nota de corte é **parametrizável** (referência: 40 acertos em 80 questões). |
| **RN04** | Tratamento de questões anuladas na correção: **pendente (D03)**. Proposta: contabilizar como acerto para todos e exibir sinalização de anulação. |
| **RN05** | A disciplina de cada questão é definida por faixas numéricas determinísticas do edital, sem uso de IA. |
| **RN06** | **Dificuldade calculada** a partir das respostas dos usuários (simulado e avulsas), em job assíncrono e periódico, nunca a cada resposta. Considera só questões efetivamente respondidas (em branco ficam fora) e exclui as anuladas (D03). Exige mínimo de respostas por questão (referência: 30); abaixo disso a questão fica **sem classificação**, sem valor padrão presumido. Fase inicial: apenas taxa de acerto; o tempo médio só entra com dados de respostas avulsas, pois no simulado a navegação livre o torna pouco confiável. Faixas (fácil, média, difícil) definidas por terços da distribuição real quando houver volume; cortes fixos servem só de referência inicial. As estatísticas são agregadas por questão, sem `usuarioId`, e permanecem após reinício de estatísticas ou exclusão de conta. |
| **RN07** | Ranking: elegibilidade a partir de um mínimo de questões respondidas (referência: 20); desempate por número de acertos e, em seguida, taxa de acerto; exibição por apelido ou iniciais por padrão; pontuação de jogos não entra. |
| **RN08** | Um dia conta para o streak quando há ao menos uma questão respondida *(proposto)*. |
| **RN09** | Toda resposta do tutor exibe o aviso de limitação e, quando houver, as fontes utilizadas; resposta sem fonte recuperada deve ser sinalizada como tal. |
| **RN10** | Limites de uso do tutor no trial e no plano pago: **pendente (D08)**. O plano gratuito não inclui o tutor (RF12.9). |
| **RN11** | A garantia de reembolso é política própria do produto, comunicada de forma igual para qualquer método de pagamento. |
| **RN12** | "Reiniciar estatísticas" zera progresso e desempenho, preservando conta, assinatura, anotações e favoritos. "Excluir conta" remove dados de forma permanente. |
| **RN13** | Trial reverso: 7 dias corridos de acesso completo a partir da criação da conta, sem cartão (D11). Sem plano pago ao fim do prazo, o usuário passa ao plano gratuito. |
| **RN14** | Plano gratuito: no máximo 1 simulado iniciado por dia (D12). Sessão abandonada conta no limite, exceto se não tiver nenhuma resposta *(proposto em D13)*. |
| **RN15** | O estado de acesso (TRIAL, GRATUITO, PAGO) é derivado no servidor, por datas e assinatura, e aplicado em todo recurso restrito. Clientes apenas exibem o estado. |
| **RN16** | Toda ação administrativa sensível (cancelar, conceder trial, excluir) gera registro de auditoria com ator, alvo, ação, justificativa e momento. O registro mínimo é preservado após a exclusão do usuário (D28). |
| **RN17** | O administrador acessa apenas contagens e estado do usuário, nunca respostas, anotações ou conversas (D29) *(redação proposta)*. |
| **RN18** | Cancelamento pelo administrador tem efeito imediato e exclusão exige dupla confirmação; ambos exigem justificativa (D25, D27) *(redação proposta)*. |
| **RN19** | Notificações push não contêm enunciado, resposta, conteúdo do tutor nem dado que revele o estudo na tela bloqueada. |

---

## 4. Requisitos Não Funcionais

| ID | Categoria | Requisito | Verificação |
|---|---|---|---|
| **RNF01** | Desempenho | Listagem e resolução de questão com latência p95 < 300 ms, excluídas chamadas ao LLM | Teste de carga; métrica por endpoint |
| **RNF02** | Desempenho (IA) | Resposta do tutor em *streaming* (SSE ou WebSocket); tempo até o primeiro token como métrica monitorada (meta inicial a calibrar) | Métrica de latência do tutor |
| **RNF03** | Escalabilidade | Backend *stateless*, permitindo múltiplas instâncias atrás de balanceador | Teste com 2+ instâncias |
| **RNF04** | Disponibilidade e integridade | Cada resposta de simulado é persistida no momento do envio, nunca apenas ao final | CA-RF03.6-01 |
| **RNF05** | Resiliência | Chamadas a LLM, gateway de pagamento e fontes externas com *timeout*, *circuit breaker* e mensagem de contingência ao usuário; falha de um módulo não derruba os demais | Testes de falha injetada |
| **RNF06** | Segurança (autenticação) | Senhas com hash BCrypt; JWT de acesso de curta duração (RF01.2a, implementado); refresh token rotativo e revogação (RF01.2b) e *rate limiting* em autenticação e no chat na fase final (ADR-003) | Revisão de segurança antes de qualquer exposição pública |
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
| **RNF17** | Consistência do acesso | O estado de acesso é calculado com o relógio do servidor, com transições (trial → gratuito → pago) determinísticas e cobertas por testes de fronteira (virada do dia, fim do trial) | Testes automatizados |
| **RNF18** | Contrato de API | Web e mobile consomem a mesma API versionada (`/api/v1`); o contrato OpenAPI é a fonte para gerar o cliente Kotlin; mudança incompatível exige nova versão | Verificação do contrato no CI |
| **RNF19** | Push | Provedor de *push* isolado atrás de porta; payload sem conteúdo sensível (RN19); falha do provedor não afeta estudo | Teste de falha injetada |
| **RNF20** | Privacidade no mobile (LGPD) | Dados locais mínimos, cifrados e com expiração; exclusão de conta invalida tokens e remove dispositivos; *analytics* de comportamento individual só com opt-in | Revisão de privacidade |
| **RNF21** | Feature gate | Restrições por estado de acesso aplicadas no servidor; clientes nunca são a barreira | CA-RF12.9-04 |

---

## 5. Premissas, Restrições e Dependências

**Premissas**
- O volume inicial de usuários é baixo o bastante para operar em um único banco PostgreSQL com pgvector.
- A base de questões atual foi validada em 12/09/2026 (detalhes em `03`).

**Restrições**
- Backend em Java (Spring Boot / Spring Modulith); frontend em Angular (planejado); cliente mobile em Kotlin (Android nativo com Jetpack Compose; ver `04`).
- Schema gerido exclusivamente por Flyway.
- Autenticação temporária até a fase final; o ambiente não pode ser exposto publicamente nem conter dados reais de usuários antes da entrega de RF01.2b (ver R05 em `03`).
- Conteúdo, marca e ativos próprios (ver nota de propriedade intelectual).

**Dependências externas**
- Fonte das provas: oab.fgv.br (ingestão).
- Provedor de LLM (via langchain4j).
- Gateway de pagamento com cartão recorrente e PIX/boleto.
- Firebase Cloud Messaging (confirmado, Mobile 2A) e Google Play Console (confirmado, Mobile 1).
- *(Futuro)* e-mail transacional (RF01.3).
- *(Candidato, não confirmado)* serviço de jurisprudência para enriquecimento do tutor.

---

## 6. Rastreabilidade (requisito → MVP → módulo)

| Marco | Escopo | Requisitos | Módulos (ver `02`) |
|---|---|---|---|
| MVP 1 | Questões, simulados, conta e estado de acesso | RF01.1, 01.2a, 01.4, 01.6 (exclusão), RF02.1–02.2, RF03, RF09.1–09.3, 09.5, RF12.5 (gate), RF12.8–12.11 | questoes, usuarios, auth, administradores, simulados |
| MVP 2 | Tutor de IA | RF02.3, RF04.1–04.3, 04.5–04.7 | tutor-ia |
| MVP 3 | Desafio de aquisição | RF10 | aquisicao |
| MVP 4 | Engajamento | RF00 (exceto 00.6), RF01.6 (reinício), RF02.4–02.6, RF07, RF08, RF02.9 (pós) | dashboard, ranking, anotacoes |
| MVP 5 | Legislação | RF02.7, RF04.4, RF05, RF09.4 | legislacao, tutor-ia |
| MVP 6 | Planejamento | RF06 | planejamento |
| MVP 7 | Monetização e admin | RF01.5, RF12.1–12.7, RF12.12, RF14 | assinatura, usuarios, administradores |
| MVP 8 | Gamificação | RF11, RF00.6 | gamificacao |
| Mobile 1 | Núcleo do app | RF13.1–13.3, 13.5, 13.8, 13.10 | app Kotlin (`04`), API |
| Mobile 2A | Offline, sincronização e push base | RF13.7, 13.9, 13.11, RF15.1, 15.2, 15.4, 15.5, RF15.3 (simulado retomável) | app Kotlin, notificacoes, sincronizacao |
| Mobile 2B | Favoritos e dashboard | RF13.4, RF13.6 (dashboard) | app Kotlin |
| Mobile 2C | Plano e lembretes | RF13.6 (plano), RF15.3 (lembrete de tarefa) | app Kotlin, notificacoes |
| Fase final | Segurança de acesso | RF01.2b, RF01.3 | auth, usuarios |

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
| Estrutura adotada (D07, 12/10/2026) | Quatro planos com desconto progressivo (A.3), em vez de plano único + anual |
| Posicionamento *(hipótese)* | Mensal R$ 9,99 fica cerca de 80% abaixo do mensal do concorrente (R$ 49,99); o anual, a R$ 6,67/mês equivalentes, cerca de 73% abaixo do anual dele (R$ 24,99/mês) |
| Lacuna de mercado *(hipótese)* | Faixa de preço de entrada baixa com tutor de IA e simulado diário gratuito após o trial; validar com dados reais antes de comunicar |
| Volume de conteúdo do concorrente | ~3.040 questões comentadas, 38 simulados completos, ~313 videoaulas |
| Volume atual do produto | 1.520 questões (≈ metade); considerar na ingestão e no posicionamento até equilibrar |
| 2ª fase do concorrente | Trilha numerada (guia da prova, guias de peça, provas e espelhos, correção por IA, simulado de 5 h) com liberação futura; apenas registro, sem requisito |

**A.3 Estrutura de planos (D07, decidida em 12/10/2026)**

| Plano | Preço | Equivalente mensal | Desconto frente a pagar o Mensal pelo mesmo período |
|---|---|---|---|
| Mensal | R$ 9,99 | R$ 9,99 | — |
| Trimestral | R$ 26,97 | R$ 8,99 | 10% |
| Semestral | R$ 47,94 | R$ 7,99 | 20% |
| Anual | R$ 79,99 | R$ 6,67 | 33% |

---

## Histórico de revisões

| Versão | Data | Alteração |
|---|---|---|
| 1.0–5.0 | — | Versões iniciais da análise de requisitos; v4.0 (15/09/2026) consolidou o estado real de implementação; v5.0 (09/10/2026) removeu dados de terceiros da inspeção do concorrente |
| 6.0 | 09/10/2026 | Reestruturação em três documentos (requisitos, arquitetura, status); MoSCoW, critérios de aceite e RNFs mensuráveis; revisão pelos princípios de arquitetura modular; D03, D04, D09, D10 |
| 6.1–6.1.1 | 12/10/2026 | Correção do intervalo de provas (29º–47º) e benchmark *(entrada 6.1 não recuperada; completar se necessário)* |
| 6.2 | 12/10/2026 | Painel administrativo (RF14), D24–D29, R16 |
| 6.3 | 12/10/2026 | Mobile Kotlin; RF13, RF15, RNF18–RNF20; RF01.2 dividido; RN19 |
| 6.4 | 12/10/2026 | Matriz de planos (RF12.5); RF12.9 e RN14 reescritos; CA-RF12.9-04/05, CA-RF13-11; RNF21 |
| **6.5** | **12/10/2026** | **Estrutura de planos (D07): Mensal R$ 9,99 · Trimestral R$ 26,97 · Semestral R$ 47,94 · Anual R$ 79,99. Indicadores de ticket médio e mix de planos em §1.3. A.2 com posicionamento e lacuna de mercado** |
| 6.5.1 | 12/10/2026 | Dificuldade calculada: RN06 reescrita (só questões respondidas, anuladas fora, sem valor padrão, taxa de acerto primeiro, faixas por terços); filtro de dificuldade removido do MVP 1 (RF02.1) e movido para RF02.9 |
| 6.5.2 | 12/10/2026 | Revisão de módulos futuros: Mobile 2 fatiado em 2A/2B/2C; streak (RF00.6) movido para o MVP 8; RF04.2 limitado a comentários no MVP 2 |
