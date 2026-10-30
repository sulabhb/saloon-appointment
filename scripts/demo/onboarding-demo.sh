#!/usr/bin/env bash
# Walks through onboarding a new salon business against a running backend, using only the public API:
# platform admin signs in -> creates the business -> owner accepts the invite -> business goes live.
#
# Start the backend with a bootstrap platform admin first, for example:
#   SALON_BOOTSTRAP_PLATFORM_ADMIN_EMAIL=ops@example.com \
#   SALON_BOOTSTRAP_PLATFORM_ADMIN_PASSWORD=change-me-please \
#   ./gradlew bootRun
#
# Then:  PLATFORM_ADMIN_EMAIL=ops@example.com PLATFORM_ADMIN_PASSWORD=change-me-please scripts/demo/onboarding-demo.sh
# Needs curl and python3.
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
PLATFORM_ADMIN_EMAIL="${PLATFORM_ADMIN_EMAIL:?Set PLATFORM_ADMIN_EMAIL}"
PLATFORM_ADMIN_PASSWORD="${PLATFORM_ADMIN_PASSWORD:?Set PLATFORM_ADMIN_PASSWORD}"
SLUG="${DEMO_SLUG:-bloom-studio}"
OWNER_EMAIL="${DEMO_OWNER_EMAIL:-maya@${SLUG}.test}"
OWNER_PASSWORD="${DEMO_OWNER_PASSWORD:-bloom-owner-password}"

step=0
heading() { step=$((step + 1)); printf '\n\033[1m%d. %s\033[0m\n' "$step" "$1"; }
note() { printf '   %s\n' "$1"; }

# call METHOD PATH [BODY] [TOKEN]  -> prints "STATUS" on line 1, body after
call() {
  local method=$1 path=$2 body=${3:-} token=${4:-}
  local args=(-s -w '\n%{http_code}' -X "$method" "$BASE_URL$path" -H 'Content-Type: application/json')
  [[ -n $token ]] && args+=(-H "Authorization: Bearer $token")
  [[ -n $body ]] && args+=(-d "$body")
  local out; out=$(curl "${args[@]}")
  RESPONSE_STATUS=${out##*$'\n'}
  RESPONSE_BODY=${out%$'\n'*}
}
show() { note "HTTP $RESPONSE_STATUS"; [[ -n $RESPONSE_BODY ]] && python3 -m json.tool <<<"$RESPONSE_BODY" | sed 's/^/   /'; return 0; }
field() { python3 -c 'import sys, json
d = json.load(sys.stdin)
for k in sys.argv[1].split("."):
    d = d[int(k)] if k.isdigit() else d[k]
print(d)' "$1" <<<"$RESPONSE_BODY"; }
expect() { [[ $RESPONSE_STATUS == "$1" ]] || { note "Expected HTTP $1, got $RESPONSE_STATUS"; show; exit 1; }; }

heading "Platform admin signs in"
call POST /api/v1/auth/login "{\"email\":\"$PLATFORM_ADMIN_EMAIL\",\"password\":\"$PLATFORM_ADMIN_PASSWORD\",\"client\":\"android\",\"deviceName\":\"demo\"}"
expect 200
ADMIN_TOKEN=$(field token)
note "Signed in as $(field me.user.fullName) (platform admin: $(field me.user.platformAdmin))"

heading "Platform admin creates the business '$SLUG' and invites its owner"
call POST /api/v1/platform/businesses "{
  \"slug\": \"$SLUG\", \"legalName\": \"Bloom Studio Pty Ltd\", \"displayName\": \"Bloom Studio\",
  \"defaultTimeZone\": \"Australia/Melbourne\", \"ownerEmail\": \"$OWNER_EMAIL\",
  \"accentColor\": \"#7A4E8C\", \"description\": \"Colour, cuts and styling in Fitzroy.\"}" "$ADMIN_TOKEN"
if [[ $RESPONSE_STATUS == 409 ]]; then note "'$SLUG' already exists. Rerun with DEMO_SLUG=another-name."; exit 1; fi
expect 201
show
BUSINESS_ID=$(field business.id)
INVITE_URL=$(field ownerInvite.url)
INVITE_TOKEN=${INVITE_URL##*/}
note "Email delivery is not built yet, so the platform admin passes this link to the owner:"
note "$INVITE_URL"

heading "Customers cannot book yet: the business is still onboarding"
call GET "/api/v1/public/b/$SLUG"
show

heading "Owner opens the invite link"
call GET "/api/v1/invites/$INVITE_TOKEN"
expect 200
show

heading "Owner accepts: chooses a name and password, and is signed in"
call POST "/api/v1/invites/$INVITE_TOKEN/accept" "{\"fullName\":\"Maya Chen\",\"password\":\"$OWNER_PASSWORD\",\"client\":\"android\",\"deviceName\":\"Maya's phone\"}"
expect 200
OWNER_TOKEN=$(field token)
note "Signed in as $(field me.user.fullName), role $(field me.selected.role) in business $(field me.selected.businessId)"

heading "The invite link cannot be used again"
call GET "/api/v1/invites/$INVITE_TOKEN"
show

heading "Owner sees only their own business's branches (none yet: branch setup screens come in M2)"
call GET /api/v1/staff/locations "" "$OWNER_TOKEN"
show

heading "Platform admin sets the business live"
call POST "/api/v1/platform/businesses/$BUSINESS_ID/status" '{"status":"LIVE"}' "$ADMIN_TOKEN"
expect 200
show

heading "Customers (web and Android app) can now open it with the salon code '$SLUG'"
call GET "/api/v1/public/b/$SLUG"
expect 200
show

heading "Platform dashboard"
call GET /api/v1/platform/businesses "" "$ADMIN_TOKEN"
python3 -c 'import sys, json
rows = json.load(sys.stdin)
print("   {:<16} {:<12} {:>9} {:>6} {:>8}".format("salon code", "status", "branches", "staff", "invites"))
for b in rows:
    print("   {:<16} {:<12} {:>9} {:>6} {:>8}".format(b["slug"], b["status"], b["activeBranches"], b["activeStaff"], b["pendingInvites"]))' <<<"$RESPONSE_BODY"

printf '\n\033[1mDone.\033[0m Owner sign-in for later: %s / %s\n' "$OWNER_EMAIL" "$OWNER_PASSWORD"
