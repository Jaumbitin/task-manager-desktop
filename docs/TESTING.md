# Testes do frontend

## Automáticos

- ApiClientTest: contrato HTTP, Bearer, JSON, datas, métodos CRUD, respostas inválidas.
- FxmlSmokeTest: carrega ambas as telas e CSS; verifica tarefas, contadores, busca e filtros.
- LiveApiTest: opt-in contra a API real, habilitado com -Dtask.integration=true.

Os testes locais usam um servidor HTTP efêmero com dados sintéticos.
O teste visual gera work/dashboard-preview.png. Ele exige uma sessão gráfica;
no Linux headless, execute com Xvfb, como na pipeline.

## Fluxo manual

Com backend ativo, crie uma conta, entre, crie/edite/conclua/reabra/exclua tarefas.
Confira filtros, busca, mensagem de lista vazia e cancelamento da exclusão.
Teste limites de tamanho, senha incorreta, API desligada e sessão expirada.
Navegue pelo teclado e redimensione a janela.

O teste real cria uma conta aleatória e limpa a tarefa; a conta permanece no banco.
Não forneça credenciais de uma conta importante para testes exploratórios.
