# Memória persistente do operador — Studio de Produção

Ler esta memória antes de trabalhar. Ela preserva as regras do usuário entre sessões.

## Regras permanentes da Kellvyn

- O app de destino é Kellvyn IA, Android nativo em Kotlin/Jetpack Compose. Não usar WebView no APK final.
- A interface original aprovada do Lovable é a referência visual. Preservar composição, cores, ícones, efeitos, animações e comportamento já aprovados. Não redesenhar nem adicionar controles por iniciativa própria.
- O Studio é a oficina interna que o assistente operador utiliza. Não converter suas ferramentas em abas ou botões adicionais dentro da interface principal da Kellvyn IA.
- A oficina contém Estúdio Visual, Biblioteca de Recursos, Engenharia e QA. A Biblioteca possui 37 paletas/296 cores, 24 presets de materiais, 14 ambientes de luz/sombra, 9 primitivas extras, 12 curvas de animação e protótipos de partículas para chuva, neve, neblina e relâmpagos. Reutilizar essas ferramentas e a prévia climática antes de recriar recursos. A prévia climática é Canvas 2D; não é efeito Android nativo nem cópia confirmada da chuva do Lovable.
- Fazer uma mudança relacionada à tarefa atual por vez: inspecionar → alterar minimamente → comparar diff → testar → registrar evidências e pendências.
- A prévia web é ferramenta de inspeção e modelagem; ela não substitui o APK Android nativo.
- A compilação de aplicativos que a Kellvyn cria deve ocorrer no próprio aparelho, conforme a especificação do produto. O laboratório virtual do Studio testa um APK já compilado e nunca compila APK.
- Não entregar ZIP como substituto do APK. Não declarar função concluída só porque há código, botão ou documentação.
- Nunca inventar logs, métricas, progresso, sucesso de instalação ou aprovação. Quando o teste físico não aconteceu, declarar isso explicitamente.
- Reutilizar bibliotecas e ferramentas compatíveis com suas licenças. Não copiar ferramentas proprietárias sem autorização.
- Não colocar tokens, chaves ou credenciais na memória, no código, em capturas ou em artefatos de teste.

## Perfil de aparelho alvo

O arquivo-fonte do perfil está em studio/memory/POCO_F6_PROFILE.json. Configuração informada pelo usuário: Poco F6, HyperOS/Android 15, 8 GB de RAM e 512 GB de armazenamento.

A referência oficial da POCO informa painel de 6,67 polegadas, 2712 × 1220 pixels, 446 PPI e taxa de atualização de até 120 Hz: https://www.po.co/global/product/poco-f6/specs/

## Significado dos resultados

- **Prévia visual:** mostra a representação no navegador. Não prova comportamento Android.
- **Teste unitário/estático:** comprova apenas as regras executadas.
- **Emulador virtual:** exercita o APK numa instância Android AOSP. Não reproduz HyperOS, Snapdragon/Adreno físicos, toque real, temperatura, consumo de bateria ou throttling do Poco F6.
- **Teste físico:** só pode ser declarado depois de executar no Poco F6 real e guardar a evidência correspondente.
- **Fidelidade visual:** não prometer 100% com base somente em uma resolução configurada ou screenshot. Comparar capturas em condições documentadas, interações e animações; registrar o que não foi possível simular.

## Rotina de trabalho visual

1. Abrir a referência original e a implementação atual.
2. Registrar a resolução, a escala e o contexto das capturas.
3. Utilizar a Biblioteca de Recursos: pesquisar paletas por família/cor, testar materiais e iluminação na cena, escolher a curva de animação e prototipar efeitos climáticos com presets e controles antes de preparar parâmetros para o adaptador nativo.
4. Comparar antes/depois e fazer uma alteração por vez.
5. Validar na prévia ou no emulador conforme a área testada.
6. Separar evidências de emulador das evidências do aparelho físico.
7. Não alterar a estrutura aprovada da interface só para fazer a prévia parecer correta.

## Laboratório virtual

Consultar studio/device-lab/README.md. O workflow .github/workflows/studio-poco-f6-lab.yml inicializa um emulador Android 15, instala um APK de release já existente e recolhe screenshot, hierarquia de UI, logs e uso de memória. Não compila o APK.

A página do resultado identifica o APK e seu SHA-256; usar a evidência correspondente à execução, nunca assumir que uma captura antiga valida um novo commit.
