#!/usr/bin/env bash
# Sets up a complete, bookable salon against a running backend, through the API only:
# platform admin creates the business -> owner accepts the invite -> branch with opening hours ->
# services -> two stylists with weekly shifts -> owner takes it live.
#
# Start the backend with a bootstrap platform admin first, for example:
#   SALON_BOOTSTRAP_PLATFORM_ADMIN_EMAIL=ops@example.com \
#   SALON_BOOTSTRAP_PLATFORM_ADMIN_PASSWORD=change-me-please \
#   ./gradlew bootRun
#
# Then:  PLATFORM_ADMIN_EMAIL=ops@example.com PLATFORM_ADMIN_PASSWORD=change-me-please scripts/demo/seed-salon.sh
# Needs curl and python3. Set DEMO_SLUG to create another salon.
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
SITE_URL="${SITE_URL:-http://localhost:5173}"
PLATFORM_ADMIN_EMAIL="${PLATFORM_ADMIN_EMAIL:?Set PLATFORM_ADMIN_EMAIL}"
PLATFORM_ADMIN_PASSWORD="${PLATFORM_ADMIN_PASSWORD:?Set PLATFORM_ADMIN_PASSWORD}"
SLUG="${DEMO_SLUG:-luna-hair-co}"
OWNER_EMAIL="${DEMO_OWNER_EMAIL:-jo@luna-hair.test}"
OWNER_PASSWORD="${DEMO_OWNER_PASSWORD:-luna-owner-demo-pass}"

note() { printf '   %s\n' "$1"; }
heading() { printf '\n\033[1m%s\033[0m\n' "$1"; }

# call METHOD PATH [BODY] [TOKEN]: sets RESPONSE_STATUS and RESPONSE_BODY
call() {
  local method=$1 path=$2 body=${3:-} token=${4:-}
  local args=(-s -w '\n%{http_code}' -X "$method" "$BASE_URL$path" -H 'Content-Type: application/json')
  [[ -n $token ]] && args+=(-H "Authorization: Bearer $token")
  [[ -n $body ]] && args+=(-d "$body")
  local out; out=$(curl "${args[@]}")
  RESPONSE_STATUS=${out##*$'\n'}
  RESPONSE_BODY=${out%$'\n'*}
}
# py EXPRESSION: evaluates a Python expression with the response JSON as `d`
py() { python3 -c "import sys, json; d = json.load(sys.stdin); print($1)" <<<"$RESPONSE_BODY"; }
expect() {
  [[ $RESPONSE_STATUS == "$1" ]] && return 0
  note "Expected HTTP $1, got $RESPONSE_STATUS: $RESPONSE_BODY"
  exit 1
}

heading "Platform admin creates '$SLUG'"
call POST /api/v1/auth/login "{\"email\":\"$PLATFORM_ADMIN_EMAIL\",\"password\":\"$PLATFORM_ADMIN_PASSWORD\",\"client\":\"android\",\"deviceName\":\"seed\"}"
expect 200
ADMIN=$(py 'd["token"]')
call POST /api/v1/platform/businesses "{
  \"slug\": \"$SLUG\", \"legalName\": \"Luna Hair Co Pty Ltd\", \"displayName\": \"Luna Hair Co\",
  \"defaultTimeZone\": \"Australia/Brisbane\", \"ownerEmail\": \"$OWNER_EMAIL\",
  \"accentColor\": \"#7A4E8C\", \"description\": \"Colour, cuts and styling in Brisbane.\"}" "$ADMIN"
if [[ $RESPONSE_STATUS == 409 ]]; then note "'$SLUG' already exists. Rerun with DEMO_SLUG=another-name."; exit 1; fi
expect 201
INVITE=$(py 'd["ownerInvite"]["url"].rsplit("/", 1)[1]')

heading "Owner accepts the invite"
call POST "/api/v1/invites/$INVITE/accept" "{\"fullName\":\"Jo Rivera\",\"password\":\"$OWNER_PASSWORD\",\"client\":\"android\",\"deviceName\":\"seed\"}"
expect 200
OWNER=$(py 'd["token"]')

heading "Branch and opening hours (weekdays with a lunch break, Saturday 9 to 5)"
call POST /api/v1/admin/locations '{"name": "Fortitude Valley", "slug": "fortitude-valley", "addressLine1": "9 Brunswick Street",
  "suburb": "Fortitude Valley", "state": "QLD", "postcode": "4006", "phone": "07 3000 1111",
  "email": "valley@luna-hair.test", "timeZone": "Australia/Brisbane"}' "$OWNER"
expect 201
BRANCH=$(py 'd["id"]')
HOURS=$(python3 -c 'import json
hours = [{"weekday": d, "opensAt": o, "closesAt": c} for d in range(1, 6) for o, c in (("09:00", "12:30"), ("13:30", "17:00"))]
hours.append({"weekday": 6, "opensAt": "09:00", "closesAt": "17:00"})
print(json.dumps({"hours": hours}))')
call PUT "/api/v1/admin/locations/$BRANCH/hours" "$HOURS" "$OWNER"
expect 200

heading "Services"
call POST /api/v1/admin/categories '{"name": "Cuts & styling"}' "$OWNER"
expect 201
CATEGORY=$(py '[c["id"] for c in d if c["name"] == "Cuts & styling"][0]')
service() {
  call POST /api/v1/admin/services "{\"categoryId\": \"$CATEGORY\", \"name\": \"$1\", \"description\": \"$2\",
    \"durationMin\": $3, \"bufferAfterMin\": 10, \"priceCents\": $4, \"priceIsFrom\": $5, \"offerAtLocationIds\": [\"$BRANCH\"]}" "$OWNER"
  expect 201
  py "[s['id'] for c in d for s in c['services'] if s['name'] == '$1'][0]"
}
CUT=$(service "Cut & blow-dry" "Consultation, wash, precision cut and blow-dry finish." 60 8500 false)
TRIM=$(service "Fringe trim" "A quick tidy between cuts." 15 2000 false)
COLOUR=$(service "Full colour" "Root to tip colour with a gloss. Price depends on length." 120 16000 true)
note "Cut & blow-dry, Fringe trim, Full colour"

heading "Stylists and their usual week"
stylist() {
  call POST /api/v1/admin/staff "{\"displayName\": \"$1\", \"bio\": \"$2\", \"locationIds\": [\"$BRANCH\"], \"serviceIds\": [$3]}" "$OWNER"
  expect 201
  py "[s['id'] for s in d if s['displayName'] == '$1'][0]"
}
JO=$(stylist "Jo" "Owner and senior stylist." "\"$CUT\", \"$TRIM\", \"$COLOUR\"")
SAM=$(stylist "Sam" "Cuts and fringes." "\"$CUT\", \"$TRIM\"")
week() {
  python3 -c 'import json, sys
branch, days = sys.argv[1], [int(x) for x in sys.argv[2].split(",")]
shifts = [{"weekday": d, "locationId": branch, "startsAt": o, "endsAt": c} for d in days for o, c in (("09:00", "12:30"), ("13:30", "17:00"))]
print(json.dumps({"shifts": shifts}))' "$BRANCH" "$1"
}
call PUT "/api/v1/admin/staff/$JO/schedule/weekly" "$(week 1,2,3,4,5,6)" "$OWNER"
expect 200
call PUT "/api/v1/admin/staff/$SAM/schedule/weekly" "$(week 2,3,4,5,6)" "$OWNER"
expect 200
note "Jo works Monday to Saturday, Sam Tuesday to Saturday, both with a lunch break"

heading "Owner takes the salon live"
call POST /api/v1/admin/business/go-live "" "$OWNER"
expect 200
note "Status: $(py 'd["profile"]["status"]')"

printf '\n\033[1mDone.\033[0m\n'
note "Booking site:  $SITE_URL/book/$SLUG"
note "Owner sign-in: $OWNER_EMAIL / $OWNER_PASSWORD"
