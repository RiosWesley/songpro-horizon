# Teste no carro — tema Horizon + mapa do open-headunit (Song Pro 8,8", SX309)

Ordem obrigatória: cada fase só começa se a anterior passou. Carro **em P, freio de mão puxado,
parado**, notebook com carga, adb conectado (`adb -s <ip-da-multimídia>:5555`). A recuperação detalhada
(comandos exatos, payloads e matriz de falhas) está em [`RECUPERACAO.md`](RECUPERACAO.md).

Artefatos:
- Tema: `out-carrier/OFFLINE_ONLY_horizon_sx309_88.rcc` (SHA-256 `ac00975e75992bc64c5a53a28206a73d8cde0f7f5558e8d7d9e25e269579676a`,
  103 entradas; `manifest.json` e `SHA256SUMS` ao lado). Só é selecionável depois de renomeado (ver §3 do RECUPERACAO).
- Ferramenta de seleção: `tools/horizon-theme-tool/` (consulta / carrega / restaura).
- open-headunit: branch `feat/cluster-display-output` (feed de música + letra, botão MAPA, assinatura adb corrigida).

---

## Fase 1 — open-headunit sozinho (o cluster não muda)

1. Instalar o APK novo (as configurações e a autorização adb se mantêm).
2. Tocar música no Spotify do celular, com o Android Auto conectado.
3. Conferir o feed que o tema vai ler:
   ```bash
   adb -s <ip-da-multimídia>:5555 shell 'cat /sdcard/.cluster/horizon/now_playing.json; ls -la /sdcard/.cluster/horizon/'
   ```
   Esperado: `seq` aumentando a cada leitura, título/artista corretos, `art_*.jpg|png` presente.
   Com internet na multimídia (hotspot do celular), `lyrics` preenchido em músicas com letra no LRCLIB.
4. Retomar o experimento pendente do botão MAPA (16→35 logo após 12/53) e anotar o resultado.

**Passa se:** o JSON é escrito e atualizado. Letra é bônus (depende de internet e do LRCLIB).

## Fase 2 — ensaios de recuperação, ainda sem tema

1. `query` com a ferramenta (somente leitura): registra o estado de fábrica e confirma que o
   protocolo deste firmware responde como esperado.
2. Um único `adb reboot fission-all` com o painel **normal**, para saber como é o reinício do host
   e do cluster neste carro e quanto tempo leva ("Restarting" → instrumentos BYD normais).

**Passa se:** a consulta decodifica e o painel volta normal depois do reinício. Se qualquer um
falhar, **parar aqui** — sem caminho de recuperação comprovado não se carrega tema.

## Fase 3 — o tema

1. Copiar o carrier com o nome selecionável para `/sdcard/.cluster/translate/` (único `.rcc` lá).
2. `load <nome>` com a ferramenta: um pedido só, esperar os ACKs (troca só vale em P).
3. Observar no painel, nesta ordem:
   - [ ] luzes de alerta aparecem (arranjo de bordas), "OK" no centro inferior
   - [ ] velocidade, limite, marcha, bateria, combustível, hora, ODO corretos
   - [ ] ADAS 3D no centro com o seu carro na pista (alvos quando houver trânsito)
   - [ ] card de música com capa; letra se a Fase 1 mostrou `lyrics`
   - [ ] setas esquerda/direita do volante alternam música ↔ viagem; o menu nativo continua abrindo
   - [ ] potência e gráfico do último minuto respondem ao acelerar/frear (com o carro ligado, em P)
4. Mapa: no open-headunit, `cluster_display_rect = 390,100,500,292` (o quadro do tema) e botão MAPA.
   - [ ] o Waze aparece no quadro central, com bordas suaves; ADAS 3D na janelinha à esquerda
   - [ ] o painel ADAS 2D nativo não cobre o topo do mapa (risco conhecido; anotar se cobrir)
5. Voltar ao normal: `restore` com a ferramenta e conferir o painel de fábrica.

**Parar e recuperar** (RECUPERACAO §4 → §5) se: tela preta, "Simple mode", instrumentos faltando,
"m/h", luzes ausentes ou qualquer coisa estranha. Nunca recarregar o mesmo arquivo que falhou.

## O que o teste NÃO cobre e fica para depois
- Dirigir: todo o teste é parado. A primeira volta só depois de tudo da Fase 3 passar.
- Desempenho do ADAS 3D no QCM6125 (a BYD deixou o 3D comentado nesta firmware; observar se o
  painel fica lento).
- Se o host não conseguir ler `.cluster/horizon/`, o card mostra só "título - artista" do texto
  nativo; aí a letra precisa de outro canal (propriedade de mídia do instrumento).
