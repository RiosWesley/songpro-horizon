# Song Pro Horizon: instalar e usar

O Song Pro Horizon é feito para o **BYD Song Pro com DiLink 3.0 e painel de 8,8"
(firmware SX309)**. Ela faz três coisas:

1. **Android Auto pelo cabo**, com o mapa do celular (Waze/Maps) no painel de instrumentos.
2. **Instala o tema de painel Horizon** e **volta ao painel original** com um toque.
3. **Escolhe quem atende o celular no cabo:** o Song Pro Horizon ou o Android Auto nativo da BYD.

O app tem cinco seções numa barra à esquerda:

| Seção | O que tem |
|---|---|
| **Início** | O estado (esperando o celular ou conectado), o botão de conectar ou de abrir o Android Auto, três linhas de estado e o painel como fica com o Horizon |
| **Painel** | Instalar, atualizar ou tirar o Horizon, reiniciar o painel, a vaga Simples, o medidor de potência, o card da esquerda e o formato do mapa |
| **Android Auto** | Quem atende o cabo (este app ou o da BYD), abrir sozinho, mapa e letra no painel, qualidade da imagem e tamanho dos elementos |
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

- **Instalar Horizon:** o app monta o tema **no próprio carro**, a partir dos arquivos de fábrica
  dele. Nada da BYD vem dentro do app. Depois de montar, o app coloca o tema em
  `/sdcard/.cluster/translate/` e o seleciona. Por último, pergunta se pode **reiniciar o painel**:
  o painel e a multimídia apagam e voltam em cerca de 1 minuto, já com o Horizon.
- **Voltar ao original:** o app move o tema para `/sdcard/.cluster/retired/` (não apaga nada) e
  pede para reiniciar. Sem o arquivo, o painel volta sozinho ao de fábrica. Este é o caminho de
  volta comprovado no carro. O "restaurar padrão" da BYD não funciona neste firmware.
- **O Horizon no menu de tema do painel:** o Horizon ocupa a vaga do **Simples** no menu do próprio
  painel (botão voltar → Tema); o **Clássico** continua de fábrica. Trocar entre os dois por esse menu não
  precisa do app nem de reiniciar. Na troca, a BYD toca a animação dela e o Horizon espera ela acabar.
- **Tema na vaga Simples:** Painel → Na vaga Simples → **Horizon** ou **Simples original**. O
  "Simples original" devolve a vaga ao Simples de fábrica sem tirar o Horizon do carro. O menu no volante
  também tem essa opção ("Tema do painel"), mas, como o menu é desenhado pelo Horizon, a volta para o
  Horizon só pelo app.
- **Modos de condução:** o fundo do Horizon muda de cor com o modo (ECO verde, NORMAL azul, SPORT
  laranja), com uma transição de quase 1 s.
- **Telas de fábrica no centro:** quando o painel abre o menu, um alerta (EPB, portas, pneus…), o
  cronômetro de 0 a 100, o carregamento ou o resumo da viagem, a estrada do ADAS esmaece. Os alertas
  ganham um cartão do Horizon atrás deles. A faixa de música de fábrica fica escondida, porque o Horizon
  mostra a música.
- **Medidor de potência:** o lado direito do Horizon tem 5 estilos — Anel, Segmentos, Arco lateral,
  Coluna e Faixa e onda (com o gráfico do último minuto). Troque em **Painel → Medidor de
  potência → Trocar** ou pelo menu no volante; muda na hora, sem reiniciar o painel.
- **Formato do mapa:** **Janela** (no centro do Horizon, com o ADAS e os cards em volta) ou **Tela
  cheia** (o mapa no painel inteiro; o Horizon vira só uma moldura com velocidade, marcha e
  potência em cima e a barra de baixo). Troque em **Painel → Mapa no painel** ou pelo
  menu no volante. Muda **na hora**, também com o celular conectado e o carro andando: com o Horizon,
  o vídeo do mapa é sempre combinado para o painel inteiro, e trocar o formato só move a imagem
  (66 px para cima na janela, para a seta do carro cair dentro dela), muda o recorte do Horizon e
  avisa o celular onde pôr os cartões.
- O mapa e o botão flutuante são janelas sobrepostas, que precisam da permissão de sobreposição
  (`SYSTEM_ALERT_WINDOW`). Uma instalação nova começa sem ela, e sem ela o painel abre a projeção
  mas fica um quadro preto. Por isso o app a concede a si mesmo pelo ADB local quando o celular conecta.
- Nos dois formatos, o vídeo do mapa é maior que a área livre e o Horizon esfuma por cima dessa
  sobra; o app pede ao celular (insets de conteúdo do Android Auto) que mantenha os cartões de
  manobra e de chegada na área livre, e repete o pedido a cada troca de formato. *Não confirmado no carro:* se o Android Auto respeita esses
  insets no painel de instrumentos.
- Ao instalar, o app já ajusta o mapa para a janela central do Horizon. Ao voltar ao original, o
  mapa volta à faixa livre do painel de fábrica.

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

## Se algo der errado

- Painel estranho depois de instalar: **Painel → Voltar ao original**, e reiniciar o painel quando o app oferecer.
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
