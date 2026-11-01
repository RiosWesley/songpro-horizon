# Estudo: o Horizon como tema oficial (lista de temas, alertas e o centro do painel)

Feito em 2026-11-01, só lendo o QML de fábrica (`cluster_theme2.rcc`, pasta `qml10_25/`) e a `libBydDataSource.so`
do carro. Nada foi testado no carro.

## 1. A lista de temas do painel

**Como funciona.** No menu do painel (volante → menu → Tema) há só duas opções, fixas no QML
(`menu/ThemeSwitch.qml`): `themeIndex` 1 = **Clássico** e 2 = **Simples**. Cada uma é uma imagem de prévia
(`images10_25/menu/{day,night}/theme_classic_*.png` e `theme_simple_*.png`, 496×199) e um texto
(`TranslateStr.menu_themeClassicStr` / `menu_themeSimpleStr`). As setas só animam entre as duas; o OK manda
`RECV_MSG_ID_MENU_THEME_PAGE` com o índice escolhido (`menu/SettingMenu.qml`).

**Quem decide é a biblioteca.** `BusinessUi1::updateThemeIndex(int)` (`0x11c43c`) roda a troca, com a animação de
fábrica (`ThemeChangeAnimation.qml`, mensagens `THEME_SWITCH_REQUEST_*`), e grava a escolha (`m_u8ThemeIndexStore`,
chave `theme_index` na configuração). No fim ela só **alterna**: se o atual é 2 passa a 1, se é 1 passa a 2. Um
terceiro valor não existe para ela. A raiz (`cluster.qml`) troca os mostradores em `onThemeIndexChanged`.

**Conclusão.**

- **Um terceiro tema na lista: não dá** sem mudar a biblioteca da BYD, que fica em `/system` e não mexemos.
- **Ocupar uma das duas vagas: dá, só com o carrier.** Exemplo com a vaga 2:
  - a raiz só liga o Horizon quando `DataSource.themeIndex === 2` (e não durante a animação de troca);
  - o `ThemeSwitch.qml` mostra "Horizon" e uma prévia do Horizon no lugar de "Simples".

  Com isso, escolher Clássico ou Horizon passa a ser pelo menu do próprio painel, com a animação de fábrica, sem
  reiniciar e sem o app. O app continua servindo para instalar e para remover de vez.
- **É reversível?** Sim, tanto quanto o Horizon de hoje. A troca de vaga não cria nenhum estado novo fora do
  carrier: o arquivo fica em `/sdcard/.cluster/translate/`, nada em `/system` muda, e a biblioteca continua
  guardando só 1 ou 2, valores de fábrica. Há dois níveis de volta:
  - no próprio menu do painel, escolher a outra vaga (o tema de fábrica);
  - de vez, pelo "Voltar ao original" do app ou movendo o carrier para `retired/` com um `fission-all`. A vaga
    volta a ser o Simples de fábrica, porque o QML alterado sai junto com o carrier. Esse caminho já foi
    comprovado no carro.

  Os limites são os de sempre: a volta foi comprovada partindo de um Horizon que funcionava, não de uma raiz
  quebrada, e o Horizon deve ser removido antes de qualquer OTA que mexa na multimídia.
- **Cuidados:**
  - a raiz de fábrica muda algumas posições conforme o tema (cabeçalho, autonomia, luzes), então a vaga escolhida
    tem de ser conferida com o Horizon por cima;
  - a troca animada só acontece com o carro ligado (`BODYWORK_POWER_LEVEL` = 3); fora disso a biblioteca só grava
    o índice.

## 2. Os alertas (EPB, portas, pneus…)

**Por que aparecem soltos sobre o ADAS.** Os alertas são do `Center` de fábrica (z 3, acima do Horizon), não do
tema. Cada tipo é um componente em `warningInfo/` (`CommonWarningInfo`, `SpeedWarningInfo`, `CarBody`,
`CarKeyWarningInfo`, `MaintainWarningInfo`, `GenerPowerWarningInfo`, `CumErrorWarningInfo`, `DmsWarningInfo`,
`TireInfo`…), centrado, em y 197 no desenho de 1920×720. Todos têm o mesmo cartão de fundo
(`images10_25/warningInfo/{day,night}_bg.png`, retângulo escuro arredondado de 658×396), mas ele só aparece com
`naviType === 4` (navegação em tela cheia) ou num modo de terreno. No tema normal a BYD conta com o fundo liso dela
atrás do alerta. Com o Horizon, quem fica atrás é a estrada do ADAS 3D, e o alerta se mistura com ela (foto do
"EPB ativado" sobre o carro).

**Como o Horizon sabe que há um alerta.** O `Center` mostra cada tipo quando uma propriedade do `DataSource` vale
`UI_DISPLAY`, e o Horizon lê o mesmo `DataSource`:

- `commonWarningInfoNeedDisplay`, `speedWarningInfoNeedDisplay`, `cumErrorWarningInfoNeedDisplay`;
- `generPowerWarningInfoNeedDisplay`, `carBodyNeedDisplay`, `modeSwitchWarningInfoNeedDisplay`;
- `tireWarningInfoNeedDisplay` ou `tirePressureAbnormalNeedDisplay`, `phonePromptNeedDisplay`;
- `maintainWarningInfoDisplay`, `dmsAnimNeedDisplay`;
- `carKeyWarningInfoDisplay` de 1 a 0xE.

**Proposta, sem mexer no QML de fábrica:** enquanto qualquer uma estiver ligada, o Horizon desenha o próprio cartão
no lugar do de fábrica (x 631, y 197, 658×396 no desenho; 421, 131, 439×264 no painel), no estilo dele. Ele também
esmaece a estrada do ADAS por baixo e volta ao normal quando o alerta some. Como o Horizon fica abaixo do `Center`,
o alerta de fábrica continua por cima, com o texto e o ícone dele. No modo mapa o cartão cobre também o mapa.

A outra saída seria mudar a regra do cartão em cada um dos ~10 componentes de alerta. Isso aumenta muito o que o
carrier altera nos arquivos de fábrica, e cada arquivo precisaria de verificação própria.

## 3. Tudo o que o painel de fábrica mostra no centro (menu do volante e telas automáticas)

O `Center` de fábrica (z 3, acima do Horizon) mostra uma tela de cada vez, por prioridade, e o Horizon não esconde
nenhuma delas: só as páginas de informação de condução e a janela grande do ADAS 2D cedem a ele (patch do
`Center.qml`). Cada tela abre quando um dado do `DataSource` muda, e todos esses dados existem na biblioteca deste
carro (conferido nas strings da `libBydDataSource.so`). Coordenadas no desenho de 1920×720.

**Menu do volante** (botão voltar; `menuNeedDisplay`). `SettingMenu`, 752×453 centrado em y 144, **com fundo
próprio** (`menu_bg.png`). As páginas (`FirstMenu.qml`), com o número de página que a biblioteca usa:

| Página | O que faz | Com o Horizon hoje |
|---|---|---|
| 1 Informação de condução | Escolhe a página de consumo, pneus, autonomia, viagem… | Funciona; as páginas em si não aparecem fora do menu, porque o Horizon mostra energia e viagem |
| 2 Navegação | Tipo de navegação (sem, pequena, tela cheia) | Funciona; muda `naviType`, que o Horizon não usa |
| 3 Lembrete de velocidade | Ajusta o aviso de velocidade | Funciona; o aviso é um alerta (abaixo) |
| 4 Tema | Clássico / Simples | Funciona; com a troca de vaga, vira Clássico / Horizon |
| 5 Temperatura do ar | Ajuste do ar | Funciona |
| 7 Volume do ar | Ventilação | Funciona |
| 6 Falhas históricas | Lista de falhas | Funciona |
| 8 Aceleração | Liga o cronômetro de 0 a 100 | Abre a tela do cronômetro (abaixo) |

O menu é legível, porque tem fundo, mas a estrada do ADAS continua andando em volta e por baixo das bordas dele.

**Telas que o painel abre sozinho:**

| Tela | Dado que abre | Como é | Com o Horizon hoje |
|---|---|---|---|
| Alertas (EPB, portas, pneus, chave, manutenção, modo, velocidade, DMS…) | os `*NeedDisplay` da seção 2 | Ícone e texto em y 197, cartão só na navegação | Soltos sobre o ADAS (foto) |
| Cronômetro 0–100 / 0–50 | `speedUpTimerInterfaceDisplay` | Tela inteira com o fundo de cidade de fábrica (`bg_city.png`) | Cobre o Horizon com o visual de fábrica |
| Carregando / descarregando (V2L) | `chargeNeedDisplay`, `dischargeNeedDisplay` | Tela inteira com fundo próprio (`BG.png`) | Cobre o Horizon com o visual de fábrica |
| Resumo da viagem (ao desligar) | `journeyInterfaceDisplay` | Tela inteira **sem** fundo | Sobre o Horizon, provavelmente misturado como os alertas |
| Navegação nativa simples e dicas | `naviType` 2 + `naviState`, `mapSendStatus` | Faixa no centro, sem fundo | Sobre o ADAS |
| Alerta de porta (DOW) | `dowWarningState` | Painel 608×290, sem fundo | Sobre o ADAS |
| Fluxo de energia de teste | `performanceMode` | Centro | Sobre o ADAS |
| Música de fábrica | `mediaInfoNeedDisplay` (na raiz, x 788 y 92) | Faixa no topo, com letra | Escondida pelo fundo do Horizon, mas aparece de leve atrás do km/h no mapa em tela cheia |

O que fica como está, sem problema: luzes de alerta, dicas do ADAS (z 111), janela de distância do ACC e as
animações de ligar e de chegada.

## 4. Plano para o Horizon tratar tudo isso

**Fase A — a base, que já resolve tudo o que está feio:**

1. **Vaga de tema** (seção 1). A raiz liga o Horizon só na vaga escolhida e o esconde durante a animação de troca;
   o menu de tema mostra "Horizon" com uma prévia dele.
2. **Palco do centro.** O Horizon lê os mesmos dados de abertura (tabelas acima). Enquanto uma tela de fábrica
   estiver no centro:
   - a estrada do ADAS esmaece;
   - nas telas sem fundo (alertas, resumo da viagem, navegação nativa, DOW), o Horizon desenha o próprio cartão
     no lugar certo;
   - o menu e as telas cheias já trazem fundo, então só esmaecem a estrada.

   Assim tudo que é de fábrica continua funcionando como de fábrica, só que legível.
3. **Música de fábrica escondida** com o Horizon ligado (patch na raiz). Isso também acaba com a letra que aparece
   atrás do km/h no mapa em tela cheia.

**Estado (2026-11-01):** a Fase A está pronta no código e só foi testada no mock. Estão nela a vaga do
Simples (`themeIndex` 2, com "Simples original" escolhido no app em `menu.json` `"skin"`), o palco do centro
(`FactoryCentre.qml`, `WarningCard.qml`) e a música de fábrica escondida. Junto com ela entrou a cor do
fundo por modo de condução. Falta validar tudo no carro.

**Fase B — as telas cheias no visual do Horizon.** O cronômetro de 0 a 100, o carregamento e o resumo da viagem
passam a ser desenhados pelo Horizon, com os mesmos dados da biblioteca:

- 0 a 100: `speedUp100km*`, `speedUp50km*`, `speedUpChrono*`, `speedUpTipText`;
- carregamento: `chargeBatSoc`, `chargePower`, `chargeTime*`, `chargeTip`;
- resumo da viagem: `journey*`, `usingPlan*`.

O `Center` deixa de criar a versão de fábrica enquanto o Horizon está ligado, como já faz com as páginas de
condução. Continua sendo a biblioteca que decide quando cada tela abre e fecha; o Horizon só desenha.

**O que fica de fábrica de propósito:** o menu do volante e as páginas dele. A navegação pelas teclas é um contrato
com a biblioteca (`RECV_MSG_ID_MENU_*`); refazer isso arrisca quebrar o menu, e com fundo e estrada esmaecida ele
já fica bom.

**Ordem sugerida:** Fase A, depois o layout 2 (que mexe na mesma geometria), depois a Fase B, uma tela por vez,
começando pelo 0 a 100.

**Como validar:** o mock não tem o `Center` real. Para as prévias, entram no mock imagens estáticas das telas de
fábrica (alerta, menu) por cima do Horizon. No carro, cada tela tem de ser provocada uma vez: abrir o menu,
puxar o freio de mão para o alerta do EPB, rodar um 0 a 100 e plugar o carregador.
