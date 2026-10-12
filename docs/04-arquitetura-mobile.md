# Simulador-OAB — Arquitetura do aplicativo mobile (Kotlin)

| Campo | Valor |
|---|---|
| **Documento** | 04 — Arquitetura do Aplicativo Mobile |
| **Versão** | 6.3.1 (substitui a versão Expo/React Native) |
| **Data** | 12/10/2026 |
| **Responsável** | Arthur |
| **Status** | Em revisão |
| **Relacionados** | `01-requisitos-simulador-oab.md` (RF13, RF15, RNF18–RNF20) · `02-arquitetura-simulador-oab.md` (ADR-012 a ADR-016) · `03-status-roadmap-riscos.md` (D15–D18, R17, Mobile 1–2) |

> **Escopo:** define o cliente móvel. Regras de negócio em `01`; contratos em `02`; estado e decisões em `03`. Seções marcadas como **Proposto** são redação nova, ainda sem validação.

## 1. Decisão de plataforma

Três clientes oficiais sobre a mesma API:

| Cliente | Tecnologia | Objetivo |
|---|---|---|
| Web | Angular | Estudo completo, administração, telas amplas |
| Mobile | Kotlin (Android primeiro; iOS conforme D16) | Estudo diário, questões rápidas, simulados, notificações e offline controlado |
| API | Spring Boot + Spring Modulith | Regras, autenticação, dados e contratos |

O app nunca acessa o PostgreSQL. Toda operação passa pela API REST versionada. O contrato OpenAPI é a fonte para gerar o cliente Kotlin (ADR-013).

**Plataforma Kotlin (D16):** Android nativo (Kotlin + Jetpack Compose), com código organizado para futura extração via Kotlin Multiplatform caso o iOS seja priorizado.

## 2. Escopo do primeiro aplicativo

Fluxo de estudo recorrente:

1. Login, cadastro, recuperação de acesso e encerramento de conta.
2. Dashboard com foco do dia e progresso (o streak aparece quando o módulo de gamificação existir, MVP 8).
3. Resolução de questões por disciplina, com feedback e comentário.
4. Favoritos e revisão de erros.
5. Simulado com persistência de respostas, resultado e desempenho por disciplina.
6. Plano de estudos e tarefas do dia.
7. Notificações de lembrete.
8. Configurações, dispositivos e exclusão de conta.

**Marcos:** Mobile 1 entrega o núcleo (itens 1, 3, 5 e 8, sem recuperação de acesso até a Fase final). Mobile 2A traz offline, sincronização e push base; 2B, favoritos e dashboard (depende do MVP 4); 2C, plano e lembretes (depende do MVP 6).

**Depois da estabilização do núcleo:** tutor com streaming, legislação completa, ranking detalhado, gamificação avançada e pagamentos (D18, R15).

**Estado de acesso:** o app exibe o estado (TRIAL, GRATUITO, PAGO) devolvido pela API e nunca decide acesso localmente; o bloqueio real é do servidor (RF13.10, RNF21). Em plano gratuito, as telas de recursos restritos mostram o aviso e a chamada é recusada pela API.

## 3. Arquitetura do aplicativo

**Padrão:** MVVM com fluxo unidirecional de estado, repositórios e camadas `ui` → `domain` → `data`. O banco local é a fonte única de verdade da interface (*offline-first*).

| Camada | Escolha | Observação |
|---|---|---|
| UI | Jetpack Compose + Navigation Compose | Estado exposto por `StateFlow` |
| Assíncrono | Coroutines + Flow | — |
| Injeção de dependência | Hilt (Android) ou Koin (se KMP) | Decidir junto com D16 |
| Rede | Retrofit + OkHttp, ou Ktor Client (KMP) | `kotlinx.serialization` |
| Persistência local | Room (ou SQLDelight se KMP) | Somente dados autorizados, com expiração |
| Preferências | DataStore | Valores sensíveis cifrados (§8) |
| Segundo plano | WorkManager | Fila de sincronização e reenvio |
| Push | Firebase Cloud Messaging | — |

**Estrutura de módulos Gradle (Proposto):**

```text
simulador-oab-android
├── app/                       # Application, navegação raiz, injeção, flags
├── build-logic/               # convention plugins (versões e regras comuns)
├── gradle/libs.versions.toml  # catálogo de versões
├── core/
│   ├── model/                 # entidades e DTOs de domínio (Kotlin puro)
│   ├── domain/                # casos de uso e contratos de repositório (Kotlin puro)
│   ├── network/               # cliente gerado do OpenAPI, interceptors, erro padrão
│   ├── database/              # Room: entidades, DAOs, fila de operações
│   ├── datastore/             # preferências e sessão cifradas
│   ├── designsystem/          # tema e componentes Compose
│   └── testing/               # fakes e utilitários de teste
├── data/                      # implementação dos repositórios (rede + banco) e sync
└── feature/
    ├── auth/
    ├── questoes/
    ├── simulados/
    ├── dashboard/
    ├── plano/
    ├── configuracoes/         # perfil, dispositivos, exclusão de conta
    └── notificacoes/
```

Regras de dependência, alinhadas aos princípios de arquitetura modular de `02`:

- `feature:*` depende de `core:domain`, `core:model` e `core:designsystem`; **nunca de outra `feature:*`**. A navegação entre features passa por rotas expostas pelo `app`.
- `core:model` e `core:domain` não importam nada de Android, para permitir a extração via KMP (D16).
- `data` implementa os contratos de `core:domain`; as features não conhecem Room nem Retrofit.
- Cada feature contém UI, ViewModel e testes próprios, e pode ser removida ou desligada por *feature flag* (§9) sem afetar o núcleo de estudo.

## 4. Autenticação mobile

O mobile usa login próprio com JWT (D15; OAuth/OIDC fica como evolução). Fluxo alvo:

1. O app autentica o usuário.
2. A API devolve access token curto e refresh token rotativo.
3. O access token fica apenas em memória.
4. O refresh token fica em armazenamento cifrado com chave do Android Keystore (§8).
5. Ao receber `401`, o cliente tenta refresh uma única vez e repete a chamada original.
6. Se o refresh falhar, remove a sessão e direciona ao login.

**Situação atual:** a API já emite JWT de acesso (RF01.2a), sem refresh nem revogação (RF01.2b, fase final). Os passos 2, 4, 5 e 6 dependem de RF01.2b (ADR-016). O backend não deve receber senha em *query string*; os endpoints que ainda usam e-mail e senha fora do corpo são temporários e precisam ser substituídos antes da publicação do app (R05).

## 5. Sincronização e modo offline

O offline é **controlado**, não uma cópia completa do banco. O app pode armazenar:

- questões baixadas explicitamente pelo usuário;
- alternativas sem gabarito antes da resposta;
- respostas ainda não sincronizadas;
- tarefas do plano e favoritos;
- último dashboard consultado.

Cada operação offline tem `operationId`, `createdAt`, versão do recurso e estado `PENDING`, `SYNCED` ou `CONFLICT`. A fila local é enviada pelo WorkManager quando a conexão volta. Respostas de simulado são idempotentes no backend por sessão, questão e `Idempotency-Key` (ADR-014).

| Operação | Resolução |
|---|---|
| Resposta de simulado | A primeira resposta aceita pelo servidor vence; repetição retorna o mesmo resultado |
| Favorito | Operação de conjunto idempotente |
| Tarefa de estudo | Estado mais recente do servidor, salvo ação explícita de reconciliação |
| Anotação | Não sobrescrever silenciosamente; manter versão e solicitar resolução |
| Perfil | Servidor retorna conflito de versão quando houver edição concorrente |

O simulado em andamento persiste localmente como proteção contra queda de conexão, mas a conclusão oficial depende da confirmação do servidor. Limites do plano gratuito (1 simulado por dia) são verificados pelo servidor ao iniciar a sessão; iniciar offline não contorna o limite.

## 6. Notificações

O app registra o push token por usuário e dispositivo em endpoint próprio. O backend (módulo `notificacoes`) cuida de preferências, horário permitido e remoção de tokens inválidos, enviando via FCM atrás de uma porta substituível (ADR-015). Tipos iniciais:

- lembrete de tarefa do plano;
- aviso de simulado retomável;
- conclusão de geração de plano ou relatório;
- alerta de streak, se habilitado pelo usuário.

Notificações não contêm conteúdo jurídico sensível, resposta de questão nem informação que revele dados de estudo na tela bloqueada (RN19). Falha do FCM não afeta o estudo.

## 7. API para clientes móveis

A API é versionada em `/api/v1`. O mobile usa paginação, `ETag`/`If-None-Match` para catálogos quando disponível e respostas com campos estáveis. Erros seguem o formato abaixo; o `correlationId` ainda precisa ser adicionado por filtro e no `ErroDTO` (R20):

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
| `POST` | `/api/v1/auth/login` | Login e emissão de tokens |
| `POST` | `/api/v1/auth/refresh` | Rotação do refresh token (RF01.2b) |
| `POST` | `/api/v1/auth/logout` | Revogação do refresh token (RF01.2b) |
| `GET` | `/api/v1/me/dashboard` | Dashboard resumido para mobile |
| `GET` | `/api/v1/me/acesso` | Estado de acesso, dias restantes do trial e limites do dia *(proposto)* |
| `GET` | `/api/v1/me/sync?since=...` | Alterações desde o cursor informado; atendido pelo módulo `sincronizacao` (`02`, §4.1) |
| `POST` | `/api/v1/me/sync` | Envio de operações offline idempotentes |
| `POST` | `/api/v1/me/devices` | Registro de push token |
| `DELETE` | `/api/v1/me/devices/{id}` | Remoção de dispositivo |
| `GET` | `/api/v1/questoes` | Busca paginada de questões |
| `PUT` | `/api/v1/simulados/{id}/questoes/{questaoId}/resposta` | Resposta idempotente |

## 8. Segurança e privacidade

*(Proposto)* Segredos locais (refresh token e preferências sensíveis) ficam cifrados com chave do Android Keystore, por exemplo via Tink sobre DataStore; nunca em texto puro nem em `SharedPreferences` comum. O backup automático do Android não inclui esses dados.

O app bloqueia captura de tela (`FLAG_SECURE`) apenas em telas que exibam dados realmente sensíveis, sem impedir o estudo de conteúdo público por padrão. Logs do cliente removem tokens, senha, conteúdo do tutor e dados pessoais. O usuário consegue revogar sessões e remover dispositivos autorizados.

A exclusão da conta invalida tokens, apaga ou anonimiza dados conforme a política definida (evento `UsuarioExcluido`, `02`) e remove tokens de notificação. O app apaga o banco local e a sessão ao concluir a exclusão. A coleta de analytics é opt-in quando envolver comportamento de estudo individual (RNF20).

## 9. Distribuição e operação

A distribuição inicial é feita pelo Google Play Console (confirmado), começando por trilha de teste interno e, após estabilização, produção. A publicação em produção só ocorre depois da Fase final de segurança (R05). O pipeline produz builds assinados fora do código-fonte (*Play App Signing*), usa ambientes separados e não inclui chaves de backend no bundle. Variáveis públicas do app contêm apenas URL pública e identificadores não secretos.

Versão mínima de Android e política de atualização estão pendentes (D17); quando a API sinalizar versão incompatível, o app exibe a tela de atualização obrigatória sem perder operações pendentes (RF13.11). Cobrança dentro do app depende de D18 e de R15.

O app tem *feature flags* para ativar gradualmente tutor, notificações, offline e pagamentos. Falhas de uma feature opcional não podem impedir a resolução de questões nem a consulta de resultados já persistidos.

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
11. *(Proposto)* O app exibe o estado de acesso vindo da API e, em plano gratuito, a API recusa recursos restritos mesmo se o app for adulterado.
12. *(Proposto)* Nenhum módulo `feature:*` depende de outro `feature:*`, verificado no build.
