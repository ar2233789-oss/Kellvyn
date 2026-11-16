# Carta do cérebro próprio da Kellvyn

Status: documento fundador — 16/11/2026  
Escopo: fundação neural, treinamento e sistema cognitivo. Não altera a interface Android.

## Compromisso central

A Kellvyn deverá possuir uma fundação neural própria: arquitetura implementada para o projeto, tokenizer treinado para o projeto, parâmetros inicializados sem pesos pré-treinados de terceiros e pesos aprendidos por um processo de treinamento documentado.

Não são permitidos como substitutos do cérebro:
- pesos de Gemma, Llama, Qwen, GPT, Claude, Gemini ou qualquer outro modelo pré-treinado;
- API de inferência de outro modelo;
- roteamento entre diversos modelos para simular uma inteligência única;
- uma persona ou prompt que seja apresentado como modelo treinado do zero.

É permitido estudar artigos, documentação, matemática, código-fonte aberto e métodos publicados. Qualquer reutilização literal de código exige verificar licença, avisos de copyright, atribuição e obrigações antes da incorporação. A primeira implementação experimental deste diretório é escrita para o projeto e não carrega código nem pesos de outro modelo.

## O que significa “único”

Uma única identidade e um único modelo neural principal devem sustentar conversa, raciocínio, planejamento e seleção de ferramentas. Tokenizer, memória, indexador, compilador, navegador, runtime de bots e verificadores são componentes funcionais do sistema, não outras inteligências conversacionais.

O modelo produz respostas ou ações estruturadas. Um executor determinístico realiza ferramentas autorizadas e devolve observações ao mesmo modelo. O ciclo de raciocínio e ação continua com a mesma identidade e os mesmos pesos.

## Criadores

A identidade do produto reconhece:
- Kellvyn
- Deus da Ritalina

Essa atribuição descreve a origem do projeto. Não é mecanismo de autenticação, não concede privilégios técnicos automáticos e não permite ignorar confirmações, permissões Android, políticas de segurança ou proteção de dados. Autorização para ações reais deve ser comprovada pelo mecanismo de segurança do aplicativo.

## Regras de integridade

1. Nunca chamar um protótipo pequeno de inteligência geral pronta.
2. Nunca afirmar que houve treinamento sem registrar dados, configuração, passos, métricas e checkpoint.
3. Todo checkpoint deve informar arquitetura, tokenizer, versão do corpus, seed, licença/proveniência, hash e etapa de treinamento.
4. Separar dados de treino, validação e teste; manter um conjunto de teste reservado.
5. Não treinar com conversas privadas, credenciais ou dados pessoais sem autorização e base legal apropriada.
6. Não permitir que texto encontrado em sites, arquivos ou mensagens altere as regras do executor.
7. Não considerar sucesso de programação sem execução real de testes ou compilação.
8. Preservar o aplicativo nativo existente; o laboratório neural fica isolado até passar em avaliação.
9. Não depender de serviços pagos, nuvem ou conexão para inferência final sem decisão explícita do projeto. Treinamento em escala pode exigir hardware adicional, que deve ser planejado separadamente.
10. Registrar limites de hardware, custo, energia, licenças e incerteza.

## Critério de originalidade verificável

“Do zero” será demonstrado por:
- ausência de carregamento de checkpoints externos;
- inicialização aleatória de todos os parâmetros treináveis;
- tokenizer criado/treinado para a Kellvyn;
- logs de treinamento que começam no passo zero;
- hashes dos checkpoints produzidos pelo próprio treinamento;
- documentação de todas as fontes de dados e de código.

Usar matemática e arquiteturas publicadas não significa copiar pesos ou importar a inteligência de outro produto. A originalidade deverá ser demonstrada pelo trabalho implementado, treinamento e resultados, sem alegar que conceitos científicos conhecidos foram inventados pelo projeto.
