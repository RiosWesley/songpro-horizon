# Estudo: tema de cluster (.rcc) do DashCast → cluster 8,8" (1280×480) do Song Pro

Data: 2026-10-31. **Somente estudo**: nada foi gravado no carro, nenhum tema selecionado/carregado,
nenhum `service call AutoContainer`, broadcast `com.byd.cluster.spi` ou reboot. No carro só rodei
leituras (`getprop`, `sha256sum`, `ls`, `dumpsys display`, `logcat -d`, `adb pull` de arquivos de `/system`).
Artefatos puxados ficam no scratchpad (`.../scratchpad/stock/*.rcc`, `libs/*.so`, `ex_cluster_theme{1,2}/` com os QML extraídos).

## 0. Resumo da conclusão

> **Atualização Rodada 2 (final do arquivo):** 8,8" = `cluster_theme2` confirmado (sysfs `clu_size`=88 → `g_ro_clu_size`=1); o "m/h" vem de `naviSpeedId` sem x/y (posição 0,0 clipada) e é corrigível por carrier; mapa de geometria medido; candidato local gerado (não carregar).
>
> **Atualização Rodada 3 (final do arquivo):** prévias em `estudos/previas-temas.png`; ADAS 3D documentado (comentado em `Center.qml`, só liga com `adasType===1`); contrato de teclas/mensagens extraído do metaobject (`estudos/rodada3/moc_enums.json`).

- **O mecanismo é o mesmo** no carro do usuário (confirmado por strings e disassembly de `libBydDataSource.so`), **mas nenhum
  hash de entrada bate** com o coorte que o DashCast conhece. O DashCast se recusa a funcionar aqui, e deve recusar:
  precisa de um coorte novo (builder + perfil + hashes próprios), não de "afrouxar" hashes.
- **O DashCast nunca validou o "native dashboard" em carro** (release notes 1.9.81/1.9.82: "has not been validated on a vehicle"),
  nem a recuperação de QML quebrado. Ou seja, o usuário seria a primeira validação, mesmo no Seal.
- **Boa notícia de geometria:** o QML do 8,8" é a mesma cena 1920×720 escalada por 2/3 (`scale: screenSize===1 ? 2/3 : 1`).
  Todas as coordenadas de projeto continuam em 1920×720; o mapa nativo da tela é 1280×480.
- **Estado atual do carro é limpo:** `/sdcard/.cluster` não existe (nenhum carrier DashCast no disco).

## 1. Como o DashCast carrega o tema e como recupera

### 1.1 Formato e carrier
- Não é tema de launcher/overlay de APK. É um **RCC binário Qt (formato 3)**: magic `qres`, inteiros big-endian, nomes UTF-16BE,
  arquivos zlib (`flags=1`, 4 bytes de tamanho + zlib). Codec próprio em `tools/cluster/build_live_marker.py` (`decode`/`build`).
  `rcc --binary --format-version 3` do Qt serve para fixture, mas o DashCast **preserva os registros crus** do `commonTranslate.rcc` de fábrica
  (todas as línguas `.qm`/`.ts`) e adiciona só os QML (`CLUSTER_CUSTOM_THEME_DEVELOPER_GUIDE.md` §5, §8).
- O carrier é um "translate RCC": contém os aliases `qml10_25/cluster.qml` (e `qml/cluster.qml`), `qml10_25/WarinLightLayout.qml` e
  recursos próprios (`dashcast/DashCastDashboard.qml`, `DashboardLayout.js`) que sombreiam os do `cluster_theme2.rcc` (§1.2 de `CLUSTER_NATIVE_DASHBOARD.md`, `build_full_dashboard.py`).
  Aviso do próprio guia: alias duplicado **não prova** que o novo arquivo vence a busca; ordem de registro importa.
- Nome obrigatório (regex em `ClusterThemePolicy.kt`): `Di3F_DynastyOceanTranslate_V0001_TranslateDashCast_[0-9a-f]{32}.rcc`.
  O nativo faz match com `(Di3F_.*_.*)_Translate(.*).rcc` e compara a versão `Di3F_DynastyOceanTranslate_V0001` — **as duas strings existem
  na `libBydDataSource.so` do usuário** (`strings`). Limite do carrier no DashCast: < 4 MiB.

### 1.2 Onde é gravado
| Visão | Caminho |
|---|---|
| Android (alias) | `/sdcard/.cluster/translate/` |
| Host nativo (o que o Qt lê) | `/data/cells/cell2-rw/data/media/0/.cluster/translate/` (string presente na lib do usuário) |
| Config persistente | `/collect2/byd_datasource_config.xml`, chave `translate_rcc_path` (item de config `0x38`) |
| Entradas de fábrica | `/system/lib64/cluster_theme1.rcc`, `cluster_theme2.rcc`, `commonTranslate.rcc` |

No carro do usuário: de dentro da cell Android, `/data/cells` **não é visível** (shell uid 2000) e `/sdcard/.cluster` não existe.
O DashCast cria o diretório pelo lado Android (daemon como shell), com checagem de inode/dispositivo/pai.

### 1.3 Qual chamada seleciona
- Binder `AutoContainer` (`android.os.IAutoContainer`, pacote `com.xdja.containerservice`, uid 1000), transação 3 (send), tipo 8, byte array FlatBuffer
  de 13 slots; callback registrado antes pela transação 4.
  - Query: cmd 1, sub 4. **Carregar**: cmd 5, sub 0, mode 0, string slot 7 = **só o basename**. **Restaurar padrão**: cmd 5, sub 0, mode 1.
  - Erros reconhecidos: `0x232c NOT_RCC`, `0x232d UI_VERSION_MISMATCH`, `0x232e RESOURCE_REGISTER_FAILED`, `0x2330 RESOURCE_IS_SAME`, `0x2331 RESOURCE_NOT_FOUND`.
- Seleção ≠ aplicação: o nativo grava a escolha e notifica; o reread do QML ocorre em outro caminho (`updateThemeRcc`, timer `cluSizeCheck`).
  Há um espaçamento de 15,5 s entre trocas e 1200 ms entre comandos nativos de projeção (31→16→35 abrir; 18→0→31 fechar).
- Código: `ClusterThemeProtocol.kt`, `ClusterThemeIviHelper.kt`, `ClusterThemeIviExchange.kt`, `ClusterThemeLiveTest.kt`.
- **Eu não enviei nada disso.**

### 1.4 Persistência e recuperação (o que sobrevive)
Evidência em `CLUSTER_GUIDED_RECOVERY.md` (Ghidra no `02dfc7f5…`) e **reconfirmada nas strings do `46c36784…` do usuário**
(`[updateTranslateRccFromXml] translate rcc file not exists`, `translateRccPathFromXml is %s`, `translateRccPath in configFile is empty`,
`newRccPath match fail`, `theme rcc path switch < 15s, return`):

| Situação | Efeito |
|---|---|
| Seleção persiste em `byd_datasource_config.xml` | Sobrevive a reboot Android e a reset da multimídia (é config do host/nativo, não da cell) |
| **Arquivo do carrier ausente** no próximo start do DataSource | O host volta ao padrão (reescreve a config com o default). É o **caminho de recuperação real** |
| Reboot Android simples (`reboot`) | **Não** reinicia o DataSource do host; no teste do dono do DashCast **não** recuperou o cluster |
| `adb reboot fission-all` | Reinicia host+cell; testado **uma vez**, partindo do estado normal: voltou com "Restarting" + instrumentos BYD normais. **Não** testado a partir de QML quebrado |
| Restore de fábrica (cmd 5/0/mode 1) | Volta a seleção ao default se o nativo ainda responde; se o QML quebrou a raiz, o nativo pode nem responder |
| Mover o carrier para `.dashcast-retired-<hex>/` + `reboot fission-all` | Fluxo guiado do DashCast 1.9.80+ (§13–16 do guia). "Simple mode running" = cluster em fallback; só sai com o arquivo fora + restart do host |
| Country Code Tool (BYD) | Foi o que recuperou o dono na falha histórica (`PowerManager.reboot("fission-all")` por uid 1000). Não é replicável por nós |

Pontos críticos para o usuário:
1. O **arquivo** é o que mantém o tema vivo; remover/mover o `.rcc` + reiniciar o host é o rollback.
2. Mover o arquivo **não desmapeia** um RCC já registrado: o quadro atual pode não mudar até o restart do host.
3. `shell` e DashCast **não** têm `android.permission.REBOOT`; o restart sai por **serviço ADB `reboot:fission-all`** (autoridade do adbd), não por Binder.
4. O dono do DashCast já perdeu o cluster por ~3 h com um full-app (`full-app-21f7735c`, hoje "recovery-only").

## 2. O que muda no carro do usuário (evidência medida)

### 2.1 Identidade
`getprop`: `BYD-AUTO/DiLink3.0/DiLink3.0:10/QKQ1.210910.001/eng.build.20251108.051320`, `fission_single_os=0`, `ro.fission.mode=cell`,
`persist.sys.gbClientVersion=dilink3.0_6125f_ivi_cluster_mp0712_dev.202507160`. Tela central 1920×1080.

| Arquivo `/system` | Usuário (SX309) | DashCast exige |
|---|---|---|
| `lib64/libBydCluster.so` | `9f8a0b26…17ca` | `21f7735c…` |
| `lib64/libBydDataSource.so` | `46c36784…83bf` | `02dfc7f5…` |
| `lib64/cluster_theme1.rcc` (132 074 237 B) | `7ff2bde7…b009` | `83dcdb5b…` |
| `lib64/cluster_theme2.rcc` (124 110 471 B) | `64cda421…1795` | `0041e6b8…` |
| `lib64/commonTranslate.rcc` (1 973 616 B) | `b41bd89a…27fcc3` | `1dfee5a0…` |
| `bin/fissiond` | `31d39dfc…fd93e` | **igual** `31d39dfc…` |

Hashes do `.rcc` conferem com os do carro (`sha256sum` no PC = `sha256sum` no device).
Ainda diferente: `commonTranslate` tem **80** entradas (40 `.qm` + 40 `.ts`, em vez de 84/42); inclui `pt_BR`.
Os asserts `len(rows)==84` do DashCast quebram de propósito. O codec do repo lê o arquivo, mas `build(decode(x))` **não** reproduz byte a byte
(1 973 622 vs 1 973 616 B, diferença de cabeçalho/padding) — o carrier deve ser validado por entradas, não por bytes totais.

Hashes dos QML extraídos do carro:
- `qml10_25/cluster.qml` (theme2): `046749d4743eb04de4d9454f548ba970c487610b0c25bbe56c9baa2238750fd8` (DashCast: `8c9cb017…`)
- `qml/cluster.qml` (theme1): `f74082cafeed18274942e74dd2890909b24085d0fc6e2fa8113d6069fd595df2` (DashCast: `76a2ad22…`)
- Tamanhos stored: theme2 14 976 B / theme1 15 694 B; `WarinLightLayout.qml` 7 612 / 6 970 B.

### 2.2 Onde ficam os recursos de fábrica
- **Os QML do cluster não estão soltos**: estão dentro de `cluster_theme1.rcc` (2 850 entradas, só 1–2 `qml/…`) e `cluster_theme2.rcc` (3 486 entradas, `qml10_25/…`, 191 `.qml/.js` no total, o resto imagens `.png/.ktx`),
  ambos legíveis por shell. Raízes: `qml/cluster.qml` e `qml10_25/cluster.qml`; `alwaysDisplay/Presentation.qml`; `WarinLightLayout.qml`; `CustomStyle`/`common/singleton` **não** estão nesses RCC (vêm do binário/plugin Qt nativo).
- Qt do cluster em `/vendor/FissionCluster_5_15_10` e `/vendor/FissionCluster_6_5_5` (`lib64` não legível, `qt-reserved-files/qml` visível) — mesmo layout citado pelo DashCast (`5_15_10`).
- O renderer nativo fica **fora** da cell (não aparece o `qtandroidnative` para o shell, `/system/bin/qtandroidnative` = Permission denied).
- **Qual RCC o nativo escolhe**: disassembly de `BusinessUi1::updateThemeRcc(int)` (`0x118e60`): se `A ∈ {1,2}` **e** `B ∈ {1,2}` (dois globais do DataSource; o log imprime `updateThemeRcc %d,%d`) → `/system/lib64/cluster_theme2.rcc`; senão `cluster_theme1.rcc`.
  Os globais `A`/`B` não foram identificados com certeza (o provável `B` é `screenSize`). O `theme1/qml/cluster.qml` é fixo 1920×720 e **não referencia `screenSize`**; o `theme2` trata `screenSize===1` (1280×480) — por isso é muito provável que o 8,8" use **theme2**, mas **isto precisa ser confirmado em leitura** antes de gerar carrier (§3.4).

### 2.3 Layout do 8,8"
`dumpsys display` (leitura): display 1 `fission_bg_xdjaVirtualSurface`, 1280×480, 320 dpi, VIRTUAL, ON, owner `com.xdja.containerservice` (uid 1000), touch NONE.

`qml10_25/cluster.qml` (extraído do carro):
```
Item { id:rootItemId; width:1920; height:720
  scale: DataSource.screenSize === 0x01 ? 2/3 : 1
  x:     DataSource.screenSize === 0x01 ? -320 : 0
  y:     DataSource.screenSize === 0x01 ? -120 : 0 }
```
Com `transformOrigin` central, ponto de projeto `(u,v)` → tela `(2u/3, 2v/3)` exatos. `alwaysDisplay/Presentation.qml` (a janela do Android):
`width/disp_width = screenSize===1 ? 1280 : 1920`, `height/disp_height = … 480 : 720`, `disp_name:"xdjaVirtualSurface"`, `disp_mode:1`, `multi_view:true`.
Ou seja, o vídeo da projeção já é 1280×480 e a cena nativa é desenhada por cima, miniaturizada. (No theme1 o Presentation é 1920×720 fixo.)

Faixas opacas no modo mapa (`navi_full_bg.png`, 1920×720, alfa): topo ≈ y 0–105 e base ≈ y 580–720 em coordenadas de projeto (≈ 0–70 e 387–480 px físicos); o miolo é transparente com bordas suaves nas laterais.
(Medida visual na imagem extraída do carro; falta medir alfa com ferramenta.) Esse é o motivo do mapa do usuário hoje aparecer "numa faixa": a faixa livre real é ~1280×317 px físicos.

### 2.4 O que muda na prática vs o Seal
| Item | Seal (DashCast) | Song Pro 8,8" |
|---|---|---|
| Resolução | 1920×720 | 1280×480 (cena 1920×720 × 2/3) |
| `screenSize` | 2 | 1 (pela raiz do QML) |
| Âncoras do builder em `qml10_25/cluster.qml` | todas | **todas presentes e únicas** (rootItemId, bgImageContainerId, headLineId, driveRangeInfoBgId, naviSpeedId, naviPowerId, warningLayoutId) |
| `WarinLightLayout.qml` | 78 `WarningLight` (3 em grupo) | **77** (3 em grupo; âncora `Item{ x:1039+70` presente) → inventário diferente |
| `naviSpeedId` | com posição própria | **sem `x/y/width/height`** (só `visible`) → posição implícita, precisa medir |
| `DashCast.dashcastRequested` | exige `screenSize===2`, root 1920×720 | **falha de propósito** (screenSize 1) |
| `commonTranslate.rcc` | 84 entradas | 80 |
| DashCast no carro | — | `com.byd.dashcast` já instalado; a UI recusa fora do coorte |

## 3. Plano de port

### 3.1 O que gerar
Um carrier `Di3F_DynastyOceanTranslate_V0001_TranslateDashCast_<nonce32hex>.rcc` com:
1. As **80 entradas de `commonTranslate.rcc` do carro**, byte a byte (stored + decoded + país/língua/flags).
2. `qml10_25/cluster.qml` = raiz **do carro** (`046749d4…`) + inserções reversíveis marcadas (`DASHCAST_…_BEGIN/END`), CRLF, âncoras únicas.
3. `qml10_25/WarinLightLayout.qml` do carro (`sha` a registrar) com inventário **77/3 recalculado** (se for usar a técnica de empacotar telltales; no 8,8" pode-se simplificar, ver 3.3).
4. `qml/cluster.qml` (theme1) e demais raízes incluídas **idênticas** ao fábrica (para não alterar o que não é nosso) — só se a seleção do theme exigir.
5. Recursos próprios: `dashcast/DashCastDashboard.qml` + `DashboardLayout.js` adaptados (retângulo de app e moldura em coordenadas de projeto).
Limite de segurança: < 4 MiB; Qt 5.15 (`import QtQuick 2.0/2.15`).

### 3.2 Reaproveitamento do código
`build_full_dashboard.py` / `build_live_cohort_marker.py` / `build_live_marker.py`: trocar `STOCK_SHA`, `ORIGINALS`, `WARNING_SHA`, asserts 84→80, 78→77, `APP_RECT`, e `patch_root` (condição `screenSize===0x02` e `width===1920` → `screenSize===0x01` e `1920×720` em coordenadas de projeto, que **continua sendo a raiz**). `DashCastDashboard.qml`/`DashboardLayout.js` reutilizáveis. Nada disso deve ser feito por edição de hash existente (regra do guia §9.2): criar **perfil/ID novo** (ex.: `native-dashboard-sx309-88`).

### 3.3 Geometria do 8,8" e como medir
- Janela transparente em coords de projeto = retângulo desejado × 3/2. Faixa livre do mapa hoje: ≈ x 0–1920, y ≈ 105–580 (design) = **1280×317 físicos**. O retângulo "tipo Seal" (752×320 design) viraria 501×213 px: **pequeno demais** — escolher ~ x 400–1520, y 110–575 (design) e deixar o nativo (velocidade/marcha/bateria/ADAS) nas laterais.
- Medir **em PC, offline**: extrair `images10_25/display_always/{day,night}/navi_full_bg.png`, calcular mapa de alfa (PIL/`magick`; não instalei) e `x/y/width/height` de `speedValueId`, `naviPowerId`, `ecoSportModeImgId`, `centerContainerId`, `adasContainerParentId`, `adasWindowBackground (1266.5,392)`, `adas2dNaviWindowBg (1256,225)`.
  Desktop: `qmlscene` não está instalado (nem PyQt5); instalar PyQt5/Qt5-declarative do repo da distro em venv no scratchpad (RAM 7 GB: ok).
- **Medida física** (única que vale): foto/screenshot do cluster com o carrier de marcador (perfil "corner marker") — mas isso é *escrita* e fica para o primeiro teste, depois de aprovado.
- Confirmar **antes de tudo**: qual `cluster_theme*.rcc` o 8,8" usa (3.4) e se `screenSize===1` realmente é o valor em runtime (via **leitura** `Read OEM RCC selection` do DashCast, ou análise estática de `updateThemeRcc`/`cluSizeCheck` com Ghidra/llvm-objdump).

### 3.4 Pendências de leitura antes de qualquer teste
1. Identificar os globais `A`/`B` de `updateThemeRcc` (disassembly ou Ghidra: `llvm-objdump` já instalado, falta Ghidra).
2. Confirmar se o `CustomStyle`/`presentationZ` (singleton) é o mesmo — os Z do plugin não estão nos RCC; o DashCast usa `CustomStyle.bgZ` na moldura.
3. Verificar o `WarinLightLayout` **do carro** pelo mesmo teste de invariantes do DashCast (77/3 passa a ser o novo contrato).
4. Análise do `libBydCluster.so` (31 MB) só para o ABI dos plugins `com.byd.DataSource` e do `AndroidNativeWindowItem` (assinaturas das propriedades usadas nos QML: já comparados nas strings do QML do carro — sem divergência visível).
5. Ler (DashCast, só leitura) a seleção atual para registrar o baseline.

### 3.5 Riscos
- **Coorte novo sem validação em nenhum carro** (nem o Seal): risco de "Simple mode" persistente, como o full-app histórico.
- QML quebrado no root pode tornar o nativo mudo ao pedido de restore (por isso o DashCast move o arquivo sem pedir query).
- Sombreamento de alias pelo carrier **não está provado** (alias duplicado vs. ordem de registro).
- Hashes diferentes ⇒ o daemon/UI do DashCast não admite o perfil; precisaria de patch do DashCast **ou** de outra via (adb/loader próprio), com os riscos do §10.5 do guia (não lançar helper à mão).
- Warning lamps/ADAS compostos fora do QML que alteramos; contraste do texto retido sobre mapa claro (`#333333` dia).
- 8,8": qualquer escala >1 ou posição fora de 1280×480 corta instrumentos; 2/3 transforma pixel fonte em ~0,67.
- Concorrência: outra sessão está testando o painel; o `AutoContainer` não tem ID de correlação — duas sessões mutando = evidência ambígua.
- Autoridade de reboot: só `adb reboot fission-all` (adbd). Não temos garantia de recuperação a partir de QML quebrado.

### 3.6 Caminho de recuperação ANTES do primeiro teste (obrigatório)
1. **Backup** (feito, PC): `stock/cluster_theme1.rcc`, `cluster_theme2.rcc`, `commonTranslate.rcc`, `libBydCluster.so`, `libBydDataSource.so` com SHA-256 iguais aos do carro; copiar também para pasta durável.
2. Ter **adb por Wi-Fi/USB estável** e um segundo canal (tailnet/cabo). Testar `adb shell` e `adb reboot fission-all` em estado normal **uma vez** (como o dono do DashCast fez), com o carro parado, **antes** de qualquer carrier — para saber que o restart do host funciona neste firmware.
3. Garantir a ação de recuperação **que não depende do QML**: mover o carrier de `/sdcard/.cluster/translate/` (alias Android) para fora do diretório de lookup + `adb reboot fission-all`. Comando de leitura de verificação: `ls /sdcard/.cluster/translate/` e `sha256sum` do arquivo.
4. Ter o **Country Code Tool** (`com.byd.countrycodetool`, uid 1000) disponível como plano C; contato com a concessionária/serviço BYD ciente.
5. Carrier de teste inicial = **marcador passivo** (retângulo pequeno e `z:-1`), não o dashboard. Só então o dashboard.
6. Nunca carregar o mesmo arquivo que falhou; não repetir reboot em loop; não limpar journal/prefs do DashCast para "reabrir" carga.

## 4. Lista de verificação de segurança do 1º teste no carro

Antes:
- [ ] Carro **parado em P, freio de estacionamento, desligado de tráfego**, de preferência em garagem; sem navegação ativa.
- [ ] Bateria 12 V/HV estável; carro em ON; outra sessão de teste **parada** (nenhum AutoContainer/Waze em curso).
- [ ] `adb get-state` = device; `adb shell id` uid 2000; segunda conexão adb aberta (USB ou Wi-Fi).
- [ ] Hashes de `libBydCluster`/`libBydDataSource`/`cluster_theme*`/`commonTranslate` iguais aos registrados (§2.1); `/sdcard/.cluster/translate/` ausente.
- [ ] Baseline lido (read-only): seleção atual, `dumpsys display` (display 1 1280×480 ON), foto dos instrumentos.
- [ ] Backup dos stock no PC com SHA conferido; carrier de teste com SHA e manifesto reproduzível (builder `--check`).
- [ ] `adb reboot fission-all` já provado uma vez em estado normal; usuário sabe o aspecto de "Restarting" e do "Simple mode".
- [ ] Teste com **marcador passivo primeiro**; depois dashboard nativo; um carrier por vez.

Durante:
- [ ] Um único pedido de seleção; esperar ≥ 15,5 s; sem replay se a resposta vier ambígua (timeout ≠ falha).
- [ ] Observar o painel físico: velocidade, marcha, bateria, autonomia, luzes de alerta, ADAS devem permanecer.
- [ ] Qualquer anomalia (preto, "m/h", instrumentos sumidos, "Simple mode"): **parar**, não recarregar.

Desfazer:
1. Pedir restore padrão (cmd 5/0/mode 1) **se** o nativo responder; confirmar visualmente.
2. Se persistir/ambíguo: mover o carrier para fora do lookup, verificar (`ls` + `sha256sum`), então **um** `adb reboot fission-all`.
3. Depois do restart: confirmar instrumentos normais e que `.cluster/translate/` não tem entrada `TranslateDashCast`.
4. Se adb cair: Country Code Tool no cluster central / serviço BYD; não repetir comandos de veículo.

## 5. Evidência e arquivos consultados
- DashCast: `docs/CLUSTER_CUSTOM_THEME_DEVELOPER_GUIDE.md`, `CLUSTER_NATIVE_DASHBOARD.md`, `EXPERIMENTAL_CLUSTER_THEME.md`, `CLUSTER_BANDLESS_THEME.md`, `CLUSTER_GUIDED_RECOVERY.md`, `CLUSTER_INDEPENDENT_RECOVERY.md`, `CLUSTER_LIVE_MARKER_TEST.md`, `docs/releases/1.9.81|82-beta.md`;
  código `clusterlive/ClusterThemePolicy.kt`, `tools/cluster/build_live_marker.py`, `build_full_dashboard.py`.
- Carro (somente leitura): `getprop`, `sha256sum`, `dumpsys display`, `adb pull` dos 3 `.rcc` e 2 `.so` de `/system/lib64`; `llvm-objdump` de `BusinessUi1::updateThemeRcc`.
- Extração local: scripts `rccls.py`, `anchors.py` no scratchpad (decodificam o RCC por `mmap`, sem Qt).

---

# Rodada 2 (2026-10-31) — pendências do §3.4, tudo offline

Escopo: só análise no PC (llvm-objdump + Python, sem Ghidra) e leituras no carro (`getprop`). Nada gravado no carro.
Artefatos: `estudos/rodada2/` (scripts, manifesto, `mapa_88_projeto.png` 1920×720, `mapa_88_fisico.png` 1280×480) e, no scratchpad,
`<pasta local>/OFFLINE_ONLY_sx309_88_bandless_speedfix.rcc` (candidato local; **não** carregar).

## R2.1 Qual RCC o 8,8" usa — RESOLVIDO

Os globais de `BusinessUi1::updateThemeRcc` (`0x118e60`), via relocs GLOB_DAT: **A = `g_productIndex`** (`.data` 0x17d008, init 2) e
**B = `g_ro_clu_size`** (0x17d00c, init 3). Regra: `A∈{1,2} && B∈{1,2}` → `/system/lib64/cluster_theme2.rcc`; senão `cluster_theme1.rcc`.

`g_ro_clu_size` é preenchido em `BydDataSourceModule::Init` (`0xf634c`) e re-tentado por `cluSizeCheck` (`0x119148`, timer enquanto "ro.clu.size is not init"):
lê **`/sys/module/max96745/parameters/clu_size`** (driver SerDes MAX96745) e compara por `memcmp`:

| conteúdo do sysfs | `g_ro_clu_size` | log da lib | RCC |
|---|---|---|---|
| `88` | **1** | "clu size check result is 8.8" | **theme2** (`qml10_25`) |
| `1025` | 2 | "… is 10.25" | theme2 |
| outro (`123`) / não inicializado | 3 | "… is 12.3" | theme1 (`qml/`) |

No carro: `getprop ro.clu.size` = **`88`** (o sysfs não é legível pelo shell: Permission denied). `ro.product.name`=`DiLink3.0` (também comparado em Init).
Conclusão: o 8,8" carrega **`cluster_theme2.rcc`/`qml10_25`**; o theme1 é o painel de 12,3". Isto também explica o Seal: seu cluster é de **10,25"** (1920×720), logo também theme2 (`screenSize===2`).
Evidência de runtime independente: existe o display virtual `fission_bg_xdjaVirtualSurface` **1280×480** — o `Presentation.qml` do theme2 só cria 1280×480 com `screenSize===1`; o do theme1 é fixo 1920×720.
Não tracei o setter de `DATA_ITEM_ID_SCREEN_SIZE` (que alimenta `DataSource.screenSize`), mas `screenSize===1` é consistente com os três indícios (ro.clu.size=88, display 1280×480, mapeamento 88→1).
Implicação: o carrier **precisa** shadowar `qml10_25/cluster.qml`; `qml/cluster.qml` (theme1) é irrelevante no 8,8" (pode ir idêntico ao de fábrica, como no DashCast).

## R2.2 O bug do "m/h" — causa provável encontrada, corrigível por carrier

No `qml10_25/cluster.qml` **do carro** (SX309), linhas 1976–2010:
- `naviPowerId` tem `x:149; y:588; width:267; height:75`.
- `naviSpeedId` **não tem `x`, `y`, `width` nem `height`** (3 linhas em branco no lugar). Só `visible: naviType === FULL_SCREEN`.
- Dentro dele há um `Item` com `anchors.horizontalCenter/verticalCenter: parent`, e `speedValueId` (94 px) + `speedUnitId` (45 px, "km/h" ou "mph", anchor.left = valor.right + 12).
- O theme1 (12,3") ainda tem `x:1501; y:564; width:93` no `speedValueId` — ou seja, no theme2 desta firmware a posição **foi perdida**.

Efeito: o pai tem tamanho 0 em (0,0); o conteúdo centra em x=0,y=0 e metade vai para coordenadas negativas (clipada pela borda da cena). Com velocidade de 1 dígito
(parado/andando devagar com a projeção) o `speedValueId` fica em ≈[-81,-29] e a unidade começa em ≈-17 → o "k" some e aparece **"m/h" colado no canto superior esquerdo** — exatamente o sintoma.
É só no modo navegação em tela cheia (`naviType===FULL_SCREEN`, o modo do mapa/projeção); no modo normal a velocidade vem dos `SpeedDashBoard*`.
(O "native dashboard" do DashCast usa `Translate{x: 232 - naviSpeedId.x …}`, que **assume** `x` conhecido; aqui `x=0`, então o mesmo código funcionaria, mas o ponto de partida é um item sem geometria.)

**Um carrier corrige isso**: inserir logo após `id: naviSpeedId` as linhas `x: 1504; y: 588; width: 267; height: 75` (espelho do `naviPowerId`; `1920-149-267=1504`). É o candidato local.
Pontos a validar fisicamente: o retângulo 1504,588 sobrepõe a faixa inferior onde também ficam `odoItemId` (x=1446, w=400, ancorado embaixo) e a janela do ADAS (1266–1518, 392–642): posição final deve ser ajustada vendo o painel
(ver imagem). Hipótese ainda **não** confirmada em hardware; nenhum `logcat` do QML disponível (log fica fora da cell).

## R2.3 Mapa de geometria no modo mapa (FULL_SCREEN) — medido

Alfa de `navi_full_bg.png` (dia e noite **idênticos** em alfa; 1920×720 RGBA; extraído do `cluster_theme2.rcc` do carro):

| Critério | y (projeto) | y físico (×2/3) | x (projeto) |
|---|---|---|---|
| alfa == 0 (totalmente transparente), bbox | 148 – 538 | 99 – 359 | 411 – 1509 (→ físico 274 – 1006) |
| alfa ≤ 16 (coluna central) | 135 – 551 | 90 – 367 | 139 – 1768 (linha do meio) |
| alfa ≤ 50 % | 115 – 571 | 77 – 381 | toda a largura (vinheta lateral só dim) |
| alfa ≤ 250 (início da banda) | 88 – 598 | 59 – 399 | — |

As bandas são **gradientes**, não bordas duras (≈60 px de transição). Zonas livres, `bandless` (3 `opacity` do DashCast aplicados à firmware do carro) elimina a imagem e deixa só os elementos:

| Elemento (QML do carro) | Projeto (1920×720) | Físico (1280×480) |
|---|---|---|
| `headInfoId` (marcha/modo/hora), `x=0,y=0` | 0,0 1920×65 | 0,0 1280×43 |
| `headLineId` (`headLine.png` 1055×3, `y:75`, centrado) | 432,75 1055×3 | 288,50 703×2 |
| `driveRangeInfoContainerId` `y:578`; `bg_soc_fuel.png` 993×80 centrado | 463,578 993×80 | 309,385 662×53 |
| `naviPowerId` | 149,588 267×75 | 99,392 178×50 |
| `naviSpeedId` | **sem geometria (0,0)**; proposto 1504,588 267×75 | proposto 1003,392 178×50 |
| `odoItemId` `x:1446` (FULL), w=400, ancorado embaixo (`bottomMargin:12`) | x1446, ≈ y 640–708 | x964 |
| `adasWindowBackground` | 1266.5,392 | 844,261 |
| `adas2dNaviWindowBg` | 1256,225 | 837,150 |
| `adasContainerParentId` (clip) | 1323,446 189×120 | 882,297 126×80 |
| `ecoSportModeImgId` | centrado, `y:0` (imagem de tamanho variável; não medida) | y=0 |
| `centerContainerId` / `WarinLightLayout` | `anchors.fill: parent` (1920×720) | tela inteira |
| Luzes (74 diretas): 62 de 74 resolvidas por regex (modo navegação) | colunas x<100 e x>1700 e linhas y<65 / y>650 | ver PNG |

A janela do ADAS (2D) só aparece com `adasInterfaceDisplay`/`adasWindow` ativo e **cobre o lado direito do mapa** (≈ 837–1006 × 150–430 físicos).
Medidas `ecoSportModeImgId` e as luzes sem expressão numérica ficaram **aproximadas** (não medi bounds de imagem para elas).

Conferência com o relato do usuário (mapa 0,0–1280×420; barra de cima ~0–42 e de baixo ~388–480): 42 px físicos ≈ 63 projeto ≈ `headInfoId` (65);
388 físicos ≈ 582 projeto ≈ `driveRangeInfoContainerId.y=578`. **Bate.** O espaço entre as barras é ≈ 1280×342 físicos (y 43–385).

Imagens: `estudos/rodada2/mapa_88_projeto.png` (fundo xadrez = transparente; contornos verde/amarelo/laranja = alfa 0/50 %/250; retângulos = elementos nativos; círculos = luzes) e `mapa_88_fisico.png` (mesmo, 1280×480).

## R2.4 `WarinLightLayout.qml` do carro — contrato novo

`sha256` `4c3651a6202deeedd8da80c03efd7f17c34ccbb063c0f88edee4545848247092`, 58 695 B (DashCast/Seal: `5cc7de50…`). Rodei os mesmos helpers do `build_full_dashboard.py` (`qml_objects`, `brace_end`):

| Invariante | Seal (DashCast) | **Carro (SX309)** |
|---|---|---|
| `WarningLight` vivos | 78 | **77** |
| diretos / agrupados (semáforo, depth 2) | 75 / 3 | **74 / 3** |
| declarações comentadas | 2 | **0** |
| âncora do grupo `Item{\r\n        x:1039+70\r\n` | 1 | 1 ✔ |
| `import QtQuick 2.0` / cabeçalho `import com.byd.WarningLight 1.0\r\n\r\nItem {\r\n` | 1 / 1 | 1 / 1 ✔ |
| ids estáveis (regex do builder: 8 espaços) | 75 | **73 de 74**: `avasFaultLight` tem id com **12 espaços** (linha 997) → o builder atual levantaria "no stable id" |

A cadeia `warning_inventory` do DashCast **reprova** (esperado). Para um carrier que reposicione luzes seria preciso: 77/3, 74 diretas, regex de id tolerante a indentação,
e reler o estado (`displayState` em ≈61; as demais usam `visible`). **Para o carrier mínimo (R2.5) `WarinLightLayout` não é tocado.**
Detalhe útil: as luzes já têm um arranjo próprio do modo navegação (`navigationFlag`, ex.: `avasFaultLight` em x=608,y=669), então o nativo já as realoca para a faixa inferior.

## R2.5 Proposta: retângulo e perfil `native-dashboard-sx309-88`

### Escolha de design (mudança em relação ao Seal)
O que o usuário quer no 8,8" **não** é um recorte de 752×320: o mapa (a projeção do display 1, 1280×480) **já ocupa o painel inteiro por baixo** do QML; o que atrapalha é o QML opaco por cima. Então o carrier não precisa de "janela" via Loader/SurfaceDaemon; precisa de:
1. **Tirar as imagens de fundo opacas** no FULL (3 `opacity`, receita já documentada como "bandless"), mantendo todos os instrumentos nativos (marcha, bateria, autonomia, odômetro, luzes, ADAS).
2. **Dar geometria ao `naviSpeedId`** (corrige o "m/h").
Retângulo útil resultante (zona do mapa onde nada nativo desenha, coordenadas de **projeto**; físico entre parênteses):
- **Conservador (sem mexer em bg):** miolo 100 % transparente `x=411,y=148,w=1098,h=390` (274,99,732×260). Só para validar visualmente.
- **Recomendado (bandless):** `x=0,y=66,w=1920,h=512` → físico `(0,44,1280,341)` — é o vão entre `headInfoId` (65) e `driveRangeInfoContainerId` (578); luzes laterais ficam sobrepostas ao mapa. **A janela do ADAS direita (837–1006 físicos) e o carro 2D** continuam nativos.
- **Máximo:** `0,0,1920,720` (mapa por baixo de tudo; os textos nativos sobrepõem — legibilidade depende do conteúdo; `#333333` de dia).
A projeção hoje ocupa 0–420 físicos; para o "recomendado" o conteúdo do mapa deveria ficar em 44–385 (341 px de altura) para não ser coberto pelas barras de texto.

### Esboço de mudanças em `tools/cluster/build_full_dashboard.py` (cópia para o perfil novo)
| Local | Seal | SX309 8,8" |
|---|---|---|
| `cohort.STOCK_SHA` | `1dfee5a0…` | `b41bd89a9e3a881c5514992af7033a062cb6207355f809384f0469205d27fcc3` |
| `ORIGINALS` (`build_live_cohort_marker`) | `76a2ad22…` / `8c9cb017…` | `qml/cluster.qml` **`f74082ca…595df2`**, `qml10_25/cluster.qml` **`046749d4…8750fd8`** |
| `WARNING_SHA` | `5cc7de50…` | `4c3651a6…847247092` (só se mexer em luzes; mínimo: não) |
| assert locales | `len(rows)!=84`, "42 qm + 42 ts" | **80** (40 `.qm` + 40 `.ts`) |
| inventário de luzes | 78 / 75 / 3 / 2 comentadas | 77 / 74 / 3 / 0 (só se usar) |
| ids de luz | regex 8 espaços | `^\s+id:` |
| `APP_RECT` / `DashCastDashboard.qml` (moldura 752×320, Loader) | usado | **removido** no perfil mínimo; se quiser moldura: `0,66,1920,512` |
| `patch_root` condição | `naviType===0x04 && screenSize===0x02 && 1920×720 && powerGear===0x03 && playAnim && …` | **`screenSize===0x01`** (a raiz continua 1920×720 em projeto) — só necessário se houver Loader; o mínimo usa só `powerGear===ON && naviType===FULL_SCREEN` (já testado como sintaxe) |
| deslocar `naviSpeedId` | `Translate{x:232-naviSpeedId.x…}` | **definir `x/y/width/height`** (1504,588,267,75) — ajustar por foto |
| `naviPowerId` (`98.5,446`) | `Translate` | **não mexer** (já bem posicionado) |
| `minimum_daemon_protocol`, variante, `ClusterThemeVariant`, perfil no app | `full-dashboard-21f7735c` | **perfil novo**: `native-dashboard-sx309-88`, hashes acima, coorte (`libBydCluster 9f8a0b26…`, `libBydDataSource 46c36784…`, `cluster_theme2 64cda421…`) |
| guardas do daemon/UI | exigem `21f7735c/02dfc7f5` | precisam do coorte novo; **o APK do DashCast não admite sem patch** |

Fora do builder, o app/daemon precisam de (1) coorte SX309 em `ClusterRestartCohort`/`ClusterThemePolicy.originalHashes`, (2) perfil no `ClusterThemeVariant`, (3) geometria "small panel" no latch do DashCast (hoje recusa `≠1920×720`), (4) reconhecimento de recuperação para o novo asset.

### Candidato gerado só como arquivo local (inspeção)
`<pasta local>/OFFLINE_ONLY_sx309_88_bandless_speedfix.rcc` — SHA-256 `99e369e37847a5e6d7c2e76622716cdaff0af724fb6112482dc817a8cb52d52a`, 2 003 438 B (< 4 MiB), 82 entradas =
80 locales do carro (stored/decoded/metadata idênticos, verificado) + `qml/cluster.qml` **idêntico ao de fábrica** + `qml10_25/cluster.qml` patchado (`c725b1ce…49b`, **+749 B**, 15 linhas, 4 inserções marcadas `SX309_88_BEGIN/END`).
Reversível: remover as inserções devolve exatamente `046749d4…` (assert no script). `qmllint` (Qt 6) lê o arquivo sem erro de sintaxe (exit 0; 2 528 avisos de import não resolvido — esperados, QtQuick3D/OEM). **Não** valida o QML no Qt do carro (5.15.10 + plugins BYD).
Nome propositalmente fora do padrão `Di3F_…TranslateDashCast_<nonce>.rcc`: não é carregável por engano. Manifesto: `estudos/rodada2/OFFLINE_ONLY_sx309_88_manifest.json`; script: `estudos/rodada2/build_sx309_88_local.py`.

### Riscos que continuam
- `opacity` não prova que o `bgImage` some se o OEM reatribui (`visible`/`source`) — o próprio DashCast documenta isso; o binding por opacidade é o mais robusto, mas **não validado** em carro.
- Sem fallback de fundo se a projeção cair com FULL ativo: o painel ficará transparente sobre preto/imagem do display 1.
- A hipótese do "m/h" é só estática. O ajuste de `naviSpeedId` pode colidir com odômetro/ADAS (ver PNG).
- Sombreamento de alias pelo carrier ainda não foi observado em nenhum carro; recuperação continua sendo "remover arquivo + `reboot fission-all`" (§3.6), a validar antes.
- Comprimento do ciclo de pesquisa: nenhuma leitura do estado runtime (`DataSource.naviType`, `screenSize`) foi feita.

---

# Rodada 3 (2026-10-31) — prévias, ADAS 3D e teclas do volante (offline)

Nada gravado no carro. Só leituras: `getprop` e `ls` em `/vendor/FissionCluster_*`. Scripts e dados em `estudos/rodada3/` (`moc.py` extrai os enums Qt da `libBydCluster.so`; `moc_enums.json` é o resultado completo; `qtrender.py`/`compose.py` geram as prévias).

## R3.1 Prévias dos temas

Folha de contato: **`estudos/previas-temas.png`**. PNGs individuais em `estudos/previas/` (cada tema em `<nome>.png` 1280×480 e `<nome>_1920x720.png`): `fabrica_dia`, `fabrica_noite`, `bandless_noite`, `sx309_88_noite`, `sx309_88_dia`, `dashcast_native_noite`, `dashcast_native_dia`, `ambient_noite`.

**Como foram feitas (e o que NÃO são):** não são capturas do cluster. Os validadores `validate_*_qt5.py` do DashCast exigem as fontes de fábrica do **Seal** (asserts de hash) e só exercitam fixtures; não rodam no carro nem aqui sem elas. Em vez disso:
- PyQt5 5.15.14 (venv, `QT_QPA_PLATFORM=offscreen`, `QT_QUICK_BACKEND=software`) renderizou o **QML real** `tools/cluster/full_dashboard/DashCastDashboard.qml` (dia/noite) em 1920×720.
- As imagens de fábrica vêm do **`cluster_theme2.rcc` do carro**: `navi_full_bg.png`, `headLine.png`, `bg_soc_fuel.png`, `bg.png` (dia/noite). O `horizon_glow.png` é do artefato ambient do DashCast.
- O mapa é um **placeholder sintético** ("MAPA SIMULADO") e os instrumentos (marcha, kW, SOC, odômetro, velocidade) são texto desenhado nas posições do QML do carro — **mocks**. As luzes são círculos ilustrativos.
- Cenas "fábrica" reproduzem a posição real do `naviSpeedId` sem x/y: o **"m/h" no canto** aparece nas prévias de fábrica e de ambient; a prévia `sx309_88` mostra a velocidade em 1504,588 (posição proposta, ainda a ajustar).

| Prévia | Fonte | Observação |
|---|---|---|
| fábrica dia/noite | `navi_full_bg` + `headLine` + `bg_soc_fuel` do carro | mostra as bandas em gradiente e o "m/h" |
| bandless (= `fullscreen_instruments`) | as 3 `opacity` do DashCast | **no original, `fullscreen_instruments` só liga com `screenSize===0x02`** (1920×720); no 8,8" (`screenSize===1`) ele **não faria nada**. O `bandless` offline do DashCast (`powerGear===ON && naviType===FULL`) não tem esse gate |
| `sx309_88` | meu candidato local | bandless + geometria da velocidade |
| `dashcast_native` | QML real do DashCast, moldura 752×320 | mostra por que a moldura do Seal **não serve** no 8,8" (cartões laterais cobrem o mapa; a janela vira 501×213 px) |
| `ambient_noite` | `bg.png` do carro + glow estático | a animação (respiração/streak) não é capturada; sem os instrumentos reais |

**Não renderizados, e por quê:** `full_app`/`minimal_app`/`fullscreen_minimal`/`perf` — são só `Loader` do `Presentation.qml` nativo (superfície Android, sem pixels próprios; precisam do plugin `com.byd.AndroidNativeWindowItem`). `full_surface_test` — prova física noturna sobre o root do Seal. Qualquer `cluster.qml` completo — depende de `com.byd.DataSource`, `com.byd.WarningLight`, `QtQuick3D 1.15` e do singleton `CustomStyle`, que não existem fora do cluster. Não forcei nenhum deles.

Nota: o tema "estilo BYD 2027 com ADAS 3D" que o dono do DashCast mostrou **não está** neste repositório (o `beta` = v1.9.82); o `native dashboard` público é só uma moldura de retângulos (ver `DashCastDashboard.qml`).

## R3.2 ADAS 3D comentado — o que é preciso para religar

### O que existe no carro
`qml10_25/adas3d/` (`Adas3dContainer.qml` 1 064 linhas, `Adas3d.qml`, `components/{Car,Truck,Bike,Pedestrian}{,Detail,NoLight,NoLightDetail}.qml`, `Model3D.qml` 59 KB) + meshes em `qml10_25/adas3d/models/meshes/` (`car`, `carDetail`, `truck`, `truckDetail`, `bike`, `bikeDetail`, `pedestrain`, `pedestrainDetail`, `self_car`, `Lane_lft/rgt`, `Carpet`; ≈1,2 MB) e imagens `images10_25/adas3d/` (self_car_*, radar/{bl,bm,br,fl,fr} com 25 frames cada, `maps/`). Alvos: **estado 1 = carro, 2 = caminhão, 3 = moto/bike, 4 = pedestre** (`getTargetSource` → `CarDetail`/`TruckDetail`/`BikeDetail`/`PedestrianDetail`).

### Onde está desligado
`Center.qml`: a instância `Adas3dContainer{ id:adas3dContainerId … }` (≈ linhas 1141–1153) e o corpo de `updateAdas3dContainer()` (≈354) estão **comentados**. Parâmetros do original: `width: naviType===4 ? 535 : 608`, `height:352`, `x: naviType===4 ? 1270 : 692`, `y: naviType===4 ? 5 : 15`, `priority:3`, `requestToDisplay: adasTestMode===1 || (adasInterfaceDisplay===1 && adasType===1 && powerGear===3)`. Ou seja, **só aparece com `adasType===1`** (plataforma ADAS "3D") ou em modo de teste.

### Propriedades do `DataSource` que ele lê (52 no total)
`adasTestMode, adasInterfaceDisplay, adasType, adasPlatform, adasShowMode, adasWindow, adasDebugMode, adasCarDoorOpenSide, accRemoteText, carTimeDisLevel, carNameType, leftRightRudderDrive, dayNightMode, dilinkName, naviType, laneChangeAnimationDirection, laneChangeDirection, laneChangeColor, laneRadius, roadWidth, carToLeftLaneDistance, leftLineType/Color, rightLineType/Color, mainTargetIndex`, **alvos** `{leftFront,rightFront,leftRear,rightRear,frontTargetOne,frontTargetTwo}{State,PosX,PosY}`, `rearRadarStatus, leftFrontRadarStatus, rightFrontRadarStatus, leftRearRadarStatus, rightRearRadarStatus`, mais `sendPluginMsgId` (resposta `RECV_MSG_ID_LANE_CHANGE_ANIMATION_FINISHED` = 44) e as constantes `CARNAME_*`. (Lista completa em `estudos/rodada3/adas3d_props.txt`.)

**Existem na lib do carro?** Sim. Todas as 52 aparecem como propriedade/constante no metaobject de `libBydCluster.so` (`DataSourceManager`), e o enum `DataItemId` (extraído do metaobject) contém os itens de entrada correspondentes: `DATA_ITEM_ID_{LEFT,RIGHT}_{FRONT,REAR}_TARGET_{STATE,POS_X,POS_Y}` (499–510), `FRONT_TARGET_ONE/TWO_*` (511–516), `REAR_TARGET_*` (517–519), `*_RADAR_STATUS` (520–525), `LEFT/RIGHT_LINE_TYPE/COLOR` (468–471), `LANE_RADIUS` 472, `ROAD_WIDTH` 475, `ADAS_TYPE` 467, `ADAS_INTERFACE_DISPLAY` 466, `ADAS_SHOW_MODE` 533, `ADAS_TEST_MODE` 47, `CAR_NAME_TYPE` 70, `ADAS_PLATFORM` 552. **O pipeline de dados existe na firmware**; o que não dá para saber offline é se a ECU do Song Pro **publica** esses alvos (depende de CAN/ADAS do carro e de `adasType`).

### Dependências de Qt
- Usa `import QtQuick3D 1.15`, `QtQuick.Timeline 1.0`, `QtGraphicalEffects 1.0` (Qt 5.15; Quick3D era preview).
- **Presentes no cluster:** `/vendor/FissionCluster_5_15_10/qt-reserved-files/qml/` contém `QtQuick3D`, `QtGraphicalEffects`, `QtQuick/Timeline`, `Scene3D` e `Qt3D` (visto por `ls`; as `.so` em `lib64/` não são listáveis pelo shell, mas o próprio cluster.qml já importa `QtQuick3D 1.15` e `QtGraphicalEffects` na raiz). Logo, Quick3D **está disponível** no runtime do cluster; já é carregado pelo `cluster.qml` de fábrica.
- Não confirmado: custo de GPU/CPU no 8,8" (SoC QCM6125). O fato de a instância estar comentada nesta firmware pode ser decisão de desempenho/produto da BYD.
- Os meshes são `.mesh` do Quick3D **5.15**; o Quick3D do PC (Qt 6.12 instalado) **não os lê** (formato mudou) e o `Adas3d.qml` usa a API `1.15`. Portanto **não gerei `adas3d.png`**: exigiria portar para Qt 6 e converter meshes (balsam), o que não é uma prévia fiel. O `qt5-quick3d` do Arch não está instalado e requer root; PyQt5 de pip não inclui Quick3D.

### Como religar num carrier (esboço, sem aplicar)
1. No `Center.qml` (override em `qml10_25/Center.qml`, hash a registrar) **descomentar** o `Adas3dContainer` e o corpo de `updateAdas3dContainer()` e a conexão `requestToDisplayChanged`; o import `"./adas3d"` já está na linha 7.
2. Geometria para o 8,8" (coordenadas de projeto; ×2/3 físico): original 608×352 → físico **405×235**. Centro da tela: `x=(1920-608)/2=656`, `y≈140` (físico 437,93). O container tem `priority:3` e `CenterChild`, ou seja, **compete com as outras janelas do centro** (menu, navegação, ADAS 2D); a lógica de prioridade do `CenterContainer` (C++) decide quem aparece.
3. Condição de exibição: manter `adasInterfaceDisplay===1 && adasType===1`. **Se o Song Pro tiver `adasType===0` (ADAS 2D), o 3D nunca aparece** — para forçar, usar `adasTestMode===1`/`adasShowMode===1` é "modo de teste" (alvos fictícios!) e **não é aceitável dirigindo**. Precisa-se medir `adasType` em runtime antes (leitura via diag do DashCast: não feita aqui).
4. `selfCarSource`: para `carNameType` do 8,8" há casos `CARNAME_EWE_8_8_EV_0xBF` (0x1a) → `self_car_EWE.png` e `CARNAME_HA6HBU_8_8_HEV_0xD9` (0x1b) → `self_car_HA6H.png`; os demais caem no `default` `self_car_SK2.png`. O `carNameType` do Song Pro **não foi identificado** (os `getprop` não o expõem: `ro.vehicle.type.value=37`, `persist.sys.vehicle_40d_code=90`).
5. A cena 3D tem 960×370 nativos (`Adas3d.qml`); o container tem 600/608×352; **como a cena de 960×370 é ajustada a essa área não foi verificado** (precisa ler o final de `Adas3dContainer.qml`/`Model3D.qml`).
6. Riscos: Quick3D em 8,8" não testado; o mapa/projeção ocupa a mesma área central; desligar a janela do ADAS 2D de fábrica seria remover um **aviso de segurança**; qualquer carrier que ligue o 3D tem que preservar o 2D como fallback.

## R3.3 Teclas do volante e mensagens — contrato extraído

Valores extraídos do metaobject real da `libBydCluster.so` (`DataSourceManager`, enum `PluginMsgId`, 135 entradas; `moc_enums.json`). Convenção do BYD: **`SEND_MSG_ID_*` = mensagens do nativo → QML** (chegam em `DataSource.onPluginMsgReceived(id, value)`); **`RECV_MSG_ID_*` = QML → nativo** (via `DataSource.sendPluginMsgId(id)` / `sendPluginMsgInt(id, value)`).

### Nativo → QML (`onPluginMsgReceived`)
| id | nome | uso no tema de fábrica |
|---|---|---|
| 0 | `KEY_UP` | menu: sobe item na lista (DrivingInfo etc.); fora do menu: `Center.keyMsgHandler` |
| 1 | `KEY_DOWN` | idem, desce |
| 2 | `KEY_LEFT` | menu principal: página anterior (`pathView.decrementCurrentIndex` + `MENU_DISPLAY_PAGE`) |
| 3 | `KEY_RIGHT` | página seguinte |
| 4 | `KEY_OK` | confirma: seleciona item (`DRIVEINFO_DISPLAY_PAGE`, `NAVI_TYPE`, `MENU_THEME_PAGE`, `MENU_ACCELERATE_TIMER`…) |
| 5 | `KEY_BACK` | fecha menu: `MENU_DISPLAY_STATE=2` |
| 6 | `KEY_LONG_PRESS` | tratado em `SettingMenu.qml` (≈338) e `Center.keyMsgHandler` (id ≥0 e ≤6 vão para `keyMsgHandler`) |
| 7 | `KEY_DRIVEINFO_SHORT` | avança a página de driveinfo (`DRIVEINFO_DISPLAY_PAGE` com `driveInfoModel.get(i).page`) |
| 8 | `KEY_DRIVEINFO_LONG` | reset do item atual (`DRIVE_INFO_RECENT_50KM…`, `FUEL_CONSUMP`, `DRIVE_TIME`, `CHARGE` → valor 1) |
| 9 | `DASHBOARD_SELFCHECK` | `cluster.qml` inicia o timer de fim de autoteste (`POWERON_ANIM_SELFCHECK_COMPLETE`) |
| 10 | `DAY_NIGHT_MODE_SWITCH_REQUEST` | animação dia/noite |
| 11 | `THEME_SWITCH_REQUEST` | `value` = HIDE_ANIM / DISP_ANIM / SWITCH_ANIM / POWER_OFF_STATE (animação de troca de tema) |
| 12 | `ENTER_THEME_CHANGE_PAGE` | |
| 13 | `QML_GRAB_TO_IMAGE` | captura (debug) |
| 14 / 15 | `NAVI_FULL_SWITCH` / `NAVI_FULL_SWITCH_REQUEST` | transição para/da navegação em tela cheia |
| 16 | `TRANSLATE_SWITCH_ID` | troca de recurso translate (rcc) |
`KeyStateEnum`: `KEY_RELEASE=0`, `KEY_CLICK=1`, `KEY_LONG_CLICK=2` (o `value` da mensagem de tecla). O `CenterContainer` C++ também emite `keyMsgSignal` ao filho do topo (`SettingMenu.onKeyMsgSignal`).

### QML → nativo (o que o tema DEVE responder)
| id | nome | quando |
|---|---|---|
| 17 | `QML_LOAD_COMPLETE` | no `Component.onCompleted` do root (**obrigatório**) |
| 18 | `QML_QSTR_LOAD_COMPLETE` | |
| 19 | `POWERON_ANIM` (value: START/COMPLETE/CRASH/SELFCHECK_COMPLETE) | sequência de ligar; `cluster.qml` envia CRASH se `playAnim`, COMPLETE ao fim do approach |
| 20 | `DAY_NIGHT_ANIM` | fim da animação dia/noite |
| 21 | `THEME_SWITCH_ANIM` (HIDE/DISP_ANIM_COMPLETE, SWITCH_ANIM_COMPLETE) | resposta a `THEME_SWITCH_REQUEST` |
| 23 | `DRIVEINFO_DISPLAY_PAGE` | página de driveinfo atual (value = `page`) |
| 24 | `MENU_DISPLAY_STATE` | 1 = menu aberto, 2 = fechado |
| 25 | `MENU_DISPLAY_PAGE` | índice do menu (`MenuIndex` 0 DRIVE_INFO,1 SPEED_REMINDER,2 THEME,3 AIR_TEMP,4 HISTORY_FAULT,5 AIR_VOLUME,6 ACCELERATE) |
| 26 | `MENU_DRIVEINFO_PAGE` | sub-página de driveinfo no menu |
| 27/28/29/30 | `MENU_AC_TEMPERATURE`/`AC_VOLUME`/`SPEED_REMINDER`/`THEME_PAGE` | |
| 31–35 | `DRIVE_INFO_RECENT_50KM_ENERGY_CONSUMP`, `FUEL_CONSUMP`, `DRIVE_TIME`, `AVG_SPEED`, `CHARGE` | reset/ação (value 1) |
| 36 | `MENU_ACCELERATE_TIMER` | |
| 37 | `ADAS_REMOTE_SHOW_FINISH` | |
| 38 | `MENU_STATE_TO_IVI` | |
| 39 / 42 | `NAVI_TYPE` / `MENU_NAVI_TYPE` | escolha do modo navegação (`NaviType`: 1 OFF, 2 EASY, 3 SMALL_SCREEN, 4 FULL_SCREEN) |
| 40 | `CLUSTER_DEBUG` | |
| 41 | `NAVI_FULL_SWITCH_ANIM` | fim da animação de navegação cheia |
| 43 | `IVI_BOOT_COMPLETED` | |
| 44 | `LANE_CHANGE_ANIMATION_FINISHED` | ADAS 3D |
| 45 | `TRANSLATE_PATH_SWITCH` | |
`MSG_ID_MAX` e o grupo `INTERNAL_MSG_ID_*` (diagnóstico de lâmpadas/ponteiros, `INTERNAL_MSG_ID_BACKLIGHT`…) não pertencem ao contrato de tema.

Outros enums úteis: `TopUiIndex` (0 INVALID, 1 DRIVE_INFO, 2 MENU, 3 FAULT_PROMPT, 4 CHARGE, 5 DISCHARGE, 6 ADAS, 7 THIS_JOURNEY, 8 ACCELERATOR), `UiDisplayStatus` (0 HIDE, 1 DISPLAY), `PowerGear` (1 OFF, 2 ACC, 3 ON), `IVI_TO_CLUSTER_REQ_CMD` (1 QUERY_INFO, 2 SKIN_PATH, 3 CARBODY_PATH, 4 NFC_PATH, **5 TRANSLATE_PATH**) e `UPDATE_RCC_ERROR_CODE` (0x2329 DIR_NOT_EXISTS … 0x232d VERSION_NOT_VERIFIED, 0x232e NOT_STANDARD_RCC, 0x2330 RESOURCE_IS_SAME, 0x2331 NOT_EXISTS, **0x2332 GEAR_IS_NOT_P_AND_NOT_INVALID**, 0x2333 SET_SKIN_FREQUENCY) — confirma o `cmd 5` do DashCast e adiciona a regra "só troca de tema em P" e um limite de frequência.

### Como um tema nosso não quebra o menu nativo
1. **Não substituir** `Center.qml`/`SettingMenu.qml`/`DrivingInfo.qml`; só inserções reversíveis (a regra do DashCast). Qualquer `onPluginMsgReceived` próprio deve ser **adicional** (`Connections` em outro item) e **nunca consumir** a tecla — o nativo não tem "consumo", cada `Connections` recebe a mensagem.
2. Se o tema mostrar telas próprias, **não enviar** `MENU_DISPLAY_STATE`/`MENU_DISPLAY_PAGE`; deixar `menuNeedDisplay` (`DataSource.menuNeedDisplay === UI_DISPLAY`) comandar o menu nativo. Para qualquer janela nova do centro, usar `CenterChild` com `priority` menor que o menu/avisos.
3. Preservar `QML_LOAD_COMPLETE` (17) no root e as respostas de animação (POWERON 19, THEME_SWITCH 21, NAVI_FULL_SWITCH_ANIM 41, DAY_NIGHT 20) — o nativo espera por elas.
4. As teclas só têm significado quando `menuNeedDisplay` está ativo ou a janela do topo trata `keyMsgSignal`; se o nosso tema usar teclas para algo próprio (ex.: alternar mapa/ADAS), **só quando o menu nativo estiver fechado** (`menuNeedDisplay!==UI_DISPLAY`) e `powerGear===3`, e nunca em P→D crítico.

### Só dá para confirmar no carro
- **Qual botão físico gera qual id** (OK, BACK, setas esquerda/direita/cima/baixo do volante, botão "i"/driveinfo): ids 0–8 vêm da ECU via native `KeyStateEnum` (CLICK/LONG_CLICK), mapeamento físico não está no QML. Teste seguro: ler `console.log("[onPluginMsgReceived] id value")` do `Center.qml` — o log fica **fora** da cell (não visível por adb), então precisaria de um tema de teste com marcador (escrita) ou captura dos logs do host.
- Quais teclas o Song Pro realmente envia (alguns botões podem ser do IVI e não do cluster).
- Se `adasType`, `carNameType` e os alvos são publicados pela ECU do Song Pro.

## R3.4 Resumo (10 linhas)
1. Folha de contato **`estudos/previas-temas.png`** e 16 PNGs em `estudos/previas/` (8 temas × 1280×480 e 1920×720); simuladas: mapa/instrumentos são mocks, fundos são do `cluster_theme2` do carro, e o QML real do DashCast foi renderizado com PyQt5 offscreen.
2. `fullscreen_instruments` **não age no 8,8"** (gate `screenSize===2`); o `bandless` e o meu `sx309_88` sim; a moldura 752×320 do `native dashboard` não serve no 8,8".
3. Os validadores do DashCast não rodam sem as fontes do Seal; `full_app`/`minimal_app` são só `Loader` sem pixels; QML completo precisa de plugins BYD/Quick3D.
4. ADAS 3D: 52 propriedades do `DataSource` lidas (todas existem em `libBydCluster.so`; itens `DATA_ITEM_ID_*TARGET*` 499–519), `QtQuick3D 1.15` presente no cluster; está comentado em `Center.qml` e só liga com `adasType===1`.
5. Sem prévia de `adas3d.png`: meshes Quick3D 5.15 não abrem no Qt 6.12 instalado; documentado o esboço de religação (container 608×352 → físico 405×235).
6. Contrato de teclas extraído do metaobject: `SEND_MSG_ID_KEY_*` 0–8 (nativo→QML), `RECV_MSG_ID_*` 17–45 (QML→nativo), `KeyStateEnum` 0/1/2, `MenuIndex`, `TopUiIndex`, `NaviType`, `CARNAME_TYPE`.
7. Regras para não quebrar o menu: só inserções, `Connections` aditivos, manter `QML_LOAD_COMPLETE` e respostas de animação, janelas do centro com `CenterChild`/prioridade.
8. Descoberta lateral: `UPDATE_RCC_ERROR_CODE 0x2332` = troca de tema só em P; `0x2333` limita frequência.
9. Só confirmável no carro: botão físico ↔ id, `adasType`, `carNameType`, e se a ECU publica alvos ADAS.
10. Nada gravado no carro; scripts em `estudos/rodada3/`.

---

# Rodada 4 (2026-10-31) — carrier offline do tema Horizon

Nada gravado no carro (só `service list` e `getprop`/`ls` em leituras). Arquivos em `estudos/rodada4/` e, na origem, `horizon/` (`tools/build_horizon_sx309_88.py`, `RECUPERACAO.md`, `out-carrier/`, `mock/root_test/`, `out-root/`).

## R4.1 Builder `build_horizon_sx309_88.py`

`python3 tools/build_horizon_sx309_88.py --stock DIR --horizon qml/horizon --out DIR [--speed-fix] [--check]` (codec: o de `build_live_marker.py` do DashCast, só leitura).
- **Recusa** se qualquer entrada divergir: SHA-256 inteiro dos 3 `.rcc` do carro (`7ff2bde7…`, `64cda421…`, `b41bd89a…`), e dos 3 QML extraídos (`qml/cluster.qml` `f74082ca…`, `qml10_25/cluster.qml` `046749d4…`, `qml10_25/WarinLightLayout.qml` `4c3651a6…`), 80 locales, âncoras únicas, patch exatamente reversível.
- **Gera** `OFFLINE_ONLY_horizon_sx309_88.rcc` (102 entradas, 2 056 511 B < 4 MiB, SHA-256 `6f9cd3d8…b839` com os arquivos do Horizon desta rodada): 80 locales byte a byte (stored + decoded + metadados, verificado por round-trip), `qml/cluster.qml` idêntico ao de fábrica, `qml10_25/cluster.qml` e `WarinLightLayout.qml` patchados, e `horizon/*.qml|js` (flag 0, como o DashCast faz com recursos próprios). Mais `manifest.json` (SHA de tudo), `SHA256SUMS`, `patched/` e `patches/*.diff`. `--check` reconstrói e exige bytes idênticos (passou).
- **Correção em relação ao `patch_lamps.py`:** aquele script lê o QML em texto, o que **converte CRLF→LF e descarta o BOM** do original (o resultado não é "o fábrica + inserções"). O builder opera em bytes (BOM e CRLF preservados) e reverte por `HORIZON_BEGIN/END` + restauração exata da linha do `navigationFlag`.
- **Mudança de comportamento importante no patch das luzes:** `navigationFlag: true` incondicional e `opacity: 0` na placa TSR faziam o cluster **deixar de ser o de fábrica mesmo com o Horizon falhando**. Agora o layout de luzes recebe `horizonActive` (id `horizonLamps`): `navigationFlag = (fábrica) || horizonLamps.horizonActive` e `opacity: horizonLamps.horizonActive ? 0 : 1` na placa TSR. Sem Horizon, ambos são o de fábrica.
- Opção `--speed-fix` (desligada por padrão, para manter "idêntico ao fábrica"): dá geometria ao `naviSpeedId` (corrige o "m/h" com o Horizon desligado; ver R2.2).

## R4.2 Patch da raiz (`qml10_25/cluster.qml`), com `HORIZON_BEGIN/END`

**z reais** (lidos do `CustomStyle.qml` embutido na `libBydCluster.so`; o stub do harness tinha valores inventados): `bgZ=-2`, `presentationZ=-3` (superfície Android), `themeSwithAnimZ=-1`, `adasZ=99`, `warningLightZ=100`, `approachAnimaionZ=199`, `powerOnAnimationZ=200`, `fpsZ=201`; `centerContainerId z:3`, janelas ADAS 2D `z:3`, textos de dica ADAS `z:111`; `alwyasDisplayId` z=0 (padrão).

(a) **Loader** `horizonLoader`, inserido antes de `presentationLoaderId` (âncora única): `z: 1` (acima da camada sempre visível z=0 e da superfície Android z=-3; **abaixo** de `centerContainerId` z=3, textos ADAS 111, luzes 100 e animações de ligar 199/200), `asynchronous: true`, `active: horizonWanted`, `source: "qrc:/horizon/HorizonCluster.qml"`, `visible: horizonActive`.

(b) **Fail-open** (propriedades na raiz):
- `horizonWanted` = `screenSize===1 && 1920×720 && powerGear===3 && playAnim===true && alwyasDisplayId.visible && warningLayoutId.visible` — só quando o fábrica mostraria instrumentos (espelha o gate do DashCast).
- `horizonActive` = `horizonWanted && Loader.Ready && item !== null`.
- `horizonAdas3d` = `horizonActive && (item.adasReady !== undefined ? item.adasReady : item.adasFallback === false)`. **Recomendo** o Horizon expor `readonly property bool adasReady: stage.ready`: `adasFallback` só vira `true` em `Loader.Error`, então durante o `Loading` assíncrono a janela ADAS 2D nativa já sumiria sem o 3D pronto.
- Falha do Loader (erro de parse, import) ⇒ `horizonActive=false` ⇒ nada é escondido.

(c) **Ids escondidos** (`opacity: horizonActive ? 0 : 1`, original = sem opacity = 1):
| id | por quê |
|---|---|
| `bgImageContainerId` | fundo/terreno de fábrica (opaco nas faixas) |
| `alwyasDisplayId` | **toda** a camada sempre visível: marcha (`gearModeId`, `realGearTextId`), modos ECO/SPORT, relógio, bússola, temperatura externa, `headLineId`, `driveRangeInfoContainerId` (SOC/combustível/autonomia), `odoItemId`, `naviPowerId`, `naviSpeedId` e os medidores criados dinamicamente (`powerDashBoardUi`/`speedDashBoardUi`, filhos desta camada). É um **pai único** porque os medidores são criados em runtime (sem id) |
| `adasWindowBackground`, `adas2dNaviWindowBg`, `adasContainerParentId` | janela/moldura ADAS 2D de fábrica, **só** com `horizonAdas3d` |

Escondido por consequência (perda deliberada, não coberta pelo Horizon): **bússola/direção e a luz de recuperação de energia (`feedbackLight`)**, ícones de modo de condução (o Horizon mostra ECO/SPORT em texto), terreno.
**Não escondido:** `warningLayoutId` (luzes/alertas, z=100), `Center` (menu, drive info, pop-ups de aviso, e os painéis ADAS 2D/DOW/3R1V `adasUi` etc., z=3), `adasTipTextBg`/`adasTextTip3R1V` (dicas ADAS, z=111), `adasCarDistanceItemId` (janela de distância ACC), `adasDebugLoaderId`, animações de ligar/approach/tema, o `Presentation`. Só a **placa TSR** nativa (na camada de luzes) some com Horizon ativo.
Ressalva: o painel ADAS 2D que o `Center` cria dinamicamente (`centerContainerId.adasUi`, `CenterChild`) **não foi escondido** (o C++ anima `opacity/visible`, e é um aviso); em carros com `adasType===0` ele aparece (z=3) **por cima** do Horizon, em (585,−5) 608×290 no modo mapa — sobreposição a observar no teste físico.

## R4.3 Mídia (`now_playing.json`, capa) — evidência estática

- **Caminho do host:** a string `/data/cells/cell2-rw/data/media/0/.cluster/translate/` está **nas duas libs** (`libBydDataSource.so` — monta o caminho e faz `access()` — e `libBydCluster.so`). Ou seja, o processo que roda o Qt do cluster/plugin resolve caminhos nessa visão do host; `/sdcard/.cluster/…` (Android) ≡ `/data/cells/cell2-rw/data/media/0/.cluster/…` (host). O caminho `…/.cluster/horizon/` está correto **por analogia**, mas **nenhuma string** nas libs menciona `horizon`/outros irmãos de `translate/`.
- **Ler irmão de `translate/`:** plausível (mesma árvore e dono/permissão de `media/0`), mas **não comprovado**: permissões e SELinux do processo Qt do host não são legíveis daqui (o shell é `u:r:shell`). Só dá para confirmar no carro.
- **Restrição útil:** o nativo exige **exatamente um** `.rcc` em `translate/` (`0x232b MULTI_FILES`, `0x232a` vazio, `0x2329` inexistente). Manter a mídia em `horizon/`, nunca dentro de `translate/` (como o Horizon já faz).
- **`XMLHttpRequest` GET em `file://` no Qt 5.15:** pelo código-fonte do `qtdeclarative` 5.15 (`qqmlxmlhttprequest.cpp`, `requestFromUrl`): em arquivo local, `GET` **é permitido por padrão** (só emite um aviso "dangerous… will be disabled by default in a future Qt version"); só é **recusado** se a variável de ambiente `QML_XHR_ALLOW_FILE_READ` existir com valor 0. O harness já mostrou o mesmo aviso (e a leitura segue). No cluster (Qt 5.15.10 da BYD) o build pode estar modificado ou a variável definida pelo init do host (não verificável). O `NowPlayingFeed.qml` já trata isso bem: falha ⇒ `alive=false` ⇒ texto nativo (`mediaInfoString`).
- **Alternativas, em ordem:** (1) manter XHR + fallback atual (único caminho sem custo extra); (2) capa por `Image { source: "file:///…/art_<id>.png"; cache: false }` (leitura direta, sem XHR); (3) para os **dados** sem arquivo: continuar usando `DataSource.mediaInfoString` (o canal nativo, `title｜artist｜lyric｜próxima`), que o Horizon já usa como fallback — não depende de permissão de arquivo. Escrever o JSON com `write temp + rename` (o leitor tolera meio-arquivo e detecta `seq` parado).

## R4.4 Verificação offline

- **Round-trip:** o carrier decodifica de volta; 80 locales idênticos (stored/decoded/país/língua/flags); entradas próprias idênticas; `--check` reproduz byte a byte.
- **`qmllint` (Qt 6.12):** sem erros de sintaxe em `cluster.qml` patchado, `WarinLightLayout.qml` patchado e todos os `horizon/*.qml` (exit 0; avisos de import/tipos esperados — nota: o `cluster.qml` de fábrica dá os mesmos 81 avisos "custom-parsed"; as 81 ocorrências de "syntax/parse" eram falso positivo da palavra "custom-parsed").
- **Render da raiz patchada** (`mock/root_test/render_root.py`, PyQt5 5.15 offscreen/software; stubs do harness + fixture com **`CustomStyle` com z reais**, `Center` vazio e `Adas3dContainer` plano, porque o software backend dá **segfault** no `View3D` do Quick3D; o `DataSource` é gerado com todas as propriedades e as constantes extraídas do metaobject da lib):
  | Cenário | Resultado |
  |---|---|
  | `night_map` com o carrier | `horizonWanted/Active/Adas3d = True`; só o Horizon aparece (`out-root/root_night_map.png`) |
  | `night_instruments`, `day_map` com o carrier | idem (`root_night_instruments.png`, `root_day_map.png`) |
  | **sem carrier** (fábrica) | instrumentos de fábrica visíveis; **reproduz o "km/h" cortado em (0,0)** (`root_factory_night_map.png`) |
  | carrier com `HorizonCluster.qml` **quebrado** (erro de sintaxe) | `horizonWanted=True, horizonActive=False`; o render é **pixel a pixel idêntico** ao de fábrica (bbox das diferenças = nenhuma) — **fail-open comprovado no harness** (`root_failopen_night_map.png`) |
- **O que o harness NÃO cobre:** QtQuick3D real no cluster (a cena 3D de fábrica foi validada pela outra sessão com GPU); o `Center` real (menu/pop-ups/ADAS 2D dinâmico); `WarningLight` nativo (stub); ordem de registro dos RCCs no host; `AndroidNativeWindowItem` real (o stub aparece em outro tamanho); erros de binding em runtime dentro do Horizon (não derrubam o Loader: ficam "Ready" com aviso); Qt 5.15.10 da BYD (usei 5.15.14).

## R4.5 `horizon/RECUPERACAO.md`

Escrito (copia em `estudos/rodada4/RECUPERACAO.md`): pré-condições, nome do arquivo, `mv` atômico, os payloads FlatBuffer exatos (consulta `0c00000008000c00…`, restaurar `1000000000000a00…`), a sequência consulta→espera 15,5 s→carga→consulta, restaurar padrão, mover para `/sdcard/.cluster/retired/` + **um** `adb reboot fission-all`, matriz de falhas e plano C (Country Code Tool/concessionária). Sinalizo que **não existe** carga por `service call` (byte[] não é marshalável) e que o layout FlatBuffer foi verificado pelo DashCast em **outras** versões da lib (as classes `cluster::fbs::PadToClusterReq/ClusterToPadRes` existem na lib deste carro).

## R4.6 Resumo (10 linhas)
1. Builder `horizon/tools/build_horizon_sx309_88.py` gera `OFFLINE_ONLY_horizon_sx309_88.rcc` (102 entradas, 2 056 511 B, SHA `6f9cd3d8…`), com manifest/SHA de tudo; recusa hash divergente; `--check` reproduz byte a byte.
2. 80 locales do carro byte a byte; root theme2 e `WarinLightLayout` com inserções `HORIZON_BEGIN/END`, reversíveis; root theme1 idêntico; Horizon em `qrc:/horizon/`.
3. Loader do Horizon em z=1 (z reais: bg −2, Android −3, Center 3, luzes 100, power-on 200), acima do fábrica e abaixo de Center/luzes/animações.
4. Fail-open: esconde só `bgImageContainerId`, `alwyasDisplayId` (pai único; medidores são dinâmicos) e 3 itens da janela ADAS 2D quando `horizonActive`; Center, pop-ups, menu, luzes, dicas ADAS não são tocados.
5. Corrigi o `patch_lamps.py` (perdia BOM/CRLF e alterava luzes mesmo sem Horizon): agora `navigationFlag`/TSR dependem de `horizonActive`.
6. Render da raiz patchada: Horizon aparece e esconde o fábrica; com Horizon quebrado o render é pixel-idêntico ao fábrica; o "km/h" cortado em (0,0) do fábrica foi reproduzido.
7. `qmllint` sem erro de sintaxe; o harness não cobre Quick3D real, `Center` real, WarningLight nativo nem o Qt 5.15.10 da BYD.
8. Mídia: caminho do host está nas duas libs (para `translate/`); `horizon/` irmão é plausível mas não comprovado; XHR `file://` GET é permitido por padrão no Qt 5.15 (só bloqueia com `QML_XHR_ALLOW_FILE_READ=0`); capa via `Image file://`; texto via `mediaInfoString`.
9. `RECUPERACAO.md`: payloads FlatBuffer exatos, `mv` para `/sdcard/.cluster/retired/` + um `reboot fission-all`, matriz de falhas; sem `service call`.
10. Riscos abertos: ADAS 2D dinâmico do Center fica por cima; erro do **root** patchado não é fail-open; recuperação a partir de QML quebrado nunca foi validada em carro; recomendo ensaiar `reboot fission-all` normal antes.

---

# Rodada 5 (2026-10-31) — `HorizonThemeTool` (selecionar/consultar/restaurar), offline

Nada executado no carro. Leituras: `service list`, `pm path`/`dumpsys package`, `cat /system/etc/container_comm_cfg.json` e `adb pull` (só leitura) de `XdjaContainerService.apk` e `framework.jar`. Entregas em `horizon/tools/horizon-theme-tool/` (cópia em `estudos/rodada5/`).

## R5.1 O que o carro mostrou (conferências que o helper do DashCast não fazia por mim)

1. **Transações confirmadas neste carro** (constantes `TRANSACTION_*` do `framework.jar` puxado): `IAutoContainer` `sendJson`=1, `sendInfo`=2, **`sendInfo2(int,byte[])`=3**, **`registerCallback`=4**, `getProjectionDisplayInfo`=5; `IContainerCallback` `serviceDied`=1, `receivedJson`=2, `receivedInfo`=3, **`receivedInfo2(int,byte[])`=4**. É exatamente o contrato do DashCast.
2. **O serviço Java é outro APK:** `/system/priv-app/XdjaContainerService/XdjaContainerService.apk`, SHA-256 `c6d2c097…22d0` (o DashCast fixa `1b9eef49…` do Seal). Qualquer ferramenta copiada do DashCast **recusaria** este carro; a minha fixa o hash daqui.
3. **Quem pode enviar:** `AutoContainerService.checkSendPermissionAndAllowType` (decompilado do dex) libera o chamador se `checkSignatures(selfUid, callingUid)==0` **ou** se o nome do pacote está em `/system/etc/container_comm_cfg.json`. Neste carro o JSON só lista `com.xdja.clusterdemo` (SHA `79226328…81ef`). O **shell passa** porque `dumpsys package` mostra a **mesma assinatura** (`22216e4d`) para `com.android.shell` (uid 2000), `com.xdja.containerservice` (uid 1000) e `android`. Um app comum (ex.: o próprio open-headunit) levaria `SecurityException: Not allowed package` (o DashCast documenta isso em outro carro).
4. **Marcha:** nenhum serviço legível pelo shell expõe a marcha de forma óbvia (`autoservice`/`byd_car_service` existem, mas não identifiquei uma leitura segura). O pedido de troca de tema é **recusado pelo próprio OEM fora de P** (`0x2332 GEAR_IS_NOT_P_AND_NOT_INVALID`, enum extraído na Rodada 3). A ferramenta, por isso, exige `--assert-parked` (declaração do usuário, não sensor) e mostra o `0x2332` quando o OEM recusar. **Não consegui "recusar se a marcha não estiver em P" por leitura**: isso fica a cargo do OEM.

## R5.2 A ferramenta

`CLASSPATH=/data/local/tmp/horizon-theme-tool.dex app_process / HorizonThemeTool <cmd>` como shell. Java 8 → `d8 --min-api 29` → `horizon-theme-tool.dex` (31 KB, SHA-256 `2c62932a4ec8a5a84c34786d7d64d2811e588627a60a483405b753e7fa9fa10d`; `./build.sh`; o hash muda se o código mudar).
- `query`: só leitura (coorte + seleção decodificada). `load <basename> --sha256 <hex> --assert-parked`: confere, consulta, exige baseline = padrão de fábrica, **um** pedido, espera ACK2, consulta de novo. `restore --assert-parked`: se já é padrão não envia; se a seleção é nossa envia **um** `5/0/1`; se é de outro, recusa. `payloads`: offline.
- **Recusas antes de enviar** (saída 3): uid ≠ 2000; API ≠ 29; fingerprint ≠ `…eng.build.20251108.051320…`; qualquer SHA-256 do coorte (libs, `commonTranslate`, `fissiond`, APK do serviço, `container_comm_cfg.json`, `framework.jar`; e `cluster_theme1/2.rcc` no `load`); basename fora de `…_TranslateHorizon_[0-9a-f]{32}.rcc`; sem `--sha256`; arquivo ausente/link/irregular/> 4 MiB/SHA divergente; ≠ 1 `.rcc` em `translate/`; sem `--assert-parked`; baseline não-padrão; < 16 s do último pedido; última execução não assentada (exige `--ack-ambiguous`).
- **Nunca repete:** uma conexão Binder pinada, um pedido, timeout = **AMBÍGUO** (saída 2). Saídas 0/2/3/4 + linha `HORIZON_RESULT={…}`. Prazo total 150 s.
- **FlatBuffer feito à mão** (`FlatBuf.java`, porte do algoritmo do flatbuffers-java) + decodificador com os mesmos limites do `ClusterThemeProtocol.kt` (MIT, citado no cabeçalho). Erros mapeados: `0x2329–0x2334` (inclui 232c/232d/2330/2331/2332/2333).

## R5.3 Verificação offline

- **`./test.sh` (JVM) — ALL PASS:** consulta e restaurar **idênticas** às da tabela de `RECUPERACAO.md` (`0c00000008000c00…`, `1000000000000a00…`); `load` de nome de exemplo (128 B) e de nome estilo DashCast **idênticos ao `FlatBufferBuilder` real** (flatbuffers-java **1.12.0**, a única no cache do Gradle; o DashCast usa 25.2.10 — o algoritmo de layout é o mesmo, mas não comparei contra essa versão); 2000 pedidos aleatórios (nomes de 0 a 300 caracteres) idênticos; decodificador lê respostas montadas como o OEM monta (seleção padrão/custom, ACK2, ACK3 `0x2330`); 3000 buffers corrompidos só geram `IllegalArgumentException`. **O teste achou um bug real no meu `grow()`** (copiava do lugar errado quando o buffer passa de 256 B; não afetaria nomes de 86 caracteres, mas estava errado); corrigido.
- **O que NÃO foi testado:** a ferramenta nunca rodou num dispositivo/emulador (`Bridge`, `Cohort`, `HorizonThemeTool.run` só compilam contra `android.jar` 36; `dexdump` lista as classes). Riscos de primeira execução (todos antes de enviar algo): reflexão `ServiceManager.checkService` sob a política de APIs ocultas (o DashCast usa um bypass; um `NoSuchMethodException` aqui seria seguro, só aborta); o formato exato do ACK2/ACK3 neste firmware (slots verificados pelo DashCast em outras versões da lib; as classes `cluster::fbs::PadToClusterReq/ClusterToPadRes` existem na lib do carro); hash de 256 MB dos RCC no tempo previsto.

## R5.4 Nome do arquivo e `install.sh`

Nome selecionável: `Di3F_DynastyOceanTranslate_V0001_TranslateHorizon_<32 primeiros hex do SHA-256 do carrier>.rcc` (nome ↔ conteúdo). O matcher nativo `(Di3F_.*_.*)_Translate(.*).rcc` fecha com ele (grupo 1 = `Di3F_DynastyOceanTranslate_V0001` = a UI version), e o prefixo `TranslateHorizon_` **não** colide com os `TranslateDashCast_` do DashCast. `install.sh CARRIER.rcc [serial]` **só imprime** os comandos (calcula SHA/nome localmente, não chama adb): empurrar o dex, `query`, preparar/stagear o carrier fora do lookup, `mv` atômico, `load`, `query`, `restore` e a recuperação do §5.

## R5.5 `RECUPERACAO.md`

§3 e §4 reescritos com os comandos exatos da ferramenta, a tabela de evidências (transações, assinatura, APK), recusas, códigos de saída e a sequência. Cópia em `estudos/rodada4/RECUPERACAO.md`.

## R5.6 Resumo (8 linhas)
1. `HorizonThemeTool` (Java → `.dex` 31 KB) faz `query`/`load`/`restore` via AutoContainer como shell, um pedido por execução, timeout = ambíguo, sem DashCast.
2. Confirmei no `framework.jar` do carro as transações 3/4 (e a 4 do callback) e que o shell passa pelo `checkSignatures` (assinatura `22216e4d` igual à do serviço).
3. Achei que o APK do serviço (`c6d2c097…`) **difere** do Seal (`1b9eef49…`): a ferramenta fixa o hash daqui e recusa qualquer outro coorte (9 arquivos de sistema, mais fingerprint/API).
4. Marcha: não há leitura segura pelo shell; a ferramenta exige `--assert-parked` e o OEM recusa fora de P (`0x2332`).
5. `./test.sh`: ALL PASS — payloads iguais ao do `RECUPERACAO.md` e ao `FlatBufferBuilder` real (1.12.0), 2000 aleatórios, decodificador e 3000 buffers corrompidos; o teste revelou e corrigi um bug no meu `grow()`.
6. Nome: `…_TranslateHorizon_<32 hex do SHA>.rcc`, compatível com o regex nativo e sem colidir com o DashCast; `install.sh` só imprime os comandos.
7. `RECUPERACAO.md` §3/§4 atualizados com os comandos exatos; nada foi executado no carro (nem `push`, `app_process` ou `service call`).
8. Ainda não validado em dispositivo: reflexão de APIs ocultas, formato real dos ACKs neste firmware e o QML no Qt 5.15.10 do cluster.
