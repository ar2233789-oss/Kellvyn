# Kellvyn Brain Lab — fundação neural do zero

Este diretório é o primeiro laboratório de treinamento próprio da Kellvyn.

**Estado:** protótipo inicial de engenharia, não um modelo conversacional útil e não uma inteligência geral.

## Garantias deste protótipo

- Não baixa nem carrega pesos externos.
- Não importa Gemma, Llama, Qwen, GPT, Claude, Gemini ou outro checkpoint.
- Inicializa parâmetros aleatoriamente.
- Implementa tokenizer byte-level e uma rede causal pequena no código deste projeto.
- Pode treinar um modelo minúsculo a partir de um arquivo de texto fornecido pelo pesquisador.
- Salva arquitetura e pesos em um checkpoint local.

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
python -m kellvyn_brain.train --data ./data/toy_corpus.txt --steps 200 --seq-len 64 --batch-size 8 --checkpoint ./runs/kellvyn-toy.pt
```

O comando é apenas uma prova de funcionamento. A configuração pequena e o tokenizer por bytes não representam a arquitetura de produção.

## Próximas etapas

1. Rodar os testes.
2. Fazer o teste de overfit de corpus minúsculo e verificar se a loss cai.
3. Implementar tokenizer BPE próprio treinado sobre o corpus selecionado.
4. Criar manifesto de dados e validação de licença.
5. Planejar treino em GPU a partir de orçamento medido.
6. Somente depois integrar inferência ao app Android nativo.

Não use arquivos de conversas privadas, tokens, senhas ou dados pessoais para treino.
