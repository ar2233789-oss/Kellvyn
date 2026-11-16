# Plano de treinamento próprio — Kellvyn

## Princípios

- Nenhum peso pré-treinado externo.
- Checkpoint inicial gerado por inicialização aleatória reproduzível.
- Tokenizer e vocabulário treinados para o corpus Kellvyn.
- Dados com origem, licença, data, idioma e filtros rastreáveis.
- Avaliação separada e mantida fora do treinamento.
- Treinamento em hardware dimensionado pela meta; Poco F6 é alvo de inferência.
- Não usar dados privados de conversas ou credenciais como corpus.

## Etapa 0 — prova de integridade

O pacote `brain-lab` fornece uma rede pequena com:
- tokenizer byte-level transparente para a primeira prova;
- Transformer causal implementado no projeto;
- inicialização aleatória;
- objetivo de previsão do próximo token;
- script de treino CPU/CUDA/MPS;
- checkpoint com configuração e pesos;
- testes de causalidade, loss finita, round-trip do tokenizer e checkpoint.

Critério: conseguir treinar em um corpus de brinquedo, reduzir loss e reconstruir a execução a partir do checkpoint. Isso prova o pipeline, não inteligência.

## Etapa 1 — tokenizer de produção

Substituir byte-level por BPE ou Unigram treinado do zero:
- avaliar taxa de compressão por idioma;
- reservar tokens de controle para conversas e ferramentas;
- testar nomes, acentos, português informal, código e números;
- congelar versão/hash do vocabulário por checkpoint;
- impedir que tokenizer mude no meio de uma série de treinamento.

## Etapa 2 — inventário de dados

Criar manifesto por fonte com:
- identificador e URL de origem;
- licença e termos aplicáveis;
- idioma e domínio;
- período de coleta;
- tamanho e hash;
- etapas de extração, filtragem e deduplicação;
- métricas de qualidade e riscos conhecidos.

Mistura inicial candidata:
- português geral e conteúdo educacional de licença compatível;
- inglês técnico e científico licenciado;
- documentação e código com licenças identificadas;
- matemática com soluções e verificação;
- fontes acadêmicas abertas com direitos documentados.

Não usar scrape aleatório como sinônimo de dataset autorizado. FineWeb é principalmente inglês e tem licença ODC-By; não satisfaz sozinho o objetivo multilíngue.

## Etapa 3 — pré-treinamento

- definir escala somente depois de medir tokens disponíveis, GPU RAM, largura de banda, tempo e custo;
- testar pelo menos duas configurações pequenas;
- registrar seed, batch de tokens, contexto, LR, scheduler, optimizer, precisão e FLOPs estimados;
- salvar checkpoints e estado do optimizer;
- medir loss de treino e validação, bits por byte, tokens/segundo e uso de memória;
- usar deduplicação para reduzir contaminação treino-teste.

## Etapa 4 — mid-training

Aumentar a presença de domínios com valor para as capacidades desejadas:
- português e multilíngue;
- matemática e ciência;
- documentação técnica e programação;
- instruções e diálogos;
- contextos longos e formatos estruturados.

Medir mistura por ablação; não assumir que mais código, matemática ou texto sintético sempre melhora.

## Etapa 5 — SFT

Construir exemplos de:
- seguir instruções;
- explicar incerteza e pedir informação essencial;
- responder com evidências;
- planejar tarefas;
- emitir tool calls válidas;
- interpretar observações;
- concluir sem inventar execução.

Dados sintéticos devem ser filtrados e verificados. Exemplos de ferramentas precisam conter resultados reais ou ambientes simulados determinísticos, claramente rotulados.

## Etapa 6 — preferências e recompensas verificáveis

Começar com preferência humana e classificadores determinísticos para tarefas com resultado objetivo. Depois investigar DPO/GRPO ou métodos equivalentes em ambiente controlado.

Para programação, a recompensa vem de testes, compilação e critérios de segurança; para matemática, de verificação da resposta; para pesquisa, de fidelidade às fontes. Evitar recompensar estilo ou confiança como substitutos de correção.

## Etapa 7 — ferramenta e agente

Treinar a mesma rede a selecionar ações autorizadas e ler observações:
- esquema de ferramenta estrito;
- ferramenta disponível e descrição curta;
- argumento validado antes da execução;
- retorno da ferramenta adicionado como observação;
- limite de passos, timeout e orçamento;
- estado salvo para recuperar tarefas interrompidas;
- avaliação de sucesso em tarefas completas, não apenas em JSON válido.

## Etapa 8 — multimodalidade

Não declarar multimodalidade até ter:
- conjuntos de imagem-texto/áudio-texto/vídeo com licenças rastreáveis;
- representações e encoders desenvolvidos e treinados no projeto;
- alinhamento com o modelo principal;
- benchmarks e testes cegos;
- medição de RAM e latência no Android.

## Métricas e marcos

| Marco | Métricas mínimas |
|---|---|
| Pipeline | teste de gradiente, loss finita, overfit do corpus minúsculo, checkpoint reproduzível |
| Tokenizer | round-trip, compressão/bytes por token e cobertura por idioma |
| Pré-treino | bits por byte e loss em validação sem contaminação |
| Instruções | taxa de seguimento, formato válido, recusa correta e calibração de incerteza |
| Matemática/código | exatidão e testes executados em conjunto reservado |
| Ferramentas | sucesso por tarefa, falhas de argumentos, loops e ações não autorizadas |
| Implantação | RAM, latência, energia, temperatura e estabilidade no Poco F6 |

## Portões de decisão

Não aumentar o modelo até o conjunto de dados, pipeline e benchmark funcionarem. Não integrar ao app principal até haver checkpoint reproduzível, inferência funcional, testes automatizados e comparação com o baseline anterior. Não promover um modelo porque apenas conversa fluentemente.
