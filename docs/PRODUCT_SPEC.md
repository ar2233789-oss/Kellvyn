# Especificação central — Kellvyn IA

Este documento reúne os requisitos do produto para manter a direção do projeto consistente.

## Identidade e objetivo
- Nome: **Kellvyn IA**. Não usar Brian/Brain como nome e não apresentar o app como produto oficial da OpenAI.
- Assistente Android nativo com IA real, planejamento, geração/revisão de código e, como objetivo central, capacidade de criar aplicativos instaláveis.
- A imagem verde-esmeralda enviada pelo usuário é a referência obrigatória de aparência: fundo escuro, brilho verde-esmeralda/turquesa, barra superior, lista da conversa, compositor inferior e navegação no rodapé. A interface não deve ser azul-neon dominante.
- A montagem posterior de oito telas gerada pelo assistente serve **somente como inventário de funções desejadas** (projetos, chat com plano, editor, console, resultado de APK, jogos e configurações); sua aparência azul e seus resultados ilustrativos não devem ser copiados como se fossem os requisitos visuais ou recursos já existentes.
- Nenhuma imagem de conceito comprova que uma função está implementada. Só mostrar sucesso, progresso de compilação, tamanho de APK ou aprendizado de jogo quando a função real tiver sido implementada e executada.
- Nada de demo disfarçada: controles, status e respostas precisam corresponder a funções reais.

## Chat principal e navegação da interface
- O construtor é uma ferramenta interna do agente e não pode aparecer como tela, botão ou modo manual de Build. O usuário descreve o que quer no chat; a IA decide quando criar um projeto, editar arquivos ou iniciar a compilação local.
- Pedidos claros para criar aplicativos ou projetos são encaminhados automaticamente ao construtor. Perguntas, análises e modificações de projetos existentes permanecem no agente de conversa, sem criar outro projeto indevidamente.
- O compositor mostra somente o estado real do núcleo local e o botão de envio; não oferece seleção de provedor externo de IA.
- Preservar o compositor compacto e a navegação inferior AI, Navegador, Editor, Terminal, Git e Apps. Navegador abre links no navegador do Android por intent (sem WebView); Terminal e Git são ferramentas distintas e funcionam no espaço local.

## Restrições obrigatórias
- Entrega final: APK Android nativo. Nada de site embrulhado em WebView. Interface em Kotlin/Jetpack Compose, sem WebView.
- Não usar Replit nem serviços de terceiros para compilar os aplicativos que o Kellvyn criar.
- A compilação de projetos gerados deve ocorrer no próprio aparelho, sem token, chave, conta externa ou serviço remoto de build. A IA inicia e acompanha esse fluxo pelo chat; a área Apps serve para consultar código e exportar cópias, não para operar o construtor manualmente.
- O repositório GitHub pode continuar sendo usado por nós para versionar o código-fonte do Kellvyn; o app não pode depender do GitHub para compilar projetos.
- O ZIP é um formato secundário de exportação/cópia de segurança, nunca o resultado principal da conversa. Quando existir compilação local, o resultado de “criar aplicativo” deve ser um cartão para salvar/baixar o APK.
- O modo de IA local deve funcionar sem rede depois que o modelo tiver sido instalado. A primeira transferência do modelo pode exigir internet.
- O chat conversacional usa o motor local empacotado no aplicativo; não há provedor externo de IA nem configuração de endpoint. Pesquisa web/GitHub é uma capacidade separada e só é acionada quando a mensagem atual solicita explicitamente esse acesso.
- Nunca embutir segredos ou tokens no APK/código.

## Aparelho-alvo e ambiente
- Poco F6 com HyperOS, Android 15, 8 GB de RAM e 512 GB de armazenamento, conforme informado pelo usuário.
- O usuário não tem PC; o fluxo de trabalho precisa ser utilizável pelo telefone.
- A toolchain local precisará considerar ARM64, memória, armazenamento, temperatura e tempo de build. Não afirmar que foi testada no Poco F6 sem um teste real nele.

## Design e interação
- Paleta visual seguindo a imagem verde enviada: preto profundo, verde-esmeralda/turquesa luminoso como acento dominante, textos brancos/verde-claro e painéis quase pretos; sem paleta azul dominante.
- Preservar a composição da imagem verde: barra superior compacta com nome do assistente e ações, estados do núcleo discretos, chat como área principal, compositor fixo próximo ao rodapé e navegação inferior.
- Incorporar progressivamente as funções vistas na montagem conceitual posterior: projetos/arquivos, editor, console, cartão real de APK e configurações; área de jogos somente quando a integração de observação/controle for real. Sem WebView; toda navegação deve ser nativa.
- Nomes, tamanhos, porcentagens, logs e resultados ilustrados são exemplos visuais, não dados reais ou promessa de funções prontas.
- Chat principal com status do núcleo que mostra estados honestos: planejando, gerando, compilando, testando, concluído ou erro.
- Órbita/mascote flutuante discreto é uma meta visual, não algo já concluído.
- Abaixo de cada resposta da IA: ícone pequeno de folha/páginas com “Copiar”, copiando a resposta inteira.
- Entrada por ditado em português e leitura de resposta podem usar serviços de voz do Android; indicar claramente quando eles não estiverem disponíveis.
- O APK criado deve aparecer como resultado principal. Não inundar a conversa com ZIPs.

## Cérebro de IA
- LiteRT-LM é um runtime, não um modelo criado do zero nem o cérebro inteiro.
- O adaptador atual usa um único modelo SmolLM2-360M-Instruct (~374 MB), empacotado no APK, copiado ao armazenamento privado e verificado por SHA-256 antes do uso. A troca automática para Qwen Coder, importação de modelos ou seleção de outro motor não está implementada nesta revisão. Compatibilidade, qualidade e desempenho ainda requerem teste real no telefone.
- O modelo incluído é de porte leve, adequado principalmente a conversa e tarefas básicas; a qualidade de programação não deve ser presumida e precisa ser avaliada com testes reais.
- O chat não envia prompts a um provedor externo de IA. As ferramentas de pesquisa web/GitHub são outra capacidade e só são acionadas quando a mensagem atual solicita explicitamente uma pesquisa ou ação sobre um link.
- O projeto deve manter Kotlin/Compose Compiler alinhados com a versão da API LiteRT-LM escolhida e usar versões de coroutines compatíveis com o runtime. A compatibilidade do modelo e o desempenho precisam ser verificados na build e no aparelho-alvo.
- O agente não pede nem configura provedor de IA online. A pesquisa web/GitHub só é roteada quando o usuário pede explicitamente esse acesso na mensagem atual; nesse caso, o site consultado acessa a internet, mas o motor conversacional continua local.
- O núcleo desejado deve planejar, dividir tarefas, usar ferramentas reais, observar resultados, diagnosticar erros, corrigir e tentar novamente com limites/controles.
- Princípio arquitetural obrigatório: o agente principal trabalha diretamente pelo chat e não exige que o usuário abra, configure ou use a tela Terminal. Ele deve planejar e executar tarefas pelo acesso próprio e controlado ao workspace, arquivos, projetos, pesquisa e ferramentas de build. O Terminal é uma interface manual opcional; dentro dele, um agente auxiliar pode explicar comandos, diagnosticar erros e orientar o usuário sem tirar dele o controle.
- Implementado no código: ciclo limitado de uso de ferramentas no chat para workspace, leitura/busca de arquivos locais, consulta de página HTTPS pública, pesquisa web, descoberta/listagem de caminhos e leitura de arquivos de repositórios públicos do GitHub quando a permissão de internet das ferramentas está ligada, independentemente do motor local/online. A importação de arquivos públicos para o workspace tem limites e pede confirmação. O diagnóstico do terminal pelo chat é auxiliar, não dependência.
- Criação/edição normal de arquivos ocorre automaticamente após pedido claro no chat, usando APIs diretas do workspace. O Editor sincroniza a versão salva após alterações feitas pelo agente quando não há rascunho não salvo; quando há conflito, preserva o rascunho e avisa. Exclusões, importação de repositórios e operações Git que alteram estado pedem confirmação. O agente usa uma camada JGit separada; não usa a tela Terminal nem o shell manual. O shell manual é separado e exige confirmação.
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
8. Manter logs e código acessíveis para diagnóstico; não mostrar ZIP como se fosse APK.

Pipeline local implementado no código: ECJ compila Java, D8 gera DEX, o empacotador cria o manifesto Android binário mínimo e apksig assina/verifica o APK. O fluxo Build do chat o inicia em segundo plano sem depender da tela Terminal; a ferramenta também está disponível para o agente compilar um projeto salvo. Há uma tentativa limitada de correção automática após erro real. Perfil inicial: Activity Java nativa com APIs Android do framework, sem Kotlin/Compose, recursos externos ou bibliotecas de terceiros. A primeira instalação da plataforma solicita aceite explícito da licença Android SDK e valida o checksum. A saída é um APK no armazenamento privado e o app abre o instalador nativo. Nenhuma build em nuvem é usada.

## Ferramentas e tarefas futuras
- Editor de arquivos com salvamento local.
- O chat autônomo consulta/edita arquivos diretamente pelo workspace, pode ler arquivos grandes por trechos limitados a 120 linhas e usa JGit para operações de versão local, sem depender da tela Terminal. O Terminal é uma área opcional com shell manual separado, acionado pelo usuário com `! comando`, confirmação e limites de tempo/saída. Esse shell nunca é acessível ao agente automático e não é um sandbox: como roda com o UID do app, comandos podem alcançar arquivos privados acessíveis ao aplicativo. O botão de ajuda do terminal envia o contexto para o mesmo agente, que continua usando APIs diretas.
- Git local quando implementado de verdade; não deve ser requisito de build.
- Sem navegador baseado em WebView. Não reintroduzir WebView.
- Aprender a jogar exige capacidade concreta de observar estado do jogo, receber pixels/dados, executar comandos de controle autorizados e avaliar o resultado. Um LLM sozinho não enxerga nem controla jogo. Essa função ainda não está implementada.
- Automação de jogos, overlay flutuante, terminal seguro, depuração automática e ciclo de build/teste continuam pendentes até haver código funcional e teste real.

## Segurança e qualidade
- Tratar código gerado pelo modelo como entrada não confiável.
- Bloquear traversal de caminhos e escrita fora do workspace.
- Não executar código gerado diretamente sem sandbox/limites e controles adequados.
- Mostrar erros reais; não esconder falhas nem marcar tarefas como concluídas sem teste.
- O app não deve enviar código-fonte para um serviço remoto para compilação.

## Estado técnico conhecido (13 de outubro de 2026)
Implementado no código: Kotlin/Compose, chat com histórico local limitado, botão Copiar, um modelo LiteRT-LM empacotado no APK, memória explícita, ferramentas web/GitHub por pedido explícito, construtor interno acionado pelo chat, workspace local, editor, exportação ZIP secundária, compilador Android local e painéis separados de Terminal e Git. O usuário não escolhe um provedor de IA nem opera um modo Build manualmente. Voz e segundo modelo não estão expostos nesta revisão.

Ainda não concluído/validado: compilação local de projetos gerados; resposta real no Poco F6; instalação e desempenho no aparelho; depuração/build/teste automático local; aprendizagem de jogos; overlay/mascote; terminal real seguro; entrega de APKs dos projetos criados. A build anterior do próprio Kellvyn não comprova essas capacidades.

## Critério de conclusão
O critério de conclusão do bloco de build é confirmar no aparelho-alvo que um projeto Java pedido em português passa pelo fluxo de chat, gera APK, apresenta assinatura verificada e abre o instalador nativo sem terminal nem serviço remoto.
