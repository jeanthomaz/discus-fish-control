# Acará Controle (protótipo 0.8.0)

App Android nativo para controlar e investigar lâmpadas OceanTech Nour, desenvolvido a partir dos comandos observados no APK 1.0.8 fornecido pelo usuário.

## Recursos

- Seletor dos cinco modelos exibidos pelo app antigo; inicia em **NOUR freshwater**.
- Busca de dispositivos BLE, conexão e confirmação inicial `xd_ble_flagpass001`.
- Filtro de busca pelo padrão OceanTech `TO-L-` seguido por quatro caracteres, igual ao filtro de nomes do APK antigo.
- Leitura do estado atual pela característica `AE10`, incluindo estado ligado/desligado, modo automático/manual e intensidades dos canais.
- Controles de energia e modo, com os comandos `app_pwm_on...` identificados no APK.
- Controles manuais Branco, Azul, Verde e Vermelho; envio explícito com `rtvall00` e valores de três dígitos.
- Leitura e gravação dos sete dias de repetição semanal pela característica `AE03`, usando `app_week_data` e `app_set_pwm_fre750`.
- Sincronização do relógio usando o formato `RTCSET` do app antigo.
- Editor de um dos oito horários automáticos, com liga/desliga, hora e níveis dos quatro canais, usando o comando `w_a_d` recuperado do editor do APK.
- Aba dedicada à programação inspirada no fluxo do app original: o gráfico mostra as mudanças ao longo do dia; um seletor em ordem cronológica escolhe um horário de cada vez para edição. O editor reúne estado ligado/desligado, hora, quatro intensidades e salvamento.
- Impede salvar dois pontos ativos no mesmo horário; horários repetidos já gravados são sinalizados para correção.
- Ícone vetorial original inspirado no acará-disco.
- Interface reorganizada em cartões, com descoberta de dispositivos separada dos controles e painéis de controle exibidos após a leitura inicial da lâmpada. A barra de status do Android respeita as áreas seguras e recortes do dispositivo.

As gravações usam tramas ASCII de 20 bytes, como no APK. Os controles de canal continuam limitados ao modo manual.

## Abrir e gerar APK

Abra esta pasta no Android Studio recente, instale Android SDK Platform 36 quando solicitado e deixe o IDE sincronizar o Gradle. Use **Build > Build APK(s)**. O projeto usa Java e não exige instalar uma linguagem separada; o Android Studio inclui a cadeia de build necessária.

Requisitos declarados: Android Gradle Plugin 8.13.2, Gradle 8.13, JDK 17 ou superior e Android SDK 36.

## Observações

- As linhas do gráfico ligam os pontos configurados como uma prévia visual; a luminária pode aplicar transições próprias entre horários.
- As predefinições de brilho (`AE12`) ainda precisam de leitura e validação.
- O app antigo recebeu resposta da lâmpada no fluxo de leitura semanal; este projeto lê `AE03` quando ela é anunciada como legível. Se não aparecer, os controles semanais ficam desabilitados.
- A edição 0.8.0 precisa ser instalada e validada na lâmpada real. A compilação Java foi conferida; o filtro, a leitura do gráfico, a prevenção de horários repetidos e os recuos de tela precisam de validação no aparelho.
