# Kellvyn Brain Lab — fundação neural do zero

Este diretório é o primeiro laboratório de treinamento próprio da Kellvyn.

**Estado:** protótipo inicial de engenharia, não um modelo conversacional útil e não uma inteligência geral.

## Garantias deste protótipo

- Não baixa nem carrega pesos externos.
- Não importa Gemma, Llama, Qwen, GPT, Claude, Gemini ou outro checkpoint.
- Inicializa parâmetros aleatoriamente.
- Implementa uma rede causal e um tokenizer byte-level BPE treinado no texto fornecido.
- Pode treinar um modelo minúsculo a partir de um arquivo de texto autorizado.
- Salva arquitetura, pesos e regras do tokenizer no mesmo checkpoint.

A implementação usa PyTorch como biblioteca numérica/autograd. PyTorch não fornece pesos de IA aqui; o modelo é instanciado do zero. A arquitetura experimental usa componentes matemáticos conhecidos e não reivindica invenção de Transformer, RoPE, RMSNorm ou SwiGLU.

## Requisitos

- Python 3.10+
- PyTorch 2.2+
- Ambiente com CPU, CUDA ou MPS compatível com a instalação do PyTorch.

Instalação de desenvolvimento:

```bash
python -m venv .venv
source .venv/bin/activate
pip install -e ".[dev]"
pytest
```

## Primeiro treino

Prepare um corpus UTF-8 a partir de fontes cuja licença foi revisada. O manifesto declara as fontes e o arquivo consolidado em `training_corpus`; o trainer exige esse manifesto no treino normal. O bypass `--allow-unverified-data` é exclusivamente para smoke tests sintéticos:

```bash
python -m kellvyn_brain.train --data ./data/prepared_train.txt --manifest ./data/manifest.json --steps 200 --seq-len 64 --batch-size 8 --bpe-merges 64 --checkpoint ./runs/kellvyn-toy.pt
```

O BPE desta primeira versão é intencionalmente simples e legível; treinar/encodar é caro para corpus grande. Antes de pré-treinamento sério, precisaremos de uma implementação otimizada e validada em corpus documentado.

O comando é apenas uma prova de funcionamento. A configuração pequena não representa a arquitetura de produção nem inteligência conversacional.

## Próximas etapas

1. Rodar os testes e o overfit de corpus minúsculo.
2. Otimizar o tokenizer BPE e medir compressão por idioma.
3. Criar manifesto de dados e validação de licença.
4. Planejar treino em GPU a partir de orçamento medido.
5. Expandir avaliação para português, matemática, código e instruções.
6. Somente depois integrar inferência ao app Android nativo.

Não use arquivos de conversas privadas, tokens, senhas ou dados pessoais para treino.


## Avaliar o tokenizer de um checkpoint

Depois de treinar um checkpoint, compare o BPE aprendido no projeto com o baseline byte-level usando um corpus de avaliação separado do treino:

```bash
kellvyn-tokenizer-benchmark \
  --checkpoint ./runs/kellvyn-toy.pt \
  --data ./data/heldout_portuguese.txt \
  --output ./runs/tokenizer-benchmark.json \
  --repeats 5
```

O relatório registra hashes do checkpoint, corpus e vocabulário; bytes UTF-8 úteis; quantidade de tokens; redução percentual frente ao baseline; reconstrução exata por documento; e tempos medianos de codificação. Os documentos são medidos separadamente para não criar merges artificiais entre fronteiras.

Use dados com origem e licença documentadas. O relatório é uma medição do tokenizer, não uma avaliação de raciocínio ou qualidade conversacional. Tempos só são comparáveis em máquinas e condições equivalentes.


## Validar proveniência e integridade do corpus

Antes de usar dados em treinamento de produção, prepare um manifesto JSON com uma entrada por fonte. Cada entrada precisa incluir `source_id`, `file` relativo à raiz, `reference`, `license`, `rights_basis`, `language`, `domain`, `collected_at`, `sha256`, `bytes` e `document_count`.

Exemplo mínimo de estrutura:

```json
{
  "schema_version": 1,
  "corpus_id": "kellvyn-pt-v1",
  "sources": [
    {
      "source_id": "fonte-pt-001",
      "file": "fontes/texto.txt",
      "reference": "https://exemplo.org/dataset-card",
      "license": "CC-BY-4.0",
      "rights_basis": "Atribuição mantida conforme a licença",
      "language": "pt-BR",
      "domain": "educacao",
      "collected_at": "2026-11-20",
      "sha256": "HASH_SHA256_DO_ARQUIVO",
      "bytes": 12345,
      "document_count": 250
    }
  ]
}
```

Valide antes de preparar o corpus:

```bash
kellvyn-validate-corpus --manifest ./data/manifest.json --root ./data --output ./runs/corpus-validation.json
```

O validador confere campos de proveniência, datas, caminhos relativos sem escape da raiz, UTF-8, SHA-256, tamanho e quantidade de documentos. Ele **não decide juridicamente** se uma licença permite treinamento; essa revisão permanece obrigatória. Nenhum conjunto de dados é incluído neste repositório.


O objeto `training_corpus` do manifesto identifica exatamente o arquivo passado em `--data`, seu SHA-256, tamanho, quantidade de documentos, fontes de origem (`derived_from`) e notas de preparação. O trainer rejeita arquivo diferente, hash divergente ou fonte não declarada. O bypass `--allow-unverified-data` deve ficar restrito aos testes sintéticos e nunca ser usado em produção.


## Retomar treinamento interrompido

O trainer grava um checkpoint recuperável a cada \`--checkpoint-every\` passos (padrão: 1000), substituindo o arquivo de forma atômica. O checkpoint contém pesos, estado do AdamW, tokenizer, configuração, proveniência, passo global e estados aleatórios do Python/PyTorch; também salva CUDA/MPS quando esses dispositivos estão em uso. As métricas são acrescentadas em JSONL.

Exemplo de continuação do mesmo experimento:

\`\`\`bash
kellvyn-train \\
  --data ./data/prepared_train.txt \\
  --manifest ./data/manifest.json \\
  --checkpoint ./runs/kellvyn-latest.pt \\
  --resume-from ./runs/kellvyn-latest.pt \\
  --steps 50000 \\
  --checkpoint-every 1000
\`\`\`

\`--steps\` é o alvo total, não a quantidade adicional. A retomada rejeita mudanças no hash do corpus/manifesto, tokenizer, arquitetura, hiperparâmetros essenciais ou dispositivo. Para iniciar outro experimento, omita \`--resume-from\` e use outro caminho de checkpoint. Checkpoints antigos sem estado RNG completo não são aceitos para retomada exata.
