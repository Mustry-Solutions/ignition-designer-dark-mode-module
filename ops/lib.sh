#!/usr/bin/env bash
# Shared helpers for the ops/ scripts. Sourced by the other scripts; not run directly.

set -euo pipefail

# Resolve key paths relative to this file, so scripts work from any directory.
OPS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${OPS_DIR}/.." && pwd)"

# --- git worktrees ----------------------------------------------------------
# There is ONE dev gateway per clone, and it belongs to the main checkout: its
# compose project is named after that directory, it bind-mounts THAT
# checkout's ops/modules, and it trusts the certificate in THAT checkout's
# ops/signing (gitignored, so a worktree has none). Run from a worktree with
# every path relative to the worktree, deploy.sh used to build the branch,
# stage it where nothing is mounted, generate a fresh keystore the gateway does
# not trust, and report success while the gateway kept serving the old build
# (or parked in commissioning). So: the CODE is built from wherever the script
# lives, and everything the gateway sees comes from the main checkout.
MAIN_ROOT="${PROJECT_ROOT}"
if _common="$(git -C "${PROJECT_ROOT}" rev-parse --git-common-dir 2>/dev/null)"; then
  _common="$(cd "${PROJECT_ROOT}" && cd "${_common}" && pwd)"
  _candidate="$(dirname "${_common}")"
  if [[ "${_candidate}" != "${PROJECT_ROOT}" && -f "${_candidate}/docker-compose.yml" ]]; then
    MAIN_ROOT="${_candidate}"
  fi
  unset _common _candidate
fi
IN_WORKTREE=0
[[ "${MAIN_ROOT}" != "${PROJECT_ROOT}" ]] && IN_WORKTREE=1

# --- which gateway: 8.3 (the default) or 8.1 --------------------------------
# IGNITION_LINE=8.1 points every script at docker-compose.8.1.yml and builds
# designer-dark-mode-8.1.modl (-Pignition.line=8.1). The workflow is the same;
# the helpers below branch only where an 8.1 gateway differs: it has no
# externalModulesFolder (the build is copied into user-lib/modules), and it
# keeps module acceptance in config.idb rather than data/modules.json.
IGNITION_LINE="${IGNITION_LINE:-8.3}"
case "${IGNITION_LINE}" in
  8.3) LINE_SUFFIX="" ;;
  8.1) LINE_SUFFIX="-8.1" ;;
  *) echo "IGNITION_LINE must be 8.3 or 8.1, not '${IGNITION_LINE}'" >&2; exit 1 ;;
esac
is_8_1() { [[ "${IGNITION_LINE}" == "8.1" ]]; }

MODULES_DIR="${MAIN_ROOT}/ops/modules${LINE_SUFFIX}"
MODL_NAME="designer-dark-mode${LINE_SUFFIX}.modl"

# Local self-signed signing material for development (gitignored). On a fresh
# gateway the certificate is accepted unattended, by seeding its fingerprint into
# data/modules.json (see accept_staged_module below); after that it persists in
# the gateway data volume. These are throwaway dev creds. Always the MAIN
# checkout's: a second keystore would be a second certificate the gateway has
# never accepted.
SIGNING_DIR="${MAIN_ROOT}/ops/signing"
KEYSTORE_FILE="${SIGNING_DIR}/dev-keystore.p12"
CERT_FILE="${SIGNING_DIR}/dev-cert.pem"
CERT_ALIAS="designer-dark-mode-dev"
SIGNING_PASS="devpassword"
SIGNING_DNAME="CN=Mustry Solutions (Dev), O=Mustry Solutions, C=BE"

# Must match id.set(...) in build.gradle.kts.
MODULE_ID="com.mustrysolutions.designerdarkmode"
CONTAINER_NAME="designer-dark-mode-ignition"
COMPOSE_FILE_NAME="docker-compose.yml"
if is_8_1; then
  CONTAINER_NAME="designer-dark-mode-ignition-81"
  COMPOSE_FILE_NAME="docker-compose.8.1.yml"
fi
# Where the gateway loads the module from, inside the container.
if is_8_1; then
  MODL_IN_CONTAINER="/usr/local/bin/ignition/user-lib/modules/${MODL_NAME}"
else
  MODL_IN_CONTAINER="/external-modules/${MODL_NAME}"
fi

# Use Java 17 for Gradle (matches the module's toolchain).
JAVA_17_HOME="/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home"
if [[ -d "${JAVA_17_HOME}" ]]; then
  export JAVA_HOME="${JAVA_17_HOME}"
fi

# Read host port overrides from .env (if present) so the printed URL matches compose.
if [[ -f "${MAIN_ROOT}/.env" ]]; then
  # shellcheck disable=SC1091
  set -a; source "${MAIN_ROOT}/.env"; set +a
fi
GATEWAY_HTTP_PORT="${GATEWAY_HTTP_PORT:-8088}"
if is_8_1; then
  GATEWAY_HTTP_PORT="${GATEWAY_81_HTTP_PORT:-9588}"
fi
# A container that already exists was created with whatever port was in force
# at `up` time (an .env since deleted, an env var since dropped). It is the
# truth for every script that talks to it; .env only decides a fresh `up`.
if _published="$(docker port "${CONTAINER_NAME}" 8088/tcp 2>/dev/null | head -1)"; then
  [[ "${_published}" =~ :([0-9]+)$ ]] && GATEWAY_HTTP_PORT="${BASH_REMATCH[1]}"
fi
unset _published
GATEWAY_URL="http://localhost:${GATEWAY_HTTP_PORT}"
ADMIN_USER="admin"
ADMIN_PASS="password"

# docker compose invocation, always pointed at the main checkout's compose
# file: the compose PROJECT is named after that directory, and a worktree's
# copy would be a second project fighting over the same container name.
COMPOSE_FILE_PATH="${MAIN_ROOT}/${COMPOSE_FILE_NAME}"
# A branch that adds a compose file is run from its worktree before the main
# checkout has the file. The 8.1 file names its own compose project and uses no
# relative paths, so its worktree copy is the same stack.
if [[ ! -f "${COMPOSE_FILE_PATH}" && -f "${PROJECT_ROOT}/${COMPOSE_FILE_NAME}" ]]; then
  COMPOSE_FILE_PATH="${PROJECT_ROOT}/${COMPOSE_FILE_NAME}"
fi
COMPOSE=(docker compose -f "${COMPOSE_FILE_PATH}" --project-directory "${MAIN_ROOT}")

# --- pretty logging -------------------------------------------------------
if [[ -t 1 ]]; then
  C_BLUE=$'\033[34m'; C_GREEN=$'\033[32m'; C_YELLOW=$'\033[33m'; C_RED=$'\033[31m'; C_RESET=$'\033[0m'
else
  C_BLUE=""; C_GREEN=""; C_YELLOW=""; C_RED=""; C_RESET=""
fi
info()  { echo "${C_BLUE}==>${C_RESET} $*"; }
ok()    { echo "${C_GREEN}✓${C_RESET} $*"; }
warn()  { echo "${C_YELLOW}!${C_RESET} $*"; }
err()   { echo "${C_RED}✗${C_RESET} $*" >&2; }

# --- guards ---------------------------------------------------------------
require_docker() {
  if ! docker info >/dev/null 2>&1; then
    err "Docker does not appear to be running. Start Docker Desktop and try again."
    exit 1
  fi
  if (( IN_WORKTREE )); then
    warn "Git worktree: building ${PROJECT_ROOT}"
    warn "               but the gateway, its staging folder and the dev keystore are ${MAIN_ROOT}'s."
  fi
}

# Run one query against a copy of an 8.1 gateway's config.idb (SQLite).
# docker cp works whether or not the container is running.
idb_query() {
  local tmp out
  tmp="$(mktemp -d)"
  if docker cp "${CONTAINER_NAME}:/usr/local/bin/ignition/data/db/config.idb" "${tmp}/config.idb" >/dev/null 2>&1; then
    out="$(sqlite3 "${tmp}/config.idb" "$1" 2>/dev/null || true)"
  fi
  rm -rf "${tmp}"
  echo "${out:-}"
}

# The certificate fingerprint the gateway's registry holds for OUR module, or
# empty when the module is not registered (fresh volume) or the gateway is
# not running. On 8.1 the registry is a table of trusted certificates, not a
# per-module entry, so the question becomes "is the dev certificate trusted".
registry_fingerprint() {
  if is_8_1; then
    idb_query "SELECT lower(hex(THUMBPRINT)) FROM CERTIFICATES WHERE lower(hex(THUMBPRINT)) = '$(dev_cert_fingerprint)';"
    return 0
  fi
  docker exec "${CONTAINER_NAME}" cat /usr/local/bin/ignition/data/modules.json 2>/dev/null \
    | MODULE_ID="${MODULE_ID}" python3 -c '
import json, os, sys
try:
    print(json.load(sys.stdin).get(os.environ["MODULE_ID"], {}).get("certFingerprint", ""))
except Exception:
    print("")
' 2>/dev/null || true
}

# The license hash the gateway's registry holds for OUR module, or empty.
registry_license_hash() {
  if is_8_1; then
    idb_query "SELECT CRC FROM EULAS WHERE MODULEID = '${MODULE_ID}';"
    return 0
  fi
  docker exec "${CONTAINER_NAME}" cat /usr/local/bin/ignition/data/modules.json 2>/dev/null \
    | MODULE_ID="${MODULE_ID}" python3 -c '
import json, os, sys
try:
    print(json.load(sys.stdin).get(os.environ["MODULE_ID"], {}).get("licenseAgreementHash", ""))
except Exception:
    print("")
' 2>/dev/null || true
}

# CRC32 of the staged .modl's license.html, the value the registry must hold.
staged_license_crc() {
  local modl
  modl="$(find "${MODULES_DIR}" -maxdepth 1 -name '*.modl' | head -1)"
  [[ -n "${modl}" ]] || return 0
  python3 -c "import zipfile,zlib,sys; print(zlib.crc32(zipfile.ZipFile(sys.argv[1]).read('license.html')))" "${modl}"
}

# The fingerprint of the dev certificate the staged build is signed with.
dev_cert_fingerprint() {
  openssl x509 -in "${CERT_FILE}" -noout -fingerprint -sha1 \
    | cut -d= -f2 | tr -d ':' | tr '[:upper:]' '[:lower:]'
}

# After a restart: the gateway must be RUNNING (not parked in commissioning
# over an unaccepted certificate) and must be serving the bytes we staged.
verify_deployed() {
  local staged state inside started
  staged="${MODULES_DIR}/${MODL_NAME}"
  state="$(curl -fsS "${GATEWAY_URL}/StatusPing" 2>/dev/null || true)"
  if echo "${state}" | grep -q COMMISSIONING; then
    err "Gateway is parked in COMMISSIONING: it has not accepted the staged module's certificate or license."
    err "Compare 'ops/status.sh' fingerprints; ops/setup.sh re-seeds acceptance."
    return 1
  fi
  inside="$(docker exec "${CONTAINER_NAME}" md5sum "${MODL_IN_CONTAINER}" 2>/dev/null | cut -d' ' -f1)"
  if [[ -z "${inside}" || "${inside}" != "$(md5 -q "${staged}" 2>/dev/null || md5sum "${staged}" | cut -d' ' -f1)" ]]; then
    if is_8_1; then
      err "The gateway does not hold the staged build at ${MODL_IN_CONTAINER}."
    else
      err "The gateway does not see the staged build: /external-modules is mounted from somewhere else."
      docker inspect "${CONTAINER_NAME}" --format '{{range .Mounts}}   {{.Source}} -> {{.Destination}}{{"\n"}}{{end}}' 2>/dev/null || true
    fi
    return 1
  fi
  # Holding the bytes is not running them. An 8.1 gateway with an
  # unaccepted certificate or license does not park in commissioning, it just
  # never starts the module, so ask the log of the current boot.
  started=""
  for ((i = 1; i <= 12; i++)); do
    started="$(docker logs --since "$(docker inspect -f '{{.State.StartedAt}}' "${CONTAINER_NAME}")" "${CONTAINER_NAME}" 2>&1 \
      | grep -m1 "Starting up module '${MODULE_ID}'" || true)"
    [[ -n "${started}" ]] && break
    sleep 5
  done
  if [[ -z "${started}" ]]; then
    err "The gateway holds the build but did not start the module this boot. Check 'ops/logs.sh'."
    return 1
  fi
  ok "Gateway is running the staged build (${MODL_NAME}, md5 ${inside})."
}

# --- signing --------------------------------------------------------------
# Generate a local self-signed keystore + exported certificate the first time,
# so the module can be signed for the dev gateway. Throwaway dev credentials.
ensure_dev_keystore() {
  if [[ -f "${KEYSTORE_FILE}" && -f "${CERT_FILE}" ]]; then
    return 0
  fi
  local keytool="${JAVA_HOME:-}/bin/keytool"
  [[ -x "${keytool}" ]] || keytool="keytool"
  info "Generating a self-signed dev signing keystore (first time only)..."
  mkdir -p "${SIGNING_DIR}"
  "${keytool}" -genkeypair \
    -alias "${CERT_ALIAS}" \
    -keyalg RSA -keysize 2048 \
    -validity 3650 \
    -dname "${SIGNING_DNAME}" \
    -keystore "${KEYSTORE_FILE}" -storetype PKCS12 \
    -storepass "${SIGNING_PASS}"
  "${keytool}" -exportcert \
    -alias "${CERT_ALIAS}" \
    -keystore "${KEYSTORE_FILE}" -storetype PKCS12 \
    -storepass "${SIGNING_PASS}" \
    -rfc -file "${CERT_FILE}"
  ok "Created dev keystore at ops/signing/ (gitignored)."
}

# --- build & stage --------------------------------------------------------
# Build the module (signed with the local dev cert) and copy the freshly built
# .modl into ops/modules so the gateway can pick it up.
build_and_stage_module() {
  ensure_dev_keystore
  info "Building and signing the module with Gradle (first build downloads dependencies)..."
  # `clean` so the signed .modl is produced fresh and never confused with a stale
  # unsigned artifact. This module is small, so a clean build is quick.
  ( cd "${PROJECT_ROOT}" && ./gradlew clean build --console plain \
      -Dorg.gradle.java.installations.auto-download=false \
      -Pignition.line="${IGNITION_LINE}" \
      -Pignition.signing.keystoreFile="${KEYSTORE_FILE}" \
      -Pignition.signing.keystorePassword="${SIGNING_PASS}" \
      -Pignition.signing.certFile="${CERT_FILE}" \
      -Pignition.signing.certAlias="${CERT_ALIAS}" \
      -Pignition.signing.certPassword="${SIGNING_PASS}" )

  # Select the SIGNED module, not the `.unsigned.modl` signing intermediate.
  local modl="${PROJECT_ROOT}/build/${MODL_NAME}"
  if [[ ! -f "${modl}" ]]; then
    err "No signed ${MODL_NAME} under build/ after the build. Aborting."
    exit 1
  fi

  mkdir -p "${MODULES_DIR}"
  # Clear old copies so only the current build is staged.
  rm -f "${MODULES_DIR}"/*.modl 2>/dev/null || true
  cp "${modl}" "${MODULES_DIR}/"
  ok "Staged $(basename "${modl}") -> ${MODULES_DIR}/"
}

# 8.1 only: put the staged build where an 8.1 gateway loads modules from. It
# has no externalModulesFolder, so there is nothing to bind-mount; the file
# lives in the container (not the data volume) and a recreated container
# needs it again. No-op on 8.3, whose staging folder is mounted.
install_into_container() {
  is_8_1 || return 0
  docker cp "${MODULES_DIR}/${MODL_NAME}" "${CONTAINER_NAME}:${MODL_IN_CONTAINER}" >/dev/null
  docker exec -u root "${CONTAINER_NAME}" chown ignition:ignition "${MODL_IN_CONTAINER}"
  ok "Copied ${MODL_NAME} into the container's user-lib/modules."
}

# --- wait for gateway -----------------------------------------------------
# Poll the gateway's StatusPing endpoint until it responds (or time out).
wait_for_gateway() {
  local tries="${1:-60}"
  info "Waiting for the gateway to come up at ${GATEWAY_URL} ..."
  for ((i = 1; i <= tries; i++)); do
    if curl -fsS "${GATEWAY_URL}/StatusPing" 2>/dev/null | grep -q '"state"'; then
      ok "Gateway is responding."
      return 0
    fi
    sleep 5
  done
  warn "Gateway did not report ready after $((tries * 5))s. Check 'ops/logs.sh'."
  return 1
}

# --- unattended module acceptance ------------------------------------------
# Wait until the gateway has written its module registry (data/modules.json,
# carrying the built-ins' cert fingerprints). On a fresh volume this happens
# while the gateway parks in COMMISSIONING over the staged-but-unaccepted
# module — it is the point where accept_staged_module can safely merge.
wait_for_modules_registry() {
  local tries="${1:-60}"
  if is_8_1; then
    # config.idb exists from first boot; what matters is that commissioning
    # (automatic here, from the compose environment) has finished writing it.
    info "Waiting for the 8.1 gateway to finish commissioning ..."
    for ((i = 1; i <= tries; i++)); do
      if curl -fsS "${GATEWAY_URL}/StatusPing" 2>/dev/null | grep -q RUNNING; then
        ok "Gateway is running."
        return 0
      fi
      sleep 5
    done
    err "The 8.1 gateway was not RUNNING after $((tries * 5))s. Check 'ops/logs.sh'."
    return 1
  fi
  info "Waiting for the gateway to write its module registry ..."
  for ((i = 1; i <= tries; i++)); do
    if docker exec "${CONTAINER_NAME}" \
         grep -q certFingerprint /usr/local/bin/ignition/data/modules.json 2>/dev/null; then
      ok "Module registry present."
      return 0
    fi
    sleep 5
  done
  err "Gateway never wrote data/modules.json after $((tries * 5))s. Check 'ops/logs.sh'."
  return 1
}

# Pre-accept the staged module's signing certificate and EULA with no browser
# wizard. Ignition 8.3 records third-party acceptance in data/modules.json as
# {filename, onStartup, certFingerprint (sha1 of the signing cert),
# licenseAgreementHash (CRC32 of the module's license.html, matching the
# platform's ModuleUtil.calculateLicenseCrc)}. The gateway treats that file as
# authoritative, so merge our entry into the gateway-written file — never
# replace it, it also carries every built-in module.
#
# The licenseAgreementHash half matters here specifically: this module ships a
# license.html (build.gradle.kts `license.set`), and without the hash every
# fresh boot parks in commissioning even with the certificate accepted.
accept_staged_module() {
  if is_8_1; then
    accept_staged_module_81
    return
  fi
  local modl fingerprint tmp license_crc
  modl="$(find "${MODULES_DIR}" -maxdepth 1 -name '*.modl' | head -1)"
  [[ -n "${modl}" ]] || { err "No staged .modl in ops/modules."; return 1; }
  fingerprint="$(openssl x509 -in "${CERT_FILE}" -noout -fingerprint -sha1 \
                   | cut -d= -f2 | tr -d ':' | tr '[:upper:]' '[:lower:]')"
  [[ -n "${fingerprint}" ]] || { err "Could not fingerprint ${CERT_FILE}."; return 1; }
  license_crc="$(python3 -c "import zipfile,zlib,sys; print(zlib.crc32(zipfile.ZipFile(sys.argv[1]).read('license.html')))" "${modl}")"

  info "Pre-accepting the module certificate (fingerprint ${fingerprint}) + license (crc ${license_crc})..."
  "${COMPOSE[@]}" stop gateway
  tmp="$(mktemp -d)"
  docker cp "${CONTAINER_NAME}:/usr/local/bin/ignition/data/modules.json" "${tmp}/modules.json"
  LICENSE_CRC="${license_crc}" MODULE_ID="${MODULE_ID}" MODL_NAME="$(basename "${modl}")" FINGERPRINT="${fingerprint}" \
  python3 - "${tmp}/modules.json" <<'PY'
import json, os, sys
path = sys.argv[1]
with open(path) as f:
    modules = json.load(f)
modules[os.environ["MODULE_ID"]] = {
    "filename": f"/external-modules/{os.environ['MODL_NAME']}",
    "onStartup": "enabled",
    "certFingerprint": os.environ["FINGERPRINT"],
    "licenseAgreementHash": int(os.environ["LICENSE_CRC"]),
}
with open(path, "w") as f:
    json.dump(modules, f, indent=2)
PY
  docker cp "${tmp}/modules.json" "${CONTAINER_NAME}:/usr/local/bin/ignition/data/modules.json"
  rm -rf "${tmp}"
  # docker cp writes the file as root; the gateway must be able to rewrite its
  # own registry (it spams AccessDeniedException otherwise). Chown via a helper
  # container against the same volume — the gateway itself is stopped here.
  "${COMPOSE[@]}" run --rm -u root --entrypoint sh gateway \
      -c 'chown ignition:ignition /usr/local/bin/ignition/data/modules.json'
  "${COMPOSE[@]}" start gateway
  ok "Module acceptance seeded; gateway restarting."
}

# 8.1 keeps acceptance in its internal SQLite database, data/db/config.idb:
#   CERTIFICATES(CERTIFICATES_ID, THUMBPRINT, SUBJECTNAME) — THUMBPRINT is the
#     raw SHA-1 of the DER certificate (SecurityUtils.getCertificateThumbprintBytes)
#   EULAS(EULAS_ID, MODULEID, CRC) — CRC32 of the module's license.html
# Worked out from the 8.1.50 gateway (ModuleManagerImpl, CertificateRecord,
# ModuleEulaRecord). Written while the gateway is stopped, under a fixed high
# id that the gateway's own records will not reach; re-seeding replaces it.
accept_staged_module_81() {
  local fingerprint subject license_crc tmp
  fingerprint="$(dev_cert_fingerprint)"
  subject="$(openssl x509 -in "${CERT_FILE}" -noout -subject | sed 's/^subject= *//' | sed "s/'/''/g")"
  license_crc="$(staged_license_crc)"
  [[ -n "${fingerprint}" && -n "${license_crc}" ]] || { err "Could not read the dev certificate or the staged license."; return 1; }

  info "Pre-accepting the module certificate (${fingerprint}) + license (crc ${license_crc}) in config.idb..."
  "${COMPOSE[@]}" stop gateway
  tmp="$(mktemp -d)"
  docker cp "${CONTAINER_NAME}:/usr/local/bin/ignition/data/db/config.idb" "${tmp}/config.idb"
  sqlite3 "${tmp}/config.idb" "
    DELETE FROM CERTIFICATES WHERE CERTIFICATES_ID = 9001 OR lower(hex(THUMBPRINT)) = '${fingerprint}';
    DELETE FROM EULAS WHERE EULAS_ID = 9001 OR MODULEID = '${MODULE_ID}';
    INSERT INTO CERTIFICATES (CERTIFICATES_ID, THUMBPRINT, SUBJECTNAME) VALUES (9001, X'${fingerprint}', '${subject}');
    INSERT INTO EULAS (EULAS_ID, MODULEID, CRC) VALUES (9001, '${MODULE_ID}', ${license_crc});"
  docker cp "${tmp}/config.idb" "${CONTAINER_NAME}:/usr/local/bin/ignition/data/db/config.idb"
  rm -rf "${tmp}"
  # docker cp writes as root; the gateway must own its database.
  "${COMPOSE[@]}" run --rm -u root --entrypoint sh gateway \
      -c 'chown ignition:ignition /usr/local/bin/ignition/data/db/config.idb'
  "${COMPOSE[@]}" start gateway
  ok "Module acceptance seeded; gateway restarting."
}
