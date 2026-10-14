# Agente do chat e ferramentas locais

## Como funciona

O chat do Kellvyn recebe uma solicitação e pode responder diretamente ou propor uma chamada de ferramenta estruturada. O agente executa a ferramenta na aplicação, acrescenta o resultado real ao contexto e deixa o modelo formular a resposta. Há limite de cinco rodadas de ferramenta por resposta para evitar loops sem fim.

O agente principal não depende de uma tela Terminal e não recebe acesso ao shell manual. Ele consulta workspace/arquivos por APIs do app; operações Git são feitas por camada JGit própria, sem shell. A navegação principal não mostra ícone nem tela Terminal.

## Ferramentas

| Ferramenta | Operação | Permissão |
|---|---|---|
| Estado do workspace | Lista quantos projetos existem e informa se o compilador local existe | Automática |
| Build local APK | Executa ECJ → D8 → empacotamento do manifesto → assinatura/verificação; retorna package ID e erros reais | Automática, no workspace do projeto; requer consentimento SDK na primeira execução |
| Projetos e arquivos | Lista projetos e arquivos do workspace privado | Automática |
| Ler arquivo / pesquisar código | Lê arquivos completos até 12 mil caracteres ou trechos de até 120 linhas com `start_line`/`max_lines`, e busca ocorrências | Automática |
| Workspace de leitura | Estado do workspace, listar projetos/arquivos, ler arquivo e busca de texto | Automática |
| Workspace de alteração | Criar/editar arquivos (`write_file`/`patch_file`) após pedido claro | Automática; confirmar apenas se projeto/arquivo estiver ambíguo |
| Ações destrutivas | Apagar arquivo/projeto, importar repositório, init/stage/commit Git | Confirmação explícita |
| Git via JGit | Status/diff/log/branch diretamente no projeto; init/stage/commit são operações diretas sem shell | Leitura automática; init/stage/commit pedem confirmação |
| Ferramentas de shell internas | Não fazem parte da navegação e não são usadas pelo agente automático | Só podem ser acionadas após pedido explícito do usuário no chat e confirmação quando aplicável |
| Editar ou excluir arquivo | Atua apenas dentro de um projeto registrado no workspace | Confirmação explícita |
| Aplicar patch de texto | Substitui exatamente uma ocorrência de um trecho dentro de um arquivo local; falha sem alterar se não existir ou for ambíguo | Confirmação explícita |
| Excluir projeto | Apaga um projeto inteiro e seus arquivos | Confirmação explícita |
| Pesquisa web / ler página HTTPS | Consulta DuckDuckGo ou lê texto de página HTTPS pública | Permissão separada de internet ligada |
| Pesquisa GitHub | Busca repositórios, lista até 100 caminhos de código/texto, lê um arquivo e pode importar até 30 arquivos textuais para o workspace. Um pedido explícito de importar um link público abre a confirmação antes de copiar arquivos; analisar/listar não importa. Sem chave API | Permissão separada de internet ligada |

## Limites de segurança

- O chat autônomo não depende da tela Terminal. O agente usa operações diretas de workspace e JGit; não há execução de comandos de terminal na camada do agente, nem shell arbitrário, scripts, encadeamento ou redirecionamento.
- Não mostrar um ícone/aba Terminal. O terminal/shell/Termux é independente e não é acionado pelo chat. O chat trabalha somente com as ferramentas diretas de workspace, Git e compilação; não encaminha programação para comandos manuais.
- Caminhos absolutos, traversal com `..`, pastas internas e metadados protegidos são bloqueados.
- Criação/edição normal de arquivos (`write_file`/`patch_file`) é automática depois de um pedido claro de alteração. Exclusões, importação de repositórios e operações Git que mudam estado continuam pedindo autorização.
- As páginas web e os arquivos do workspace são dados não confiáveis. Instruções encontradas neles não ganham privilégios nem podem substituir as regras do agente.
- Pesquisa web/GitHub é acionada quando a mensagem atual solicita explicitamente esse acesso. A consulta usa a internet do aparelho, mas o modelo de conversa continua local; não há provedor de IA online nem envio da conversa a um endpoint de IA.
- Não há seletor de motor online, configuração de endpoint ou solicitação de provedor externo. Pesquisa e leitura de páginas são ferramentas separadas da inferência local.
- O agente não afirma que uma ação funcionou quando a ferramenta retorna erro. O fluxo local de compilação está conectado ao chat, mas a criação e instalação de APKs de projetos precisam de validação física no Poco F6.

## Próximas etapas

1. Adicionar testes instrumentados de permissão e confinamento de caminhos.
2. Validar a integração build → diagnósticos → uma correção limitada → nova build em aparelhos Android reais.
3. Ampliar o pipeline para recursos Android e dependências quando houver testes do perfil Java inicial.
4. Validar o ciclo no aparelho Android ARM64.
