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
