# Oficina de Produção Kellvyn

**Branch isolada:** studio-production-suite  
**Aplicativo Kellvyn:** não é modificado por esta oficina.  
**Versão da oficina:** 0.3.0 — biblioteca expandida de recursos, modelador de partículas e ferramentas reais de aplicação à cena; ainda não certificada como ferramenta de produção completa.

## Módulos

### 1. Estúdio visual — 2D / 3D / 4D experimental

- Viewport WebGL com Three.js e navegação por câmera; vista ortográfica superior 2D e perspectiva 3D.
- Geometrias iniciais (cubo, esfera, toro e plano), seleção por toque/clique, hierarquia de cena e transformações.
- Material físico básico (PBR: cor, metallic, roughness, opacidade e transparência).
- Luz ambiente/hemisférica e luz direcional, receptor de sombras, shadow map e liga/desliga de grade/sombras.
- Linha do tempo de 0 a 5 segundos, keyframes de transformação, reprodução e interpolação linear.
- Exportação/importação de uma cena JSON.
- A dimensão chamada **4D experimental** relaciona um parâmetro W ao tempo, cor e escala para exploração visual. Não é um solucionador matemático nem um motor completo de geometria 4D.

### 1B. Biblioteca expandida de recursos

- **37 paletas categorizadas** com mais de 280 amostras de cor em grupos como natureza, verdes Kellvyn, clima, cinema, metálicos, neon, pastel, contraste, sci-fi e tons quentes/frios; filtro por família, busca, seleção HEX, aplicação de paleta em objetos e paleta pessoal temporária.
- Exportação JSON conjunta das paletas, presets de material e presets de iluminação. A paleta pessoal permanece na sessão até ser exportada; não promete sincronização em nuvem.
- **24 presets de materiais** cobrindo plástico, cerâmica, metais, borracha, pedra, madeira, vidro/translúcido, obsidiana e superfícies emissivas. O preset atualiza propriedades do material selecionado no motor Three.js.
- **14 ambientes de iluminação** com cores/intensidades de luz hemisférica e direcional, métodos de sombra PCF suave/PCF rígida/VSM, resolução de shadow map e parâmetros de viés/raio. Compatibilidade e resultado variam conforme o navegador e a GPU.
- **9 primitivas adicionais**: cilindro, cone, cápsula, anel, octaedro, icosaedro, dodecaedro, tetraedro e nó toroidal, além das geometrias iniciais.
- **12 curvas de animação**: linear, ease-in/out/in-out, cúbicas, seno, back, bounce, elastic e degrau. A escolha modifica a interpolação efetiva da linha do tempo entre keyframes e é incluída nas configurações de cena.
- **Modelador de partículas climáticas** com prévia animada de chuva, neve, neblina e chuva com relâmpagos; controles de densidade, velocidade, vento, comprimento/tamanho, opacidade, brilho e cores; presets climáticos e importação/exportação JSON.
- O modelador climático desenha a prévia em Canvas 2D. Não é simulador físico de gotas, não é sistema de partículas Android nativo e não modifica nem substitui a chuva aprovada do Lovable. O JSON exportado é um contrato de parâmetros para adaptação futura, não um efeito integrado ao APK.

### 2. Bancada de engenharia de software

- Importação local de arquivos/pasta por seletor do navegador, com limite deliberado de tamanho e quantidade.
- Inventário de fontes, configurações Gradle, manifesto Android, specs de interface, testes e assets 2D/3D.
- Extração textual de dependências Maven/Gradle e sinalização de versões dinâmicas/SNAPSHOT.
- Preparação de patch por correspondência exata, verificação de ocorrência única e exportação de cópia modificada.
- Os originais permanecem como foram carregados; a oficina não escreve de volta no repositório ou no aparelho automaticamente.
- Os arquivos são analisados no navegador; não são enviados a um servidor pela oficina.

### 3. Laboratório de testes e relatório

- Autotestes de integração da interface e estado do motor visual.
- Análise estática de estrutura, manifesto, configuração de build, dependências, handlers de UI, marcadores TODO/FIXME, segredos aparentes, WebView, existência de testes e referências de paridade visual.
- Matriz explícita de alternativas de validação.
- Relatório JSON e Markdown com resultado PASS/WARN/FAIL, evidências textuais, caminho/linha quando disponíveis e limites declarados.
- Não atribui aprovação de compilação sem compilar; não afirma ausência de CVEs sem uma base de vulnerabilidades.

### 4. Memória persistente e perfil de dispositivo

- studio/memory/OPERATOR_MEMORY.md guarda as regras de trabalho entre sessões: preservar o layout Lovable, usar ferramentas existentes, editar com mudanças pequenas e não confundir prévia com teste físico.
- studio/memory/POCO_F6_PROFILE.json registra o alvo Poco F6 e as configurações do aparelho virtual.
- studio/device-lab/README.md explica como executar e interpretar os testes virtuais, bem como suas limitações.
- O perfil fica versionado no GitHub para que o operador possa relê-lo antes de cada sessão. Não contém credenciais.

### 5. Laboratório virtual de Android

- O workflow .github/workflows/studio-poco-f6-lab.yml prepara um emulador Android 15/API 35 com resolução de tela configurada para 1220 × 2712.
- Faz checkout isolado da branch `main`, encontra uma execução bem-sucedida de `Build Kellvyn APK` para o mesmo SHA do código, baixa o APK do release e verifica o SHA-256 antes de instalar. Essa execução oficial já executou `testDebugUnitTest` e `assembleDebug`; não há uma segunda compilação redundante no laboratório.
- O script de smoke test automatiza os controles existentes: chat offline, ações das respostas, Configurações, paletas, combinação chuva + raios, catálogo Apps, ferramentas de navegação, seletor de arquivos e ligação/desligamento do acesso à Web.
- Recolhe capturas, hierarquia da interface, logcat, resumo de memória, SHA-256 e o commit exato do código nativo testado.
- O APK de download só é publicado depois que as verificações de instalação, execução e controles passam; o emulador continua sendo AOSP, não HyperOS físico.

## Abrir e desenvolver

Sirva o diretório studio/ em um servidor estático HTTPS. Como os módulos são ES modules e o renderizador importa Three.js de uma CDN com versão fixa, abrir o arquivo HTML diretamente por file:// pode ser bloqueado pelo navegador. A primeira carga do renderizador precisa de conexão para buscar Three.js; depois de carregado, as operações de cena ocorrem localmente na sessão.

A primeira tentativa automática de ativar GitHub Pages foi bloqueada porque a conexão de automação não tem permissão administrativa para criar essa configuração. Para abrir o estúdio como site interativo, o proprietário precisa habilitar Settings → Pages → Build and deployment → GitHub Actions uma vez. Depois, abra Actions → Publish isolated production studio → Run workflow na branch studio-production-suite. A publicação é independente do pipeline do APK.

## Verificações automatizadas

Dentro da pasta studio, com Node.js 20 ou superior:

- npm test — testes estáticos de contrato da oficina.
- npm run check — sintaxe JavaScript e testes do contrato.

O workflow de QA usa Node.js, sem dependências de terceiros. Ele verifica a estrutura e sintaxe do código, mas não substitui teste interativo num navegador real/GPU.

## Limites atuais — não confundir com recursos concluídos

- O editor é uma bancada visual inicial, não substitui Blender, CAD paramétrico, Houdini ou um motor 3D profissional; não tem modelagem escultural, malha editável, rigging, render path-tracing nem solver 4D.
- O código lê projetos no navegador e cria uma cópia de arquivo alterado; não aplica patches a um clone Git, não resolve versões contra repositórios Maven e não compila projetos Android.
- A verificação de dependências é textual e não inclui feed de vulnerabilidades online.
- O laboratório virtual não é um Poco F6 físico: usa Android AOSP/Google APIs, não HyperOS, e não mede o Snapdragon/Adreno reais, temperatura, bateria ou toque físico. Teste em emulador não conta como teste físico.
- A densidade lógica de 440 dpi do perfil é provisória; 446 PPI descreve a densidade física da tela e não deve ser tratada automaticamente como densidade lógica do Android.
- O fluxo de transformação da interface Lovable em interface Android nativa ainda precisa de um adaptador de migração específico e validação de paridade. Esta oficina prepara inspeção e relatório; não declara essa conversão concluída.

## Migração da interface Lovable para Android nativo

A estrutura original foi importada para `studio/lovable-source/` como referência de 119 arquivos, incluindo a interface web e o projeto Android Java/Kotlin já existente.

- [Fonte original preservada](./lovable-source/)
- [Auditoria de controles, funções e animações](./porting/PORT-AUDIT.md)
- [Cópia de trabalho Android nativa](./native-port/android/)
- [Execuções de compilação e testes nativos](https://github.com/ar2233789-oss/Kellvyn/actions/workflows/studio-native-port-qa.yml)

A cópia de referência não deve ser editada durante a migração. As mudanças ficam em `studio/native-port/android/`, sem WebView de produção. A auditoria inicial encontrou diferenças concretas no painel dos três pontos, temas, densidade e temporização das partículas; a conversão integral e a validação visual no Poco F6 ainda não estão concluídas.

## Contrato para a próxima etapa

Antes de usar a oficina para mexer no Kellvyn: carregar o projeto de origem, executar QA, registrar o relatório, preparar um patch isolado, revisar o diff e só então promover uma alteração aprovada. Nenhuma alteração do aplicativo deve ocorrer como efeito colateral de importar ou executar o estúdio.
