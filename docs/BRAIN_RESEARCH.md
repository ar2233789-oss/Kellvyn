# Caderno de pesquisa — referências para a Kellvyn

Data da primeira auditoria: 16/11/2026.  
Objetivo: estudar implementações e resultados publicados; não importar pesos nem identidade de produtos terceiros.

## Android Studio / Gemini Agent Mode

- Documentação oficial: https://developer.android.com/studio/gemini/agent-mode
- Aprendizado: decomposição de uma tarefa complexa em plano, pesquisa/leitura de arquivos, ferramentas aprovadas, edição em múltiplos arquivos, execução e avaliação iterativa.
- Limite: o modelo Gemini e a inferência proprietária não são o código-fonte aberto do Android Studio. A documentação revela o contrato e o ciclo do agente; não permite inspecionar os pesos nem todo o raciocínio interno.
- Uso na Kellvyn: especificar ciclo de ferramenta e feedback para o nosso próprio modelo.

## nanochat — Andrej Karpathy

- Código: https://github.com/karpathy/nanochat
- Licença declarada pelo repositório: MIT (confirmar avisos e versão exata antes de qualquer reutilização).
- Lido nesta auditoria: `nanochat/gpt.py`, `scripts/base_train.py`, `scripts/chat_sft.py`, `scripts/chat_rl.py`, `nanochat/tokenizer.py`.
- Aprendizado: pipeline relativamente compacto e completo — tokenizer, pré-treinamento, SFT, RL, avaliação, checkpoints e inferência.
- Observação crítica do próprio projeto: treino em CPU/MPS requer reduzir muito o modelo e não resulta em capacidade forte. Configurações significativas são pensadas para GPU.
- Uso na Kellvyn: guia de etapas e clareza de treinamento. Não copiar o GPT, o código, tokenizer ou pesos sem uma revisão deliberada de licença e de originalidade.

## OLMo-core / Allen Institute for AI

- Código: https://github.com/allenai/OLMo-core
- Documentação: https://olmo-core.readthedocs.io/
- Licença da biblioteca OLMo-core: Apache-2.0, conforme documentação.
- Aprendizado: blocos de treinamento distribuído, carregamento de dados, mistura de datasets, avaliação, otimização e checkpoints.
- Uso na Kellvyn: estudar quando a escala exigir mais de uma GPU. Não é necessário adotar toda a plataforma no protótipo Android.

## OpenHands

- SDK: https://github.com/OpenHands/software-agent-sdk
- Arquitetura: https://docs.openhands.dev/sdk/arch/overview
- Aprendizado: loop de raciocínio-ação, eventos, conversas, workspaces, ferramentas tipadas, condensação de contexto e validação de segurança.
- Uso na Kellvyn: contrato arquitetural de execução e observações. O modelo que escolhe ações será o modelo neural da Kellvyn.

## Aider

- Código: https://github.com/Aider-AI/aider
- Arquivo inicialmente inspecionado: `aider/coders/base_coder.py`.
- Aprendizado: mapa de repositório, edição localizada, integração com lint/testes, histórico de mudanças e ciclos de correção.
- Limite: o projeto integra-se com diferentes LLMs; não é uma fundação neural própria.
- Uso na Kellvyn: engenharia do workspace e feedback pós-edição.

## SWE-agent

- Código: https://github.com/SWE-agent/SWE-agent
- Aprendizado: interface agente-computador e avaliação sobre tarefas de engenharia reais.
- Uso na Kellvyn: casos de teste de repositórios, correção de bugs e reprodução de falhas.

## OpenCode

- Código: https://github.com/anomalyco/opencode
- Aprendizado: organização de sessões, ferramentas, permissões e fluxo de trabalho de programação.
- Limite: é agente de programação, não modelo neural a ser adotado.

## Avaliação

- HELM: https://github.com/stanford-crfm/helm
- Portal: https://crfm.stanford.edu/helm/
- O repositório HELM entrou em modo de manutenção em 01/06/2026; ainda pode fornecer desenho de cenários, transparência de prompts e métricas, mas não será nossa única referência.
- Uso na Kellvyn: desenhar avaliação ampla, reproduzível, com tarefas, métricas e resultados por amostra.

## Dados

- FineWeb: https://huggingface.co/datasets/HuggingFaceFW/fineweb
- Dataset card descreve corpus de web em inglês filtrado e deduplicado; licença ODC-By 1.0.
- Não é corpus de português e não pode ser nossa única fonte. É referência de curadoria e não autorização para ignorar atribuição ou obrigações de licença.
- Para português, recolher corpora com proveniência, licença, qualidade, representatividade e direitos documentados. Evitar presumir que conteúdo publicamente acessível é livre para treinamento.

## Leis de escala

- Hoffmann et al. (2022), Training Compute-Optimal Large Language Models: https://arxiv.org/abs/2203.15556
- Resultado central: tamanho do modelo e tokens de treino devem ser dimensionados considerando o orçamento computacional; modelos grandes subtreinados podem perder para modelos menores melhor treinados.
- Uso na Kellvyn: projetar experimentos de escala com orçamento explícito. A proporção reportada no artigo não deve ser tratada como lei universal para todos os dados e objetivos.

## Discord

- Interactions e application commands: https://discord.com/developers/docs/interactions/overview
- Gateway: https://discord.com/developers/docs/events/gateway
- Aprendizado: comandos slash, eventos Gateway, intents, permissões e limites oficiais.
- Uso na Kellvyn: os bots devem ser projetos gerados e executados com credenciais protegidas, respeitando intents e regras da plataforma.

## Política de engenharia para referências

Antes de transportar trechos de qualquer repositório:
1. fixar commit e versão;
2. ler LICENSE e avisos de terceiros;
3. identificar dependências e licenças transitivas;
4. manter atribuição e avisos obrigatórios;
5. executar revisão de segurança;
6. documentar exatamente o que foi reutilizado.

Preferência inicial: reimplementar as ideias matemáticas e os contratos a partir de documentação/artigos, sem copiar arquivos de referência para a fundação neural.


## Gemini CLI — engenharia de agente aberta

- Código: https://github.com/google-gemini/gemini-cli
- Licença declarada: Apache-2.0; verificar novamente a versão/commit antes de reutilizar trechos.
- Arquivos lidos nesta auditoria: `packages/core/src/agent/agent-session.ts`, `packages/core/src/agents/generalist-agent.ts`, `packages/core/src/agents/agent-scheduler.ts`, `packages/core/src/agents/local-executor.ts`, `packages/core/src/tools/tool-registry.ts`, `packages/core/src/policy/policy-engine.ts`, `packages/core/src/config/memory.ts`, `packages/core/src/tools/enter-plan-mode.ts` e `packages/core/src/tools/write-todos.ts`.
- Aprendizado: eventos de ciclo de vida e streaming; escalonamento de chamadas de ferramenta; limites de turnos/tempo; compressão de contexto; validação de argumentos; políticas de permissão; confirmação; memória hierárquica e plano explícito.
- Limite: o Gemini CLI utiliza modelos Gemini/API Google. O código aberto é o agente e a infraestrutura, não os pesos nem a inteligência neural Gemini.
- Uso na Kellvyn: estudar protocolos, scheduler, segurança e gestão de contexto. Reimplementar os contratos essenciais ligados ao modelo neural próprio.

## Android Studio: código aberto versus Gemini

- Código do plugin Android para IntelliJ: https://android.googlesource.com/platform/tools/adt/idea/
- Documentação do Agent Mode: https://developer.android.com/studio/gemini/agent-mode
- O repositório público de Android Studio descreve o código do plugin Android para IntelliJ que compõe uma parte importante do IDE. A documentação oficial explica o Agent Mode: plano, ferramentas, leitura de arquivos, edição, permissões e iteração.
- Limite importante: não encontrei uma distribuição pública do código completo do modelo Gemini nem de toda a implementação proprietária do Agent Mode. Portanto, não é correto dizer que lemos o cérebro do Gemini; estudamos documentação de comportamento e fontes abertas do ambiente/infraestrutura disponível.
- Uso na Kellvyn: reproduzir a qualidade do ciclo de desenvolvimento com nosso modelo e nossos próprios executores, não depender do Gemini.

## Qwen3 — relatório de treinamento e arquitetura

- Repositório: https://github.com/QwenLM/Qwen3
- Relatório técnico: https://arxiv.org/abs/2505.09388
- Aprendizado: famílias dense/MoE, mistura multilíngue, tokenizer byte-level BPE, treinamento em múltiplas etapas, SFT e RL. O relatório descreve um volume de pré-treinamento de dezenas de trilhões de tokens, mostrando a escala de dados e computação exigida para competir em capacidades avançadas.
- Limite: pesos abertos não equivalem a treinamento da Kellvyn. Nenhum checkpoint Qwen ou tokenizer pré-treinado será importado.
- Uso na Kellvyn: referência de análise de trade-offs, fases de treinamento e benchmarks. Construiremos nosso próprio tokenizer e pesos.

## OLMo 3 — transparência de treinamento em escala

- Relatório e scripts: https://github.com/allenai/OLMo-core/tree/main/src/scripts/official/OLMo3
- Aprendizado: fases de treinamento base, instrução e raciocínio; documentação de datasets, logs, checkpoints e configurações.
- O relatório OLMo 3 descreve modelos 7B/32B treinados com trilhões de tokens, deixando claro que uma primeira prova com dezenas de milhares de tokens valida o código, não capacidade competitiva.
- Uso na Kellvyn: rastreabilidade experimental, composição dos dados e avaliação por estágios; não importar os pesos.

## Síntese após a leitura de código

As referências convergem em quatro lições:
1. A qualidade do agente depende de ferramentas tipadas, observações reais, limites e recuperação de erros — não apenas de prompts longos.
2. O contexto tem orçamento finito; precisa de memória recuperável e condensação que preservem fatos e decisões importantes.
3. Treinamento deve ser uma sequência auditável: tokenizer → pré-treino → ajuste de instruções → uso de ferramentas → avaliação.
4. A qualidade de dados e o orçamento de tokens/compute condicionam o teto de capacidade. Começar pequeno é adequado para validar a implementação, não para prometer inteligência de ponta.



## ExecuTorch — candidata para executar nosso próprio modelo no Android

- Documentação LLM 1.5: https://docs.pytorch.org/executorch/stable/llm/getting-started.html
- Exportação de modelo customizado: https://docs.pytorch.org/executorch/stable/llm/export-custom-llm.html
- Integração Android: https://docs.pytorch.org/executorch/stable/llm/run-on-android.html
- Backends Android: https://docs.pytorch.org/executorch/stable/android-backends.html
- A documentação descreve a exportação de um modelo PyTorch próprio com `torch.export`, transformação para Edge e geração de um arquivo `.pte`. O caminho de modelo customizado existe justamente para modelos que não fazem parte da API nativa de exportação.
- Para usar o runner padrão de geração autoregressiva, o modelo precisa aceitar IDs de tokens, opcionalmente uma posição de entrada quando usa KV cache, e devolver logits. O cache deve ficar em buffers do próprio modelo; metadados como limites de sequência e IDs BOS/EOS precisam corresponder ao tokenizer.
- Sem KV cache, o modelo pode ser executado, mas cada passo tende a recalcular a atenção de todo o contexto e fica mais lento.
- A documentação atual apresenta API Java/Kotlin experimental via AAR `executorch-android`. O runner oferece carregamento, streaming de tokens, callback de erro e estatísticas; a API ainda pode mudar.
- A documentação lista XNNPACK para CPU, Vulkan para GPU Android e Qualcomm AI Engine para NPU/DSP, além de outras opções. O suporte efetivo depende do dispositivo, backend, operadores e versão. Começar por CPU XNNPACK como baseline de compatibilidade; depois medir Vulkan e eventualmente QNN no Poco F6 real. Não presumir aceleração NPU só pelo fabricante do chip.
- Os arquivos `.pte` são preparados para um backend específico; operadores não suportados podem cair para CPU. Isso pode reduzir desempenho e precisa ser medido.
- Decisão Kellvyn: ExecuTorch é a primeira candidata séria ao runtime de inferência nativo porque aceita modelos PyTorch customizados e possui integração Android. Não fornece pesos de modelo e não é o cérebro. Os pesos e o tokenizer serão os treinados no projeto.
- Lacuna a resolver: o tokenizer BPE atual é serializado em JSON próprio. Precisamos de um tokenizer compatível em Kotlin/C++ ou de um runner/IOManager próprio; não afirmar que o runner padrão lê nosso JSON sem trabalho adicional.
- Lacuna a resolver: a rede experimental atual ainda não tem KV cache e precisa de uma fronteira de entrada/saída exportável; antes da implantação, acrescentar cache próprio e executar a exportação do modelo Kellvyn (não de um exemplo GPT-2).

## MLC LLM — candidata secundária, não escolhida ainda

- Guia Android: https://llm.mlc.ai/docs/deploy/android.html
- O fluxo Android documentado compila uma biblioteca de modelo específica e empacota runtime/tokenizer; a configuração lista artefatos de pesos convertidos e uma identificação de biblioteca por arquitetura.
- Pode ser interessante se a arquitetura própria e as operações necessárias forem suportadas ou se adicionarmos implementação/compilação específica. Isso ainda não foi demonstrado para a arquitetura Kellvyn.
- Decisão: manter como alternativa de investigação; não adotar só porque executa modelos conhecidos. O requisito é exportar e rodar nossos próprios pesos.

## Curadoria e métrica para tokenizers próprios

- Treinar vocabulário próprio sem importar uma tabela pré-treinada é tecnicamente possível; o BPE da primeira prova aprende regras a partir apenas do corpus de treino.
- O BPE atual usa contagem repetida de pares e varredura completa do texto por merge. É transparente, mas tem custo elevado e não serve para corpora de bilhões de tokens sem otimização.
- Próxima etapa: benchmark de bytes/token por idioma e treinamento BPE otimizado (estrutura de pares incremental/implementação compilada própria), preservando o formato e o hash do vocabulário no checkpoint.
- Para comparar tokenizers com tamanhos diferentes, usar bits-per-byte em avaliação, em vez de comparar somente loss por token.
- Fontes de método: nanochat `scripts/tok_train.py` e `nanochat/loss_eval.py`; documentação de BPE training da Hugging Face é referência de processo/hiperparâmetros, não fonte de vocabulário para a Kellvyn.
