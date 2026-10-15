# Especificação central — Kellvyn IA

Este documento reúne os requisitos do produto para manter a direção do projeto consistente.

## Identidade e objetivo
- Nome: **Kellvyn IA**. Não usar Brian/Brain como nome e não apresentar o app como produto oficial da OpenAI.
- Assistente Android nativo com IA real, planejamento, geração/revisão de código e, como objetivo central, capacidade de criar aplicativos instaláveis.
- A imagem verde-esmeralda enviada pelo usuário é a referência de paleta e composição compacta: fundo escuro, brilho verde-esmeralda/turquesa, barra superior, conversa central e compositor inferior. A clarificação posterior do usuário prevalece para navegação: chat único, menus no topo e nenhuma barra de abas inferior. Não usar azul-neon dominante.
- A montagem posterior de oito telas gerada pelo assistente serve **somente como inventário de funções desejadas** (projetos, chat com plano, editor, console, resultado de APK, jogos e configurações); sua aparência azul e seus resultados ilustrativos não devem ser copiados como se fossem os requisitos visuais ou recursos já existentes.
- Nenhuma imagem de conceito comprova que uma função está implementada. Só mostrar sucesso, progresso de compilação, tamanho de APK ou aprendizado de jogo quando a função real tiver sido implementada e executada.
- Nada de demo disfarçada: controles, status e respostas precisam corresponder a funções reais.

## Chat principal e navegação da interface
- O construtor é uma ferramenta interna do agente e não pode aparecer como tela, botão ou modo manual de Build. O usuário descreve o que quer no chat; a IA decide quando criar um projeto, editar arquivos ou iniciar a compilação local.
- Pedidos claros para criar aplicativos ou projetos são encaminhados automaticamente ao construtor. Perguntas, análises e modificações de projetos existentes permanecem no agente de conversa, sem criar outro projeto indevidamente.
- O compositor mantém o campo de mensagem, ditado, estado real da IA e envio; não oferece seleção de modelo nem configuração externa.
- A tela principal é o chat único, com cabeçalho compacto e compositor fixo. O botão ☰ abre somente o histórico; o atalho Apps/APKs no cabeçalho abre o catálogo; o menu de ferramentas reúne anexos, códigos guardados, terminal Android independente, memória e assistente flutuante; ⋮ abre configurações. O seletor nativo aceita texto/código até 256 KB, PDF com texto pesquisável, DOCX, ODT e imagens para análise visual aproximada e OCR local; os resultados entram no rascunho e não são enviados até o usuário tocar em enviar. Ditado fica junto ao compositor e “Ouvir” fica nas ações da resposta. Não recriar navegação por abas, seletor de modo/provedor ou tela manual de Build. Não há ícone nem tela separada de terminal; ferramentas internas só podem ser usadas quando o pedido atual exigir. Tarefas de internet usam a conexão ativa do Android sem configuração manual.

## Restrições obrigatórias
- Entrega final: APK Android nativo. Nada de site embrulhado em WebView. Interface em Kotlin/Jetpack Compose, sem WebView.
- Não usar Replit nem serviços de terceiros para compilar os aplicativos que o Kellvyn criar.
- A compilação de projetos gerados deve ocorrer no próprio aparelho, sem token, chave, conta externa ou serviço remoto de build. A IA inicia e acompanha o fluxo pelo chat. A área Apps serve para consultar projetos/códigos e salvar ou instalar APKs reais; não há exportação de arquivo compactado nem botão para iniciar o construtor manualmente.
- O repositório GitHub pode continuar sendo usado por nós para versionar o código-fonte do Kellvyn; o app não pode depender do GitHub para compilar projetos.
- Não gerar nem entregar ZIP. O resultado solicitado para criação de aplicativo é o APK compilado, apresentado diretamente no chat principal. Não afirmar que o APK existe até a compilação e a verificação terem sido concluídas.
- O modo de IA local deve funcionar sem rede depois que o modelo tiver sido instalado. A primeira transferência do modelo pode exigir internet.
- Não existe controle para trocar, selecionar ou apagar manualmente o modelo de IA. A preparação do modelo único é automática; se ele não vier incluído no APK, o app baixa a cópia fixada uma vez e depois realiza inferência local.
- Pedidos diretos de imagem usam geração de ilustrações vetoriais via SVG pelo modelo textual local, validação restritiva e renderização PNG no aparelho. Isto não equivale a difusão fotorrealista e não permite analisar visualmente a imagem de entrada.
- A entrada de anexos aceita texto/código, PDF pesquisável ou escaneado (com OCR local nas páginas sem camada de texto), DOCX, ODT e imagens para rotulagem visual aproximada + OCR local. O conteúdo extraído no aparelho é inserido no rascunho; isso não é visão computacional geral nem legenda detalhada da imagem.
- A ajuda flutuante precisa de permissão Android de sobreposição; leitura da tela exige ativação explícita do serviço de acessibilidade e só captura quando o usuário aciona a ação correspondente. A proteção reforçada na tela Configurações pode ser ativada para exigir confirmação antes de gravar/corrigir arquivos ou compilar APKs.
- O chat conversacional usa o motor local empacotado no aplicativo; não há provedor externo de IA nem configuração de endpoint. Pesquisa web/GitHub é uma capacidade separada e só é acionada quando a mensagem atual solicita explicitamente esse acesso.
- Nunca embutir segredos ou tokens no APK/código.

## Aparelho-alvo e ambiente
- Poco F6 com HyperOS, Android 15, 8 GB de RAM e 512 GB de armazenamento, conforme informado pelo usuário.
- O usuário não tem PC; o fluxo de trabalho precisa ser utilizável pelo telefone.
- A toolchain local precisará considerar ARM64, memória, armazenamento, temperatura e tempo de build. Não afirmar que foi testada no Poco F6 sem um teste real nele.

## Design e interação
- Paleta visual seguindo a imagem verde enviada: preto profundo, verde-esmeralda/turquesa luminoso como acento dominante, textos brancos/verde-claro e painéis quase pretos; sem paleta azul dominante.
- O símbolo da IA e o ícone do aplicativo instalado devem permanecer luminosos e legíveis em tamanhos pequenos, mantendo a paleta verde-esmeralda.
- Preservar a composição compacta da referência verde: barra superior com nome e ações discretas, chat como área principal e compositor fixo próximo ao rodapé. A navegação implementada permanece nos menus superiores, sem abas inferiores.
- Incorporar progressivamente as funções vistas na montagem conceitual posterior: projetos/arquivos, editor, console, cartão real de APK e configurações; área de jogos somente quando a integração de observação/controle for real. Sem WebView; toda navegação deve ser nativa.
- Nomes, tamanhos, porcentagens, logs e resultados ilustrados são exemplos visuais, não dados reais ou promessa de funções prontas.
- Chat principal com status do núcleo que mostra estados honestos: planejando, gerando, compilando, testando, concluído ou erro.
- Órbita/mascote flutuante discreto é uma meta visual, não algo já concluído.
- Abaixo de cada resposta da IA: ícone pequeno de folha/páginas com “Copiar”, copiando a resposta inteira.
- O ditado em português é acionado pelo microfone junto ao compositor, e a leitura em voz alta pela ação “Ouvir” abaixo de cada resposta. Ambos devem informar honestamente quando o serviço do Android não estiver disponível.
- O APK compilado deve ser entregue diretamente no chat principal. Não gerar nem entregar ZIP.

## Cérebro de IA
- LiteRT-LM é um runtime, não um modelo criado do zero nem o cérebro inteiro.
- O adaptador atual usa um único modelo de conversa integrado ao APK, copiado ao armazenamento privado e verificado por SHA-256. Não existe seletor de modelos nem alternância de modos na interface.
- O modelo incluído é de porte leve, adequado principalmente a conversa e tarefas básicas; a qualidade de programação não deve ser presumida e precisa ser avaliada com testes reais.
- O chat não envia prompts a um provedor externo de IA. As ferramentas de pesquisa web/GitHub são outra capacidade e só são acionadas quando a mensagem atual solicita explicitamente uma pesquisa ou ação sobre um link.
- O projeto deve manter Kotlin/Compose Compiler alinhados com a versão da API LiteRT-LM escolhida e usar versões de coroutines compatíveis com o runtime. A compatibilidade do modelo e o desempenho precisam ser verificados na build e no aparelho-alvo.
- A conversa usa o modelo local integrado. Pesquisa web/GitHub só é acionada quando o usuário pede explicitamente; usa a conexão ativa do Android sem exigir configuração manual.
- A IA desejada deve planejar, dividir tarefas, usar ferramentas reais, observar resultados, diagnosticar erros, corrigir e tentar novamente com limites/controles. Preservar o efeito visual de chuva do modelo solicitado pelo usuário conforme a referência original; não substituir por uma animação inventada nem declarar pronto sem verificar a implementação.
- Princípio arquitetural obrigatório: o agente principal trabalha pelo chat, planeja tarefas, altera arquivos e inicia a compilação quando necessário. Não há tela/ícone Terminal na navegação. Terminal/shell/Termux é independente e não participa do fluxo de chat ou programação. O agente usa as ferramentas internas de workspace, Git e compilação, sem encaminhar tarefas para o terminal.
- Implementado no código: ciclo limitado de ferramentas no chat para workspace, leitura/busca de arquivos locais, pesquisa web, consulta de páginas HTTPS e leitura de arquivos públicos do GitHub. O acesso à internet é acionado quando o pedido atual o solicita; não há provedor de IA online. Importações e ações Git protegidas pedem confirmação. O agente não depende de uma tela de terminal.
- Criação/edição normal de arquivos ocorre automaticamente após pedido claro no chat, usando APIs diretas do workspace. O Editor sincroniza a versão salva após alterações feitas pelo agente quando não há rascunho não salvo; quando há conflito, preserva o rascunho e avisa. Exclusões, importação de repositórios e operações Git que alteram estado pedem confirmação. O agente usa uma camada JGit separada; não usa a tela Terminal nem o shell manual. O shell manual permanece separado do chat e não é usado pelo agente.
- Memória de conversa não é a mesma coisa que memória duradoura. O código atual permite salvar, ver e apagar fatos explícitos; isso não é treino automático nem alteração dos pesos do modelo.

## Construtor nativo e APK
O fluxo final desejado ao receber “crie o aplicativo X”:
1. Entender requisitos e apresentar o plano de execução.
2. Gerar um projeto Android nativo coerente.
3. Escrever e revisar os arquivos.
4. Compilar localmente no telefone, sem cloud build, token ou serviço remoto.
5. Ler erros Kotlin/Java/Gradle/recursos, corrigir e tentar novamente com limite de tentativas.
6. Verificar o pacote, a assinatura e a integridade do APK.
7. Apresentar nome, versão, tamanho e ação para salvar/instalar o APK.
8. Manter logs e código acessíveis para diagnóstico e apresentar apenas o APK realmente compilado como resultado do construtor.

Pipeline local implementado no código: ECJ compila Java, D8 gera DEX, o empacotador cria o manifesto Android binário mínimo e apksig assina/verifica o APK. O fluxo Build do chat o inicia em segundo plano sem depender da tela Terminal; a ferramenta também está disponível para o agente compilar um projeto salvo. Há uma tentativa limitada de correção automática após erro real. Perfil inicial: Activity Java nativa com APIs Android do framework, sem Kotlin/Compose, recursos externos ou bibliotecas de terceiros. A primeira instalação da plataforma solicita aceite explícito da licença Android SDK e valida o checksum. A saída é um APK no armazenamento privado e o app abre o instalador nativo. Nenhuma build em nuvem é usada.

## Ferramentas e tarefas futuras
- Leitura e gerenciamento de arquivos pelo menu de aplicativos/ferramentas, com salvamento local.
- O chat consulta/edita arquivos diretamente pelo workspace e usa ferramentas internas quando solicitado. O editor serve para guardar e consultar códigos; não é um agente separado. Não expor terminal como ícone ou tela. O terminal é uma ferramenta independente, não integrada ao chat nem ao construtor. Não encaminhar tarefas de programação a comandos manuais.
- Não criar uma aba ou área de terminal/Git como parte da navegação principal.
- Sem navegador baseado em WebView. Não reintroduzir WebView.
- Aprender a jogar exige capacidade concreta de observar estado do jogo, receber pixels/dados, executar comandos de controle autorizados e avaliar o resultado. Um LLM sozinho não enxerga nem controla jogo. Essa função ainda não está implementada.
- Automação de jogos, overlay flutuante e depuração automática continuam pendentes até haver código funcional e teste real.

## Segurança e qualidade
- Tratar código gerado pelo modelo como entrada não confiável.
- Bloquear traversal de caminhos e escrita fora do workspace.
- Não executar código gerado diretamente sem sandbox/limites e controles adequados.
- Mostrar erros reais; não esconder falhas nem marcar tarefas como concluídas sem teste.
- O app não deve enviar código-fonte para um serviço remoto para compilação.

## Estado técnico conhecido (15 de outubro de 2026)
Implementado no código: Kotlin/Compose, chat com histórico local limitado, botão Copiar, adaptador LiteRT-LM, memória explícita, ferramentas web/GitHub por pedido explícito, construtor interno acionado pelo chat, workspace local, editor, ditado/TTS e compilador Android local para projetos Java. O arquivo de modelo esperado não está presente no checkout atual e a revisão ainda não tem APK novo validado. Voz e build local precisam de teste real no Poco F6. Não há seletor de modelo, modo Build manual, exportação compactada ou tela Terminal.

Ainda não concluído/validado: compilação da revisão atual do próprio Kellvyn; presença/validade do modelo de IA na distribuição; resposta real, voz, instalação e desempenho no Poco F6; compilação/instalação de projetos gerados no aparelho. O runtime nativo Telegram está no código, mas ainda não foi compilado/testado. WhatsApp Business continua apenas como código-fonte: receber mensagens exige webhook HTTPS público, que não deve ser fingido como funcional nem substituído por hospedagem remota não solicitada. Aprendizagem de jogos e overlay/mascote também permanecem pendentes. Uma build anterior não comprova estas capacidades.

## Critério de conclusão
O critério de conclusão do bloco de build é confirmar no aparelho-alvo que um projeto Java pedido em português passa pelo fluxo de chat, gera APK, apresenta assinatura verificada e abre o instalador nativo sem terminal nem serviço remoto.
