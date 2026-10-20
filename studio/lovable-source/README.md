# Kellvyn AI Assistant

Pensou por 1m 21s



Agora sim. A descrição abaixo é para você entregar diretamente à IA que vai gerar a estrutura visual. Separei o desenho da tela, a função do hambúrguer, as opções dos três pontos, o catálogo Apps/APKs e os controles do chat. A imagem original deve servir de referência visual; as regras abaixo definem o comportamento e as coisas que não podem ser inventadas.



# ESPECIFICAÇÃO COMPLETA — INTERFACE DO KELLVYN IA



## 1. Objetivo principal



Criar a interface Android nativa do **Kellvyn IA**, seguindo a imagem de referência fornecida. A imagem é a fonte principal para a aparência: composição, posições, proporções, espaçamentos, bordas, ícones, iluminação e efeito de chuva.



O resultado precisa parecer o aplicativo da referência, não uma interface genérica de chatbot.



**Identidade visual**

- Fundo preto profundo, com painéis muito escuros.

- Verde-esmeralda e verde-turquesa como cores principais de destaque.

- Texto claro e legível.

- Ícone da Kellvyn IA luminoso, com brilho perceptível, sem ficar apagado ou quase invisível.

- Bordas discretas, contornos verdes e aparência tecnológica.

- Efeito de chuva digital conforme a referência, sem substituir por outra animação inventada.

- Interface compacta, adequada à tela de um celular Android.



A cor padrão deve ser verde-esmeralda. A possibilidade de personalizar a cor fica nas configurações.



## 2. Estrutura da tela principal



A tela principal é um chat único, com cabeçalho no topo, conversa ocupando a maior parte da tela e campo de mensagem fixado próximo ao rodapé.



### A. Barra superior



Organizar horizontalmente, da esquerda para a direita:



1. **Ícone hambúrguer (☰):** abre o histórico de conversas.

2. **Símbolo luminoso da Kellvyn IA:** deve estar nítido e visível.

3. **Nome “Kellvyn Assistente”.**

4. **Status pequeno abaixo do nome:** por exemplo, “KELLVYN IA • LOCAL”, com o estado real de inicialização, disponibilidade ou prontidão.

5. **Ícone de Apps/APKs:** abre o catálogo de aplicativos e projetos salvos.

6. **Ícone de três pontos verticais (⋮):** abre as configurações.



Manter tudo compacto. Os ícones devem ter tamanho e espaçamento equilibrados, sem roubar espaço da conversa.



Adicionar o divisor horizontal discreto abaixo do cabeçalho, de acordo com a imagem de referência.



### B. Área da conversa



A conversa ocupa o espaço central e deve poder rolar verticalmente sem movimentar o cabeçalho ou o compositor fixo.



**Mensagens do usuário**

- Alinhadas à direita.

- Balões em verde muito escuro.

- Contorno verde-esmeralda discreto.

- Texto claro, com boa legibilidade.

- Imagens anexadas aparecem dentro da mensagem, em tamanho adequado, sem deformação.



**Mensagens da IA**

- Alinhadas à esquerda.

- Pequeno ícone da Kellvyn IA junto à resposta.

- Balões ou painéis escuros com bordas sutis.

- Texto claro, espaçamento confortável e suporte a respostas longas.

- Manter o brilho do símbolo da IA sem exagerar nos efeitos.



**Ações abaixo de cada resposta da IA**



Manter uma linha de ações compacta, com os controles disponíveis na referência:



- **Copiar:** copia a resposta.

- **Ouvir:** lê a resposta em voz alta.

- **Baixar:** salva o conteúdo quando o tipo de resposta permitir.

- **Enviar/compartilhar:** disponibiliza o conteúdo pelas opções compatíveis do Android.



Cada botão deve executar uma ação real. Não criar botões decorativos sem função.



### C. Campo de mensagem fixo



O compositor deve permanecer próximo ao rodapé, mesmo quando o histórico da conversa rolar.



Elementos:



1. Campo de texto com a indicação “Converse ou peça para criar um aplicativo…”.

2. Identificação compacta da Kellvyn IA e seu estado real.

3. Botão **+** para abrir as opções de anexos e acesso à Web.

4. Botão de microfone para ditado em português.

5. Botão de envio com ícone de seta.



Comportamento:

- O campo aceita textos curtos e longos.

- O texto digitado não deve ser enviado automaticamente.

- O microfone transforma fala em texto quando o serviço do Android estiver disponível; não envia sozinho a mensagem.

- O envio encaminha a mensagem para a única Kellvyn IA.

- Quando houver uma imagem ou arquivo anexado, apresentar uma prévia e permitir remover o anexo antes de enviar.

- O botão de envio deve refletir o estado real do processamento, sem mostrar sucesso falso.



### D. Menu do botão “+”



Ao tocar em **+**, abrir um menu pequeno associado ao compositor. Não transformar esse menu em uma tela separada.



Opções:



**1. Imagem ou arquivo**

- Abre o seletor de arquivos do Android.

- Permite selecionar imagens e os tipos de arquivo que o aplicativo realmente suporta.

- A imagem deve aparecer em uma prévia no compositor.

- Deve ser possível remover o anexo antes do envio.



**2. Acesso à Web**

- Liga ou desliga o módulo de acesso à Web da Kellvyn IA.

- Quando ligado, permite consultar páginas e informações externas pela conexão Wi-Fi ou pelos dados móveis disponíveis no próprio celular.

- A mesma IA continua sendo utilizada. Não trocar de modelo, provedor ou identidade.

- Quando desligado, informar que o acesso à Web foi desativado e que a IA permanece disponível localmente, conforme o estado real do modelo.

- Mostrar o estado verdadeiro do recurso.



O acesso à Web também pode ser controlado pelas configurações. As duas opções devem utilizar a mesma preferência, para que o estado não fique diferente entre os menus.



---



## 3. Ícone hambúrguer (☰) — histórico de conversas



O hambúrguer tem uma função específica: **abrir o histórico de conversas**. Não deve abrir um menu geral de ferramentas nem permitir trocar o modelo da IA.



Ao tocar nele, mostrar uma área com o título “CONVERSAS”.



### Opções do histórico



**1. Nova conversa**

- Inicia uma conversa nova.

- Mantém as conversas anteriores salvas.

- A conversa nova passa a ser a conversa ativa.



**2. Lista de conversas salvas**

- Exibe as conversas existentes.

- Mostra o título de cada conversa.

- Pode mostrar a quantidade de mensagens enviadas pelo usuário.

- Ordena as conversas das mais recentes para as mais antigas.

- Destaca a conversa atualmente aberta.



**3. Abrir conversa**

- Retoma a conversa escolhida.

- Carrega suas mensagens salvas.

- A conversa escolhida passa a ser a ativa.



**4. Fechar**

- Fecha o histórico e retorna à tela do chat.



Não inventar ações de apagar, renomear ou exportar conversas se elas não fizerem parte da implementação solicitada. O histórico não deve conter seletores de IA, provedor ou modo de funcionamento.



---



## 4. Ícone dos três pontos (⋮) — configurações



Os três pontos verticais abrem exclusivamente as **Configurações do Kellvyn**.



As opções precisam ficar organizadas por categorias, em uma tela ou janela rolável, com nomes claros e controles compactos. Os elementos abaixo descrevem as funções esperadas.



### Categoria A — Cor da interface



Disponibilizar opções de cor predefinidas:



- Esmeralda: `#38E6BC`

- Azul: `#5CB8FF`

- Violeta: `#C59BFF`

- Vermelho: `#FF8A8A`

- Dourado: `#FFD166`

- Rosa: `#FF79C6`

- Lima: `#B5E853`

- Prata: `#E6E6E6`

- Laranja: `#FF7A45`

- Índigo: `#7C83FF`

- Ciano: `#38D6E8`

- Branco: `#FFFFFF`



Cada opção deve ter uma amostra visual da cor.



**Cor personalizada HEX**

- Campo para inserir uma cor no formato hexadecimal.

- Botão “Aplicar”.

- Aceitar apenas códigos HEX válidos de seis dígitos, com ou sem o `#` digitado inicialmente.

- Salvar a escolha para continuar usando a cor após fechar e reabrir o aplicativo.



A interface inicial deve continuar usando o verde-esmeralda da referência, e não violeta ou azul como cor dominante.



### Categoria B — Internet da Kellvyn IA



**Opção: Acesso à Web**

- Controle para ativar ou desativar o acesso à Web.

- Usa a conexão de Internet disponível no celular, por Wi-Fi ou dados móveis.

- Permite à mesma IA consultar páginas e informações externas quando necessário.

- Não significa trocar para uma IA online diferente.

- Não deve existir seletor de provedor ou troca manual de modelo nessa configuração.



O estado apresentado deve corresponder ao estado real do módulo.



### Categoria C — Efeito visual



**Opção: Chuva digital**

- Interruptor para ativar ou desativar a chuva.

- Preservar o efeito conforme a imagem de referência.

- Manter gotas e movimento discretos o suficiente para que as mensagens continuem legíveis.

- O efeito deve ficar atrás da conversa, sem bloquear textos ou controles.

- Salvar a preferência do usuário.



Não substituir a chuva por partículas, círculos ou outra animação só porque é mais fácil implementar.



### Categoria D — Atalhos



**1. Memória local**

- Abre o gerenciamento das memórias explicitamente salvas.

- Permite consultar o conteúdo guardado.

- Deve permitir adicionar ou editar informações quando a funcionalidade correspondente estiver disponível.

- Deve permitir excluir informações salvas.

- Não apresentar isso como treinamento automático do modelo.



**2. Assistente virtual**

- Interruptor para ativar ou desativar a bolha flutuante do assistente sobre outros aplicativos.

- Ao ativar, solicitar as permissões Android necessárias.

- Ao desativar, interromper o serviço de forma real.

- Se a permissão estiver ausente ou o serviço não puder iniciar, informar o problema claramente.



**3. Leitura de tela**

- Abre as configurações de Acessibilidade do Android para o usuário ativar o serviço do Kellvyn.

- A leitura de texto da tela ocorre conforme a ação solicitada pelo usuário.

- Capturas automáticas relacionadas a jogos dependem da ativação do Modo de jogo e das permissões necessárias.

- Não afirmar que a permissão está ativa sem verificar o estado real do Android.



### Categoria E — Modo de jogo



**Opção: Modo de jogo**

- Interruptor para habilitar ou desabilitar a captura automática de tela destinada à análise de jogos.

- Solicitar a ativação do serviço de Acessibilidade quando necessário.

- Ao desativar, interromper as capturas automáticas.

- Mostrar mensagens que representem o estado real da funcionalidade.



Importante: ativar esse controle não significa que a IA já consegue jogar qualquer jogo sozinha. Analisar imagens, entender o estado do jogo e executar controles são capacidades que precisam estar implementadas e testadas para serem anunciadas como funcionais.



### Categoria F — Segurança



**Opção: Proteção reforçada da IA**

- Interruptor para ativar ou desativar a proteção reforçada.

- Quando ativa, exigir confirmação antes das operações protegidas previstas, como alterações ou gravações de arquivos e compilação de APKs.

- Não executar operações sensíveis silenciosamente quando a proteção exigir confirmação.

- Salvar a preferência.



### Encerramento das configurações



**Botão “Fechar”**

- Fecha as configurações e retorna ao chat.

- Não apaga as preferências salvas.



A janela deve adaptar sua altura à tela e permitir rolagem para que nenhuma opção fique inacessível.



---



## 5. Ícone Apps/APKs — catálogo de projetos



O ícone de grade no cabeçalho abre a área **APPS, BOTS E PROJETOS**.



Essa área é um catálogo para consultar projetos criados e arquivos salvos. Não deve transformar o aplicativo inteiro em um conjunto de abas permanentes.



### Elementos do catálogo



- Título “APPS, BOTS E PROJETOS”.

- Subtítulo “Seus apps e projetos salvos”.

- Lista dos projetos existentes.

- Nome, descrição e tipo do projeto.

- Indicação real do estado de cada projeto.

- Ações específicas disponíveis para cada tipo de projeto.

- Botão “Fechar”.



### Opções do catálogo



**1. Códigos**

- Abre a área de edição de códigos salvos.

- Permite selecionar um projeto.

- Permite abrir e selecionar arquivos.

- Permite editar e salvar arquivos quando compatível.

- Permite baixar um arquivo individual.

- As ações de apagar devem ser explícitas e protegidas contra exclusões acidentais.



**2. Git**

- Abre a área de operações Git do workspace local.

- Permite selecionar um projeto.

- Permite consultar o status, listar branches e consultar commits.

- Permite inicializar o Git, preparar arquivos e criar commits locais quando possível.

- Operações que alterem o estado devem respeitar as confirmações necessárias.

- Não fingir que um commit local foi enviado para um servidor remoto.



**3. Navegador / Google**

- Abre o navegador externo do aparelho.

- Permite fazer pesquisas ou abrir endereços.

- Deve utilizar o navegador real do Android, sem substituir a interface por uma WebView.



### Cartões dos projetos



Cada projeto pode aparecer em um cartão discreto com nome, descrição, tipo e estado.



Ações possíveis, de acordo com o projeto:



- **Código:** abrir o editor.

- **Instalar APK:** aparece habilitado somente quando houver um APK real disponível.

- **APK pendente:** indica que ainda não há um APK pronto para instalar.

- **Manter ativo / Parar bot:** somente para projetos de bot cujo runtime seja realmente compatível.

- Estado de execução: mostrar apenas informações verificadas.



Se não houver projetos, exibir uma mensagem informativa e orientar o usuário a solicitar a criação de um projeto no chat principal.



A área de catálogo não pode criar um segundo chat nem uma segunda IA.



---



## 6. Funcionamento das respostas



As respostas da Kellvyn IA devem permanecer dentro da conversa principal.



Ações por resposta:

- Copiar o texto.

- Ouvir a resposta em voz alta.

- Baixar conteúdo quando aplicável.

- Enviar ou compartilhar conteúdo quando a ação estiver disponível.



Durante uma tarefa, mostrar somente estados reais, por exemplo:

- Inicializando.

- Pronta.

- Planejando.

- Executando.

- Compilando.

- Testando.

- Concluída.

- Erro.



Não mostrar uma tarefa como concluída, uma compilação como bem-sucedida ou um APK como disponível sem confirmação real.



A mesma Kellvyn IA deve atender a conversa comum, programação, criação de projetos, análise de arquivos e consulta à Web. A escolha da ferramenta ocorre internamente conforme a tarefa, sem pedir que o usuário troque de IA.



---



## 7. Regras obrigatórias de navegação



1. Existe uma única Kellvyn IA e uma única conversa principal como centro de interação.

2. O hambúrguer abre somente o histórico de conversas.

3. O ícone Apps/APKs abre somente o catálogo de projetos.

4. Os três pontos verticais abrem somente as configurações.

5. O botão + do compositor abre anexos e controle de acesso à Web.

6. O campo de mensagem e o botão de envio permanecem no rodapé.

7. A área das mensagens é rolável; cabeçalho e compositor permanecem estáveis.

8. Não criar uma barra de abas inferior com IA, Navegador, Editor, Terminal, Git e Apps.

9. Não recriar seletores de modo de IA, modelo ou provedor.

10. Não incluir Qwen Code como provedor ou opção de troca.

11. Não criar um botão ou uma tela manual separada para iniciar o construtor de aplicativos. A criação deve ser solicitada no chat principal e conduzida internamente pela IA.

12. Não mostrar o compactador como ferramenta, opção de menu ou tela separada. Ele permanece uma capacidade interna que somente a IA pode acionar quando necessário.

13. Não criar telas, controles ou indicadores decorativos que não tenham função real.

14. Não redesenhar a interface livremente: usar a imagem de referência para decidir a aparência e esta especificação para definir as funções.



## 8. Prioridade para a geração da estrutura



Primeiro, gerar a estrutura visual da tela principal usando a imagem de referência como guia. Respeitar especialmente a barra superior, o espaço central da conversa, o compositor inferior, o ícone luminoso, a paleta esmeralda e a chuva digital.



Manter as opções descritas neste documento associadas às suas funções, mas não espalhar todos os controles pela tela inicial. Os controles pertencem aos menus indicados.



Depois de a estrutura ser gerada, a próxima etapa de ajuste deve se concentrar nas opções e no comportamento dos menus, preservando o desenho aprovado, a navegação do chat único e todas as restrições descritas acima.



O resultado deve ser uma interface Android nativa, coerente, legível e funcional, fiel à referên

cia visual — sem abas inferiores, sem troca de provedor e sem recursos falsos.



A divisão do trabalho fica assim: a outra IA gera a estrutura visual com base na imagem e nessa descrição. Depois, quando você trouxer o resultado, a etapa de ajuste fica concentrada nas opções, nos menus e no comportamento de cada controle, sem refazer o layout aprovado à toa.  



Pode por lá no github, faz desse jeito que tá aí sem troca nada da imagem nem o local só o redirecionamento que e porque eu uso poco f6 e concerta a função de chuva que não presta e tudo lá no Github lá

This project was built with [Lovable](https://lovable.dev).

## Build with Lovable

Continue developing this project in the [Lovable editor](https://lovable.dev/projects/53c2bba5-162c-46c1-baaf-0d5fea3a6c19).

- **Ship faster**: describe what you want to build and Lovable handles the code.
- **Stay in sync**: every change made in Lovable is committed straight to this repository.
- **Full ownership**: this code is yours. Push to `main` on GitHub and your changes sync back into Lovable, ready for your next prompt.

## Development

Prefer working locally? You need Node.js and npm — [install with nvm](https://github.com/nvm-sh/nvm#installing-and-updating).

```sh
git clone <this-repository-url>
cd <repository-name>
npm i
npm run dev
```
