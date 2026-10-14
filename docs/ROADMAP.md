# Kellvyn IA — roadmap técnico

## Fase 1 — Aplicativo Android nativo
- [x] Projeto Kotlin/Jetpack Compose.
- [x] Tema preto/esmeralda com cabeçalho compacto, chat central e compositor compacto; a criação é roteada automaticamente pelo chat e não existe seletor/tela manual de Build. Navegação: AI/Navegador/Editor/Terminal/Git/Apps.
- [x] Regras de roteamento chat→construtor cobertas por testes: pedidos diretos e briefs detalhados criam projetos pelo chat, enquanto perguntas, análises e modificações de apps existentes ficam no agente normal.
- [x] Chat com histórico local limitado, ação para limpar e rolagem automática.
- [x] Botão de copiar abaixo das respostas.
- [ ] Ditado e leitura em voz alta; não expor botões de áudio até haver ações funcionais integradas.
- [x] Memória explícita controlada pelo usuário.
- [x] Terminal local de workspace com comandos de arquivos e Git via JGit; shell manual opcional para comando digitado pelo usuário, com confirmação e limites. O agente automático não recebe o shell manual, somente sua lista interna de ferramentas.
- [x] Agente do chat usa APIs diretas para listar/ler/pesquisar arquivos e projetos, sem chamar comandos de terminal. Diagnóstico manual de terminal usa o texto de saída que o próprio usuário encaminha ao agente.
- [x] A tela Terminal possui campo/botão de ajuda que encaminha pergunta e saída recente ao agente principal.
- [x] Edições normais feitas pelo agente do chat aplicam `write_file`/`patch_file` sem pedir autorização por arquivo; exclusões, importações e operações Git protegidas continuam exigindo confirmação.
- [x] O Editor sincroniza arquivos salvos após ações do agente, preserva rascunhos não salvos e diferencia falhas de leitura de uma remoção confirmada pela lista de arquivos.
- [x] `read_file` aceita trechos limitados por `start_line`/`max_lines` para inspecionar arquivos grandes sem retornar o arquivo inteiro.
- [x] O agente principal opera Git por uma camada JGit própria no projeto (status, diff, log, branches e operações locais autorizadas), sem depender da tela Terminal; a navegação Git abre um painel Git local separado.
- [x] A camada Git JGit e o painel Git separado do Terminal compilam na build Android.
- [x] Testado manualmente com Kotlin/JDK o roteamento chat→Build e as proteções do shell manual (diretório inicial, comando vazio, estado de saída e limite de saída). Isso não equivale à suíte Gradle/Android completa.
- [x] Validador estático do projeto gerado verifica versões dos plugins, namespace/applicationId/pacote, Manifest MAIN/LAUNCHER exportado, alvo JVM 17, marcadores de código incompleto, `setContent` vazio, telas delegadas sem controle visual e uso de `WebView`; a cópia de teste local cobre configurações válidas/inválidas. A suíte JUnit completa ainda aguarda Gradle.
- [x] Pesquisa web HTTPS, descoberta/listagem de repositórios públicos do GitHub, leitura de arquivos e importação limitada após confirmação quando o pedido é importar; pesquisa web/GitHub só é roteada quando explicitamente solicitada na mensagem atual.
- [ ] Testar instalação e uso no Poco F6 real.

## Fase 2 — Motor de IA local
- [x] Contrato comum para motores e estados explícitos de erro/indisponibilidade.
- [x] Adaptador inicial LiteRT-LM.
- [ ] Importar modelos adicionais .litertlm (não está exposto no motor atual).
- [x] Empacotar um modelo local pequeno no APK, copiar para a pasta privada e validar tamanho e SHA-256 ao iniciar.
- [ ] Segundo perfil Qwen Coder e alternância entre modelos (não implementados nesta revisão).
- [ ] UI para ver tamanho/remover/importar modelos (não está exposta nesta revisão).
- [ ] Obter uma resposta real com modelo compatível no Poco F6.
- [ ] Medir RAM, tempo, aquecimento e qualidade.
- [x] Manter o chat no modelo local; ferramentas web/GitHub separadas e acionadas somente por pedido explícito.
- [x] Ciclo limitado de ferramentas no chat para consultar workspace, ler/editar arquivos e usar web search quando a permissão separada de internet estiver ativa; edições normais são automáticas após pedido claro, enquanto exclusões, importação e alterações Git pedem aprovação.
- [ ] Melhorar avaliação de planos multi-etapas, recuperação de erro e confirmação de resultado no próprio aparelho.

## Fase 3 — Construtor de aplicativos
- [x] Gerar código de projeto estruturado com o modelo local empacotado.
- [x] Validar caminhos/tamanho e salvar dentro do workspace privado.
- [x] Validar previamente a consistência estrutural e versões do projeto gerado, e tentar uma correção automática quando essa validação falha.
- [x] Listar, editar, apagar e exportar projetos em ZIP.
- [x] Adicionar o primeiro pipeline de compilação local (ECJ → D8 → manifesto binário → APK assinado/verificado) e ligá-lo ao agente do chat. O construtor é interno e dispara automaticamente em pedidos de criação; a lista Apps não tem botão para iniciar builds. O pipeline inicial compila apps Java nativos sem bibliotecas externas.
- [ ] Nenhuma compilação de projeto criado deverá depender de GitHub Actions, token, servidor remoto, Replit ou outro serviço de build.
- [x] Fixar o perfil inicial Java/framework Android e as dependências ECJ, D8/R8, apksig; obter `android.jar` por download consentido e verificado. Kotlin/Compose, recursos e bibliotecas de terceiros ficam fora do primeiro perfil.
- [x] Limitar fontes Java e resultados de build; executar compilação fora da UI e publicar progresso/diagnósticos no chat.
- [ ] Medir CPU, RAM, temperatura, duração e armazenamento no Poco F6.
- [ ] Recolher erros reais de build e permitir correções automáticas controladas.
- [x] Implementar verificação de assinatura e identificador de pacote antes de anunciar sucesso, e ação de instalação via FileProvider/instalador Android.
- [ ] Executar builds reais e instalar APKs gerados no aparelho de destino.
- [x] Fazer o fluxo de Build do chat iniciar a geração e a compilação local automaticamente; manter ZIP como exportação secundária e APK como resultado principal.

## Fase 4 — Ferramentas nativas
- [x] Editor local de código para arquivos gerados.
- [x] Terminal de workspace com operações internas seguras/Git e shell manual separado sob confirmação.
- [ ] Testar no Android real o shell manual, interrupção por timeout e limite de saída.
- [x] Conectar o resultado/diagnósticos do pipeline Java APK ao chat e à ferramenta do agente; o terminal é opcional.
- [ ] Testar em dispositivo ARM64 o ciclo build → erro → correção → build e o fluxo de instalação.
- [x] Git local por JGit com painel próprio, sem depender de serviço remoto ou da tela Terminal para compilar.
- [ ] Agente de depuração que lê logs locais, propõe correções e repete builds limitados.
- [ ] Interface de atividade do núcleo com estados verdadeiros.
- [ ] Overlay/órbita flutuante opcional e discreta.
- [ ] Automação de jogos somente após implementar percepção, ações autorizadas e avaliação de resultados.

## Fase 5 — Segurança e testes
- [x] Armazenar conversas e memória em área privada do app.
- [x] Controlar tamanho do histórico e dos projetos.
- [x] Bloquear caminhos de arquivo fora do workspace.
- [x] Não embutir chaves online no APK.
- [x] Desativar backup Android dos dados do aplicativo.
- [ ] Testes unitários/instrumentados de memória, histórico, parsing, escrita e build.
- [ ] Testar sem internet depois que o modelo estiver instalado.
- [ ] Testar pouco espaço, memória insuficiente, modelo incompatível e build interrompido.
- [ ] Testar no Poco F6 sem PC.
- [ ] Assinar release final e criar processo local de backup/recuperação.

## Critério de conclusão

O projeto não estará completo até construir localmente no próprio telefone um projeto Android gerado pela IA, capturar e corrigir erros, produzir um APK instalável e mostrar o APK como resultado principal. Uma compilação anterior do próprio Kellvyn ou a exportação de um ZIP não prova que essa capacidade já existe.
