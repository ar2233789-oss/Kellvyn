# Regras permanentes para o Copilot — Kellvyn IA

Leia estas instruções antes de planejar, editar, testar ou abrir PR. Elas registram os requisitos do usuário; não os substitua por suposições.

## 1. Regra máxima: conservar o layout Lovable existente

A interface Lovable já existe e é a referência visual oficial. **Não recrie, redesenhe, simplifique, modernize, reorganize ou substitua a interface.** Preserve como está a estrutura visual e o comportamento já aprovado: árvore dos componentes, posições, dimensões, espaçamentos, cores, tipografia, textos, ícones, botões, menus, barras, painéis, estilos, brilho, transições e animações.

Não mude CSS, classes visuais, markup/JSX, ordem dos elementos, rótulos, ícones ou posição de controles para “facilitar” a integração. Não remova e não acrescente controles. Não use uma tela genérica de chatbot como substituta. Antes de qualquer alteração, confira o estado atual, o histórico/branches e os arquivos existentes; não assuma que README antigo ou uma versão nativa incompleta é mais confiável do que a estrutura Lovable real.

A integração pode exigir ajustes técnicos mínimos em handlers, estado, persistência, serviços ou chamadas de ferramenta, inclusive dentro de um componente React existente. Nesses casos, mude somente a lógica indispensável e preserve inalterados a estrutura visual, os estilos, os elementos, os rótulos e suas posições. Não imponha uma regra cega de “nunca tocar em src/**” que impeça conectar funções; a regra é **não alterar visualmente a interface**. Ao terminar, liste arquivos alterados e demonstre que nenhuma mudança visual foi feita.

## 2. Animações: conservar e permitir combinação

- A chuva digital, a neve, os raios/relâmpagos e quaisquer outros efeitos que já existam devem continuar com a aparência, movimento, intensidade, camadas e temporização originais do Lovable. Não substitua o efeito por uma animação inventada nem remova um efeito para implementar outro.
- **Chuva e raios podem ficar ativados ao mesmo tempo.** Não trate chuva e raios como escolhas mutuamente exclusivas. Implemente a composição simultânea usando as animações existentes, mantendo cada uma com sua aparência original. Preserve também a neve e os demais efeitos existentes.
- O indicador/animação de cérebro pensando **já foi criado pelo Lovable**. Reutilize exatamente o indicador existente (incluindo o componente `ThinkingBrain`, quando presente), exibido abaixo das respostas enquanto a IA está processando. Não o recrie, não o mova, não o substitua por texto/lista de raciocínio longa, não mude seu estilo e não crie um segundo indicador. Ele deve refletir o estado real do processamento.

## 3. Existe uma única IA Kellvyn

Existe **uma única assistente**: Kellvyn IA. Não criar, mostrar ou oferecer modos/modelos alternativos, seleção ou troca de núcleo, múltiplas identidades, agentes visíveis, menu de provedores, seletor de modelos, medidor de tokens ou planos de serviço. Não inserir opções “modo leve”, “modo pesado” nem Qwen Code/Qwen Coder. Essas expressões não podem virar opções, funções ou rótulos da interface.

Ferramentas internas, compilador/construtor, compactador, motor local e módulos auxiliares são recursos técnicos invisíveis da mesma IA, não outras IAs. A Kellvyn IA decide internamente qual recurso técnico usar para a solicitação, sem pedir ao usuário para escolher um modelo ou agente. Plugins devem representar ferramentas/integrações úteis; não criar um mecanismo para trocar a IA principal por Gemini, Claude, Qwen ou outro modelo.

Identidade conversacional: falar em português brasileiro por padrão e se identificar como Kellvyn IA, não como Google, Google DeepMind, OpenAI ou outra empresa. No projeto, reconhecer Kellvyn como criador; “Deus da Ritalina” é como seu desenvolvedor/tutor superior prefere ser reconhecido. Não apresentar identidade de empresa ou inventar capacidades.

## 4. O chat principal é o centro de comando da IA

Os pedidos de conversar, raciocinar, pesquisar na Web, analisar anexos, programar, criar aplicativos, criar/editar bots, compilar projetos e acionar ferramentas devem ser recebidos e coordenados pela **conversa principal**. A IA deve entender o pedido e escolher internamente as ferramentas necessárias. Não criar outro chat, outro agente, outro centro de comando, painel autônomo ou menu adicional para iniciar tarefas. Não exigir que o usuário escolha manualmente construtor, compactador ou mecanismo de IA.

O chat principal é também o lugar onde o usuário pede ajuda à IA para operar o Termux/terminal. A IA só deve orientar ou executar uma tarefa de terminal quando isso for pedido explicitamente na conversa principal; não deve executar comandos por conta própria nem em segundo plano.

## 5. Termux/terminal: ferramenta independente para o usuário

O Termux/terminal **é independente e pode ser aberto e usado diretamente pelo usuário quando ele quiser**. Não condicione o uso do terminal a uma conversa com a IA e não transforme o terminal em agente de IA ou em outro centro de comando. Mantenha o botão e o acesso à ferramenta no local original, sem alterações visuais.

A regra “pedir pelo chat principal” aplica-se à **ajuda da Kellvyn IA com o terminal**: só oferecer/realizar essa ajuda quando o usuário pedir na conversa principal. A IA não deve abrir o terminal, executar comandos, instalar pacotes ou iniciar scripts automaticamente. A ferramenta deve funcionar como terminal real sempre que a plataforma Android permitir; se faltar uma integração, informe exatamente a limitação em vez de fingir execução. O editor de código é independente e serve para escrever, editar e salvar código/notas; não é construtor, agente ou terminal.

## 6. Botões e funções que já existem: preservar a aparência e conectar a função real

Use os controles já existentes nos mesmos lugares. Não acrescente menus, abas ou botões e não transforme utilitários em agentes. Preserve o comportamento visual original. Audite o código real antes de concluir o que cada controle faz.

- **Hambúrguer:** histórico de conversas; permite começar uma conversa e reabrir conversas guardadas. Não é um menu geral de ferramentas.
- **Três pontos:** configurações existentes, incluindo personalização de cores, conexão/Web e opções visuais e de proteção já previstas. A proteção reforçada fica na posição final prevista. Não inventar alertas redundantes ou novos passos de confirmação só para ativar o controle.
- **Botão “+”:** anexar imagens, documentos e arquivos suportados e controlar o acesso à Web. A preferência de Web deve ser a mesma em todos os locais onde já existe esse controle.
- **Microfone:** ditado/entrada de voz em português; transcreve para o campo e não envia automaticamente sem ação do usuário. A reprodução de voz nas respostas deve parar/continuar conforme o controle existente.
- **Indicador de cérebro:** reutilizar o componente e animação de pensamento já existentes abaixo das respostas; exibir apenas enquanto há processamento real.
- **Navegador:** abrir navegação/pesquisa real, usando a conexão do aparelho. Não apresentar navegação simulada como acesso à Web efetivo.
- **Editor:** escrever, editar e salvar trechos/arquivos/notas; não criar, executar ou compilar projetos por conta própria.
- **Terminal/Termux:** ferramenta independente do usuário, conforme a seção 5.
- **Bots / “manter bot ativo”:** preservar a barra/interruptores que já existem para manter cada bot ligado em segundo plano ou desligá-lo. Não substituir esse controle por Git, nem criar um controle visual novo. Ligar o interruptor deve iniciar/manter o processo real do bot correspondente; desligar deve pará-lo. A escolha deve persistir e o estado mostrado deve refletir o processo real, não somente um valor salvo em localStorage.
- **Apps/ferramentas/projetos:** preservar o botão e a superfície existentes. Pedidos para criar apps ou projetos são feitos no chat principal; a interface auxiliar não pode virar uma segunda IA ou um novo centro de comando.
- **Plugins/integrações:** se o controle já existir, mantê-lo visualmente igual e conectá-lo a ferramentas/integrações reais. Não usar esse espaço para trocar a IA principal ou cadastrar modelos/assistentes alternativos.

Os rótulos e posições exatos da estrutura Lovable existente têm prioridade sobre listas antigas. Não recrie uma barra de navegação a partir desta descrição: ela serve para definir as responsabilidades dos controles existentes, não para redesenhá-los. Em particular, não reintroduzir Git no lugar do controle “manter bot ativo”.

## 7. Bots e execução em segundo plano

A criação, escrita, depuração e configuração de bots deve ser solicitada no chat principal. O usuário pode depois usar a barra/interruptor existente para manter o bot ativo ou pará-lo. Cobrir os tipos de bot pedidos no projeto — Discord, Telegram e WhatsApp — somente através das APIs, bibliotecas e permissões realmente suportadas.

Não basta mudar um interruptor ou escrever “Ativo” no armazenamento local. Ligar/desligar precisa comunicar com um runtime de bot real, persistir a preferência e mostrar estados verificáveis (iniciando, ativo, falha, parado). Quando Android exigir serviço em primeiro plano, notificação ou restrições específicas para manter atividade, implemente a integração necessária sem alterar o desenho do controle existente e respeite as limitações do sistema. Não invente conexão, credenciais, mensagens entregues ou execução. Se faltar token, sessão, permissão, backend ou API, explicar o requisito específico e não declarar o bot funcional.

## 8. Cérebro, arquivos, Web e construção de projetos

- **Offline primeiro:** priorizar um único cérebro local compatível com o aparelho. Destino do usuário: Poco F6, HyperOS/Android, 8 GB de RAM e 512 GB de armazenamento. Não presumir que há computador disponível para o usuário.
- **Internet opcional:** quando o usuário habilitar o acesso à Web, usar a conexão Wi‑Fi/dados móveis do próprio celular. Ao desligar, cessar o acesso à Web. Não trocar de identidade/modelo nem adicionar provedor obrigatório, assinatura paga ou chaves secretas embutidas no APK. Se o recurso ainda não estiver conectado, dizer isso honestamente.
- **Conversa contextual:** manter o contexto da conversa ativa e o histórico salvo, conforme a implementação existente.
- **Imagens/documentos:** conectar seleção, anexação, leitura e análise real de imagem, PDF e outros tipos de arquivo suportados. Não alegar que leu o conteúdo se a ferramenta não o processou.
- **Geração de imagem:** só anunciar ou retornar imagem quando houver implementação real de geração conectada; não responder apenas com a promessa de que pode gerar.
- **Apps e APKs:** a criação deve começar no chat principal. Construtor e compactador/otimizador são internos, invocados pela IA quando necessários, nunca expostos como opções separadas. Gerar artefatos reais, compilar e validar. O objetivo é APK Android nativo instalável, não apenas uma página web, preview, PWA ou WebView vendida como app nativo.
- **Acesso e compartilhamento:** manter as ações de copiar, ouvir, baixar e compartilhar que já existem; habilitá-las somente quando a ação correspondente for real.
- **Sem ZIP como entrega:** entregar o APK diretamente pelo GitHub Release ou link direto de artefato quando houver build válido. Não substituir o APK por ZIP.
- **Assistente flutuante, acessibilidade e jogo:** preservar os controles e a animação/identidade existentes; conectar cada opção às permissões/serviços Android reais quando solicitado. Não afirmar que a permissão está ativa sem verificá-la.

## 9. Configurações e preferências

Preservar as opções já previstas na interface original, inclusive paletas de cor e cor HEX personalizada, controle de Web, efeitos visuais, memória local explicitamente salva, assistente flutuante, leitura de tela/acessibilidade, modo de jogo e proteção reforçada. Os estados e preferências devem persistir realmente quando a função assim exigir. Efeitos devem ficar atrás do conteúdo e não prejudicar a leitura. Não treinar ou alterar silenciosamente o modelo por meio da memória local.

## 10. Regras de engenharia e validação

1. Antes de editar: inspecionar a branch, histórico, arquivos, dependências e alterações preexistentes. Preservar o trabalho válido.
2. Trabalhar em commits/branches rastreáveis e limitar a alteração ao objetivo técnico.
3. Para cada controle, rastrear o caminho completo: interação → lógica → serviço real → retorno de estado → mensagem honesta ao usuário.
4. Não usar dados falsos, localStorage ou temporizadores como substitutos para inferência, Web, processo de bot, terminal, compilação ou serviço em segundo plano real.
5. Executar os testes e build disponíveis; verificar o APK (arquivo, pacote e assinatura) antes de publicar. Não alegar teste no aparelho se não foi feito.
6. Antes/depois, revisar o diff visual: não houve mudança na composição, estilos, cores, animações, textos, ícones, controles ou posições do Lovable.
7. No relatório final, listar arquivos alterados, funções realmente ligadas, testes executados, resultados e limitações/bloqueios. Não dizer “pronto” se ainda for somente código parcial ou se a função não foi validada.

## Ordem de prioridade

1. Layout Lovable e animações originais intactos.
2. Chuva e raios podem operar simultaneamente; reutilizar o cérebro animado que já existe.
3. Uma única IA, sem modos/provedores/modelos alternativos.
4. Chat principal coordena todas as tarefas de IA; terminal permanece independente e é usado diretamente pelo usuário quando ele quiser.
5. A IA só ajuda com Termux/terminal quando for solicitada no chat principal.
6. A barra existente “manter bot ativo” controla processos reais em segundo plano e nunca é substituída por Git.
7. Implementação verdadeira, build/testes comprovados e nenhuma capacidade inventada.


## 11. UI audit and strict ban on post-Lovable controls

The user explicitly orders a forensic audit of every visible control in the **current main layout** and removal of controls/options added after the approved Lovable layout. This is a narrow exception authorizing removal of proven non-Lovable extras, not a redesign.

Before changing UI:
1. Inspect current `main`, all relevant branches, commit ancestry, diffs, and the actual `KellvynApp.tsx`, `DigitalRain.tsx`, store/settings, and styles. Identify the last confirmed Lovable visual edits, including commits marked with `X-Lovable-Edit-ID`. Important Lovable-origin history includes `2b2a755200fb9ca37ef14d11280902673d2dd89a`, `65b1939caafbe1076bdfb48b552df609d67f06da`, `0d7e71061e16442ddd72409d38a2c19d8df55d28`, `05c45a6eda914d8bd2aeb11f785c44eff53775bc`, `f462211f27d7dacfc305a196d7b3564e19658ea4`, `c99c58e4c2ebd6d0e78147018d1c4d77bd2a480e`, `8ea07f5e167bc8d157d5e6954e80c826609e7726`, `7a8ab8401ffdca68d68f6df32a03cd36f9e2d727`, and `6f3c64fb4c6b1623fb7ffd8344ad4a5d48de2bad`. Do not mistake a later Lovable-approved revision for an unauthorized addition just because it differs from the first UI commit.
2. Build a private audit inventory (in the task report, not a new user-facing panel) of each existing visible button/menu/option: its label/icon, exact location, associated handler, commit where it appeared, and whether it belongs to the last approved Lovable UI.
3. Compare the current interface against the last approved Lovable state and screenshots/reference assets already in the repository. Remove a visible button/option only when a diff/history review shows it was introduced by a later non-Lovable change and was not part of the approved UI. If commit provenance or visual intent is ambiguous, do not delete it speculatively; report the ambiguity and ask rather than damaging the approved layout.
4. Do **not** add buttons, labels, tabs, menu entries, settings, badges, modes, floating controls, or options. Do not create new visible UI to expose backend functions. Existing controls only.
5. Do not create parallel navigation or new options to “make a function accessible.” Connect the requested technical behavior to controls already present in the approved Lovable UI. If a requested capability has no existing control, trigger it through the main chat and keep its implementation internal; do not add a control.
6. Preserve the exact labels, order, placement, styles, touch areas, animations, and visual behavior for every control that remains. Do not replace one function/button with a new design. In particular, preserve the existing bot keep-alive bar/controls, the original animated thinking-brain component, and simultaneous rain + lightning effects.
7. After changes, show a control-by-control audit and list exactly which proven post-Lovable extras were removed and which commit introduced each one. If no control can be proven to be a non-Lovable addition, remove none. Run build/tests and verify the final diff adds **zero new visible controls/options** and contains no unauthorized changes to layout or animations.

## 12. Finish the implementation, not just the audit

The requested end state is the existing Lovable UI with one working Kellvyn IA behind it. After the control audit, complete the brain/tool integrations through the main chat and the user's existing controls: contextual chat; on-device/offline local model; optional web access over the phone's network when enabled; real attachment/file/image/PDF processing where supported; real image generation only if an actual generator is integrated; code editing; APK/app generation and build verification; bot creation/configuration and real keep-alive start/stop controls; speech input/output; history/settings/memory; and Android-native integrations supported by the project. Keep compiler/builder/compactor internal.

Do not stop at an audit document or a generic plan. Make the required technical changes, test what can actually be tested, fix failures, and open a focused PR. Build/package validation is not proof of successful on-device inference or bot operation; report those separately and accurately.
