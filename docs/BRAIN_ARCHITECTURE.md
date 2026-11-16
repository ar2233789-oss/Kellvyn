# Arquitetura proposta — cérebro da Kellvyn

Status: arquitetura inicial para experimentação; decisões ainda sujeitas a ablações e resultados.  
Regra: não carregar pesos pré-treinados.

## 1. Separação de responsabilidades

### A. Fundação neural
Um modelo autoregressivo próprio, inicialmente decoder-only, com:
- tokenizer treinado para o corpus Kellvyn;
- embeddings de tokens;
- blocos Transformer causais;
- normalização e conexões residuais;
- projeção de saída e objetivo de previsão do próximo token;
- inferência com cache KV quando o runtime estiver maduro.

A primeira prova de funcionamento usa um Transformer pequeno, byte-level BPE treinado no texto de treino, com atenção causal. O algoritmo BPE inicial prioriza auditabilidade, não escala. Isso valida a matemática, o treinamento e a serialização; não pretende oferecer qualidade conversacional.

### B. Sistema cognitivo integrado
O mesmo modelo deve aprender a:
1. interpretar objetivo, restrições e contexto;
2. decidir se responde ou se precisa de uma ferramenta;
3. emitir uma chamada estruturada conforme um esquema permitido;
4. receber o resultado real da ferramenta;
5. atualizar a situação da tarefa;
6. decidir o próximo passo ou concluir.

Não há um segundo LLM planejador. Planejamento, seleção de ferramenta e resposta são comportamentos treinados do mesmo modelo. O executor, os validadores e os sistemas operacionais são componentes determinísticos.

### C. Memória
A memória persistente é um armazenamento local com:
- origem e data do fato;
- consentimento/razão para guardar;
- escopo e sensibilidade;
- validade/expiração quando aplicável;
- recuperação por relevância;
- correção e exclusão pelo usuário.

A memória não atualiza automaticamente os pesos. Aprendizagem de pesos exige um ciclo offline, curado, versionado e avaliado.

### D. Ferramentas e execução
Interfaces tipadas para pesquisa, leitura de arquivos, programação, Git, compiladores, testes, bots, documentos, visão e áudio. Toda ação tem:
- esquema de entrada e saída;
- permissões mínimas;
- limites de tempo, tamanho e rede;
- validação de argumentos;
- registro do resultado;
- confirmação para operações sensíveis ou destrutivas.

Conteúdo externo é dado não confiável, nunca instrução privilegiada.

## 2. Fundação neural: baseline experimental

A experiência inicial em `brain-lab/` implementa:
- tokenizer byte-level como baseline de referência;
- BPE byte-level cujas regras são aprendidas do zero no texto de treino;
- embeddings próprios;
- RMSNorm;
- atenção causal multi-head com RoPE;
- MLP SwiGLU;
- conexões residuais e projeção de vocabulário;
- cross-entropy de próximo token;
- AdamW, clipping de gradiente, validação e checkpoints que preservam o tokenizer.

O BPE atual é uma implementação didática de complexidade alta, útil para provar que o vocabulário pode ser aprendido sem importar uma tabela. Não deve ser usado em corpus massivo sem otimização e benchmark. Os métodos neurais são conhecidos na literatura. O código e todos os pesos são próprios do experimento.

## 3. Evolução a avaliar, não assumir

Após estabelecer baseline correto, testar uma mudança por vez:
- BPE otimizado versus Unigram tokenizer treinado no corpus;
- GQA versus MHA;
- contextos maiores e atenção local/global;
- diferentes proporções de profundidade/largura;
- SwiGLU e alternativas de MLP;
- RoPE e alternativas posicionais;
- técnicas de otimização e agendamento da taxa de aprendizado;
- precisão mista, quantização e cache KV.

Não adotar MoE, arquiteturas recorrentes, mecanismos exóticos ou multimodalidade antes de haver benchmark que justifique custo e complexidade.

## 4. Treinamento em etapas

1. Teste de pipeline: modelo minúsculo deve aprender a sequência de um corpus didático.
2. Tokenizer e corpus: medir taxa de compressão, cobertura do português, duplicação e qualidade.
3. Pré-treinamento: previsão do próximo token em mistura documental licenciada.
4. Mid-training: código, matemática, ciências, instruções e contextos longos, com proporções medidas.
5. Supervised fine-tuning: exemplos de diálogo e tarefas revisados, com separação por origem e licença.
6. Treino de uso de ferramentas: trajetórias de chamadas/observações e execução verificada.
7. Preferências: avaliação humana e recompensas verificáveis; evitar recompensa baseada apenas em parecer convincente.
8. Multimodalidade: trilhas independentes de dados pareados e encoders próprios, só depois de texto confiável.

## 5. Implantação

Treinamento e inferência são problemas distintos. A execução final será preparada para o Android nativo e o Poco F6, com medições reais de:
- RAM máxima e sustentada;
- latência de primeiro token e tokens/segundo;
- energia, temperatura e throttling;
- tamanho do checkpoint e memória de KV cache;
- comportamento com e sem rede.

O aparelho de 8 GB é alvo de inferência otimizada, não uma promessa de infraestrutura suficiente para pré-treinar uma IA de grande escala. Não usar o emulador como prova de desempenho térmico ou autonomia no aparelho real.

## 6. Critério de passagem

Nenhuma mudança é promovida apenas por menor loss. Precisa manter ou melhorar:
- perplexidade/bits por byte em dados não vistos;
- português e instruções;
- matemática e código;
- generalização fora dos exemplos memorizados;
- uso correto de ferramentas;
- taxa de erro e alucinação;
- eficiência por parâmetro, memória e latência.

Resultados, configurações, seeds e hashes são versionados. Toda versão deve poder ser reproduzida ou explicitamente marcada como não reproduzível.



## 7. Implantação da fundação própria no Android — plano de prova

ExecuTorch é a primeira candidata de runtime, não parte do cérebro:
1. Validar o modelo PyTorch em CPU e congelar contrato de entrada (IDs de token) e saída (logits).
2. Criar uma classe exportável que devolva apenas logits e remova validações Python que dependam de valores de tensores durante o tracing.
3. Exportar exatamente os pesos aleatórios/treinados da Kellvyn para `.pte` com a rota de modelo customizado do ExecuTorch.
4. Comparar logits do PyTorch e ExecuTorch sobre os mesmos IDs de entrada, com tolerância numérica documentada.
5. Rodar um teste mínimo Android nativo usando `executorch-android`/API Java-Kotlin; primeiro XNNPACK CPU.
6. Integrar nosso tokenizer BPE por implementação Kotlin/C++ com testes de paridade Python↔Android; não usar vocabulário externo.
7. Adicionar KV cache como estado interno do modelo e testar prefill/decode; depois otimizar e reexportar.
8. Quantizar pesos/ativações após medir perda de qualidade e compatibilidade de operadores. Gerar artefatos separados por backend quando necessário.
9. Medir no Poco F6 físico: RAM, latência, tokens/s, energia, temperatura e throttling. Emulador não substitui esta etapa.

O contrato documentado do runner de LLM customizado aceita IDs de tokens, posição opcional para cache KV, devolve logits e recebe metadados de contexto/BOS/EOS. A API Android LLM de ExecuTorch é atualmente experimental; o protótipo pode precisar usar diretamente `Module` ou um runner/IOManager próprio se o tokenizer ou o protocolo não couber no runner padrão.

Não adicionar dependência ao aplicativo principal até a prova de exportação e paridade. O runtime não fornece parâmetros, tokenizer treinado ou inteligência: esses artefatos são gerados pelo treinamento Kellvyn.
