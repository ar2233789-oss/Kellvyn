# Oficina de paridade visual — Kellvyn

Esta é uma bancada de engenharia visual separada da tela de produção. O objetivo é receber uma cópia **congelada** da interface original do Lovable e usá-la como referência mensurável para a implementação Android nativa.

## Estado da referência oficial

O projeto indicado como fonte oficial tem ID `53c2bba5-162c-46c1-baaf-0d5fea3a6c19`. A conexão Lovable desta sessão devolveu `project_not_found` para esse ID. Portanto, o código e a captura da referência **ainda não foram importados**. Nenhum projeto de nome parecido foi usado como substituto.

A situação está registrada em [reference/source-lock.json](reference/source-lock.json). Enquanto estiver pendente, os testes estruturais do Android podem rodar, mas nenhum resultado pode ser descrito como paridade visual confirmada.

## Ferramentas preparadas

- `scripts/audit_native_layout.py`: verifica se os elementos nativos críticos do chat, do indicador de pensamento e da animação continuam presentes; no modo estrito exige a fonte exata do Lovable.
- `scripts/compare_screenshots.py`: compara duas capturas no mesmo tamanho, calcula diferença média, percentual de pixels alterados e SSIM quando disponível, e cria sobreposição/mapa de diferença.
- `rain_motion.py` e `tests/test_rain_motion.py`: espelham a fórmula de posicionamento da chuva encontrada no código nativo e verificam a continuidade do movimento entre quadros.
- `model/rain_calibration.scad`: modelo geométrico 3D para inspecionar espaçamento, comprimento e inclinação dos traços.
- `scripts/render_rain_animation.sh`: gera uma animação diagnóstica com FFmpeg a partir da fórmula usada no Kotlin.
- `diagrams/parity-pipeline.dot`: diagrama do caminho entre fonte congelada, render nativo e comparação visual.

## Comandos

~~~bash
python workshop/scripts/audit_native_layout.py --repo-root .
python -m unittest discover -s workshop/tests -v
python workshop/scripts/compare_screenshots.py --reference ref.png --candidate android.png --output-dir /tmp/kellvyn-diff
./workshop/scripts/render_rain_animation.sh /tmp/kellvyn-rain
openscad -o /tmp/rain-calibration.stl workshop/model/rain_calibration.scad
dot -Tsvg workshop/diagrams/parity-pipeline.dot -o /tmp/parity-pipeline.svg
~~~

A comparação de screenshots só é válida quando as duas capturas representam o mesmo estado da tela, a mesma orientação, o mesmo tamanho e o mesmo conteúdo. A ferramenta não redimensiona silenciosamente as imagens.

## Regra para aceitar a referência

Ao recuperar acesso ao projeto de ID `53c2…`, copiar os arquivos visuais para `reference/lovable/` e guardar uma captura-base sem alterar o projeto Lovable. Registrar no source-lock o commit real do Lovable e os hashes SHA-256 dos arquivos e da captura. Só então alterar o estado para `imported` e executar a auditoria em modo estrito.

Não redesenhar botões, hierarquia, espaçamentos ou animações para facilitar a comparação. A bancada não altera o motor de IA e não altera MainActivity.kt; primeiro estabelece a referência verificável, depois orienta alterações isoladas na implementação nativa.