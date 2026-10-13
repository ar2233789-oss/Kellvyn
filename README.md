# Kellvyn IA

Kellvyn IA é um aplicativo Android nativo em Kotlin/Jetpack Compose, em desenvolvimento para funcionar primeiro no próprio aparelho.

## Código implementado até agora

- Interface nativa preta/esmeralda alinhada à referência: cabeçalho compacto, dois indicadores, chat central, compositor em duas linhas com seletor Build/Conversa + motor, microfone/envio e seis abas inferiores AI, Navegador, Editor, Terminal, Git e Abas.
- Botão de copiar (ícone de folhas/páginas) embaixo das respostas do assistente.
- Entrada por ditado em português e leitura em voz alta, se os serviços de voz do Android estiverem disponíveis.
- Histórico local limitado e apagável.
- Memória explícita controlada pelo usuário: guardar, visualizar e apagar fatos.
- Adaptador inicial LiteRT-LM com dois slots locais: modelo leve de conversa (~374 MB) e Qwen2.5-Coder-1.5B quantizado (~1,12 GB). Em tarefas de código/Build, o Qwen é ativado temporariamente quando já estiver instalado, depois o app tenta restaurar o modelo anterior; nenhum download grande ocorre silenciosamente. A compatibilidade/desempenho no Poco F6 ainda precisa de teste real.
- Modo online opcional para respostas do assistente, com endpoint HTTPS configurado pelo usuário.
- Agente conversacional com ciclo limitado de ferramentas: consulta projetos/arquivos (inclusive trechos de arquivos grandes por número de linha), pesquisa na web, descobre repositórios públicos do GitHub, lista caminhos e lê arquivos por branch; pode importar até 30 arquivos textuais de um repositório público ao workspace após confirmação. A internet das ferramentas precisa estar autorizada. Para suporte opcional ao terminal, o usuário pode encaminhar a saída recente ao mesmo agente; o chat principal não usa nem precisa da tela Terminal. Para editar arquivos locais, pode aplicar patches exatos automaticamente após pedido claro, evitando reescrever o arquivo inteiro. Exclusões, importações e alterações Git pedem confirmação. Essa permissão é separada do motor de inferência: o modelo local continua local, mas a consulta/página solicitada é acessada externamente.
- Terminal manual opcional com atalho `! comando`, confirmação e limites de tempo/saída; dentro dele há um botão para enviar a saída recente ao agente principal pelo chat. O shell manual não é acessível ao agente automático.
- Construtor que gera arquivos de código pela seleção Build no chat, workspace privado, editor, navegador externo sem WebView e Git local via JGit. Antes de salvar, valida caminhos, estrutura mínima, versões esperadas, namespace/pacote, manifest e Compose; isso não substitui a compilação real.

## Requisitos que não podem ser violados

- O app final deve ser Android nativo; não embutir páginas da web em WebView.
- A criação de APKs deve ocorrer localmente no telefone. Não usar GitHub Actions, tokens, Replit ou serviços de build na nuvem para compilar os projetos criados.
- O código gerado pode ser exportado em ZIP para inspeção, mas ZIP não é APK.
- A tela principal deve privilegiar o APK compilado como resultado. A compilação é invocada diretamente pelo fluxo de Build do chat; o Terminal é opcional.
- Não fingir que recursos pendentes estão prontos.

## Escopo do primeiro pipeline local

A IA local ainda precisa de teste com um modelo real no Poco F6. O modelo pequeno baixado é uma opção de entrada para tarefas básicas, não um modelo avançado de programação. A qualidade, RAM, calor e velocidade ainda não foram medidos no aparelho.

O agente do chat usa APIs diretas para arquivos/projetos e uma camada Git via JGit separada, sem depender da tela Terminal; edições normais são automáticas após pedido claro, enquanto exclusões, importações e alterações Git exigem confirmação. O terminal manual é uma opção independente, com shell do usuário protegido por confirmação/limites, e oferece ajuda do mesmo agente usando o contexto fornecido pela pessoa. O fluxo de Build do chat e o construtor interno agora iniciam um pipeline local Java → DEX → APK assinado/verificado no próprio telefone. O agente também tem uma ferramenta de build direta, independente da tela Terminal, recebe o progresso e pode analisar os diagnósticos e tentar uma correção limitada após erro. A primeira build aceita apps Android Java nativos com `android.widget`, sem Kotlin/Compose, recursos externos ou dependências de terceiros. Na primeira execução, o download da plataforma Android pede aceite explícito da licença e verifica a integridade do arquivo. O app possui uma ação que abre o instalador nativo para o APK criado. A interface e o pipeline ainda precisam de uma build Android completa e teste no aparelho-alvo; as mudanças de código, isoladamente, não provam instalação/funcionamento. Testes isolados com Kotlin/JDK passaram para o roteamento de criação no chat, limites do shell manual e os sete cenários anteriores do validador estático. Acrescentei sete testes ao validador para permitir `placeholder` legítimo em UI e comentários comuns, detectar `FIXME`/`TODO()` e comentários XML pendentes, rejeitar `setContent` vazio, detectar tela delegada sem controles Compose e bloquear `WebView` em interfaces geradas. A regex foi testada isoladamente com 11 casos. A classe `BoundedSourceReader` compilou com Kotlin/JDK e passou seis verificações JVM de leitura por trecho; acrescentei seis testes JUnit para essa classe. A política de autorização também compilou com Kotlin/JDK e passou onze cenários para separar edições automáticas das ações protegidas; adicionei quatro testes JUnit para manter essa regra. A sincronização do Editor passou sete verificações JVM para atualizar/criar arquivos pelo chat sem perder rascunhos locais, e tem sete testes JUnit adicionais. A suíte JUnit completa ainda precisa ser executada num ambiente Gradle. Nada disso substitui uma compilação/teste Android completo.

O modo online opcional é separado da compilação. Se for configurado, a conversa/contexto vai ao endpoint HTTPS escolhido; a compilação de projetos não usará esse endpoint.

## Documentação
- [Especificação central do produto](docs/PRODUCT_SPEC.md)
- [Ferramentas do agente e permissões](docs/AGENT_TOOLS.md)
- [Motor de IA local](docs/LOCAL_AI.md)
- [Roadmap técnico](docs/ROADMAP.md)
- [Arquitetura de compilação local estilo mini Android Studio](docs/LOCAL_BUILD_ARCHITECTURE.md)

## Build do próprio Kellvyn durante o desenvolvimento
A compilação anterior do próprio app foi feita em ambiente de desenvolvimento e passou por verificações de pacote/assinatura. O fluxo de build em nuvem foi removido conforme o requisito de build local. Não há atualmente um APK novo validado para a revisão depois das últimas alterações.
