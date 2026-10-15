# Instruções para agentes de código (Codex, ChatGPT, Claude Code e outros)

Projeto: Kellvyn IA, app Android nativo (Kotlin/Jetpack Compose) com uma única IA local. Dono: Kellvyn. Responda sempre em português do Brasil.

Antes de qualquer alteração, leia `docs/REGRAS_DO_PRODUTO.md`. Em conflito com README, PRODUCT_SPEC ou ROADMAP, esse arquivo vence.

## Proibido (não reintroduzir, nem em texto de interface, nem em código, nem em documentação)

- Conceito de token, cota ou limite de uso para o usuário.
- Troca de núcleo ou de modelo: seletor de modelo, rótulos como "Build • GPT-5 Nano" e similares. Existe uma única Kellvyn IA.
- Modo leve/pesado, seletor de provedor ou endpoint, Qwen Code.
- Qualquer tela, botão ou opção do construtor/compilador. Só o chat principal aciona.
- Compilar apps criados na nuvem, com Replit ou GitHub Actions. A compilação é local, no aparelho.
- Entregar arquivo compactado no lugar do APK.

## Como trabalhar

- Não crie botão ou tela sem a lógica funcionando por trás ("botão sem tomada").
- Só marque algo como pronto depois de build e teste reais. Nunca finja que um recurso pendente está pronto.
- Outros agentes também commitam na `main`. Trabalhe em branch, atualize antes de editar e abra PR.
- Arquivos muito grandes (`MainActivity.kt`, `AssistantAgent.kt`) dificultam a edição: prefira extrair telas e classes para arquivos menores em vez de aumentar esses.
- Quando uma regra do produto mudar, atualize `docs/REGRAS_DO_PRODUTO.md` no mesmo commit.
