# Memória persistente do operador — Studio de Produção

Ler esta memória antes de trabalhar. Ela preserva as regras do usuário entre sessões.

## Regras permanentes da Kellvyn

- O app de destino é Kellvyn IA, Android nativo em Kotlin/Jetpack Compose. Não usar WebView no APK final.
- A interface original aprovada do Lovable é a referência visual. Preservar composição, cores, ícones, efeitos, animações e comportamento já aprovados. Não redesenhar nem adicionar controles por iniciativa própria.
- O Studio é a oficina interna que o assistente operador utiliza. Não converter suas ferramentas em abas ou botões adicionais dentro da interface principal da Kellvyn IA.
- A oficina contém Estúdio Visual, Biblioteca de Recursos, Engenharia e QA. A Biblioteca possui 37 paletas/296 cores, 24 presets de materiais, 14 ambientes de luz/sombra, 9 primitivas extras, 12 curvas de animação e protótipos de partículas para chuva, neve, neblina e relâmpagos. Reutilizar essas ferramentas e a prévia climática antes de recriar recursos. A prévia climática é Canvas 2D; não é efeito Android nativo nem cópia confirmada da chuva do Lovable.
- Fazer uma mudança relacionada à tarefa atual por vez: inspecionar → alterar minimamente → comparar diff → testar → registrar evidências e pendências.
- A prévia web é ferramenta de inspeção e modelagem; ela não substitui o APK Android nativo.
- A compilação de aplicativos que a Kellvyn cria deve ocorrer no próprio aparelho, conforme a especificação do produto. O laboratório virtual do Studio testa um APK já compilado e nunca compila APK.
- Não entregar ZIP como substituto do APK. Não declarar função concluída só porque há código, botão ou documentação.
- Nunca inventar logs, métricas, progresso, sucesso de instalação ou aprovação. Quando o teste físico não aconteceu, declarar isso explicitamente.
- Reutilizar bibliotecas e ferramentas compatíveis com suas licenças. Não copiar ferramentas proprietárias sem autorização.
- Não colocar tokens, chaves ou credenciais na memória, no código, em capturas ou em artefatos de teste.

## Perfil de aparelho alvo

O arquivo-fonte do perfil está em studio/memory/POCO_F6_PROFILE.json. Configuração informada pelo usuário: Poco F6, HyperOS/Android 15, 8 GB de RAM e 512 GB de armazenamento.

A referência oficial da POCO informa painel de 6,67 polegadas, 2712 × 1220 pixels, 446 PPI e taxa de atualização de até 120 Hz: https://www.po.co/global/product/poco-f6/specs/

## Significado dos resultados

- **Prévia visual:** mostra a representação no navegador. Não prova comportamento Android.
- **Teste unitário/estático:** comprova apenas as regras executadas.
- **Emulador virtual:** exercita o APK numa instância Android AOSP. Não reproduz HyperOS, Snapdragon/Adreno físicos, toque real, temperatura, consumo de bateria ou throttling do Poco F6.
- **Teste físico:** só pode ser declarado depois de executar no Poco F6 real e guardar a evidência correspondente.
- **Fidelidade visual:** não prometer 100% com base somente em uma resolução configurada ou screenshot. Comparar capturas em condições documentadas, interações e animações; registrar o que não foi possível simular.

## Rotina de trabalho visual

1. Abrir a referência original e a implementação atual.
2. Registrar a resolução, a escala e o contexto das capturas.
3. Utilizar a Biblioteca de Recursos: pesquisar paletas por família/cor, testar materiais e iluminação na cena, escolher a curva de animação e prototipar efeitos climáticos com presets e controles antes de preparar parâmetros para o adaptador nativo.
4. Comparar antes/depois e fazer uma alteração por vez.
5. Validar na prévia ou no emulador conforme a área testada.
6. Separar evidências de emulador das evidências do aparelho físico.
7. Não alterar a estrutura aprovada da interface só para fazer a prévia parecer correta.

## Laboratório virtual

Consultar studio/device-lab/README.md. O workflow .github/workflows/studio-poco-f6-lab.yml inicializa um emulador Android 15, instala um APK de release já existente e recolhe screenshot, hierarquia de UI, logs e uso de memória. Não compila o APK.

A página do resultado identifica o APK e seu SHA-256; usar a evidência correspondente à execução, nunca assumir que uma captura antiga valida um novo commit.

## Registro operacional — atualização de 2026-11-20

### Escopo vigente do trabalho

- O único foco de desenvolvimento autorizado neste ciclo é o **cérebro da Kellvyn** e sua fundação própria. Preservar o trabalho existente e aprimorá-lo; não substituir a implementação existente por uma arquitetura inventada sem inspeção.
- Não desenvolver agentes, sistemas multiagente, laboratório de agentes ou orquestrador. Esse caminho foi explicitamente suspenso pelo usuário.
- Não retomar Copiloto.
- Não usar Gemma nem pesos, APIs de inferência ou modelos pré-treinados de terceiros como substitutos. A fundação própria deve manter inicialização/treinamento próprios e comprovação verificável.
- Na conversa, usar “cérebro da Kellvyn”; não chamar o projeto de “Brain” ou “Brian”.
- Não modificar a interface aprovada enquanto o escopo vigente for o cérebro. Mudanças visuais só com pedido explícito.

### Protocolo obrigatório de memória e auditoria

1. Antes de cada sessão, ler este arquivo e localizar a branch e os documentos ativos do trabalho atual no GitHub. Não assumir que a branch principal contém o trabalho mais recente.
2. Depois de cada decisão, correção de escopo ou marco real informado pelo usuário, atualizar este registro ou o diário de trabalho correspondente no mesmo ciclo. Não esperar uma “finalização” para registrar.
3. Cada entrada deve conter data, pedido/decisão do usuário, estado confirmado, alteração realmente executada, evidência (arquivo, commit, teste ou link) e próximo passo pendente.
4. Separar claramente: pedido do usuário, hipótese, trabalho planejado, alteração feita e teste comprovado. Nunca registrar intenção como progresso concluído.
5. A auditoria GitHub é documentação versionada, não captura automática da conversa. O operador deve reler e atualizar os arquivos manualmente usando o contexto da sessão; nunca afirmar que a conversa foi salva sem commit confirmado.
6. Não apagar decisões anteriores. Registrar correções como novas entradas datadas e marcar explicitamente o que foi substituído.
7. Se houver ambiguidade entre repositórios ou branches, inspecionar os arquivos e commits antes de escolher; não inferir pelo nome de arquivo ou usar snapshots antigos como estado atual.

### Diagnóstico da falha de continuidade em 2026-11-20

- O operador consultou apenas a branch principal de repositórios antigos e não abriu esta memória na branch `studio-production-suite`; por isso confundiu fontes antigas do app Android com o trabalho atual da fundação própria.
- O registro de progresso visual em `MainActivity.kt` usa estado temporário de UI e não substitui esta memória persistente.
- A rotina de auditoria inicial em `AssistantAgent.kt` é um preflight acionado por pedidos de finalização; não é um diário contínuo das decisões do usuário.
- A implementação `UserMemoryStore.kt` declara memória explícita, sem extração automática do chat. Ela também não substitui o registro operacional do GitHub.
- Correção permanente: consultar esta memória e os documentos/branches do trabalho antes de responder; registrar decisões e mudanças manualmente com commits confirmados.


## Fonte obrigatória para retomada do cérebro

- Antes de qualquer resposta ou edição relacionada ao cérebro da Kellvyn, abrir `docs/WORK_AUDIT.md` na branch `brain/from-scratch-foundation`.
- Abrir também os documentos apontados pela auditoria e conferir o HEAD/CI atual.
- A auditoria é o ponto oficial de retomada entre chats; atualizar manualmente após cada marco real, confirmar o commit e reler o arquivo.
- Escopo atual: cérebro próprio da Kellvyn; sem modelos pré-treinados, agentes, sistema multiagente, orquestrador ou Copiloto.
- A auditoria inicial foi criada em 2026-11-20 no commit `fa7b668d2f5fe8847a4e2fb92fe06dea5d9bbb26`.


## Correção operacional — prioridade atual de auditoria

- Em 2026-11-20, o usuário corrigiu que a tarefa atual é trabalhar na auditoria, não no cérebro.
- Não alterar arquivos do cérebro durante a etapa de auditoria. Não improvisar mudanças técnicas nem presumir que documentos do repositório definem a próxima tarefa.
- Primeiro concluir e consultar `docs/WORK_AUDIT.md` na branch `brain/from-scratch-foundation`; depois conferir as decisões anteriores registradas e só então retomar implementação conforme o pedido concreto.
- Todo chat novo deve começar pela leitura da auditoria e desta memória, antes de responder ou escolher arquivos/projetos.
- A última correção de escopo está registrada no commit `719474793f54156daec146e888858a11ae4b61c7`.


## Trava de auditoria no repositório — 2026-11-20

- Ponto de entrada único criado em `AUDIT_START_HERE.md`, na branch `brain/from-scratch-foundation`.
- Workflow `.github/workflows/audit-gate.yml` criado na mesma branch. Ele confere a existência da auditoria e o marcador de fase; sinaliza como falha qualquer alteração técnica durante `AUDIT_ONLY`; quando houver autorização explícita, exige atualização da auditoria no mesmo conjunto de mudanças.
- A fase registrada atualmente em `docs/WORK_AUDIT.md` é `AUDIT_ONLY`. Nenhum código técnico está autorizado nesta etapa.
- Limite obrigatório: GitHub Actions valida commits/pushes/PRs; não consegue abrir arquivos dentro de uma nova conversa do ChatGPT. O operador precisa consultar explicitamente a auditoria e esta memória no início de cada sessão. Nunca afirmar que existe carregamento automático.
- O status de CI reprovado não impede sozinho push direto enquanto uma regra de proteção de branch que exija esse status não estiver ativada e verificada. Não alegar que a branch está protegida sem prova.


### Evidência da instalação da trava — 2026-11-20

- O workflow `.github/workflows/audit-gate.yml` foi refinado no commit `074608549c26ed74e0bb97b6c37496a601d5cce7`; ele lê a declaração de fase mais recente da auditoria.
- Validação local da sintaxe YAML e do script Bash: ambas passaram.
- A execução hospedada do workflow ainda precisa ser conferida em GitHub Actions. Não marcar como aprovada antes de ver o resultado real.
- A proteção da branch não foi configurada/verificada; uma falha de CI ainda não impede push direto sem regra de proteção que exija esse status.
- O GitHub não pode forçar o ChatGPT a abrir o arquivo em cada conversa. A consulta precisa ser executada explicitamente no início de cada sessão.


### Retomada efetiva do cérebro — 2026-11-20, 19:04 UTC

- Pedido explícito do usuário: analisar e estudar todo o trabalho existente no cérebro da Kellvyn e continuar a implementação, sem desviar para tarefas paralelas.
- A auditoria da branch `brain/from-scratch-foundation` foi atualizada para `IMPLEMENTATION_AUTHORIZED` com escopo inicial concreto.
- Incremento implementado no commit `3ce225287f64b8ed33b7e120c6ee0e27dae07b33`: benchmark de tokenizer BPE contra baseline byte-level, relatório JSON com hashes do corpus/checkpoint/tokenizer, compressão, round-trip e tempo de codificação.
- Arquivos alterados: `brain-lab/src/kellvyn_brain/tokenizer_benchmark.py`, `brain-lab/tests/test_tokenizer_benchmark.py`, `brain-lab/README.md`, `brain-lab/pyproject.toml` e `docs/WORK_AUDIT.md`.
- GitHub Actions Audit Gate passou para o commit citado (execução `44799232794`). Na última consulta, o workflow Kellvyn Brain Lab ainda estava instalando PyTorch; não registrar seus testes como aprovados até conferir o resultado final.
- Próxima ação: confirmar os testes do laboratório; corrigir qualquer falha encontrada; então executar a medição com um corpus de avaliação real, autorizado e separado do treino. Não inventar resultados usando corpus sintético.
- O estudo de referências registrado em `docs/BRAIN_RESEARCH.md` inclui nanochat, OLMo-core/OLMo 3 e Qwen3 para treinamento/arquitetura; Android Studio/Gemini Agent Mode, Gemini CLI, OpenHands, Aider, SWE-agent e OpenCode para estudar contratos de execução e ferramentas; HELM para avaliação; ExecuTorch e MLC LLM para runtime Android. Isso é estudo de métodos e infraestrutura, não autorização para adotar modelos/pesos pré-treinados nem implementar agentes/orquestrador.
- Continuam proibidos: modelos/pesos/API de inferência externos como substitutos, agentes, orquestrador, Copiloto e alterações na interface Android não solicitadas.


### Continuação após validar o manifesto — 2026-11-20, 19:12 UTC

- O benchmark de tokenizer do commit `3ce225287f64b8ed33b7e120c6ee0e27dae07b33` passou no GitHub Actions: Audit Gate `44799232794` e Kellvyn Brain Lab `44799232788`, ambos `success`.
- Segundo incremento implementado no commit `f574a9e558e51724d22f377100054dbb57c4a239`: validador `kellvyn-validate-corpus` para manifesto de fontes de treinamento. Testa presença de campos de proveniência, data ISO, paths sem traversal/escape via symlink, SHA-256, tamanho, UTF-8 e quantidade real de documentos.
- Evidência: Audit Gate `44799783419` = `success`; Kellvyn Brain Lab `44799783664` = `success`, incluindo testes da fundação e smoke test de treinamento aleatório.
- Auditoria atualizada no commit `8e09285478bd8bc5f2c75b9adf5529db38869cca`.
- Limitações: nenhum corpus real foi fornecido ou medido; o validador ainda não está integrado como exigência no trainer; não ocorreu pré-treinamento de escala ou inferência Android.
- Próxima sequência: integrar manifesto ao trainer; separar de forma explícita o caminho de dados sintéticos para CI; tornar treino retomável com checkpoint periódico/estado aleatório/logs; só depois executar experimentos com corpus real cuja licença foi revisada.
