# Auditoria operacional — cérebro da Kellvyn

**Atualizada em:** 2026-11-20  
**Repositório de trabalho:** `ar2233789-oss/Kellvyn`  
**Branch de trabalho:** `brain/from-scratch-foundation`  
**HEAD auditado:** `3419166d2d1c6e900ed127532b07bf813709e150`  
**Memória do operador:** `studio/memory/OPERATOR_MEMORY.md`, branch `studio-production-suite`  
**Regra para qualquer sessão nova:** ler esta auditoria e a memória do operador antes de responder sobre o projeto, pesquisar arquivos ou editar código. Não usar somente a branch `main`.

## 1. Escopo confirmado pelo usuário

O trabalho ativo é construir e aprimorar o cérebro próprio da Kellvyn.

- Uma única inteligência neural principal, desenvolvida para o projeto.
- Não usar Gemma ou qualquer outro modelo/peso pré-treinado como cérebro substituto.
- Não depender de API de inferência de outra IA.
- Não desenvolver agentes, equipes de agentes, sistemas multiagente ou orquestrador.
- Não retomar o Copiloto.
- Não alterar a interface Android aprovada enquanto o foco for o cérebro.
- Não tratar documentação, protótipo, código exportado ou teste mínimo como prova de que existe uma IA conversacional pronta.
- Usar “cérebro da Kellvyn” na conversa; não inventar nomes alternativos para o projeto.

A pesquisa antiga sobre agentes e ferramentas em `docs/BRAIN_RESEARCH.md` é referência histórica. Ela não autoriza implementar agentes nem muda o escopo atual.

## 2. Repositório e estado de versão

A branch correta para a fundação neural é `brain/from-scratch-foundation`. O histórico dessa branch mostra alterações concentradas em **16/11/2026**; não há commits posteriores nessa branch antes desta auditoria de 20/11/2026.

Última sequência técnica confirmada:
- `13126e0`: tokenizer BPE treinável para o projeto.
- `a177897`: deduplicação de documentos antes da divisão treino/validação.
- `bff3e51`: validação e restauração de checkpoints/estado do otimizador.
- `48991e9`: otimização incremental do BPE com lista ligada e heap.
- `fab60d7`: teste de equivalência do BPE incremental com a referência.
- `908da35`: caminho de logits somente tensores para exportação.
- `3419166`: exportador de checkpoint para grafo `torch.export`.

HEAD auditado: [commit 3419166](https://github.com/ar2233789-oss/Kellvyn/commit/3419166d2d1c6e900ed127532b07bf813709e150).

## 3. O que existe de fato

| Área | Estado verificado | Limite atual |
|---|---|---|
| Modelo neural | Decoder-only causal próprio em PyTorch, parâmetros inicializados aleatoriamente | Baseline minúsculo de engenharia, não um modelo conversacional útil |
| Configuração padrão | largura 128, 2 camadas, 4 cabeças, contexto de 128 tokens | Valores de prova; não são escala de produção |
| Tokenizer | Byte tokenizer de referência e BPE byte-level treinável | O código ainda precisa de benchmark em corpus representativo e grande |
| Treinamento | Previsão do próximo token, AdamW, clipping, validação e seed | Nenhum pré-treinamento de escala foi comprovado |
| Dados | Divisão por documento, deduplicação e hash de corpus no checkpoint | Não há corpus de produção versionado no diretório `brain-lab/` |
| Checkpoint | Salva configuração, pesos, tokenizer, metadados e estado do otimizador | Não foi encontrado checkpoint treinado e publicado no repositório |
| Exportação | Exportador `.pt2` por `torch.export`, comparando logits do módulo eager e exportado | Isso ainda não é arquivo ExecuTorch `.pte` nem runtime Android |
| Android | Existe planejamento de prova de integração em `docs/BRAIN_ARCHITECTURE.md` | Nenhuma inferência dessa fundação própria foi comprovada no APK |
| Avaliação | Testes de unidade e smoke test automatizado | Ainda não existe avaliação ampla de qualidade conversacional |

Arquivos-base:
- `brain-lab/src/kellvyn_brain/model.py`
- `brain-lab/src/kellvyn_brain/tokenizer.py`
- `brain-lab/src/kellvyn_brain/train.py`
- `brain-lab/src/kellvyn_brain/export.py`
- `brain-lab/tests/test_foundation.py`
- `docs/BRAIN_CHARTER.md`
- `docs/BRAIN_ARCHITECTURE.md`
- `docs/BRAIN_RESEARCH.md`
- `docs/BRAIN_TRAINING_PLAN.md`

## 4. Evidência de testes existente

O workflow **Kellvyn Brain Lab** terminou com conclusão `success` para o HEAD `3419166`, em 16/11/2026: [execução 43809460057](https://github.com/ar2233789-oss/Kellvyn/actions/runs/43809460057).

O workflow executa:
1. instalação de PyTorch CPU e pytest;
2. testes de `brain-lab/tests`;
3. smoke test de treinamento com corpus sintético de cinco frases repetidas, somente **3 passos**;
4. verificação de checkpoint, hash, tokenizer e estado do otimizador.

Interpretação correta: os testes comprovam que o protótipo e o pipeline mínimo executam. **Não comprovam treinamento de uma IA útil, qualidade de raciocínio, desempenho em português ou funcionamento Android.**

## 5. Pendências reais — não marcar como concluídas

1. Corpus: definir e documentar fontes permitidas, licenças, idioma, domínio, deduplicação, filtros, hashes e separação treino/validação/teste.
2. Tokenizer: medir compressão, cobertura do português, código, números e texto informal em amostras representativas; otimizar apenas com medições.
3. Modelo: medir capacidade, parâmetros, memória e custo de treino; não aumentar dimensões por palpite.
4. Treinamento: rodar experimentos reproduzíveis com logs, curvas de loss, validação, seed, tokens vistos, velocidade e checkpoints.
5. Capacidades: criar avaliação reservada para português, instruções, contexto, matemática, programação, honestidade e generalização.
6. Inferência: finalizar exportação para runtime executável, testar paridade numérica e só então avaliar integração Android.
7. Dispositivo: medir RAM, latência, tokens/s, temperatura, energia e estabilidade no Poco F6 físico; não extrapolar do emulador.

## 6. Próxima sequência de trabalho

1. Partir exclusivamente do HEAD e dos arquivos listados nesta auditoria; comparar o código real antes de cada edição.
2. Fechar o inventário e a política de dados antes de iniciar pré-treinamento sério.
3. Construir benchmark pequeno, reproduzível e reservado, para medir tokenizer e modelo antes de aumentar capacidade.
4. Melhorar o pipeline de treinamento e a avaliação, preservando checkpoints reproduzíveis.
5. Avançar a exportação e runtime Android somente depois de o modelo e os dados passarem nos testes.
6. Registrar cada mudança real, teste e bloqueio nesta auditoria com data, commit e evidência.

## 7. Protocolo obrigatório para continuidade entre chats

Em toda sessão:
1. Abrir esta auditoria na branch `brain/from-scratch-foundation`.
2. Abrir `studio/memory/OPERATOR_MEMORY.md` na branch `studio-production-suite`.
3. Conferir HEAD, commits recentes, mudanças e evidências de CI das branches envolvidas.
4. Repetir em uma frase o escopo vigente para si mesmo antes de editar: cérebro próprio; sem modelos pré-treinados, agentes, orquestrador ou Copiloto.
5. Atualizar esta auditoria após qualquer marco real. Cada atualização deve registrar: data, alteração, arquivos, commit, testes executados, resultado e próximo passo.
6. Se nenhuma alteração foi feita, dizer isso claramente. Nunca alegar que o registro foi atualizado sem confirmar o commit e reler o arquivo.

**Esta auditoria é o ponto de retomada. Memória de conversa, resumo automático e indicadores temporários da interface não a substituem.**

## 8. Correção de escopo — 2026-11-20, 15:29

O usuário corrigiu o operador: **a tarefa executada neste momento é atualizar e usar a auditoria, não modificar o cérebro.**

- Não alterar código, arquitetura, tokenizer, treinamento, exportação ou integração Android do cérebro nesta etapa.
- Não transformar os documentos técnicos existentes em autorização automática para implementar uma solução. Eles são material de referência e estado encontrado no repositório; decisões do usuário prevalecem.
- O operador ainda não demonstrou compreender com precisão a próxima alteração desejada no cérebro. Portanto, não deve escolher uma melhoria técnica por conta própria nem começar a editar por impulso.
- A ordem atual é deixar a auditoria confiável, consultar as memórias/decisões anteriores e impedir que conversas novas retomem projetos ou ideias descartadas.
- Quando a etapa de auditoria estiver concluída, só retomar implementação do cérebro seguindo o objetivo e as instruções concretas já registradas pelo usuário, sem reabrir agentes, orquestrador ou Copiloto.

### Distinção obrigatória no histórico

- **Confirmado pelo usuário:** o foco futuro é o cérebro da Kellvyn; agentes e Copiloto estão fora do escopo; não usar Gemma; não desviar para outros projetos; consultar a auditoria em todo chat novo.
- **Encontrado no código/documentação:** existem protótipos, documentos de arquitetura e workflows na branch técnica. Isso descreve o conteúdo atual do repositório, não prova que o operador tenha entendido qual é a próxima tarefa pedida.
- **Ainda não autorizado nesta etapa:** qualquer alteração nos arquivos técnicos do cérebro.

Esta correção prevalece sobre qualquer frase anterior desta auditoria que possa ser interpretada como autorização para começar uma implementação imediatamente.


## 9. Trava operacional e estado da fase — 2026-11-20

**Fase atual:** `AUDIT_ONLY` — a tarefa presente é completar e tornar confiável a auditoria. Nenhum arquivo técnico do cérebro está autorizado para edição nesta fase.

Foi criado o ponto de entrada `AUDIT_START_HERE.md` e o workflow `.github/workflows/audit-gate.yml`.

O workflow **Audit Gate**:
- verifica se esta auditoria existe e declara uma fase válida;
- reprova alterações técnicas enquanto a fase estiver em `AUDIT_ONLY`;
- quando a fase for explicitamente autorizada pelo usuário como `IMPLEMENTATION_AUTHORIZED`, exige que a auditoria também seja atualizada no mesmo push/PR;
- executa em pushes e pull requests direcionados à branch `brain/from-scratch-foundation`.

Limite técnico: o workflow valida alterações no GitHub; não tem como disparar a abertura desta auditoria dentro de uma conversa do ChatGPT. Em cada chat novo, o assistente ainda precisa consultar os dois arquivos diretamente antes de trabalhar. Não declarar carregamento automático nem consulta feita sem executar a leitura.

A trava do workflow não substitui regras de proteção de branch do GitHub. Sem uma regra de proteção que exija o status check, o workflow consegue marcar uma alteração como reprovada, mas não impede por si só que alguém faça push direto na branch. O operador não deve afirmar que a proteção de branch está ativada sem verificar isso.

Para sair de `AUDIT_ONLY`, é necessária autorização explícita do usuário e uma atualização desta auditoria com a tarefa específica, limites e critérios de aceitação. A existência de código ou documentação no repositório não equivale a autorização.
