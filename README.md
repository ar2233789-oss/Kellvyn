# Kellvyn IA

Kellvyn IA é um aplicativo Android nativo em Kotlin/Jetpack Compose, em desenvolvimento para funcionar primeiro no próprio aparelho.

## Código implementado até agora

- Interface nativa preta/esmeralda alinhada à referência: cabeçalho compacto, dois indicadores, chat central, compositor em duas linhas com seletor Build/Conversa + motor, microfone/envio e seis abas inferiores AI, Navegador, Editor, Terminal, Git e Abas.
- Botão de copiar (ícone de folhas/páginas) embaixo das respostas do assistente.
- Entrada por ditado em português e leitura em voz alta, se os serviços de voz do Android estiverem disponíveis.
- Histórico local limitado e apagável.
- Memória explícita controlada pelo usuário: guardar, visualizar e apagar fatos.
- Adaptador inicial LiteRT-LM com dois slots locais: modelo leve de conversa (~374 MB) e Qwen2.5-Coder-1.5B quantizado (~1,12 GB), que podem ser alternados sem baixar novamente; a compatibilidade/desempenho dos modelos no Poco F6 ainda precisa de teste real.
- Modo online opcional para respostas do assistente, com endpoint HTTPS configurado pelo usuário.
- Agente conversacional com ciclo limitado de ferramentas: consulta projetos/arquivos, pesquisa na web, descobre repositórios públicos do GitHub, lista caminhos e lê arquivos por branch; pode importar até 30 arquivos textuais de um repositório público ao workspace após confirmação. A internet das ferramentas precisa estar autorizada. Pedidos de suporte ao terminal são diagnosticados sem tornar o terminal necessário para o chat. Para editar arquivos locais, pode aplicar patches exatos após confirmação, evitando reescrever o arquivo inteiro. Essa permissão é separada do motor de inferência: o modelo local continua local, mas a consulta/página solicitada é acessada externamente. Gravação, exclusão e comandos que alteram arquivos/Git pedem confirmação.
- Terminal manual opcional com atalho `! comando`, confirmação e limites de tempo/saída; dentro dele há um botão para enviar a saída recente ao agente principal pelo chat. O shell manual não é acessível ao agente automático.
- Construtor que gera arquivos de código pela seleção Build no chat, workspace privado, editor de arquivos, lista de abas locais, navegador externo sem WebView, terminal de workspace e Git local via JGit.

## Requisitos que não podem ser violados

- O app final deve ser Android nativo; não embutir páginas da web em WebView.
- A criação de APKs deve ocorrer localmente no telefone. Não usar GitHub Actions, tokens, Replit ou serviços de build na nuvem para compilar os projetos criados.
- O código gerado pode ser exportado em ZIP para inspeção, mas ZIP não é APK.
- A tela principal deve privilegiar o APK compilado como resultado, depois que o compilador local estiver implementado.
- Não fingir que recursos pendentes estão prontos.

## Limitações reais atuais

A IA local ainda precisa de teste com um modelo real no Poco F6. O modelo pequeno baixado é uma opção de entrada para tarefas básicas, não um modelo avançado de programação. A qualidade, RAM, calor e velocidade ainda não foram medidos no aparelho.

O agente do chat usa APIs diretas para arquivos/projetos e uma camada Git via JGit separada, sem depender da tela Terminal; alterações e importações exigem confirmação. O terminal manual é uma opção independente, com shell do usuário protegido por confirmação/limites, e oferece ajuda do mesmo agente usando o contexto fornecido pela pessoa. O construtor atual gera, valida, guarda, permite editar e exportar arquivos de código. Ele **ainda não compila projetos gerados em APK no próprio telefone**. A interface recém-alterada ainda precisa passar por build e teste; não há captura do aplicativo rodando que confirme o resultado. O próximo bloco central é uma toolchain de compilação Android local e compatível com o ambiente ARM64 do telefone, sem serviço remoto; ela ainda não está implementada ou validada.

O modo online opcional é separado da compilação. Se for configurado, a conversa/contexto vai ao endpoint HTTPS escolhido; a compilação de projetos não usará esse endpoint.

## Documentação
- [Especificação central do produto](docs/PRODUCT_SPEC.md)
- [Ferramentas do agente e permissões](docs/AGENT_TOOLS.md)
- [Motor de IA local](docs/LOCAL_AI.md)
- [Roadmap técnico](docs/ROADMAP.md)
- [Arquitetura de compilação local estilo mini Android Studio](docs/LOCAL_BUILD_ARCHITECTURE.md)

## Build do próprio Kellvyn durante o desenvolvimento
A compilação anterior do próprio app foi feita em ambiente de desenvolvimento e passou por verificações de pacote/assinatura. O fluxo de build em nuvem foi removido conforme o requisito de build local. Não há atualmente um APK novo validado para a revisão depois das últimas alterações.
