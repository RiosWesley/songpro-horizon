# Guia: seu próprio tema no painel de 8,8" do BYD Song Pro

Passo a passo para quem tem um **BYD Song Pro com DiLink 3, firmware SX309 e painel de instrumentos de 8,8"** e
quer trocar a tela principal do painel por um tema feito por você, em QML. O escopo é o básico: um tema que
mostre as informações (velocidade, marcha, bateria, autonomia, combustível, ODO, hora). Mapa, Android Auto e
projeção no painel **não** são tratados aqui.

O que já foi comprovado no carro (com o tema Horizon deste repositório): o arquivo de tema (o "carrier") substitui a
raiz do painel, a seleção funciona, o tema aparece depois de reiniciar o host e a volta ao painel de fábrica funciona.
O **tema de exemplo deste guia** foi testado só no PC (render com mock e teste da raiz de fábrica com o carrier); a
primeira vez dele num carro vai ser a sua.

Documentos de apoio: o estudo completo em [estudos/tema-cluster-88.md](estudos/tema-cluster-88.md), a recuperação em
[../horizon/RECUPERACAO.md](../horizon/RECUPERACAO.md) e a história das descobertas em
[historia-cluster-song-pro.md](historia-cluster-song-pro.md).

## Sumário

- [Antes de começar: riscos e regras](#antes-de-começar-riscos-e-regras)
- [Requisitos](#requisitos)
- [1. Conferir o firmware](#1-conferir-o-firmware)
- [2. Copiar os arquivos de fábrica](#2-copiar-os-arquivos-de-fábrica)
- [3. Entender a raiz do painel](#3-entender-a-raiz-do-painel)
- [4. Escrever o tema](#4-escrever-o-tema)
- [5. O patch na raiz e o fail-open](#5-o-patch-na-raiz-e-o-fail-open)
- [6. Montar o carrier](#6-montar-o-carrier)
- [7. Testar no PC](#7-testar-no-pc)
- [8. Colocar no carro, selecionar e reiniciar](#8-colocar-no-carro-selecionar-e-reiniciar)
- [9. Voltar ao original e trocar de versão](#9-voltar-ao-original-e-trocar-de-versão)
- [10. Erros comuns](#10-erros-comuns)
- [11. Créditos e licenças](#11-créditos-e-licenças)

---

## Antes de começar: riscos e regras

Você vai mexer no painel que mostra velocidade e luzes de alerta. Leia tudo antes do primeiro comando.

- **Carro parado, em P, freio de mão puxado**, de preferência em garagem. O próprio sistema recusa troca de tema fora
  de P (erro `0x2332`). Nunca saia dirigindo com um tema que você não viu funcionar parado.
- **Só para o firmware exato.** As ferramentas conferem a impressão digital do build e o SHA-256 dos arquivos de
  sistema e recusam qualquer outro. Outro firmware precisa de outros hashes e de um estudo novo; não "afrouxe" as
  conferências.
- **Uma mudança por reinício.** Mude uma coisa, reinicie, olhe o painel.
- **Nunca recarregue um carrier que falhou.** Corrija, gere uma versão nova e troque (seção 9).
- **Mova, nunca apague.** Arquivos saem de `translate/` para `retired/`.
- **Um `adb reboot fission-all` por vez.** Se o adb cair, espere ele voltar sozinho; não repita o comando em loop.
- A volta ao original (seção 9) foi comprovada no carro a partir de um tema que funcionava. A recuperação a partir de
  um QML que quebre a **raiz** do painel nunca foi testada. O plano C está no fim da seção 9.
- **Não publique arquivos da BYD.** Os `.rcc` de fábrica, os QML extraídos, o carrier gerado e as pastas `patched/` e
  `patches/` contêm código da BYD. Para compartilhar, publique só o código do **seu** tema; cada pessoa monta o próprio
  carrier com os arquivos do próprio carro.

## Requisitos

- BYD Song Pro com painel de 8,8", DiLink 3, firmware SX309 (a seção 1 confirma).
- **ADB sem fio da multimídia ligado** e acessível pelo PC em `<ip-da-multimídia>:5555`. Como ligar não está coberto
  aqui. Uma segunda via (cabo USB ou outra rede) ajuda, porque o reinício derruba o adb por alguns segundos.
- PC com Linux, `adb` e **Python 3** (o montador do carrier só usa a biblioteca padrão).
- Este repositório. Os comandos supõem que você está na **raiz do repositório** (a pasta que tem `horizon/` e `docs/`)
  e usam `~/cluster-song` como pasta de trabalho.
- Opcional, para o teste no PC: **PyQt5 5.15** (Qt 5.15, a mesma série do painel) e, para o teste avançado da raiz,
  o `rcc-qt5` (pacote do Qt 5 da sua distribuição).
- Opcional, só se for recompilar a ferramenta de seleção: JDK e Android SDK (o `.dex` pronto fica em
  `horizon/tools/horizon-theme-tool/build/`).

Os comandos são para **bash**. Se o seu terminal for fish ou outro, rode `bash` antes.

```bash
mkdir -p ~/cluster-song/stock ~/cluster-song/meutema ~/cluster-song/out
adb connect <ip-da-multimídia>:5555
adb -s <ip-da-multimídia>:5555 get-state
```

O último comando deve responder `device`.

---

## 1. Conferir o firmware

Tudo nesta seção só **lê** o carro.

### 1.1 Build e tamanho do painel

```bash
adb -s <ip-da-multimídia>:5555 shell getprop ro.build.fingerprint
adb -s <ip-da-multimídia>:5555 shell getprop ro.clu.size
```

Esperado:

- `BYD-AUTO/DiLink3.0/DiLink3.0:10/QKQ1.210910.001/eng.build.20251108.051320:user/release-keys`
- `88` (painel de 8,8"; é o que faz o painel usar o `cluster_theme2.rcc`)

### 1.2 Hashes dos arquivos do painel

```bash
adb -s <ip-da-multimídia>:5555 shell sha256sum /system/lib64/libBydCluster.so /system/lib64/libBydDataSource.so /system/lib64/cluster_theme1.rcc /system/lib64/cluster_theme2.rcc /system/lib64/commonTranslate.rcc
```

Os dois `.rcc` grandes levam alguns segundos. Compare com a tabela:

| Arquivo | SHA-256 esperado |
|---|---|
| `libBydCluster.so` | `9f8a0b269fbee37bad510e8dfbc239b857e00a87663fc9fb7ae39913f86017ca` |
| `libBydDataSource.so` | `46c367844f6dee435485107d83f709f69cb7d7cd9f84bb1cf40c7a38deeb83bf` |
| `cluster_theme1.rcc` | `7ff2bde70effb76ce06b701c161d7600cd8bd4918c7705a58e30507de67ab009` |
| `cluster_theme2.rcc` | `64cda4218526d815036f5f8dce95e6152033e653e58ce061efaf037265bf1795` |
| `commonTranslate.rcc` | `b41bd89a9e3a881c5514992af7033a062cb6207355f809384f0469205d27fcc3` |

**Se qualquer um for diferente, pare aqui.** Este guia não serve para o seu firmware.

### 1.3 Nenhum tema de terceiros instalado

```bash
adb -s <ip-da-multimídia>:5555 shell ls -la /sdcard/.cluster/ /sdcard/.cluster/translate/
```

O normal num carro que nunca recebeu tema é "No such file or directory". Se já existir algum `.rcc` em `translate/`
(de outro projeto, como o DashCast), pare e entenda de onde ele veio antes de seguir.

### 1.4 Conferência completa com a ferramenta (só leitura)

A `HorizonThemeTool` ([código](../horizon/tools/horizon-theme-tool/)) é a ferramenta que seleciona o tema. Ela roda
na multimídia como usuário `shell`, pelo `app_process`. O comando `query` só lê: confere o build, a versão da API e o
SHA-256 de 7 arquivos de sistema, e mostra qual tema está selecionado.

```bash
adb -s <ip-da-multimídia>:5555 push horizon/tools/horizon-theme-tool/build/horizon-theme-tool.dex /data/local/tmp/
adb -s <ip-da-multimídia>:5555 shell sha256sum /data/local/tmp/horizon-theme-tool.dex
sha256sum horizon/tools/horizon-theme-tool/build/horizon-theme-tool.dex
```

Os dois hashes devem ser iguais. (Se o `.dex` não existir na sua cópia, gere com
`horizon/tools/horizon-theme-tool/build.sh`, que precisa de JDK e Android SDK.)

```bash
adb -s <ip-da-multimídia>:5555 shell 'CLASSPATH=/data/local/tmp/horizon-theme-tool.dex app_process / HorizonThemeTool query'
```

Esperado: várias linhas `HORIZON: cohort: ok …`, uma linha `HORIZON: selection: … [FACTORY DEFAULT]` e, no fim,
`HORIZON_RESULT={"status":"SETTLED",…}`. Se aparecer `REFUSED`, leia o motivo e pare.

---

## 2. Copiar os arquivos de fábrica

O montador precisa dos três `.rcc` do **seu** carro (cerca de 258 MB no total).

```bash
adb -s <ip-da-multimídia>:5555 pull /system/lib64/cluster_theme1.rcc ~/cluster-song/stock/
adb -s <ip-da-multimídia>:5555 pull /system/lib64/cluster_theme2.rcc ~/cluster-song/stock/
adb -s <ip-da-multimídia>:5555 pull /system/lib64/commonTranslate.rcc ~/cluster-song/stock/
sha256sum ~/cluster-song/stock/*.rcc
```

Os hashes devem ser os mesmos da tabela 1.2. Guarde uma cópia desses três arquivos em outro lugar; eles são o seu
backup de referência (e não devem ser publicados).

---

## 3. Entender a raiz do painel

### 3.1 Qual arquivo manda no 8,8"

- Os QML do painel não ficam soltos: estão **dentro** dos `.rcc` de `/system/lib64`. O `cluster_theme1.rcc` (raiz
  `qml/cluster.qml`) é o do painel de 12,3". O **8,8" usa o `cluster_theme2.rcc`**, raiz `qml10_25/cluster.qml`.
- O "carrier" é um RCC de tradução que o sistema registra junto com o tema de fábrica. Quando ele contém um arquivo
  com o mesmo nome (alias) de um arquivo de fábrica, **o dele vence**. Foi assim que o Horizon trocou a raiz
  (`qml10_25/cluster.qml`) e o `qml10_25/Center.qml`, comprovado no carro.
- O carrier também precisa levar as **80 entradas de idioma do `commonTranslate.rcc`, byte a byte** (40 `.qm` e 40
  `.ts`, incluindo `pt_BR`). O montador da seção 6 cuida disso e confere.

### 3.2 Coordenadas

A raiz é uma cena de **1920×720 "de projeto"**. No 8,8" ela é reduzida a 2/3 e vira **1280×480 físicos**. Desenhe
tudo em 1920×720; para saber o tamanho no vidro, multiplique por 2/3. Um texto de `pixelSize: 30` aparece com cerca
de 20 px no painel. Na primeira versão do Horizon os textos ficaram pequenos no carro; os menores textos dele hoje
ficam entre 24 e 32 px de projeto.

### 3.3 Ler a raiz de fábrica (só no seu PC)

Para estudar, extraia a raiz com a função de leitura do próprio montador:

```bash
mkdir -p ~/cluster-song/fabrica
python3 -I -c "import sys; sys.path.insert(0, sys.argv[1]); from build_horizon_sx309_88 import rcc_entry; open(sys.argv[3], 'wb').write(rcc_entry(sys.argv[2], 'qml10_25/cluster.qml'))" horizon/tools ~/cluster-song/stock/cluster_theme2.rcc ~/cluster-song/fabrica/cluster.qml
sha256sum ~/cluster-song/fabrica/cluster.qml
grep -n "id:" ~/cluster-song/fabrica/cluster.qml | head -60
```

O hash deve ser `046749d4743eb04de4d9454f548ba970c487610b0c25bbe56c9baa2238750fd8`. É código da BYD: leia, mas não
publique.

### 3.4 Os ids que importam

| id (como está no arquivo) | O que é | z |
|---|---|---|
| `rootItemId` | a raiz, 1920×720, escalada a 2/3 no 8,8" | |
| `bgImageContainerId` | imagens de fundo de fábrica | atrás |
| `alwyasDisplayId` (grafia da BYD) | a camada "sempre visível": marcha (`gearModeId`), modos ECO/SPORT, hora, bússola, temperatura (`headInfoId`), faixa de bateria/combustível/autonomia (`driveRangeInfoContainerId`), ODO (`odoItemId`), potência (`naviPowerId`), velocidade (`naviSpeedId`) e os mostradores criados em tempo de execução | 0 |
| `presentationLoaderId` | a superfície Android (projeção) | -3 |
| `centerContainerId` | o `Center.qml`: menu, páginas de drive info, avisos, telas de carga, ADAS 2D | 3 |
| `adasWindowBackground`, `adas2dNaviWindowBg`, `adasContainerParentId` | a janela compacta do ADAS 2D | 3 |
| `warningLayoutId` | as luzes de alerta (`WarinLightLayout.qml`) | 100 |
| animações de aproximação e de ligar | | 199 e 200 |

O seu tema entra num `Loader` com **z 1**: acima da camada sempre visível e da superfície Android, **abaixo** do
centro (menu e avisos), das luzes de alerta e das animações. Ou seja, menu, avisos e luzes de fábrica continuam por
cima do seu tema, de propósito.

Depois da seção 6, o arquivo `~/cluster-song/out/patches/cluster.qml.diff` mostra exatamente o que foi inserido.

---

## 4. Escrever o tema

### 4.1 Regras da pasta do tema

O montador (seção 6) recebe a pasta do tema com `--horizon`. Ele exige:

- **Só arquivos `.qml` e `.js`**, numa pasta plana. Subpastas são ignoradas; qualquer outro arquivo (imagem, por
  exemplo) faz o montador parar.
- **`HorizonCluster.qml`**: a raiz do seu tema. O patch da raiz carrega exatamente `qrc:/horizon/HorizonCluster.qml`,
  por isso o nome.
- **`HorizonGeometry.js`**: obrigatório para o montador. No exemplo ele guarda as posições.
- Os demais arquivos podem ter qualquer nome. Como ficam na mesma pasta, a raiz enxerga os tipos sem `import`.

Os nomes "Horizon" são só o contrato do montador; o conteúdo é todo seu.

### 4.2 Ambiente

- Qt 5.15 (5.15.10 no painel): use `import QtQuick 2.15`.
- Dados do carro: `import com.byd.DataSource 1.0` (o singleton `DataSource`).
- Fonte do painel: o singleton `CustomStyle` em `import "qrc:/common/singleton"`, como faz o
  [HorizonTheme.qml](../horizon/qml/HorizonTheme.qml).
- O painel também tem `QtGraphicalEffects` e `QtQuick3D 1.15`, mas um tema básico não precisa deles.

### 4.3 Propriedades do DataSource

Todas tiradas de [HorizonData.qml](../horizon/qml/HorizonData.qml), que rodou no carro. Faça como ele: **um único
arquivo lê o `DataSource`** e entrega valores limpos para o resto do tema.

| Informação | Propriedade | Como interpretar |
|---|---|---|
| Velocidade | `speed` | válido de 0 a 300 |
| Unidade | `speedUnit` | `0x02` = mph; senão km/h |
| Limite lido pela câmera | `limitTrafficSymbolValue` | válido de 5 a 150 |
| Marcha | `gearMode` | 1 P, 2 R, 3 N, 4 D |
| Modo de condução | `ecoSportMode` | 1 ECO, 2 SPORT, outro = NORMAL |
| Bateria (SOC) | `powerBatterySoc` | 0 a 100 |
| Autonomia elétrica | `socMile` | 0 a 999 (`0x3E7`) |
| Combustível | `fuelPercent` | 0 a 100 |
| Autonomia a combustão | `fuelMile` | 0 a 999 |
| Potência | `realPower` | kW, negativo regenerando |
| ODO | `odoValue` e `odoIndex` | `odoIndex` 0 = desligado; o valor é o que a faixa de fábrica mostra ali (ODO ou trip, conforme o menu do carro) |
| Temperatura externa | `outsideTemp`, `outsideTempDisplay`, `outsideTempUnit` | `outsideTempDisplay` 0 = não mostrar; `outsideTempUnit` 0 = °C |
| Dia ou noite | `dayNightMode` | `0x01` = dia |
| Carro ligado | `powerGear` | `0x03` = ON |
| Menu nativo aberto | `menuNeedDisplay` | 1 = aberto |
| Consumo dos últimos 50 km | `recent50PowerValue`, `recent50FuelValue` | texto |
| Viagem | `journeyMileText`, `driveTime`, `avgSpeedValue` | `avgSpeedValue` -1 = sem valor |

Atenção: **`evOdo` não existe** neste carro. O Horizon usou essa propriedade na v1 e o painel mostrou "undefined km".
Para o ODO, use `odoValue`/`odoIndex`.

### 4.4 Onde desenhar

- Com o seu tema ativo, as **luzes de alerta** passam para o arranjo que a BYD usa na navegação em tela cheia: uma
  coluna em cada lateral (x < 112 e x > 1752), uma fileira no alto, uma fileira embaixo à esquerda e o "OK" (READY)
  embaixo no centro, em x 900–1020, y 588–648. Não ponha nada importante nessas faixas. Algumas luzes do alto ficam
  mais para dentro; confira no teste da raiz (seção 7.2), que desenha as luzes nas posições reais.
- **Deixe o centro livre.** Menu, avisos e a janela do ADAS 2D de fábrica aparecem ali, por cima do tema. Sem ADAS 3D
  próprio, a janela compacta do ADAS 2D é ampliada e levada para o centro (cerca de x 733–1187, y 246–534).
- **A placa de limite de fábrica (TSR) some** enquanto o tema está ativo (o patch das luzes faz isso, seção 5). Se
  quiser a placa, desenhe com `limitTrafficSymbolValue`, como no exemplo.
- Com o tema ativo, também somem: hora, temperatura, bússola, luz de recuperação de energia, ícones de modo e os
  mostradores de fábrica (todos estão em `alwyasDisplayId`). Desenhe o que você quiser manter.
- Um fundo opaco esconde a superfície Android. Para um tema sem mapa isso é o desejado.

### 4.5 O tema de exemplo

Seis arquivos pequenos, cada um com uma responsabilidade. Crie todos em `~/cluster-song/meutema/`. Resultado no
mock: velocidade no alto com placa de limite e marcha, um cartão de bateria à esquerda, um de combustível à direita,
hora e ODO embaixo, centro livre.

**`HorizonGeometry.js`**: as posições.

```js
.pragma library

// Posições em coordenadas de projeto (1920x720). No 8,8" tudo aparece a 2/3 (1280x480).
// As luzes de alerta ficam nas bordas (x < 112 e x > 1752) e o "OK" (READY) em
// x 900-1020, y 588-648: nada importante nosso vai ali.
var velocidade = { x: 660, y: 10, width: 600, height: 190 }
var cartaoEsquerdo = { x: 150, y: 230, width: 400, height: 280 }
var cartaoDireito = { x: 1340, y: 230, width: 400, height: 280 }
var rodape = { x: 150, y: 600, width: 1590, height: 48 }
```

**`Dados.qml`**: o único arquivo que lê o `DataSource`.

```qml
import QtQuick 2.15
import com.byd.DataSource 1.0

// O único arquivo que lê o DataSource. Faixas e códigos iguais aos de horizon/qml/HorizonData.qml.
QtObject {
    readonly property bool noite: DataSource.dayNightMode !== 0x01
    readonly property int velocidade: DataSource.speed >= 0 && DataSource.speed <= 300 ? DataSource.speed : 0
    readonly property string unidade: DataSource.speedUnit === 0x02 ? "mph" : "km/h"
    readonly property int limite: DataSource.limitTrafficSymbolValue >= 5 && DataSource.limitTrafficSymbolValue <= 150
                                  ? DataSource.limitTrafficSymbolValue : 0
    readonly property string marcha: ["", "P", "R", "N", "D"][DataSource.gearMode] || ""
    readonly property int bateria: DataSource.powerBatterySoc >= 0 && DataSource.powerBatterySoc <= 100
                                   ? DataSource.powerBatterySoc : -1
    readonly property int autonomiaEletrica: DataSource.socMile >= 0 && DataSource.socMile <= 999 ? DataSource.socMile : -1
    readonly property int combustivel: DataSource.fuelPercent >= 0 && DataSource.fuelPercent <= 100
                                       ? DataSource.fuelPercent : -1
    readonly property int autonomiaCombustivel: DataSource.fuelMile >= 0 && DataSource.fuelMile <= 999 ? DataSource.fuelMile : -1
    // O que a faixa de fábrica mostra ali (ODO ou trip, conforme o menu do carro); vazio se desligado.
    readonly property string odometro: DataSource.odoIndex !== 0 ? String(DataSource.odoValue) : ""
}
```

**`Cores.qml`**: cores de dia e de noite e a fonte do painel.

```qml
import QtQuick 2.15
import "qrc:/common/singleton"

// Cores de dia e de noite, e a fonte do próprio cluster (como em horizon/qml/HorizonTheme.qml).
QtObject {
    property bool noite: true
    readonly property color fundo:      noite ? "#05080c" : "#eef2f5"
    readonly property color cartao:     noite ? "#111922" : "#ffffff"
    readonly property color texto:      noite ? "#f2f5f8" : "#10161d"
    readonly property color textoFraco: noite ? "#8a96a3" : "#5b6876"
    readonly property color trilho:     noite ? "#26ffffff" : "#1f000000"
    readonly property color destaque:   noite ? "#5cc8ff" : "#0b6db0"
    readonly property color alerta:     "#ff5252"
    readonly property string fonte: CustomStyle.widthFontFamily || "sans-serif"
}
```

**`Velocidade.qml`**: velocidade, placa de limite e marcha.

```qml
import QtQuick 2.15

// Velocidade no meio, placa de limite à esquerda e marcha à direita.
Item {
    id: bloco
    property Dados dados
    property Cores cores
    readonly property bool acimaDoLimite: dados.limite > 0 && dados.velocidade > dados.limite

    Text {
        id: numero
        anchors { horizontalCenter: parent.horizontalCenter; top: parent.top }
        text: bloco.dados.velocidade
        color: bloco.acimaDoLimite ? bloco.cores.alerta : bloco.cores.texto
        font { family: bloco.cores.fonte; pixelSize: 120 }
    }
    Text {
        anchors { horizontalCenter: numero.horizontalCenter; top: numero.bottom; topMargin: -6 }
        text: bloco.dados.unidade
        color: bloco.cores.textoFraco
        font { family: bloco.cores.fonte; pixelSize: 26 }
    }
    // Com o tema ativo a placa TSR de fábrica some (patch das luzes), então ela é desenhada aqui.
    Rectangle {
        visible: bloco.dados.limite > 0
        anchors { right: numero.left; rightMargin: 48; verticalCenter: numero.verticalCenter }
        width: 76; height: 76; radius: 38
        color: "#ffffff"
        border { color: "#d32f2f"; width: 7 }
        Text {
            anchors.centerIn: parent
            text: bloco.dados.limite
            color: "#111111"
            font { family: bloco.cores.fonte; pixelSize: 30; bold: true }
        }
    }
    Text {
        anchors { left: numero.right; leftMargin: 48; verticalCenter: numero.verticalCenter }
        text: bloco.dados.marcha
        color: bloco.cores.destaque
        font { family: bloco.cores.fonte; pixelSize: 64; bold: true }
    }
}
```

**`Cartao.qml`**: um cartão reutilizável (bateria e combustível).

```qml
import QtQuick 2.15

// Cartão com título, valor grande, uma linha de detalhe e uma barra de 0 a 100 %.
Rectangle {
    id: cartao
    property Cores cores
    property string titulo
    property string valor
    property string detalhe
    property int percentual: -1          // -1 = valor desconhecido: barra vazia
    readonly property bool baixo: percentual >= 0 && percentual <= 15
    radius: 18
    color: cores.cartao

    Column {
        anchors { fill: parent; margins: 28 }
        spacing: 14
        Text {
            text: cartao.titulo
            color: cartao.cores.textoFraco
            font { family: cartao.cores.fonte; pixelSize: 26; letterSpacing: 2 }
        }
        Text {
            text: cartao.valor
            color: cartao.baixo ? cartao.cores.alerta : cartao.cores.texto
            font { family: cartao.cores.fonte; pixelSize: 72 }
        }
        Rectangle {
            width: parent.width; height: 10; radius: 5
            color: cartao.cores.trilho
            Rectangle {
                width: cartao.percentual < 0 ? 0 : parent.width * cartao.percentual / 100
                height: parent.height; radius: 5
                color: cartao.baixo ? cartao.cores.alerta : cartao.cores.destaque
            }
        }
        Text {
            text: cartao.detalhe
            color: cartao.cores.textoFraco
            font { family: cartao.cores.fonte; pixelSize: 28 }
        }
    }
}
```

**`HorizonCluster.qml`**: a raiz do tema, que junta as peças.

```qml
import QtQuick 2.15
import "HorizonGeometry.js" as Layout

// Raiz do tema. O montador carrega exatamente qrc:/horizon/HorizonCluster.qml, por isso o nome.
// As luzes de alerta, o menu e os avisos de fábrica continuam por cima deste item.
Item {
    id: tema
    width: 1920
    height: 720

    // Lido pelo patch da raiz: false = este tema não tem ADAS 3D, a janela ADAS 2D de fábrica fica.
    readonly property bool adasReady: false
    property string hora: "--:--"

    Dados { id: dados }
    Cores { id: cores; noite: dados.noite }

    Timer {
        interval: 1000; running: true; repeat: true; triggeredOnStart: true
        onTriggered: tema.hora = Qt.formatTime(new Date(), "hh:mm")
    }

    Rectangle { anchors.fill: parent; color: cores.fundo }

    Velocidade {
        dados: dados; cores: cores
        x: Layout.velocidade.x; y: Layout.velocidade.y
        width: Layout.velocidade.width; height: Layout.velocidade.height
    }

    Cartao {
        cores: cores
        x: Layout.cartaoEsquerdo.x; y: Layout.cartaoEsquerdo.y
        width: Layout.cartaoEsquerdo.width; height: Layout.cartaoEsquerdo.height
        titulo: "BATERIA"
        valor: dados.bateria < 0 ? "--" : dados.bateria + "%"
        percentual: dados.bateria
        detalhe: (dados.autonomiaEletrica < 0 ? "--" : dados.autonomiaEletrica) + " km elétrico"
    }

    Cartao {
        cores: cores
        x: Layout.cartaoDireito.x; y: Layout.cartaoDireito.y
        width: Layout.cartaoDireito.width; height: Layout.cartaoDireito.height
        titulo: "COMBUSTÍVEL"
        valor: dados.combustivel < 0 ? "--" : dados.combustivel + "%"
        percentual: dados.combustivel
        detalhe: (dados.autonomiaCombustivel < 0 ? "--" : dados.autonomiaCombustivel) + " km a combustão"
    }

    Item {
        x: Layout.rodape.x; y: Layout.rodape.y
        width: Layout.rodape.width; height: Layout.rodape.height
        Text {
            anchors { left: parent.left; verticalCenter: parent.verticalCenter }
            text: tema.hora
            color: cores.texto
            font { family: cores.fonte; pixelSize: 32 }
        }
        Text {
            anchors { right: parent.right; verticalCenter: parent.verticalCenter }
            text: dados.odometro !== "" ? "ODO " + dados.odometro + " km" : ""
            color: cores.textoFraco
            font { family: cores.fonte; pixelSize: 28 }
        }
    }
}
```

A propriedade `adasReady: false` diz ao patch da raiz que o seu tema **não** desenha o ADAS. Com isso, os painéis de
ADAS 2D de fábrica continuam valendo (é um aviso de segurança; não os esconda num tema básico).

### 4.6 Armadilhas de QML que já custaram uma versão

- **Máscara feita com `Canvas` escondido não funciona no carro.** Um `Canvas` invisível usado como fonte de
  `OpacityMask` nunca é pintado dentro da raiz de fábrica (no PC funciona, por isso engana). Para recortes e
  degradês de borda, calcule num `ShaderEffect`, como em [SoftMask.qml](../horizon/qml/SoftMask.qml).
- **Propriedade que não existe vira `undefined`**, sem erro visível no painel (caso do `evOdo`). Teste cada valor no
  mock e no carro.
- **Nada de logs do QML pelo adb.** O renderizador do painel roda no host, fora da parte Android; os erros do seu QML
  não aparecem no `logcat`. O teste no PC (seção 7) é a sua única tela de erro.

---

## 5. O patch na raiz e o fail-open

Você **não precisa editar Python** para um tema básico: o montador
[build_horizon_sx309_88.py](../horizon/tools/build_horizon_sx309_88.py) aplica os patches na raiz de fábrica e
empacota a sua pasta. Mas entenda o que ele faz, porque é isso que vai rodar no seu painel.

### 5.1 O Loader e as condições

As inserções vão entre marcas `// HORIZON_BEGIN` e `// HORIZON_END`, em bytes (preservando BOM e quebras de linha
CRLF do original). O montador confere que, tirando as marcas, o arquivo volta **exatamente** ao de fábrica; se não
voltar, ele para. Na raiz entram três coisas:

1. **Quando o tema deve rodar** (`horizonWanted`): painel de 8,8" (`screenSize === 0x01`), raiz 1920×720, carro em ON
   (`powerGear === 0x03`), animação de ligar concluída (`playAnim`) e as camadas de fábrica visíveis. Em ACC, ou
   antes da animação terminar, fica o painel de fábrica.
2. **O Loader** do tema, logo antes do `presentationLoaderId`:

   ```qml
   Loader {
       id: horizonLoader
       z: 1
       active: rootItemId.horizonWanted
       asynchronous: true
       source: "qrc:/horizon/HorizonCluster.qml"
       visible: rootItemId.horizonActive
   }
   ```

3. **O fail-open**: `horizonActive` só é verdadeiro com `horizonWanted` **e** o Loader em `Loader.Ready` com um item
   criado. Só então `bgImageContainerId` e `alwyasDisplayId` recebem `opacity: 0`. Se o seu QML tiver erro de sintaxe
   ou de import, o Loader não fica pronto e **nada de fábrica é escondido**: o painel continua o original.

O fail-open foi comprovado no teste da raiz com o tema deste guia propositalmente quebrado: `horizonActive` ficou
falso e o painel de fábrica apareceu inteiro. Ele **não** protege contra erro na própria raiz patcheada; por isso as
inserções são pequenas e conferidas pelo montador.

### 5.2 O que mais o montador muda (vale para qualquer tema)

O montador foi feito para o Horizon e aplica os mesmos patches a qualquer pasta que você passar. Todos dependem de
`horizonActive`; sem o tema ativo, tudo fica como na fábrica.

| Arquivo no carrier | Mudança | Efeito no seu tema básico |
|---|---|---|
| `qml10_25/cluster.qml` | Loader z 1, fail-open, `opacity` nas camadas de fábrica | o seu tema aparece no lugar da tela principal |
| `qml10_25/cluster.qml` | janela compacta do ADAS 2D | com `adasReady: false` ela fica, ampliada, no centro |
| `qml10_25/WarinLightLayout.qml` | arranjo de navegação das luzes; placa TSR escondida | luzes nas bordas; desenhe a sua placa de limite |
| `qml10_25/Center.qml` | páginas de drive info e fluxo de energia cedem ao tema | sem isso o fluxo de energia (z 3) apareceria por cima do tema (aconteceu na v1 do Horizon). Menu, avisos e telas de carga não mudam |
| `qml10_25/adas3d/components/Model3D.qml` | os 6 `Loader3D` dos alvos ficam síncronos | só importa para quem usa o ADAS 3D: assíncronos, eles derrubavam o renderizador a cada 1–3 min |
| `qml10_25/adas3d/Adas3dContainer.qml` | faixas, alvos e fundo do 3D | só é instanciado pelo Horizon; não afeta o seu |
| `qml/cluster.qml` | nenhuma (idêntico ao de fábrica) | o 8,8" não usa |
| 80 entradas de `common/language/` | nenhuma (byte a byte) | |

A opção `--speed-fix` dá posição ao `naviSpeedId` de fábrica (corrige o "m/h" no canto do modo mapa de fábrica). Não é
necessária para o seu tema e vem desligada.

Se quiser um caminho ou nome de arquivo diferente de `horizon/HorizonCluster.qml`, é preciso mudar no montador o
`source` do Loader (`ROOT_LOADER`) e a função `horizon_files`. Isso não foi testado; para o básico, mantenha os nomes.

---

## 6. Montar o carrier

```bash
python3 -I horizon/tools/build_horizon_sx309_88.py --stock ~/cluster-song/stock --horizon ~/cluster-song/meutema --out ~/cluster-song/out
```

Saída esperada, numa linha: `BUILT OFFLINE_ONLY_horizon_sx309_88.rcc: 92 entries, … bytes, SHA-256 …`. Com o tema
de exemplo deste guia foram 92 entradas (80 idiomas, 6 QML de fábrica, 6 arquivos do tema) e cerca de 2,4 MB; os
números mudam com o seu tema. Antes de gravar, o montador:

- recusa se o SHA-256 dos três `.rcc` ou de qualquer QML de fábrica usado não for o do SX309;
- recusa se o `commonTranslate.rcc` não tiver as 80 entradas de idioma;
- confere que os patches são reversíveis e que as 80 entradas saem idênticas;
- recusa carrier com 4 MiB ou mais.

**Ele não confere o seu QML.** Um tema com erro de sintaxe monta normalmente; quem pega isso é o teste da seção 7.

Os QML vão sem compressão (flag 0) dentro do carrier, então as mesmas entradas geram o mesmo arquivo em qualquer
máquina. Confira com `--check`, que remonta e exige bytes idênticos:

```bash
python3 -I horizon/tools/build_horizon_sx309_88.py --stock ~/cluster-song/stock --horizon ~/cluster-song/meutema --out ~/cluster-song/out --check
```

A pasta `~/cluster-song/out/` fica com:

| Arquivo | Para quê |
|---|---|
| `OFFLINE_ONLY_horizon_sx309_88.rcc` | o carrier (o nome "OFFLINE_ONLY" impede que ele seja selecionado por engano) |
| `manifest.json`, `SHA256SUMS` | hashes de tudo |
| `patched/qml10_25/` | a raiz, as luzes e o `Center.qml` já patcheados |
| `patches/*.diff` | o que mudou em cada QML de fábrica |

### 6.1 O nome que o painel aceita

O sistema só considera arquivos com o formato `Di3F_DynastyOceanTranslate_V0001_Translate<Nome>.rcc`. A
`HorizonThemeTool` é mais restrita e só aceita **`Di3F_DynastyOceanTranslate_V0001_TranslateHorizon_<32 hex>.rcc`**,
em que os 32 hex são o começo do SHA-256 do carrier (nome e conteúdo amarrados). Use esse formato; se mudar a parte
`Horizon`, a ferramenta recusa (o padrão fica em `NAME_PREFIX` e `NAME` de
[Policy.java](../horizon/tools/horizon-theme-tool/src/Policy.java), e exigiria recompilar).

Calcule o hash e o nome **no mesmo terminal** que você vai usar na seção 8:

```bash
SHA=$(sha256sum ~/cluster-song/out/OFFLINE_ONLY_horizon_sx309_88.rcc | cut -d' ' -f1)
NOME=Di3F_DynastyOceanTranslate_V0001_TranslateHorizon_$(echo $SHA | cut -c1-32).rcc
echo $SHA
echo $NOME
```

Anote os dois valores. Se fechar o terminal, rode esses quatro comandos de novo.

O `install.sh` da ferramenta imprime uma sequência parecida, mas foi escrito antes dos testes no carro: o passo de
`restore` dele não funciona neste firmware e falta o reinício depois do `load`. Siga a seção 8.

---

## 7. Testar no PC

### 7.1 O tema sozinho, com dados simulados

O [render.py](../horizon/mock/render.py) desenha um QML com um `DataSource` falso, gerado a partir de um cenário JSON,
e salva um PNG 1920×720. Instale o PyQt5 num ambiente separado:

```bash
python3 -m venv ~/cluster-song/venv
~/cluster-song/venv/bin/pip install "PyQt5>=5.15,<5.16"
```

Renderize de noite e de dia:

```bash
QT_QPA_PLATFORM=offscreen QT_QUICK_BACKEND=software ~/cluster-song/venv/bin/python -I horizon/mock/render.py ~/cluster-song/meutema/HorizonCluster.qml horizon/mock/scenarios/night_instruments.json ~/cluster-song/out/noite.png
QT_QPA_PLATFORM=offscreen QT_QUICK_BACKEND=software ~/cluster-song/venv/bin/python -I horizon/mock/render.py ~/cluster-song/meutema/HorizonCluster.qml horizon/mock/scenarios/day_map.json ~/cluster-song/out/dia.png
```

Cada comando termina com `saved … 1920x720`. Se o QML tiver erro, o script para e mostra arquivo, linha e mensagem.
Abra os PNGs. Para ver o tamanho real no vidro, reduza a imagem para 1280×480.

Os valores simulados ficam em `horizon/mock/scenarios/base.json` (velocidade 62, limite 60, marcha D, bateria 54 %,
ODO 20959…). Para testar outros casos, copie `base.json` para uma pasta sua e crie cenários ao lado dele, por
exemplo `bateria_baixa.json` com `{"extends": ["base.json"], "datasource": {"powerBatterySoc": 9, "speed": 0}}`.

### 7.2 A raiz de fábrica com o seu carrier (avançado)

Este teste carrega a **raiz patcheada** de dentro do carrier, sobre o `cluster_theme2.rcc` de fábrica, com as luzes de
alerta nas posições reais. Precisa do `rcc-qt5` instalado.

```bash
STOCK2=~/cluster-song/stock/cluster_theme2.rcc QMLDIR=~/cluster-song/out/patched PYQT=~/cluster-song/venv/bin/python sh horizon/mock/root_test/run.sh ~/cluster-song/out/OFFLINE_ONLY_horizon_sx309_88.rcc ~/cluster-song/out/raiz.png night_instruments --stub-3d
```

Esperado: uma linha `horizonWanted True horizonActive True horizonAdas3d False` e um `saved …`. Os avisos
`Error: Cannot assign [undefined] to QString` em `cluster.qml` também aparecem sem carrier nenhum: vêm dos dados
simulados, não do seu tema. Para comparar com o painel de fábrica, troque o caminho do carrier por `none`.

Para provar o fail-open com o seu tema: copie a pasta do tema, estrague de propósito o `HorizonCluster.qml`, monte
com `--out` noutra pasta e rode o teste. O resultado deve ser `horizonActive False` e o painel de fábrica na imagem.

### 7.3 O que o PC não cobre

O Qt do painel (5.15.10 da BYD), o `Center` real (menu, avisos), as luzes nativas (no PC são desenhos simples), as
fontes reais e o desempenho. A conferência final é no carro.

---

## 8. Colocar no carro, selecionar e reiniciar

Antes: carro em P, freio de mão, ligado (ON), sem outro teste de painel rodando. A ferramenta já está em
`/data/local/tmp/` (seção 1.4) e as variáveis `SHA` e `NOME` estão definidas no terminal (seção 6.1).

### 8.1 Na primeira vez: ensaie o reinício com o painel normal

Antes de carregar qualquer tema, faça **um** reinício do host com o painel de fábrica, para conhecer o processo:

```bash
adb -s <ip-da-multimídia>:5555 reboot fission-all
```

No carro do autor, o adb caiu uns 11 s depois do comando e voltou sozinho uns 80 s depois; o painel mostrou
"Restarting" e voltou normal. Confira com `adb -s <ip-da-multimídia>:5555 get-state` até responder `device`. **Se o
painel não voltar normal, pare: sem esse caminho funcionando não se carrega tema.**

### 8.2 Enviar o carrier

Primeiro para uma pasta fora da pasta de busca, depois um `mv` (renomear dentro do mesmo armazenamento é atômico):

```bash
adb -s <ip-da-multimídia>:5555 shell mkdir -p /sdcard/.cluster/translate /sdcard/.cluster/horizon /sdcard/.cluster/retired
adb -s <ip-da-multimídia>:5555 push ~/cluster-song/out/OFFLINE_ONLY_horizon_sx309_88.rcc /sdcard/.cluster/horizon/.incoming.rcc
adb -s <ip-da-multimídia>:5555 shell sha256sum /sdcard/.cluster/horizon/.incoming.rcc
```

O hash deve ser igual ao `$SHA`. Então:

```bash
adb -s <ip-da-multimídia>:5555 shell mv /sdcard/.cluster/horizon/.incoming.rcc /sdcard/.cluster/translate/$NOME
adb -s <ip-da-multimídia>:5555 shell ls -la /sdcard/.cluster/translate/
```

Deve haver **exatamente um** `.rcc` em `translate/`. O sistema recusa a pasta vazia (`0x232a`) ou com mais de um
arquivo (`0x232b`).

### 8.3 Selecionar

```bash
adb -s <ip-da-multimídia>:5555 shell "CLASSPATH=/data/local/tmp/horizon-theme-tool.dex app_process / HorizonThemeTool load $NOME --sha256 $SHA --assert-parked"
```

As aspas duplas fazem o seu terminal colocar os valores de `$NOME` e `$SHA` no comando. `--assert-parked` é a sua
declaração de que o carro está parado em P (a ferramenta não consegue ler a marcha).

A ferramenta confere tudo de novo (leva alguns segundos para calcular o hash dos dois `.rcc` grandes), envia **um**
pedido e consulta a seleção. **Neste firmware o resultado normal é:**

- uma linha `HORIZON: callback ACK4 config …`;
- `HORIZON: AMBIGUOUS: selected=true ack2=false. Do NOT repeat. …`;
- `HORIZON_RESULT={"status":"AMBIGUOUS","detail":"selected=true ack2=false"}`, código de saída 2.

O `load` só **grava** a escolha; o painel não muda ainda. Não repita o comando. Se aparecer `selected=false` ou uma
linha `ACK3 FAILURE`, pare e leia o código de erro (seção 10).

Confira a seleção:

```bash
adb -s <ip-da-multimídia>:5555 shell 'CLASSPATH=/data/local/tmp/horizon-theme-tool.dex app_process / HorizonThemeTool query'
```

A linha `selection:` deve terminar com `[HORIZON CARRIER]` e mostrar o caminho do seu arquivo.

### 8.4 Reiniciar e olhar o painel

```bash
adb -s <ip-da-multimídia>:5555 reboot fission-all
```

**Um** comando. Espere o adb voltar sozinho (nas medições, de 50 a 80 s) e o painel terminar a animação de ligar. O
host lê a escolha salva e o tema aparece com o carro em ON. Confira, parado:

- [ ] velocidade, marcha, bateria, autonomia, combustível, ODO e hora corretos;
- [ ] todas as luzes de alerta presentes, incluindo o "OK" (READY);
- [ ] o menu do volante abre e fecha; avisos aparecem por cima do tema;
- [ ] nada importante coberto pelas luzes ou pela janela do ADAS.

Com o carrier carregado, **não troque de tema pelo menu do painel** (os dois temas do menu são estilos de fábrica;
essa troca não foi testada).

Qualquer coisa estranha (tela preta, instrumentos faltando, "Simple mode", painel piscando): vá direto para a seção 9.

---

## 9. Voltar ao original e trocar de versão

### 9.1 Voltar ao painel de fábrica

O comando `restore` da ferramenta **não funciona** neste firmware: o sistema responde `RESOURCE_IS_SAME` (`0x2330`) e
a seleção continua no seu arquivo. A volta comprovada no carro é tirar o arquivo da pasta e reiniciar **uma** vez: na
partida, o host não encontra o arquivo selecionado e volta sozinho ao padrão.

```bash
adb -s <ip-da-multimídia>:5555 shell ls /sdcard/.cluster/translate/
adb -s <ip-da-multimídia>:5555 shell mv /sdcard/.cluster/translate/$NOME /sdcard/.cluster/retired/$NOME
adb -s <ip-da-multimídia>:5555 shell ls -la /sdcard/.cluster/translate/ /sdcard/.cluster/retired/
adb -s <ip-da-multimídia>:5555 reboot fission-all
```

Se `$NOME` não estiver mais definido, copie o nome que o primeiro `ls` mostrar. Mova **antes** de reiniciar: mover
não muda o painel na hora, e reiniciar com o arquivo ainda lá repete o problema.

Depois que o adb voltar:

```bash
adb -s <ip-da-multimídia>:5555 shell 'CLASSPATH=/data/local/tmp/horizon-theme-tool.dex app_process / HorizonThemeTool query'
```

Esperado: `[FACTORY DEFAULT]`, e o painel de fábrica no vidro.

**Plano C**, se nem isso devolver o painel: não fique reiniciando em loop. O que recuperou o autor do DashCast numa
falha antiga foi o Country Code Tool da BYD na multimídia (ele mexe na configuração de país; use só ciente disso e
uma vez) e, depois disso, a concessionária. Detalhes em [RECUPERACAO.md](../horizon/RECUPERACAO.md) §7.

### 9.2 Trocar de versão (um reinício)

Gere a versão nova (seções 6 e 7), defina `SHA` e `NOME` dela e, com o antigo nome em mãos:

```bash
adb -s <ip-da-multimídia>:5555 shell mv /sdcard/.cluster/translate/<nome-antigo>.rcc /sdcard/.cluster/retired/
adb -s <ip-da-multimídia>:5555 push ~/cluster-song/out/OFFLINE_ONLY_horizon_sx309_88.rcc /sdcard/.cluster/horizon/.incoming.rcc
adb -s <ip-da-multimídia>:5555 shell sha256sum /sdcard/.cluster/horizon/.incoming.rcc
adb -s <ip-da-multimídia>:5555 shell mv /sdcard/.cluster/horizon/.incoming.rcc /sdcard/.cluster/translate/$NOME
adb -s <ip-da-multimídia>:5555 shell ls -la /sdcard/.cluster/translate/
adb -s <ip-da-multimídia>:5555 shell "CLASSPATH=/data/local/tmp/horizon-theme-tool.dex app_process / HorizonThemeTool load $NOME --sha256 $SHA --assert-parked --replace-own --ack-ambiguous"
adb -s <ip-da-multimídia>:5555 reboot fission-all
```

- `--replace-own` permite selecionar por cima de outro carrier `TranslateHorizon_` (sem ele a ferramenta exige o
  padrão de fábrica como ponto de partida).
- `--ack-ambiguous` é necessário porque o `load` anterior terminou `AMBIGUOUS`. Use só depois de olhar o painel e
  rodar `query`.
- A ferramenta exige 16 s entre dois comandos que mudam a seleção.
- Nunca traga de volta de `retired/` uma versão que falhou.

---

## 10. Erros comuns

| Sintoma | Causa provável | O que fazer |
|---|---|---|
| Ferramenta: `REFUSED: firmware is not the SX309 cohort: …` | firmware diferente | parar; este guia não serve para ele |
| Montador: `… SHA-256 … is not the SX309 cohort … refusing` | `.rcc` de outro firmware ou cópia incompleta | refazer o `pull` e conferir os hashes (seção 2) |
| Montador: `unexpected file in the Horizon directory (only .qml/.js)` | imagem ou outro arquivo na pasta do tema | deixar só `.qml` e `.js` |
| Montador: `missing horizon/HorizonCluster.qml` (ou `HorizonGeometry.js`) | nome de arquivo obrigatório faltando | seção 4.1 |
| Montador: `carrier size … outside the recovery bound` | carrier com 4 MiB ou mais | enxugar o tema |
| Ferramenta: `basename must match …` | nome do arquivo fora do padrão | seção 6.1 |
| Ferramenta: `N .rcc files in /sdcard/.cluster/translate/` | zero ou mais de um arquivo na pasta | mover os extras para `retired/` |
| Ferramenta: `baseline is not the factory default` | já há um tema selecionado | se for um carrier seu, `--replace-own` (9.2); se for de outro projeto, não mexer |
| Ferramenta: `the previous run (…) did not settle` | o último `load` terminou `AMBIGUOUS` (normal aqui) | olhar o painel, `query`, e só então `--ack-ambiguous` |
| Ferramenta: `last mutation was … s ago` | dois pedidos em menos de 16 s | esperar |
| `ACK3 FAILURE … 0x2332 GEAR_IS_NOT_P_AND_NOT_INVALID` | carro fora de P | colocar em P |
| `restore` responde `0x2330 RESOURCE_IS_SAME` | comportamento deste firmware | usar a seção 9.1 |
| O painel não mudou depois do `load` | o `load` só grava a escolha | `adb reboot fission-all` (8.4) |
| Depois do reinício o painel continua de fábrica | carro em ACC ou animação de ligar não concluída; ou erro no seu QML (fail-open) | ligar o carro; se continuar, rodar o teste da seção 7, corrigir e trocar de versão (9.2) |
| Tela preta, instrumentos sumidos, "Simple mode" | raiz quebrada ou tema com erro grave | seção 9.1 imediatamente; não recarregar o mesmo arquivo |
| Um valor aparece como "undefined" | propriedade que não existe (como `evOdo`) | usar só as da tabela 4.3 |
| Recorte ou degradê aparece no PC mas não no carro | `Canvas` escondido usado como máscara | `ShaderEffect` (4.6) |
| Fluxo de energia ou drive info por cima do tema | montador sem o patch do `Center.qml` | usar o montador como está (5.2) |
| Painel volta ao de fábrica por ~2 s com "ELKA OFF"; no `adb logcat -d` aparece `AutoContainerServiceNative died` | ADAS 3D com os `Loader3D` assíncronos de fábrica | só acontece se você usar o ADAS 3D; o montador já os deixa síncronos |
| Janela do ADAS no meio do tema | esperado com `adasReady: false` | deixar o centro livre |
| A placa de limite sumiu | o patch das luzes esconde a placa TSR com o tema ativo | desenhar a sua (exemplo em `Velocidade.qml`) |

---

## 11. Créditos e licenças

- **DashCast**, de Cedric Carre (licença MIT, https://github.com/Kiroha/byd-dashcast). Dele vêm o codec de RCC usado
  pelo montador ([third_party/dashcast](../horizon/tools/third_party/dashcast/), sem alterações), o protocolo de
  seleção pelo `AutoContainer` que a `HorizonThemeTool` implementa e a ideia de recuperar movendo o arquivo e
  reiniciando o host.
- **open-headunit** (licença AGPL-3.0), este repositório, derivado do headunit original de Michael Reid. O tema
  Horizon, o montador, a ferramenta de seleção, o mock e este guia fazem parte dele.
- Os arquivos de fábrica (`.rcc`, `.so` e os QML dentro deles) são da BYD. Este guia cita só nomes, caminhos, ids e
  hashes; não redistribua esses arquivos nem carriers montados com eles.
