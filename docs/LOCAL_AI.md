# Motor local de IA

O Kellvyn usa LiteRT-LM para executar o modelo conversacional dentro do Android, no backend CPU. O chat usa um único modelo local; não há provedor de inferência externo, seletor de modelos ou alternância de modos.

## Como o modelo fica disponível

1. Na inicialização, o app procura um arquivo de modelo já salvo e validado em `files/models`.
2. Se o APK contiver `models/kellvyn-model.litertlm`, o app instala essa cópia privada depois de conferir tamanho e SHA-256.
3. Se o APK não contiver o arquivo, o app baixa diretamente o modelo público LiteRT-LM SmolLM2-360M-Instruct, de licença Apache-2.0, para o armazenamento privado do próprio aparelho. A transferência tem aproximadamente 374 MB, exibe progresso real no estado do chat e verifica o tamanho e o SHA-256 antes de aceitar o arquivo. Nenhum prompt é enviado para o site de origem.
4. Depois que o arquivo foi guardado, a inferência ocorre localmente e pode funcionar sem rede. A internet só é necessária para a primeira transferência quando não há modelo embutido, e para pesquisas externas que o usuário pedir explicitamente.
5. Se a transferência falhar ou a integridade não conferir, o modelo não é carregado nem tratado como pronto. O erro aparece no status; é possível tentar novamente ao abrir o app com uma conexão disponível.

Fonte do modelo e licença: https://huggingface.co/litert-community/SmolLM2-360M-Instruct

## Limites atuais

O modelo é pequeno e voltado principalmente a conversa e tarefas básicas. Não há garantia de que produza aplicativos complexos sem erros. A qualidade real, o tempo de resposta, o consumo de RAM e o aquecimento ainda precisam ser medidos no Poco F6.

Não existem seletor de modelos, modos leve/pesado, provedor externo, downloader de modelos alternativos ou troca de modelo nesta revisão.

## Privacidade e comportamento

- Conversas e memórias explícitas ficam no armazenamento privado local do aplicativo.
- O texto do chat não é enviado a um endpoint externo de IA.
- A pesquisa web/GitHub é separada da inferência. Só pode ser acionada quando o usuário a pedir e a permissão correspondente estiver ligada.
- O app não afirma que a IA está pronta antes que o runtime local confirme a inicialização.
- O ditado e a leitura em voz alta usam serviços nativos do Android e ainda precisam de validação prática no Poco F6.
