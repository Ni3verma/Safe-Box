#!/usr/bin/env bash
#
# Tests for scripts/store-screenshots/render.py, the compositor behind scripts/take-store-screenshots.sh.
#
# The renderer is the last thing between the raw captures and the Play Console upload, and the
# README is regenerated from the same run, so what it refuses matters as much as what it produces.
# Everything here runs on synthetic captures written by a few lines of Python: no emulator, no
# committed binary fixtures, and the committed scenes.json/theme.json are exercised as well so a
# renamed layout or a missing font fails in the PR that caused it. Chrome is needed; the renderer
# finds it the same way it does for real runs (STORE_SCREENSHOTS_CHROME, the macOS app, or
# google-chrome/chromium on PATH, which GitHub's Ubuntu runners have).
#
# Run: scripts/tests/store-screenshots-test.sh
set -uo pipefail

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
repo_root=$(cd "$script_dir/../.." && pwd)
render="$repo_root/scripts/store-screenshots/render.py"
store_dir="$repo_root/scripts/store-screenshots"

work_dir=$(mktemp -d)
trap 'rm -rf "$work_dir"' EXIT

failures=0
total=0

ok() {
    total=$((total + 1))
    echo "ok    $1"
}

fail() {
    total=$((total + 1))
    failures=$((failures + 1))
    echo "FAIL  $1"
    shift
    printf '      %s\n' "$@"
}

# Writes a PNG: make_png <path> <width> <height> <rgb|rgba> [filters]. The optional filters
# argument is a comma-separated list of PNG filter types applied to successive rows, cycling, so a
# decoder can be exercised against every filter the specification defines.
cat > "$work_dir/make_png.py" <<'PY'
import struct
import sys
import zlib

path, width, height, mode = sys.argv[1], int(sys.argv[2]), int(sys.argv[3]), sys.argv[4]
filters = [int(f) for f in sys.argv[5].split(",")] if len(sys.argv) > 5 else [0]
bpp = 4 if mode == "rgba" else 3


def pixel(x, y):
    rgb = [(x * 7 + y) & 0xFF, (y * 3) & 0xFF, (x ^ y) & 0xFF]
    return rgb + [(x + y) & 0xFF] if bpp == 4 else rgb


def paeth(a, b, c):
    p = a + b - c
    pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
    if pa <= pb and pa <= pc:
        return a
    return b if pb <= pc else c


def apply_filter(kind, row, prev):
    out = bytearray(len(row))
    for i in range(len(row)):
        left = row[i - bpp] if i >= bpp else 0
        up = prev[i]
        upper_left = prev[i - bpp] if i >= bpp else 0
        if kind == 0:
            predictor = 0
        elif kind == 1:
            predictor = left
        elif kind == 2:
            predictor = up
        elif kind == 3:
            predictor = (left + up) >> 1
        else:
            predictor = paeth(left, up, upper_left)
        out[i] = (row[i] - predictor) & 0xFF
    return out


raw = bytearray()
prev = bytearray(width * bpp)
for y in range(height):
    row = bytearray()
    for x in range(width):
        row += bytes(pixel(x, y))
    kind = filters[y % len(filters)]
    raw.append(kind)
    raw += apply_filter(kind, row, prev)
    prev = row


def chunk(kind, body):
    return struct.pack(">I", len(body)) + kind + body + struct.pack(">I", zlib.crc32(kind + body) & 0xFFFFFFFF)


header = struct.pack(">IIBBBBB", width, height, 8, 6 if bpp == 4 else 2, 0, 0, 0)
with open(path, "wb") as handle:
    handle.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(bytes(raw))) + chunk(b"IEND", b""))
PY

# Prints "<width>x<height> depth=<bits> colour=<type>" for a PNG.
cat > "$work_dir/png_info.py" <<'PY'
import struct
import sys

with open(sys.argv[1], "rb") as handle:
    head = handle.read(29)
if head[:8] != b"\x89PNG\r\n\x1a\n" or head[12:16] != b"IHDR":
    raise SystemExit("not a PNG")
width, height, depth, colour = struct.unpack(">IIBB", head[16:26])
print("%dx%d depth=%d colour=%d" % (width, height, depth, colour))
PY

make_png() {
    python3 "$work_dir/make_png.py" "$@"
}

png_info() {
    python3 "$work_dir/png_info.py" "$1"
}

# A fresh case directory with a raw/ folder holding 1080x2400 captures named after the arguments,
# a README with the marker block, and a tiny theme that reuses the committed font and frame.
# Synthetic captures are generated once and copied, because a full-size PNG takes a moment to
# write in pure Python.
new_case() {
    local name="$1"
    shift
    local dir="$work_dir/$name"
    mkdir -p "$dir/raw" "$dir/out"
    if [ ! -f "$work_dir/capture.png" ]; then
        make_png "$work_dir/capture.png" 1080 2400 rgb
    fi
    local capture
    for capture in "$@"; do
        cp "$work_dir/capture.png" "$dir/raw/$capture.png"
    done
    printf '# Title\n\nIntro paragraph.\n\n<!-- store-screenshots:start -->\nold block\n<!-- store-screenshots:end -->\n\nTrailing paragraph.\n' > "$dir/README.md"
    printf '%s\n' "$dir"
}

# Runs render.py for a case directory with the given scenes file (and any extra arguments),
# capturing output and exit status into the case directory.
render_case() {
    local dir="$1" scenes="$2"
    shift 2
    python3 "$render" --raw "$dir/raw" --out "$dir/out" --scenes "$scenes" --theme "${THEME:-$store_dir/theme.json}" \
        --readme "$dir/README.md" --preview "$dir/preview.html" --work-dir "$dir/work" "$@" > "$dir/output.txt" 2>&1
    echo $? > "$dir/status"
    cat "$dir/output.txt" > "$dir/log"
}

status_of() {
    cat "$1/status"
}

output_of() {
    cat "$1/output.txt"
}

write_scenes() {
    local path="$1"
    shift
    printf '%s\n' "$@" > "$path"
}

# --- rendering --------------------------------------------------------------------------------

dir=$(new_case renders first-light second-light second-dark)
write_scenes "$dir/scenes.json" '{ "readme": { "columns": 2, "width": 150 }, "scenes": [' \
    '{ "id": "first", "layout": "single", "captures": ["first-light"], "headline": "First\nline two", "subhead": "Sub" },' \
    '{ "id": "second", "layout": "duo", "captures": ["second-light", "second-dark"], "headline": "Second & co" } ] }'
render_case "$dir" "$dir/scenes.json"
if [ "$(status_of "$dir")" = 0 ]; then
    ok "renders_two_scenes_exit_zero"
else
    fail "renders_two_scenes_exit_zero" "$(output_of "$dir")"
fi
for name in 01-first.png 02-second.png; do
    info=$(png_info "$dir/out/$name" 2>&1)
    if [ "$info" = "1080x1920 depth=8 colour=2" ]; then
        ok "output_${name}_is_1080x1920_8bit_rgb_without_alpha"
    else
        fail "output_${name}_is_1080x1920_8bit_rgb_without_alpha" "got: $info"
    fi
done
if grep -q 'src="./out/01-first.png" alt="First line two" width="150"' "$dir/README.md" &&
    grep -q 'src="./out/02-second.png" alt="Second &amp; co" width="150"' "$dir/README.md" &&
    ! grep -q 'old block' "$dir/README.md"; then
    ok "readme_block_lists_outputs_in_store_order_with_escaped_alt_text"
else
    fail "readme_block_lists_outputs_in_store_order_with_escaped_alt_text" "$(cat "$dir/README.md")"
fi
if [ "$(sed -n '1,4p' "$dir/README.md")" = "$(printf '# Title\n\nIntro paragraph.\n')" ] &&
    grep -q '^Trailing paragraph.$' "$dir/README.md" &&
    [ "$(grep -c 'store-screenshots:start\|store-screenshots:end' "$dir/README.md")" = 2 ]; then
    ok "readme_outside_the_markers_is_untouched"
else
    fail "readme_outside_the_markers_is_untouched" "$(cat "$dir/README.md")"
fi
if grep -q 'out/01-first.png' "$dir/preview.html" && grep -q 'raw/second-dark.png' "$dir/preview.html"; then
    ok "preview_page_links_composites_and_raw_captures"
else
    fail "preview_page_links_composites_and_raw_captures"
fi

# Re-rendering must be a no-op for the README, and a reorder must remove the stale numbered file.
write_scenes "$dir/scenes.json" '{ "scenes": [' \
    '{ "id": "second", "layout": "duo", "captures": ["second-light", "second-dark"], "headline": "Second & co" },' \
    '{ "id": "first", "layout": "single", "captures": ["first-light"], "headline": "First" } ] }'
render_case "$dir" "$dir/scenes.json"
if [ "$(status_of "$dir")" = 0 ] && [ -f "$dir/out/01-second.png" ] && [ -f "$dir/out/02-first.png" ] &&
    [ ! -f "$dir/out/01-first.png" ] && [ ! -f "$dir/out/02-second.png" ]; then
    ok "reordering_scenes_renumbers_outputs_and_removes_stale_ones"
else
    fail "reordering_scenes_renumbers_outputs_and_removes_stale_ones" "$(ls "$dir/out")" "$(output_of "$dir")"
fi

# --only renders a subset and leaves the README alone until the set is complete.
dir=$(new_case subset a-light b-light)
write_scenes "$dir/scenes.json" '{ "scenes": [' \
    '{ "id": "a", "layout": "single", "captures": ["a-light"], "headline": "A" },' \
    '{ "id": "b", "layout": "single", "captures": ["b-light"], "headline": "B" } ] }'
render_case "$dir" "$dir/scenes.json" --only a
if [ "$(status_of "$dir")" = 0 ] && [ -f "$dir/out/01-a.png" ] && [ ! -f "$dir/out/02-b.png" ] &&
    grep -q 'old block' "$dir/README.md" && output_of "$dir" | grep -q 'not written'; then
    ok "only_renders_the_named_scene_and_keeps_readme_until_complete"
else
    fail "only_renders_the_named_scene_and_keeps_readme_until_complete" "$(output_of "$dir")"
fi

# A subset render after a reorder must still drop the file left under the old number; otherwise it
# sits in the output directory, unreferenced by the README, and gets committed.
write_scenes "$dir/scenes.json" '{ "scenes": [' \
    '{ "id": "b", "layout": "single", "captures": ["b-light"], "headline": "B" },' \
    '{ "id": "a", "layout": "single", "captures": ["a-light"], "headline": "A" } ] }'
render_case "$dir" "$dir/scenes.json" --only b
if [ "$(status_of "$dir")" = 0 ] && [ -f "$dir/out/01-b.png" ] && [ ! -f "$dir/out/01-a.png" ] &&
    [ ! -f "$dir/out/02-a.png" ] && output_of "$dir" | grep -q 'not written'; then
    ok "only_after_a_reorder_removes_the_stale_numbered_output"
else
    fail "only_after_a_reorder_removes_the_stale_numbered_output" "$(ls "$dir/out")" "$(output_of "$dir")"
fi

# --- refusals ---------------------------------------------------------------------------------

# $1 case name, $2 expected message fragment, $3 scenes JSON, rest: captures to provide
expect_refusal() {
    local name="$1" fragment="$2" scenes="$3"
    shift 3
    local dir
    dir=$(new_case "$name" "$@")
    printf '%s\n' "$scenes" > "$dir/scenes.json"
    render_case "$dir" "$dir/scenes.json"
    if [ "$(status_of "$dir")" = 1 ] && output_of "$dir" | grep -qF -- "$fragment"; then
        ok "$name"
    else
        fail "$name" "exit $(status_of "$dir"), expected 1 with '$fragment'" "$(output_of "$dir")"
    fi
}

expect_refusal "refuses_a_missing_capture" "does not exist" \
    '{ "scenes": [ { "id": "a", "layout": "single", "captures": ["a-light"], "headline": "A" }, { "id": "b", "layout": "single", "captures": ["missing-light"], "headline": "B" } ] }' \
    a-light

dir=$(new_case wrong_size a-light)
make_png "$dir/raw/b-light.png" 1080 1920 rgb
printf '%s\n' '{ "scenes": [ { "id": "a", "layout": "single", "captures": ["a-light"], "headline": "A" }, { "id": "b", "layout": "single", "captures": ["b-light"], "headline": "B" } ] }' > "$dir/scenes.json"
render_case "$dir" "$dir/scenes.json"
if [ "$(status_of "$dir")" = 1 ] && output_of "$dir" | grep -q 'b-light.png is 1080x1920; the pixel_8 frame takes 1080x2400 captures'; then
    ok "refuses_a_capture_that_does_not_fit_the_frame"
else
    fail "refuses_a_capture_that_does_not_fit_the_frame" "$(output_of "$dir")"
fi

expect_refusal "refuses_a_single_scene" "Play accepts 2 to 8 phone screenshots, 1 are listed" \
    '{ "scenes": [ { "id": "a", "layout": "single", "captures": ["a-light"], "headline": "A" } ] }' a-light

nine='{ "scenes": ['
for i in 1 2 3 4 5 6 7 8 9; do
    nine="$nine { \"id\": \"s$i\", \"layout\": \"single\", \"captures\": [\"a-light\"], \"headline\": \"S\" },"
done
nine="${nine%,} ] }"
expect_refusal "refuses_nine_scenes" "Play accepts 2 to 8 phone screenshots, 9 are listed" "$nine" a-light

expect_refusal "refuses_an_unknown_layout" "layout 'trio' is not defined" \
    '{ "scenes": [ { "id": "a", "layout": "trio", "captures": ["a-light"], "headline": "A" }, { "id": "b", "layout": "single", "captures": ["a-light"], "headline": "B" } ] }' a-light

expect_refusal "refuses_a_capture_count_that_does_not_match_the_layout" "layout duo has 2 slot(s) but 1 capture(s) are listed" \
    '{ "scenes": [ { "id": "a", "layout": "duo", "captures": ["a-light"], "headline": "A" }, { "id": "b", "layout": "single", "captures": ["a-light"], "headline": "B" } ] }' a-light

expect_refusal "refuses_duplicate_scene_ids" "scene ids must be unique" \
    '{ "scenes": [ { "id": "a", "layout": "single", "captures": ["a-light"], "headline": "A" }, { "id": "a", "layout": "single", "captures": ["a-light"], "headline": "B" } ] }' a-light

dir=$(new_case no_markers a-light)
printf '# Title\n\nNo markers here.\n' > "$dir/README.md"
printf '%s\n' '{ "scenes": [ { "id": "a", "layout": "single", "captures": ["a-light"], "headline": "A" }, { "id": "b", "layout": "single", "captures": ["a-light"], "headline": "B" } ] }' > "$dir/scenes.json"
render_case "$dir" "$dir/scenes.json"
if [ "$(status_of "$dir")" = 1 ] && output_of "$dir" | grep -q 'has no <!-- store-screenshots:start -->'; then
    ok "refuses_a_readme_without_the_marker_block"
else
    fail "refuses_a_readme_without_the_marker_block" "$(output_of "$dir")"
fi

dir=$(new_case bad_clip a-light)
python3 - "$store_dir/theme.json" "$dir/theme.json" <<'EOF'
import json, sys
theme = json.load(open(sys.argv[1]))
theme["layouts"]["half"] = [{"left": 0, "top": 0, "scale": 0.5, "clip": [[0, 0], [100, 100]]}]
json.dump(theme, open(sys.argv[2], "w"))
EOF
printf '%s\n' '{ "scenes": [ { "id": "a", "layout": "half", "captures": ["a-light"], "headline": "A" }, { "id": "b", "layout": "single", "captures": ["a-light"], "headline": "B" } ] }' > "$dir/scenes.json"
THEME="$dir/theme.json" render_case "$dir" "$dir/scenes.json"
if [ "$(status_of "$dir")" = 1 ] && output_of "$dir" | grep -qF 'layouts.half[0].clip must be a list of at least three [x, y] points'; then
    ok "refuses_a_slot_clip_with_fewer_than_three_points"
else
    fail "refuses_a_slot_clip_with_fewer_than_three_points" "$(output_of "$dir")"
fi

# --- alpha fallback ---------------------------------------------------------------------------

# Chrome writes RGB for an opaque page, so the RGBA path only runs if that ever changes; it is
# exercised here directly, against every filter type the PNG specification defines.
make_png "$work_dir/rgba.png" 64 40 rgba 0,1,2,3,4
python3 - "$store_dir" "$work_dir/rgba.png" "$work_dir/rgb.png" "$work_dir/make_png.py" > "$work_dir/alpha.txt" 2>&1 <<'PY'
import importlib.util
import struct
import sys
import zlib

store_dir, source, target, _ = sys.argv[1:5]
spec = importlib.util.spec_from_file_location("render", store_dir + "/render.py")
render = importlib.util.module_from_spec(spec)
spec.loader.exec_module(render)
from pathlib import Path
render.strip_alpha(Path(source), Path(target))
width, height, depth, colour, interlace = render.png_info(target)
assert (width, height, depth, colour, interlace) == (64, 40, 8, 2, 0), (width, height, depth, colour, interlace)
data = Path(target).read_bytes()
raw = zlib.decompress(b"".join(body for kind, body in render.png_chunks(data) if kind == b"IDAT"))
stride = width * 3
previous = bytearray(stride)
pos = 0
for y in range(height):
    row = bytearray(raw[pos + 1:pos + 1 + stride])
    render.unfilter_row(raw[pos], row, previous, 3)
    pos += 1 + stride
    for x in range(width):
        expected = ((x * 7 + y) & 0xFF, (y * 3) & 0xFF, (x ^ y) & 0xFF)
        got = tuple(row[x * 3:x * 3 + 3])
        assert got == expected, (x, y, got, expected)
    previous = row
print("ok")
PY
if [ "$(cat "$work_dir/alpha.txt")" = "ok" ]; then
    ok "strip_alpha_decodes_every_filter_type_and_keeps_rgb_exact"
else
    fail "strip_alpha_decodes_every_filter_type_and_keeps_rgb_exact" "$(cat "$work_dir/alpha.txt")"
fi

# --- the committed configuration ---------------------------------------------------------------

# scenes.json and theme.json as committed, with a synthetic capture for every name they mention:
# a renamed layout, a missing font or frame file, or a ninth scene fails here.
dir=$(new_case committed)
captures=$(python3 -c '
import json, sys
scenes = json.load(open(sys.argv[1]))
print("\n".join(sorted({c for s in scenes["scenes"] for c in s["captures"]})))
' "$store_dir/scenes.json")
for capture in $captures; do
    cp "$work_dir/capture.png" "$dir/raw/$capture.png"
done
render_case "$dir" "$store_dir/scenes.json" --readme - --preview -
count=$(find "$dir/out" -name '*.png' | wc -l | tr -d ' ')
if [ "$(status_of "$dir")" = 0 ] && [ "$count" -ge 2 ] && [ "$count" -le 8 ]; then
    ok "committed_scenes_and_theme_render_${count}_images"
else
    fail "committed_scenes_and_theme_render" "exit $(status_of "$dir"), $count image(s)" "$(output_of "$dir")"
fi

echo
echo "$((total - failures))/$total passed"
[ "$failures" -eq 0 ]
