# Song Pro Horizon: instalar e usar

O Song Pro Horizon é feito para o **BYD Song Pro com DiLink 3.0 e painel de 8,8"
(firmware SX309)**. Ela faz três coisas:

1. **Android Auto pelo cabo**, com o mapa do celular (Waze/Maps) no painel de instrumentos.
2. **Instala o tema de painel Horizon** e **volta ao painel original** com um toque.
3. **Escolhe quem atende o celular no cabo:** o Song Pro Horizon ou o Android Auto nativo da BYD.

A tela inicial tem só dois botões: **USB** e **Painel BYD**. O resto do Open Headunit, a base do
app, continua lá, mas fica escondido. Um toque longo em **Painel BYD** abre as configurações completas.

> Projeto independente, sem relação com a BYD. Mexer no painel de instrumentos tem risco: siga os
> passos com o carro **parado, em P e com freio de mão**. A história completa e os detalhes técnicos
> estão em [historia-cluster-song-pro.md](historia-cluster-song-pro.md).

## Antes de instalar

- **ADB por rede ligado na multimídia** (o mesmo que DashCast e Electro pedem). O app fala com a
  própria multimídia por ADB local (`127.0.0.1:5555`). É assim que ele roda a ferramenta do tema e
  liga ou desliga o AA nativo. Na primeira vez, a multimídia pergunta se permite a depuração:
  marque **sempre permitir**.
- **Firmware SX309, painel de 8,8".** O app confere o firmware e os arquivos do painel antes de
  qualquer coisa. Em outro firmware, a instalação do tema fica desligada.

## Usar

### Tema do painel (Horizon)

- **Instalar Horizon:** o app monta o tema **no próprio carro**, a partir dos arquivos de fábrica
  dele. Nada da BYD vem dentro do app. Depois de montar, o app coloca o tema em
  `/sdcard/.cluster/translate/` e o seleciona. Por último, pergunta se pode **reiniciar o painel**:
  o painel e a multimídia apagam e voltam em cerca de 1 minuto, já com o Horizon.
- **Voltar ao original:** o app move o tema para `/sdcard/.cluster/retired/` (não apaga nada) e
  pede para reiniciar. Sem o arquivo, o painel volta sozinho ao de fábrica. Este é o caminho de
  volta comprovado no carro. O "restaurar padrão" da BYD não funciona neste firmware.
- **Medidor de potência:** o lado direito do Horizon tem 5 estilos — Anel, Segmentos, Arco lateral,
  Coluna e Faixa e onda (com o gráfico do último minuto). Troque em **Painel BYD → Medidor de
  potência → Trocar** ou pelo menu no volante; muda na hora, sem reiniciar o painel.
- Ao instalar, o app já ajusta o mapa para a janela central do Horizon. Ao voltar ao original, o
  mapa volta à faixa livre do painel de fábrica.

### Menu no volante (opcional, desligado por padrão)

Um botão do volante abre o menu do Horizon no painel, no lugar do card da esquerda. É o mesmo
recurso de remapear botões que o Electro usa: um serviço de acessibilidade que só lê os botões do
volante.

- **Ligar:** Painel BYD → Menu no volante. O app ativa o serviço sozinho, pelo ADB local.
- **Botão:** por padrão é o botão programável da BYD (`AUTO_CUSTOM_KEY`). Para usar outro, toque
  **Escolher botão** e aperte o botão do volante que você quer.
- **No painel:** toque curto abre e fecha o menu, ◁ ▷ escolhem o item e o clique do rolete muda o
  valor. O menu fecha sozinho depois de 10 s sem tecla.
- **Com o menu fechado, nada muda:** ◁ ▷ continuam trocando a música, e o rolete o volume. O app só
  segura essas teclas com o menu aberto. O toque longo do botão programável vem com outro código e
  segue com a função de fábrica.
- **Itens:** card da esquerda (Automático: música quando tem música, senão viagem; Música; Viagem),
  medidor de potência e, com o celular conectado, mapa no painel.

> Não confirmado no carro: qual código cada botão manda, e se a BYD deixa segurar o botão
> programável antes de girar a tela. Com o menu ligado, o registro do app mostra cada tecla
> (`WheelMenu: key …`).

### Android Auto pelo cabo

- **Song Pro Horizon:** desativa o Android Auto da BYD (`pm disable-user`; nada é desinstalado) e liga
  a conexão automática do app quando o celular é plugado.
- **AA nativo da BYD:** religa o app da BYD e desliga a conexão automática deste app.
- Depois de trocar, **desconecte e conecte o celular de novo**.

## Se algo der errado

- Painel estranho depois de instalar: **Painel BYD → Voltar ao original → Reiniciar**.
- Sem o app, pelo PC, faça a mesma coisa à mão (detalhes em [../horizon/RECUPERACAO.md](../horizon/RECUPERACAO.md) §5):

  ```bash
  adb -s <ip-da-multimídia>:5555 shell 'mkdir -p /sdcard/.cluster/retired && mv /sdcard/.cluster/translate/*TranslateHorizon_*.rcc /sdcard/.cluster/retired/'
  ```

  ```bash
  adb -s <ip-da-multimídia>:5555 reboot fission-all
  ```

- "ADB local indisponível": ligue o ADB por rede na multimídia e aceite o pedido de depuração.

## Para quem quer mexer

| O quê | Onde |
|---|---|
| QML do Horizon | [`horizon/qml/`](../horizon/qml) |
| Montagem do tema no carro (Kotlin) | `app/src/main/java/com/andrerinas/openheadunit/byd/theme/` |
| Montagem do tema no PC (mesmo resultado, byte a byte) | [`horizon/tools/build_horizon_sx309_88.py`](../horizon/tools/build_horizon_sx309_88.py) |
| Ferramenta que seleciona o tema (Binder, roda como shell) | [`horizon/tools/horizon-theme-tool/`](../horizon/tools/horizon-theme-tool) |
| Tela Painel BYD | `app/src/main/java/com/andrerinas/openheadunit/main/BydPanelFragment.kt` |
| Como criar um tema próprio | [guia-tema-cluster-song-pro.md](guia-tema-cluster-song-pro.md) |

O teste `HorizonCarrierTest` confere que o tema montado pelo app é idêntico ao do builder em Python.
Ele precisa dos arquivos de fábrica do carro, que não estão no repositório:

```bash
HORIZON_STOCK_DIR=<pasta com cluster_theme1.rcc, cluster_theme2.rcc, commonTranslate.rcc> HORIZON_REFERENCE_CARRIER=<saída do build_horizon_sx309_88.py> ./gradlew :app:testGithubDebugUnitTest --tests '*HorizonCarrierTest'
```

## Créditos

- Base: Open Headunit (AGPL-3.0, André Knieriem e colaboradores), Headunit Revived e o headunit
  original de Michael Reid.
- DashCast (MIT, Cedric Carre, <https://github.com/Kiroha/byd-dashcast>): o protocolo de seleção de
  tema e o formato do carrier vieram de lá; o codec RCC está em `horizon/tools/third_party/dashcast/`.
