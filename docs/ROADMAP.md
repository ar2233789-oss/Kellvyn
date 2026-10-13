# Kellvyn IA — roadmap técnico

## Fase 1 — Fundação Android
- [x] Configuração Gradle para aplicativo Android Kotlin/Compose.
- [x] Manifesto com atividade inicial.
- [x] Interface inicial de conversa com tema escuro.
- [x] Contrato para conectar um mecanismo real de IA.
- [x] Base de persistência local privada para conversas.
- [ ] Compilar e instalar um APK de depuração.
- [ ] Validar a execução em dispositivo Android real.

## Fase 2 — Motor de IA
- [ ] Escolher um modelo compatível com o aparelho e medir RAM, armazenamento e velocidade.
- [ ] Integrar inferência local/offline, se houver modelo e runtime compatíveis.
- [ ] Definir integração online opcional, com configuração explícita e sem segredos embutidos no APK.
- [ ] Mostrar erros reais de modelo, falta de memória, ausência de rede e cancelamento.
- [ ] Não apresentar respostas fixas como se fossem raciocínio de IA.

## Fase 3 — Qualidade e privacidade
- [ ] Testes unitários para armazenamento e tratamento de falhas.
- [ ] Opção para apagar conversas.
- [ ] Explicação clara de onde os dados são processados.
- [ ] Verificação de permissões e tráfego de rede.
- [ ] Testes de build limpo e instalação antes de declarar uma versão pronta.

## Critério de conclusão
O projeto só será chamado de APK funcional quando a compilação tiver sido executada com sucesso e o pacote puder ser instalado. A integração de IA só será considerada funcional depois de uma chamada real ao modelo retornar uma resposta e os erros serem tratados corretamente.
