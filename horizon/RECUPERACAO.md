# Recuperação do primeiro teste — carrier Horizon (BYD Song Pro 8,8", SX309)

Documento de planejamento. **Nenhum comando abaixo foi executado no carro** (nem por mim, nem pelo builder). Tudo que escreve no carro está marcado **[ESCREVE]**. Leituras estão marcadas **[LÊ]**.

> **Atalho, a partir do carrier que ocupa a vaga do Simples:** com o painel funcionando, o caminho mais curto
> até o painel de fábrica é o menu do próprio painel (botão voltar → Tema → **Clássico**). O carrier só mexe
> na vaga do Simples, então o Clássico é sempre o de fábrica. Isso não remove nada; para tirar o Horizon de
> vez, siga o §5. Cópia dos arquivos de fábrica do painel: repositório privado `songpro-cluster-backup`.

Base: `../docs/estudos/tema-cluster-88.md` (mecanismo, evidência no `libBydDataSource.so` do carro, §1.4) e os guias do DashCast. O DashCast **nunca validou** a recuperação a partir de QML quebrado; o único teste dele foi `reboot fission-all` a partir do estado normal.

## 0. Estado de partida (lido do carro em 2026-10-31)

- `/sdcard/.cluster/` **não existe** (nenhum carrier no disco) **[LÊ]** → o fallback nativo (arquivo ausente ⇒ padrão) vale.
- `libBydCluster.so` `9f8a0b26…17ca`, `libBydDataSource.so` `46c36784…83bf`, `cluster_theme2.rcc` `64cda421…1795`, `commonTranslate.rcc` `b41bd89a…27fcc3` (iguais aos usados pelo builder; ele recusa se divergirem).
- `AutoContainer [android.os.IAutoContainer]` presente em `service list` **[LÊ]**; os símbolos `cluster::fbs::PadToClusterReq/ClusterToPadRes` e `DataSourceManager::handleIviRccReqMsg` existem na lib do carro.
- Atenção: o layout de campos FlatBuffer (slots 0,1,2,7) foi verificado pelo DashCast em **outras** versões da lib (`3197abee…`, `21f7735c…`), não na `9f8a0b26…` deste carro. Os símbolos existem, mas **o primeiro `query` (somente leitura) deve ser a primeira troca**, antes de qualquer seleção.

## 1. Antes de qualquer teste (nada disso escreve no carro)

1. Carro **parado, em P, freio de estacionamento**, em garagem, bateria 12 V boa, outro teste de painel **parado** (não há ID de correlação nas respostas do `AutoContainer`).
2. `adb -s <ip-da-multimídia>:5555 get-state` = `device`; uma **segunda** via de acesso aberta (USB ou outra rede), porque o `reboot fission-all` derruba ADB por um tempo.
3. Backups no PC com SHA conferido contra o carro (já feitos para os 3 `.rcc` e as 2 `.so`; `sha256sum` do carro = do PC).
4. Do builder: `OFFLINE_ONLY_horizon_sx309_88.rcc` + `manifest.json` + `SHA256SUMS` (`python3 tools/build_horizon_sx309_88.py ... --check` reproduz byte a byte).
5. **Ensaio de recuperação em estado normal (recomendado, antes do carrier):** um único `adb reboot fission-all` com o painel normal, para confirmar que o restart do host funciona neste firmware (o DashCast só testou isso no Seal). Exige consentimento explícito do usuário (reinicia a multimídia/host). Registrar: aparece "Restarting", volta o painel normal, `adb` volta.
6. Combinar com o usuário o **aspecto de "Simple mode"** e o plano C (item 7).

## 2. Colocar o arquivo [ESCREVE]

Nome obrigatório (regex nativa `(Di3F_.*_.*)_Translate(.*).rcc`, UI version `Di3F_DynastyOceanTranslate_V0001`):

```
Di3F_DynastyOceanTranslate_V0001_TranslateHorizon_<32 hex minúsculos>.rcc
```

O diretório de lookup deve conter **exatamente um** `.rcc` (o nativo devolve `0x232b RESOURCE_DIR_IS_MULTI_FILES` se houver mais, `0x232a` se vazio, `0x2329` se não existir). Mídia do Horizon fica em **outro** diretório (`/sdcard/.cluster/horizon/`), nunca dentro de `translate/`.

```bash
SER=<ip-da-multimídia>:5555
NAME=Di3F_DynastyOceanTranslate_V0001_TranslateHorizon_$(sha256sum OFFLINE_ONLY_horizon_sx309_88.rcc | cut -c1-32).rcc   # nonce = SHA-256 do carrier (ver install.sh)
adb -s $SER shell mkdir -p /sdcard/.cluster/translate /sdcard/.cluster/horizon /sdcard/.cluster/retired
adb -s $SER push OFFLINE_ONLY_horizon_sx309_88.rcc /sdcard/.cluster/horizon/.incoming.rcc   # fora do lookup
adb -s $SER shell sha256sum /sdcard/.cluster/horizon/.incoming.rcc                          # = SHA do manifest
adb -s $SER shell mv /sdcard/.cluster/horizon/.incoming.rcc /sdcard/.cluster/translate/$NAME # rename atômico no mesmo FS
adb -s $SER shell ls -la /sdcard/.cluster/translate/                                        # [LÊ] só 1 arquivo
```

Escrever o `now_playing.json` com `write temp + mv` (o leitor já tolera meio-arquivo e detecta `seq` parado).

## 3. Selecionar [ESCREVE — Binder] com `HorizonThemeTool`

Ferramenta própria, sem DashCast: `tools/horizon-theme-tool/` (`build.sh` → `build/horizon-theme-tool.dex`, `test.sh`, `install.sh`). Roda como **shell (uid 2000)**:

```bash
CLASSPATH=/data/local/tmp/horizon-theme-tool.dex app_process / HorizonThemeTool <comando>
```

`service call AutoContainer …` **não serve** (a transação 3 leva `int 8` + byte[]; `service call` não marshala byte[]). A ferramenta usa o mesmo contrato do DashCast (`ClusterThemeIviHelper.kt`/`ClusterThemeProtocol.kt`, MIT, citado no cabeçalho) e foi **conferida contra este carro**:

| Fato | Evidência (leitura no carro) |
|---|---|
| `IAutoContainer`: `sendJson` 1, `sendInfo` 2, **`sendInfo2(int, byte[])` 3**, **`registerCallback` 4**, `getProjectionDisplayInfo` 5 | constantes `TRANSACTION_*` no `framework.jar` do carro |
| `IContainerCallback`: `serviceDied` 1, `receivedJson` 2, `receivedInfo` 3, **`receivedInfo2(int, byte[])` 4** | idem |
| O shell pode enviar | o serviço (`XdjaContainerService.apk`, uid 1000) só aceita quem tem a **mesma assinatura** (`checkSignatures`) ou está em `/system/etc/container_comm_cfg.json` (aqui só `com.xdja.clusterdemo`). `dumpsys package`: shell e containerservice têm a assinatura `22216e4d` ⇒ passa pelo caminho de assinatura |
| APK diferente do Seal | SHA-256 `c6d2c097…` (o DashCast fixa `1b9eef49…` do Seal) — a ferramenta fixa o **deste** carro |

### Comandos

| Comando | O que faz |
|---|---|
| `query` | só leitura: confere o coorte (ver abaixo) e imprime a seleção OEM decodificada (`[FACTORY DEFAULT]`, `[HORIZON CARRIER]` ou `[OTHER]`) |
| `load <basename> --sha256 <hex> --assert-parked` | confere tudo, consulta, **exige baseline = padrão de fábrica**, envia **um** pedido `5/0/0`, espera ACK2 (2,5 s), consulta de novo |
| `restore --assert-parked` | consulta; se já é o padrão não envia nada; se a seleção é nossa envia **um** `5/0/1`; se a seleção é de outro, recusa |
| `payloads` | offline: imprime os bytes dos pedidos (não toca o dispositivo) |

**Recusas (antes de enviar qualquer coisa; saída 3):** não é uid 2000; API ≠ 29 ou `ro.build.fingerprint` ≠ `…eng.build.20251108.051320…`; **qualquer SHA-256** fora do coorte (`libBydCluster 9f8a0b26…`, `libBydDataSource 46c36784…`, `commonTranslate b41bd89a…`, `fissiond 31d39dfc…`, `XdjaContainerService.apk c6d2c097…`, `container_comm_cfg.json 79226328…`, `framework.jar 69591c25…`; e, para `load`, também `cluster_theme1/2.rcc` `7ff2bde7…`/`64cda421…`); basename fora de `Di3F_DynastyOceanTranslate_V0001_TranslateHorizon_[0-9a-f]{32}.rcc`; sem `--sha256`; arquivo ausente/link/irregular/>4 MiB/SHA divergente; **≠ 1 `.rcc`** em `/sdcard/.cluster/translate/` (`0x232a/0x232b`); falta `--assert-parked`; baseline não-padrão; último pedido há < 16 s; última execução ficou **AMBÍGUA** (exige `--ack-ambiguous` consciente).
**Marcha:** a ferramenta **não consegue ler a marcha** (nenhum serviço legível identificado). `--assert-parked` é uma afirmação sua (não um sensor) e o **OEM recusa fora de P** com `0x2332`.
**Um pedido por execução:** uma só conexão Binder, sem reconexão nem repetição; timeout = **AMBÍGUO** (`HORIZON_RESULT status AMBIGUOUS`, saída 2), nunca "falha"; estado em `/data/local/tmp/horizon-theme-tool.state`; prazo total de 150 s (o hash dos dois `.rcc` de 256 MB leva alguns segundos).
Saída: linhas `HORIZON: …` legíveis (erros mapeados: `0x232c NOT_RCC`, `0x232d` UI, `0x232e`, `0x2330 RESOURCE_IS_SAME`, `0x2331`, `0x2332` GEAR, `0x2333` frequência) e uma linha final `HORIZON_RESULT={…}`. Códigos de saída: **0** assentado, **2** ambíguo, **3** recusado, **4** falhou.

### Sequência exata (o `install.sh` só IMPRIME estes comandos, com nome e SHA reais)

```bash
./tools/horizon-theme-tool/build.sh && ./tools/horizon-theme-tool/test.sh
./tools/horizon-theme-tool/install.sh out-carrier/OFFLINE_ONLY_horizon_sx309_88.rcc <ip-da-multimídia>:5555   # só imprime
```
1. `adb push …/horizon-theme-tool.dex /data/local/tmp/` e conferir o SHA.
2. **`query`** (leitura): deve imprimir todas as linhas `cohort: ok …` e `selection … [FACTORY DEFAULT]`. Se algo recusar, **parar**.
3. Colocar o arquivo (§2; o nome é `…_TranslateHorizon_<32 primeiros hex do SHA-256 do carrier>.rcc`).
4. `load <NAME> --sha256 <SHA> --assert-parked` → esperar `SETTLED` (saída 0) e **olhar o painel**. Seleção ≠ registro ≠ pixel; o QML só é relido quando o caminho de aplicação roda.
5. `query` de novo.
Ambíguo (2): **não repetir**; olhar o painel; `query`; decidir.

## 4. Restaurar padrão [ESCREVE — Binder]

```bash
adb -s $SER shell 'CLASSPATH=/data/local/tmp/horizon-theme-tool.dex app_process / HorizonThemeTool restore --assert-parked'
adb -s $SER shell 'CLASSPATH=/data/local/tmp/horizon-theme-tool.dex app_process / HorizonThemeTool query'      # [LÊ] esperar [FACTORY DEFAULT]
```
- `restore` envia o pedido `5/0/1` (payload `1000000000000a000c000800000004000a0000000100000005000000`), **não** carrega `cluster_theme2.rcc`. Espera ≥16 s desde o `load` (a ferramenta recusa antes disso).
- `SETTLED` = `isDefault`: `cmd 1001, sub 4, ints[0]=1, ints[3]=1, strings[0]=UI version, strings[2]="default"` — **não** é prova física: conferir o painel de fábrica a olho (velocidade, marcha, bateria, autonomia, luzes, ADAS).
- Se a ferramenta terminar `AMBIGUOUS`/`FAILED` (p.ex. `0x2330` e a seleção continua sendo o arquivo): **não insistir** — ir ao §5.

> **Observado no carro (2026-10-31):** neste firmware o `restore` **não funciona**. O OEM responde ACK3
> `[3,0,9008,0,0]` = `RESOURCE_IS_SAME (0x2330)` e a seleção continua no carrier (igual ao que o
> DashCast registrou). O caminho de volta é sempre o §5. Também observado: `load` só grava a escolha
> (ACK4, sem ACK2) e o painel **não muda** até o próximo `reboot fission-all`, que lê a seleção salva
> (`source=0`) e então mostra o tema. Cada restart levou 50–80 s até o `adb` voltar sozinho.
> O §5 foi **comprovado** no carro: v1 movido para `retired/` + um `reboot fission-all` → painel de
> fábrica e a consulta voltou a `[FACTORY DEFAULT]` sozinha.
>
> **Trocar de versão do Horizon (um restart):** mover a versão atual para `retired/`, colocar a nova
> em `translate/` (§2) e `load <NOVA> --sha256 <SHA> --assert-parked --replace-own --ack-ambiguous`
> (seleciona por cima de outro carrier Horizon; o OEM respondeu ACK4) → `reboot fission-all`.

## 5. Recuperação que não depende do QML: tirar o arquivo e reiniciar o host

Base estática (lib do carro, `BusinessUi1::updateTranslateRccFromXml`): lê a seleção persistida (`/collect2/byd_datasource_config.xml`, chave `translate_rcc_path`), monta `/data/cells/cell2-rw/data/media/0/.cluster/translate/<nome>` e faz `access(F_OK)`; **arquivo ausente ⇒ log "translate rcc file not exists", grava o padrão e volta à tradução de fábrica no próximo start do DataSource**. Reboot Android comum **não** reinicia esse host (foi o que *não* recuperou o dono do DashCast).

```bash
SER=<ip-da-multimídia>:5555
# [ESCREVE] mover (nunca apagar) para fora do lookup; mesmo filesystem ⇒ rename
adb -s $SER shell mv /sdcard/.cluster/translate/$NAME /sdcard/.cluster/retired/$NAME
# [LÊ] verificar
adb -s $SER shell ls -la /sdcard/.cluster/translate/ /sdcard/.cluster/retired/
adb -s $SER shell sha256sum /sdcard/.cluster/retired/$NAME             # = SHA do manifest
adb -s $SER shell cat /proc/sys/kernel/random/boot_id; adb -s $SER shell cat /proc/uptime   # anotar p/ comparar
# [ESCREVE] UM único restart do host + cell (só com o carro em P, freio de mão, usuário ciente)
adb -s $SER reboot fission-all
```

Regras (do guia do DashCast, §15–16):
- **Um** pedido. Timeout/desconexão do cliente = *desconhecido*, **não** repetir; esperar o ADAS/ADB voltar.
- Mover o arquivo **antes** do restart: se reiniciar com o arquivo ruim ainda lá, o host repete a falha.
- Mover não desmapeia um RCC já registrado: o quadro atual pode não mudar até o restart do host.
- Não usar `adb shell reboot`, `svc power reboot`, `PowerManager.reboot` nem escrever propriedades: o `reboot:fission-all` vai pela autoridade do **adbd**; `shell`/apps não têm `android.permission.REBOOT`.
- Depois do restart: **[LÊ]** `adb get-state`, `boot_id`/`uptime` mudaram, `ls /sdcard/.cluster/translate/` sem `TranslateHorizon`, e conferir o painel de fábrica fisicamente.

## 6. Matriz de falhas

| Sintoma | Provável causa | Ação |
|---|---|---|
| Painel normal, nada mudou após seleção | seleção ≠ aplicação; ou o carrier não sombreou o alias | parar; consulta; restaurar padrão (§4); mover arquivo (§5). Não recarregar |
| Fábrica "como era" mas sem Horizon (Loader falhou) | `HorizonCluster.qml` falhou ao carregar (fail-open) | o painel é o de fábrica por construção; restaurar padrão normalmente |
| Instrumentos de fábrica **somem** ou tela preta | QML do **root** (`cluster.qml`) com erro, ou o Horizon "Ready" com erro de binding | §5 imediatamente (mover + `reboot fission-all`); não pedir carga de novo |
| "Simple mode running" / cluster em fallback persistente | QML do root quebrou no host | §5; se persistir, §7 |
| `adb` cai | restart em curso ou travamento da multimídia | esperar sem repetir; segunda via de acesso |
| `0x232b` | mais de um `.rcc` em `translate/` | mover o extra para `retired/` e consultar de novo |
| Painel normal mas luzes laterais/placa TSR estranhas | patch do `WarinLightLayout` ativo sem Horizon (não deveria) | restaurar padrão; conferir `horizonActive`; reportar |

## 7. Plano C (se o plano B falhar)

1. **Country Code Tool** (`com.byd.countrycodetool`, uid 1000, na multimídia): foi o que recuperou o dono do DashCast. Faz `PowerManager.reboot("fission-all")` após escrever `/collect2/countrycode.txt` e chamar a API MCU de configuração (**mexe na configuração de país**: só com o usuário ciente e uma única vez).
2. Concessionária/serviço BYD, informando: carro em P, `fission-all` já tentado uma vez, arquivo movido para `/sdcard/.cluster/retired/`.
3. Não: reboots em loop, mudar propriedades, apagar journal/prefs, montar `/system`.

## 8. O que este carrier muda e o que não muda (para a decisão de risco)

- **Muda:** `qml10_25/cluster.qml` (inserções `HORIZON_BEGIN/END`: propriedades `horizonWanted/horizonActive/horizonAdas3d`, um `Loader` z=1, `opacity` em `alwyasDisplayId`, `bgImageContainerId`, 3 itens da janela ADAS 2D) e `qml10_25/WarinLightLayout.qml` (id + flag, `navigationFlag` OU `horizonActive`, opacidade da placa TSR). Tudo condicionado a `horizonActive`; sem ele os valores são os de fábrica.
- **Não muda:** os 80 locales do `commonTranslate` (byte a byte), o root do theme1, o `Center.qml`/menu/pop-ups/`adasUi` 2D (Center), as animações de ligar, o `Presentation`, os `.rcc` de fábrica em `/system`.
- Risco que **não** é fail-open: erro de sintaxe/instanciação no **root patchado** (verificado só por `qmllint` Qt 6 e por render com stubs; não no Qt 5.15 do cluster).
