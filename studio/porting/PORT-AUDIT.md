# Auditoria da interface Lovable vs. Android nativo

Data da auditoria: 2026-10-20  
Referência imutável: `studio/lovable-source/` — upstream `d64ddeef348ea5378e26e855bc3870b8f264e462`.

## Estado da transferência

A cópia de referência foi importada para o Studio, incluindo o código React/CSS da interface, o projeto Android Java/Kotlin existente, os arquivos de build e os testes. Isso é uma cópia de código para análise, não uma confirmação de que a interface já esteja fiel no APK.

## Diferenças observadas no código

| Área | Original web | Android nativo atual | Ação necessária |
|---|---|---|---|
| Cabeçalho | Menu abre histórico; título “Kellvyn Assistente”; três pontos abrem painel completo de configurações | Histórico e título semelhantes, mas três pontos abrem um PopupMenu curto e depois diálogos separados | Recriar o painel de configurações nativo completo; não trocar a função dos três pontos |
| Configurações | Um painel com cores personalizadas, brilho 0–60%, paletas, efeitos, memória, atalhos e proteção | Só quatro cores prontas; opções separadas em diálogos; brilho e seletor HSV da web não têm equivalente evidente | Mapear cada controle original e implementar o mesmo resultado nativamente |
| Efeitos | Canvas web com requestAnimationFrame, tempo normalizado por delta-time, redimensionamento e pausa/retomada por visibilidade | View/Canvas nativo; 64 gotas fixas, partículas fixas e quadro agendado a cada 45 ms | Igualar densidade, velocidade baseada no tempo, tamanho, alfa, resize e ciclo de vida |
| Chuva + raios | Raios são uma camada independente e podem coexistir com chuva/neve/folhas | O modelo de seleção também permite coexistência; a renderização chama cada camada separadamente | Preservar a combinação; testar especificamente chuva + raios e a opção “Nenhum” |
| Navegação inferior | AI, Navegador, Editor, Terminal, Bots e Apps, com ícones e comportamento próprios | Seis itens existem, mas são símbolos textuais e alguns abrem diálogos em vez dos painéis originais | Preservar os seis destinos e aproximar ícones, tamanhos, estados e comportamento |
| Compositor | Caixa arredondada, botão +, estado “Kellvyn”, microfone, enviar e cartão de anexo | Campo nativo com +, status, microfone e enviar, mas com arranjo visual diferente | Recriar a hierarquia, espaçamento e estados sem introduzir novos botões |
| Animações | Animações CSS e indicador de pensamento React | Views e animações Android separadas | Não converter animações por aproximação visual: comparar estados e sincronização com a referência |

## Observações técnicas sobre chuva e raios

- A animação web usa `requestAnimationFrame`, calcula `dt` a partir do tempo real e limita o delta para evitar saltos após engasgos.
- A chuva web ajusta a quantidade de partículas à área de tela (até 160 por tipo) e redimensiona o canvas com limite de `devicePixelRatio = 2`.
- A animação nativa atual agenda novos quadros a cada 45 ms (~22 quadros/s em condições ideais) e move as gotas por quadro. Portanto, a velocidade varia com a cadência real do dispositivo.
- A chuva nativa usa 64 gotas fixas e cores/comprimentos diferentes. O relâmpago nativo dura três quadros e a web usa um tempo de brilho interpolado. Essas diferenças são candidatas concretas a explicar por que a animação parece diferente ou irregular.
- Ambos os códigos mantêm raios como camada independente; não remover essa capacidade.

## Inventário inicial de controles da interface web

- Cabeçalho: histórico e configurações (três pontos).
- Chat: bolhas, estado “Thinking”, indicador de pensamento, copiar, ouvir/parar leitura, baixar e compartilhar.
- Compositor: anexar arquivo/imagem, plugins, ativar/desativar acesso à Web, remover anexo, ditado por voz e enviar.
- Navegação inferior: AI, Navegador, Editor, Terminal, Bots e Apps.
- Configurações: três cores editáveis com seletor próprio, intensidade de brilho, temas prontos, chuva/neve/folhas/raios/nenhum, memória local, atalhos e proteção.
- Painéis adicionais: histórico, apps/projetos, apps do celular, memória, editor de código, navegador, tarefas, bots ativos e plugins.

## Regras para a implementação

1. Não alterar a cópia de referência em `studio/lovable-source/`.
2. Trabalhar em uma cópia nativa separada dentro do Studio.
3. Usar a versão web para inventário e comparação, nunca como WebView de produção.
4. Preservar a lógica original dos três pontos e todos os seis destinos da barra inferior.
5. Não declarar a conversão concluída até compilar o APK, executar testes e comparar visualmente a interface e as animações.

## Atualização de execução — 2026-10-21

### Ajustes já aplicados na cópia Android

- Cabeçalho nativo: incluído o símbolo luminoso Kellvyn e o atalho superior Apps/APKs, ausentes na primeira versão do port.
- Status do cabeçalho: agora reutiliza o estado real de prontidão do motor local em vez de exibir um rótulo estático.
- Clima: a quantidade desenhada de gotas/neve agora é dimensionada pela área da tela, com limite de 160 partículas por camada, aproximando as densidades por área do Canvas original.
- A cópia de referência em `studio/lovable-source/` continua intocada.

### Ainda não certificado como paridade 100%

- A composição principal continua implementada com Android Views/Canvas, enquanto o original usa React/CSS. Os dois não são automaticamente pixel-idênticos.
- Os ícones da barra inferior ainda usam glifos de texto, não os mesmos desenhos vetoriais do original.
- A área de composição, balões de mensagens, ações de resposta e painéis de configurações ainda precisam de comparação visual e ajustes de medidas/estados.
- A chuva nativa ainda precisa de comparação lado a lado; a contagem foi corrigida, mas sprites, alfa por partícula, comprimentos, rotação e temporização podem continuar diferentes.
- O catálogo Apps/APKs ainda é um placeholder e não deve ser considerado funcionalmente concluído.
- Assistente virtual e modo de jogo continuam desativados; não devem ser apresentados como recursos prontos.
- O workflow de compilação/testes está sendo executado para o commit atual. Só um resultado verde pode confirmar compilação e testes automatizados; isso não certifica a aparência no Poco F6 físico.
- A publicação do Studio pela GitHub Pages depende de Pages estar habilitado no repositório; a conexão de automação não tem permissão administrativa para ativar essa configuração.

**Critério de conclusão:** não declarar 100% de identidade até que a versão nativa seja compilada, suas telas/estados sejam capturados no laboratório e comparados com capturas correspondentes do Lovable, e os controles principais sejam testados. Nenhuma mudança nesta auditoria autoriza editar a fonte original.
