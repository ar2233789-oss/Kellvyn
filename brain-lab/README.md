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

Prepare um arquivo UTF-8 de texto cujo uso seja autorizado e documente a origem/licença. Para validar o pipeline, comece com um texto pequeno e repetitivo:

```bash
python -m kellvyn_brain.train --data ./data/toy_corpus.txt --steps 200 --seq-len 64 --batch-size 8 --bpe-merges 64 --checkpoint ./runs/kellvyn-toy.pt
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
