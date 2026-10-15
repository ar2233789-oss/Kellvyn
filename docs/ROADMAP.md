# Kellvyn IA — estado real e próximos passos

Este documento separa código presente de funções que ainda precisam de build e teste no aparelho. A presença de uma classe ou botão não comprova que o recurso passou por execução real.

## Código presente nesta revisão

- [x] Acesso à internet ligado à tarefa na única Kellvyn IA: o mesmo agente usa o modelo local e pode pesquisar dados atuais/externos pela conexão do celular sem frase obrigatória; pedidos explicitamente offline e tarefas claramente locais continuam sem rede. Roteamento e pesquisa automática passaram nos testes unitários e build da revisão `a6b734fa`; validação física no Poco F6 continua separada.
- [x] Aplicativo Android nativo em Kotlin/Jetpack Compose, com uma tela principal de conversa.
- [x] Criar, corrigir e compilar projetos é solicitado pelo chat principal; não existe seletor manual de “conversar/criar”.
- [x] Sem modo leve/pesado, sem seletor de modelo e sem provedor externo de IA.
- [x] Navegação: hambúrguer abre somente o histórico; Apps/APKs abre o catálogo; ⋮ abre configurações. Não existe menu superior de ferramentas; anexos ficam junto ao compositor, e Terminal Termux é acessível a partir de Apps/Configurações.
- [x] Histórico limitado e apagável; memórias explícitas podem ser guardadas, vistas e apagadas.
- [x] Contexto recente ampliado para 32 mensagens, com orçamento dinâmico de tokens que preserva o pedido atual, reduz contexto antigo e limita a saída ao espaço disponível do modelo.
- [x] Falhas de inicialização do modelo mostram erro e ação de tentar novamente; cancelamentos de inferência, geração de projetos, OCR e exportação são propagados em vez de virarem erros genéricos.
- [x] Gradle Wrapper oficial 8.9 incluído com SHA-256 da distribuição fixado para builds locais do aplicativo anfitrião. O workflow `android-apk.yml` também compila e publica o APK anfitrião de debug após testes unitários; isso não substitui a validação local no aparelho.
- [x] Ditado em português no compositor e ação “Ouvir” nas respostas, usando recursos disponíveis no próprio Android e informando quando indisponíveis.
- [x] Ícones do cabeçalho, compositor e catálogo Apps substituídos por desenhos vetoriais escaláveis em Canvas; validar a aparência final no Poco F6 ainda está pendente.
- [x] Código da sobreposição flutuante e do leitor de tela declarado no Manifesto; o usuário precisa conceder permissão de sobreposição e habilitar Acessibilidade para ler texto somente por toque.
- [x] Importação de PDF pesquisável ou escaneado (renderização de páginas + OCR), DOCX e ODT. Anexos de imagem agora seguem caminho multimodal local; validar com build e teste no aparelho ainda pendente.
- [x] Geração local de ilustrações vetoriais: modelo textual retorna SVG validado, a biblioteca local renderiza PNG, a resposta mostra prévia e permite salvar. Não é difusão fotorrealista.
- [x] Resposta em streaming publica o primeiro trecho assim que chega; a tela mostra um estado de processamento desde o envio para não aparentar congelamento antes do primeiro token.
- [x] O compilador verifica SHA-1/tamanho do arquivo da plataforma, valida o android.jar extraído e conserva um arquivo anterior conhecido enquanto instala a nova plataforma. O build local ainda precisa ser executado em Android.
- [x] Agente acessa o workspace por APIs diretas, lê e pesquisa arquivos, altera arquivos após pedidos claros e encaminha diagnósticos para o chat.
- [x] Editor de código mantém rascunhos não salvos e sincroniza alterações feitas pelo agente com proteção contra sobrescrita.
- [x] Ferramentas Git locais por JGit; nenhuma aba/ícone Terminal ou Git na navegação principal.
- [x] Pipeline local inicial ECJ → D8 → manifesto Android binário → assinatura/verificação para projetos Java nativos.
- [x] Código da ponte Termux RUN_COMMAND e recepção de resultado real. O Termux é um aplicativo independente e a IA só pode conectar quando a pessoa pedir ajuda com Termux explicitamente pelo chat principal; cada comando exige confirmação. A integração ainda precisa de build e teste real.
- [x] Fluxo do chat consulta o compilador e mostra o resultado/diagnósticos; o APK mais recente pode ser salvo ou instalado.
- [x] Exportação de projetos em arquivo compactado removida. O resultado de criação de aplicativos é o APK, não um arquivo compactado.
- [x] Separação do tipo de build: a CI compila/publica somente o APK anfitrião de debug e roda testes de aceitação com projetos-fixture descartáveis; a compilação de projetos solicitados pelo usuário no uso real permanece responsabilidade do compilador local no aparelho, sem Replit ou servidores remotos.
- [x] Código para runtime nativo de bots Telegram: token criptografado, long polling da Bot API, comandos por manifesto e ativação pelo catálogo Apps. Build e teste real continuam pendentes.
- [x] Isolamento entre runtimes Discord/Telegram: cada serviço confere o `platform` do manifesto antes de alterar o registro compartilhado; comandos de parada enviados ao runtime errado não desativam o outro tipo de bot. Testes unitários da guarda de plataforma adicionados; teste de serviço real continua pendente.

## Validação pendente — não marcar como concluída sem executar

- [ ] Compilar o próprio Kellvyn a partir de um checkout limpo em ambiente Android local.
- [x] Adicionar inicialização do modelo local com fallback para download público fixado por revisão, progresso real, tamanho esperado e verificação SHA-256, sem inferência remota.
- [ ] Testar o download inicial do Gemma 4 E2B de aproximadamente 2,6 GB, a análise multimodal, falha/cancelamento de rede e reinicialização no Poco F6.
- [x] CI executou `testDebugUnitTest` e `assembleDebug` para o APK anfitrião; build #47 terminou com sucesso: https://github.com/ar2233789-oss/Kellvyn/actions/runs/38765936032. Isso comprova a compilação na CI, não uma build independente em Android local.
- [ ] Confirmar permissões e funcionamento da bolha flutuante em Android 15/HyperOS e ajustar dimensões/legibilidade em uma captura real do Poco F6.
- [ ] Instalar e testar o APK anfitrião no Poco F6 real, incluindo a transferência inicial do modelo se ele não vier embutido.
- [ ] Testar com token de teste a ativação/desativação e os comandos /start, /ping e /echo do runtime Telegram, além de reconexão após perda de rede e restauração após reinício.
- [ ] Executar teste instrumentado no Android com ambos os runtimes ativos para comprovar restauração, parada individual e parada global sem interferência entre Discord e Telegram.
- [ ] Concluir a suíte de aceitação com modelo local no emulador e inspecionar relatório/logs antes de declarar aprovadas as conversas reais, geração de apps e compilação/instalação de APKs-fixture. Execução atual: https://github.com/ar2233789-oss/Kellvyn/actions/runs/38765935892 (ainda em andamento na última verificação).
- [ ] Confirmar que o modelo gera respostas reais e medir RAM, tempo, aquecimento e estabilidade.
- [ ] Confirmar que ditado e leitura em voz alta funcionam em português no aparelho-alvo.
- [ ] Pedir no chat a criação de um app Java mínimo, deixar o Kellvyn compilá-lo localmente, verificar assinatura/package ID e instalar o APK.
- [ ] Validar o ciclo build → erro real → diagnóstico → correção limitada → nova build no aparelho.
- [ ] Testar falta de espaço, modelo inválido/ausente, ausência de rede após instalação do modelo e cancelamento da compilação.

## Limite do primeiro compilador

O pipeline integrado atualmente atende projetos Java nativos baseados nas APIs do framework Android. O runtime Telegram foi adicionado, mas precisa de build e teste reais antes de ser considerado validado. O compilador ainda não substitui Gradle/Android Studio para projetos arbitrários: Kotlin/Compose, recursos, assets e bibliotecas externas continuam fora do perfil inicial.

## Regras que permanecem obrigatórias

O chat principal é o único ponto de entrada para tarefas do usuário. Nenhum modo leve/pesado, seletor de modelo ou provedor. Nenhuma aba ou menu superior de ferramentas para Terminal; o terminal Termux é acessível pelo catálogo Apps e pelas Configurações. Nenhuma compilação na nuvem. Não gerar, oferecer ou entregar arquivo compactado como alternativa. Só anunciar APK pronto após compilação e verificação reais; a entrega final prevista é o APK diretamente no chat principal.
