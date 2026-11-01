# ADAS do Song Pro (SX309): de onde vêm os alvos e o que dá para ler

Levantamento de 2026-10-31, só leitura (nada foi escrito no carro).

## O que o painel faz (libBydDataSource.so, desmontado)

- `BusinessUi1::updateAdasTargetSignal` escolhe o formato dos alvos:
  - **novo**, CAN **0x295** (`ADAS_TSR_TARGET_*_01..04`), quando `BODYWORK_POWER_LEVEL` (0x12D, campo 0x2A) = 3
    e algum subquadro 0x295 está presente (`BODYWORK_ONLINE_HAS_0x02950N`);
  - **antigo**, CAN **0x374** (`SETTING_TARGET_*_01..03`), caso contrário.
  - O carro usa o **novo**: a distância chega com decimais (no antigo seria `raw − 100`, inteiro).
- Slots: 0x295 bloco 1 → frente-esquerda, 2 → frente 1 (`F1`), 3 → frente-direita, 4 → frente 2.
  Traseiros (`leftRear`/`rightRear`) **não têm leitura de CAN** nesta versão do painel.
- Regra por slot: só atualiza se o subquadro está presente **e** `TYPE` ∈ 1..4. Caso contrário **zera só o
  tipo** e **não mexe em x/y** → o "carro travado" na última posição.
  Conversão (novo): x = raw × 0.1 − 12 m; y = raw × 0.1 − 10 m. Ignora `STATE`, `STATE_LEVEL` e velocidades.
- No carro, só o slot 2 recebe `TYPE` 1..4, e só com ACC/ICC ativo. O painel não filtra mais nada:
  o limite vem do que a câmera manda.

## Modos de depuração do painel (ClusterDebug, comandos `AutoContainer 2 i32 1000 i32 <n>`)

12/13 mostrar/ocultar ADAS · 16/17/18 projeção cheia/meia/desligar · 32/33 auto-atualização ADAS 3D ·
36 screenshot do painel · 49/50 cena ADAS normal/**SR** · 200/201 captura UDP eth0 ·
204/205/224/225 SR por Ethernet/CAN/projeção/mensagens reais · 218/219 gravação de tela.
"SR" = reconstrução de cena (vários objetos); provavelmente exige uma central de ADAS por Ethernet.

## Leitura pela multimídia

- O serviço nativo `autoservice` (`libbydautoservice.so`) **não restringe leitura por chamador**:
  `BYDAutoService::checkGetPermission` sempre permite. As permissões `BYDAUTO_*_GET` (signature) só são
  checadas no cliente Java (`BYDAuto*Device.getInstance`), que `BYDAutoManager` não usa.
- `tools/adas-probe` (dex via `app_process`, uid shell) lê pelo `BYDAutoManager.getInt` com IDs no
  **formato CAN** (o do SDK do CarSetting, ex. `0x12D0002A`), não no do framework (`0x1CD0002A`).
  O tipo de dispositivo não importa na leitura. `-10011` = nenhum dado recebido (ex.: carro desligado).
- Catálogo de 11 148 sinais (nome → ID): `tools/adas-probe/byd_signal_catalog.tsv`.
- Candidatos a "veículo à frente sem ACC":
  - **0x138** (FCW): `ADAS_FRONT_VEHICLE_STATUS` 0x1380000A, `_DISTANCE` 0x1380000B, `_SPEED` 0x13800018,
    `_ACCELERATION` 0x13800020, `ADAS_FCW_LEVEL_STATUS` 0x13800008;
  - **0x09B** (faróis): `LIGHTS_TARGET_COUNT` 0x09B00408, `LIGHTS_TARGET_1..12_INFO` (até 12 alvos).
- Caminho, se os dados existirem sem ACC: o open-headunit lê esses sinais (mesma leitura, sem permissão)
  e escreve um feed como o `now_playing.json`; o tema desenha os alvos a partir dele.

## Teste com o carro ligado (2026-11-01)

`tools/adas-probe/start_log.sh 3600` (sobreviveu ao adb desconectar, testado), 7,5 min rodando com o ACC ligado
duas vezes; o painel mostrou um veículo nas duas. Resultado: **todos** os sinais de alvo `-10011` o tempo todo
(FCW exceto o nível = 3, faróis, os 4 slots do `0x295`, o `0x374`), enquanto outros sinais leem normalmente
(`0x13300023` = 2, `0x44100008` = 0, `0x4A50502C` = 22, `0x12D0002A` = 2). Os quadros do ADAS não chegam à
multimídia: o painel os lê do seu lado. A leitura pela multimídia não serve para os alvos.

## Gravador no próprio painel (Horizon)

Como a multimídia não vê os alvos, o tema grava o que o painel recebe: `horizon/qml/AdasRecorder.qml`, desligado
até o arquivo de comando `/sdcard/.cluster/horizon/adas-rec.json` pedir:

```json
{ "on": true, "id": "volta1", "urls": ["http://127.0.0.1:8765/lo", "http://<ip-da-multimídia>:8765/wlan"] }
```

4 vezes por segundo: velocidade, marcha, luzes do ACC/AEB, texto do ACC, alvo principal, `aheadDistance`, nível,
faixas e os 7 slots (estado, x, y). A cada 5 s ele tenta duas saídas, porque nenhuma é garantida no painel:

- `PUT` de um pedaço `adas-rec-<id>-NNNN.txt` ao lado do arquivo de comando. No Qt 5.15 do PC isso só gera um
  aviso (`QML_XHR_ALLOW_FILE_WRITE`); falta ver se o painel deixa gravar nessa pasta.
- `POST` para cada URL da lista, recebido na multimídia por `AdasRecSink` (no mesmo dex do adas-probe:
  `app_process / AdasRecSink 8765 3600`, grava em `/data/local/tmp/adas-rec.log` com o caminho de cada pedido,
  o que mostra qual URL o painel alcançou).

`"on": false` ou apagar o arquivo para a gravação (o último pedaço sai na hora).

**No carro (2026-11-01, carrier `22c102f8…`): ligar o gravador derruba o painel.** Com o comando ligado (URLs
`127.0.0.1`, IP do Wi-Fi e IP do `rmnet`, todas na porta 8765), o renderizador do painel caiu a cada ~8 s
("ELKA OFF", `AutoContainerServiceNative died` no logcat), começando logo depois da primeira descarga (5 s).
Nenhum pedaço foi gravado e nada chegou ao `AdasRecSink`. Apagar o arquivo de comando parou as quedas na hora;
sem o arquivo o gravador fica parado e o tema é estável. Ainda não se sabe se quem derruba é o `PUT` ou o `POST`.
**Não ligar o gravador com o carro andando.**
