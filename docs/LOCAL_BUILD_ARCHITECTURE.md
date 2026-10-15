# Arquitetura de compilação local — Kellvyn IA

## Regra do produto

O usuário pede tudo pelo chat principal. O agente pode criar e corrigir o projeto e iniciar a compilação local sem uma tela ou modo manual de Build. O resultado esperado é um APK Android nativo validado e apresentado no próprio chat.

A compilação de projetos gerados deve ocorrer no aparelho. A integração Termux pode ser usada pelo agente principal para tarefas solicitadas pelo chat que precisem de shell real e exige confirmação antes de cada comando; o construtor e o compilador local não encaminham tarefas para o Termux. Não usar Replit, GitHub Actions, tokens, servidores ou serviços de build na nuvem. Não oferecer arquivos compactados como substituto do APK.

## Pipeline local já presente no código

1. O agente recebe o pedido no chat principal e prepara os arquivos dentro do workspace privado.
2. O validador verifica caminhos, tamanhos e consistência do projeto.
3. O compilador local baixa a plataforma Android necessária depois de consentimento explícito e verifica o checksum.
4. O perfil inicial usa ECJ para compilar Java contra APIs do Android, D8 para produzir DEX, geração do manifesto binário e apksig para assinar e verificar o APK.
5. A ferramenta devolve ao agente os resultados e erros reais. O chat não deve apresentar porcentagem, assinatura ou sucesso sem confirmação da ferramenta.
6. Um APK validado pode ser salvo ou instalado pelo fluxo nativo Android.
7. Se a compilação falhar, o agente pode analisar o diagnóstico real e aplicar correções controladas pelo workspace antes de uma nova tentativa limitada.

## Limitações atuais do compilador de projetos

O primeiro perfil só aceita aplicativos Java nativos que usem APIs do framework Android. Ainda não compila Kotlin/Compose, scripts Gradle completos, recursos `res/`, `assets/` ou bibliotecas externas. Não declarar que cria qualquer aplicativo Android arbitrário antes de ampliar e testar esse suporte.

O compilador interno de projetos criados é diferente do processo necessário para compilar o próprio Kellvyn (o aplicativo anfitrião), escrito em Kotlin/Compose. Um não prova que o outro foi compilado.

## Estado verificável nesta revisão

O código-fonte contém o chat único, o agente, o workspace, o editor, o motor LiteRT-LM, ferramentas locais e o pipeline Java→DEX→APK. Os fluxos de build na nuvem foram removidos do repositório e a exportação de projetos em arquivo compactado foi retirada.

**Ainda não existe um APK novo desta revisão validado para entrega.** O arquivo de modelo `app/src/main/assets/models/kellvyn-model.litertlm` não está presente no checkout atual; o APK anfitrião não pode ser declarado pronto até o modelo correto ser obtido e verificado pelo caminho de distribuição previsto e até a build ser realmente executada.

## Validação pendente

- Compilar o aplicativo anfitrião em ambiente Android local com as dependências e o modelo correto.
- Testar inicialização, resposta do modelo e leitura em voz alta/ditado em português no Poco F6 real.
- Pedir pelo chat que o app crie um projeto Java mínimo, compilar no próprio telefone, verificar package ID e assinatura e abrir o instalador nativo.
- Testar falhas de download, pouco espaço, falta de memória, cancelamento e funcionamento sem rede depois que o modelo estiver disponível.

A conclusão só pode ser declarada após evidência de uma build real e testes no aparelho-alvo. O código-fonte e telas de demonstração não substituem o APK compilado.
