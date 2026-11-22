# Corpus semente `kellvyn_seed_v1.txt`

**O que é:** 53 conversas curtas em português do Brasil, no formato de `kellvyn_brain.chat_format`
(`<|usuario|>` / `<|kellvyn|>`, uma linha em branco entre conversas).

**O que ensina:** identidade (Kellvyn é o único criador; Deus da Ritalina é desenvolvedor, tutor e superior),
tom calmo e acolhedor, honestidade sobre limites (não gera vídeo, música nem imagem; não executa código;
não aprendeu a jogar), recusas educadas (senha de terceiros, letra protegida, token) e variação de
resposta para cumprimentos e agradecimentos.

**Origem:** texto original escrito para o projeto Kellvyn, a pedido do mantenedor, por Claude (Anthropic).
Não copia trechos de livros, sites, músicas, outros modelos ou conversas privadas, e não contém dados
pessoais, tokens ou senhas.

**Licença/direitos:** a escolha da licença é do mantenedor (Kellvyn). Os campos do manifesto gerado no CI
usam `original-do-projeto-kellvyn` como rótulo. O validador só confere que os campos estão preenchidos; ele
não decide juridicamente nada.

**Limites:** 53 conversas ensinam estilo e identidade a um modelo minúsculo. Não ensinam conhecimento do
mundo, raciocínio nem português fluente. Para conversa de verdade é preciso muito mais texto, com origem e
licença documentadas, e treino em GPU.

**Como gerar o manifesto e treinar** (o CI faz isso automaticamente em `.github/workflows/brain-lab.yml`):

```bash
kellvyn-build-manifest \
  --text brain-lab/data/seed_pt/kellvyn_seed_v1.txt \
  --manifest brain-lab/data/seed_pt/manifest.json \
  --corpus-id kellvyn-seed-v1 --source-id kellvyn-seed-v1 \
  --reference "brain-lab/data/seed_pt/PROVENANCE.md" \
  --license original-do-projeto-kellvyn \
  --rights-basis "Texto original escrito para o projeto Kellvyn a pedido do mantenedor; sem material de terceiros" \
  --domain dialogos-identidade-tom --collected-at 2026-11-21

kellvyn-train --data brain-lab/data/seed_pt/kellvyn_seed_v1.txt \
  --manifest brain-lab/data/seed_pt/manifest.json \
  --checkpoint ./runs/kellvyn-seed.pt --steps 400 --batch-size 16 --seq-len 64 \
  --width 64 --layers 2 --heads 4 --learning-rate 3e-3 --bpe-merges 128 --device cpu
```

O `manifest.json` não é versionado: ele é gerado a partir do arquivo, então o hash nunca fica desatualizado.
