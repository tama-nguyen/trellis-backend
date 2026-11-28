#!/bin/sh
# HTTP smoke checks against a running `docker compose --profile full` stack. Writes a JUnit report
# (smoke-junit.xml) for local verification. This is not part of the build-only GitHub workflow:
#
#   build-logic/scripts/dev/trellis-dev.sh up --profile full && build-logic/scripts/ci/smoke.sh
#
# `compose up --wait` already proved every container healthy; these checks prove the endpoints a user
# or another service depends on answer through the published ports.
set -u

HOST="${TRELLIS_SMOKE_HOST:-127.0.0.1}"
REPORT="${TRELLIS_SMOKE_REPORT:-smoke-junit.xml}"

failures=0
total=0
cases=""

check() {
  # check <name> <url> [expected-substring]
  name="$1"; url="$2"; want="${3:-}"
  total=$((total + 1))
  body=$(curl -fsS --max-time 10 "$url" 2>&1)
  rc=$?
  if [ $rc -eq 0 ] && { [ -z "$want" ] || printf '%s' "$body" | grep -q -- "$want"; }; then
    echo "ok    $name  $url"
    cases="$cases<testcase classname=\"smoke\" name=\"$name\"/>"
  else
    echo "FAIL  $name  $url"
    printf '%s\n' "$body" | head -5 | sed 's/^/      /'
    failures=$((failures + 1))
    msg=$(printf '%s' "$body" | head -c 300 | sed 's/&/\&amp;/g; s/</\&lt;/g; s/"/\&quot;/g')
    cases="$cases<testcase classname=\"smoke\" name=\"$name\"><failure message=\"$msg\"/></testcase>"
  fi
}

check backend-readiness "http://$HOST:${TRELLIS_BACKEND_MGMT_PORT:-18081}/actuator/health/readiness" '"UP"'
check backend-prometheus "http://$HOST:${TRELLIS_BACKEND_MGMT_PORT:-18081}/actuator/prometheus" 'jvm_'
check grafana "http://$HOST:${TRELLIS_GRAFANA_PORT:-13300}/api/health" 'ok'
check prometheus-ready "http://$HOST:${TRELLIS_PROMETHEUS_PORT:-19090}/-/ready"
check keycloak-realm "http://$HOST:${TRELLIS_KEYCLOAK_PORT:-18180}/realms/trellis/.well-known/openid-configuration" 'issuer'

printf '<?xml version="1.0" encoding="UTF-8"?><testsuite name="smoke" tests="%s" failures="%s">%s</testsuite>\n' \
  "$total" "$failures" "$cases" > "$REPORT"
echo "$((total - failures))/$total smoke checks passed (report: $REPORT)"
[ "$failures" -eq 0 ]
