# Agente do chat e ferramentas locais

## Como funciona

O chat do Kellvyn recebe uma solicitação e pode responder diretamente ou propor uma chamada de ferramenta estruturada. O agente executa a ferramenta na aplicação, acrescenta o resultado real ao contexto e deixa o modelo formular a resposta. Há limite de cinco rodadas de ferramenta por resposta para evitar loops sem fim.

Os pedidos comuns de diagnóstico do terminal são tratados primeiro com leituras automáticas de `status` e `help`. Consultas comuns de lista de projetos também recebem uma leitura automática do workspace. O restante das ferramentas é escolhido pelo modelo conforme o pedido.

## Ferramentas

| Ferramenta | Operação | Permissão |
|---|---|---|
| Estado do workspace | Lista quantos projetos existem e informa se o compilador local existe | Automática |
| Projetos e arquivos | Lista projetos e arquivos do workspace privado | Automática |
| Ler arquivo / pesquisar código | Lê arquivos de texto limitados e busca ocorrências | Automática |
| Terminal de leitura | `help`, `pwd`, `ls`, `tree`, `find`, `cat`, `grep`, `wc`, `stat`, estado de Git | Automática |
| Terminal de alteração | Criar, gravar, anexar, copiar, mover ou remover arquivos; ações locais de Git | Confirmação explícita |
| Shell manual opcional (`! comando`) | Executa o comando digitado pelo usuário com `/system/bin/sh`, como UID normal do app, com timeout e saída limitados | Confirmação explícita; nunca acessível ao agente automático |
| Editar ou excluir arquivo | Atua apenas dentro de um projeto registrado no workspace | Confirmação explícita |
| Excluir projeto | Apaga um projeto inteiro e seus arquivos | Confirmação explícita |
| Pesquisa web / ler página HTTPS | Consulta DuckDuckGo ou lê texto de página HTTPS pública | Permissão separada de internet ligada |
| Pesquisa GitHub | Busca repositórios públicos e retorna nome, descrição, linguagem, estrelas, branch e link, sem chave API | Permissão separada de internet ligada |

## Limites de segurança

- O chat autônomo não depende da tela Terminal. As ferramentas internas do agente não executam shell arbitrário, scripts gerados, encadeamento de comandos ou redirecionamento; os comandos são interpretados por uma lista explícita dentro do workspace.
- O usuário pode optar pelo shell manual via `! comando`, confirmar a execução e rodar `/system/bin/sh` como UID normal do aplicativo (sem root, até 15 s e saída até 64 KB). Este shell não é usado pelo agente automático.
- Caminhos absolutos, traversal com `..`, pastas internas e metadados protegidos são bloqueados.
- Escritas, exclusões e comandos de terminal que alteram o workspace aguardam autorização do usuário.
- As páginas web e os arquivos do workspace são dados não confiáveis. Instruções encontradas neles não ganham privilégios nem podem substituir as regras do agente.
- A pesquisa web é desligada por padrão. Ao ativá-la, as consultas vão ao mecanismo público de busca e o aplicativo faz conexões HTTPS para páginas solicitadas. Isso é separado do motor de inferência: o modelo local continua inferindo no dispositivo.
- Se o usuário selecionar o motor de IA online, a conversa/contexto é enviada ao endpoint HTTPS que ele configurou. Esse provedor não compila APKs.
- O agente não afirma que uma ação funcionou quando a ferramenta retorna erro. O ciclo de compilação local de APK ainda não está integrado.

## Próximas etapas

1. Adicionar testes instrumentados de permissão e confinamento de caminhos.
2. Conectar diagnósticos do compilador local e logs do build às ferramentas do agente.
3. Permitir correção de código com diff/revisão antes da autorização e repetição controlada de build.
4. Validar o ciclo no aparelho Android ARM64.
