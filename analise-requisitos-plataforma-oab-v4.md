# Análise de Requisitos — Plataforma de Preparação para a OAB (v4 — consolidada)

Nome de trabalho: Simulador-OAB (evolução do projeto agente-rag)
Stack alvo: Backend Java (Spring Boot 4.1 / Spring Modulith 2.1) + Frontend Angular (planejado)
Revisão: v4 — documento canônico único, consolida e substitui versões anteriores. Recomenda-se remover os arquivos anteriores do projeto para evitar divergência futura entre cópias.

Nota de propriedade intelectual e pesquisa de mercado: este documento especifica um produto inspirado nos fluxos e padrões de experiência de soluções de mercado, mas com marca, textos, ilustrações, banco de questões, identidade verbal e ativos próprios. O que se aproveita é sempre o padrão de fluxo e a estrutura de dados, nunca nomes, textos, preços exatos como meta obrigatória, ou dados pessoais de usuários reais (dados individuais vistos em rankings de mercado não devem circular em nenhum artefato do projeto).

## 0. Estado atual de implementação (consolidado)

Esta seção substitui a coluna "Status no agente-rag" das versões anteriores, já defasada. Reflete o estado real do código em 15/09/2026.

| Módulo | Status | Detalhe |
| --- | --- | --- |
| questoes | Implementado (leitura) | Entidades read-only (Prova/Questao/Alternativa/Disciplina), repositórios com JOIN FETCH para evitar N+1, NumeroProvaExtractor para lookup de nº de prova em memória, QuestoesQueryService (buscarQuestoesPorProva / verificarResposta / buscarGabarito) |
| usuarios | Implementado | Entidade Usuario com ProvedorAuth (LOCAL/GOOGLE/FACEBOOK/EMAIL_LINK); endpoints de cadastro/consultarPerfil/atualizarPerfil/excluirConta usando e-mail+senha como autenticação temporária; e-mail updatable = false; troca de senha restrita a contas LOCAL |
| administradores | Implementado | Entidade Administrador separada da Usuario (isolamento de segurança); endpoint de cadastro com @Profile("dev") — nunca existe em produção |
| simulados | Schema pronto, código em validação | V6: simulado_sessao / simulado_sessao_questao / resposta_simulado / resultado_simulado, com FKs reais para usuarios e questoes; classes Java escritas, mas ainda não confirmadas compiladas/testadas |
| Classificação de disciplinas | Implementado | V7: 1.520 questões classificadas em 20 disciplinas por faixas numéricas determinísticas do edital da OAB — sem uso de IA |
| Autenticação JWT | Adiada deliberadamente | API roda sem trava de autenticação completa durante o desenvolvimento para não bloquear testes; spring-security-crypto (BCrypt) já em uso só para hash de senha |
| Frontend Angular | Não iniciado | Planejado para depois da API estar mais madura |
| RAG/IA (Tutor) | Não iniciado | langchain4j já é dependência do projeto, mas módulo tutor-ia ainda não construído |

Migrações Flyway até o momento: V1 (baseline restaurada), V2–V7, onde V3 = extensão pgvector, V4 = usuarios, V5 = administradores, V6 = simulados + disciplinas, V7 = classificação de disciplina das 1.520 questões. Flyway é o dono exclusivo do schema (ddl-auto: validate).

Decisão pendente: escopo do "simulado cortesia" para usuários anônimos (1 por dia) — falta decidir (a) tamanho: simulado completo de 80 questões vs. versão reduzida, e (b) método de rastreamento: IP vs. cookie. Esta decisão afeta diretamente o RF10 (Desafio de Aquisição) abaixo.

## 1. Visão Geral

Plataforma web de preparação para a 1ª fase do Exame de Ordem Unificado (EOU) da OAB. O banco de questões é construído a partir de provas oficiais e históricas disponibilizadas pela **Fundação Getúlio Vargas (FGV)**, banca organizadora oficial do exame. A solução conta com simulados no formato real da prova, correção automática com estatísticas de desempenho e um tutor de IA (RAG) que explica erros e dúvidas sobre o conteúdo. O projeto evolui a base já existente (agente-rag, Spring Modulith + langchain4j), que hoje cobre ingestão/parsing de provas e a estrutura de simulados, estendendo-a para uma plataforma completa com frontend próprio.

### 1.1 Objetivo

Permitir que o candidato:

- estude por banco de questões oficiais da FGV filtradas por disciplina/ano/exame;
- realize simulados cronometrados no formato oficial da FGV (80 questões);
- receba explicação de cada questão (comentada) e, quando não houver comentário oficial, gerada via IA (RAG sobre legislação e doutrina);
- acompanhe evolução de desempenho por disciplina;
- organize um plano de estudos e tire dúvidas com um tutor conversacional;
- experimente o produto sem cadastro antes de decidir se cria conta;
- tenha um motivo lúdico/recorrente para voltar ao app além do estudo "seco".

### 1.2 Escopo (referência: estudo de mercado)

| Módulo | Descrição | Status de implementação |
| --- | --- | --- |
| Questões | Banco de questões filtrável e resolução avulsa | Entidades e serviço de consulta prontos; falta camada web (controller/DTO) |
| Simulados | Simulado completo cronometrado, formato FGV | Schema pronto; classes Java em validação |
| Tutor de IA | Chat que explica erros e responde dúvidas jurídicas | Não iniciado (langchain4j disponível, módulo ainda não construído) |
| Legislação | Consulta a leis "secas" (CF, CC, CPC, CP, CDC, CLT, legislação especial) | Não iniciado |
| Planejamento | Plano de estudos personalizado (cronograma por disciplina) | Não iniciado |
| Ranking | Comparação de desempenho entre usuários | Não iniciado |
| Anotações | Bloco de notas do usuário vinculado a questões/leis | Não iniciado |
| Desafio de aquisição | Fluxo sem cadastro para converter visitante em usuário | Não iniciado; inclui decisão pendente de "simulado cortesia" (ver seção 0) |
| Gamificação | Mini-jogos jurídicos, pontuação, sequência (separado do ranking de estudo) | Não iniciado |
| Assinatura/Monetização | Planos pagos, trial, pagamento recorrente e avulso | Não iniciado |
| Dashboard/Home | Tela inicial pós-login com foco do dia, métricas agregadas e atalhos | Não iniciado |
| Segunda fase (futuro) | Prática de peças e questões discursivas | Fora do MVP |
| Vídeos (futuro) | Videoaulas por disciplina | Fora do MVP |

## 2. Personas

- Candidato (usuário final): estuda para o Exame de Ordem Unificado (EOU) da FGV, quer prática massiva de questões oficiais e feedback rápido.
- Visitante não cadastrado: chegou por anúncio/indicação, ainda não decidiu criar conta — precisa de um gancho de valor imediato.
- Administrador de conteúdo: cadastra/curadoria de provas da FGV, disciplinas, legislação e correções (pode ser o próprio Arthur inicialmente).
- (Futuro) Professor/parceiro de conteúdo: produz videoaulas e comentários de questões.

## 3. Requisitos Funcionais

### RF00 — Dashboard / Tela Inicial

- RF00.1 Tela inicial pós-login com contagem regressiva até a data da prova alvo da FGV (reaproveita a mesma lógica de RF06.6, exibida também fora do módulo de Planejamento).
- RF00.2 Bloco "Foco de agora": até N disciplinas sugeridas (derivadas do plano de estudo ativo ou do desempenho recente) com atalho direto para praticar, e link para ver o plano completo.
- RF00.3 Cartões de métricas agregadas do usuário: questões resolvidas (total acumulado), taxa de acerto média geral, horas de estudo no mês corrente, cobertura (nº de disciplinas com pelo menos uma questão praticada / total de disciplinas).
- RF00.4 Bloco de frase motivacional, rotativo/atualizável.
- RF00.5 "Acesso Rápido": atalhos diretos para as ações mais frequentes (praticar questões, iniciar simulado, perguntar ao tutor, videoaulas ou equivalente do MVP atual).
- RF00.6 Indicador de sequência de estudo (streak) com calendário semanal (dias com e sem atividade) e recorde histórico — mesmo dado do RF11.4, exibido de forma resumida no Dashboard além da Sala de Jogos.
- RF00.7 Painel "Desempenho por matéria": lista de disciplinas com indicador de desempenho ou status "não praticado" quando ainda sem dados suficientes.
- RF00.8 "Atividade recente": log das últimas interações do usuário (questão respondida, simulado, etc.) com resultado resumido e tempo relativo (ex. "há 7 min").
- RF00.9 Ponto de atenção de UX: evitar sobrepor prompts de instalação de PWA ao conteúdo do Dashboard/estudo por padrão; se implementado, disparar em momento de baixa fricção (ex. fim de sessão) e sempre com opção de dispensa persistente.

### RF01 — Autenticação e Conta

- RF01.1 Cadastro de usuário (e-mail/senha e, opcionalmente, OAuth Google/Facebook) — implementado via ProvedorAuth (LOCAL/GOOGLE/FACEBOOK/EMAIL_LINK).
- RF01.2 Login/logout com JWT (access + refresh token) — adiado deliberadamente para o fim do desenvolvimento, para não bloquear testes de API; hoje a autenticação temporária usa e-mail+senha direto.
- RF01.3 Recuperação de senha por e-mail — não iniciado.
- RF01.4 Perfil do usuário (nome, meta de aprovação/data da prova, disciplinas de maior dificuldade) — parcialmente implementado (endpoints de consultar/atualizar perfil existem; campos de meta de prova ainda não confirmados no modelo).
- RF01.5 Área de configurações com abas: perfil, assinatura, preferências, notificações, contato/suporte — não iniciado.
- RF01.6 Encerrar conta com opção de "reiniciar estatísticas" (zera progresso sem perder conta, assinatura, anotações e favoritos) e opção de exclusão permanente de dados (LGPD) — endpoint excluirConta já existe; opção de "reiniciar estatísticas" ainda não implementada.

### RF02 — Banco de Questões

- RF02.1 Listar questões oficiais da FGV com filtros: ano/exame, disciplina, banca (FGV), dificuldade, "já respondida"/"errada"/"não respondida" — filtro por disciplina viável desde a classificação V7; demais filtros dependem de simulados/histórico do usuário.
- RF02.2 Responder questão avulsa com feedback imediato (correta/incorreta + gabarito comentado) — lógica de verificação já existe em QuestoesQueryService.verificarResposta; falta camada web.
- RF02.3 Exibir comentário oficial da questão da FGV quando existir; caso não exista, botão "Explicar com IA" (aciona o Tutor).
- RF02.4 Favoritar questão / marcar para revisão.
- RF02.5 Exportar/gerar lista de questões por filtro (para revisão dirigida).
- RF02.6 Modo estudo com curtir, compartilhar, reportar erro e pular questão.
- RF02.7 Vincular questão a artigo de legislação relacionado (link direto para o módulo Legislação).
- RF02.8 Registrar, por questão, um subtópico específico além da disciplina (ex. "Tribunal do Júri - Revelia" dentro de Direito Processual Penal), exibido no cabeçalho da questão junto com exame/matéria/dificuldade. Requer campo próprio no modelo de Questao (não apenas tag livre) para permitir filtro e estatística por subtópico no futuro.
- RF02.9 Dificuldade calculada por desempenho real dos alunos (decisão de produto confirmada), substituindo/complementando uma dificuldade estática definida na ingestão. Derivada de EstatisticaQuestao (ver seção 6):
  - Taxa de acerto baixa + tempo médio alto → dificuldade "Difícil"/"Muito Difícil".
  - Taxa de acerto alta + tempo médio baixo → "Muito Fácil"/"Fácil".
  - Questões com volume de respostas abaixo de um mínimo (ex. 30 respostas) mantêm dificuldade default/estimada até atingir esse mínimo.
  - Recalcular em job assíncrono periódico (não em tempo real a cada resposta).
  - O filtro de dificuldade do RF02.1 passa a operar sobre essa dificuldade calculada; combinação com filtro de matéria funciona como AND.
  - Diferencial competitivo frente a soluções tradicionais, cuja dificuldade costuma ser um rótulo estático de curadoria.

### RF03 — Simulados

- RF03.1 Iniciar simulado completo (80 questões no padrão oficial da FGV) ou personalizado (N questões, por disciplina/mix) — schema pronto (V6).
- RF03.2 Cronômetro por simulado (ex. 5h como na prova real da FGV), com opção de pausar (EM_ANDAMENTO/ABANDONADO/CONCLUIDO).
- RF03.3 Navegação entre questões, marcar para revisão antes de finalizar.
- RF03.4 Corrigir automaticamente ao finalizar, calculando ResultadoSimulado (acertos, erros, brancos, % de acerto, aprovado/reprovado pela nota de corte do Exame de Ordem).
- RF03.5 Desempenho detalhado por disciplina (DesempenhoPorDisciplina) com gráfico de evolução histórica.
- RF03.6 Retomar simulado abandonado.
- RF03.7 Tela de listagem exibindo estatísticas agregadas do usuário: simulados realizados, média de acertos, tempo médio.
- RF03.8 Durante o simulado em andamento, a barra de ações da questão é reduzida em relação ao modo de estudo avulso: manter compartilhar, vídeo/anotar, estatísticas e reportar; remover "pular" (no simulado o usuário só navega e pode deixar em branco, não pula em definitivo) e remover favoritar (favoritar faz sentido no banco avulso, não durante a prova cronometrada).

### RF04 — Tutor de IA (RAG)

- RF04.1 Chat contextual a partir de uma questão oficial da FGV ("por que a alternativa C está errada?").
- RF04.2 Chat livre para dúvidas de conteúdo jurídico, com respostas ancoradas em fontes (lei/doutrina) via RAG.
- RF04.3 Histórico de conversas do tutor por usuário (criar/excluir conversa).
- RF04.4 Indicar fonte/trecho de legislação usado na resposta (rastreabilidade) — diferencial: cada resposta linka diretamente para o artigo de lei específico usado (clique → abre o artigo no módulo Legislação), não apenas menciona a fonte em texto.
- RF04.5 Perguntas sugeridas para iniciar a conversa.
- RF04.6 Aviso permanente de que o tutor é apoio ao estudo e pode errar (mitigação de risco jurídico/reputacional).
- RF04.7 Ações de UX sobre a resposta: copiar, interromper geração em andamento.

### RF05 — Legislação

- RF05.1 Repositório de leis "secas" navegável por artigo.
- RF05.2 Busca textual na legislação, com filtro por categoria.
- RF05.3 Vincular artigo de lei a questões relacionadas (base para o RAG do tutor).
- RF05.4 Favoritar artigo/lei, sincronizado para usuários autenticados.
- RF05.5 Área de súmulas de tribunais superiores, apresentada como alternância (aba/toggle) dentro da mesma tela de legislação, não como rota separada.
- RF05.6 Listagem organizada por categoria/matéria como agrupador (cabeçalho de seção), com uma linha por diploma legal contendo: nome do diploma, quantidade de artigos, data da última atualização e matéria associada.
- RF05.7 Cobertura mínima esperada: além dos grandes códigos (CF/88, CC, CPC, CP, CDC, CLT), legislação especial por matéria: Estatuto da OAB e Código de Ética, Estatuto da Criança e do Adolescente, Lei de Migração, Lei de Execução Penal, Marco Civil da Internet, Código Eleitoral e legislação eleitoral correlata, LINDB, Lei de Improbidade Administrativa, Lei de Processo Administrativo, Nova Lei de Licitações, Lei de Recuperação Judicial e Falência, Código Tributário Nacional, entre outras normas especiais por disciplina.

### RF06 — Planejamento de Estudos

- RF06.1 Definir data da prova oficial e disponibilidade semanal (horas/dias de estudo).
- RF06.2 Geração de cronograma sugerido por disciplina, ponderado pelo peso de cada disciplina no Exame da OAB e pelo desempenho atual do usuário (disciplinas com mais erro recebem mais peso).
- RF06.3 Checklist diário/semanal de tarefas de estudo.
- RF06.4 Presets de plano prontos (ex. "Iniciante", "Equilibrado", "Intensivo", "Fim de Semana", "Foco em Questões", "Foco em Vídeo") como alternativa rápida à configuração manual — reduz o escopo do MVP de planejamento.
- RF06.5 Painel com foco atual por matéria, progresso do dia, progresso por módulo e progresso semanal/mensal frente à meta.
- RF06.6 Contagem regressiva até a data da prova alvo do Exame de Ordem.
- RF06.7 Trocar, salvar ou limpar o plano ativo.
- RF06.8 Parâmetros de referência para calibrar os presets próprios (lógica de parametrização, não nomes/textos):
  - Preset (equivalente) / Horas/dia / Questões/dia / Frequência / Observação
  - Iniciante / 1,5h / 15 / dias úteis / peso maior para disciplinas com mais peso no exame
  - Equilibrado / 3h / 30 / dias úteis / inclui revisão no fim de semana
  - Intensivo / 6h / 60 / todos os dias / múltiplas sessões por dia
  - Fim de Semana / 2h/dia (8h no fds) / 20/dia (40 no fds) / fds + carga reduzida em dia útil / revisão integrada
  - Foco em Questões / 4h / 80 / — / simulado semanal + revisão de erros
  - Foco em Vídeo / 3h / 20/dia após vídeo / dias úteis / revisão via resumo no fim de semana
  - Cada preset deve expor de forma resumida (ex. 2 badges de destaque) ao menos tempo/dia e questões/dia.
- RF06.9 A opção de configuração manual do plano (RF06.1) deve ser apresentada com o mesmo peso visual dos presets prontos (um card equivalente, não um link secundário).

### RF07 — Ranking

- RF07.1 Ranking geral por pontuação em simulados oficiais da FGV (opt-in, respeitando privacidade — nome ou apelido).
- RF07.2 Ranking por disciplina.
- RF07.3 Filtro por período (semana/mês/geral).
- RF07.4 Regra de elegibilidade: usuário só aparece no ranking após um mínimo de questões respondidas (ex. 20), para evitar distorção por amostra pequena.
- RF07.5 Critério de desempate: ordenar por número de acertos e usar taxa de acerto (%) como critério secundário.
- RF07.6 Exibir a posição individual do usuário mesmo fora do top exibido publicamente.
- RF07.7 Para usuários abaixo do mínimo de elegibilidade (RF07.4), exibir um indicador de progresso rumo à entrada no ranking (ex. "faltam N questões para você entrar"), em vez de simplesmente ocultar a tela.
- RF07.8 Diferenciação de privacidade em relação ao padrão de mercado: por padrão, exibir no ranking público apelido ou iniciais, não o nome completo do usuário; exibição de nome completo deve ser opt-in explícito nas preferências de privacidade (RF01.5). Plataformas tradicionais costumam expor nome completo por padrão associado à taxa de acerto individual — superfície de privacidade desnecessária a evitar no produto próprio.

### RF08 — Anotações

- RF08.1 Criar nota de texto livre vinculada a uma questão da FGV, artigo de lei ou disciplina.
- RF08.2 Listar/buscar anotações do usuário.
- RF08.3 Editar/excluir anotação.
- RF08.4 (se houver módulo de vídeo no futuro) Suportar anotação vinculada a timestamp de videoaula.
- RF08.5 O tipo de referência da anotação (referenciaTipo) deve admitir um valor "AVULSA" (ou referenciaId nulo), para notas de texto livre sem vínculo com questão/artigo/disciplina/vídeo específico. A tela de listagem deve permitir filtrar por categoria (Todas, Questões, Vídeos, Avulsas) e exibir contador total e estado vazio com orientação de uso.

### RF09 — Administração de Conteúdo

- RF09.1 Ingestão de novas provas e cadernos históricos da FGV (reaproveita ScraperService/PdfParserService/GabaritoParser já existentes).
- RF09.2 Acompanhamento de status de ingestão (StatusIngestao).
- RF09.3 CRUD manual de questões/disciplinas para correção de erros de parsing dos cadernos da FGV.
- RF09.4 Cadastro/atualização da base de legislação.
- RF09.5 (consolidado da seção 0) Cadastro de administrador restrito a ambiente de desenvolvimento (@Profile("dev")) — implementado; em produção este endpoint não deve existir, reforçando o isolamento entre usuarios e administradores.

### RF10 — Desafio de Aquisição

- RF10.1 Fluxo público sem exigência de cadastro: apresentar N questões reais de provas anteriores da FGV (ex. 3) para o visitante responder.
- RF10.2 Mostrar resultado imediato e explicação de cada questão ao final do desafio.
- RF10.3 Ao concluir, convidar o visitante a criar conta gratuita para continuar estudando (CTA de conversão).
- RF10.4 Registrar eventos de funil (início do desafio, cada resposta, conclusão) para métricas de aquisição/conversão.
- RF10.5 Este módulo é o principal ponto de entrada de marketing (anúncios, redes sociais) — deve funcionar sem dependências de autenticação para não perder conversão por fricção.
- RF10.6 (decisão pendente, consolidada da seção 0) "Simulado cortesia" para usuário anônimo (1 por dia): variante mais ampla do desafio de 3 questões, ainda sem decisão de escopo. Duas dimensões em aberto:
  - Tamanho: simulado completo de 80 questões da FGV (igual ao formato oficial) vs. versão reduzida (ex. 20-30 questões) — trade-off entre fidelidade ao formato real e custo de fricção/tempo para o visitante.
  - Rastreamento: por IP vs. por cookie — IP é mais simples mas gera falsos positivos (redes compartilhadas/NAT); cookie é mais preciso por dispositivo mas pode ser limpo pelo usuário para burlar o limite diário.

### RF11 — Gamificação

- RF11.1 "Sala de jogos" com mini-jogos de conteúdo jurídico (ex. quiz rápido baseado em exames da FGV, forca de termos jurídicos).
- RF11.2 Pontuação própria dos jogos, contagem de vitórias, sequência (streak) atual e melhor sequência histórica.
- RF11.3 Pontuação de jogos não deve contar para o Ranking de estudo (RF07) — sistemas de engajamento separados, para não distorcer a métrica de desempenho real na prova.
- RF11.4 Mecanismo de streak diário (ex. "estudou X dias seguidos") como gatilho de retenção, exibido no perfil/dashboard.

### RF12 — Assinatura e Monetização

- RF12.1 Página de planos com opções mensal, trimestral e anual, destacando a opção de melhor custo-benefício e o valor mensal equivalente.
- RF12.2 Pagamento recorrente via cartão de crédito e pagamento avulso via PIX/boleto.
- RF12.3 Período de teste gratuito (trial) e garantia de reembolso em N dias (ex. 7 dias).
- RF12.4 Gestão de estados de pagamento (pendente, aprovado, recusado, cancelado, estornado) integrada a um gateway de pagamento (ex. Mercado Pago ou similar).
- RF12.5 Modelo freemium: definir explicitamente o que é gratuito vs. o que é exclusivo do plano pago.
- RF12.6 Cancelamento de assinatura autoatendido, dentro da área de configurações (RF01.5).
- RF12.7 Benchmark de precificação observado em soluções de mercado (referência, não meta obrigatória):

  | Plano | Preço/mês | Cobrança | Desconto destacado |
    | --- | --- | --- | --- |
  | Mensal | R$ 49,99/mês | mensal recorrente | — |
  | Trimestral | R$ 39,99/mês | R$ 119,99 a cada 3 meses | "Economize 20%" |
  | Anual | R$ 24,99/mês | R$ 299,99 à vista, 1x/ano | "50% de desconto" |

  Serviços de mercado costumam oferecer conteúdo idêntico nos três planos (o diferencial é essencialmente prazo/desconto) — recomenda-se começar com uma estrutura mais simples (ex. plano único + anual com desconto) em vez de replicar os três níveis de cara.
- RF12.8 A garantia de reembolso do produto (RF12.3) deve ser uma política própria, comunicada de forma consistente independentemente do método de pagamento — gateways costumam limitar "compra garantida" apenas a pagamentos feitos através da própria conta do gateway.
- RF12.9 Avaliar suporte a split de pagamento em 2 cartões de crédito como opção "sem conta no gateway" — não é bloqueante de MVP.
- RF12.10 Benchmark de volume de conteúdo de referência: ~313 videoaulas, ~3.040 questões comentadas, 38 simulados completos da OAB (FGV). Gap atual do projeto: 1.520 questões (≈ metade do volume de referência) — considerar no planejamento de ingestão e na comunicação de posicionamento até o volume aumentar.

## 4. Requisitos Não Funcionais

| Categoria | Requisito |
| --- | --- |
| Desempenho | Resposta de API para listagem/resolução de questão < 300ms (p95), sem contar chamadas ao LLM |
| Desempenho (IA) | Resposta do tutor via streaming (SSE/WebSocket) para reduzir percepção de latência do LLM |
| Escalabilidade | Backend stateless (JWT quando implementado), permitindo múltiplas instâncias atrás de um load balancer |
| Disponibilidade | Simulado em andamento não pode ser perdido em caso de refresh/queda de conexão — persistir resposta a cada questão, não só ao final |
| Segurança (autenticação) | Senhas com hash BCrypt (spring-security-crypto, já em uso); JWT com expiração curta + refresh token — implementação completa deliberadamente adiada para o fim do desenvolvimento, para não bloquear testes de API durante a construção dos módulos; rate limiting em endpoints de autenticação e no chat do tutor (custo de LLM) a implementar junto com o JWT |
| Segurança (pagamentos) | Nenhum dado sensível de cartão trafega ou é armazenado pelo backend próprio — delegar ao gateway de pagamento (tokenização); validar webhooks do gateway com assinatura/segredo compartilhado |
| Privacidade (LGPD) | Consentimento explícito para exibição no ranking; dados de estudo não compartilhados sem opt-in; exclusão permanente de dados a pedido do usuário (RF01.6) |
| Observabilidade | Logs estruturados, métricas de uso por módulo (Actuator + Micrometer/Prometheus) |
| Manutenibilidade | Spring Modulith por domínio: questoes, usuarios, administradores, simulados, tutor-ia, legislacao, planejamento, ranking, gamificacao, anotacoes, assinatura, aquisicao. Cada módulo segue o padrão internal/ (entidades/repositórios package-private) + interface/porta pública + service/ (impl package-private) + dto/ (records públicos) + controller/ (REST público) |
| Custo | Monitorar custo de tokens do LLM por usuário/mês (o Tutor de IA é o item de maior custo variável); considerar limite de mensagens/dia no plano gratuito |
| Compatibilidade | Frontend responsivo (mobile-first), já que boa parte do estudo é feito no celular |
| Segurança de credenciais | Nunca expor chaves de service_role (ou equivalente com privilégio total) no bundle do frontend; usar exclusivamente chaves públicas/anônimas no client e manter chaves privilegiadas só no backend, fora do controle de versão (padrão recomendado de segurança web) |
| Schema | Flyway como dono exclusivo do schema (ddl-auto: validate) — nenhuma alteração de schema fora de migração versionada |

## 5. Arquitetura Proposta

### 5.1 Visão macro

```text
Angular SPA (frontend, planejado)
        │  REST/JSON + SSE (chat streaming)
        ▼
Spring Boot 4.1 / Spring Modulith 2.1 (backend)
        │
        ├── Módulo Usuários (auth, perfil, assinatura)              [implementado, parcial]
        ├── Módulo Administradores (isolado de Usuários)             [implementado]
        ├── Módulo Questões (ingestão de provas FGV, leitura)       [implementado, parcial]
        ├── Módulo Simulados (sessão, resultado, desempenho)         [schema pronto, código em validação]
        ├── Módulo Tutor-IA (langchain4j + RAG)                      [não iniciado]
        ├── Módulo Legislação                                        [não iniciado]
        ├── Módulo Planejamento                                      [não iniciado]
        ├── Módulo Ranking                                           [não iniciado]
        ├── Módulo Gamificação                                       [não iniciado]
        ├── Módulo Anotações                                         [não iniciado]
        ├── Módulo Aquisição (desafio sem cadastro)                  [não iniciado]
        ├── Módulo Assinatura/Pagamentos                             [não iniciado]
        │
        ├── PostgreSQL 18 (dados relacionais, banco_oab_dev local)
        ├── pgvector (extensão já habilitada via V3, para RAG)
        ├── Gateway de Pagamento (webhook + tokenização de cartão)
        └── LLM Provider (via langchain4j)