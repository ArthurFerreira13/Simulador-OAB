# Simulador-OAB — Entrega de arquitetura

Esta pasta contém o desenho alinhado ao projeto real extraído de `ordem.rar`.

## Entregáveis

- [`docs/01-arquitetura-modular.md`](docs/01-arquitetura-modular.md): módulos atuais, dependências permitidas, regras Spring Modulith e roadmap.
- [`docs/02-modelo-dados.md`](docs/02-modelo-dados.md): modelo relacional efetivo V1–V9 e modelo alvo incremental.
- [`db/V10__extensoes_produto.sql`](db/V10__extensoes_produto.sql): migração compatível com tabelas legadas, para revisão antes de entrar no projeto.
- [`api/openapi.yaml`](api/openapi.yaml): endpoints atuais e contratos REST planejados.
- [`diagrams/modulos.mmd`](diagrams/modulos.mmd): fonte Mermaid do diagrama de dependências.
- [`diagrams/modulos.png`](diagrams/modulos.png): renderização do diagrama.

## Como aplicar no projeto

1. Copiar a migração V10 para `src/main/resources/db/migration` somente após reconciliar sua numeração com o branch atual.
2. Não executar a V10 automaticamente em produção antes de validar o schema em banco limpo e banco restaurado.
3. Adicionar o teste `ApplicationModules.of(OrdemApplication.class).verify()` ao conjunto de testes arquiteturais.
4. Criar as interfaces públicas nomeadas antes de permitir que novos módulos consumam serviços internos.
5. Substituir credenciais em query string e corpo de endpoints operacionais por JWT antes de expor a API publicamente.
6. Gerar clientes ou documentação interativa a partir de `api/openapi.yaml`, mantendo o contrato versionado.

## Divergências importantes encontradas

O blueprint inicial usava UUID e nomes de tabelas no singular. O projeto real usa IDs `BIGINT`/`INTEGER` e tabelas legadas no plural. Os artefatos desta pasta foram corrigidos para respeitar o estado real. A migração V10 adiciona apenas extensões e não recria o núcleo existente.

- [`docs/03-arquitetura-mobile.md`](docs/03-arquitetura-mobile.md): plataforma Expo/React Native, autenticação, offline, sincronização, push e critérios de aceite.
- [`docs/04-auditoria-estado-atual.md`](docs/04-auditoria-estado-atual.md): comparação do `ordem.rar` atual com a arquitetura e plano de integração mobile.
