#!/usr/bin/env bash
# Build TLockout and push the resulting JAR to a remote server over SFTP,
# deleting any existing TLockout*.jar in the remote plugins directory first.
#
# Setup:
#   cp .deploy.env.example .deploy.env
#   chmod 600 .deploy.env
#   # edit .deploy.env with your PebbleHost SFTP details
#
# Usage:
#   ./deploy.sh             # build jar + delete old remote jars + upload new jar
#   ./deploy.sh --no-build  # skip gradle build, upload existing jar for current version
#   ./deploy.sh --offline   # pass --offline to gradle

set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$REPO_DIR/.deploy.env"

DO_BUILD=true
GRADLE_OFFLINE=false

usage() {
    cat <<'EOF'
Usage: ./deploy.sh [options]

Options:
  --no-build   Skip Gradle build and upload the already-built JAR for the current version
  --offline    Run Gradle with --offline
  -h, --help   Show this help message
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --no-build)
            DO_BUILD=false
            shift
            ;;
        --offline)
            GRADLE_OFFLINE=true
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            echo "Error: unknown option '$1'" >&2
            usage >&2
            exit 2
            ;;
    esac
done

if [[ ! -f "$ENV_FILE" ]]; then
    echo "Error: $ENV_FILE not found." >&2
    echo "Create it from the template:" >&2
    echo "  cp .deploy.env.example .deploy.env && chmod 600 .deploy.env" >&2
    exit 1
fi

# shellcheck disable=SC1090
source "$ENV_FILE"

: "${SFTP_HOST:?SFTP_HOST is required in .deploy.env}"
: "${SFTP_PORT:?SFTP_PORT is required in .deploy.env}"
: "${SFTP_USER:?SFTP_USER is required in .deploy.env}"
: "${SFTP_PASS:?SFTP_PASS is required in .deploy.env}"
SFTP_REMOTE_DIR="${SFTP_REMOTE_DIR:-/plugins}"
SFTP_INSECURE="${SFTP_INSECURE:-false}"

# Strip trailing slash(es) while preserving leading slash if present.
REMOTE_DIR="${SFTP_REMOTE_DIR%/}"
if [[ -z "$REMOTE_DIR" ]]; then
    echo "Error: SFTP_REMOTE_DIR must point to your plugins directory (e.g. /plugins), not root." >&2
    exit 1
fi

if ! command -v curl >/dev/null 2>&1; then
    echo "Error: curl is required but not installed." >&2
    exit 1
fi
if ! curl --version | grep -qiE '^Protocols:.*\bsftp\b'; then
    echo "Error: installed curl does not have SFTP support compiled in." >&2
    exit 1
fi

# Read current plugin version from build.gradle.kts.
VERSION="$(sed -nE 's/^[[:space:]]*version[[:space:]]*=[[:space:]]*"([^"]+)".*/\1/p' "$REPO_DIR/build.gradle.kts" | head -n 1)"
if [[ -z "$VERSION" ]]; then
    echo "Error: could not read version from $REPO_DIR/build.gradle.kts" >&2
    exit 1
fi

JAR_NAME="TLockout-${VERSION}.jar"
JAR_PATH="$REPO_DIR/build/libs/${JAR_NAME}"

if [[ "$DO_BUILD" == true ]]; then
    JDK_DIR="$REPO_DIR/.tools/jdk-25.0.5.1+2"
    GRADLE_BIN="$REPO_DIR/.tools/gradle-9.9.2/bin/gradle"
    GRADLE_HOME_DIR="$REPO_DIR/.tools/gradle-home"

    if [[ ! -x "$GRADLE_BIN" || ! -d "$JDK_DIR" ]]; then
        echo "Error: local toolchain not found under $REPO_DIR/.tools/." >&2
        exit 1
    fi

    echo "==> Building ${JAR_NAME}..."
    GRADLE_ARGS=(jar --no-daemon)
    if [[ "$GRADLE_OFFLINE" == true ]]; then
        GRADLE_ARGS+=(--offline)
    fi
    (
        cd "$REPO_DIR"
        JAVA_HOME="$JDK_DIR" \
        GRADLE_USER_HOME="$GRADLE_HOME_DIR" \
        "$GRADLE_BIN" "${GRADLE_ARGS[@]}"
    )
fi

if [[ ! -f "$JAR_PATH" ]]; then
    echo "Error: built JAR not found at $JAR_PATH" >&2
    exit 1
fi

# Escape a string for curl -K double-quoted config values.
curl_escape() {
    local s="$1"
    s="${s//\\/\\\\}"
    s="${s//\"/\\\"}"
    printf '%s' "$s"
}

make_curl_auth_config() {
    printf 'user = "%s:%s"\n' "$(curl_escape "$SFTP_USER")" "$(curl_escape "$SFTP_PASS")"
    if [[ "$SFTP_INSECURE" == "true" ]]; then
        printf 'insecure\n'
    fi
}

LIST_URL="sftp://${SFTP_HOST}:${SFTP_PORT}/${REMOTE_DIR}/"
UPLOAD_URL="sftp://${SFTP_HOST}:${SFTP_PORT}/${REMOTE_DIR}/${JAR_NAME}"

echo "==> Checking ${REMOTE_DIR}/ on ${SFTP_HOST}:${SFTP_PORT} for existing TLockout JARs..."
set +e
REMOTE_LIST="$(make_curl_auth_config | curl --silent --show-error --fail --list-only -K - "$LIST_URL")"
LIST_STATUS=$?
set -e

if [[ $LIST_STATUS -ne 0 ]]; then
    if [[ $LIST_STATUS -eq 51 || $LIST_STATUS -eq 60 ]]; then
        echo "" >&2
        echo "SSH host key for ${SFTP_HOST}:${SFTP_PORT} is not in ~/.ssh/known_hosts yet." >&2
        echo "Run this once to trust it:" >&2
        echo "  mkdir -p ~/.ssh && ssh-keyscan -p ${SFTP_PORT} ${SFTP_HOST} >> ~/.ssh/known_hosts" >&2
        echo "Or set SFTP_INSECURE='true' in .deploy.env to skip host key checking." >&2
    fi
    exit "$LIST_STATUS"
fi

mapfile -t OLD_JARS < <(printf '%s\n' "$REMOTE_LIST" | tr -d '\r' | grep -E '^TLockout[^/]*\.jar$' || true)

UPLOAD_CFG="$(make_curl_auth_config)"
if [[ ${#OLD_JARS[@]} -gt 0 ]]; then
    echo "==> Deleting existing remote plugin JAR(s):"
    for old_jar in "${OLD_JARS[@]}"; do
        echo "    - ${REMOTE_DIR}/${old_jar}"
        UPLOAD_CFG+=$'\n'"quote = \"rm $(curl_escape "${REMOTE_DIR}/${old_jar}")\""
    done
else
    echo "==> No existing TLockout*.jar files found in ${REMOTE_DIR}/."
fi

JAR_SIZE="$(du -h "$JAR_PATH" | cut -f1)"
echo "==> Uploading ${JAR_NAME} (${JAR_SIZE}) to ${REMOTE_DIR}/${JAR_NAME}..."
printf '%s\n' "$UPLOAD_CFG" | curl --fail --show-error --progress-bar -K - -T "$JAR_PATH" "$UPLOAD_URL"

echo "==> Done! ${JAR_NAME} is uploaded to ${REMOTE_DIR}/ on ${SFTP_HOST}."
