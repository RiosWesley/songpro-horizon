# Como o DashCast obtém dados de ADAS (Seal e outros DiLink 3/5)

Estudo de 2026-11-01, só leitura do código-fonte do DashCast (https://github.com/Kiroha/byd-dashcast, MIT).
Clone em disco: `_analise-local/scratch-11e578e0/dashcast-src/repo`, branch `origin/beta`, commit `66913f53`
(DashCast 1.9.82-beta, 2026-10-31), igual ao `beta` remoto de hoje (`git ls-remote`). O `release/1.9.79-beta`
só tem 2 commits a mais (identidade do daemon), nada de ADAS; `main` e `release/1.9.78-beta` não têm nada fora
do `beta`. A issue 84 (`gh issue view 84 --comments`, só leitura) tem o corpo e 2 comentários, nenhum sobre dados
de ADAS. Nada foi compilado nem executado; o carro não foi acessado.

Continua [adas-dados.md](adas-dados.md) e [adas-em-p.md](adas-em-p.md).

Marcas:

- **[código DashCast]** (arquivo:linha; o código Kotlin fica em `app/src/main/java/com/byd/dashcast/`, abreviado
  como `…/`; o resto é relativo à raiz do repositório);
- **[doc DashCast]** (documento, notas de versão ou CHANGELOG do DashCast);
- **[código BYD]**: desmontagem, feita neste estudo, da `libBydDataSource.so` **do Song Pro**
  (`repos/songpro-cluster-backup/raw/system/lib64/`, SHA-256 `46c36784…bf`), ou `getprop` já salvo do carro
  (`repos/songpro-cluster-backup/estado/getprop-sem-ids.txt`). Não vem do DashCast;
- **[palpite]**.

## Resumo

1. **O DashCast não tem um caminho de dados de ADAS que funcione.** Ele tem dois diagnósticos manuais, que o
   próprio CHANGELOG marca como **não validados no carro**: um retrato (snapshot) de 33 sinais por
   `BYDAutoManager.getInt(1038, id)` e uma leitura de logs antigos do painel. Nenhum app, tema ou HUD do DashCast
   consome alvos, distância do veículo à frente, intervalo do ACC, faixas ou BSD. **[doc DashCast]**
   (CHANGELOG.md:116-123, CHANGELOG.md:133-141, docs/releases/1.9.52-beta.md)
2. **O retrato é o mesmo caminho que já deu `-10011` no Song Pro**: mesmo serviço, mesmo tipo de dispositivo
   (1038), mesmos IDs (`0x2950N000/10/1C/30` e `0x3740N0xx`), mesmas fórmulas. Não há nada novo para copiar dali.
   **[código DashCast]** (…/proxy/AdasTargetProperties.kt:5-19, …/proxy/daemon/AdasTargetReader.kt:18-29,
   …/ui/diag/AdasTargetDiagnostics.kt:47-55)
3. **O segundo caminho é novo para nós: os logs do painel vistos pela multimídia.** O DashCast lê
   `/data/logs/host/logs/aplog*`, o `/data/logs` do lado do painel (o "host" do Fission) montado dentro da
   célula Android, e procura as linhas `CLUSTER_LOG … [callbackInt] id=499..519(...)`. Também olha, só os
   metadados, `/data/logs/host/clusterlogs` e `/data/logs/host/spilogs`. **[código DashCast + doc DashCast]**
   (…/proxy/AdasLogProbePolicy.kt:5-33, docs/ADAS_TARGET_DIAGNOSTIC.md:96-124)
4. **As mesmas montagens existem no Song Pro.** O `getprop` do carro tem `dev.mnt.blk.data.logs.host.logs`,
   `.clusterlogs`, `.spilogs` e `.tombstones`. **[código BYD]** (getprop-sem-ids.txt:56-59)
5. **Achado novo (não é do DashCast): a `libBydDataSource.so` do Song Pro escreve no log cada item que recebe do
   MCU**, antes de qualquer filtro de `TYPE`: `D CLUSTER_LOG: receive:0x<id> <tamanho> 0x.. 0x..`. Isso só acontece
   no nível de log DEBUG (`g_byd_log_level` = 0), que é o valor que o `Init` grava. Se esse log chegar a um dos
   diretórios acima, ele seria a "escuta" do `0x295` sem ACC que buscamos. **[código BYD]** (seção 5)
6. **Nada de ZeroMQ, SPI, `192.168.195.x`, portas 8889/6666/49160, IntercomSdk ou `fission_screencap` no código do
   DashCast.** O único rastro é `libzmq.so` na lista de bibliotecas que o export do sistema copia. **[código DashCast]**
   (…/ui/diag/SystemExportPolicy.kt:20)
7. **Nada volta do painel com dado de ADAS pela interface do AutoContainer.** O `receivedInfo2`/`ClusterToPadRes`
   que o DashCast usa só traz respostas de tema (`cmd=1001/sub=4`). O ouvinte genérico do `IContainerCallback`
   só registra em log o que chega. **[código DashCast]** (…/clusterlive/ClusterThemeProtocol.kt:7-28,
   …/proxy/daemon/Phase4ProcessVerbs.kt:401-470)
8. **Uma afirmação do DashCast muda a leitura do nosso `-10011`:** na pesquisa deles (que está fora do
   repositório), a "fonte 1" do MCU vai para a multimídia **e** para o painel, e os 32 IDs de alvo + LKS aparecem
   na tabela de chaves "B" do lado do painel. A falta no cache do Android **não prova** que o dado só chega ao
   painel, porque o HAL pode descartar chaves que não reconhece. **[doc DashCast]**
   (docs/ADAS_TARGET_DIAGNOSTIC.md:126-131)

## 1. Inventário: o que o DashCast lê

### 1.1 Retrato dos alvos (`Diag → ADAS targets`, protocolo 28 do daemon)

- Serviço: `android.hardware.BYDAutoManager`, construído **uma vez** com o contexto do sistema do daemon e
  chamado por reflexão como `getInt(1038, id)`. 1038 é o tipo de dispositivo de ADAS. **[código DashCast]**
  (…/proxy/daemon/AdasTargetReader.kt:17-29, …/proxy/AdasTargetProperties.kt:5)
- Quem chama: o ProxyDaemon do DashCast, uid 2000 (shell), via a transação 42 (`TXN_ADAS_TARGET_READ`).
  **[código DashCast]** (…/proxy/daemon/ProxyDaemonContract.kt:254, …/proxy/ProxyClient.kt:718-728)
- Lista fechada de 33 IDs: para cada um dos 4 grupos (`0x1000` frente-esquerda, `0x2000` frente 1, `0x3000`
  frente-direita, `0x4000` frente 2) e cada hipótese de formato, 4 IDs: disponibilidade (`+0x00`), tipo, X e Y.
  - formato novo: `0x2950N000`, tipo `+0x1C`, X `+0x10`, Y `+0x30`;
  - formato antigo: `0x3740N000`, tipo `+0x34`, X `+0x10`, Y `+0x18`;
  - mais o modo do LKS, `0x3160000E`.
  **[código DashCast]** (…/proxy/AdasTargetProperties.kt:6-19)
- São os mesmos IDs do nosso catálogo (`ADAS_TSR_TARGET_*_0N`, `BODYWORK_ONLINE_HAS_0x02950N`) e do nosso
  `AdasProbe`. **[código DashCast + catálogo local]**
- Decodificação só para desenhar: exige disponibilidade 1 e tipo 1..4; novo `x·0,1 − 12` e `y·0,1 − 10`;
  antigo `(x − 100)/10` e `y − 100`. Igual à nossa leitura do painel. **[código DashCast]**
  (…/ui/diag/AdasTargetDiagnostics.kt:47-55)
- `-10011` = "o HAL não tem essa chave no cache"; `-10013` = tipo errado. **[doc DashCast]**
  (docs/ADAS_TARGET_DIAGNOSTIC.md:53-57)
- Evidência declarada: firmware SX326 (OTA `13.1.33.2602030.1`) do Seal, `libBydDataSource.so` SHA-256
  `1bda5155…`. Os IDs "são chaves internas de cache, não IDs de CAN nem prova de que o Android os expõe".
  **[doc DashCast]** (docs/ADAS_TARGET_DIAGNOSTIC.md:21-28; …/proxy/AdasTargetProperties.kt:3)
- **Resultado no carro: nenhum registrado.** As notas da 1.9.52 dizem que o acesso, a decodificação e a
  atualização dos dados "continuam não verificados no veículo". Não há `-10011` nem valor real em nenhum
  documento, release ou incidente. **[doc DashCast]** (docs/releases/1.9.52-beta.md; CHANGELOG.md:140)

### 1.2 Logs do painel pela montagem do host (`Inspect existing OEM ADAS logs`, protocolo 30)

- Caminhos fixos: `/data/logs/host/logs`, `/data/logs/host/clusterlogs`, `/data/logs/host/spilogs`.
  **[código DashCast]** (…/proxy/AdasLogProbePolicy.kt:5)
- Origem, segundo eles, no `xdja.fission_host.rc` do SX326: o `/data/logs` do host aparece na célula 2 (Android)
  em `/data/logs/host/logs`; os diretórios de log do painel e do SPI do host aparecem em `…/clusterlogs` e
  `…/spilogs`. **[doc DashCast]** (docs/ADAS_TARGET_DIAGNOSTIC.md:96-99)
- Só o primeiro é lido. Arquivos `aplog` e `aplog.1..10` (o serviço `apk_logfs` do host faz
  `logcat -f /data/logs/aplog -r5000 -n10`, mas fica **desligado** a menos que uma propriedade da BYD o ligue;
  o DashCast não liga). Dos outros dois, só `stat` do diretório. **[código DashCast + doc DashCast]**
  (…/proxy/daemon/AdasLogReader.kt:66-75; docs/ADAS_TARGET_DIAGNOSTIC.md:99-103)
- Leitura: no máximo 6 arquivos, os últimos 256 KiB de cada, `O_RDONLY|O_NOFOLLOW|O_NONBLOCK`. **[código DashCast]**
  (…/proxy/daemon/AdasLogReader.kt:35, 96-125; …/proxy/AdasLogProbePolicy.kt:57-61)
- Filtro: só linhas `CLUSTER_LOG … [callbackInt|callbackReal] id=NNN(nome), value=…` com id 499..519, que são os
  7 grupos `leftFrontTarget`, `rightFrontTarget`, `leftRearTarget`, `rightRearTarget`, `frontTargetOne`,
  `frontTargetTwo`, `rearTarget` × `State`/`PosX`/`PosY`. **[código DashCast]** (…/proxy/AdasLogProbePolicy.kt:65-83)
- O próprio DashCast avisa: são **itens já decodificados** pelo painel (depois do filtro de `TYPE`), saem só quando
  mudam, e `State` 0 pode deixar a posição antiga. **[doc DashCast]** (docs/ADAS_TARGET_DIAGNOSTIC.md:117-124)
- **Resultado no carro: nenhum registrado** ("vehicle log visibility … remain unverified"). **[doc DashCast]**
  (CHANGELOG.md:122-123)

### 1.3 Energia e plataforma (para decidir quando esconder o painel de ADAS)

- `BYDAutoBodyworkDevice.getPowerLevel()` (dispositivo 1001, tem de dar 2) e
  `BYDAutoManager.getInt(1000, 0x12D0002A)` (`BODYWORK_POWER_LEVEL`, tem de dar 3); plataforma
  `getInt(1000, 0x40D00010)` (`BODYWORK_AUTO_TYPE`). **[código DashCast]** (…/proxy/daemon/AdasBodyworkReader.kt:7-19,
  …/cluster/FullAppProjectionAdas.kt:76) **[doc DashCast]** (docs/NATIVE_ADAS_PANE.md:120-125)
- Não é dado de ADAS. Serve para lembrar que no painel o formato `0x295` exige `POWER_LEVEL` = 3, e que na
  multimídia medimos 2 em P (adas-dados.md). **[palpite]**

### 1.4 HUD e o que volta do painel

- Estado do HUD: `0x38B0001C` (liga/desliga), `0x38B0000D` e `0x42E00008` (modo). Ouvinte
  `registerListener` nos dispositivos de Setting e Instrument, para ver o retorno do HUD. Nenhum ID de ADAS.
  **[código DashCast]** (…/proxy/daemon/CanFeedbackListener.kt:50-54, 287, 332-334; …/hud/HudDiagActivity.kt:703-707)
- O HUD do Seal desenha o próprio ADAS (contorno do carro à frente). O DashCast só liga/desliga essa camada
  (`SET_HUD_OPTION_DISPLAY` `0x4C10E030`); não lê o dado. **[código DashCast + doc DashCast]**
  (…/proxy/daemon/CanWriteVerbs.kt:130; docs/HUD_DILINK3_HANDOFF.md:59, 119)
- `IContainerCallback` (transações 1 `serviceDied`, 2 `receivedJson`, 3 `receivedInfo`, 4 `receivedInfo2`):
  o daemon registra o próprio Binder e só escreve em log o tipo e o tamanho, no máximo 1 linha a cada 5 s.
  **[código DashCast]** (…/proxy/daemon/Phase4ProcessVerbs.kt:363-390, 401-470)
- `ClusterToPadRes` tipo 8: só consulta e carga de tema (`cmd=1/sub=4` → `1001/4`; `cmd=5`). **[código DashCast]**
  (…/clusterlive/ClusterThemeProtocol.kt:7-28) **[doc DashCast]** (docs/CLUSTER_READ_ONLY_RCC_QUERY.md:20-45)
- Os temas do DashCast mantêm as camadas de ADAS de fábrica (`adasTipItemId`, `adasTextTip3R1V`,
  `adasCarDistanceItemId` = aviso do intervalo do ACC) e não leem nenhuma propriedade de ADAS do `DataSource`
  (só `speed`, `powerGear`, `naviType`, `gearMode`, `dayNightMode` etc.). **[código DashCast]**
  (tools/cluster/build_fullscreen_instruments.py:27-35; busca por `DataSource.` em `tools/` e `docs/`)

### 1.5 O que o DashCast **escreve** (só para contexto; nada disso é leitura)

- Esconder/restaurar o painel de ADAS do Seal: broadcast explícito para
  `com.byd.clusterdebug/.BroadcastReceiverCAN`, ação `com.byd.cluster.spi`, extra `wholeFrame` =
  `23,50,00,08,01,00` (esconde) ou `…,01` (restaura). É o sinal `0x23500008` (`INSTRUMENT_2IN1_ACC_WORK_INTERFACE`).
  **[código DashCast]** (…/cluster/NativeAdasWindowControl.kt:106-109)
- O receptor chama `BYDAutoTestDevice.set(TEST_SIMULATE_DOWN_SET 0xAA00020F, bytes)`, que vai até
  `AutoInterface::writeDevice`: **é escrita real no dispositivo**, não só cache. **[doc DashCast]**
  (docs/NATIVE_ADAS_PANE.md:34-42)
- Sequência completa (263 quadros, só no SX361 do Seal): `26,F0,00,28,01,06`, `43,20,00,00,01,01`,
  `43,20,00,21,01,01`, `23,50,00,08,01,00`, mais os comandos 13/12/53 do AutoContainer. **[doc DashCast]**
  (docs/NATIVE_ADAS_PANE.md:127-145)
- `0x43F08010` (`NAVI_LEAD_MSG`) e `0x43F08018` (`DISTANCE_TARGET_AHEAD`) são **campos de navegação** (ícone e
  distância da manobra seguinte) que o DashCast escreve. Não são "veículo à frente". **[código DashCast]**
  (…/proxy/daemon/CanWriteVerbs.kt:100-103; …/system/CanNavigationBatches.kt:67-69)

### 1.6 O que não existe no DashCast

Busca em todo o repositório (código, docs, ferramentas, histórico de arquivos apagados) **[código DashCast]**:

- `zmq`/`ZeroMQ`, `192.168.195`, `8889`, `6666`, `49160`, `spidev`, `IntercomSdk`, `fission_screencap`: nenhum uso
  (só `libzmq.so` na lista de cópia do export, …/ui/diag/SystemExportPolicy.kt:20, e `fission_screencap` citado
  como binário encontrado, docs/openbyd-2.5/OEM_EVIDENCE_DL3_SX361_20261007.md:105-111).
- `getIntArray`, `getBuffer`, `BYDAutoRadarDevice`, `BYDAutoAdasDevice`: nenhum uso.
- Nenhum ID de `0x138` (FCW / veículo à frente), `0x418` (BSD/RCTA/DOW), `0x297` (faixas), `0x32D` (intervalo do ACC),
  `0x09B` ou `0x291`.
- O antigo "Sniffer" do Diag era `logcat` + `dumpsys` (CHANGELOG.md, entrada 1.1.9-build181), e a antiga aba
  "ADAS" só mandava `sendInfo(1000, 12|13)` (CHANGELOG.md, entrada 1.2.0-build184). **[doc DashCast]**

## 2. Que dado, e se depende do ACC

| Dado | DashCast tem? | Depende do ACC? |
|---|---|---|
| Distância/velocidade do veículo à frente | Não | — |
| Intervalo do ACC | Não (só mantém o aviso de fábrica no tema) | — |
| Faixas | Não (só o modo do LKS `0x3160000E` no retrato) | — |
| Vários alvos | 4 slots da frente (`0x295`/`0x374`) no retrato; 7 grupos (499..519) no log | No Song Pro sim: só o slot 2 tem `TYPE`, e só com ACC/ICC (nossa Fase 6). O log de callbacks herda esse filtro |
| BSD/RCTA/DOW | Não | — |

O DashCast não diz em lugar nenhum que algo exige ACC. Ele só avisa que zero não prova "pista livre" e que
`State` 0 pode deixar a posição velha. **[doc DashCast]** (docs/ADAS_TARGET_DIAGNOSTIC.md:56-57, 122-123)

## 3. O que vem do painel para a multimídia

- **Arquivos**, não API: o `/data/logs` do host (onde roda o renderizador do painel, `qtandroidnative`) aparece na
  célula Android em `/data/logs/host/logs`. É o único canal painel → multimídia que o DashCast usa para ADAS.
  **[doc DashCast]** (docs/ADAS_TARGET_DIAGNOSTIC.md:96-99; docs/CLUSTER_READ_ONLY_RCC_QUERY.md:5-10)
- No Song Pro a montagem existe (props `dev.mnt.blk.data.logs.host.*`). **[código BYD]** (getprop-sem-ids.txt:56-59)
- O `apk_logfs` da **multimídia** está parado (`init.svc.apk_logfs` = stopped, `persist.service.apklogfs.enable`
  = 0). O do host tem outro espaço de propriedades; não sabemos o estado dele. **[código BYD + palpite]**
  (getprop-sem-ids.txt:103, 265)
- O `/data/logs` do host é também onde a `libBydDataSource.so` do Song Pro grava, pela thread de depuração
  (`BusinessUi1::debugThreadRun` chama `adbScreenCap` e `dumpSomeInfo`), a captura de tela
  (`fission_screencap -p -s cluster /data/logs/cluster_fission_screencap_%s.png`) e despejos (`top`, `ps`,
  `procrank`, `dumpsys SurfaceFlinger`, `byd_datasource_config.xml`). Então esses arquivos devem aparecer em
  `/data/logs/host/logs/` na multimídia. **[código BYD + palpite]**
- `IContainerCallback`: canal Binder de volta, mas o DashCast nunca viu ADAS nele. **[código DashCast]**

## 4. "A fonte 1 do MCU vai para os dois lados"

- O DashCast afirma, com base em `ingress/FINDINGS.txt` da investigação `REA-ADAS-Bridge-20261021` (não está no
  repositório): roteamento no kernel e despacho no HAL mostram que a fonte 1 do MCU vai à multimídia e ao painel;
  os 32 IDs de alvo + LKS estão na tabela de chaves "B" do painel; a falta no cache do Android não prova que o dado
  fica só no painel, porque o HAL pode descartar chaves desconhecidas; e "nenhum endpoint Android de alvos foi
  estabelecido". **[doc DashCast]** (docs/ADAS_TARGET_DIAGNOSTIC.md:126-131)
- Isso **corrige o fecho** do nosso adas-dados.md ("os quadros do ADAS não chegam à multimídia"). O certo é: o
  cache do `autoservice` não tem essas chaves. Se o MCU as manda e o HAL as joga fora, ou se o MCU nem as manda,
  continua em aberto. **[palpite]**
- Coerente com a desmontagem do Song Pro: o painel recebe do MCU **itens já por sinal** (ID de 32 bits + 1, 2 ou 4
  bytes), não quadros CAN crus, e informa ao MCU a lista de IDs que quer (`CanDataSyncThread::SyncIDUpdate`). Se a
  multimídia funciona igual, ela só recebe os IDs que o HAL dela pede, e o HAL dela não pede o `0x295`.
  **[código BYD + palpite]**
- O HAL da multimídia tem nível de log próprio (`sys.autohal.loglevel` = 2) e o `libbydautoservice.so` tem
  `BYDAutoService::getLogLevel`. Um nível mais verboso talvez mostrasse chaves descartadas, mas mudar a propriedade
  é escrita. **[código BYD + palpite]** (getprop-sem-ids.txt:608)

## 5. Achado no Song Pro: o painel registra cada item recebido (fora do DashCast)

Desmontagem da `libBydDataSource.so` do Song Pro (`llvm-objdump`, referências às strings por `adrp`+`add`) **[código BYD]**:

- `CanReadThreadBase::ProcessCanData(uchar*, uint)` (0x158228) lê o fluxo do SPI item a item: 4 bytes de ID
  (big-endian), 1 byte de tamanho, os dados. Tamanhos 1, 2 e 4 viram inteiro; os outros ficam em bytes.
- Para cada item que **não** pertence a um grupo, **se `g_byd_log_level` == 0**, ele monta
  `"0x%08X %d "` (ID, tamanho) + `"0x%02X "` por byte e chama
  `__android_log_print(3 = DEBUG, "CLUSTER_LOG", "receive:%s", …)` (0x15847c-0x1584fc). Só **depois** chama
  `CanDataWrapper::DispatchFunctionData` (0x158514), onde o filtro de `TYPE` acontece mais adiante.
- Para itens de grupo ele só escreve `processing_group_id:0x%08X` (DEBUG, 0x158558-0x158584) e junta os dados
  para `DispatchGroupData`, sem imprimir os bytes.
- `g_byd_log_level` fica no `.bss` e `BydDataSourceModule::Init` grava 0 nele (0xf6394). Os nomes no binário são
  `BYD_LOG_LEVEL_DEBUG/INFO/WARNING/ERROR/FATAL`; pelo uso, 0 = DEBUG.
- Quem muda o nível: `BusinessUi1::clusterDebug` caso 37 → 0 e caso 38 → 1 (tabela de saltos em 0x37edc);
  `BusinessDeveloper::msgDataUpdate` (mensagem `INTERNAL_MSG_ID_LOG_LEVEL`); existe também o item
  `DATA_ITEM_ID_LOG_LEVEL`. Não se sabe se algo o sobe na partida. Que o número do caso seja o mesmo `<n>` do
  `AutoContainer 2 i32 1000 i32 <n>` é **[palpite]** (o caso 36, que o ClusterDebug chama de captura de tela, aqui
  só manda uma mensagem de plug-in).
- Os `[callbackInt] id=%d(%s), value=%d` (o que o DashCast procura) saem em INFO quando o nível ≤ 1
  (`DataItemWrapper::SetDataItem_INT`, 0xf799c-0xf79d0).
- Os itens do `0x295` estão na tabela de sinais do painel (estruturas de 0x30 bytes a partir de 0x184bd8: ID,
  tipo, limites). Pelo campo de tipo, o X do slot 1 é de 1 byte (máx. `0xFF`), o Y do slot 2 é de 2 bytes e o
  `TYPE` vai até `0x0F`. **Não descobri** se o `0x295` chega como item avulso (aparece no `receive:`) ou como grupo (só aparece
  `processing_group_id:`).

**Consequência [palpite]:** se o host gravar o próprio logcat num desses diretórios, com o nível ainda em DEBUG, e o
`0x295` vier como item avulso, o log traz **todos** os X/Y/`TYPE` dos 4 slots que o MCU manda ao painel, com ACC
ligado ou não. É exatamente a escuta do "TYPE 0" que ninguém viu, sem hardware de CAN.

## 6. O que contradiz ou corrige o que sabíamos

1. "Os quadros do ADAS não chegam à multimídia" (adas-dados.md, teste de 2026-11-01): o `-10011` só prova que o
   **cache** do `autoservice` não tem as chaves. O DashCast afirma que o MCU manda a fonte 1 aos dois lados e que o
   HAL descarta chaves desconhecidas (seção 4). **[doc DashCast]**
2. "A câmera manda alvos sem ACC? Não há evidência": continua sem evidência, mas agora existe um jeito barato de
   ver (seções 5 e 7). **[palpite]**
3. `0x43F08018` (`INSTRUMENT_DISTANCE_OF_TARGET_AHEAD…`, adas-em-p.md item 6 e seção 3): no DashCast é distância da
   **manobra de navegação**, não do veículo à frente. Na issue 84, num Dolphin/Seagull com painel pequeno, os IDs de
   escrita de navegação são `0x0` no `BYDAutoFeatureIds$Instrument`. **[código DashCast + doc DashCast]**
4. O painel não lê CAN cru: lê do MCU, pelo SPI, itens por sinal com o mesmo ID de 32 bits do catálogo
   (`0x2950101C` etc.). Isso combina com o "IDs no formato CAN funcionam no `getInt`" do nosso adas-probe. **[código BYD]**
5. Nada contradiz o restante da lista do pedido (ZeroMQ, SPI, `BydZmqPublisher`, `[SendMsgInt]`,
   `fission_screencap`, `AutoContainer`, ClusterDebug): o DashCast simplesmente não usa nada disso para ADAS.

## 7. Caminhos para tentar no Song Pro (do melhor para o pior)

Todos são só leitura, como shell pelo adb da multimídia (`adb -s <ip-da-multimídia>:5555 shell …`).

1. **Ver se os logs do host aparecem na multimídia.**
   ```sh
   adb shell 'id; ls -la /data/logs/host/ /data/logs/host/logs /data/logs/host/clusterlogs /data/logs/host/spilogs 2>&1 | head -100'
   ```
   Sucesso: listagem com arquivos de data recente (`aplog*`, logs do painel, logs do SPI). `Permission denied`
   ou diretório vazio: o canal está fechado para o shell (SELinux ou `apk_logfs` do host desligado), e os passos 2
   e 3 caem.
2. **Procurar os itens crus do `0x295` nesses arquivos** (com ACC desligado e outro carro à frente, e depois com ACC):
   ```sh
   adb shell 'grep -a -h "CLUSTER_LOG" /data/logs/host/logs/aplog* /data/logs/host/clusterlogs/* 2>/dev/null | grep -a -E "receive:0x(295|291|374|36F|138)|processing_group_id:0x(295|291)" | tail -n 200'
   adb shell 'grep -a -h -E "callback(Int|Real)\] id=(49[9]|50[0-9]|51[0-9])\(" /data/logs/host/logs/aplog* /data/logs/host/clusterlogs/* 2>/dev/null | tail -n 100'
   ```
   Sucesso pleno: linhas `receive:0x2950N0xx …` com bytes de X/Y mudando em mais de um slot **com `TYPE` 0**
   (`0x2950N01C … 0x00`). Isso responderia se a câmera manda posições sem ACC. Sucesso parcial: só
   `processing_group_id:0x2950…` (o `0x295` vem em grupo e os bytes não são impressos) ou só `[callbackInt]`
   (a mesma visão filtrada que o painel desenha). Nada: o host não está em DEBUG/INFO ou não grava o logcat ali.
3. **Ler o diretório do SPI** (pode ter o fluxo MCU → painel):
   ```sh
   adb shell 'ls -la /data/logs/host/spilogs/; f=$(ls -t /data/logs/host/spilogs/* 2>/dev/null | head -1); [ -n "$f" ] && head -c 4096 "$f" | od -A x -t x1 | head -60'
   ```
   Sucesso: um formato com IDs de 32 bits `29 50 N0 xx` (big-endian, como no `ProcessCanData`) seguidos de
   tamanho e valor.
4. **Ver se o logcat da própria multimídia recebe o `CLUSTER_LOG`** (barato; provavelmente não):
   ```sh
   adb shell 'logcat -d -b all | grep -c CLUSTER_LOG; logcat -d -b all -s CLUSTER_LOG | tail -n 5'
   ```
   Sucesso: contagem > 0. Aí basta um `logcat -s CLUSTER_LOG:D` contínuo, sem arquivo do host.
5. **Saber se a multimídia está na rede do painel** (para o PUB ZeroMQ `127.0.0.1:49160` e os SUB de
   `192.168.195.2:8889`/`.3:6666`):
   ```sh
   adb shell 'cat /proc/net/tcp /proc/net/tcp6 | grep -i -E ":(C008|22B9|1A0A) "; ip -brief addr; ip route'
   ```
   (`C008` = 49160, `22B9` = 8889, `1A0A` = 6666; `0100007F` = 127.0.0.1, `02C3A8C0`/`03C3A8C0` =
   192.168.195.2/.3.) Sucesso: um `0100007F:C008` em estado `0A` (LISTEN) aqui, o que quer dizer que a multimídia
   compartilha a rede do painel. Mesmo assim o PUB carrega mensagens de plug-in do QML, não alvos **[palpite]**, e
   assinar o PUB já não é leitura pura. Pelo `ip -brief addr` antigo (sem `192.168.195.x`), o mais provável é não ter
   nada. **[palpite]**
6. **Retrato pelo `BYDAutoManager` como no DashCast**: não vale repetir. É o mesmo `getInt(1038, 0x2950N0xx)` que já
   deu `-10011`. Só o LKS (`0x3160000E`) seria novo, e não traz alvo.

Fora das regras de hoje (escrevem ou mandam comando), anotados só para decisão do dono: o comando de depuração
37 do painel (volta o `g_byd_log_level` para DEBUG, se a numeração bater); `setprop sys.autohal.loglevel` mais
verboso na multimídia; assinar o PUB `tcp://127.0.0.1:49160`.

## Pendências

- Confirmar no carro os passos 1 a 3 (é o que decide se a escuta do `0x295` sem ACC existe).
- Descobrir, na desmontagem, se o `0x295` é tratado como grupo (mapa em `CanReadThreadBase + 0x100`) ou como
  item avulso.
- Ver se algo sobe o `g_byd_log_level` na partida (QML de fábrica mandando `INTERNAL_MSG_ID_LOG_LEVEL` ou
  `DATA_ITEM_ID_LOG_LEVEL`, ou a configuração em XML).
