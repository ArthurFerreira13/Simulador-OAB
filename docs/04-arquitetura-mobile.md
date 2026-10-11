# Simulador-OAB — Arquitetura do aplicativo mobile

## 1. Decisão de plataforma

O produto terá três clientes oficiais:

| Cliente | Tecnologia | Objetivo |
|---|---|---|
| Web | Angular | Estudo completo, administração e experiências de tela ampla. |
| Mobile | Expo + React Native + TypeScript | Estudo diário, questões rápidas, simulados, notificações e revisão offline controlada. |
| API | Spring Boot + Spring Modulith | Fonte única de regras de negócio, autenticação, dados e contratos. |

O aplicativo mobile não terá acesso direto ao PostgreSQL. Toda operação passará pela API REST versionada. O contrato OpenAPI será a fonte para gerar tipos TypeScript e reduzir divergência entre Angular, mobile e backend.

## 2. Escopo do primeiro aplicativo

O MVP mobile deve priorizar o fluxo de estudo recorrente:

1. login, cadastro e recuperação de acesso;
2. dashboard com foco do dia, streak e progresso;
3. resolução de questões por disciplina;
4. resposta com feedback e comentário;
5. favoritos e revisão de erros;
6. simulado com persistência de respostas;
7. resultado e desempenho por disciplina;
8. plano de estudos e tarefas do dia;
9. notificações de lembrete;
10. configurações e exclusão de conta.

Legislação completa, tutor com streaming, ranking detalhado, gamificação avançada e pagamentos podem ser adicionados após a estabilização do núcleo de estudo.

## 3. Estrutura recomendada do projeto mobile

```text
apps/mobile
├── app/                         # Expo Router: telas e rotas
│   ├── (auth)/
│   ├── (tabs)/
│   │   ├── index.tsx            # dashboard
│   │   ├── questoes.tsx
│   │   ├── simulados.tsx
│   │   ├── plano.tsx
│   │   └── perfil.tsx
│   ├── questoes/[id].tsx
│   ├── simulados/[id].tsx
│   └── configuracoes/
├── src/
│   ├── api/                     # cliente HTTP gerado do OpenAPI
│   ├── auth/                    # sessão, refresh e proteção de rotas
│   ├── features/                # questões, simulados, plano, perfil
│   ├── components/              # componentes visuais compartilhados
│   ├── storage/                 # SecureStore e cache local
│   ├── notifications/           # push token e preferências
│   └── telemetry/               # erros, métricas e eventos mínimos
├── assets/
├── app.json
└── package.json
```

A navegação deve usar Expo Router. Tokens e dados sensíveis devem usar `expo-secure-store`, nunca `AsyncStorage`. O cache local de questões pode usar SQLite ou armazenamento estruturado, mas deve conter somente dados autorizados e possuir política de expiração.

## 4. Autenticação mobile

O mobile utilizará OAuth 2.1/OIDC ou login próprio com JWT. O fluxo recomendado é:

1. o app autentica o usuário;
2. a API devolve access token curto e refresh token rotativo;
3. o access token fica apenas em memória ou SecureStore protegido;
4. o refresh token fica no SecureStore;
5. ao receber `401`, o cliente tenta refresh uma única vez;
6. se o refresh falhar, remove a sessão e direciona ao login.

O backend não deve receber senha em query string. Os endpoints atuais que usam `email` e `senha` no corpo ou na query são temporários e precisam ser substituídos por autenticação baseada em token antes da publicação do aplicativo.

## 5. Sincronização e modo offline

O offline será **controlado**, não uma cópia completa do banco. O app poderá armazenar:

- questões baixadas explicitamente pelo usuário;
- alternativas sem gabarito antes da resposta;
- respostas ainda não sincronizadas;
- tarefas do plano e favoritos;
- último dashboard consultado.

Cada operação offline deve ter `operationId`, `createdAt`, versão do recurso e estado `PENDING`, `SYNCED` ou `CONFLICT`. A fila local será enviada quando a conexão voltar. Respostas de simulado devem ser idempotentes no backend por sessão, questão e `Idempotency-Key`.

Conflitos devem ser resolvidos assim:

| Operação | Resolução |
|---|---|
| Resposta de simulado | A primeira resposta aceita pelo servidor vence; repetição retorna o mesmo resultado. |
| Favorito | Operação de conjunto idempotente. |
| Tarefa de estudo | Estado mais recente do servidor, salvo ação explícita de reconciliação. |
| Anotação | Não sobrescrever silenciosamente; manter versão e solicitar resolução. |
| Perfil | Servidor retorna conflito de versão quando houver edição concorrente. |

O simulado em andamento deve persistir localmente como proteção contra queda de conexão, mas a conclusão oficial depende da confirmação do servidor.

## 6. Notificações

O app deverá registrar o push token por usuário e dispositivo em endpoint próprio. O backend será responsável por preferências, horário permitido e remoção de tokens inválidos. Tipos iniciais:

- lembrete de tarefa do plano;
- aviso de simulado retomável;
- conclusão de geração de plano ou relatório;
- alerta de streak, se habilitado pelo usuário.

Notificações não devem conter conteúdo jurídico sensível, resposta de questão ou informação que revele dados de estudo na tela bloqueada.

## 7. API para clientes móveis

A API deve ser versionada em `/api/v1`. O mobile utilizará paginação, `ETag`/`If-None-Match` para catálogos quando disponível e respostas com campos estáveis. Erros seguem o formato:

```json
{
  "codigo": "CONFLITO_DE_ESTADO",
  "mensagem": "A sessão já foi finalizada.",
  "correlationId": "01J...",
  "detalhes": []
}
```

Endpoints adicionais recomendados:

| Método | Endpoint | Uso |
|---|---|---|
| `POST` | `/api/v1/auth/login` | Login e emissão de tokens. |
| `POST` | `/api/v1/auth/refresh` | Rotação do refresh token. |
| `POST` | `/api/v1/auth/logout` | Revogação do refresh token. |
| `GET` | `/api/v1/me/dashboard` | Dashboard resumido para mobile. |
| `GET` | `/api/v1/me/sync?since=...` | Alterações desde o cursor informado. |
| `POST` | `/api/v1/me/sync` | Envio de operações offline idempotentes. |
| `POST` | `/api/v1/me/devices` | Registro de push token. |
| `DELETE` | `/api/v1/me/devices/{id}` | Remoção de dispositivo. |
| `GET` | `/api/v1/questoes` | Busca paginada de questões. |
| `PUT` | `/api/v1/simulados/{id}/questoes/{questaoId}/resposta` | Resposta idempotente. |

## 8. Segurança e privacidade

O app deve bloquear captura de tela apenas em telas que exibam dados realmente sensíveis, sem impedir o estudo de conteúdo público por padrão. Logs do cliente devem remover tokens, senha, conteúdo do tutor e dados pessoais. O usuário deve conseguir revogar sessões e remover dispositivos autorizados.

A exclusão da conta precisa invalidar tokens, apagar ou anonimizar dados conforme a política definida e remover tokens de notificação. A coleta de analytics deve ser opt-in quando envolver comportamento de estudo individual.

## 9. Distribuição e operação

A distribuição inicial será feita por builds de desenvolvimento e, após estabilização, por canais oficiais Android e iOS. O pipeline deve produzir builds assinados fora do código-fonte, usar ambientes separados e não incluir chaves de backend no bundle. Variáveis públicas do app podem conter apenas URL pública e identificadores não secretos.

O aplicativo deve ter feature flags para ativar gradualmente tutor, notificações, offline e pagamentos. Falhas de uma feature opcional não podem impedir a resolução de questões ou a consulta de resultados já persistidos.

## 10. Critérios de aceite mobile

1. O app autentica sem armazenar senha ou token em texto puro.
2. Uma expiração do access token executa refresh uma única vez e preserva a ação original quando possível.
3. Uma resposta feita sem conexão aparece como pendente e é sincronizada depois.
4. Repetir a sincronização não duplica resposta nem favorito.
5. O app funciona com catálogo paginado e não baixa 1.520 questões automaticamente.
6. Uma queda de rede durante simulado não apaga a sessão local.
7. A conclusão do simulado só é exibida como oficial após confirmação da API.
8. O usuário consegue remover o dispositivo e excluir a conta.
9. Push tokens inválidos são removidos pelo backend.
10. O cliente mobile e o backend são verificados contra o mesmo contrato OpenAPI.
