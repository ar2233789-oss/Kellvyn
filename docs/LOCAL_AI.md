# Motor local de IA

O Kellvyn usa LiteRT-LM para executar o modelo conversacional dentro do Android, no backend CPU. O código prevê um único modelo local; não há provedor externo, seletor de modelo ou alternância de modos.

## Pré-requisito obrigatório de distribuição

O motor exige este arquivo dentro do APK:

`app/src/main/assets/models/kellvyn-model.litertlm`

Tamanho esperado: `373719040` bytes. SHA-256 esperado: `8e2834da211b439751af968ed650febdde5a8cb8d88bc6c1a3059f049caa5c2e`.

**Esse arquivo não está presente no checkout atual.** A antiga automação de empacotamento baixava o arquivo apenas durante uma build remota; essa automação foi removida por exigência de build local. Portanto, a build atual do aplicativo anfitrião ainda não pode ser considerada pronta. Antes de compilar uma distribuição, é necessário obter o modelo licenciado por um caminho local, confirmar tamanho e SHA-256 e colocá-lo no caminho acima. Não substituir essa etapa por um modelo diferente ou por respostas simuladas.

## Inicialização do modelo

- Na primeira abertura, se o modelo válido estiver dentro do APK, o app copia-o para `files/models` e confere tamanho e SHA-256 antes de inicializar o runtime.
- O carregamento acontece em segundo plano. O compositor só deve indicar “IA local” pronta quando o motor confirmar que carregou.
- Se o modelo estiver ausente, corrompido ou incompatível, o app informa a falha e não simula respostas.
- Com o modelo instalado no armazenamento privado, a inferência pode funcionar sem rede. A pesquisa web/GitHub é separada e só ocorre quando o usuário pede explicitamente esse acesso no chat.

## Limites atuais

O modelo de porte pequeno previsto é destinado principalmente a conversa e tarefas básicas. Não há garantia de que gere aplicativos complexos sem erros. A qualidade real, o tempo de resposta, o consumo de RAM e o aquecimento ainda precisam ser medidos no Poco F6.

Não existem seletor de modelos, modos leve/pesado, provedor externo, downloader de modelos alternativos nem fluxo de troca de modelos nesta revisão.

## Privacidade e comportamento

- Conversas e memórias explícitas ficam no armazenamento privado local do aplicativo.
- O texto do chat não é encaminhado a um endpoint externo de IA.
- Se o usuário pedir pesquisa web/GitHub ou uma ação sobre um link, o agente pode consultar o recurso solicitado. Isso não troca o motor conversacional local por um serviço de inferência online.
- A pesquisa externa usa a conexão ativa do Android e deve ser acionada apenas quando o pedido atual a solicitar.
- O código de voz usa serviços nativos do Android; o ditado e a leitura em voz alta ainda precisam de validação prática no aparelho-alvo.
