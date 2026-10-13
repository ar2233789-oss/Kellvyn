# Kellvyn IA — roadmap técnico

## Fase 1 — Fundação Android
- [x] Configuração Gradle para aplicativo Android Kotlin/Compose.
- [x] Tema escuro com acentos neon.
- [x] Chat com rolagem automática e controle para copiar cada resposta.
- [x] Entrada por ditado em português e botão para ouvir respostas em voz alta.
- [x] Histórico privado limitado e apagável.
- [x] Fluxo de CI para compilar, verificar a assinatura/identidade do APK e publicar artefato.
- [ ] Instalar e testar em aparelho Android físico.

## Fase 2 — Motor de IA
- [x] Interface comum dos motores de IA, com estados explícitos de sucesso/indisponibilidade/falha.
- [x] Adaptador local LiteRT-LM e importação de arquivo compatível.
- [x] Download de modelo público compatível com tamanho e SHA-256 verificados.
- [x] Exibir tamanho do modelo guardado, apagar modelo e liberar armazenamento.
- [x] Provedor online compatível com Chat Completions, somente HTTPS, com token cifrado via Android Keystore.
- [ ] Testar uma resposta real do modelo baixado no dispositivo-alvo.
- [ ] Testar uma resposta real do endpoint online configurado pelo usuário.
- [ ] Medir RAM, velocidade, aquecimento e qualidade no Poco F6.

## Fase 3 — Memória e construtor
- [x] Memória explícita controlada pelo usuário: adicionar, listar, remover uma memória ou todas.
- [x] Passar memórias salvas ao modelo ativo; avisar que são transmitidas ao endpoint quando o modo ONLINE está selecionado.
- [x] Agente que pede a geração estruturada de projeto Android ao modelo.
- [x] Validar caminhos e tamanho e guardar os arquivos no armazenamento privado.
- [x] Editor de arquivos para projetos gerados.
- [x] Exportar projetos para ZIP.
- [ ] Compilar e testar projetos gerados automaticamente; o construtor atual exporta código e não cria APKs de terceiros diretamente no aparelho.
- [ ] Adicionar ferramentas reais de terminal seguro, navegador integrado e Git conectado sem exibir controles simulados.

## Fase 4 — Privacidade e qualidade
- [x] Não embutir chaves de API no APK.
- [x] Token online cifrado via Android Keystore.
- [x] Endpoint HTTPS obrigatório e redirecionamentos automáticos desativados no pedido autenticado.
- [x] Backup Android desativado para o app.
- [x] Importação de modelo por seletor de documentos sem pedir acesso amplo ao armazenamento.
- [ ] Testes unitários/instrumentados para histórico, memória, endpoints e arquivos de modelo.
- [ ] Testes de instalação e comportamento em dispositivo físico.
- [ ] Testes de falta de memória, modelo incompatível, falha de rede e cancelamento.
- [ ] Assinatura release e distribuição fora do APK de depuração.

## Critérios de conclusão

Uma compilação bem-sucedida confirma que o APK foi gerado e passou pelas verificações automatizadas, não que foi instalado no telefone ou que o modelo realmente responde nele. O motor local só será validado depois de gerar uma resposta no dispositivo; o modo online, depois de receber uma resposta do endpoint configurado. O construtor só estará completo quando projetos gerados forem compilados e testados de forma confiável.
