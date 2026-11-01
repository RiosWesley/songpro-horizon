# Firmware 13.1.33.2602030.1 comparado com o 2511 do carro

Estudo de 2026-11-01, só no PC. O pacote `Di3.0_13.1.33.2602030.1.zip` (4,15 GB) foi lido no lugar, sem extrair;
nada foi instalado. O carro está no 13.1.33.**2511**080.1 (série SX309), com MCU 18.3.5.**2510**230.2. Segundo o dono,
os pacotes são por chipset (13.1.33), não por modelo, e voltar de versão funciona.

## O que o pacote atualiza

- `BYDUpdatePackage/msm8953_64/UpdateFull.zip`, com o arquivo `config` = `qc+mcu+dsp+anc`: Android (Qualcomm), **MCU**,
  processador de som e cancelamento de ruído.
- **MCU:** `mcu_18_3_5.xcd` (7 MB). É cifrado em blocos de 16 bytes independentes (uma região vazia de 1,3 MB repete o
  mesmo bloco 81 738 vezes), então não há versão legível, só a família 18.3.5, a mesma do carro.
- **Comparado com o pacote 2511** (`Di3.0_13.1.33.2511080.1.zip`, mesma estrutura, série SX309):
  - os módulos de som (`dspres_e_*`) e de cancelamento de ruído (`anc/*`) são **idênticos**, byte a byte;
  - o **MCU muda**: o arquivo é outro e o hash que o acompanha (`.xcd.txt`) também (2511 `4200d64d02bd…`,
    2602 `338fef6588fe…`). Esse hash não é o MD5 do arquivo cifrado, mas é igual nos módulos que não mudaram, então
    funciona como impressão digital do conteúdo;
  - quanto o MCU muda não dá para medir: cada pacote é cifrado de um jeito, e nem a região vazia coincide entre os dois.
- **Android:** `update.zip` é um OTA A/B **comum, sem cifra** (`payload.bin` de 3,2 GB). Compilação
  `eng.build.20260204.025317`, série **SX326**, Android 10. É o que permite comparar os arquivos do painel no PC.
- Diferente do pacote do Dolphin SE (DiLink 100), em que todos os módulos vêm cifrados.

Como ler: [`horizon/tools/firmware/`](../../horizon/tools/firmware) (`locate_payload.py` acha o `payload.bin`
dentro dos zips; `extract_payload.py` remonta só as partições pedidas, direto no disco), depois
`debugfs -R "dump /system/lib64/<arquivo> <saída>" system.img`.

## O que muda no painel

- **`libbydautoservice.so` (serviço de dados do Android): idêntica.** A leitura pela multimídia não muda.
- **`libBydDataSource.so`:** novos tratadores de avisos do ADAS (`updateAdasWarningInfo` 0x51, 0x52, 0x53, 0x59, 0x61,
  0x63, 0x64, versões com voz e "modo de condução não ativo"). **O tratamento dos alvos não muda:**
  `updateAdasTargetSignal`, `updateFrontTargetOne` e `updateLeftFrontTarget` têm a mesma lógica, inclusive o filtro que
  descarta a posição de alvo com tipo fora de 1..4. A diferença é só um campo que mudou de posição na classe.
- **QML (`cluster_theme2.rcc`):** 20 de 99 arquivos mudaram (avisos, carregamento, viagem, mídia). No ADAS, só entram
  os códigos de texto do ACC 0x52..0x55 (o carro do desenho mostra o ícone de mãos no volante). Nada sobre alvos.
- **Conclusão para "vários veículos sem ACC": o 2602 não ajuda.** O limite continua sendo o que a câmera manda e o
  filtro de tipo do painel, iguais nas duas versões.

## O 2602 é a versão do DashCast

Três arquivos do 2602 têm os SHA-256 que o DashCast fixa: `XdjaContainerService.apk` `1b9eef49…`,
`libBydCluster.so` `3197abee…` (a versão em que ele validou o protocolo do tema) e `commonTranslate.rcc`
`5a0f1c39…`. No 2511 os três são outros.

## Adaptar o Horizon ao 2602

- **Os seis patches do tema encaixam sem mudança** (`cluster.qml`, `WarinLightLayout.qml`, `Center.qml`,
  `Model3D.qml`, `Adas3dContainer.qml`, `ThemeSwitch.qml`), testados com os patches do montador sobre o QML do 2602.
- **Precisam de novos SHA-256 fixados:**
  - nos montadores (Python e Kotlin): `cluster_theme1.rcc` `1096a440…`, `cluster_theme2.rcc` `42940e7c…`,
    `commonTranslate.rcc` `5a0f1c39…`, os arquivos QML originais que mudaram e a impressão digital do firmware;
  - na ferramenta do tema: `libBydCluster.so` `3197abee…`, `libBydDataSource.so` `1bda5155…`,
    `commonTranslate.rcc`, `XdjaContainerService.apk` `1b9eef49…`, `framework.jar` `f3e9c416…`. Os arquivos
    `fissiond` e `container_comm_cfg.json` não mudaram.
- O melhor é aceitar as duas versões, com um conjunto de SHA-256 para cada.

## Fora do painel

Comparação dos APKs das partições `system` e `product` (175 no 2511, 176 no 2602), pelo hash e pela versão
(`aapt dump badging`). Quase tudo foi recompilado; abaixo, só o que tem versão nova ou é novo. As propriedades do
sistema só mudam em data e identificação da compilação: o patch de segurança do Android continua em 2023-02-05.

| Área | App | 2511 → 2602 |
|---|---|---|
| Assistente de voz | BydVoice, BydVoiceDownload, BydVoiceSetting | S.11.2.2 → **S.12.4.1** |
| | SpeechHMI, SpeechAvatar, VRdownloadXF (iFlytek) | v.1031 (09/2025) → v.1032 (12/2025) |
| Ajustes do carro | CarSetting | 1.4.1.182 (11/2025) → **1.4.1.212** (01/2026) |
| | CarSettingsPlugins | 1.4.1.59 → 1.4.1.61 |
| Tela inicial | Launcher3 | 5.6.3.35 → **5.7.4.36** |
| Mídia | IntegrationMediaCenter | 1.3.0.109 → 1.3.0.120 |
| | MediaController e widget, MusicWidget | novas compilações |
| Carregamento | BydSmartTravel (Smart Charging) | 09/2025 → 12/2025 |
| Atualização | Cota (Upgrade, OTA do veículo) | 2.1.1 → 2.1.4 |
| | ProductLineOTA | 08/2025 → 12/2025 |
| Nuvem e conta | **CloudServiceFrame** (novo), AppServer, BydDiLinkAccount | — / 1.3.1.108 → 109 / 08 → 10/2025 |
| Loja e manual | OverseaAppStore, OverseaUserManual, BydBootGuide | V5.0.63 → V5.0.74 / 09 → 11/2025 / 107 → 114 |
| Visual | WallpaperHome, BydThemeStore, ScreenSaver | 154 → 162 / 10 → 12/2025 / 1.17 → 1.18 |
| Câmera e fotos | BydAutoCamera, BydAutoPhoto, BydCDR | novas compilações (09 → 11/2025) |
| Entrada e alertas | IntelligentEntry, BydCpdWarn (criança no carro) | 09 → 12/2025 / 08 → 10/2025 |
| Telefone | BluetoothPhone, BluetoothSettings, XCall, Bluetooth do Android | novas compilações |
| Projeção | Android Auto da BYD, Android Auto (Google), CarPlay | mesma versão, nova compilação |
| Painel | ClusterDebug | 2508 → 2511 |
| Outros | CanDataCollect, TrafficMonitor, MiniKaraoke, teclado | novas versões |
| Mapas de outros mercados | TMap (Coreia), Zenrin (Japão), TelenavHere | 0.15 → 1.0.3 / v3 → v4 / 9.0.141 → 9.0.152 |

Relevante para o Song Pro Horizon: o `ClusterDebug` (usado para ligar a projeção e esconder o ADAS de fábrica) e o
`XdjaContainerService` (usado pela ferramenta do tema) mudaram; os dois precisam de teste no carro antes de usar o app no 2602.
