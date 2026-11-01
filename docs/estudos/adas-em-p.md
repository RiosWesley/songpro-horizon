# O entorno do carro em P: que dado existe e como o Horizon pode usá-lo

Estudo de 2026-11-01, carro ligado e parado em P (freio de estacionamento aplicado, `GEARBOX_EPB_STATE` = 3).
Continua [adas-dados.md](adas-dados.md) e a Fase 6 de [../historia-cluster-song-pro.md](../historia-cluster-song-pro.md).
Nada foi escrito no carro: só leituras (`getInt`, `getIntArray`, `getBuffer`), `getprop`, `ip`, `logcat -d`,
`pm list permissions` e `adb pull` de duas bibliotecas e dois APKs do sistema.

Marcas usadas no texto:

- **[medido]**: lido no carro neste estudo, com o comando indicado.
- **[código]**: lido no código da BYD (desmontagem da `libBydDataSource.so`, QML de fábrica, `framework.jar`, app ClusterDebug).
- **[palpite]**: dedução que ainda precisa de confirmação.

## Resumo

1. **Nenhuma fonte acessível traz posição de veículos em P.** A câmera só preenche um alvo (slot 2 do `0x295`) e
   só com ACC/ICC ativo. O painel não filtra nada além do `TYPE`, então a falta de alvos vem da câmera. Os dois
   alvos traseiros que o painel conhece (quadro `0x291`) nunca chegam ao QML: as funções que os publicariam
   são vazias (`ret`). **[código]**
2. **Os sensores de estacionamento (PDC) só existem do lado da multimídia.** O painel não assina nenhum quadro
   de PDC e não tem propriedade para isso. A multimídia lê 9 zonas (distância e estado) pelos IDs virtuais
   `0x990000xx`. Em P todas as zonas dão `150` e estado `1` (seguro). **Falta medir em R** para saber se os
   valores mudam. **[medido + código]**
3. **O carro tem radares traseiros de canto (BSD).** Eles respondem no diagnóstico de OTA, e o `0x418` mostra
   `ADAS_HAS_BSD` = 1. Esses radares não dão posições, só avisos por lado: ponto cego (LCA), tráfego cruzado
   traseiro (RCTA), colisão traseira (RCW) e abertura de porta (DOW). **O painel já lê esses avisos**
   (`leftRearRadarStatus`, `rightRearRadarStatus`, `rearRadarStatus`, `dowWarningState`) e o 3D de fábrica já
   desenha as "ondas" de radar com eles. A multimídia também lê o `0x418`. **[medido + código]**
4. **O `aheadDistance` não é distância.** É um nível de barras (1, 3 a 6) do caminho "3R1V", que só vale para
   alguns modelos (`adasPlatform` = 1). Neste carro deve ficar sempre 0. **[código]**
5. **O modo "SR" não traz dado novo.** Os rótulos do ClusterDebug dizem que a reconstrução de cena vem de
   mensagens Ethernet, de mensagens CAN ou de uma "projeção da multimídia". O carro não tem central de ADAS por
   Ethernet, e por CAN chegariam os mesmos quadros de hoje. Na projeção, quem desenha é a própria multimídia.
   **[código + palpite]**
6. **A opção escondida `0x43F08018` não tem efeito neste painel.** A `libBydDataSource.so` não a referencia. **[código]**
7. **Melhor candidato:** um mapa de proximidade com **PDC + avisos dos radares traseiros**. Os avisos (BSD/DOW)
   o tema já pode ler direto. O PDC precisa de um feed da multimídia, mas só depois de confirmado em R.

## O que ficou rodando no carro

Um registrador **só de leitura**, desacoplado do adb, de **3 horas** (iniciado às 11:26:22, termina sozinho por
volta das 14:26). Ele grava uma linha só quando algum valor muda. Se o dono **engatar a ré com um obstáculo atrás**
(parede, outro carro) ou **rodar** nesse intervalo, o arquivo mostra se o PDC e os avisos do BSD aparecem para
a multimídia.

```sh
# ver / baixar
adb -s <ip-da-multimídia>:5555 shell tail -n 20 /data/local/tmp/park-probe.log
adb -s <ip-da-multimídia>:5555 pull /data/local/tmp/park-probe.log
# parar antes do fim (mata só o registrador)
adb -s <ip-da-multimídia>:5555 shell pkill -f ParkProbe
```

Colunas: `hora t_ms | dist. PDC ×9 | estado PDC ×9 | getBuffer(1025,2) | 0x418 ×17 | 0x235 ×7 | contexto ×6`.
A ordem dentro de cada grupo é a das listas em `horizon/tools/adas-probe/src/ParkProbe.java`. O contexto traz
a marcha (`0x34200020`), a chave do radar de ré (`0x26700038`) e quatro estados da câmera 360 (`0x4F4010xx`).
A câmera de ré deve mudar esses estados ao engatar R. **[palpite]**

## Ferramenta

`horizon/tools/adas-probe/src/ParkProbe.java` (nova; o `build.sh` coloca no mesmo dex do `AdasProbe`). Ela usa só
`BYDAutoManager.getInt/getIntArray/getBuffer`, por reflexão, como o `AdasProbe`.

| Modo | O que faz |
|---|---|
| `ParkProbe once [hexid ...]` | PDC (9 distâncias e 9 estados, por `getInt` e por `getIntArray`), `getBuffer(1025,1)` e `(1025,2)`, os 17 sinais do `0x418` e os IDs extras |
| `ParkProbe log <s>` | O mesmo a cada 250 ms (com o `0x235` e o contexto), imprimindo só mudanças |
| `ParkProbe scan <arquivo>` | Lê uma lista de IDs (um hex por linha), 2 ms entre leituras |

No carro, o dex foi enviado como `/data/local/tmp/park-probe.dex` (SHA-256 `d34eaf7f…fd86`). A lista da varredura
ficou em `/data/local/tmp/park-scan-ids.txt`.

## 1. Em P: alguma fonte traz obstáculos ou veículos?

### 1.1 Varredura geral [medido]

Foram lidos os 1 886 sinais das classes `Adas`, `Radar`, `Panorama`, `Instrument`, `Collision`, `Sensor`,
`Speed` e `Gearbox` do catálogo (sem os `_SET`):

```sh
adb shell 'CLASSPATH=/data/local/tmp/park-probe.dex app_process / ParkProbe scan /data/local/tmp/park-scan-ids.txt'
```

Resultado: 1 202 deram `-10011` (sem dado), 15 deram `-10013` e 669 tinham valor, distribuídos em 81 quadros.
Dos quadros de ADAS que o painel usa, **tinham dado em P**: `0x418` (BSD), `0x453` e `0x432` (configuração),
`0x32D` (estados do ACC/AEB), `0x316` (LKS/HMA), `0x2B4`, `0x29C`, `0x1FF`, `0x235`, `0x26F` e `0x2CA` (só as
chaves do FCTA). **Sem dado em P**: `0x295`, `0x291`, `0x374`, `0x36F` (alvos), `0x138` (FCW, exceto o nível),
`0x09B` (alvos dos faróis), `0x297` (faixas), `0x2CE` (obstáculos "CBW") e todos os quadros brutos de PDC.

### 1.2 Sensores de estacionamento (PDC)

**Sinais no catálogo** **[código]**:

| Quadro | Sinais | Em P |
|---|---|---|
| `0x365` | `ADAS_{LEFT,RIGHT}_{FRONT,FRONT_MIDDLE,REAR_MIDDLE,REAR}_OBSTACLE_DISTANCE` (8) | todos `-10011` |
| `0x1EC` | `ADAS_RADAR_ALARM_SECTION_0..15_OBSTACLE_DISTANCE` e `_SOUND_ALARM_LEVEL` (16 + 16) | todos `-10011` |
| `0x3F5` | `RADAR_REVERSE_RADAR_STATUS` | `-10011` |
| `0x2D0` | `RADAR_{FL,FR,RL,RR}_{CORNER,MIDDLE}_RADAR_ZONE_DISPLAY_FLAG` | `-10011` |
| `0x31A` | `ADAS_REVERSING_RADAR_SYS_STATE` | `-10011` |
| `0x267` | `RADAR_REVERSE_RADAR_SWITCH_STATE` | `1` |
| `0x22C` | `RADAR_PAS_SUPORRT_22C_MASSAGE` | `0` |
| virtual `0x990` | `RADAR_OBSTACLE_DISTANCE_*` `0x99000061..69`, `RADAR_PROBE_STATE_*` `0x99000071..79` | `150` / `1` |

**Como a BYD lê** **[código]** (`framework.jar`, `android.hardware.bydauto.radar.BYDAutoRadarDevice`):

- Tipo de dispositivo `1025`.
- `getAllRadarDistance()` = `getIntArray(1025, {0x99000061..0x99000069})`.
- `getAllRadarProbeStates()` = `getIntArray(1025, {0x99000071..0x99000079})`.
- `getAllRadarObstacleDistances()` = `getBuffer(1025, 2)`; `getAllRadarStatus()` = `getBuffer(1025, 1)`.
- Constantes: `RADAR_DISTANCE_MIN` 0 e `MAX` 155. Estado da sonda: 0 anormal, 1 seguro, 2 verde, 3 amarelo,
  4 vermelho.
- Áreas: 1 frente-esquerda (`0x61`), 2 frente-direita (`0x64`), 3 traseira-esquerda (`0x66`),
  4 traseira-direita (`0x67`), 5 esquerda (`0x65`), 6 direita (`0x68`), 7 frente-meio-esquerda (`0x62`),
  8 frente-meio-direita (`0x63`), 9 traseira-meio (`0x69`). O mapeamento área → ID vem da ordem do `switch` em
  `getRadarObstacleDistance` e bate com os nomes do catálogo.
- A permissão `android.permission.BYDAUTO_RADAR_GET` é **signature** (`pm list permissions -f`). Um app comum
  não usa essa classe.

**Medido em P** **[medido]**:

```
$ adb shell 'CLASSPATH=/data/local/tmp/park-probe.dex app_process / ParkProbe once'
pdc dist  (getInt)      150 150 150 150 150 150 150 150 150
pdc dist  (getIntArray) 150 150 150 150 150 150 150 150 150
pdc state (getInt)      1 1 1 1 1 1 1 1 1
buffer(1025,1) status   00000000
buffer(1025,2) obstacle 00000000
```

O registrador de 3 h ficou parado nesses valores enquanto foi conferido.

**Leitura** **[palpite]**:

- `150` é o valor de "nada medido" (perto do máximo 155; unidade provável: cm).
- Os quadros brutos não aparecem porque o PDC fica em repouso em P. Ele deve acordar em R, a baixa velocidade ou
  pelo botão.
- Não se sabe se este Song Pro tem sensores dianteiros. O estado `1` nas 9 zonas não distingue zona inexistente
  de zona livre.

**O painel não tem PDC** **[código]**: a `libBydDataSource.so` registra 762 sinais em 77 quadros. Nenhum deles é de
PDC (`0x365`, `0x1EC`, `0x3F5`, `0x2D0`, `0x31A`, `0x22C`). O `0x990` que ela assina é só estado do MCU
(`0x99000003`, `0x99000039`, `0x99000040`). O `DataItemId` (578 itens) não tem item de PDC, e o QML de fábrica não
tem tela de PDC. Portanto o PDC só chega ao Horizon por um feed da multimídia.

### 1.3 Radares traseiros de canto (BSD, RCTA, RCW, DOW)

**Eles existem** **[medido]**:

- No diagnóstico de OTA de 2026-11-01 (`_analise-local/ota/check-live-ok.txt`), `SA2H-2023_BSD_BYD` e
  `SA2H-2023_BSD_R_BYD` responderam (versão 4.00.03).
- Os que não responderam: `MRR_BYD` (radar frontal de média distância), `front CPD radar_Valeo`,
  `Mid-CPD-R_Valeo`, `DMSC`, `ECL`, `TC` e `TRCU`.
- **Correção ao item 5 do resumo anterior:** "CPD" deve ser *Child Presence Detection*, o radar de cabine que
  detecta criança esquecida, e não radar de canto. **[palpite]** O que falta mesmo na frente é o radar (MRR).
  Atrás há dois radares de canto.

Valores em P (`ParkProbe once` e varredura) **[medido]**:

| Sinal | ID | Valor |
|---|---|---|
| `ADAS_HAS_BSD` | `0x41800000` | 1 |
| `ADAS_BSD_STATE` | `0x41800008` | 1 |
| `ADAS_RIGHT_RADAR_LCA_WARNINGLEFT` / `RIGHT` | `0x41800010` / `12` | `-10011` / `-10011` |
| `ADAS_RIGHT_RADAR_RCTA_WARNINGLEFT` / `RIGHT` | `0x41800014` / `15` | 0 / 0 |
| `ADAS_RCW_ACTIVATION` | `0x41800016` | `-10011` |
| `ADAS_BSD_STATE_HAL` | `0x41800018` | 65535 |
| `ADAS_RTCA_SWITCH_STATE` | `0x4180001A` | 1 |
| `ADAS_RCW_SWITCH_STATE` | `0x4180001C` | 1 |
| `ADAS_DOW_SWITCH_STATE` | `0x4180001E` | 2 |
| `ADAS_DOW_WARN_LEFT` / `RIGHT` | `0x41800020` / `22` | 0 / 0 |
| `ADAS_RTCB_SWITCH_STATE` | `0x41800028` | 3 |
| `ADAS_{RTCA,RCW,DOW,BSD}_CONFIG` | `0x45300008/0A/0B/0C` | 1 / 1 / 1 / 1 (`RTCB_CONFIG` = 0) |
| `ADAS_FCTA_CONFIG` / `FCTB_CONFIG` | `0x43200018/19` | 1 / 1 (mas sem radar frontal de canto: `0x2CA` só traz as chaves) |

**Como o painel usa** **[código]** (`BusinessUi1::updateRearRadar`, `updateFrontRadar`, `updateAdasDowWarning`,
`updateRearRadarFunctionEquipment`):

- `leftRearRadarStatus` = (`LCA_WARNINGLEFT` ∈ 1..2 **e** `BSD_STATE_HAL` = 2) **ou**
  (`RCTA_WARNINGLEFT` = 1 **e** `RTCA_SWITCH` = 2). `rightRearRadarStatus` usa os sinais do outro lado.
- `rearRadarStatus` = `RCW_ACTIVATION` = 1 **e** `RCW_SWITCH` = 2.
- Os três só são calculados com `ADAS_HAS_BSD` = 1 e com a trava interna `+0xcf8` ligada. A trava liga quando algum
  `*_CONFIG` do `0x453` vale 1, o que acontece neste carro.
- `leftFrontRadarStatus`/`rightFrontRadarStatus` vêm do `0x2CA` (FCTA, radar frontal de canto). Neste carro devem
  ficar em 0. `fontRadarStatus` não tem quem o escreva.
- `dowWarningState` vem de `DOW_WARN_LEFT/RIGHT` com `DOW_SWITCH` e `HAS_BSD` (`updateAdasDowWarning`). O
  `adasCarDoorOpenSide` vem dos mesmos sinais, em `updateAdasWarningInfo0x9`. O `Center.qml` de fábrica abre o
  `AdasDowWarning.qml` (carro com a porta do lado marcada) sempre que `dowWarningState` ≠ 0, sem exigir
  `powerGear` = 3.
- O 3D de fábrica (`Adas3dContainer.qml` → `Adas3d.qml`) e o 2D (`Adas.qml`) já ligam as ondas
  `images…/radar/{bl,bm,br,fl,fr}` (25 quadros cada) a essas propriedades.

**Leitura** **[palpite]**:

- As chaves seguem a convenção da BYD: 1 = desligado, 2 = ligado. Então o DOW está ligado, e o RCTA e o RCW estão
  desligados no menu do carro. Com o RCTA desligado, a onda traseira não acende na ré. Isso se confere no menu de
  ADAS do carro.
- Em P, o LCA dá `-10011` e o `BSD_STATE_HAL` dá 65535 porque o ponto cego só funciona em movimento.
- **O que funciona parado:** o DOW (é para isso que ele existe), o RCTA em R e talvez o RCW (aviso de colisão
  traseira com o carro parado no semáforo).

### 1.4 Câmera 360 / AVM

- **[código]** `BYDAutoPanoramaDevice` só tem estados, modo de exibição, rotação, toque, APA e transparência.
  Não há lista de objetos. Os avisos de tráfego cruzado frontal mostrados no 360 (`0x2CA00012/13`) são do radar
  frontal de canto, que não existe aqui.
- **[medido]** `PANORAMA_OFFLINE_STATE` = 2, `OUTPUT_STATE` = 1, `WORK_STATE` = 65535, `ONLINE_STATE` = 65535,
  `BACK_LINE_CONFIG` = 1, `LVDS_STATE` = 0, `PANORAMA_HAS_ACU` = 2.
- **[medido]** No `0x1D1`, `ADAS_AVM_APA_SUPPORTED` = 65535: sem estacionamento automático. `getprop sys.byd.pano` = 0.
- **[palpite]** A "parede de radar" do 360 é desenhada com o próprio PDC.

**Descartado como fonte de dado.** Levar o **vídeo** da câmera ao painel seria outro projeto. A tela 1 já recebe a
projeção do mapa, mas o acesso de um app de terceiros à câmera não foi verificado.

### 1.5 Outros

| Fonte | Situação |
|---|---|
| `0x09B` `LIGHTS_TARGET_1..12_INFO` (até 12 alvos dos faróis adaptativos; o carro tem HMA, `ADAS_HAS2_HMA` = 1) | `-10011` na multimídia, e o painel não assina o `0x09B` **[medido + código]** |
| `0x2CE` `ADAS_CBW_*_OBSTACLE_{TYPE,X,Y,HEIGHT}` (3 obstáculos) | `-10011`. Parece ser recurso de estacionamento de outros modelos **[medido + palpite]** |
| `0x138` FCW (veículo à frente, distância, velocidade) | `-10011` em P e rodando (teste de 2026-11-01). O painel não assina o `0x138` **[medido + código]** |
| `0x235` "2in1" (`ACC_WORK_INTERFACE`, barras de distância, posição na faixa, linhas) | Presente na multimídia: `WORK_INTERFACE` = 2, o resto 0 em P. Não traz alvos **[medido]** |

## 2. As propriedades de ADAS que o QML do painel lê

Fonte: `DataItemId` do metaobject da `libBydCluster.so` (`estudos/rodada3/moc_enums.json`), nomes de propriedade
nas strings da mesma biblioteca e quem escreve cada item na `libBydDataSource.so`. Para isso as chamadas
`GetDataItemWrapper(n)` e `CanDataWrapper::GetData_*(id)` foram cruzadas por função (scripts no scratchpad).
Tudo nesta tabela é **[código]**, menos a última coluna, que junta o que foi medido.

| Propriedade (item) | Escrita por | Fonte CAN e condição | Tem dado parado / sem ACC? |
|---|---|---|---|
| `frontTargetOne*` (511–513), `frontTargetTwo*` (514–516), `leftFrontTarget*` (499–501), `rightFrontTarget*` (502–504) | `updateFrontTarget*`, `updateLeft/RightFrontTarget`, via `updateAdasTargetSignal` | `0x295` slots 2, 4, 1, 3 (novo) ou `0x374` (antigo); só com `TYPE` ∈ 1..4 | Não: só o slot 2, só com ACC/ICC (Fase 6) |
| `leftRearTarget*` (505–507), `rightRearTarget*` (508–510) | `updateLeftRearTarget`/`updateRightRearTarget` = **`ret`** | nunca | Nunca |
| `rearTarget*` (517–519) | ninguém | — | Nunca |
| `mainTargetIndex` (498) | `updateMainTarget` | estados e Y do `0x295`, do `0x291` (slots 1 e 3) e do `0x374`/`0x36F` | Só com alvo |
| `leftRearRadarStatus` (522), `rightRearRadarStatus` (523), `rearRadarStatus` (525) | `updateRearRadar` | `0x418` LCA/RCTA/RCW, com `HAS_BSD` e config `0x453` | **Sim**, em eventos (RCTA em R, RCW, LCA rodando) |
| `leftFrontRadarStatus` (520), `rightFrontRadarStatus` (521) | `updateFrontRadar` | `0x2CA` FCTA | Não (sem radar frontal de canto) |
| `fontRadarStatus` (524) | ninguém | — | Nunca |
| `dowWarningState` (577), `adasCarDoorOpenSide` (485) | `updateAdasDowWarning`, `updateAdasWarningInfo0x9` | `0x418` DOW | **Sim** (feito para o carro parado) |
| `rearRadarEquip` (544), `frontRadarEquip` (545) | `update*RadarFunctionEquipment` | configs `0x453` / `0x432` | Estático |
| `aheadDistance` (568), `carDistanceLevel` (477), `adasTextTip` (566), `leftLaneLine`/`rightLaneLine` (569/570) | `updateAdasCarInfo3R1V` | `0x235`, com `0x26F` presente, `0x26F00028` = 1, `ACC_WORK_INTERFACE` = 1, `POWER_LEVEL` = 3 e trava `+0xd1c` | Não (ver abaixo) |
| `carDistanceLevel` (477), segunda fonte | `updateCarDistance` | `0x32D` (`ADAS_TAUGAP_TIME_TO_5R1V`, `ACC_MODE`) | Só com ACC |
| `carTimeDisLevel` (476) | `updateCarRealTimeSpacing2D` | `0x32D00015` + estado do alvo 2 | Só com alvo |
| linhas, raio, largura da via, distância à faixa (468–475, 550) | `updateLaneLine`, `updateCarPosition` | `0x297`, `0x34B`, `0x316` | Só rodando com faixa |
| `adasType` (467) | `adasSelfStudy` | `0x432` / `0x43200021` (a multimídia não decodifica: `-10011`) | Estático |
| `adasPlatform` (552) | `switchAdasPlatform` | código de modelo interno: 1 só para `0x66`, `0x73`, `0x8D`, `0xB5` | Estático |
| `adasShowMode` (533) | só `clusterDebug` | — | Demonstração, nunca dado real |

### 2.1 `aheadDistance`

- **[código]** O único que escreve o item 568 é `updateAdasCarInfo3R1V`: o valor é `INSTRUMENT_2IN1_ACC_DISTANCE`
  (`0x2350000D`) quando vale 1 ou 3..6, senão 0.
- **[código]** O único QML que o lê é `adas2d/Adas3R1V.qml`, que acende barras de distância (3 → 1 barra, 4 → 2,
  1/5/6 → outras). Esse QML só carrega com `adasPlatform` = 1.
- **[código]** A função também exige a trava `+0xd1c`. Quem a liga é o `switchAdasPlatform`, só para os modelos acima.
- **[medido]** Na multimídia, `0x2350000D` = 0 e `ACC_WORK_INTERFACE` = 2 em P.
- **[palpite]** O Song Pro usa o caminho `0x295`, não o 3R1V. Então `aheadDistance` = 0 sempre, o que bate com o
  diagnóstico v8diag, que "não mostrou nada útil".

### 2.2 O modo "SR"

- **[código]** Rótulos reais do app ClusterDebug (strings UTF-8 do `classes.dex`):
  - 49: "切换普通ADAS场景" (cena ADAS normal);
  - 50: "切换SR ADAS场景" (cena ADAS SR);
  - 204: "SR调试 以太网报文驱动的ADAS" (ADAS movido por mensagens Ethernet);
  - 205: "SR调试 CAN报文驱动的ADAS" (ADAS movido por mensagens CAN);
  - 224: "SR调试 中控SR投屏ADAS" (a multimídia projeta o ADAS SR);
  - 225: "SR调试 根据实际报文显示ADAS" (ADAS conforme as mensagens reais).
  - Outros do mesmo menu: 53 (troca para o ADAS 2D), 54/55 (faixa de pedestres), 59–70 (vagas 1 a 6),
    71–73 (animação de busca de vaga).
- **[código]** A `libBydDataSource.so` não tem símbolo nem string de SR. O `clusterDebug()` dela só mexe em
  modo de teste, tipo de ADAS, modo de exibição e configurações. Os modos 204–225 são tratados em outro binário do
  host, que não temos.
- **[medido]** A multimídia não tem interface Ethernet (`ip -brief addr`: só `lo`, `wlan0`, `rmnet*`, `dummy0`), e o
  OTA não lista central de ADAS.
- **[palpite]** Neste carro, o SR por CAN usaria os mesmos quadros que já não trazem alvos, e o SR por Ethernet não
  tem fonte. O 224 é um **caminho de exibição** (a multimídia desenha, o painel mostra), não um dado. O Horizon já
  tem o seu.
- Não foi testado: os comandos do `AutoContainer` estão fora das regras.

## 3. A câmera manda alvos sem ACC?

Não há evidência de que mande.

- **[código]** O painel aceita qualquer slot do `0x295` com `TYPE` 1..4 e não olha o ACC. Se a câmera mandasse
  alvos sem ACC, eles apareceriam. Na Fase 6, só o slot 2 recebeu tipo, e só com ACC/ICC.
- **[código]** Há espaço para mais dois alvos (`0x291` no formato novo, `0x36F` no antigo, slots 1 e 3), mas só
  para escolher o `mainTargetIndex`. As posições desses alvos nunca chegam ao QML.
- **[medido]** Na multimídia, `0x295`, `0x291`, `0x374`, `0x36F`, `0x138` (menos o nível do FCW), `0x09B` e `0x2CE`
  dão `-10011`, tanto em P (hoje) quanto rodando (teste da Fase 6, para `0x295`, `0x374`, `0x138` e `0x09B`).
- **[código]** `0x43F08018` (`INSTRUMENT_DISTANCE_OF_TARGET_AHEAD_ADVANCED_SET`):
  - Nem os bytes nem os imediatos `0x8018`/`0x43F0` aparecem na `libBydDataSource.so`.
  - Do `0x43F`, o painel só assina os subquadros A a D: nome da rota, música, chamada e rádio.
  - Escrever o sinal exige permissão de assinatura e está fora das regras.
- **[palpite]** A opção deve servir a outro software de painel. Aqui ela não mudaria nada.

Só uma escuta direta do CAN da câmera, com hardware, diria se ela manda algo que nem o painel nem a multimídia
leem. Isso está fora do escopo.

## 4. Como o Horizon usaria cada fonte

| Fonte | Caminho | Por quê |
|---|---|---|
| Avisos dos radares traseiros (LCA, RCTA, RCW) | **Direto no QML**: `DataSource.leftRearRadarStatus`, `rightRearRadarStatus`, `rearRadarStatus` | O painel já calcula. O 3D de fábrica já desenha as ondas. Não precisa da multimídia |
| DOW (porta) | **Direto no QML**: `DataSource.dowWarningState`, `adasCarDoorOpenSide` | Idem. O `Center` de fábrica já abre o aviso, e o Horizon só precisa não escondê-lo (o `FactoryCentre.qml` já o considera) |
| PDC (9 zonas: distância e estado) | **Feed da multimídia**: JSON em `/sdcard/.cluster/horizon/park.json`, lido como o `now_playing.json` | O painel não recebe PDC |
| Alvos da frente (0x295) | Direto no QML, como hoje | Sem mudança: um alvo, com ACC |

**Quem escreve o feed de PDC.**

- `BYDAutoManager.getInt` funciona como `shell` (uid 2000), que é o caminho do `ParkProbe` e do daemon do DashCast
  (`AdasTargetReader.kt`).
- Para um app comum há duas barreiras **[código]**: a `BYDAutoRadarDevice` exige `BYDAUTO_RADAR_GET` (signature), e
  o construtor de `BYDAutoManager` está na lista bloqueada da API oculta (`hiddenapi 0x0002 BLOCKED` no
  `framework.jar`). O `getInt` em si não está.
- Então o caminho seguro é um processo uid 2000, iniciado pelo app via Self-ADB (já previsto no plano do app) ou à
  mão por adb. Ele lê os 18 IDs a 4–5 Hz e grava o JSON.
- **[palpite]** Ler de dentro do app com a isenção de API oculta (Android 10) pode funcionar, mas não foi testado.

## 5. O que vale construir (em ordem)

1. **Avisos laterais e traseiros no Horizon, sem a multimídia.** É o que dá para fazer já.
   - Precisa: um componente no Horizon que mostre ondas ou setores atrás e dos lados do carro com
     `leftRearRadarStatus`/`rightRearRadarStatus`/`rearRadarStatus`, e o lado da porta com `dowWarningState`. Ele
     deve funcionar também com o 3D parado ou fora de tela (P, modo mapa). Validar no carro: DOW abrindo a porta
     com uma bicicleta ou carro passando; RCTA em R depois de ligar a chave no menu; LCA numa ultrapassagem.
   - Custo: baixo (só QML). Os dados existem neste carro **[medido + código]**.
   - Limite: são avisos por lado, sem distância nem quantidade de veículos.
2. **Mapa de PDC no painel.** Depende de um teste antes.
   - Primeiro: engatar R com um obstáculo atrás enquanto o registrador de 3 h roda (ou rodar
     `ParkProbe log 120` antes) e ver se `0x99000061..69` saem de 150 e `0x99000071..79` de 1.
   - Se saírem:
     - um leitor uid 2000 que grava `park.json` com `seq`, as 9 distâncias e os 9 estados;
     - um `ParkFeed.qml` no padrão do `NowPlayingFeed.qml` (500 ms ou menos, `seq` para detectar feed parado);
     - o desenho das 9 zonas em volta do carro, só quando o feed estiver vivo e houver zona ≠ 1.
   - Custo: médio. O bipe de fábrica continua sendo o aviso de segurança, e o painel só complementa.
   - Se não saírem em R: o PDC deste carro é desenhado no host, como a câmera de ré, e a ideia cai.
3. **Não vale construir:**
   - mais veículos à frente sem ACC (a câmera não manda);
   - o `aheadDistance` (é do 3R1V);
   - o modo SR (não há fonte nova e o teste exige comandos proibidos);
   - a opção `0x43F08018` (o painel não escuta);
   - dados do 360 (só vídeo; levar a imagem ao painel seria outro projeto).

## Pendências

- Medir o PDC em R e os avisos do BSD rodando. O registrador de 3 h cobre isso se o dono usar o carro até as 14:26.
- Confirmar no menu do carro as chaves de RCTA e RCW (o palpite é que 1 = desligado).
- Saber se o carro tem sensores dianteiros. O dono sabe; os 9 estados `1` não distinguem.
- O valor `150` e a unidade (cm) são palpite até a medição em R.
