# Estudo: o Horizon na lista de temas do painel, e os alertas

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
