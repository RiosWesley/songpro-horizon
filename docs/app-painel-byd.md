# Song Pro Horizon: instalar e usar

O Song Pro Horizon é feito para o **BYD Song Pro com DiLink 3.0 e painel de 8,8"
(firmware SX309)**. Ela faz três coisas:

1. **Android Auto pelo cabo**, com o mapa do celular (Waze/Maps) no painel de instrumentos.
2. **Instala o tema de painel Horizon** (com backup do painel original antes) e **volta ao painel de
   fábrica**, sem nenhuma modificação, com um toque.
3. **Escolhe quem atende o celular no cabo:** o Song Pro Horizon ou o Android Auto nativo da BYD.

O app tem cinco seções numa barra à esquerda:

| Seção | O que tem |
|---|---|
| **Início** | O estado (esperando o celular ou conectado), o botão de conectar ou de abrir o Android Auto, três linhas de estado e o painel como fica com o Horizon |
| **Painel** | Instalar ou atualizar o Horizon, voltar à fábrica, reiniciar o painel, a vaga Simples, o medidor de potência, o card da esquerda e o formato do mapa |
| **Android Auto** | Quem atende o cabo (este app ou o da BYD), abrir sozinho, sem fio pelo Bluetooth, mapa e letra no painel, qualidade da imagem e tamanho dos elementos |
| **Volante** | O menu do Horizon no volante, o botão que abre o menu e o que cada tecla faz |
| **Sobre** | Versão, créditos e **Ajustes avançados**, com todos os ajustes herdados do Open Headunit |

O visual segue um passe de design com a skill [unslop](https://github.com/heydamir/unslop) (Damir
Sokolovsky, CC BY 4.0): fundo neutro escuro, o azul do Horizon como único destaque e a imagem do
próprio painel como o momento de cor.

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

- **Primeira abertura:** num carro sem o Horizon, o app abre a seção Painel e oferece a instalação
  uma vez.
- **Instalar Horizon:** o app monta o tema **no próprio carro**, a partir dos arquivos de fábrica
  dele. Nada da BYD vem dentro do app. Na primeira instalação, antes de colocar qualquer coisa, o app
  guarda o painel original em `/sdcard/SongProHorizon/original/` (cerca de 290 MB):
  - os arquivos de fábrica do painel;
  - o que havia na pasta de temas;
  - o tema selecionado, o firmware, o estado do Android Auto da BYD e os serviços de acessibilidade;
  - um `SHA256SUMS` de tudo, conferido com os originais.

  Como há variações do Song Pro, cada carro guarda o seu. Depois o app coloca o tema em
  `/sdcard/.cluster/translate/` (uma atualização move a versão anterior para `retired/`), o
  seleciona e pergunta se pode **reiniciar o painel**: o painel e a multimídia apagam e voltam em
  cerca de 1 minuto, já com o Horizon.
- **Voltar à fábrica** (só no app, Painel): devolve o painel exatamente como veio, sem nenhuma
  modificação:
  - apaga tudo o que é do Horizon: o tema, as versões antigas em `retired/`, os arquivos que o app
    escreve para ele e a ferramenta do tema;
  - remove as pastas do painel que ficarem vazias (o carro veio sem `/sdcard/.cluster`);
  - religa o Android Auto da BYD e desliga o menu do volante e o Android Auto sem fio;
  - pede para reiniciar.

  Sem o arquivo do tema, o painel grava sozinho a seleção de fábrica e mostra o de fábrica: é o
  caminho de volta comprovado no carro. O "restaurar padrão" da BYD não funciona neste firmware. Ficam
  só o app e o backup do original.
- **O Horizon no menu de tema do painel:** o Horizon ocupa a vaga do **Simples** no menu do próprio
  painel (botão voltar → Tema); o **Clássico** continua de fábrica. Trocar entre os dois por esse menu não
  precisa do app nem de reiniciar. Na troca, a BYD toca a animação dela e o Horizon espera ela acabar.
- **Tema na vaga Simples:** Painel → Na vaga Simples → **Horizon** ou **Simples original**. O
  "Simples original" devolve a vaga ao Simples de fábrica sem tirar o Horizon do carro. O menu no volante
  também tem essa opção ("Tema do painel"), mas, como o menu é desenhado pelo Horizon, a volta para o
  Horizon só pelo app.
- **Modos de condução:** o fundo do Horizon muda de cor com o modo (ECO verde, NORMAL azul, SPORT
  laranja), com uma transição de quase 1 s.
- **Carro seguido:** com o ACC seguindo um carro, a estrada do ADAS mostra a faixa do seu carro até ele,
  colorida pela distância (vermelho perto, laranja, verde longe), como no painel de fábrica. O ADAS 3D
  de fábrica tem essa faixa, mas a BYD deixou o código comentado; o tema o liga de novo.
- **Telas de fábrica no centro:** quando o painel abre o menu, um alerta (EPB, portas, pneus…), o
  cronômetro de 0 a 100, o carregamento ou o resumo da viagem, a estrada do ADAS esmaece. Os alertas
  ganham um cartão do Horizon atrás deles. A faixa de música de fábrica fica escondida, porque o Horizon
  mostra a música.
- **Medidor de potência:** o lado direito do Horizon tem 5 estilos — Anel, Segmentos, Arco lateral,
  Coluna e Faixa e onda (com o gráfico do último minuto). Troque em **Painel → Medidor de
  potência → Trocar** ou pelo menu no volante; muda na hora, sem reiniciar o painel.
- **Formato do mapa:** três formatos, trocados em **Painel → Mapa no painel** ou pelo menu no
  volante. Mudam **na hora**, também com o celular conectado e o carro andando.
  - **Janela:** o mapa no centro do Horizon, com o ADAS numa janela à esquerda e os cards em volta.
  - **Lateral:** o mapa no lado direito, no lugar do medidor de potência, espelhando o card da
    esquerda; o ADAS fica centralizado sob a velocidade e a potência vira uma barra horizontal em
    cima.
  - **Tela cheia:** o mapa no painel inteiro. O Horizon guarda só uma faixa em cima (velocidade,
    marcha e potência), põe um fundo escuro atrás de cada grupo da barra de baixo e mostra o ADAS
    numa janelinha à direita, como a de fábrica na navegação.

  O painel mostra só o mapa, a seta, a rota e os nomes de rua: os cartões do Android Auto (manobra,
  distância, tempo e chegada) ficam de fora, porque a multimídia já mostra tudo isso. Quem desenha os
  cartões é o celular, e ele não aceita deixar de desenhar: uma área livre pequena demais para eles é
  ignorada, e a seta cai para o pé da imagem (medido no carro). Por isso o app pede a imagem inteira de
  1280×720, diz ao celular que a área livre é ela toda (a seta fica na linha 360 e os cartões perto do
  topo e do pé, ~98 px para dentro) e desenha só a faixa do meio, das linhas 175 a 545. Cada formato
  põe essa faixa na sua abertura: Tela cheia em tamanho real, Janela a 0,93 e Lateral a 0,876, para
  cobrir também as bordas esfumadas. No build de depuração, `ClusterLayoutProbe` troca a área, a
  posição e a faixa ao vivo pelo ADB, e o app salva uma captura do painel alguns segundos depois de o
  mapa aparecer ou mudar de formato (`/data/local/tmp/horizon-map-<formato>.png`).
- **Ligar o mapa:** pelo menu no volante ("Mapa no painel"). Não há mais botão na tela.
- O mapa é uma janela sobreposta, que precisa da permissão de sobreposição
  (`SYSTEM_ALERT_WINDOW`). Uma instalação nova começa sem ela, e sem ela o painel abre a projeção
  mas fica um quadro preto. Por isso o app a concede a si mesmo pelo ADB local quando o celular conecta.
- Na Janela e na Lateral, a imagem do mapa passa da área livre e o Horizon esfuma por cima dessa
  sobra.
- *Em estudo:* com o mesmo Waze, o celular de outra pessoa mostrou no painel uma visão reta de cima
  sem rota (a imersiva só voltava com rota), enquanto a tela da multimídia ficava normal. O app
  manda o mesmo pedido para qualquer celular; a diferença deve estar na versão ou na exibição do
  mapa (2D/3D) do Waze daquele celular.
- Ao instalar, o app já ajusta o mapa para a janela central do Horizon. Ao voltar ao original, o
  mapa volta à faixa livre do painel de fábrica.

### Sensores de estacionamento no painel

Quando os sensores de estacionamento veem algo (na ré, por exemplo), o Horizon mostra o carro
visto de cima no lugar da estrada do ADAS. Cada zona tem três faixas: verde longe, amarela, vermelha
perto. Ao lado aparece a menor distância em cm. Quatro zonas na frente, cinco atrás. A estrada
esmaece por trás e volta quando os sensores param de ver.

Um app comum não pode ler os sensores (a permissão da BYD é de sistema). Por isso o app leva um
leitor pequeno, só de leitura (`horizon/tools/park-feed`), que roda como shell pelo ADB local e
grava `park.json` ao lado do arquivo da música. O app liga o leitor sempre que abre com o Horizon no
carro (o app acorda no boot), ao instalar o Horizon e quando um celular conecta; "Voltar à fábrica"
o desliga e apaga. *Medido no carro:* as cinco zonas de trás em R (docs/estudos/adas-em-p.md). As da
frente ainda não foram testadas.

### Menu no volante (opcional, desligado por padrão)

Um botão do volante abre o menu do Horizon no painel, no lugar do card da esquerda. É o mesmo
recurso de remapear botões que o Electro usa: um serviço de acessibilidade que só lê os botões do
volante.

- **Ligar:** Volante → Menu do Horizon no volante. O app ativa o serviço sozinho, pelo ADB local.
- **Botão:** por padrão é o botão programável da BYD (`AUTO_CUSTOM_KEY`). Para usar outro, toque
  **Escolher botão** e aperte o botão do volante que você quer.
- **No painel:** toque curto abre e fecha o menu, ◁ ▷ escolhem o item e o clique do rolete muda o
  valor. O menu fecha sozinho depois de 10 s sem tecla.
- **Com o menu fechado, nada muda:** ◁ ▷ continuam trocando a música, e o rolete o volume. O app só
  segura essas teclas com o menu aberto. O toque longo do botão programável vem com outro código e
  segue com a função de fábrica.
- **Itens:** card da esquerda (Automático: música quando tem música, senão viagem; Música; Viagem),
  medidor de potência, formato do mapa e, com o celular conectado, mapa no painel.

> Não confirmado no carro: qual código cada botão manda, e se a BYD deixa segurar o botão
> programável antes de girar a tela. Com o menu ligado, o registro do app mostra cada tecla
> (`WheelMenu: key …`).

### Android Auto pelo cabo

- **Song Pro Horizon:** desativa o Android Auto da BYD (`pm disable-user`; nada é desinstalado) e liga
  a conexão automática do app quando o celular é plugado.
- **AA nativo da BYD:** religa o app da BYD e desliga a conexão automática deste app.
- Depois de trocar, **desconecte e conecte o celular de novo**.

### Android Auto sem fio (opcional, desligado por padrão)

Como num carro com AA sem fio de fábrica: quando o carro conecta o Bluetooth do celular, o Android Auto
abre sem cabo.

- **Ligar:** Android Auto → **Sem fio pelo Bluetooth**. Na primeira vez o app abre a lista de
  aparelhos pareados para escolher o seu celular (dá para trocar em **Celular**). Sem nenhum escolhido,
  vale qualquer celular pareado que pedir.
- **Como funciona:** é o modo "Native AA" do Open Headunit. O Bluetooth da central anuncia o Android
  Auto; o celular, ao conectar no carro, liga de volta por esse canal, recebe a senha de uma rede Wi-Fi
  Direct que o app cria, entra nela e projeta. O Wi-Fi da central continua livre para entrar numa rede,
  e as ligações continuam no Bluetooth da BYD (o app não registra um viva-voz próprio).
- O app se concede a localização pelo ADB local, porque o Android 10 só informa a rede Wi-Fi Direct a
  quem tem essa permissão.
- O celular escolhido também acorda o app quando conecta o Bluetooth, como no início automático do
  Open Headunit.
- Só com o Song Pro Horizon atendendo o Android Auto. Escolher o AA da BYD ou **Voltar à fábrica**
  desliga o sem fio.
- No celular: Android Auto com "Android Auto sem fio" ligado (Android 11 ou mais novo).

> Não testado no carro: se o Bluetooth da BYD aceita o anúncio do Android Auto, se o Wi-Fi Direct
> sobe junto com o Wi-Fi da central e a qualidade do vídeo sem fio com o mapa no painel ao mesmo tempo.

## Se algo der errado

- Painel estranho depois de instalar: **Painel → Voltar à fábrica**, e reiniciar o painel quando o app oferecer.
- Sem o app, pelo PC, tire o tema de cima à mão (detalhes em [../horizon/RECUPERACAO.md](../horizon/RECUPERACAO.md) §5):

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
| Leitor dos sensores de estacionamento (roda como shell) | [`horizon/tools/park-feed/`](../horizon/tools/park-feed) (o `build.sh` gera o dex que o app leva) |
| Seções e barra lateral | `main/HorizonSidebar.kt`, `HomeFragment.kt` (Início), `BydPanelFragment.kt` (Painel), `AndroidAutoFragment.kt`, `WheelFragment.kt`, `HorizonAboutFragment.kt` |
| Visual das seções | `res/values/horizon_ui.xml` (cores, textos, botões), `res/drawable/hz_*` (ícones, marca, medidores) |
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
