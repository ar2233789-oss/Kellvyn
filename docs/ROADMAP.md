# Kellvyn IA — estado real e próximos passos

Este documento separa código presente de funções que ainda precisam de build e teste no aparelho. A presença de uma classe ou botão não comprova que o recurso passou por execução real.

## Código presente nesta revisão

- [x] Aplicativo Android nativo em Kotlin/Jetpack Compose, com uma tela principal de conversa.
- [x] Criar, corrigir e compilar projetos é solicitado pelo chat principal; não existe seletor manual de “conversar/criar”.
- [x] Sem modo leve/pesado, sem seletor de modelo e sem provedor externo de IA.
- [x] Cabeçalho com histórico de conversas, menu superior para leitura de arquivos de texto/código, códigos guardados, Apps/APKs, memória e configurações.
- [x] Histórico limitado e apagável; memórias explícitas podem ser guardadas, vistas e apagadas.
- [x] Ditado em português no compositor e ação “Ouvir” nas respostas, usando recursos disponíveis no próprio Android e informando quando indisponíveis.
- [x] Agente acessa o workspace por APIs diretas, lê e pesquisa arquivos, altera arquivos após pedidos claros e encaminha diagnósticos para o chat.
- [x] Editor de código mantém rascunhos não salvos e sincroniza alterações feitas pelo agente com proteção contra sobrescrita.
- [x] Ferramentas Git locais por JGit; nenhuma aba/ícone Terminal ou Git na navegação principal.
- [x] Pipeline local inicial ECJ → D8 → manifesto Android binário → assinatura/verificação para projetos Java nativos.
- [x] Fluxo do chat consulta o compilador e mostra o resultado/diagnósticos; o APK mais recente pode ser salvo ou instalado.
- [x] Exportação de projetos em arquivo compactado removida. O resultado de criação de aplicativos é o APK, não um arquivo compactado.
- [x] Workflows de build e teste em nuvem removidos; não compilar apps criados via GitHub Actions, Replit ou servidores remotos.
- [x] Código para runtime nativo de bots Telegram: token criptografado, long polling da Bot API, comandos por manifesto e ativação pelo catálogo Apps. Build e teste real continuam pendentes.

## Validação pendente — não marcar como concluída sem executar

- [ ] Compilar o próprio Kellvyn a partir de um checkout limpo em ambiente Android local.
- [x] Adicionar inicialização do modelo local com fallback para download público fixado por revisão, progresso real, tamanho esperado e verificação SHA-256, sem inferência remota.
- [ ] Testar o primeiro download de aproximadamente 374 MB, cancelamento/falha de rede e reinicialização no Poco F6.
- [ ] Instalar e testar o APK anfitrião no Poco F6 real, incluindo a transferência inicial do modelo se ele não vier embutido.
- [ ] Testar com token de teste a ativação/desativação e os comandos /start, /ping e /echo do runtime Telegram, além de reconexão após perda de rede e restauração após reinício.
- [ ] Testar que o serviço Discord não inicia nem desativa projetos Telegram ao restaurar ou encerrar bots.
- [ ] Confirmar que o modelo gera respostas reais e medir RAM, tempo, aquecimento e estabilidade.
- [ ] Confirmar que ditado e leitura em voz alta funcionam em português no aparelho-alvo.
- [ ] Pedir no chat a criação de um app Java mínimo, deixar o Kellvyn compilá-lo localmente, verificar assinatura/package ID e instalar o APK.
- [ ] Validar o ciclo build → erro real → diagnóstico → correção limitada → nova build no aparelho.
- [ ] Testar falta de espaço, modelo inválido/ausente, ausência de rede após instalação do modelo e cancelamento da compilação.

## Limite do primeiro compilador

O pipeline integrado atualmente atende projetos Java nativos baseados nas APIs do framework Android. O runtime Telegram foi adicionado, mas precisa de build e teste reais antes de ser considerado validado. O compilador ainda não substitui Gradle/Android Studio para projetos arbitrários: Kotlin/Compose, recursos, assets e bibliotecas externas continuam fora do perfil inicial.

## Regras que permanecem obrigatórias

O chat principal é o único ponto de entrada para tarefas do usuário. Nenhum modo leve/pesado, seletor de modelo ou provedor. Nenhuma aba/ícone Terminal. Nenhuma compilação na nuvem. Não gerar, oferecer ou entregar arquivo compactado como alternativa. Só anunciar APK pronto após compilação e verificação reais; a entrega final prevista é o APK diretamente no chat principal.
