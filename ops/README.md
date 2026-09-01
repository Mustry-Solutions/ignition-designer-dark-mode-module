# Local dev gateway

Disposable Ignition 8.3.6 gateway in Docker for testing the Designer Dark Mode module.
Development only: fixed weak admin credentials (`admin` / `password`) and an
auto-accepted EULA. Never point this at anything real.

| Script | What it does |
|--------|--------------|
| `setup.sh` | Build + sign the module and start the gateway with it installed. Commissioning is unattended — the module's certificate and EULA are seeded into `data/modules.json` for you, so there is no browser wizard to click through. |
| `deploy.sh` | Rebuild after code changes and restart the gateway to reload the module. Relaunch the Designer afterwards to pick up designer-scope code. |
| `status.sh` | Container status, gateway URL, staged module files. |
| `logs.sh` | Tail gateway logs. |
| `teardown.sh` | Stop the gateway (`--purge` also wipes its data volume). |

The gateway publishes on **http://localhost:8088** (HTTPS 8043), Ignition's own defaults — configurable in
`../.env` (copy `../.env.example` if you already run a gateway on 8088).

How the pieces fit:

- The module is signed with a throwaway self-signed certificate generated into
  `signing/` on first run (gitignored). `setup.sh` accepts it for you — it writes
  the cert fingerprint and the EULA hash into the gateway's `data/modules.json`
  while the gateway is stopped, which is what keeps a fresh gateway out of
  commissioning. Because the cert stays the same across rebuilds, that is done
  once, and `deploy.sh` then swaps in new builds with nothing to accept.
- The signed `.modl` is staged into `modules/`, which is bind-mounted into the
  container as the gateway's `externalModulesFolder`.
- Gateway state lives in the `gateway-data` Docker volume, so commissioning and
  cert acceptance survive restarts. `teardown.sh --purge` resets everything.

Testing the Designer: install the Designer Launcher on your machine, add the
gateway at `http://localhost:8088`, launch a Designer, and use
**Tools → Dark Mode**.
