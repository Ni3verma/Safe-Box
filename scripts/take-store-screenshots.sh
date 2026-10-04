#!/usr/bin/env bash
#
# Photographs the Play Store listing's scenes from the real app on an emulator and composites them
# into the store images.
#
# Installs the app APK and the :upgrade-test harness on a dedicated AVD, pins the device to a
# repeatable look, runs StoreScreenshotTour once to build the demo vault and once per appearance
# to photograph it, grabs the biometric sheet itself, and pulls everything to
# <out>/raw/<capture>-<theme>.png, 1080x2400 each. It then hands those to
# scripts/store-screenshots/render.py, which writes the 1080x1920 store images, the README block
# and the review page; see that script's header for what it reads and writes.
#
# Usage: scripts/take-store-screenshots.sh [options]
#   --apk <path>             app to photograph, a qa or release build
#                            (default app/build/outputs/apk/qa/SafeBox-qa.apk)
#   --avd <name>             AVD to use; created from the installed API 35 Play image if missing
#                            (default SafeBox_Store_Pixel_8_API_35)
#   --scenes all|a,b,...     scenes to capture (default all); ids are listed in Scene.kt
#   --theme both|light|dark  appearances to capture (default both)
#   --seed-color <hex>|none  Material You seed colour, RRGGBB (default 006A65, the app's own primary);
#                            none keeps whatever palette the device's wallpaper gives
#   --palette-seeds <hex,..> seed colours the palette scene photographs the password screen under
#                            (default 1565C0,6750A4,B3261E,2E7D32)
#   --out <dir>              directory that holds raw/ (default screenshots); anything else is a
#                            trial run whose store images stay beside its captures and whose
#                            README block is left alone
#   --keep-emulator          leave an emulator this script booted running
#   --render-only            skip the emulator; composite the captures already in <out>/raw
#   --no-render              stop after the captures are pulled
#   --render-theme <file>    compositor theme instead of scripts/store-screenshots/theme.json
#
# The AVD is dedicated, created and provisioned by scripts/lib/store-avd.sh: no hardware keyboard,
# a PIN and an enrolled fingerprint, none of which can be arranged on a running device. The
# first run takes a few minutes longer for that; nothing in it touches other AVDs.
#
# Everything the device is told to look like is undone on exit: night mode, the demo status bar and
# the palette go back to what they were. Build the app and the harness first; the biometric sheet
# prints the app's label, so the QA build is given the production one:
#   ./gradlew :app:assembleQa -PappLabel="Safe Box" :upgrade-test:assembleDebug
set -euo pipefail
# Name whatever command ends the run; a failing command whose output is redirected would
# otherwise leave only the exit trap's lines behind.
set -o errtrace
trap 'echo "error: \"$BASH_COMMAND\" failed at line $LINENO." >&2' ERR

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=lib/backup-files.sh
source "$script_dir/lib/backup-files.sh"
# shellcheck source=lib/instrumentation-guard.sh
source "$script_dir/lib/instrumentation-guard.sh"
# shellcheck source=lib/crash-sentinel.sh
source "$script_dir/lib/crash-sentinel.sh"
# shellcheck source=lib/harness.sh
source "$script_dir/lib/harness.sh"
# shellcheck source=lib/store-avd.sh
source "$script_dir/lib/store-avd.sh"

usage() {
    sed -n '/^# Usage:/,/^# The AVD/p' "${BASH_SOURCE[0]}" | sed '$d' | sed 's/^# \{0,1\}//' >&2
    exit 2
}

apk="app/build/outputs/apk/qa/SafeBox-qa.apk"
avd="SafeBox_Store_Pixel_8_API_35"
scenes="all"
theme="both"
# The app's own primary colour (primaryLight in app/src/main/java/com/andryoga/safebox/ui/theme/Color.kt),
# so the dynamic palette on API 31+ lands on the brand teal the static theme uses below it. The
# palette must be pinned at all: the app follows Material You, so an unpinned run takes the colour
# of whatever wallpaper the AVD happens to have.
seed_color="006A65"
# Four seeds a wallpaper might plausibly produce, far enough apart in hue to read as different
# palettes at a glance; the brand seed above is the fifth swatch the compositor can draw on.
palette_seeds="1565C0,6750A4,B3261E,2E7D32"
out_dir="screenshots"
keep_emulator=0
render_only=0
no_render=0
render_theme=""

while [ "$#" -gt 0 ]; do
    case "$1" in
        --apk) apk="${2:?--apk needs a path}"; shift 2 ;;
        --avd) avd="${2:?--avd needs a name}"; shift 2 ;;
        --scenes) scenes="${2:?--scenes needs all or a comma-separated list}"; shift 2 ;;
        --theme) theme="${2:?--theme needs both, light or dark}"; shift 2 ;;
        --seed-color) seed_color="${2:?--seed-color needs RRGGBB or none}"; shift 2 ;;
        --palette-seeds) palette_seeds="${2:?--palette-seeds needs a comma-separated list of RRGGBB}"; shift 2 ;;
        --out) out_dir="${2:?--out needs a directory}"; shift 2 ;;
        --keep-emulator) keep_emulator=1; shift ;;
        --render-only) render_only=1; shift ;;
        --no-render) no_render=1; shift ;;
        --render-theme) render_theme="${2:?--render-theme needs a file}"; shift 2 ;;
        -h|--help) usage ;;
        *) echo "error: unknown argument '$1'" >&2; usage ;;
    esac
done

# --out is compared with the default to tell a trial from the real thing, so spell it one way.
out_dir="${out_dir%/}"
out_dir="${out_dir#./}"
if [ "$render_only" -eq 1 ] && [ "$no_render" -eq 1 ]; then
    echo "error: --render-only and --no-render together leave nothing to do." >&2
    exit 2
fi
if [ -n "$render_theme" ] && [ ! -f "$render_theme" ]; then
    echo "error: --render-theme file '$render_theme' does not exist." >&2
    exit 2
fi

case "$theme" in
    both) themes="light dark" ;;
    light|dark) themes="$theme" ;;
    *) echo "error: --theme must be both, light or dark, got '$theme'." >&2; exit 2 ;;
esac
# Scene ids are validated on the device by Scene.parse, which names the valid ones; this only
# rejects shapes that could never be a list, before minutes are spent on the setup phase.
if ! printf '%s' "$scenes" | grep -qE '^[a-z_]+(,[a-z_]+)*$'; then
    echo "error: --scenes must be 'all' or comma-separated scene ids, got '$scenes'." >&2
    exit 2
fi
case ",$scenes," in
    *,all,*|*,unlock,*) unlock_scene=1 ;;
    *) unlock_scene=0 ;;
esac
case ",$scenes," in
    *,all,*|*,palette,*) palette_scene=1 ;;
    *) palette_scene=0 ;;
esac
is_rrggbb() {
    case "$1" in
        [0-9A-Fa-f][0-9A-Fa-f][0-9A-Fa-f][0-9A-Fa-f][0-9A-Fa-f][0-9A-Fa-f]) return 0 ;;
        *) return 1 ;;
    esac
}
if [ "$seed_color" != "none" ] && ! is_rrggbb "$seed_color"; then
    echo "error: --seed-color must be six hex digits (RRGGBB) or none, got '$seed_color'." >&2
    exit 2
fi
for seed in $(printf '%s' "$palette_seeds" | tr ',' ' '); do
    if ! is_rrggbb "$seed"; then
        echo "error: --palette-seeds must be comma-separated RRGGBB values, got '$seed'." >&2
        exit 2
    fi
done
if [ ! -f settings.gradle ]; then
    echo "error: run this from the repository root." >&2
    exit 2
fi
if [ ! -s "$apk" ]; then
    echo "error: $apk is missing or empty. Build it: ./gradlew :app:assembleQa -PappLabel=\"Safe Box\"" >&2
    exit 1
fi

OUT_ROOT="upgrade-test-out/store-screenshots"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
AVD_HOME="${ANDROID_AVD_HOME:-$HOME/.android/avd}"
# Where the tour writes captures. The shell user owns it, so `screencap` can write and `adb pull`
# can read without going through the app's or the harness's sandbox.
DEVICE_CAPTURE_DIR="/data/local/tmp/store-screenshots"
# Shown on the backup screen as "primary:<name>", so it is named for what it is rather than for
# the upgrade test.
BACKUP_DIR="SafeBoxBackups"
DEVICE_BACKUP_DIR="/sdcard/$BACKUP_DIR"
PALETTE_SETTING="theme_customization_overlay_packages"
DEMO_ACTION="com.android.systemui.demo"
# The window title SystemUI gives the biometric sheet (AuthContainerView), stable since Android 10.
BIOMETRIC_WINDOW="BiometricPrompt"
BIOMETRIC_CAPTURE="unlock_biometric"
SHEET_TIMEOUT_S=15
SCREENSHOT_TIMEOUT_S=10
PALETTE_SETTLE_S=3

pinned=0
palette_before=""

# One SystemUI demo-mode command. Ignored by the device unless sysui_demo_allowed is set.
demo() {
    adb shell am broadcast -a "$DEMO_ACTION" -e command "$@" > /dev/null
}

# The status bar as every store screenshot shows it: 10:00, full battery, full Wi-Fi and signal,
# no notification icons. The mobile icon needs an explicit slot on API 35: without one the demo
# connection is not created and the real modem's "3G" stays. Re-applied before every pass because
# a night-mode flip rebuilds the status bar. Demo mode is left and re-entered first: after a
# rebuild the controller still believes it is in demo mode, and a bare re-apply then brings the
# clock back but not the mobile icon (seen on every dark and palette capture, 2026-10-04).
apply_demo_status_bar() {
    demo exit
    demo enter
    demo clock -e hhmm 1000
    demo battery -e level 100 -e plugged false
    demo network -e wifi show -e level 4 -e fully true
    demo network -e mobile show -e datatype none -e level 4 -e slot 0
    demo notifications -e visible false
    demo status -e volume hide -e bluetooth hide -e location hide -e alarm hide -e sync hide \
        -e tty hide -e eri hide -e mute hide -e speakerphone hide
}

# Sets the Material You seed. Written as the secure-settings JSON the wallpaper picker writes;
# the whole command is quoted for the device shell, which otherwise brace-expands the JSON and
# settings complains that an argument was "expected to be 'default'". SystemUI rebuilds its colour
# overlays after the write, off the caller's thread, so a moment is allowed before anything is
# launched against the new palette.
#
# $1 - RRGGBB
pin_palette() {
    local json='{"android.theme.customization.system_palette":"FF'"$1"'","android.theme.customization.theme_style":"TONAL_SPOT","android.theme.customization.color_source":"preset"}'
    adb shell "settings put secure $PALETTE_SETTING '$json'"
    sleep "$PALETTE_SETTLE_S"
}

# Puts the palette back to what the device had before this run touched it.
restore_palette() {
    if [ -z "$palette_before" ] || [ "$palette_before" = "null" ]; then
        adb shell settings delete secure "$PALETTE_SETTING" > /dev/null 2>&1 || true
    else
        adb shell "settings put secure $PALETTE_SETTING '$palette_before'" > /dev/null 2>&1 || true
    fi
}

# The palette the regular captures use: the seed colour, or the device's own under --seed-color none.
run_palette() {
    if [ "$seed_color" != "none" ]; then
        pin_palette "$seed_color"
    else
        restore_palette
        sleep "$PALETTE_SETTLE_S"
    fi
}

# Pins everything about the device's look that is not the app's doing, and remembers what to put
# back.
pin_device() {
    palette_before=$(adb shell settings get secure "$PALETTE_SETTING" | tr -d '\r')
    pinned=1
    adb shell settings put global sysui_demo_allowed 1
    adb shell settings put system font_scale 1.0
    run_palette
    apply_demo_status_bar
}

unpin_device() {
    [ "$pinned" -eq 1 ] || return 0
    adb shell cmd uimode night no > /dev/null 2>&1 || true
    demo exit 2> /dev/null || true
    restore_palette
}

# $1 - light or dark. Verified after the fact because `cmd uimode` reports success for a value it
# did not apply; the tour checks again before its first capture.
set_night_mode() {
    local wanted
    case "$1" in
        dark) wanted=yes ;;
        *) wanted=no ;;
    esac
    adb shell cmd uimode night "$wanted" > /dev/null
    sleep 2
    local report
    report=$(adb shell cmd uimode night | tr -d '\r')
    case "$report" in
        *"$wanted") ;;
        *) echo "error: asked for night mode '$wanted', device reports '$report'." >&2; exit 1 ;;
    esac
}

# True once a PNG file is complete: its last eight bytes are the IEND chunk and its CRC.
png_is_complete() {
    [ "$(tail -c 8 "$1" 2>/dev/null | od -An -tx1 | tr -d ' \n')" = "49454e44ae426082" ]
}

# Photographs the biometric sheet, the one capture the tour cannot take.
#
# The sheet is a FLAG_SECURE window, so `screencap` (and anything else that asks SurfaceFlinger
# for a screenshot) gets an all-black frame while it is up. The emulator console's screenshot
# reads the emulator's own framebuffer instead and shows it. The emulator writes the file
# asynchronously under a name of its choosing, hence the empty staging directory and the wait
# for a complete PNG.
#
# $1 - light or dark
capture_biometric_sheet() {
    local target="$out_dir/raw/$BIOMETRIC_CAPTURE-$1.png"
    local staging
    staging="$(cd "$HARNESS_OUT" && pwd)/emulator-screenshot"
    rm -rf "$staging"
    mkdir -p "$staging" "$out_dir/raw"
    echo "== $BIOMETRIC_CAPTURE-$1: emulator console =="
    adb shell am force-stop "$APP_PACKAGE"
    adb shell am start -W -n "$LAUNCH_COMPONENT" > /dev/null
    local waited=0
    until adb shell dumpsys window windows | grep -q "$BIOMETRIC_WINDOW"; do
        sleep 1
        waited=$((waited + 1))
        if [ "$waited" -ge "$SHEET_TIMEOUT_S" ]; then
            echo "error: the biometric sheet did not appear within $SHEET_TIMEOUT_S s of a cold start." >&2
            exit 1
        fi
    done
    # The sheet's fingerprint icon plays a short entrance even with animations off.
    sleep 2
    adb emu screenrecord screenshot "$staging" > /dev/null
    local file="" seconds=0
    while :; do
        file=$(find "$staging" -name '*.png' | head -n 1)
        if [ -n "$file" ] && png_is_complete "$file"; then
            break
        fi
        sleep 1
        seconds=$((seconds + 1))
        if [ "$seconds" -ge "$SCREENSHOT_TIMEOUT_S" ]; then
            echo "error: the emulator did not write a screenshot into $staging within $SCREENSHOT_TIMEOUT_S s." >&2
            exit 1
        fi
    done
    mv "$file" "$target"
    adb shell input keyevent KEYCODE_BACK
    adb shell am force-stop "$APP_PACKAGE"
    echo "   $(wc -c < "$target" | tr -d ' ') bytes"
}

# Copies every capture off the device, one file at a time so an existing raw/ directory is added
# to rather than nested into; captures of scenes this run did not ask for stay as they were.
pull_captures() {
    local raw="$out_dir/raw" names name count=0
    mkdir -p "$raw"
    names=$(adb shell ls "$DEVICE_CAPTURE_DIR" | tr -d '\r' | grep -E '\.png$' || true)
    for name in $names; do
        if ! adb pull "$DEVICE_CAPTURE_DIR/$name" "$raw/$name" > /dev/null 2>&1 || [ ! -s "$raw/$name" ]; then
            echo "error: could not pull $DEVICE_CAPTURE_DIR/$name." >&2
            exit 1
        fi
        count=$((count + 1))
    done
    if [ "$count" -eq 0 ]; then
        echo "error: the tour reported success but left no captures in $DEVICE_CAPTURE_DIR." >&2
        exit 1
    fi
    echo "Pulled $count captures into $raw/:"
    printf '%s\n' "$names" | sed 's/^/   /'
}

# Composites <out>/raw into the store images. With the default --out the compositor's own
# defaults apply: screenshots/readme, the README block and screenshots/preview.html. Any other
# --out is a trial, so its images and review page stay beside its captures and the README keeps
# pointing at the committed set.
render_captures() {
    local args=(--raw "$out_dir/raw")
    if [ -n "$render_theme" ]; then
        args+=(--theme "$render_theme")
    fi
    if [ "$out_dir" != "screenshots" ]; then
        args+=(--out "$out_dir/readme" --readme - --preview "$out_dir/preview.html" --work-dir "$out_dir/render")
    fi
    echo "== Render =="
    python3 "$script_dir/store-screenshots/render.py" "${args[@]}"
}

if [ "$render_only" -eq 1 ]; then
    if [ ! -d "$out_dir/raw" ]; then
        echo "error: --render-only needs captures in $out_dir/raw/; run without it first." >&2
        exit 1
    fi
    render_captures
    exit 0
fi

ensure_avd
assert_avd_has_no_keyboard
# Covered from the moment it boots: harness_init below can exit (no harness APK, no build tools),
# and an emulator this script started must not outlive that. The trap widens once there is
# something to unpin and logs to collect.
trap 'stop_emulator' EXIT
start_emulator
harness_init "$OUT_ROOT"
trap 'unpin_device; harness_collect_logs; stop_emulator' EXIT
ensure_unlocked

package=$(apk_package "$apk")
case "$package" in
    com.andryoga.safebox|com.andryoga.safebox.qa) ;;
    *)
        echo "error: $apk declares applicationId '${package:-unreadable}'; a qa or release APK is needed." >&2
        echo "       debug builds carry a different label and test-only behaviour, and the harness does not declare them." >&2
        exit 1
        ;;
esac
APP_PACKAGE="$package"
label=$(apk_label "$apk")
echo "App: $apk ($APP_PACKAGE, versionCode $(apk_version_code "$apk"), label '$label')"
case "$label" in
    *QA*|*DEBUG*)
        echo "   The biometric sheet prints that label. For the store build with:" >&2
        echo "   ./gradlew :app:assembleQa -PappLabel=\"Safe Box\"" >&2
        ;;
esac
if [ "$unlock_scene" -eq 1 ]; then
    ensure_fingerprint_enrolled
fi

echo "== Install =="
pin_device
clean_slate
install_apk "$apk"
grant_notifications
# Adding an authenticator opens the QR scanner first; without the grant a permission dialog takes
# the window instead of the form.
adb shell pm grant "$APP_PACKAGE" android.permission.CAMERA
install_harness
reset_backup_dir
adb shell rm -rf "$DEVICE_CAPTURE_DIR"
LAUNCH_COMPONENT=$(adb shell cmd package resolve-activity --brief -c android.intent.category.LAUNCHER "$APP_PACKAGE" | tail -n 1 | tr -d '\r')

set_night_mode light
run_phase setup store.StoreScreenshotTour#setUpDemoVault \
    -e appPackage "$APP_PACKAGE" \
    -e backupDir "$BACKUP_DIR"

for pass in $themes; do
    set_night_mode "$pass"
    apply_demo_status_bar
    if [ "$unlock_scene" -eq 1 ]; then
        capture_biometric_sheet "$pass"
    fi
    run_phase "capture-$pass" store.StoreScreenshotTour#captureScenes \
        -e appPackage "$APP_PACKAGE" \
        -e theme "$pass" \
        -e scenes "$scenes" \
        -e captureDir "$DEVICE_CAPTURE_DIR"
    # The palette scene: the password screen once per seed, named after the seed, then the
    # palette goes back to the run's own before the next pass. A palette change rebuilds the
    # status bar, which forgets demo mode, so the demo status bar is applied again each time.
    if [ "$palette_scene" -eq 1 ]; then
        for seed in $(printf '%s' "$palette_seeds" | tr ',' ' '); do
            pin_palette "$seed"
            apply_demo_status_bar
            run_phase "palette-$seed-$pass" store.StoreScreenshotTour#captureScenes \
                -e appPackage "$APP_PACKAGE" \
                -e theme "$pass" \
                -e scenes unlock \
                -e variant "$seed" \
                -e captureDir "$DEVICE_CAPTURE_DIR"
        done
        run_palette
    fi
done

pull_captures
echo "== Captures done =="
if [ "$no_render" -eq 1 ]; then
    exit 0
fi
if ! render_captures; then
    echo "error: compositing failed; the captures are kept in $out_dir/raw/. Fix the cause and run again with --render-only." >&2
    exit 1
fi
