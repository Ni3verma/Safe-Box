#!/usr/bin/env bash
#
# Proves a Play service-account key works for Safe-Box without publishing anything.
#
# The upload in release.yml runs only on a tag, at the end of an hour-long pipeline, and the three
# ways its credential can be wrong all surface there as one opaque 401/403: the Google Play Android
# Developer API not enabled on the key's project, the service account not (yet) invited in Play
# Console - a fresh invite can take up to a day to propagate - or the wrong package name. This
# script makes the same authenticated calls in ten seconds: it mints the OAuth token the upload
# action would, opens an edit, lists the tracks, writes the internal track back unchanged, and
# discards the edit. Edits are transactional, so an uncommitted one changes nothing in Play Console.
#
# What a pass proves: the key is valid, the API is enabled, the invite has propagated, the account
# can read the app, and it holds the write permission the upload needs ("Release apps to testing
# tracks") - the no-op update is refused without it. That is everything the upload job needs
# short of a real bundle.
#
# Needs network: run it from your own shell, not the agent sandbox. Nothing from the key file is
# printed; the private key is written to a private temp directory that is removed on exit.
#
# Usage: scripts/play-api-probe.sh <service-account.json> [package-name]
set -euo pipefail

if [ "$#" -lt 1 ] || [ "$#" -gt 2 ]; then
    echo "usage: $0 <service-account.json> [package-name]" >&2
    exit 2
fi
key_file="$1"
package="${2:-com.andryoga.safebox}"
[ -s "$key_file" ] || { echo "error: '$key_file' is missing or empty." >&2; exit 2; }

work_dir=$(mktemp -d)
chmod 700 "$work_dir"
trap 'rm -rf "$work_dir"' EXIT

# Pulls one string field out of the key file. $1 - field name
key_field() {
    python3 -c 'import json, sys; print(json.load(open(sys.argv[1]))[sys.argv[2]])' "$key_file" "$1"
}

email=$(key_field client_email)
key_field private_key > "$work_dir/key.pem"

# RFC 7515 base64url, single line, unpadded.
b64url() {
    openssl base64 -A | tr '+/' '-_' | tr -d '='
}

# A ten-minute JWT bearer assertion, the same grant the upload action's google-auth library uses.
now=$(date +%s)
header=$(printf '{"alg":"RS256","typ":"JWT"}' | b64url)
claims=$(printf '{"iss":"%s","scope":"https://www.googleapis.com/auth/androidpublisher","aud":"https://oauth2.googleapis.com/token","iat":%d,"exp":%d}' \
    "$email" "$now" $((now + 600)) | b64url)
signature=$(printf '%s.%s' "$header" "$claims" | openssl dgst -sha256 -sign "$work_dir/key.pem" | b64url)

token=$(curl -sS -X POST https://oauth2.googleapis.com/token \
    -d grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer \
    -d assertion="$header.$claims.$signature" |
    python3 -c 'import json, sys
d = json.load(sys.stdin)
if "access_token" not in d:
    sys.exit("token exchange failed (bad key file or clock skew): " + json.dumps(d))
print(d["access_token"])')
echo "OK: token issued for $email"

base="https://androidpublisher.googleapis.com/androidpublisher/v3/applications/$package"
auth=(-H "Authorization: Bearer $token")

edit_id=$(curl -sS -X POST "${auth[@]}" -H 'Content-Length: 0' "$base/edits" |
    python3 -c 'import json, sys
d = json.load(sys.stdin)
if "id" not in d:
    sys.exit("edits.insert failed - API not enabled, invite not propagated yet, or wrong package: " + json.dumps(d))
print(d["id"])')
echo "OK: edit opened for $package"

tracks_json="$work_dir/tracks.json"
curl -sS "${auth[@]}" "$base/edits/$edit_id/tracks" > "$tracks_json"
python3 -c 'import json, sys
d = json.load(open(sys.argv[1]))
if "tracks" not in d:
    sys.exit("edits.tracks.list failed: " + json.dumps(d))
print("OK: tracks readable")
for t in d["tracks"]:
    name = t["track"]
    releases = t.get("releases") or []
    if not releases:
        print("  %s: no releases" % name)
    for r in releases:
        print("  %s: %s [%s] versionCodes=%s" % (name, r.get("name", "?"), r.get("status"), r.get("versionCodes", [])))' "$tracks_json"

# The write permission the upload needs ("Release apps to testing tracks") is only exercised by a
# write, so put the internal track back exactly as it is. Still inside the edit that is discarded
# below, so even a successful call changes nothing.
python3 -c 'import json, sys
d = json.load(open(sys.argv[1]))
for t in d["tracks"]:
    if t["track"] == "internal":
        json.dump(t, sys.stdout)
        break
else:
    sys.exit("no internal track found; create it in Play Console (Testing -> Internal testing)")' "$tracks_json" > "$work_dir/internal.json"
curl -sS -X PUT "${auth[@]}" -H 'Content-Type: application/json' \
    --data-binary @"$work_dir/internal.json" "$base/edits/$edit_id/tracks/internal" |
    python3 -c 'import json, sys
d = json.load(sys.stdin)
if d.get("track") != "internal":
    sys.exit("edits.tracks.update failed - the account lacks \"Release apps to testing tracks\": " + json.dumps(d))
print("OK: internal track writable (no-op update accepted)")'

status=$(curl -sS -o /dev/null -w '%{http_code}' -X DELETE "${auth[@]}" "$base/edits/$edit_id")
if [ "$status" != "204" ]; then
    echo "warning: discarding the edit returned HTTP $status; it expires on its own and commits nothing." >&2
else
    echo "OK: edit discarded, nothing changed"
fi
