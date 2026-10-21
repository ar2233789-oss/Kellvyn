# Laboratório de dispositivo virtual — referência Poco F6

## O que este laboratório faz

O workflow do Studio inicializa um Android Emulator com Android 15/API 35, configuração de memória semelhante à meta de 8 GB e resolução de tela sobrescrita para 1220 × 2712 em orientação retrato. Ele compila o código nativo Android do commit testado, executa os testes unitários, instala exatamente esse APK, abre o pacote e recolhe:

- Captura de tela do app.
- Hierarquia da interface obtida por UI Automator.
- Logcat para investigar erros e encerramentos.
- Resumo de memória do processo.
- Identidade do APK testado e SHA-256.

O objetivo é dar ao assistente operador uma referência reproduzível para inspecionar layouts, overflow, posicionamento, mensagens de erro e inicialização. Os artefatos ficam associados à execução do GitHub Actions para que o resultado real possa ser revisado.

## O que ele não é

Não é uma instância real de Poco F6 disponível por conexão interativa contínua. É um emulador Android AOSP/Google APIs rodando num executor de CI. Não executa HyperOS nem reproduz o Snapdragon 8s Gen 3/Adreno, painel AMOLED de 120 Hz, precisão de toque, câmera física, temperatura, consumo de bateria ou políticas OEM reais.

A densidade lógica de 440 dpi é provisória. O PPI físico oficial (446) não deve ser confundido com o valor de densidade lógica Android. Assim que houver leitura do aparelho real, registrar o valor capturado e atualizar o perfil, sem supor que os dois valores são iguais.

## Como executar

1. No GitHub, abra Actions e selecione o workflow “Studio — Virtual Poco F6 smoke test”.
2. Execute “Run workflow” na branch studio-production-suite quando a opção estiver disponível. O workflow também é iniciado por alterações na pasta studio ou no próprio workflow.
3. O workflow faz checkout da branch `main` em `source-main/`, executa `./gradlew testDebugUnitTest assembleDebug` em `source-main/` e registra o SHA exato do código e do APK.
4. O script `studio/run-poco-f6-smoke.sh` instala esse APK e testa o chat offline, os rótulos/ações, Configurações, paletas, efeitos combinados e o menu de anexos/Web.
5. Abra os artefatos da execução para acessar as capturas, hierarquias, resultado por verificação e logs.

Se a compilação, os testes, a instalação ou a inicialização falharem, revisar o log da etapa correspondente. Não reutilizar um APK antigo para mascarar falha no código atual.

## Como interpretar a evidência

A captura pertence somente ao APK identificado no arquivo apk-sha256.txt e ao sistema virtual que consta no log. Uma falha ao iniciar deve ser investigada com logcat e hierarquia; um processo ativo sozinho não comprova que a tela e todas as funções estão corretas. Uma execução do emulador não substitui um teste físico no Poco F6.
