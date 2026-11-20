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
