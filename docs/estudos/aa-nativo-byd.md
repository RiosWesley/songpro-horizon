# Android Auto nativo da BYD (`com.byd.androidauto`) e o mapa de cluster

Estudo somente leitura. Nada foi enviado ao cluster e nada foi instalado, habilitado ou desabilitado no carro.
Evidência: APK decompilado com jadx (934 classes, 3 erros sem relevância), `libgalreceiver_jni.so` puxada de `/system/lib64`, e `dumpsys`/`getprop` na multimídia (firmware 6125f_1for2).

## Resposta curta

O AA nativo **não tem** nenhum caminho pronto para o cluster, e **não dá para estendê-lo sem root**.
- O app usa o GAL receiver do Google (Java mais `.so` nativa). O código tem o tipo `DISPLAY_TYPE_CLUSTER` e até um `addDisplay()` para múltiplos displays, mas **nada chama `addDisplay`**. Só existe o sink de vídeo MAIN (display 0).
- O app é `sharedUserId=android.uid.system`, assinado com a chave de plataforma, e não exporta nenhum serviço, receiver útil ou provider.
- A recomendação é a opção (e) da seção 4: manter o Headunit Revived como dono da conexão e fazê-lo imitar a integração do AA nativo.

## 1. Stack e onde fica o ServiceDiscoveryResponse

O app é um receiver baseado no **GAL do Google**, com carcaça BYD. Não é aasdk nem uma biblioteca própria.

| Camada | Evidência |
|---|---|
| Java `com.google.android.projection.{protocol,sink,proto}` | Classes `GalReceiver`, `VideoSink`, `AudioSink`, `InputSource`, `SensorSource`, `NavigationStatus`, etc. As classes protobuf geradas ficam em `proto/Protos.java`. |
| Nativo `libgalreceiver_jni.so` | Fica em `/system/lib64/` (10 MB, stripped, arm64, NDK r21). O APK só tem o symlink `lib/arm64/libgalreceiver_jni.so`. Símbolos C++ `gal::MediaSinkService::set_display_type`, `VideoSink::setDisplayIdAndType`, `gal::ServiceDiscoveryResponse::add_services`, `NavigationStatusEndpoint`, etc. |
| Código BYD | `AAPService` (4376 linhas, orquestra tudo), `AAWService` (AA sem fio via RFCOMM e Wi-Fi), `AAPUsbReceiver`, `ModeKeyReceiver`, `IntentControllerBroadcastReceiver`, `AudioFocusManager`, `NavigationFocusManager`, `com.byd.mdconmanager.IMdRemoteService` (AIDL do gerenciador de dispositivos). |

O `ServiceDiscoveryResponse` é montado **dentro da `.so`**. O Java só chama `GalReceiver.registerCarService(id, service)`, que cria o endpoint nativo (`service.create(id, nativeGalReceiver)`).
Cada `registerCarService` vira um `service` no discovery. Os campos de cada `MediaSinkService`, inclusive `display_id` e `display_type`, são ajustados por `VideoSink.setDisplayIdAndType` (nativo).

Os serviços são registrados em `GalIntegration.init()` (`sink/GalIntegration.java:81-135`):

| ID | Serviço |
|---|---|
| 15 e 16 | Display MAIN (displayId 0): `VideoSink` (15) + `InputSource` (16). O id vem de `nextDisplayServiceId`, que começa em 15 |
| 1 | Rádio (só se `radio != null`; o AAPService passa `null`) |
| 2 | Wi-Fi projection |
| 3 | Sensores |
| 4 e 5 | Áudio mídia e guidance (TTS) |
| 7 | Microfone (audio source) |
| 8 | Bluetooth |
| 10, 11 e 12 | Playback status, media browse e phone status |
| 17 e 18 | `audioSinks[2]` e `[3]` (variantes sem fio) |
| 9 | `NavigationStatus` (**criado mas nunca registrado**, ver seção 2) |

A resolução anunciada vem de `AAPService.equipVideoConfig()` (`AAPService.java:4056`). Anuncia 800x480, 1280x720 e 1920x1080 (60 fps), com margens calculadas sobre a tela emulada.

## 2. O que existe para cluster

O que **existe**:
- `Protos.DisplayType` com `MAIN=0`, `CLUSTER=1` e `AUXILIARY=2` (`proto/Protos.java:37000`).
- `GalIntegration.addDisplay(DisplayType, VideoSink, InputSource)` e a classe interna `Display`, que faz `videoSink.setDisplayIdAndType(displayId, displayType)` (`GalIntegration.java:179-221`). Esse é o gancho do GAL de referência para multi-display.
- `GalIntegration.MAIN_DISPLAY_ID = 0` e um `currentDisplayId` que começa em 1, ou seja, o próximo display adicionado seria o 1.
- A `.so` tem o suporte nativo: `MediaSinkService::set_display_type`, `VideoSink::setDisplayIdAndType`, `InstrumentClusterInput` (UP/DOWN/ENTER/BACK...) e `NavigationStatusEndpoint`.

O que **não existe**:
- `grep addDisplay` só acha a definição. Em `AAPService` só aparece `videoSink.setDisplayIdAndType(0, DISPLAY_TYPE_MAIN)` (`AAPService.java:1711`).
- Não há UI, `SurfaceView`, `MediaCodec` nem display alvo para um segundo vídeo. `ProjectionRenderer` e `AAPVideoActivity` tratam um único `VideoSink`.
- `GalIntegration.init()` faz `new NavigationStatus(listener, 500, InstrumentClusterType.ENUM, 480, 480, 16)` (`:100`) mas **não faz `registerCarService(9, navigationStatus)`**. Logo o telefone nunca recebe o serviço de turn-by-turn (`NavigationStatusService`) deste carro.
- `InstrumentClusterListener` (`sink/InstrumentClusterListener.java`) é só um logger de strings: monta um texto de status e manda por `Handler`. Não escreve em nenhum cluster, só é consumido em modo debug. `onNavigationState` e `onNavigationCurrentPosition` apenas logam "Not supported operations".
- Nenhum `INSTRUMENT_CLUSTER_*` é usado pelo app fora da `Protos` gerada.
- O app **não toca** o cluster BYD (sem `AutoContainer`, sem `com.byd.cluster.spi`). `grep cluster` só acha os arquivos acima.

Conclusão: o código vem do GAL de referência, que sabe fazer cluster, mas a BYD só ligou o display MAIN.

## 3. Pontos de extensão

Tudo abaixo vem de `dumpsys package com.byd.androidauto` e do manifest (aapt2).

**Identidade**
- `sharedUserId=android.uid.system` (uid 1000), flags `SYSTEM PERSISTENT`, contexto SELinux `u:r:system_app:s0`, targetSdk 29.
- Está em `/system/app/AndroidAutoNew/`. Não dá para atualizar com APK próprio: a assinatura precisa ser a da plataforma.
- Tem várias permissões de sistema (`BYDAUTO_*`, `INSTALL_PACKAGES`, `MONITOR_INPUT`, `SYSTEM_ALERT_WINDOW`, `BLUETOOTH_PRIVILEGED`...).

**Componentes** (todos `exported=false` ou sem exportação efetiva)

| Componente | Exposição |
|---|---|
| `AAPService` (action `com.google.projection.action.AAPService`) | `exported=false` |
| `AAWService` | `exported=false` |
| `AAPBootReceiver`, `AAPUsbReceiver` | `exported=false` |
| `AAPActivity` (launcher), `AAPVideoActivity`, `DialogActivity` | Sem intent-filter exceto o LAUNCHER do `AAPActivity`; o vídeo só abre por dentro |
| `AndroidAutoProvider` (`com.byd.androidauto.connection`, tabelas `icon` e `asrstate`) | Sem `exported`, targetSdk 29 então não exportado |
| `DeviceInfoProvider` (`com.byd.androidauto.devices`) | Idem, protegido por `com.androidauto.provider.permission.deviceInfo` (normal) |
| `ModeKeyReceiver` (`byd.intent.action.MEDIA_MODE`) | Único receiver exportado de fato (tem intent-filter). Só abre `AAPVideoActivity` e responde por `IMediaKeyCallback`. Não ajuda com cluster. |

**Receivers dinâmicos** (`AAPService.java:1183-1222`): `byd.intent.action.NAVIGATION_STATE_CHANGED`, `byd.intent.action.AUDIO_FOCUS_CHANGE`, `byd.intent.action.DIS_ANDROID_AUTO`, `byd.intent.action.KEEP_ANDROID_AUTO`, `android.broadcast.voice.request.*`, `byd.intent.action.CARPLAY_DEVICE_CONNECT_BT`, entre outros.
Nenhum aceita ordem de "abrir sink de cluster". Só controlam foco, desconexão e voz.

**Flags e configuração**
- Não há flag por modelo ou região que ligue cluster. As propriedades lidas são `sys.androidauto.*`, `sys.carplay.*`, `sys.byd.pano_start`, `ro.build.region` (hoje `ROW`) e `persist.sys.androidauto.voiceType`.
- As preferências (`Settings.java`) são `SCREEN_WIDTH/HEIGHT/DPI`, `framerate_restrict_to_30`, `debug_mode`, `listen_tcp_port`, `touch_screen_enabled` etc. Nenhuma trata de cluster.
- `assets/carInfo.json` e `countrycode.json` descrevem o carro e os países permitidos, sem campo de cluster.
- Não existe arquivo de configuração em `/system` ou `/vendor` que mexa nisso.
- A versão anunciada ao telefone é `AAPConstant.getModel()` = `13.1.29.2305150.1` (build de maio de 2023 do GAL).

**Sistema**
- SELinux está `Enforcing`, build `user`/`release-keys`, `ro.secure=1`, `ro.debuggable=0`.
- `ro.boot.verifiedbootstate=orange` e `ro.boot.flash.locked=0`: **o bootloader está destravado**. Isso muda a viabilidade da opção (c), ver abaixo.
- Display 1 (`fission_bg_xdjaVirtualSurface`, 1280x480, `FLAG_PRESENTATION`) é criado pelo `com.xdja.containerservice` (uid 1000) e continua acessível ao nosso app, como já provado.
- O pacote está com `enabled=3` (disabled-user) e `stopped=true`.

## 4. Opções, de menor para maior esforço e risco

### (a) Flag ou configuração que liga o cluster no app nativo
**Inviável.** Não existe flag. O Java nunca chama `addDisplay` e o `NavigationStatus` nem é registrado (seção 2). Não há o que ligar.
Teste que confirma: monitorar o `ServiceDiscoveryResponse` que o telefone recebe do carro (por exemplo via logcat do `com.google.android.projection.gearhead` no telefone, ou captura Wi-Fi) e ver que só aparece um `MediaSinkService` de vídeo, com `display_type = MAIN`. Não foi feito aqui, porque o app está desabilitado e não deve ser religado.

### (b) App nosso que se integra por API exportada
**Inviável para o objetivo.** Não há serviço, AIDL, provider nem receiver exportado que entregue o stream de vídeo do telefone ou injete um sink. `IMdRemoteService` é o AIDL do `com.byd.mdconmanager` (gerencia dispositivos BT/AA), não do AAP.
O que dá para obter por fora, sem root, é só estado:
- Propriedades de leitura livre: `sys.androidauto.connect`, `sys.androidauto.connecting`, `sys.androidauto.foreground`.
- Broadcasts como `byd.intent.action.FOREGROUND_STATE_CHANGED`.

Esse estado serve para um app nosso saber quando o AA nativo conectou, mas o vídeo de cluster não passa por ali.

### (c) Xposed/LSPosed ou patch do app nativo
**Tecnicamente seria o jeito certo, mas exige modificar o sistema.**
- O patch seria pequeno em Java: no `GalIntegration.init`, criar um segundo `VideoSink` e `InputSource`, chamar `addDisplay(DISPLAY_TYPE_CLUSTER, ...)`, registrar com `registerCarService`; mais um decoder `MediaCodec` renderizando na display 1 (equivalente ao `ClusterMapView` do nosso fork). Também registrar o `NavigationStatus` (id 9).
- **O que bloqueia**: o app é platform-signed (uid 1000) e vive em `/system` (ext4 `ro`, atrás de `dm-6`/`dm-8`, ou seja, dm-verity), sem root. Um APK nosso não pode substituí-lo (assinatura diferente), e Frida/Xposed não conseguem anexar a um processo `system_app` a partir do `shell` (uid 2000).
- **A brecha**: o bootloader está destravado (`orange`). Em tese dá para fazer fastboot de um `boot.img` com Magisk e instalar LSPosed/Zygisk, ou regravar o `system`. Mas:
  - o risco é alto (brick do head unit, perda de garantia, OTA e `carplayserv`/fission podem recusar), e o `fission_service`/`1for2` complica o fluxo de boot;
  - o carro é de uso diário, e a imagem 1for2 tem duas partes (IVI e cluster), não sabemos qual partição o fastboot expõe nesta plataforma;
  - não verifiquei nada disso no carro (somente leitura), então fica como hipótese.
- Teste mínimo, antes de qualquer root: confirmar se a conexão fastboot do head unit existe e se é só a partição `boot` que muda. Não fazer sem a decisão explícita do usuário.

### (d) Proxy entre o celular e o app nativo
**Não vale a pena.** O GAL usa TLS com certificados do próprio `LocalModeCallbacks` (root, client e private key) e é o telefone quem decide pelo discovery. Um proxy teria de ser um receiver completo para o telefone, anunciando o sink de cluster, e reencapsular tudo para o app nativo como se fosse outro telefone (aasdk/protobuf dos dois lados, com o app nativo esperando um source de verdade). Isso é essencialmente reimplementar o que o Headunit Revived já faz, com mais partes móveis e sem benefício.

### (e) Recomendada: Headunit Revived como dono da conexão, imitando o que o nativo faz
Já sabemos que o stream de cluster funciona com o nosso fork. A pergunta real do usuário é "não depender do Headunit Revived". Sem root, isso só se resolve trocando o dono da conexão, não estendendo o nativo.
O que o AA nativo oferece (e que o nosso fork precisa replicar para ser uma boa substituição):
- **Gatilho**: o nativo sobe no boot (`AAPBootReceiver`) e quando o telefone conecta por USB (`AAPUsbReceiver`) ou BT/Wi-Fi (`AAWService`).
- **Estado do sistema**: publica `sys.androidauto.connect`/`connecting`/`foreground`. Nenhum serviço BYD que verificamos exige isso para o cluster, mas o launcher e a barra de modos (`byd.intent.action.MEDIA_MODE`) dependem.
- **Áudio e foco**: `AudioFocusManager` e `NavigationFocusManager` respeitam `sys.byd.pano_start`, `ecall`, chamadas BT.

## 5. Recomendação e primeiros passos

**Recomendação**: seguir com (e). Manter o `com.byd.androidauto` desabilitado, usar o Headunit Revived (ou um fork mais enxuto) como único receiver, e tratar o cluster que já funciona como parte do app. (c) só entra se o usuário aceitar mexer na partição do sistema, e hoje não há evidência de que seja seguro.

Primeiros passos:
1. **Auto-start**: receiver de `BOOT_COMPLETED` e `USB_DEVICE_ATTACHED` no nosso app (sem sinalizar `exported` indevido), levantando o serviço de conexão sem abrir a UI, para ficar tão transparente quanto o nativo.
2. **Mimetizar o estado**: setar as props `sys.androidauto.*` não é possível sem uid system. Em vez disso, escutar `byd.intent.action.MEDIA_MODE` e `FOREGROUND_STATE_CHANGED` para nos comportar como o nativo ao apertar o botão de modo.
3. **Registrar o `NavigationStatusService` (turn-by-turn)** no nosso receiver. O nativo nem faz isso, mas é o que dá setinha e distância no cluster, independente do mapa em tela cheia.
4. **Medir antes de decidir (c)**: com o telefone conectado ao HUR, capturar o `ServiceDiscoveryRequest` que o telefone manda e confirmar os campos de cluster. Já é o que o fork faz.
5. **Opcional, só com autorização**: investigar a viabilidade de root pelo bootloader `orange` em um aparelho de teste, não no carro.

## Resumo em 10 linhas

1. O AA nativo (`com.byd.androidauto`) é o GAL receiver do Google (Java + `/system/lib64/libgalreceiver_jni.so`), com `AAPService`/`AAWService` da BYD por cima. Não é aasdk.
2. O `ServiceDiscoveryResponse` é montado na `.so`; o Java só faz `registerCarService` em `GalIntegration.init()`.
3. O código conhece `DISPLAY_TYPE_CLUSTER` e tem `GalIntegration.addDisplay()`, mas nada o chama: só existe o sink MAIN (display 0).
4. O `NavigationStatus` é construído e nunca registrado; `InstrumentClusterListener` é só um logger. O app nunca toca o cluster BYD.
5. Não há flag, config em `/system`/`/vendor` ou propriedade que ligue cluster: opção (a) inviável.
6. O app é uid 1000 (`sharedUserId=android.uid.system`), platform-signed, sem componente exportado útil: opção (b) só dá estado, não vídeo.
7. SELinux Enforcing, `user`/`release-keys`; nem Xposed nem Frida entram em `system_app` pelo shell. Proxy (d) vira reimplementar o HUR.
8. Achado novo: `ro.boot.verifiedbootstate=orange` e `flash.locked=0` (bootloader destravado). Root/patch (c) é tecnicamente possível, mas arriscado e não verificado.
9. Recomendação: opção (e), manter o nativo desabilitado e deixar o HUR dono da conexão (auto-start no boot/USB, nav status, reagir a `MEDIA_MODE`).
10. (c) só com decisão explícita do usuário e, de preferência, testado fora do carro.
