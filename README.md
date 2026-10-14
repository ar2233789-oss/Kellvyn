# Kellvyn IA

Kellvyn IA é um aplicativo Android nativo em Kotlin/Jetpack Compose, em desenvolvimento para funcionar primeiro no próprio aparelho.

## Código implementado até agora

- Interface nativa preta/esmeralda: tela principal dedicada ao chat, cabeçalho compacto, mensagens organizadas e compositor próximo ao rodapé. Não há barra inferior de abas; conversas ficam no botão ☰ e leitura de arquivos de texto/código, códigos guardados, Apps/APKs e memória ficam no menu superior. Pedidos de conversa, criação, correção e compilação começam no mesmo chat.
- Botão de copiar (ícone de folhas/páginas) embaixo das respostas do assistente.
- O compositor é compacto e contém somente campo de mensagem, estado real da IA e botão de envio; não mostra controles decorativos ou sem função.
- Histórico local limitado e apagável.
- Memória explícita controlada pelo usuário: guardar, visualizar e apagar fatos.
- Adaptador LiteRT-LM de modelo único: se o modelo não estiver embutido no APK, o app baixa uma vez a cópia pública verificada (aprox. 374 MB) e a guarda no armazenamento privado. Depois a inferência ocorre localmente, sem enviar prompts para um provedor externo; não há seleção de modelos nem alternância de modos.
- O chat usa somente o motor local; não há provedor externo de IA ou tela de configuração de endpoint. As ferramentas de internet só são usadas quando a mensagem pede explicitamente uma pesquisa ou ação sobre um link.
- O agente principal trabalha diretamente pelo chat: consulta e altera arquivos do workspace, planeja tarefas, cria projetos, compila e apresenta diagnósticos. Pesquisa web/GitHub só é acionada quando a mensagem pede explicitamente esse acesso; usa a conexão já ativa do Android sem etapas manuais de configuração.
- Não existe aba ou ícone independente de terminal. Termux só pode entrar no fluxo após um pedido explícito do usuário no chat principal; nunca inicia automaticamente.
- O construtor é uma ferramenta interna do chat, sem tela, botão ou alternância de modo. O editor serve para guardar e consultar códigos, não atua como agente separado. O menu de aplicativos/ferramentas deve reunir leitura de arquivos, voz e demais funções solicitadas. Pedidos de criação e correção começam no chat principal.

## Requisitos que não podem ser violados

- O app final deve ser Android nativo; não embutir páginas da web em WebView.
- A criação de APKs deve ocorrer localmente no telefone. Não usar GitHub Actions, tokens, Replit ou serviços de build na nuvem para compilar os projetos criados.
- Não gerar, oferecer nem entregar arquivo compactado como alternativa. A entrega é o APK compilado diretamente no chat principal.
- A tela principal é o chat único e deve privilegiar o APK compilado como resultado; não há alternância manual entre conversar e criar aplicativos.
- Não fingir que recursos pendentes estão prontos.

## Escopo do primeiro pipeline local

A IA local ainda precisa de teste com um modelo real no Poco F6. O modelo pequeno baixado é uma opção de entrada para tarefas básicas, não um modelo avançado de programação. A qualidade, RAM, calor e velocidade ainda não foram medidos no aparelho.

O agente do chat usa APIs diretas do workspace para criar/editar arquivos e inicia a compilação quando a tarefa pede. Não exige abrir outra ferramenta para criar, corrigir ou compilar. Termux só pode ser usado após pedido explícito no chat principal.

A conversa usa o modelo local integrado. Pesquisa web/GitHub só ocorre quando a mensagem pede explicitamente; usa a conexão que o Android já tem ativa, sem exigir configuração manual.

## Documentação
- [Especificação central do produto](docs/PRODUCT_SPEC.md)
- [Ferramentas do agente e permissões](docs/AGENT_TOOLS.md)
- [Motor de IA local](docs/LOCAL_AI.md)
- [Roadmap técnico](docs/ROADMAP.md)
- [Arquitetura de compilação local estilo mini Android Studio](docs/LOCAL_BUILD_ARCHITECTURE.md)

## Build do próprio Kellvyn durante o desenvolvimento
Os workflows de build e teste em nuvem foram removidos. O repositório não contém um APK novo validado para esta revisão. Não anunciar a distribuição como pronta até fornecer o modelo ausente, executar a compilação local do aplicativo anfitrião e verificar pacote/assinatura.
