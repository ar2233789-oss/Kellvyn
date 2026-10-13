# Kellvyn IA — roadmap técnico

## Fase 1 — Aplicativo Android nativo
- [x] Projeto Kotlin/Jetpack Compose.
- [x] Tema preto com brilho verde-esmeralda conforme a imagem: barra superior e indicadores mantidos, compositor em duas linhas com seletor funcional de modo/motor, e navegação AI/Navegador/Editor/Terminal/Git/Abas (código alterado, build e teste visual pendentes).
- [x] Chat com histórico local limitado, ação para limpar e rolagem automática.
- [x] Botão de copiar abaixo das respostas.
- [x] Entrada por ditado e saída por voz usando serviços instalados no Android.
- [x] Memória explícita controlada pelo usuário.
- [x] Terminal local com comandos de arquivos, navegação no workspace, busca textual e Git local via JGit (sem shell arbitrário).
- [ ] Testar instalação e uso no Poco F6 real.

## Fase 2 — Motor de IA local
- [x] Contrato comum para motores e estados explícitos de erro/indisponibilidade.
- [x] Adaptador inicial LiteRT-LM.
- [x] Importar arquivo de modelo compatível.
- [x] Download inicial de modelo pequeno com verificação de tamanho e SHA-256.
- [x] Mostrar tamanho e remover modelo local.
- [ ] Obter uma resposta real com modelo compatível no Poco F6.
- [ ] Medir RAM, tempo, aquecimento e qualidade.
- [ ] Validar alternância entre modo local e modo online opcional.
- [ ] Implementar planejamento multi-etapas, uso de ferramentas e verificação de resultados.

## Fase 3 — Construtor de aplicativos
- [x] Gerar código de projeto estruturado com o modelo selecionado.
- [x] Validar caminhos/tamanho e salvar dentro do workspace privado.
- [x] Listar, editar, apagar e exportar projetos em ZIP.
- [ ] **Implementar toolchain de compilação Android local no aparelho ARM64**, seguindo uma arquitetura de mini IDE local (toolchains verificadas e instaladas no aparelho, engine de build local, logs e APK assinado).
- [ ] Nenhuma compilação de projeto criado deverá depender de GitHub Actions, token, servidor remoto, Replit ou outro serviço de build.
- [ ] Identificar versões/compatibilidade de Android SDK, Kotlin/Java compiler, AAPT2, D8/R8, zipalign e assinatura.
- [ ] Executar a toolchain sob limites de CPU, RAM, armazenamento e tempo.
- [ ] Recolher erros reais de build e permitir correções automáticas controladas.
- [ ] Validar APK produzido, package ID, assinatura e instalabilidade.
- [ ] Apresentar APK como resultado principal; ZIP fica como opção secundária.

## Fase 4 — Ferramentas nativas
- [x] Editor local de código para arquivos gerados.
- [x] Terminal do workspace com operações locais de arquivos e Git; comandos de shell arbitrários não são executados.
- [ ] Adicionar camada de compilação local ARM64 e conectar o resultado/erros ao terminal.
- [ ] Git local nativo, sem que serviço remoto seja necessário para build.
- [ ] Agente de depuração que lê logs locais, propõe correções e repete builds limitados.
- [ ] Interface de atividade do núcleo com estados verdadeiros.
- [ ] Overlay/órbita flutuante opcional e discreta.
- [ ] Automação de jogos somente após implementar percepção, ações autorizadas e avaliação de resultados.

## Fase 5 — Segurança e testes
- [x] Armazenar conversas e memória em área privada do app.
- [x] Controlar tamanho do histórico e dos projetos.
- [x] Bloquear caminhos de arquivo fora do workspace.
- [x] Não embutir chaves online no APK.
- [x] Desativar backup Android dos dados do aplicativo.
- [ ] Testes unitários/instrumentados de memória, histórico, parsing, escrita e build.
- [ ] Testar sem internet depois que o modelo estiver instalado.
- [ ] Testar pouco espaço, memória insuficiente, modelo incompatível e build interrompido.
- [ ] Testar no Poco F6 sem PC.
- [ ] Assinar release final e criar processo local de backup/recuperação.

## Critério de conclusão

O projeto não estará completo até construir localmente no próprio telefone um projeto Android gerado pela IA, capturar e corrigir erros, produzir um APK instalável e mostrar o APK como resultado principal. Uma compilação anterior do próprio Kellvyn ou a exportação de um ZIP não prova que essa capacidade já existe.
