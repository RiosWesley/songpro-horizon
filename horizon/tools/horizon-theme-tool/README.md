# horizon-theme-tool

Select / query / restore the Horizon cluster theme on the Song Pro 8.8" (SX309) without DashCast.
Everything here is built and tested **offline**; nothing in this directory runs on the car by itself.

| File | Purpose |
|---|---|
| `src/HorizonThemeTool.java` | CLI: `query`, `load`, `restore`, `payloads`; one request per run; exit codes 0/2/3/4 |
| `src/Bridge.java` | pinned Binder link to `AutoContainer` (tx 3 `sendInfo2`, tx 4 `registerCallback`, callback tx 4) |
| `src/FlatBuf.java` | hand-written FlatBuffers request builder + bounds-checked response decoder |
| `src/Cohort.java`, `src/Policy.java` | SHA-256 cohort refusal; names, paths, OEM error codes |
| `build.sh` | `javac -source 8` + `d8 --min-api 29` → `build/horizon-theme-tool.dex` |
| `test.sh` | desktop JVM test: payload bytes == real flatbuffers-java == `RECUPERACAO.md`; decoder; 3000 corrupted buffers |
| `install.sh` | PRINTS the `adb push` / `mv` / `app_process` commands; executes nothing |

Protocol and pinned values come from the DashCast project (MIT, https://github.com/Kiroha/byd-dashcast)
and from files read off this car; see `docs/estudos/tema-cluster-88.md` (Rodada 5) and `RECUPERACAO.md` §3–§4.
