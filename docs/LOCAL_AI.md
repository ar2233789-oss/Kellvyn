# Motor local de IA

O projeto usa a API Kotlin do LiteRT-LM para executar um modelo compatível dentro do Android. O caminho implementado não envia mensagens a um servidor: o modelo escolhido é copiado para o armazenamento privado do aplicativo e a geração usa o backend CPU.

## Modelo para o primeiro teste

- Modelo candidato: [Gemma 3 1B IT em formato LiteRT-LM](https://huggingface.co/litert-community/Gemma3-1B-IT/blob/main/gemma3-1b-it-int4.litertlm).
- Arquivo esperado: `gemma3-1b-it-int4.litertlm`.
- Tamanho publicado: aproximadamente 584 MB.
- O repositório do modelo exige login e aceitação das condições/licença Gemma antes de baixar os arquivos.
- Esta é uma opção inicial de teste, não uma validação de desempenho no Poco F6. O arquivo do modelo não está incluído no APK.

## Como ativar

1. Abra a página oficial do modelo no Hugging Face, entre na conta e aceite as condições de uso, caso sejam apresentadas.
2. Baixe o arquivo `.litertlm` compatível para o Android e aguarde o download completo.
3. Abra Kellvyn IA e toque em **Selecionar modelo local (.litertlm)**.
4. Escolha o arquivo baixado no seletor de documentos do Android.
5. Aguarde a cópia e inicialização. O status deve informar que o modelo está pronto antes de enviar mensagens.

A primeira inicialização pode levar vários segundos. Modelos e formatos diferentes não são intercambiáveis: selecione um arquivo `.litertlm` realmente compatível com a versão do LiteRT-LM usada no app.

## Privacidade e limitações

- As mensagens salvas pelo app ficam no armazenamento privado local.
- O código de inferência usa o motor local; não faz chamadas de rede para gerar respostas.
- A opção de IA online ainda não foi implementada.
- Selecionar qualquer arquivo com a extensão certa não garante que o conteúdo seja um modelo válido; erros de inicialização devem ser exibidos.
- Uma compilação de sucesso não substitui os testes de instalação, memória e desempenho no aparelho real.
