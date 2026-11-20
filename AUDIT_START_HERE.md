# Ponto de entrada obrigatório — auditoria da Kellvyn

Antes de responder, pesquisar ou alterar qualquer parte deste projeto:

1. Leia `docs/WORK_AUDIT.md` na branch `brain/from-scratch-foundation`.
2. Leia `studio/memory/OPERATOR_MEMORY.md` na branch `studio-production-suite`.
3. Confira o HEAD atual, os commits recentes e os resultados dos workflows. Não use a branch `main` como substituta dessas fontes.
4. Respeite o campo **Fase atual** da auditoria. Enquanto estiver em `AUDIT_ONLY`, não altere código, arquitetura, treinamento, exportação ou integração Android.
5. Registre decisões e marcos reais na auditoria; nunca registre hipótese como decisão do usuário, nem teste não executado como aprovado.

## Fontes oficiais

- [Auditoria operacional](https://github.com/ar2233789-oss/Kellvyn/blob/brain/from-scratch-foundation/docs/WORK_AUDIT.md)
- [Memória do operador](https://github.com/ar2233789-oss/Kellvyn/blob/studio-production-suite/studio/memory/OPERATOR_MEMORY.md)
- [Verificações automáticas da auditoria](https://github.com/ar2233789-oss/Kellvyn/actions/workflows/audit-gate.yml)

## Limite importante

Este arquivo e o workflow fazem parte do repositório. Eles não conseguem iniciar uma ação dentro de uma conversa do ChatGPT nem obrigar a plataforma a abrir o GitHub automaticamente. Em cada nova conversa, o assistente ainda precisa executar a consulta às duas fontes antes de trabalhar. Não afirmar que esse carregamento ocorreu sem realmente consultar os arquivos.

## Como liberar uma etapa técnica no futuro

Somente após autorização explícita do usuário:
- atualizar a auditoria para `IMPLEMENTATION_AUTHORIZED`;
- registrar a tarefa exata autorizada, os limites e os critérios de aceitação;
- incluir a atualização da auditoria junto às alterações técnicas;
- confirmar que o workflow `Audit Gate` passou.

Sem essa autorização explícita, a fase permanece `AUDIT_ONLY`.
