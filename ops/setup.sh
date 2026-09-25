#!/usr/bin/env bash
# Build the module and bring up a fresh local Ignition gateway with the module
# installed. Safe to re-run.
#
# Usage: ops/setup.sh                     the 8.3 gateway (docker-compose.yml)
#        IGNITION_LINE=8.1 ops/setup.sh   the 8.1 gateway (docker-compose.8.1.yml)

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib.sh"

require_docker
build_and_stage_module

info "Starting the Ignition gateway container..."
# The freshly built+signed .modl is staged in ops/modules, which is bind-mounted to
# /external-modules in the container (see docker-compose.yml). The gateway discovers
# it during first-time commissioning.
"${COMPOSE[@]}" up -d
# 8.1 has no external modules folder; copy the build in (no-op on 8.3).
install_into_container

# On a fresh volume the gateway parks in COMMISSIONING over the staged module
# until its certificate AND EULA are accepted. Seed both into data/modules.json
# rather than making someone click through the browser wizard.
wait_for_modules_registry 60 && accept_staged_module

wait_for_gateway 90 || true
verify_deployed

echo
ok "Gateway is running with the module accepted and enabled."
echo
echo "   Open ${GATEWAY_URL} and log in as ${ADMIN_USER} / ${ADMIN_PASS};"
echo "   Config -> Modules should list 'Designer Dark Mode' as Running."
echo
echo "   Acceptance persists in the gateway data volume, so this is only done"
echo "   once (and again after 'teardown.sh --purge'). The dev cert is stable"
echo "   across rebuilds, so ops/deploy.sh reloads new builds with no prompt."
echo
echo "   To test the Designer dark mode: open Designer Launcher, add gateway"
echo "   ${GATEWAY_URL}, launch a Designer, then check Tools -> Dark Mode."
echo
echo "   Tail logs:        ops/logs.sh"
echo "   Redeploy changes: ops/deploy.sh"
echo "   Tear it down:     ops/teardown.sh   (add --purge to wipe gateway data)"
