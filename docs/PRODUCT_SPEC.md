# Especificação central — Kellvyn IA

Este documento reúne os requisitos do produto para manter a direção do projeto consistente.

## Identidade e objetivo
- Nome: **Kellvyn IA**. Não usar Brian/Brain como nome e não apresentar o app como produto oficial da OpenAI.
- Assistente Android nativo com IA real, planejamento, geração/revisão de código e, como objetivo central, capacidade de criar aplicativos instaláveis.
- A imagem verde-esmeralda enviada pelo usuário é a referência obrigatória de aparência: fundo escuro, brilho verde-esmeralda/turquesa, barra superior, lista da conversa, compositor inferior e navegação no rodapé. A interface não deve ser azul-neon dominante.
- A montagem posterior de oito telas gerada pelo assistente serve **somente como inventário de funções desejadas** (projetos, chat com plano, editor, console, resultado de APK, jogos e configurações); sua aparência azul e seus resultados ilustrativos não devem ser copiados como se fossem os requisitos visuais ou recursos já existentes.
- Nenhuma imagem de conceito comprova que uma função está implementada. Só mostrar sucesso, progresso de compilação, tamanho de APK ou aprendizado de jogo quando a função real tiver sido implementada e executada.
- Nada de demo disfarçada: controles, status e respostas precisam corresponder a funções reais.

## Seletor de modo e navegação da interface
- A setinha no compositor deve controlar uma opção funcional, não ser decorativa: alterna entre conversa normal e Build (criação de projeto), e permite selecionar o motor local ou o provedor online opcional.
- O rótulo do motor deve refletir a configuração real; nunca exibir GPT-5 Nano como ativo se não estiver configurado como o modelo do provedor selecionado.
- Preservar o compositor de duas linhas e a navegação inferior AI, Navegador, Editor, Terminal, Git e Abas. Navegador abre links no navegador do Android por intent (sem WebView); Terminal/Git operam no espaço local.

## Restrições obrigatórias
- Entrega final: APK Android nativo. Nada de site embrulhado em WebView. Interface em Kotlin/Jetpack Compose, sem WebView.
- Não usar Replit nem serviços de terceiros para compilar os aplicativos que o Kellvyn criar.
- A compilação de projetos gerados deve ocorrer no próprio aparelho, sem token, chave, conta externa ou serviço remoto de build.
- O repositório GitHub pode continuar sendo usado por nós para versionar o código-fonte do Kellvyn; o app não pode depender do GitHub para compilar projetos.
- O ZIP é um formato secundário de exportação/cópia de segurança, nunca o resultado principal da conversa. Quando existir compilação local, o resultado de “criar aplicativo” deve ser um cartão para salvar/baixar o APK.
- O modo de IA local deve funcionar sem rede depois que o modelo tiver sido instalado. A primeira transferência do modelo pode exigir internet.
- Modo online para respostas do assistente pode ser separado e opt-in; não pode ser usado para compilar APKs nem receber projetos para esse fim.
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
- O código contém um adaptador LiteRT-LM com dois perfis de modelo salvos separadamente: SmolLM2-360M-Instruct leve (~374 MB) e Qwen2.5-Coder-1.5B quantizado (~1,12 GB, Apache-2.0), alternáveis sem novo download. A importação de outros arquivos .litertlm é limitada a 1,5 GB. Compatibilidade, qualidade e desempenho ainda requerem teste real no telefone.
- O modelo leve é indicado para conversa/tarefas básicas; o Qwen foi publicado para programação, mas não foi testado no Poco F6 deste projeto e não deve ser apresentado como desempenho garantido.
- Modo local e modo online opcional precisam estar claramente separados. No modo local, prompts não são enviados ao endpoint de IA. No modo online, a conversa/contexto vai ao endpoint configurado pelo usuário.
- A permissão de internet das ferramentas é independente do motor de IA e começa desligada. Ao ativá-la, o agente pode pesquisar na web e ler páginas HTTPS públicas; a consulta e as páginas solicitadas são acessadas externamente, mesmo quando o modelo continua local.
- O núcleo desejado deve planejar, dividir tarefas, usar ferramentas reais, observar resultados, diagnosticar erros, corrigir e tentar novamente com limites/controles.
- Princípio arquitetural obrigatório: o agente principal trabalha diretamente pelo chat e não exige que o usuário abra, configure ou use a tela Terminal. Ele deve planejar e executar tarefas pelo acesso próprio e controlado ao workspace, arquivos, projetos, pesquisa e ferramentas de build. O Terminal é uma interface manual opcional; dentro dele, um agente auxiliar pode explicar comandos, diagnosticar erros e orientar o usuário sem tirar dele o controle.
- Implementado no código: ciclo limitado de uso de ferramentas no chat para workspace, leitura/busca de arquivos locais, consulta de página HTTPS pública, pesquisa web, descoberta/listagem de caminhos e leitura de arquivos de repositórios públicos do GitHub quando a permissão de internet das ferramentas está ligada, independentemente do motor local/online. A importação de arquivos públicos para o workspace tem limites e pede confirmação. O diagnóstico do terminal pelo chat é auxiliar, não dependência.
- Gravação/substituição de arquivos, exclusão e comandos de terminal do agente que alteram estado abrem uma confirmação explícita. O agente não pode usar o shell manual, executar scripts gerados, encadear comandos ou redirecionar comandos; o shell manual é uma função separada acionada conscientemente pelo usuário com confirmação.
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

Estado atual: o construtor cria, valida, guarda, edita e exporta arquivos de código; **a toolchain de compilação local ainda não foi implementada**. Não anunciar que apps criados já viram APK instalável. O próximo bloco crítico é implementar/embutir ferramentas de compilação locais e provar que elas funcionam no aparelho. Não substituir isso por GitHub Actions, Replit ou servidores.

## Ferramentas e tarefas futuras
- Editor de arquivos com salvamento local.
- O chat autônomo não depende da tela Terminal: ele consulta/edita o workspace e executa as ferramentas internas diretamente. O Terminal é uma área opcional para quem deseja trabalhar manualmente e tem um atalho explícito `! comando` que pede confirmação e inicia `/system/bin/sh` como UID normal do aplicativo, sem root, com limite de 15 segundos e saída limitada. Esse shell manual nunca é exposto ao agente automático. O terminal de comandos internos/Git do agente continua na lista de operações permitidas e pede autorização para alterações.
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
Implementado no código: Kotlin/Compose, chat, histórico local limitado e apagável, botão Copiar, voz condicionada aos serviços do Android, motor local LiteRT-LM inicial, importação/download de modelo, memória explícita, modo online opcional para respostas, gerador de arquivos, workspace local, editor, ZIP secundário e console limitado de workspace.

Ainda não concluído/validado: compilação local de projetos gerados; resposta real no Poco F6; instalação e desempenho no aparelho; depuração/build/teste automático local; aprendizagem de jogos; overlay/mascote; terminal real seguro; entrega de APKs dos projetos criados. A build anterior do próprio Kellvyn não comprova essas capacidades.

## Critério de conclusão
O Kellvyn só estará completo quando a interface nativa, um modelo real, memória, ferramentas e compilador local trabalharem juntos; quando um projeto pedido em português puder ser compilado no próprio telefone sem token/serviço remoto; quando o APK válido for a saída principal; e quando os fluxos críticos tiverem sido testados no aparelho-alvo.
