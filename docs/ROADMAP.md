# Kellvyn IA — roadmap técnico

## Fase 1 — Fundação Android
- [x] Configuração Gradle para aplicativo Android Kotlin/Compose.
- [x] Manifesto e atividade inicial.
- [x] Interface escura com chat e status do núcleo.
- [x] Histórico privado de conversas, limitado e apagável.
- [x] Botão para copiar cada resposta da IA para a área de transferência.
- [x] Entrada por ditado em português usando o reconhecedor instalado no Android.
- [x] Fluxo de CI para compilar e publicar APK de depuração.
- [ ] Instalar e testar a interface em aparelho Android físico.

## Fase 2 — Motor de IA
- [x] Contrato comum para motores de IA, com estados explícitos de sucesso/indisponibilidade/falha.
- [x] Adaptador de inferência local LiteRT-LM e importação do arquivo de modelo.
- [x] Provedor online compatível com Chat Completions, por HTTPS e com credencial cifrada no Android Keystore.
- [x] Explicação de que as mensagens só são enviadas ao provedor quando o modo online está selecionado.
- [ ] Testar uma geração real com um modelo `.litertlm` compatível no aparelho-alvo.
- [ ] Testar resposta real contra um endpoint online configurado pelo usuário.
- [ ] Medir RAM, armazenamento, aquecimento e velocidade no Poco F6.

## Fase 3 — Memória e construtor
- [x] Memória de longo prazo controlada pelo usuário: salvar, listar, apagar uma memória ou todas.
- [x] Passar o contexto de memórias salvas ao motor escolhido; avisar que esse contexto vai ao provedor em modo online.
- [x] Agente que solicita a geração estruturada de um projeto Android ao modelo escolhido.
- [x] Validação de caminhos/tamanho e armazenamento privado dos arquivos gerados.
- [x] Editor local de arquivos do projeto gerado.
- [x] Exportação do projeto para ZIP.
- [ ] Compilar/testar projetos gerados automaticamente; no momento, o construtor exporta código e não gera o APK de terceiros diretamente no aparelho.
- [ ] Ferramentas adicionais (Git integrado, navegador e ambiente de build) com ações reais, não simuladas.

## Fase 4 — Privacidade e qualidade
- [x] Não embutir chaves de API no APK.
- [x] Token online cifrado com Android Keystore.
- [x] HTTPS obrigatório para o provedor online; redirecionamento automático desativado no pedido autenticado.
- [x] Backup Android desativado para reduzir exposição dos dados do app.
- [ ] Testes unitários/instrumentados de memória, histórico, endpoint e importação de modelo.
- [ ] Verificação de instalação e comportamento em dispositivo físico.
- [ ] Testes reais de falha, memória insuficiente, modelo incompatível e indisponibilidade de rede.
- [ ] Assinatura de release e processo de distribuição fora do APK de depuração.

## Critério de conclusão

O APK de depuração está validado somente quando a compilação automatizada termina com sucesso. Isso não prova que o APK foi instalado no telefone ou que o modelo gera respostas nele. A IA local só estará validada após uma resposta real do modelo no dispositivo; a IA online, após uma resposta real do endpoint configurado. O construtor só será considerado completo quando os projetos gerados puderem ser compilados e testados de forma confiável.
