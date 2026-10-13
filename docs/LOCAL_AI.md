# Motor local de IA

O Kellvyn usa a API Kotlin do LiteRT-LM para executar um modelo compatível dentro do Android. Quando o modo LOCAL está selecionado, as mensagens de inferência ficam no aparelho; o modelo é armazenado na pasta privada do app e executado no backend CPU.

## Ativação rápida

A tela LOCAL oferece o botão **Baixar modelo offline básico (~374 MB)**. Ele baixa e verifica automaticamente o modelo público [SmolLM2-360M-Instruct para LiteRT-LM](https://huggingface.co/litert-community/SmolLM2-360M-Instruct), publicado sob licença Apache-2.0. O arquivo possui cerca de 374 MB e a página do modelo não indica a exigência de login/aceitação de uma licença restrita. O primeiro download precisa de internet; depois de carregado, as mensagens no modo LOCAL são processadas no aparelho.

Esse é um modelo pequeno de entrada: serve para verificar o funcionamento do motor e tarefas simples, mas não deve ser tratado como equivalente a um modelo grande para programação. A integridade do arquivo baixado é verificada por tamanho e SHA-256 antes de ser instalado.

## Carregar outro modelo

1. Baixe um arquivo .litertlm compatível com a versão LiteRT-LM usada no aplicativo.
2. Toque em **Selecionar modelo local (.litertlm)** e escolha o arquivo no seletor do Android.
3. Aguarde a cópia e inicialização. O status deve confirmar o carregamento antes de enviar mensagens.
4. A tela mostra o tamanho do modelo salvo e permite removê-lo para liberar espaço.

Uma opção maior é o [Gemma 3 1B IT quantizado](https://huggingface.co/litert-community/Gemma3-1B-IT), aproximadamente 584 MB. Esse repositório pode exigir login e aceitação de condições/licença da Gemma antes do download. A compatibilidade e o desempenho de qualquer modelo maior ainda precisam ser testados no Poco F6 real.

## Privacidade e limitações

- As conversas e memórias explícitas ficam no armazenamento privado local. O backup Android está desativado para os dados do app.
- No modo LOCAL, a inferência não envia prompts a um servidor.
- No modo ONLINE, o texto da conversa e as memórias relevantes são enviados ao endpoint HTTPS configurado pelo usuário; isso pode envolver custos e termos do provedor.
- A entrada por voz depende de um serviço de reconhecimento instalado no Android. A saída “Ouvir” depende de uma voz em português disponível no serviço TTS do aparelho.
- Arquivos .litertlm de modelos diferentes não são intercambiáveis só por causa da extensão; o arquivo precisa ser realmente compatível.
- Uma compilação de sucesso não substitui testes de instalação, memória, aquecimento, desempenho e geração real no dispositivo físico.
