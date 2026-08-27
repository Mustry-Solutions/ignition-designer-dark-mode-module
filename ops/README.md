# Local dev gateway

Disposable Ignition 8.3.6 gateway in Docker for testing the Designer Dark Mode module.
Mirrors the setup used in `first-ignition-module`. Development only: fixed weak
admin credentials (`admin` / `password`), auto-accepted EULA.

| Script | What it does |
|--------|--------------|
| `setup.sh` | Build + sign the module and start the gateway with it installed. Commissioning is unattended — the module's certificate and EULA are seeded into `data/modules.json` for you, so there is no browser wizard to click through. |
| `deploy.sh` | Rebuild after code changes and restart the gateway to reload the module. Relaunch the Designer afterwards to pick up designer-scope code. |
| `status.sh` | Container status, gateway URL, staged module files. |
| `logs.sh` | Tail gateway logs. |
| `teardown.sh` | Stop the gateway (`--purge` also wipes its data volume). |

The gateway publishes on **http://localhost:8088** (HTTPS 8043), Ignition's own defaults — configurable in
`../.env` — so it can run alongside the first-ignition-module gateway on 9088.

How the pieces fit:

- The module is signed with a throwaway self-signed certificate generated into
  `signing/` on first run (gitignored). Because the cert stays the same across
  rebuilds, the gateway only asks you to accept it once; after that `deploy.sh`
  swaps in new builds without prompting.
- The signed `.modl` is staged into `modules/`, which is bind-mounted into the
  container as the gateway's `externalModulesFolder`.
- Gateway state lives in the `gateway-data` Docker volume, so commissioning and
  cert acceptance survive restarts. `teardown.sh --purge` resets everything.

Testing the Designer: install the Designer Launcher on your machine, add the
gateway at `http://localhost:8088`, launch a Designer, and use
**Tools → Dark Mode**.
