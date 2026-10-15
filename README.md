# Kellvyn IA

Kellvyn IA é um aplicativo Android nativo em Kotlin/Jetpack Compose, em desenvolvimento para funcionar primeiro no próprio aparelho.

## Código implementado até agora

- Interface nativa preta/esmeralda: tela única de chat, cabeçalho compacto e compositor inferior. O hambúrguer abre somente o histórico; Apps/APKs abre o catálogo; ⋮ abre configurações de interface e segurança, incluindo cor e efeito de chuva digital. Não há menu superior de ferramentas. A anexação de arquivos fica junto ao compositor.
- Botão de copiar (ícone de folhas/páginas) embaixo das respostas do assistente.
- O compositor é compacto e adaptável: campo de mensagem, estado da IA, ditado e envio. Os ícones principais agora são vetoriais desenhados no próprio app, com tamanho em dp; a validação visual final no Poco F6 continua pendente.
- Histórico local limitado e apagável; a IA considera até 12 mensagens recentes e prioriza a solicitação atual na conversa.
- Memória explícita controlada pelo usuário: guardar, visualizar e apagar fatos.
- Adaptador LiteRT-LM de modelo único Gemma 4 E2B multimodal: se o modelo não estiver embutido no APK, a preparação inicial baixa automaticamente a cópia pública verificada (aprox. 2,6 GB) e a guarda no armazenamento privado. Não há controle para trocar ou selecionar modelos; depois da preparação inicial, a inferência ocorre no aparelho.
- Existe uma única Kellvyn IA: a inferência conversacional é local e as ferramentas integradas podem usar a conexão Wi-Fi/dados do próprio celular quando a tarefa exigir informações atuais/externas, GitHub ou leitura/ação sobre um link. Não há segunda IA online, seletor de modo/provedor ou envio do prompt a um endpoint de IA.
- O agente principal trabalha diretamente pelo chat: consulta e altera arquivos do workspace, planeja tarefas, cria projetos, compila e apresenta diagnósticos. As respostas em streaming publicam o primeiro trecho assim que chega, e o chat mostra estado de processamento desde o envio. A mesma IA usa a conexão ativa do celular quando a intenção da tarefa exigir pesquisa/consulta externa, sem precisar da frase literal “pesquise na internet”.
- O Termux é um aplicativo independente. A pessoa pode abrir o Termux completo pelo painel manual opcional; a conexão da IA via RUN_COMMAND só é usada quando a pessoa pede ajuda com Termux explicitamente no chat principal. Cada comando iniciado pela IA exige confirmação. Requer Termux instalado, permissão RUN_COMMAND concedida e allow-external-apps=true no próprio Termux; o construtor e o compilador usam APIs internas e não dependem do terminal.
- O construtor é uma ferramenta interna do chat, sem tela, botão ou alternância de modo. O editor serve para guardar e consultar códigos, não atua como agente separado. Anexos podem ser texto/código, PDF pesquisável ou escaneado (OCR local limitado por páginas), DOCX, ODT ou imagem. Ao anexar uma imagem, o app preserva o arquivo original no cache privado e envia seus pixels ao modelo local multimodal junto com a mensagem; não envia a imagem a um provedor externo.
- O runtime nativo para bots compatíveis do Telegram foi adicionado ao código: token protegido pelo Android Keystore, comandos definidos por manifesto e conexão pela Bot API oficial com long polling, ativável pelo catálogo Apps. A integração ainda precisa de build e teste real no aparelho. Bots do WhatsApp Business continuam como código-fonte porque o recebimento de mensagens depende de webhook HTTPS público.

## Requisitos que não podem ser violados

- O app final deve ser Android nativo; não embutir páginas da web em WebView.
- A criação de APKs deve ocorrer localmente no telefone. Não usar Replit nem serviços de build na nuvem para compilar os projetos criados.
- Não gerar, oferecer nem entregar arquivo compactado como alternativa. A entrega é o APK compilado diretamente no chat principal.
- A tela principal é o chat único e deve privilegiar o APK compilado como resultado; não há alternância manual entre conversar e criar aplicativos.
- Não fingir que recursos pendentes estão prontos.

## Escopo do primeiro pipeline local

O modelo multimodal Gemma 4 E2B e o fluxo de anexos ainda precisam de build e teste no Poco F6. O tempo de inicialização, consumo de RAM, aquecimento e velocidade no aparelho ainda não foram medidos.

O agente do chat usa APIs diretas do workspace para criar/editar arquivos e inicia a compilação quando a tarefa pede. Não exige abrir outra ferramenta para criar, corrigir ou compilar. O Termux permanece independente e pode ser aberto pelo catálogo Apps e pelas Configurações; a conexão da IA com o Termux é acionada somente quando a pessoa pede ajuda com Termux explicitamente no chat principal, com confirmação por comando.

A conversa usa o modelo local integrado. Pesquisa web/GitHub só ocorre quando a mensagem pede explicitamente; usa a conexão que o Android já tem ativa, sem exigir configuração manual.

## Documentação
- [Especificação central do produto](docs/PRODUCT_SPEC.md)
- [Ferramentas do agente e permissões](docs/AGENT_TOOLS.md)
- [Motor de IA local](docs/LOCAL_AI.md)
- [Roadmap técnico](docs/ROADMAP.md)
- [Arquitetura de compilação local estilo mini Android Studio](docs/LOCAL_BUILD_ARCHITECTURE.md)

## Validação pendente

As rotas de PDF/DOCX/ODT, envio de imagem ao modelo multimodal, renderização SVG→PNG, sobreposição, recuperação do SDK e integração Termux ainda precisam de build e teste Android reais. O repositório não contém APK novo validado.

## Build do próprio Kellvyn durante o desenvolvimento
Os workflows de build e teste em nuvem foram removidos. O Gradle Wrapper oficial 8.9 está incluído com SHA-256 da distribuição fixado para a transferência ser verificada antes de executar o build. Em um ambiente local com JDK 17, Android SDK 35 e conexão para baixar as dependências, use `./gradlew assembleDebug` (Windows: `gradlew.bat assembleDebug`); para os testes unitários, use `./gradlew testDebugUnitTest`. Essas tarefas executam no ambiente local e não enviam a compilação para a nuvem. Este commit não contém um APK novo validado: ainda falta executar o build, verificar pacote/assinatura e testar a instalação no Poco F6 antes de anunciar a distribuição como pronta.
