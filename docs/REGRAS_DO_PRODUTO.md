# Kellvyn IA — regras do produto

Definidas por Kellvyn (instruções de 15/10/2026). Em conflito com `README.md`, `PRODUCT_SPEC.md` ou `ROADMAP.md`, **este documento vence**. Quem mudar uma regra deve atualizar este arquivo no mesmo commit.

## 1. Proibido

Não reintroduzir, nem na interface, nem no código, nem na documentação:

- Conceito de token, cota ou limite de uso para o usuário.
- Troca de núcleo ou de modelo: seletor de modelo, rótulos como "Build • GPT-5 Nano" e similares. Existe uma única Kellvyn IA.
- Modo leve/pesado, seletor de provedor ou endpoint, Qwen Code.
- Qualquer tela, botão ou opção do construtor/compilador. Só o chat principal aciona.
- Compilar os apps criados na nuvem, com Replit ou GitHub Actions. A compilação é local, no aparelho.
- Entregar arquivo compactado no lugar do APK.

Não são "tokens de uso" e continuam existindo: o orçamento interno de contexto do modelo local (limite técnico, invisível ao usuário) e o campo onde o usuário cola o token do bot de Discord/Telegram (exigido pelas plataformas).

## 2. Interface

- Tela principal: o chat.
- **☰ (três barrinhas):** só abre as conversas.
- **⋮ (três pontinhos):** configurações — cor, efeito de chuva digital etc.
- **Barra inferior:** AI, Navegador, Editor, Terminal, Git e **Apps** (o item "Abas" do mockup passa a se chamar Apps). Editor e Git ficam como no mockup, a menos que o Kellvyn peça para tirar. Esta regra substitui o "não recriar abas inferiores" do `PRODUCT_SPEC.md`.
- **Apps:** mostra os apps que a IA criou e permite abrir e instalar.
- **Navegador:** simples, no estilo Brave/Google (barra de endereço e navegação básica). A proibição de WebView continua valendo para a interface do app e para os apps gerados; a única exceção é esta tela. (A confirmar pelo Kellvyn.)
- **Terminal:** tela independente, para o usuário usar quando quiser (Termux oficial). O usuário também pode só pedir ao chat principal, que executa com confirmação por comando.
- **Wi-Fi:** um único atalho que abre as configurações de Wi-Fi do próprio Android. Sem provedor, sem assistente, sem avisos repetidos.
- **IA flutuante:** opção de assistente pessoal em sobreposição.
- **Plugins:** a IA deve aceitar plugins. Formato a definir. Nunca como tela de construtor.

## 3. Comportamento da IA

- Uma única Kellvyn IA, offline e online. O online é só ferramenta do chat, usando a rede do celular.
- Conversa normalmente. Só cria app, jogo ou bot quando a pessoa pedir.
- Tudo pelo chat principal: apps, jogos complexos e bots de Discord, WhatsApp e Telegram. O APK pronto é entregue no próprio chat, para instalar.
- Sem limite de uso.
- Respostas boas e capacidade de criar jogos complexos. O modelo atual (SmolLM2-360M, ver `docs/LOCAL_AI.md`) é só de entrada e não cumpre isso; o requisito é um único modelo mais capaz, sem seletor.
- Bot de WhatsApp Business depende de webhook HTTPS público; hoje é entregue como código-fonte.

## 4. Estado em 15/10/2026

Levantamento por busca no código da `main` (`7de9240`). Nada foi validado no aparelho.

- A busca não encontrou: Qwen, "pesado", "provedor", "núcleo".
- "token" na `MainActivity.kt` só aparece em textos sobre token de bot de Discord/Telegram.
- Existe código do assistente flutuante (`FloatingAssistantService.kt`) e da ponte com o Termux (`TermuxBridge.kt`). README e ROADMAP também descrevem o runtime de bot do Telegram.
- A busca não encontrou: barra inferior com Apps, navegador, atalho de Wi-Fi, plugins.
- `PRODUCT_SPEC.md` ainda proíbe aba inferior e WebView; vale o que está acima.

## 5. Pendências (ordem sugerida)

Só marcar `[x]` depois de build e teste reais no aparelho.

- [ ] Extrair telas do `MainActivity.kt` (cerca de 160 KB) para arquivos menores.
- [ ] Barra inferior com Apps; remover qualquer seletor de modelo.
- [ ] Atalho de Wi-Fi.
- [ ] Tela Navegador.
- [ ] Tela Terminal independente.
- [ ] Plugins.
- [ ] Validar IA flutuante e runtimes de bot no Poco F6.
- [ ] Modelo único mais capaz.
