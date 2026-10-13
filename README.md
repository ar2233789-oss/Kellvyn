# Kellvyn IA

Kellvyn IA é um aplicativo Android nativo em Kotlin/Jetpack Compose, em desenvolvimento para funcionar primeiro no próprio aparelho.

## Código implementado até agora

- Interface de chat nativa, tema escuro e detalhes azul neon.
- Botão de copiar (ícone de folhas/páginas) embaixo das respostas do assistente.
- Entrada por ditado em português e leitura em voz alta, se os serviços de voz do Android estiverem disponíveis.
- Histórico local limitado e apagável.
- Memória explícita controlada pelo usuário: guardar, visualizar e apagar fatos.
- Adaptador inicial LiteRT-LM, importação de modelo compatível e fluxo de download verificado de um modelo pequeno.
- Modo online opcional para respostas do assistente, com endpoint HTTPS configurado pelo usuário.
- Construtor que gera arquivos de código, workspace privado, editor de arquivos, exportação ZIP secundária e console limitado ao workspace.

## Requisitos que não podem ser violados

- O app final deve ser Android nativo; não embutir páginas da web em WebView.
- A criação de APKs deve ocorrer localmente no telefone. Não usar GitHub Actions, tokens, Replit ou serviços de build na nuvem para compilar os projetos criados.
- O código gerado pode ser exportado em ZIP para inspeção, mas ZIP não é APK.
- A tela principal deve privilegiar o APK compilado como resultado, depois que o compilador local estiver implementado.
- Não fingir que recursos pendentes estão prontos.

## Limitações reais atuais

A IA local ainda precisa de teste com um modelo real no Poco F6. O modelo pequeno baixado é uma opção de entrada para tarefas básicas, não um modelo avançado de programação. A qualidade, RAM, calor e velocidade ainda não foram medidos no aparelho.

O construtor atual gera, valida, guarda, permite editar e exportar arquivos de código, mas **ainda não compila projetos gerados em APK no próprio telefone**. O próximo bloco central é uma toolchain de compilação Android local e compatível com o ambiente ARM64 do telefone, sem serviço remoto; ela ainda não está implementada ou validada.

O modo online opcional é separado da compilação. Se for configurado, a conversa/contexto vai ao endpoint HTTPS escolhido; a compilação de projetos não usará esse endpoint.

## Documentação
- [Especificação central do produto](docs/PRODUCT_SPEC.md)
- [Motor de IA local](docs/LOCAL_AI.md)
- [Roadmap técnico](docs/ROADMAP.md)

## Build do próprio Kellvyn durante o desenvolvimento
A compilação anterior do próprio app foi feita em ambiente de desenvolvimento e passou por verificações de pacote/assinatura. O fluxo de build em nuvem foi removido conforme o requisito de build local. Não há atualmente um APK novo validado para a revisão depois das últimas alterações.
