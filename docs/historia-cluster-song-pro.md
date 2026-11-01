# Painel (cluster) do BYD Song Pro 8,8": a história das descobertas

Registro de um projeto de engenharia reversa do painel de instrumentos de um **BYD Song Pro com DiLink 3.0,
firmware SX309 e painel de 8,8"**. Começa em "onde estão os arquivos do cluster" e termina no estado atual: um
tema próprio (Horizon) rodando no painel real, com o mapa do Android Auto num quadro central e o ADAS 3D da
própria BYD religado.

> **Aviso.** Nada aqui é oficial nem tem apoio da BYD. Tudo foi descoberto por leitura do sistema, desmontagem
> de bibliotecas e testes num único carro. Mexer no cluster pode deixar o painel sem velocidade e sem alertas.
> Faça qualquer teste com o carro **parado, em P, com freio de mão**, com o adb funcionando e o caminho de
> recuperação já ensaiado. As ferramentas deste repositório **recusam** qualquer firmware cujos arquivos não
> batam byte a byte com o SX309 descrito aqui. Uma atualização da BYD pode invalidar tudo. Use por sua conta e risco.

Todo o trabalho descrito aconteceu em 31/10/2026. Onde algo não foi comprovado no carro, o texto diz
"não confirmado".

## Sumário

1. [O carro e o objetivo](#1-o-carro-e-o-objetivo)
2. [Mapa rápido: onde fica cada coisa](#2-mapa-rápido-onde-fica-cada-coisa)
3. [Linha do tempo](#3-linha-do-tempo)
   - [Fase 1: mapa do Android Auto no painel de fábrica](#fase-1-mapa-do-android-auto-no-painel-de-fábrica)
   - [Fase 2: o Android Auto nativo da BYD serve?](#fase-2-o-android-auto-nativo-da-byd-serve)
   - [Fase 3: estudo do tema, só no PC](#fase-3-estudo-do-tema-só-no-pc)
   - [Fase 4: desenho do Horizon e preparação](#fase-4-desenho-do-horizon-e-preparação)
   - [Fase 5: Horizon no carro, v1 a v10](#fase-5-horizon-no-carro-v1-a-v10)
   - [Fase 6: os dados do ADAS](#fase-6-os-dados-do-adas)
4. [Referência técnica condensada](#4-referência-técnica-condensada)
5. [Estado atual e próximos passos](#5-estado-atual-e-próximos-passos)
6. [O que ainda não está confirmado](#6-o-que-ainda-não-está-confirmado)
7. [Créditos e licenças](#7-créditos-e-licenças)

Documentos de apoio, com mais detalhes:

| Documento | Conteúdo |
|---|---|
| [estudos/tema-cluster-88.md](estudos/tema-cluster-88.md) | Estudo do tema em 5 rodadas: mecanismo, geometria, ADAS 3D, teclas, builder, ferramenta |
| [estudos/aa-nativo-byd.md](estudos/aa-nativo-byd.md) | Por que o Android Auto nativo da BYD não serve para o mapa no painel |
| [estudos/adas-dados.md](estudos/adas-dados.md) | De onde vêm os alvos do ADAS e como lê-los |
| [../horizon/RECUPERACAO.md](../horizon/RECUPERACAO.md) | Recuperação passo a passo, payloads e matriz de falhas |
| [../horizon/TESTE_NO_CARRO.md](../horizon/TESTE_NO_CARRO.md) | Plano de teste em fases usado no primeiro teste |

---

## 1. O carro e o objetivo

| Item | Valor |
|---|---|
| Carro | BYD Song Pro, DiLink 3.0, Android 10 |
| Firmware | SX309 (plataforma 6125f, imagem "1for2") |
| Fingerprint | `BYD-AUTO/DiLink3.0/DiLink3.0:10/QKQ1.210910.001/eng.build.20251108.051320` |
| Tela central | 1920×1080 |
| Painel | 8,8"; no Android aparece como display 1 `fission_bg_xdjaVirtualSurface`, 1280×480, só quando a projeção está ligada |
| Acesso | adb por Wi-Fi como `shell` (uid 2000). Sem root, SELinux enforcing, build `user`/`release-keys` |

O DiLink 3 usa uma arquitetura "fission": o Android da multimídia roda numa célula, e o painel é desenhado
por um processo Qt 5.15 no **host**, fora dessa célula. Por isso o adb não enxerga o renderizador do painel nem os
logs dele.

**Ponto de partida.** O dono usava o app Electro, que se apoia no projeto open source DashCast, para jogar
um Waze instalado na multimídia em tela cheia no painel. Havia dois problemas:

1. Só funciona com mapas instalados na multimídia, sem layout pensado para o painel.
2. A projeção em tela cheia deixa o painel distorcido. A explicação do dono: essa função foi feita para o painel
   redondo do Song Plus, e o Song Pro só aceita porque usa o mesmo chip.

**Objetivo.** Primeiro, mostrar no painel o mapa de cluster que o próprio celular gera pelo Android Auto. Depois,
um tema de painel próprio, com o mapa num quadro central, a janela do ADAS preservada (é informação de
segurança), o ADAS 3D e um card de música.

---

## 2. Mapa rápido: onde fica cada coisa

### 2.1 Arquivos e caminhos

| O quê | Onde | Observação |
|---|---|---|
| QML do painel | `/system/lib64/cluster_theme2.rcc` (pasta interna `qml10_25/`) | Usado pelo 8,8" e pelo 10,25". 124 110 471 B, 3 486 entradas (191 `.qml`/`.js`, o resto imagens). Legível pelo shell |
| QML do painel 12,3" | `/system/lib64/cluster_theme1.rcc` (pasta interna `qml/`) | Não é usado no 8,8" |
| Traduções | `/system/lib64/commonTranslate.rcc` | 80 entradas (40 `.qm` + 40 `.ts`, inclui `pt_BR`) |
| Raiz do painel | `qml10_25/cluster.qml` (dentro do `.rcc`) | Cena de projeto 1920×720 escalada por 2/3 no 8,8" |
| Peças relevantes | `qml10_25/Center.qml`, `qml10_25/WarinLightLayout.qml`, `qml10_25/adas3d/Adas3dContainer.qml`, `qml10_25/adas3d/components/Model3D.qml`, `alwaysDisplay/Presentation.qml` | Centro (menu, drive info, ADAS 2D), luzes de alerta, ADAS 3D |
| Lógica nativa do painel | `/system/lib64/libBydCluster.so` (31 MB) | Plugins Qt `com.byd.DataSource`, `WarningLight`, `CustomStyle` (singleton) e os enums do metaobject |
| Fonte de dados do painel | `/system/lib64/libBydDataSource.so` | `BusinessUi1::updateThemeRcc`, `updateTranslateRccFromXml`, `updateAdasTargetSignal` |
| Runtime Qt do painel | `/vendor/FissionCluster_5_15_10/` (também existe `_6_5_5`) | `qt-reserved-files/qml/` é visível e tem `QtQuick3D`, `QtGraphicalEffects`, `QtQuick/Timeline`; `lib64/` não é listável |
| Seletor do tema | `/sys/module/max96745/parameters/clu_size` | Driver do SerDes. Não é legível pelo shell; `getprop ro.clu.size` = `88` |
| Pasta do tema extra ("carrier") | `/sdcard/.cluster/translate/` | No host é `/data/cells/cell2-rw/data/media/0/.cluster/translate/`. Não existe de fábrica |
| Escolha persistida | `/collect2/byd_datasource_config.xml`, chave `translate_rcc_path` | Configuração do host. Sobrevive a reboot do Android e a reset da multimídia |
| Feed de música do Horizon | `/sdcard/.cluster/horizon/` (`now_playing.json` + `art_<hash>.png/jpg`) | Escrito pelo open-headunit. O painel consegue ler (comprovado no carro) |
| Versões aposentadas | `/sdcard/.cluster/retired/` | Para onde o carrier é movido na recuperação |
| Logs da BYD | `/sdcard/logs/` | Os pacotes `All*.zip` são cifrados (AES). Beco sem saída |

### 2.2 Serviços e apps

| Serviço / app | Papel |
|---|---|
| `AutoContainer` (`android.os.IAutoContainer`) | Binder do `com.xdja.containerservice` (uid 1000, `/system/priv-app/XdjaContainerService/`). Liga e desliga a projeção no painel (transação 2, `sendInfo`) e troca o tema (transação 3, `sendInfo2(int, byte[])`, com callback registrado pela transação 4) |
| `/system/etc/container_comm_cfg.json` | Lista de pacotes autorizados a enviar ao AutoContainer. Neste carro só tem `com.xdja.clusterdemo`. O `shell` passa porque tem a mesma assinatura de plataforma do serviço |
| `com.byd.clusterdebug` | App de engenharia da BYD. Seu receiver exportado (ação `com.byd.cluster.spi`, extra `wholeFrame`) injeta quadros no painel. Precisa do `ClusterDebugService` rodando |
| `com.byd.androidauto` | Android Auto nativo (`/system/app/AndroidAutoNew/`, `libgalreceiver_jni.so`). Disputa a conexão USB com o open-headunit |
| `autoservice` (`libbydautoservice.so`) | Serviço nativo de dados do carro. Lido por `BYDAutoManager.getInt` |
| `com.byd.countrycodetool` | Ferramenta de país da BYD. Foi o "plano C" que recuperou o painel do autor do DashCast numa falha. Mexe na configuração de país |

### 2.3 Comandos úteis

Todos via adb, com o carro parado. Troque `<ip-da-multimídia>` pelo IP da sua multimídia.

```bash
# Projeção no painel (tema de fábrica): ligar e desligar
adb -s <ip-da-multimídia>:5555 shell "service call AutoContainer 2 i32 1000 i32 16 s16 ''; sleep 3; service call AutoContainer 2 i32 1000 i32 35 s16 ''"
adb -s <ip-da-multimídia>:5555 shell "service call AutoContainer 2 i32 1000 i32 18 s16 ''; sleep 1; service call AutoContainer 2 i32 1000 i32 0 s16 ''"

# Display do painel no Android (só existe com a projeção ligada)
adb -s <ip-da-multimídia>:5555 shell dumpsys display | grep -i xdja

# Consultar o tema selecionado (só leitura), com a ferramenta deste repositório
adb -s <ip-da-multimídia>:5555 shell 'CLASSPATH=/data/local/tmp/horizon-theme-tool.dex app_process / HorizonThemeTool query'

# Reiniciar host + célula (o painel inclusive). É o único reinício que relê o tema
adb -s <ip-da-multimídia>:5555 reboot fission-all

# Recuperação: tirar o tema da pasta e reiniciar UMA vez
adb -s <ip-da-multimídia>:5555 shell mv /sdcard/.cluster/translate/<arquivo>.rcc /sdcard/.cluster/retired/
adb -s <ip-da-multimídia>:5555 reboot fission-all
```

A carga de um tema **não** sai por `service call`: a transação 3 leva um `byte[]` FlatBuffer, que o `service call`
não sabe montar. Para isso existe a [horizon-theme-tool](../horizon/tools/horizon-theme-tool/).

---

## 3. Linha do tempo

### Fase 1: mapa do Android Auto no painel de fábrica

**1.1 O que o AutoContainer aceita.** A tabela de códigos que o DashCast levantou do `clusterdebug` (comando
`service call AutoContainer 2 i32 1000 i32 <código>`) foi testada no carro:

| Código | Efeito documentado | No Song Pro 8,8" |
|---|---|---|
| 16 | Projeção em tela cheia | Funciona. É o que Electro e DashCast usam |
| 17 | Projeção "metade/pequena" | Aceito (retorno 0), cria a display, mas o painel continua original. Não há modo reduzido nativo neste painel |
| 35 | Handshake | Cria a display 1, 1280×480 |
| 18 → 0 | Desliga a projeção | Funciona |
| 29 / 30 / 31 | Tamanho 8,8" / 12,3" / 10,25" | O "ADAS window fix" antigo mandava 30 antes do 16; o próprio DashCast recusa isso em painéis 1280×480 |
| 12 / 13 | Mostrar / esconder ADAS | Usados na sequência de esconder e restaurar a janela do ADAS |

**1.2 O celular gera um mapa só para o painel.** O protocolo do Android Auto permite que o head unit anuncie um
segundo canal de vídeo (`MediaSinkService`) com `display_type = CLUSTER`. Nos protos do aasdk os campos são
`display_id = 6` e `display_type = 7`. O celular então manda um segundo stream H.264, desenhado pelo app de
navegação para o painel: mapa limpo, sem botões, com manobra e chegada. Nada roda na multimídia.

O fork [moriceh/open-headunit](https://github.com/moriceh/open-headunit) (3.3.1) já anunciava esse canal (canal 19)
e decodificava o vídeo, mas o mandava para um adaptador HDMI USB ou para uma janelinha de debug. O primeiro teste
na janelinha confirmou que **o Waze rodando no celular** manda o mapa de cluster.

Antes disso foi preciso tirar o AA nativo do caminho: o `com.byd.androidauto` pegava a conexão USB e o handshake
SSL do open-headunit falhava ("Failed to read AAP header"). Resolveu com `pm disable-user com.byd.androidauto` e
um replug do cabo. É reversível com `pm enable`. O processo do app nativo só morre de vez no próximo reinício.

**1.3 Desenhar na display do painel.** No branch `feat/cluster-display-output` deste repositório o vídeo de cluster
passou a ser desenhado numa `Presentation` na display 1. O código está em
[aap/cluster/](../app/src/main/java/com/andrerinas/openheadunit/aap/cluster/), dividido em peças pequenas:

| Arquivo | Papel |
|---|---|
| `ClusterDisplayLocator.kt` | Acha a display do painel |
| `ClusterVideoGeometry.kt` | Escolhe a resolução AA e as margens para o celular desenhar só a área útil |
| `ClusterMapArea.kt` | Retângulo do mapa (`cluster_display_rect`); `HORIZON_MAP_HOLE = 390,100,500,292` |
| `ClusterDisplayOutput.kt`, `ClusterMapView.kt` | Janela na display 1 e recorte 1:1 do vídeo (`TextureView`) |
| `ClusterToggleButton.kt`, `ClusterMapToggle.kt` | Botão flutuante "MAPA" e o reconciliador de estado |
| `ClusterPanelControl.kt` | Comandos de shell para ligar/desligar o painel e esconder/restaurar o ADAS |

**1.4 Esconder a janela do ADAS de fábrica.** Com a projeção ligada, a janela do ADAS cobria a borda direita do
mapa. A técnica que o DashCast publicou nas versões 1.9.49 a 1.9.51 (documento `NATIVE_ADAS_PANE.md`, sequência
recuperada do Electro) manda quadros CAN pelo `clusterdebug`:

- 3 quadros avulsos de esconder (`23,50,00,08,01,00`): **não bastaram**.
- Comando 13, `cmd deviceidle whitelist +com.byd.clusterdebug`, `ClusterDebugService` ligado e 3 quadros: **não bastou**.
- A sequência completa de 263 quadros: **funcionou**. São 3 de esconder, 60 × `26,F0,00,28,01,06`,
  50 × `43,20,00,00,01,01`, 50 × `43,20,00,21,01,01`, 40 × `26,F0,00,28,01,06` e 60 de esconder.
- Restaurar: 40 × `23,50,00,08,01,01`, comando 12, 1,2 s de espera, comando 53.

O DashCast só tinha habilitado isso para o firmware SX361 do Seal. No SX309 também funcionou. Atenção: esses quadros
escondem **o painel inteiro do ADAS**, inclusive os alertas de distância e de faixa.

**1.5 Acertar o tamanho do mapa.**

| Tentativa | Resultado |
|---|---|
| Stream 800×480 num quadro central de 640×384 | Bordas pretas enormes (5:3 numa tela 8:3) |
| Stream 1280×720 com margens: o celular desenha a interface num retângulo 1060×336 em (60,44) | Melhor, mas sobram faixas pretas |
| Medir o Electro | Waze em tela cheia na display 1 (0,0 a 1280×480) com densidade forçada em 178 dpi (o padrão da display é 320). As barras da BYD são desenhadas por cima. O velocímetro do Electro é um overlay de 205×205 em (75,137) |
| Tela cheia 1280×480 | O Waze põe a setinha do carro a ~80% da altura (y ≈ 380), embaixo da barra de bateria |
| **`0,0,1280,420`** (stream 1280×720 com 300 px de margem na altura) | Setinha sobe para y ≈ 330. O dono aprovou ("ficou perfeito") |

**1.6 O "m/h" no canto.** Com a projeção ligada, a velocidade do painel some e sobra só "m/h" no canto superior
esquerdo. O Electro contorna com um velocímetro próprio por cima, lendo a velocidade real. A causa só apareceu na
Fase 3: no `qml10_25/cluster.qml` deste firmware o item `naviSpeedId` está **sem `x`, `y`, `width` e `height`**.
O texto fica centrado em (0,0) e a metade esquerda é cortada pela borda.

**1.7 O botão MAPA.** Ligar e desligar o painel exige comandos de shell. O open-headunit já tinha um "Self-ADB" (o
app se conecta ao adb da própria multimídia em `127.0.0.1:5555`). Quatro problemas apareceram:

| Sintoma | Causa | Correção |
|---|---|---|
| Popup "Permitir depuração USB" a cada uso, mesmo com "sempre permitir" | A assinatura do Self-ADB tinha padding PKCS#1 de 232 bytes em vez de 236 (`AdbCrypto`). O adb rejeitava e pedia de novo | Padding calculado pelo tamanho da chave, com teste unitário que falha no código antigo |
| Toques rápidos deixavam o botão verde e o painel desligado | Comandos terminavam fora de ordem | Reconciliador (`ClusterMapToggle`): cada toque só registra o estado desejado e um executor aplica o mais recente |
| A passada de esconder o ADAS morria no meio | A multimídia mata processos em segundo plano (subshell, `nohup`, até `setsid`) quando a sessão `exec:` do adb fecha | Comandos curtos e síncronos, no máximo 10 quadros por chamada |
| Desligar não voltava o painel | Ordem diferente da do DashCast | Fechar a projeção primeiro (18 → 1 s → 0) e só depois restaurar o ADAS |

No fim da fase o dono confirmou que ligar e desligar funciona. Mais tarde o botão deixou de esconder a janela do
ADAS ao ligar. Isso ficou adiado, porque com o Horizon a janela de fábrica já fica escondida pelo próprio tema.

### Fase 2: o Android Auto nativo da BYD serve?

Pergunta: dá para ter o mapa de cluster usando a conexão do AA nativo, sem o open-headunit? Resposta curta: **não
sem root**. Detalhes em [estudos/aa-nativo-byd.md](estudos/aa-nativo-byd.md).

- O `com.byd.androidauto` é o GAL receiver do Google (Java + `libgalreceiver_jni.so`) com uma camada da BYD.
- O código conhece `DISPLAY_TYPE_CLUSTER` e tem `GalIntegration.addDisplay()`, mas nada o chama: só existe o vídeo
  principal. O `NavigationStatus` (setas turn-by-turn) é criado e nunca registrado.
- O app é uid 1000, assinado com a chave da plataforma, e não exporta nada útil.
- Achado lateral: `ro.boot.verifiedbootstate=orange` e `ro.boot.flash.locked=0` (bootloader destravado). Root seria
  tecnicamente possível, mas é arriscado e **não foi verificado**.
- Recomendação adotada: manter o AA nativo desabilitado e o open-headunit como dono da conexão.

### Fase 3: estudo do tema, só no PC

Nada foi gravado no carro nesta fase; só leituras (`getprop`, `sha256sum`, `ls`, `dumpsys`, `adb pull` de
`/system`). O estudo completo, em cinco rodadas, está em [estudos/tema-cluster-88.md](estudos/tema-cluster-88.md).

**Rodada 1: o mecanismo.** O DashCast tem um "tema nativo" que não é APK: é um **RCC binário do Qt** (formato 3)
chamado "translate RCC". Ele leva todas as traduções de fábrica mais QML próprio, e os aliases dele sombreiam os
do tema de fábrica. O nome segue a regra nativa `(Di3F_.*_.*)_Translate(.*).rcc`. O carro usa o mesmo mecanismo
(as mesmas strings existem na `libBydDataSource.so` dele), mas **nenhum hash** bate com o que o DashCast conhece:

| Arquivo | Song Pro SX309 | DashCast (Seal) |
|---|---|---|
| `libBydCluster.so` | `9f8a0b26…` | `21f7735c…` |
| `libBydDataSource.so` | `46c36784…` | `02dfc7f5…` |
| `cluster_theme2.rcc` | `64cda421…` | `0041e6b8…` |
| `commonTranslate.rcc` | `b41bd89a…` (80 entradas) | `1dfee5a0…` (84 entradas) |
| Luzes de alerta (`WarinLightLayout.qml`) | 77 | 78 |

O DashCast recusa o carro de propósito. O caminho certo é um perfil novo para o SX309, não "afrouxar" hashes. O
próprio DashCast avisa que o "native dashboard" nunca foi validado num carro.

**Rodada 2: qual RCC e por quê.** Desmontando `BusinessUi1::updateThemeRcc` (`llvm-objdump`): o painel usa
`cluster_theme2.rcc` quando `g_productIndex` ∈ {1,2} e `g_ro_clu_size` ∈ {1,2}. O `g_ro_clu_size` vem do sysfs
`clu_size` do driver MAX96745: `88` → 1 (8,8"), `1025` → 2 (10,25"), qualquer outro → 3 (12,3", theme1). Então o
8,8" usa o **theme2** com `screenSize === 1`. A raiz é a cena de 1920×720 com `scale: 2/3`, `x: -320`, `y: -120`;
toda coordenada de projeto vira física multiplicando por 2/3.

Também saíram nesta rodada: a causa do "m/h" (item 1.6), um mapa da faixa livre no modo mapa (entre as barras,
≈ 1280×342 físicos, y 43 a 385) e o contrato novo das luzes (77 = 74 diretas + 3 agrupadas).

**Rodada 3: o ADAS 3D está no carro, desligado.** O `cluster_theme2.rcc` do SX309 tem o ADAS 3D completo: modelos
de carro, caminhão, moto e pedestre (simples e detalhados), faixas, o próprio carro e radares. A instância
`Adas3dContainer` no `Center.qml` está **comentada**, e só ligaria com `adasType === 1`. Ele lê 52 propriedades do
`DataSource`, e todas existem na `libBydCluster.so`. O `QtQuick3D 1.15` está disponível no runtime do painel.
Também foram extraídos do metaobject o contrato das teclas do volante e os códigos de erro da troca de tema,
incluindo `0x2332` (só troca em P) e `0x2333` (limite de frequência). Ver seção 4.7.

**Rodada 4: o builder.** [build_horizon_sx309_88.py](../horizon/tools/build_horizon_sx309_88.py) monta o carrier a
partir dos arquivos de fábrica do próprio carro:

- recusa se qualquer hash de entrada divergir;
- copia as 80 traduções byte a byte;
- faz só **inserções marcadas** `HORIZON_BEGIN/END` nos QML de fábrica, preservando BOM e CRLF, e confere que
  removê-las devolve o arquivo original;
- `--check` reconstrói e exige bytes idênticos.

O patch da raiz é **fail-open**: o Horizon entra num `Loader` assíncrono em z=1. Os itens de fábrica só ficam
transparentes quando `horizonActive` é verdadeiro (Loader pronto e condições de painel ligado). Se o Horizon
falhar ao carregar, o painel fica idêntico ao de fábrica. Isso foi comprovado num render da raiz no PC, pixel a
pixel. Erro de sintaxe **na raiz patchada** não é fail-open.

**Rodada 5: a ferramenta de seleção.** [horizon-theme-tool](../horizon/tools/horizon-theme-tool/) é Java compilado
para `.dex` e roda como shell via `app_process`. Faz `query`, `load` e `restore` pelo AutoContainer, com FlatBuffer
montado à mão. Foi conferido no `framework.jar` do carro: transação 3 = `sendInfo2(int, byte[])`, 4 =
`registerCallback`, callback 4 = `receivedInfo2`. O APK do serviço difere do Seal (`c6d2c097…`), e a ferramenta fixa
o deste carro. Ela recusa antes de enviar qualquer coisa se o uid, a API, o fingerprint ou algum de 9 hashes de
sistema não baterem. Cada execução manda um pedido só, e timeout é tratado como "ambíguo", nunca repetido.

### Fase 4: desenho do Horizon e preparação

O pedido do dono: um painel "bonito e completo", parecido com os BYD 2027, com o ADAS 3D, mapa num quadro central
com uma janelinha de ADAS, card do Spotify com letra, plano de recuperação e prévias para aprovar antes do teste.
O tema de exemplo que o autor do DashCast mostrou não está publicado (o branch `beta` era igual à 1.9.82), então o
Horizon foi escrito do zero: [horizon/qml/](../horizon/qml/).

| Modo | Layout (coordenadas de projeto 1920×720) |
|---|---|
| Instrumentos | ADAS 3D de fábrica no centro (`stage` 1120×450). Card de música ou de viagem à esquerda. Energia à direita: arco de potência, gráfico do último minuto e consumo dos últimos 50 km |
| Mapa | Buraco do mapa em (585,150) 750×438, que dá **390,100,500,292** físicos. ADAS 3D numa janela à esquerda, música compacta embaixo dela, energia à direita |

- Velocidade, limite, marcha, SOC, autonomia e ODO são desenhados pelo tema, o que também elimina o "m/h".
- Luzes de alerta, avisos, menu e pop-ups continuam os de fábrica, por cima.
- Arquivos principais: `HorizonCluster.qml` (composição), `HorizonData.qml` (adaptador do `DataSource`),
  `HorizonGeometry.js` (layout), `AdasStage.qml` (carrega o ADAS 3D de fábrica do próprio `.rcc`),
  `Backdrop.qml` (fundo), `SoftMask.qml` (bordas suaves) e os cards.

**Prévias no PC** ([horizon/mock/](../horizon/mock/)). PyQt5 5.15 renderiza o QML real com o `.rcc` de fábrica
registrado e stubs dos plugins da BYD. Detalhes que custaram tempo:

- é preciso a plataforma Wayland, porque o `offscreen` não tem GL e o backend de software dá segfault no Quick3D;
- há dois harness: um só do Horizon e um com a **raiz de fábrica patchada** (`root_test/`). Só o segundo pega
  bugs que dependem da raiz;
- a "letra" nas prévias é inventada.

O dono iterou o visual pelas prévias: tirar o ar de "UI genérica", lado direito mais rico, traços mais grossos e
bordas do mapa em degradê.

**Feed de música.** O open-headunit já recebe do celular os metadados (com capa) e o estado de reprodução. O pacote
[nowplaying/](../app/src/main/java/com/andrerinas/openheadunit/aap/cluster/nowplaying/) grava
`/sdcard/.cluster/horizon/now_playing.json` (`seq`, título, artista, posição, duração, capa, letra) com escrita
atômica. O tema lê por `XMLHttpRequest` em `file://` a cada 500 ms (o Qt 5.15 permite, com um aviso). Considera o
feed vivo enquanto `seq` avança; senão usa o texto nativo `DataSource.mediaInfoString`.

- No Android 10 foi preciso conceder `READ/WRITE_EXTERNAL_STORAGE` com `pm grant` e usar `requestLegacyExternalStorage`.
- A letra vem do LRCLIB, que devolveu HTTP 502 durante todos os testes. Uma reserva pela API da NetEase foi escrita e
  testada, mas não instalada. A letra ficou para depois, por decisão do dono.

### Fase 5: Horizon no carro, v1 a v10

**Pré-testes.**

1. O feed de música foi conferido na multimídia: `seq` avançando e capa gravada. A letra veio nula por causa do LRCLIB.
2. `query` (só leitura): os arquivos de sistema bateram com o coorte, e a seleção estava em `[FACTORY DEFAULT]`.
3. Ensaio de um `adb reboot fission-all` com o painel normal: o adb caiu ~11 s depois do comando e voltou sozinho
   ~80 s depois, e o painel voltou normal. Sem esse ensaio, nenhum tema seria carregado.

**Descobertas sobre a seleção** (registradas em [RECUPERACAO.md](../horizon/RECUPERACAO.md)):

| Ação | O que acontece neste firmware |
|---|---|
| `load` | Só **grava** a escolha. Chega o ACK4 e não chega o ACK2. O painel não muda |
| `adb reboot fission-all` depois do `load` | O host lê a escolha salva (a consulta passa a mostrar `source=0`) e o Horizon aparece |
| `restore` (cmd 5, modo 1) | Responde ACK3 `RESOURCE_IS_SAME` (`0x2330`) e **não faz nada**, como o DashCast já tinha registrado |
| Mover o `.rcc` para `retired/` + um `fission-all` | **Recuperação comprovada.** O painel de fábrica volta, e a consulta volta sozinha a `[FACTORY DEFAULT]` |
| Trocar de versão | Aposentar a atual, pôr a nova, `load NOVA --sha256 … --assert-parked --replace-own --ack-ambiguous` e um `fission-all` |
| Tempo de cada reinício | De 50 a ~80 s até o adb voltar sozinho, nas medições registradas |

O Horizon **não** aparece como terceiro tema no menu do painel: os dois temas do menu são estilos de fábrica, e o
carrier substitui a tela principal por cima deles. Não troque de tema pelo menu com o Horizon carregado (não testado).

**As versões.** "Entradas" é o número de arquivos dentro do carrier.

| Versão | SHA-256 | Entradas | O que mudou | Resultado no carro |
|---|---|---|---|---|
| v1 | `ac00975e7599…` | 103 | Primeira carga: Loader do Horizon, fail-open, luzes no arranjo de navegação, placa de limite nativa escondida | Apareceu. Centro sobreposto, "undefined km", textos pequenos |
| v2 | `b56ecebf68eb…` | 104 | Patch do `Center.qml`; ODO por `odoValue`/`odoIndex`; textos ~20% maiores (~30% nos rótulos pequenos) | Centro limpo, ODO certo, capa da música no painel. Mas o painel reiniciava sozinho ("ELKA OFF") |
| v3 | `3ed9157e5c3c…` | 104 | Diagnóstico: ADAS 3D desligado | 0 reinícios em ~4 min. A janela 2D de reserva apareceu no canto, sobre os números de consumo |
| v4 | `276b0e2d5a24…` | 105 | `Model3D.qml` com os 6 `Loader3D` síncronos; janela 2D de reserva movida para o lugar do ADAS do Horizon | 3D de volta e 0 reinícios (monitor de 15 min cobrindo v4 e v5) |
| v5 | `61ef0d0b181a…` | 106 | `Adas3dContainer.qml`: carro centralizado quando não há faixa. open-headunit com o retângulo `390,100,500,292` | Faixas ok. Mapa não aparece, caixa cinza na janela do ADAS |
| v6 | `a33319bd9c59…` | 106 | Máscaras por `ShaderEffect` (`SoftMask`); fundo do popup do 3D transparente; pista sempre visível | **Waze no quadro central** com bordas suaves e ADAS 3D na janelinha. Primeira volta: nenhum reinício |
| v7 | `aff4a5aa8dc4…` | 106 | Fundo novo num shader: céu, horizonte, chão | Só prévia, não instalado. "Muito básico e morto, zero profundidade" |
| v8 | `f269b263b3de…` | 107 | Faixas na posição real e faixas vizinhas; chão em perspectiva com faixas de luz que andam com a velocidade | Não instalado sozinho |
| v8diag | `2f974e76875e…` | 107 | v8 + bloco de diagnóstico dos alvos do ADAS | Instalado. Usado nos testes da Fase 6 |
| v9 | `6806367fa7e7…` | 107 | Alvo sem tipo desenhado como carro | Não instalado sozinho |
| v9diag | `4b0785e8a6db…` | 107 | v9 + diagnóstico | **Instalado hoje.** Carro "fantasma" travado na última posição; o dono não gostou das linhas do fundo |
| v10 | `30c7e166e3f8…` | 107 | Fundo sem os trilhos nem movimento; alvo sem tipo só enquanto a posição muda (1,5 s); sem diagnóstico | Pronto, **não instalado** |

Os problemas principais, um a um:

**Centro sobreposto (v1 → v2).** Em P, o `Center` de fábrica (z=3) mostra a página de drive info com o "carrinho" do
fluxo de energia, desenhada **por cima** do Horizon (z=1). O ADAS 3D tinha carregado, mas parado não há dados de faixa,
por isso parecia estranho. A v2 passou a sobrescrever o `Center.qml` com inserções: enquanto o Horizon está ativo, as
páginas de drive info e o fluxo de energia cedem; enquanto o 3D do Horizon está pronto, a janela grande do ADAS 2D cede.
Menu, avisos e telas de carga não são tocados. A Rodada 1 não tinha conseguido provar que **os aliases do carrier
vencem os do tema de fábrica**. A v1 aparecer provou isso para a raiz, e a v2 confirmou para o `Center.qml`.

**"undefined km".** O Horizon lia `evOdo`, que não existe neste carro. Passou a usar `odoValue`/`odoIndex`, o mesmo
valor que o painel de fábrica mostra.

**Reinícios do painel (v2 → v4).** Depois de algum tempo o painel piscava o de fábrica por ~2 s, com "ELKA OFF", e
voltava ao Horizon. No logcat da multimídia aparecia `AutoContainerServiceNative died`. Segundo o dono, o painel de
fábrica nunca faz isso. A investigação:

1. Primeira suspeita: o open-headunit, que ao conectar mandava a sequência "desligar mapa". Num boot com o celular
   desconectado o reinício aconteceu do mesmo jeito (cerca de 1 min 13 s após o boot; no anterior, 1 min 50 s), e o
   open-headunit não mandou nada. Descartado.
2. O reinício voltou a acontecer no mesmo boot, então não era "uma vez por boot".
3. A v3, sem o ADAS 3D, ficou sem nenhum reinício.
4. Causa: o `Model3D.qml` de fábrica tem 6 `Loader3D` assíncronos, um por slot de alvo. Quando a câmera detecta e perde
   objetos, a troca de modelo cai no meio de um carregamento e o Quick3D 5.15 derruba o renderizador. A BYD deixou esse
   3D comentado neste firmware, talvez pelo mesmo motivo (não confirmado).
5. Correção (v4): os 6 loaders síncronos. Zero reinícios em 15 min de monitor e na primeira volta de rua.

**Faixas atravessando o carro (v5 → v8).** Sem faixa detectada, a câmera manda distância 0 até a linha esquerda, e o
3D desenhava uma linha passando pelo carro. A v5 centralizou o carro nesse caso, como o 2D de fábrica faz. Na rua o
dono notou que a troca de faixa não aparecia. A v8 usa a posição real quando a distância é plausível (0 < d < 100; no
vídeo, ~47 a 49 = centralizado). Ela também desenha a faixa vizinha, mais clara, quando a linha do lado é tracejada,
o que dá as três faixas de uma via de três. A câmera só manda as duas linhas da faixa atual; o 3D de fábrica só mostra
as vizinhas durante a troca automática do ICC.

**O mapa não aparecia (v5 → v6).** O buraco do fundo e as bordas suaves da janela do ADAS eram máscaras desenhadas
num `Canvas` escondido. **Dentro da raiz de fábrica esse `Canvas` nunca é pintado**, então o fundo continuava opaco
sobre o mapa. No harness só do Horizon funcionava; o harness com a raiz de fábrica reproduziu o bug. A v6 calcula as
máscaras num `ShaderEffect` (`SoftMask.qml`). A superfície Android do mapa fica **abaixo** da cena Qt (z=-3), então o
buraco precisa ter alfa 0.

**Caixa cinza e pista sumida (v5 → v6).** A caixa cinza era o fundo do popup do 3D de fábrica (`bgId`), claro quando
o painel não está em modo noite: virou opacidade 0. A pista só aparecia depois de desligar o MAPA porque dependia do
modo de ADAS que o open-headunit deixa ao restaurar: passou a ficar sempre visível no modo instrumentos.

**Janela 2D de reserva fora do lugar (v3 → v4).** Sem o 3D, a janela compacta do ADAS 2D de fábrica aparecia no canto
dela, sobre os números de consumo. Ela foi reposicionada e ampliada para o lugar do ADAS do Horizon: centro no modo
instrumentos, janela esquerda no modo mapa.

**Mapa pequeno.** O celular desenhava o mapa de cluster a 160 dpi (o padrão do open-headunit quando
`cluster_video_dpi` = 0). Subiu para **240** e tudo ficou 1,5× maior. Fica nas configurações do open-headunit.

**Fundo.** A v6 tinha um degradê com halo. A v7 (céu, horizonte e chão) foi recusada nas prévias. A v8 (faixas de luz e
pulsos que andam com a velocidade) foi recusada no carro ("essas linhas extras não ficaram legais"). A v10 tira trilhos,
emendas e movimento e deixa céu, chão em perspectiva com névoa, luz sob o carro e vinheta. A meta declarada é um fundo
parecido com a referência da BYD que o dono enviou: azul-marinho escuro, arco curvo no topo, pista em perspectiva.
**Ainda não alcançada.**

**Primeira volta de rua (com a v6).** Nenhum reinício, e o mapa e o 3D funcionaram. O dono apontou três coisas: o 3D só
mostra veículos com ACC/ICC ligado; a troca de faixa não aparecia (corrigido na v8); só aparece um veículo. Isso
abriu a Fase 6.

### Fase 6: os dados do ADAS

O objetivo: mostrar veículos sem precisar ligar o ACC, e de preferência mais de um. Resumo em
[estudos/adas-dados.md](estudos/adas-dados.md).

**1. Primeiras hipóteses, descartadas.**

- O `BYDAutoADASDevice` da multimídia só expõe estados e chaves (AEB, FCW, LKS, BSD, ELKA…), não a lista de alvos.
- O guard `adasShowMode === 1` nas funções de alvo do 3D é um modo de demonstração de loja, sem relação com o ACC.
- Existe uma opção de painel escondida, `INSTRUMENT_DISTANCE_OF_TARGET_AHEAD_ADVANCED_SET` (`0x43F08018`, valores
  `DISPLAY_FRONTCAR_NODISPLAY_DISTANCE` = 1 e `NODISPLAY_FRONTCAR_DISTANCE` = 2). Nenhuma tela de configuração deste
  carro a usa, e escrevê-la exige permissão de assinatura. Não foi testada.

**2. Diagnóstico no próprio painel (v8diag, v9diag).** O `DiagOverlay.qml` mostra no card de música os valores crus
que o painel recebe: slots F1, F2, LF, RF, LR, RR e R (tipo `st`, `x`, `y`), alvo principal, `ahead`, nível, texto do
ACC e dados de faixa. Os testes foram filmados:

- Condomínio, 8 s, um carro à frente: o texto do ACC sobe quando o ACC liga. F1 apareceu com `st 0`, `x -0.1`,
  `y 17` sem ACC. Na hora isso foi lido como "posição sem tipo", e a v9 passou a desenhar esses alvos como carro.
- Rodando no condomínio e passando por carros estacionados: F1 zerado o tempo todo, com ou sem ACC.
- Rua com a v9diag: só um veículo, motos reconhecidas, e o carro "fantasma" **travado na última posição** quando o
  rastreio acaba.

**3. A resposta estava na `libBydDataSource.so`.** Desmontando `BusinessUi1::updateAdasTargetSignal`:

- **Formato novo:** CAN `0x295` (`ADAS_TSR_TARGET_*_01..04`), usado quando `BODYWORK_POWER_LEVEL` (`0x12D`, campo
  `0x2A`) = 3 e algum subquadro `0x2950N` está presente. O carro usa este (a distância chega com decimais).
  **Formato antigo:** CAN `0x374` (`SETTING_TARGET_*`).
- **Slots do 0x295:** 1 = frente-esquerda, 2 = frente 1 (F1), 3 = frente-direita, 4 = frente 2. Os traseiros
  (`leftRear`/`rightRear`) **não têm leitura de CAN** nesta versão.
- **Regra:** o slot só é atualizado se o subquadro está presente **e** o `TYPE` está entre 1 e 4. Senão zera só o
  tipo e **não mexe em x/y**. Daí o carro travado: o `y 17` sem ACC era o último valor válido, não dado novo.
  Conversão: x = raw × 0,1 − 12 m; y = raw × 0,1 − 10 m. `STATE`, `STATE_LEVEL` e velocidades são ignorados.
- **No carro:** só o slot 2 recebe tipo, e só com ACC/ICC ativo. O painel não filtra mais nada; o limite é o que a
  câmera manda. A lógica de "alvo sem tipo" da v9/v10, portanto, não traz dado novo.

**4. Ler os sinais pela multimídia, sem permissão especial.** O `autoservice` nativo **não restringe leitura por
chamador** (`BYDAutoService::checkGetPermission` sempre permite). As permissões `BYDAUTO_*_GET` só são checadas nas
classes Java `BYDAuto*Device.getInstance`, e o `BYDAutoManager.getInt` não passa por elas.

- Os IDs têm de estar no **formato CAN** (o do SDK do CarSetting, ex.: `0x12D0002A`). Com o carro desligado, esse ID
  devolveu `0`; o mesmo sinal no formato do framework (`0x1CD0002A`) devolveu `-10011` (= nenhum dado).
- A ferramenta [adas-probe](../horizon/tools/adas-probe/) roda como shell, **só lê** e registra a cada 250 ms.
  `start_log.sh` deixa um registro rodando desacoplado do adb.
- `byd_signal_catalog.tsv` tem o catálogo de 11 148 sinais (ID, nome, classe) extraído do CarSetting.

**5. Candidatos para "veículo à frente sem ACC"**, ainda **não lidos com o carro ligado**:

- `0x138` (FCW): `ADAS_FRONT_VEHICLE_STATUS` `0x1380000A`, `_DISTANCE` `0x1380000B`, `_SPEED` `0x13800018`,
  `_ACCELERATION` `0x13800020`, `ADAS_FCW_LEVEL_STATUS` `0x13800008`. O alerta de colisão funciona sem ACC, então é o
  melhor candidato.
- `0x09B` (faróis): `LIGHTS_TARGET_COUNT` `0x09B00408` e `LIGHTS_TARGET_1..12_INFO`, até 12 alvos. Talvez só à noite
  (não confirmado).

Se houver dados sem ACC, o plano é o open-headunit ler esses sinais e escrever um feed, como o `now_playing.json`,
para o tema desenhar os alvos.

**Becos sem saída:** os pacotes de log da BYD em `/sdcard/logs` são cifrados com AES. O argumento do dono de que o
BYD King mostra o carro da frente sem ACC (mesmo chipset) **não foi verificado**.

---

## 4. Referência técnica condensada

### 4.1 O carrier

| Item | Valor |
|---|---|
| Formato | RCC binário do Qt, formato 3: magic `qres`, inteiros big-endian, nomes UTF-16BE, arquivos zlib |
| Nome selecionável | `Di3F_DynastyOceanTranslate_V0001_TranslateHorizon_<32 primeiros hex do SHA-256>.rcc` |
| Regra nativa | `(Di3F_.*_.*)_Translate(.*).rcc`, com UI version `Di3F_DynastyOceanTranslate_V0001` |
| Onde | `/sdcard/.cluster/translate/`, **exatamente um** `.rcc` (`0x232b` se houver mais, `0x232a` se vazio, `0x2329` se a pasta não existe) |
| Tamanho | < 4 MiB (limite de segurança herdado do DashCast). A v10 tem 2 085 197 B |
| Conteúdo (v10, 107 entradas) | 80 traduções de fábrica byte a byte; `qml/cluster.qml` idêntico; `qml10_25/cluster.qml`, `WarinLightLayout.qml`, `Center.qml`, `adas3d/components/Model3D.qml` e `adas3d/Adas3dContainer.qml` com inserções marcadas; os arquivos do Horizon em `qrc:/horizon/` |
| Restrições do OEM | Troca só em P (`0x2332`), limite de frequência (`0x2333`), ~15,5 s entre trocas (a ferramenta exige 16 s) |

O builder gera um arquivo chamado `OFFLINE_ONLY_horizon_sx309_88.rcc`, que de propósito **não** é selecionável; ele
só vira carrier depois de renomeado. O carrier contém arquivos da BYD (traduções e QML de fábrica com inserções),
então **não deve ser redistribuído**. Cada carro deve gerar o seu a partir dos próprios arquivos de `/system/lib64/`.

### 4.2 Seleção e recuperação

Sequência usada no carro (comandos exatos em [RECUPERACAO.md](../horizon/RECUPERACAO.md) §2 a §5):

1. Carro em P, freio de mão, ligado (o Horizon só aparece com o painel em ON; em acessório fica o de fábrica).
2. `query` deve mostrar `cohort: ok` e `[FACTORY DEFAULT]`.
3. `push` do carrier para `/sdcard/.cluster/horizon/.incoming.rcc`, conferir o SHA, `mv` atômico para `translate/`
   com o nome selecionável.
4. `load <nome> --sha256 <hex> --assert-parked`. Fica "ambíguo" (só ACK4), o que é normal aqui. Não repetir.
5. Um `adb reboot fission-all`. Depois `query` deve mostrar o arquivo com `source=0`.

Recuperação (comprovada): mover o `.rcc` para `/sdcard/.cluster/retired/` **antes** e fazer **um**
`adb reboot fission-all`. Regras:

- Nunca apagar: mover.
- Nunca recarregar um arquivo que falhou.
- Não usar `adb shell reboot`: o reboot comum do Android não reinicia o host, e foi o que não recuperou o painel do
  autor do DashCast.
- O `reboot:fission-all` sai pela autoridade do adbd. O shell e os apps não têm `android.permission.REBOOT`.
- Formatar a multimídia provavelmente **não** limpa a escolha, que fica em `/collect2` (não confirmado).
- Plano C: Country Code Tool (`com.byd.countrycodetool`) ou concessionária.

A recuperação foi comprovada partindo de um Horizon funcionando, **não** de uma raiz de QML quebrada.

### 4.3 Camadas e fail-open

Valores `z` reais, lidos do `CustomStyle` embutido na `libBydCluster.so`:

| Camada | z |
|---|---|
| Superfície Android (mapa) | −3 |
| Fundo de fábrica (`bgZ`) | −2 |
| Animação de troca de tema | −1 |
| Camada sempre visível de fábrica (`alwyasDisplayId`) | 0 |
| **Horizon** (`horizonLoader`) | **1** |
| `Center` (menu, drive info, avisos, ADAS 2D dinâmico) | 3 |
| ADAS (`adasZ`) | 99 |
| Luzes de alerta | 100 |
| Dicas de texto do ADAS | 111 |
| Animações de ligar | 199 / 200 |

- `horizonWanted` só é verdadeiro com `screenSize === 1`, raiz 1920×720, `powerGear === 3`, animação de ligar
  concluída e as camadas de fábrica visíveis.
- Só com `horizonActive` (Loader pronto) ficam transparentes: o fundo de fábrica, a camada sempre visível (marcha,
  relógio, velocidade, potência, SOC/autonomia/ODO, medidores) e a moldura da janela 2D do ADAS.
- Perdas deliberadas com o Horizon ativo: bússola e a luz de recuperação de energia (`feedbackLight`).

### 4.4 ADAS: alvos e IDs

Propriedades do `DataSource` lidas pelo 3D: `{leftFront,rightFront,leftRear,rightRear,frontTargetOne,frontTargetTwo}{State,PosX,PosY}`,
mais faixas (`carToLeftLaneDistance`, `leftLineType`, `rightLineType`, `laneRadius`, `roadWidth`). Estado 1 = carro,
2 = caminhão, 3 = moto, 4 = pedestre.

IDs no formato CAN, slot N = 1..4:

| Sinal | ID | Dispositivo |
|---|---|---|
| Subquadro presente (`BODYWORK_ONLINE_HAS_0x02950N`) | `0x2950N000` | Carroceria (1001) |
| `TYPE` | `0x2950N01C` | ADAS (1038) |
| `STATE` | `0x2950N01B` | ADAS |
| `X_AXIS` / `Y_AXIS` | `0x2950N010` / `0x2950N030` | ADAS |
| Formato antigo, slot 2 | `0x37402034`, `…030`, `…010`, `…018` | Setting (1023) |
| `BODYWORK_POWER_LEVEL` | `0x12D0002A` | Carroceria |

Para ler um sinal: `CLASSPATH=/data/local/tmp/adas-probe.dex app_process / AdasProbe get <dispositivo> <hexid>`.
Para registrar a volta inteira: `start_log.sh 1500 <ip-da-multimídia>:5555` (25 min) e depois
`adb pull /data/local/tmp/adas-probe-drive.log`.

### 4.5 Códigos do AutoContainer e menu de depuração

Comando base: `service call AutoContainer 2 i32 1000 i32 <n> s16 ''`.

| n | Efeito |
|---|---|
| 0 / 1 | Controle do stream de vídeo (qual dos dois liga não foi conferido). O 0 fecha a sequência de desligar (18 → 0) |
| 12 / 13 | Mostrar / esconder ADAS |
| 16 / 17 / 18 | Projeção cheia / meia / desligar |
| 29 / 30 / 31 | Tamanho de tela |
| 32 / 33 | Auto-atualização do ADAS 3D |
| 35 | Handshake (cria a display 1) |
| 36 | Screenshot do painel |
| 37 / 38 | Nível de log |
| 49 / 50 | Cena ADAS normal / "SR" (reconstrução de cena; provavelmente exige uma central de ADAS por Ethernet) |
| 53 | Tipo de ADAS (usado ao restaurar) |
| 200 / 201 | Captura UDP em `eth0` |
| 204 / 205 / 224 / 225 | SR por Ethernet / CAN / projeção / mensagens reais |
| 218 / 219 | Gravação de tela |

Os modos de depuração foram levantados por leitura; quase nenhum foi testado no carro.

### 4.6 Medidas do 8,8"

| Medida | Valor |
|---|---|
| Projeto → físico | × 2/3 |
| Display 1 | 1280×480, 320 dpi, virtual, dono `com.xdja.containerservice`, sem toque |
| Faixa livre entre as barras de fábrica (modo mapa) | ≈ 1280×342 físicos, y 43 a 385 |
| Mapa em tela cheia, tema de fábrica | `cluster_display_rect = 0,0,1280,420` |
| Mapa no quadro do Horizon | `cluster_display_rect = 390,100,500,292`, `cluster_video_dpi = 240` |

### 4.7 Teclas do volante (contrato do metaobject)

| Sentido | IDs |
|---|---|
| Nativo → QML (`onPluginMsgReceived`) | 0 cima, 1 baixo, 2 esquerda, 3 direita, 4 OK, 5 voltar, 6 toque longo, 7/8 drive info curto/longo. Valor: 0 soltou, 1 clique, 2 clique longo |
| QML → nativo (obrigatórios) | 17 `QML_LOAD_COMPLETE` no `Component.onCompleted` da raiz, mais as respostas de animação (19, 20, 21, 41) |

O Horizon usa esquerda/direita com valor 1 para alternar música e viagem, só fora do modo mapa e com o menu nativo
fechado. Não consome teclas e não substitui o menu. **Não confirmado no carro:** qual botão físico gera qual ID, e se
a alternância funciona.

---

## 5. Estado atual e próximos passos

**No carro hoje:**

- Horizon **v9diag** selecionado e ativo, com o bloco de diagnóstico.
- `retired/` guarda v1 a v6 e v8diag.
- `com.byd.androidauto` desabilitado.
- open-headunit com `cluster_display_rect = 390,100,500,292` e `cluster_video_dpi = 240`.

**Pronto e não instalado:**

- Horizon **v10** (sem diagnóstico, fundo sem trilhos), SHA `30c7e166e3f8…`.
- APK do open-headunit com a reserva de letra pela NetEase.

**Funciona e foi visto no carro:**

- Tema carregando pelo carrier, e recuperação por "mover + fission-all".
- ADAS 3D de fábrica estável, sem reinícios, inclusive dirigindo.
- Waze do celular no quadro central, com bordas suaves.
- Capa e progresso da música vindos do open-headunit.
- ODO correto e velocidade atualizando.

**Próximos passos:**

1. Instalar a v10, com o carro parado e um `fission-all`.
2. Rodar o `adas-probe` com o carro **ligado**: um trecho sem ACC atrás de outros carros e outro com ACC. Ver se `0x138`
   e `0x09B` trazem dados sem ACC. Se trouxerem, criar o feed de alvos no open-headunit para o Horizon.
3. Um fundo à altura da referência da BYD.
4. Um app para compartilhar com outros donos:
   - instala e remove o Horizon montando o carrier **no próprio carro**, a partir dos arquivos de fábrica dele, para
     não redistribuir arquivos da BYD;
   - inclui o open-headunit só por cabo, com a interface simplificada e direto no USB, escondendo menus sem apagar
     código;
   - permite alternar entre o open-headunit e o AA nativo da BYD (escolher o open-headunit desabilita o
     `com.byd.androidauto`).

   O desenho em discussão usa o Self-ADB para os passos de shell. **Ainda não implementado.**
5. Pendências menores:
   - parar de mandar os quadros do `clusterdebug` ao ligar o MAPA quando o Horizon está ativo (não são mais necessários
     e mexem no modo do ADAS);
   - letra das músicas;
   - confirmar as teclas do volante.

**Para outros donos:** as ferramentas fixam o coorte do SX309 (build `20251108.051320`, hashes da seção 3). Firmware
diferente é recusado de propósito e exige refazer o estudo: hashes, âncoras do QML, contagem de traduções e de luzes.

---

## 6. O que ainda não está confirmado

- Se a câmera manda alvos (posição ou tipo) sem ACC em algum sinal legível pela multimídia (`0x138`, `0x09B`).
- Se há mais de um veículo disponível em algum lugar. Até agora só o slot 2 recebeu tipo.
- Recuperação a partir de uma **raiz** de QML quebrada (só foi provada partindo de um Horizon funcionando).
- Se formatar a multimídia limpa a escolha do tema.
- Por que a BYD deixou o ADAS 3D comentado neste firmware (a falha dos loaders assíncronos é uma hipótese compatível).
- O desempenho do 3D em longas viagens. Só houve uma volta curta, sem reinícios.
- Botão físico ↔ ID de tecla, e a alternância música/viagem pelas setas.
- A opção escondida "carro à frente" (`0x43F08018`): o efeito e se é possível ligá-la.
- Os modos de depuração do ClusterDebug (SR, captura UDP, gravação): levantados por leitura, não testados.
- O que acontece ao trocar de tema pelo menu do painel com o Horizon carregado.

---

## 7. Créditos e licenças

- **open-headunit** ([andreknieriem/open-headunit](https://github.com/andreknieriem/open-headunit)), sob
  **AGPL-3.0**, derivado do Headunit for Android Auto de Michael A. Reid (ver `COPYRIGHT_MICHAEL_REID_GPLv3AFFERO.txt`).
  Este repositório é um fork do fork [moriceh/open-headunit](https://github.com/moriceh/open-headunit), que já trazia o
  canal de vídeo de cluster. As mudanças de painel deste repositório seguem a AGPL-3.0.
- **DashCast** ([github.com/Kiroha/byd-dashcast](https://github.com/Kiroha/byd-dashcast)), de Cedric Carre, sob
  **MIT**. Dele vieram: o mecanismo do translate RCC, o codec RCC (`horizon/tools/third_party/dashcast/`, com a
  licença), o protocolo FlatBuffer do AutoContainer, a tabela de códigos, os quadros do ADAS e o procedimento de
  recuperação.
- **aasdk**: os protos de referência para os campos `display_id`/`display_type` do Android Auto.
- **FlatBuffers**: o construtor da `horizon-theme-tool` é um porte do algoritmo do flatbuffers-java.
- **LRCLIB** e **NetEase**: fontes de letra usadas pelo feed de música.
- Os arquivos do painel (QML, imagens, traduções, bibliotecas) são da **BYD** e não estão neste repositório. As
  ferramentas leem os arquivos do próprio carro. O Electro é um app fechado, observado só de fora.
