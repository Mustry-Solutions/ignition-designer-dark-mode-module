# Local dev gateway

Disposable Ignition 8.3.6 gateway in Docker for testing the Designer Dark Mode module.
Development only: fixed weak admin credentials (`admin` / `password`) and an
auto-accepted EULA. Never point this at anything real.

| Script | What it does |
|--------|--------------|
| `setup.sh` | Build + sign the module and start the gateway with it installed. Commissioning is unattended — the module's certificate and EULA are seeded into `data/modules.json` for you, so there is no browser wizard to click through. |
| `deploy.sh` | Rebuild after code changes and restart the gateway to reload the module. If the gateway's registry holds another certificate for the module (a released build was run on it, or the dev keystore was regenerated), acceptance is re-seeded instead of a plain restart. Ends by checking that the gateway is running, not commissioning, and serving the bytes just staged. Relaunch the Designer afterwards to pick up designer-scope code. |
| `status.sh` | Container status, gateway URL, staged module files. |
| `logs.sh` | Tail gateway logs. |
| `teardown.sh` | Stop the gateway (`--purge` also wipes its data volume). |
| `vision-check.sh [project]` | Scan the project's saved Vision windows and templates for FlatLaf leakage (the corruption the serializer fixes prevent; see QA checklist §N). |
| `vision-jars.sh [version]` | Print the newest cached Vision jars (or the given version's) as a classpath for the Vision probe: `./gradlew :designer:visionProbe -Pvision.jars="$(ops/vision-jars.sh)"`. Needs a Designer that has opened a Vision project on this machine. |
| `qa-renderer-probe.py` | Not a shell script — paste it into the Designer's **Script Console** (`OUTPATH='...'; execfile('ops/qa-renderer-probe.py')`) to dump every visible tree's and table's cell renderer to a file. Take one before a dark/light cycle and one after, and diff: a restore that loses a renderer shows up as a changed class name, which nothing else reports. See the QA checklist, "The renderer probe". |
| `laf-harness-watchdog.sh [gradle args]` | Run `:designer:lafHarness` with a deadline; on a hang it thread-dumps every Gradle JVM into `build/laf-harness-threads-*.txt` and fails. CI uses this instead of calling Gradle directly, because the harness sometimes hangs and a cancelled job leaves no evidence. |

The gateway publishes on **http://localhost:8088** (HTTPS 8043), Ignition's own defaults — configurable in
`../.env` (copy `../.env.example` if you already run a gateway on 8088). A
container that already exists keeps the port it was created with; the scripts
read that from the container, so `.env` only decides a fresh `up`.

**Git worktrees.** There is one dev gateway per clone and it belongs to the
main checkout: the compose project is named after that directory, it
bind-mounts that checkout's `modules/`, and it trusts the certificate in that
checkout's `signing/` (gitignored, so a worktree has none). Run from a
worktree, every script builds the worktree's code but stages, signs and talks
to the gateway through the main checkout, and says so on its first line. That
is the workflow you want: `ops/deploy.sh` on a branch puts that branch on the
gateway. Before this the same command built, staged into a folder nothing
mounted, generated a fresh keystore the gateway had never accepted, and
reported success while the gateway kept serving the old build.

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
