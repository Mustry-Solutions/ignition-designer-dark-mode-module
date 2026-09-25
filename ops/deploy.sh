#!/usr/bin/env bash
# Rebuild the module and reload it into the already-running gateway.
# Use this after you change module code. For a clean slate, use teardown.sh + setup.sh.
#
# Usage: ops/deploy.sh                     the 8.3 gateway
#        IGNITION_LINE=8.1 ops/deploy.sh   the 8.1 gateway

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib.sh"

require_docker

# If the gateway isn't running yet, just hand off to setup.
if ! "${COMPOSE[@]}" ps --status running --services 2>/dev/null | grep -q '^gateway$'; then
  warn "Gateway isn't running. Running setup.sh instead."
  exec "${OPS_DIR}/setup.sh"
fi

build_and_stage_module
install_into_container

# The registry keys the module's acceptance on the signing certificate. After
# a RELEASED build (signed with the release certificate) was run on this
# gateway, or after the dev keystore was regenerated, a plain restart parks
# the gateway in commissioning over the "unknown" dev certificate. Re-seed
# in that case; it is a stop/edit/start, not a restart.
#
# The same holds for the license: the registry records a hash of the
# license.html it accepted, so a build with an edited license parks the
# gateway in commissioning just as an unknown certificate does.
if [[ "$(registry_fingerprint)" != "$(dev_cert_fingerprint)" ]]; then
  warn "The gateway's registry does not hold the dev certificate; re-seeding acceptance."
  accept_staged_module
elif [[ "$(registry_license_hash)" != "$(staged_license_crc)" ]]; then
  warn "license.html changed since the gateway accepted it; re-seeding acceptance."
  accept_staged_module
else
  info "Restarting the gateway to load the new build..."
  "${COMPOSE[@]}" restart gateway
fi
wait_for_gateway 60 || true
verify_deployed

echo
ok "Redeployed. Refresh ${GATEWAY_URL} -> Config -> Modules to confirm the new version."
warn "Relaunch the Designer to pick up the new designer-scope code."
