# Motor local de IA

O Kellvyn usa LiteRT-LM para executar o modelo conversacional dentro do Android, no backend CPU. O modelo ativo desta revisão é único e local; o chat não precisa de uma conta, chave ou provedor externo de IA.

## Como o núcleo inicia

- O APK de distribuição inclui o arquivo `models/kellvyn-model.litertlm` (~374 MB).
- Na primeira abertura, o app copia o modelo para a pasta privada `files/models` e confere tamanho e SHA-256 antes de inicializar o motor.
- O carregamento acontece em segundo plano. O compositor mostra “Preparando IA” e só indica “IA local” pronta quando o motor confirma que carregou.
- Se o modelo estiver ausente, corrompido ou incompatível, o app mostra o erro; não deve simular respostas nem afirmar que a IA está pronta.
- Depois de instalado o APK e copiado o modelo, a inferência conversacional pode funcionar sem rede. A pesquisa web/GitHub é separada e só ocorre quando o usuário pede explicitamente esse acesso no chat.

## Limites atuais

O modelo incluído é leve, voltado a tarefas básicas. Não deve ser tratado como um modelo avançado de programação, nem há garantia de que gere apps complexos sem erros. A qualidade real, o tempo de resposta, o consumo de RAM e o aquecimento ainda precisam ser medidos no Poco F6.

Esta revisão **não** contém seletor de modelos, downloader de modelos adicionais, importação de arquivos `.litertlm` ou alternância automática para Qwen Coder. A configuração anterior documentava esses recursos, mas o código ativo agora usa apenas o modelo empacotado. Não os apresentar como disponíveis até que voltem a ser implementados e testados.

## Privacidade e comportamento

- Conversas e memórias explícitas ficam no armazenamento privado local do aplicativo.
- O texto do chat não é encaminhado a um endpoint de IA externo.
- Se o usuário pedir pesquisa na web/GitHub ou uma ação sobre um link, o agente pode consultar o site solicitado. Esse acesso externo não troca o motor conversacional local por um provedor online.
- A interface não deve mostrar seletor de provedor externo, pedido de configuração de endpoint ou botões de áudio sem ação funcional.
- Uma compilação Android bem-sucedida não substitui testes de inicialização, conversa, instalação e desempenho no aparelho físico.
