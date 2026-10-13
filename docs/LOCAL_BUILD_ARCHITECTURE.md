# Arquitetura do mini Android Studio local — Kellvyn IA

## Decisão

A criação de APKs deve seguir a arquitetura de uma IDE Android que roda no próprio telefone, e não uma chamada HTTP que envia o projeto para um serviço de compilação.

A referência de engenharia consultada é o projeto aberto AIDE-OS, cuja documentação descreve dois caminhos locais: um pipeline rápido que combina AAPT2, compilador Java/Kotlin, D8 e apksig; e um motor Gradle executado localmente para projetos que dependem da configuração Gradle completa. AIDE-OS é GPL-3.0. Este documento registra somente a arquitetura estudada; não significa que seu código tenha sido copiado ou integrado ao Kellvyn.

## Fluxo proposto

1. O usuário conversa com o Kellvyn e escolhe **Build** na setinha do compositor.
2. O agente produz um projeto Android nativo, salva os arquivos no workspace privado e mostra a lista de arquivos.
3. O usuário pode revisar e editar o código, ver as abas abertas e usar o Git local.
4. O motor local verifica os pré-requisitos, baixa componentes de ferramentas publicados e fixados por versão/checksum quando necessário, e armazena-os no espaço privado do aplicativo.
5. A compilação ocorre no aparelho. Logs e diagnósticos reais são enviados para a interface.
6. Em caso de falha, o agente recebe os logs e pode propor correções controladas. Ele não deve sobrescrever arquivos sem validação.
7. O APK resultante é validado e assinado localmente e apresentado como resultado principal. ZIP continua sendo uma exportação secundária.

## Versões-alvo dos projetos gerados

A configuração padrão pretendida para projetos Android/Compose é: Android Gradle Plugin 8.7.3, Gradle 8.9, Kotlin Android/Compose 2.4.0, JDK 17, compileSdk/targetSdk 35 e minSdk 26. O gerador e seu validador compartilham essas versões para reduzir divergências. Segundo a matriz oficial do Kotlin 2.4.0, o plugin é compatível com AGP 8.5.2–9.1.0 e Gradle 7.6.3–9.5.0; o AGP 8.7 exige pelo menos Gradle 8.9. Referências: https://kotlinlang.org/docs/gradle-configure-project.html e https://developer.android.com/build/releases/about-agp. Essa configuração por si só não significa que o compilador local já esteja integrado.

## Requisitos do motor local

- Compatibilidade com Android ARM64 e as limitações do ambiente de execução Android.
- Nenhum job de compilação remoto, GitHub Actions, token, Replit ou outro serviço de build.
- Componentes de toolchain com versões e hashes verificados, instalação interrompível e recuperação de downloads incompletos.
- Respeito às licenças do Android SDK e dos componentes de terceiros.
- Limites de tempo, armazenamento, tamanho de projeto e memória; cancelamento seguro.
- Saída explícita em cada estado: não instalado, instalando, pronto, compilando, falhou ou APK validado.
- Não mostrar porcentagem de compilação se o motor real não tiver fornecido progresso.

## Estado atual verificável

Já existem o construtor de código, workspace privado, editor, lista de abas, terminal local de arquivos/Git e a interface com seletor de modo/motor. **A toolchain de compilação Android local ainda não está integrada ao Kellvyn.** Escrever arquivos ou exportar ZIP não constitui build, e um APK de uma versão anterior não valida o código atual.

## Sequência prioritária

1. Obter um caminho de compilação local ARM64 que possamos redistribuir legalmente e empacotar.
2. Integrar esse motor ao workspace sem mudar a geometria da referência visual.
3. Implementar instalação/verificação de toolchain e contrato de logs.
4. Compilar um projeto Java mínimo, depois um app Kotlin/Compose, com logs guardados localmente.
5. Validar assinatura, package ID e instalação do APK de saída no Android.
6. Conectar o agente de correção ao ciclo build → logs → alteração proposta → novo build.

## Critério de conclusão

O recurso só será considerado pronto depois que um projeto criado pela IA for realmente compilado no próprio telefone, que o APK passe pela verificação de assinatura e que a interface apresente o APK correto. O repositório da IA em si e um arquivo ZIP não satisfazem esse critério.
