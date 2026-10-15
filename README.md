# Kellvyn IA

Kellvyn IA é um aplicativo Android nativo em Kotlin/Jetpack Compose, em desenvolvimento para funcionar primeiro no próprio aparelho.

## Código implementado até agora

- Interface nativa preta/esmeralda: tela única de chat, cabeçalho compacto e compositor inferior. O hambúrguer abre somente o histórico; Apps/APKs abre o catálogo; ⋮ abre configurações de interface e segurança, incluindo cor e efeito de chuva digital. Não há menu superior de ferramentas. A anexação de arquivos fica junto ao compositor.
- Botão de copiar (ícone de folhas/páginas) embaixo das respostas do assistente.
- O compositor é compacto e adaptável: campo de mensagem, estado da IA, ditado e envio. Os ícones principais agora são vetoriais desenhados no próprio app, com tamanho em dp; a validação visual final no Poco F6 continua pendente.
- Histórico local limitado e apagável; o núcleo considera até 32 mensagens recentes e aplica um orçamento dinâmico de contexto/tokens antes da inferência para preservar a solicitação atual sem ultrapassar o limite do modelo.
- Memória explícita controlada pelo usuário: guardar, visualizar e apagar fatos.
- Adaptador LiteRT-LM de modelo único: se o modelo não estiver embutido no APK, a preparação inicial baixa automaticamente a cópia pública verificada (aprox. 374 MB) e a guarda no armazenamento privado. Não há controle para trocar, selecionar ou excluir manualmente o modelo; depois da preparação automática única, a inferência ocorre localmente, sem enviar prompts para um provedor externo.
- O chat usa somente o motor local; não há provedor externo de IA ou tela de configuração de endpoint. As ferramentas de internet só são usadas quando a mensagem pede explicitamente uma pesquisa ou ação sobre um link.
- O agente principal trabalha diretamente pelo chat: consulta e altera arquivos do workspace, planeja tarefas, cria projetos, compila e apresenta diagnósticos. As respostas em streaming publicam o primeiro trecho assim que chega, e o chat mostra estado de processamento desde o envio. Pesquisa web/GitHub só é acionada quando a mensagem pede explicitamente esse acesso.
- O terminal usa a integração oficial do Termux: a pessoa pode abrir o Termux completo ou executar comandos no painel do app. O agente principal pode chamar o Termux quando a tarefa solicitada no chat exigir shell real — como scripts, dependências, testes ou diagnóstico de comandos — sem exigir que a pessoa mencione Termux; cada comando exige confirmação. Requer Termux instalado, permissão RUN_COMMAND concedida e allow-external-apps=true no Termux; o construtor e compilador usam APIs internas, não o terminal.
- O construtor é uma ferramenta interna do chat, sem tela, botão ou alternância de modo. O editor serve para guardar e consultar códigos, não atua como agente separado. Anexos podem ser texto/código, PDF pesquisável ou escaneado (OCR local limitado por páginas), DOCX, ODT ou imagem para rotulagem visual aproximada e OCR local. A imagem não é enviada ao modelo textual nem a servidores; a descrição visual é limitada às categorias ML Kit e ao texto extraído.
- O runtime nativo para bots compatíveis do Telegram foi adicionado ao código: token protegido pelo Android Keystore, comandos definidos por manifesto e conexão pela Bot API oficial com long polling, ativável pelo catálogo Apps. A integração ainda precisa de build e teste real no aparelho. Bots do WhatsApp Business continuam como código-fonte porque o recebimento de mensagens depende de webhook HTTPS público.

## Requisitos que não podem ser violados

- O app final deve ser Android nativo; não embutir páginas da web em WebView.
- A criação de APKs deve ocorrer localmente no telefone. Não usar GitHub Actions, tokens, Replit ou serviços de build na nuvem para compilar os projetos criados.
- Não gerar, oferecer nem entregar arquivo compactado como alternativa. A entrega é o APK compilado diretamente no chat principal.
- A tela principal é o chat único e deve privilegiar o APK compilado como resultado; não há alternância manual entre conversar e criar aplicativos.
- Não fingir que recursos pendentes estão prontos.

## Escopo do primeiro pipeline local

A IA local ainda precisa de teste com um modelo real no Poco F6. O modelo pequeno baixado é uma opção de entrada para tarefas básicas, não um modelo avançado de programação. A qualidade, RAM, calor e velocidade ainda não foram medidos no aparelho.

O agente do chat usa APIs diretas do workspace para criar/editar arquivos e inicia a compilação quando a tarefa pede. Não exige abrir outra ferramenta para criar, corrigir ou compilar. O terminal real Termux é acessível pelo catálogo Apps e pelas Configurações; o agente principal também pode acioná-lo para tarefas pedidas no chat que precisem de shell real, com confirmação por comando.

A conversa usa o modelo local integrado. Pesquisa web/GitHub só ocorre quando a mensagem pede explicitamente; usa a conexão que o Android já tem ativa, sem exigir configuração manual.

## Documentação
- [Especificação central do produto](docs/PRODUCT_SPEC.md)
- [Ferramentas do agente e permissões](docs/AGENT_TOOLS.md)
- [Motor de IA local](docs/LOCAL_AI.md)
- [Roadmap técnico](docs/ROADMAP.md)
- [Arquitetura de compilação local estilo mini Android Studio](docs/LOCAL_BUILD_ARCHITECTURE.md)

## Validação pendente

As rotas de PDF/DOCX/ODT, OCR e rotulagem visual, renderização SVG→PNG, sobreposição, recuperação do SDK e integração Termux receberam código; ainda precisam de build e teste Android reais. O repositório não contém APK novo validado.

## Build do próprio Kellvyn durante o desenvolvimento
Os workflows de build e teste em nuvem foram removidos. O Gradle Wrapper oficial 8.9 está incluído com SHA-256 da distribuição fixado para a transferência ser verificada antes de executar o build. Em um ambiente local com JDK 17, Android SDK 35 e conexão para baixar as dependências, use `./gradlew assembleDebug` (Windows: `gradlew.bat assembleDebug`); para os testes unitários, use `./gradlew testDebugUnitTest`. Essas tarefas executam no ambiente local e não enviam a compilação para a nuvem. Este commit não contém um APK novo validado: ainda falta executar o build, verificar pacote/assinatura e testar a instalação no Poco F6 antes de anunciar a distribuição como pronta.
