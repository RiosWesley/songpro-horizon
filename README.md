# Song Pro Horizon

App Android para a multimídia do **BYD Song Pro com DiLink 3.0, painel de instrumentos de 8,8" e
firmware SX309**. Ele junta três coisas num app só:

- **Android Auto pelo cabo**, com o mapa do celular (Waze, Google Maps) no painel de instrumentos.
- **Horizon**, um tema próprio para o painel de instrumentos. O app monta o tema no próprio carro, a
  partir dos arquivos de fábrica dele, e volta ao painel original com um toque.
- **Quem atende o celular no cabo:** este app ou o Android Auto nativo da BYD.

A tela inicial tem só **USB** e **Painel BYD**.

> Projeto independente, sem relação com a BYD. Mexer no painel de instrumentos tem risco: instale,
> troque ou remova o tema só com o carro **parado, em P e com freio de mão**.

## Documentação

| | |
|---|---|
| [Instalar e usar o app](docs/app-painel-byd.md) | requisitos, Painel BYD, como voltar ao original |
| [História das descobertas](docs/historia-cluster-song-pro.md) | dos arquivos do painel até o estado atual |
| [Criar um tema de painel próprio](docs/guia-tema-cluster-song-pro.md) | passo a passo do básico |
| [Estudos](docs/estudos/) | tema do painel, AA nativo da BYD, dados do ADAS |
| [Recuperação](horizon/RECUPERACAO.md) | o que fazer se o painel ficar estranho |

## Estrutura

| Pasta | O quê |
|---|---|
| `app/` | o app Android. O código próprio fica em `byd/` (tema e AA nativo), `aap/cluster/` (mapa e música no painel) e `main/BydPanelFragment.kt` |
| `horizon/qml/` | o tema Horizon (QML) |
| `horizon/tools/` | montador do tema no PC, ferramenta que seleciona o tema, leitor de sinais do ADAS |
| `horizon/mock/` | renderização do tema no PC |
| `docs/` | documentação, em português |

## Compilar

```bash
./gradlew :app:assembleGithubDebug
```

O APK sai em `app/build/outputs/apk/github/debug/`. O pacote do app é `com.rioswesley.songprohorizon`.

## Licença e créditos

**AGPL-3.0** (veja [LICENSE](LICENSE)). O app é derivado do
[Open Headunit](https://github.com/andreknieriem/open-headunit) de André Knieriem e colaboradores.
Veio pelo fork de [moriceh](https://github.com/moriceh/open-headunit), que continua o
[headunit original de Michael Reid](https://github.com/mikereidis/headunit) (veja
[COPYRIGHT_MICHAEL_REID_GPLv3AFFERO.txt](COPYRIGHT_MICHAEL_REID_GPLv3AFFERO.txt)). O README original
está em [docs/README-open-headunit.md](docs/README-open-headunit.md), e o histórico de versões dele em
[CHANGELOG.md](CHANGELOG.md).

O protocolo de seleção de tema e o formato do carrier vieram do
[DashCast](https://github.com/Kiroha/byd-dashcast) (MIT, Cedric Carre). O codec RCC dele está em
`horizon/tools/third_party/dashcast/`.

Nenhum arquivo da BYD faz parte deste repositório: o app e o montador leem os arquivos de fábrica do
próprio carro.
