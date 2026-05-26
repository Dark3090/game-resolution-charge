# GameResChange (Fork)

> **Este é um fork do projeto original [GameResChange](https://github.com/Xposed-Modules-Repo/com.game.reschange) criado por [danmgk](https://t.me/danmgk).**
> Todo o crédito pelo conceito e código base pertence ao autor original.

Change the resolution of any app/game on Android 13+

Think of it as a privacy friendly version of Samsung's Game Booster/Game Optimizer Service. Samsung's app sends a list of all installed apps to Samsung servers (which is very invasive imo). The original app/module aims to achieve the same thing without such needless invasiveness.

---

## O que foi adicionado neste fork

- **Dual Operation Mode** — escolha entre dois modos de operação:
  - **Default Mode** — usa `cmd game downscale`, estável e recomendado para Android 14+
  - **Alternative Mode** — usa `device_config game_overlay`, experimental, desabilita sincronização GMS para evitar que as configurações sejam perdidas após algum tempo
- **Persistência automática** — a resolução é reaplicada automaticamente toda vez que o app é aberto, sem precisar clicar em Apply novamente
- **Lista completa de apps** — exibe todos os apps instalados, não apenas os com ícone de launcher
- **Hook no system_server** — `CATEGORY_GAME` é aplicado apenas nos apps configurados, corrigindo o bug que marcava todos os apps como jogo
- **Reset correto** — reset individual e reset geral funcionam corretamente
- **Config world-readable** — arquivo de configuração com permissão 644 para o módulo Xposed ler de qualquer processo sem bloqueio do SELinux

---

## Modos de Operação

| | Default Mode | Alternative Mode |
|---|---|---|
| Comando | `cmd game downscale` | `device_config game_overlay` |
| Persistência | Reaplicado ao abrir o app | Reaplicado ao abrir + GMS sync desabilitado |
| Compatibilidade | Android 14+ (estável) | Android 13+ (experimental) |
| Recomendado para | Maioria dos usuários | Quem tem problemas com o Default |

---

## Xposed

O módulo Xposed incluído é necessário em dispositivos com **Android 15-16** para suportar todos os apps. No Android 13-14 não é necessário ativá-lo.

---

## Créditos

- **Autor original:** [danmgk](https://t.me/danmgk) — criador do GameResChange
  - [Telegram Channel](https://t.me/danmgk)
  - [PayPal](https://www.paypal.com/donate/?hosted_button_id=BJAJW4755BXFY)
  - Litecoin/LTC: `ltc1qlens88rlj8vpjt9r4kt2mqrma6nq3v3aylgef6`
  - Ethereum/ETH: `0x2c8d02EA7202eaf9DAC14af6ABc178Cb34Cd3f00`

- **Fork mantido por:** [DarkKdevon](https://github.com/Dark3090)
