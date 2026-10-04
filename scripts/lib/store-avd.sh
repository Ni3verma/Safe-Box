#!/usr/bin/env bash
#
# The dedicated store-screenshot AVD: creating it, booting it, getting past its keyguard and
# enrolling the fingerprint the biometric scene needs. Sourced by scripts/take-store-screenshots.sh
# after scripts/lib/harness.sh; defines functions and constants only.
#
# Why a dedicated AVD rather than whichever emulator is running: two things the captures depend on
# cannot be changed on a running device. The AVD must have no hardware keyboard, or Gboard shows a
# one-line toolbar where the search scene needs a keyboard; and it must have a fingerprint
# enrolled, which takes a screen lock and a walk through Settings. Both are done once, here, and
# never touch the developer's own AVD.
#
# Callers set: avd (name), SDK, AVD_HOME, OUT_ROOT, keep_emulator. This file sets booted_emulator
# and exports ANDROID_SERIAL. Bash 3.2 compatible.

# The same image the development AVD uses, so nothing is downloaded; Play images are what the
# listing's users run. The Mac's architecture picks the ABI.
STORE_SYSTEM_IMAGE_DIR="system-images/android-35/google_apis_playstore"
STORE_DEVICE_PROFILE="pixel_8"
# The screen lock enrolment needs. Known to this script so later boots can be unlocked.
STORE_PIN="1234"
STORE_BOOT_TIMEOUT_S=180
STORE_SCREEN_TIMEOUT_S=15
STORE_MAX_SENSOR_TOUCHES=30

booted_emulator=0

# avdmanager is a Java program. The wrapper scripts look for JAVA_HOME, and a bare macOS shell
# has none, so Android Studio's runtime is the fallback, as for Gradle in this repository.
resolve_java_home() {
    if [ -n "${JAVA_HOME:-}" ]; then
        printf '%s\n' "$JAVA_HOME"
        return
    fi
    local studio="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
    if [ -x "$studio/bin/java" ]; then
        printf '%s\n' "$studio"
        return
    fi
    echo "error: creating the AVD needs Java for avdmanager; set JAVA_HOME." >&2
    exit 1
}

# Creates the AVD from the installed Play image and the Pixel 8 profile. Studio's wizard would
# enable the hardware keyboard, which is why creation is scripted rather than documented; the AVD
# shows up in Studio's device manager like any other.
ensure_avd() {
    if [ -d "$AVD_HOME/$avd.avd" ]; then
        return
    fi
    local abi
    case "$(uname -m)" in
        arm64|aarch64) abi="arm64-v8a" ;;
        *) abi="x86_64" ;;
    esac
    local package="${STORE_SYSTEM_IMAGE_DIR//\//;};$abi"
    if [ ! -d "$SDK/$STORE_SYSTEM_IMAGE_DIR/$abi" ]; then
        echo "error: AVD '$avd' does not exist and the image to create it from is not installed." >&2
        echo "       Install it once (network): sdkmanager \"$package\"" >&2
        exit 1
    fi
    local avdmanager="$SDK/cmdline-tools/latest/bin/avdmanager"
    if [ ! -x "$avdmanager" ]; then
        echo "error: AVD '$avd' does not exist and $avdmanager is missing; install the SDK command-line tools." >&2
        exit 1
    fi
    local java_home
    java_home=$(resolve_java_home)
    echo "== Creating AVD $avd =="
    mkdir -p "$OUT_ROOT"
    # avdmanager asks whether to build a custom hardware profile when it has a terminal; "no"
    # keeps the device profile's answers.
    if ! echo no | JAVA_HOME="$java_home" "$avdmanager" create avd -n "$avd" -k "$package" \
        -d "$STORE_DEVICE_PROFILE" > "$OUT_ROOT/avdmanager.log" 2>&1; then
        echo "error: avdmanager could not create '$avd'; see $OUT_ROOT/avdmanager.log" >&2
        exit 1
    fi
    local config="$AVD_HOME/$avd.avd/config.ini"
    if [ ! -f "$config" ]; then
        echo "error: avdmanager reported success but $config does not exist." >&2
        exit 1
    fi
    # avdmanager's defaults are an 800 MB data partition and no explicit RAM, well under what a
    # Pixel 8 Play image needs; these are the values Studio writes for the same device and image.
    grep -vE '^(hw\.keyboard|hw\.gpu\.enabled|hw\.gpu\.mode|hw\.ramSize|vm\.heapSize|disk\.dataPartition\.size)=' \
        "$config" > "$config.tmp"
    {
        cat "$config.tmp"
        echo "hw.keyboard=no"
        echo "hw.gpu.enabled=yes"
        echo "hw.gpu.mode=auto"
        echo "hw.ramSize=2048"
        echo "vm.heapSize=256"
        echo "disk.dataPartition.size=6442450944"
    } > "$config"
    rm -f "$config.tmp"
}

# Refuses an AVD that was created by hand with the keyboard on; the search scene would show
# Gboard's hardware-keyboard toolbar instead of a keyboard, and nothing at run time can change it.
assert_avd_has_no_keyboard() {
    local config="$AVD_HOME/$avd.avd/config.ini"
    if [ -f "$config" ] && grep -q '^hw.keyboard=yes' "$config"; then
        echo "error: AVD '$avd' has hw.keyboard=yes. Either set hw.keyboard=no in $config" >&2
        echo "       (host keyboard input into that emulator stops working) or let this script" >&2
        echo "       create its own AVD by not passing --avd." >&2
        exit 1
    fi
}

# The serial of the running emulator that booted the wanted AVD, if any. `adb emu avd name`
# answers with the name and then OK on a second line.
running_serial_for_avd() {
    local serial name
    for serial in $(adb devices | awk 'NR > 1 && $1 ~ /^emulator-/ && $2 == "device" { print $1 }'); do
        name=$(adb -s "$serial" emu avd name 2>/dev/null | head -n 1 | tr -d '\r')
        if [ "$name" = "$avd" ]; then
            printf '%s\n' "$serial"
            return
        fi
    done
}

# Uses the AVD if it is already running, otherwise boots it, and pins ANDROID_SERIAL to it so a
# second emulator (the developer's own) never receives a command meant for this one.
start_emulator() {
    local serial
    serial=$(running_serial_for_avd)
    if [ -z "$serial" ]; then
        local emulator="$SDK/emulator/emulator"
        if [ ! -x "$emulator" ]; then
            echo "error: no emulator binary at $emulator; set ANDROID_HOME." >&2
            exit 1
        fi
        mkdir -p "$OUT_ROOT"
        echo "== Booting $avd =="
        "$emulator" -avd "$avd" -no-boot-anim -no-audio > "$OUT_ROOT/emulator.log" 2>&1 &
        booted_emulator=1
        local waited=0
        while [ -z "$serial" ]; do
            sleep 3
            waited=$((waited + 3))
            if [ "$waited" -ge "$STORE_BOOT_TIMEOUT_S" ]; then
                echo "error: $avd did not appear in adb within $STORE_BOOT_TIMEOUT_S s; see $OUT_ROOT/emulator.log" >&2
                exit 1
            fi
            serial=$(running_serial_for_avd)
        done
        until [ "$(adb -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
            sleep 3
            waited=$((waited + 3))
            if [ "$waited" -ge "$STORE_BOOT_TIMEOUT_S" ]; then
                echo "error: $avd did not finish booting in $STORE_BOOT_TIMEOUT_S s; see $OUT_ROOT/emulator.log" >&2
                exit 1
            fi
        done
    fi
    ANDROID_SERIAL="$serial"
    export ANDROID_SERIAL
    await_package_manager
}

# sys.boot_completed is answered the moment a snapshot is restored, before every service is back,
# and the first things this script does are an uninstall and an install. The package manager has
# to answer before the device is used.
await_package_manager() {
    local waited=0
    until adb shell pm path android > /dev/null 2>&1; do
        sleep 3
        waited=$((waited + 3))
        if [ "$waited" -ge "$STORE_BOOT_TIMEOUT_S" ]; then
            echo "error: the package manager on $avd did not answer within $STORE_BOOT_TIMEOUT_S s." >&2
            exit 1
        fi
    done
}

stop_emulator() {
    if [ "$booted_emulator" -eq 1 ] && [ "$keep_emulator" -eq 0 ]; then
        adb emu kill > /dev/null 2>&1 || true
    fi
}

# Leaves the emulator running after a failure the user has to fix in its window.
keep_emulator_for_user() {
    keep_emulator=1
}

device_is_locked() {
    adb shell dumpsys trust | grep -q 'deviceLocked=1'
}

# A cold boot of an AVD with a screen lock lands on the keyguard, behind which the app's windows
# are neither visible nor photographable. Quick Boot usually resumes an unlocked session, so this
# is mostly a no-op; when it is not, the PIN this script set is typed.
ensure_unlocked() {
    adb shell input keyevent KEYCODE_WAKEUP
    device_is_locked || return 0
    adb shell wm dismiss-keyguard
    sleep 1
    adb shell input text "$STORE_PIN"
    adb shell input keyevent KEYCODE_ENTER
    sleep 2
    if device_is_locked; then
        keep_emulator_for_user
        echo "error: $avd is locked and PIN $STORE_PIN did not unlock it. Unlock it in the emulator window and re-run." >&2
        exit 1
    fi
}

# Enrolled fingerprints of the primary user, from the JSON dumpsys prints per user; id 0 is the
# primary user.
fingerprint_count() {
    adb shell dumpsys fingerprint | tr -d '\r' |
        sed -n 's/.*"prints":\[{"id":0,"count":\([0-9]*\).*/\1/p' | head -n 1
}

# The current screen's nodes, one per line, as uiautomator describes them.
screen_nodes() {
    adb exec-out uiautomator dump /dev/tty 2>/dev/null | tr '>' '\n'
}

# Taps the centre of the first node whose attributes contain $1 (an attribute=value fragment such
# as text="DONE"). Returns 1, without tapping, when no such node is on screen.
tap_node() {
    local bounds
    bounds=$(screen_nodes | grep -F -- "$1" | head -n 1 |
        sed -n 's/.*bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]".*/\1 \2 \3 \4/p')
    [ -n "$bounds" ] || return 1
    # shellcheck disable=SC2086
    set -- $bounds
    adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
}

# Waits up to STORE_SCREEN_TIMEOUT_S for a node whose attributes contain $1.
await_node() {
    local waited=0
    until screen_nodes | grep -qF -- "$1"; do
        sleep 1
        waited=$((waited + 1))
        [ "$waited" -lt "$STORE_SCREEN_TIMEOUT_S" ] || return 1
    done
}

# $1 - what went wrong
enrolment_help() {
    keep_emulator_for_user
    echo "error: could not enrol a fingerprint on $avd automatically: $1." >&2
    echo "       Do it once by hand in the emulator window (left running): Settings > Security & privacy >" >&2
    echo "       Device unlock > Fingerprint Unlock > Add fingerprint, then in the emulator's extended" >&2
    echo "       controls (...) > Fingerprint press 'Touch sensor' until Settings says done. Re-run afterwards." >&2
    exit 1
}

# Enrols one fingerprint if none is, by walking Settings' own enrolment flow: set the PIN it
# requires, confirm the PIN, read the introduction to its end, then touch the virtual sensor
# until Settings is satisfied. Labels are those of the API 35 Play image in English, which is
# the image this AVD is created from; any other screen falls back to the manual instructions.
ensure_fingerprint_enrolled() {
    local enrolled
    enrolled=$(fingerprint_count)
    if [ -n "$enrolled" ] && [ "$enrolled" -ge 1 ]; then
        echo "Fingerprints enrolled: $enrolled"
        return
    fi
    echo "== Enrolling a fingerprint on $avd (once) =="
    if ! adb shell locksettings set-pin "$STORE_PIN" > /dev/null 2>&1 &&
        ! adb shell locksettings verify --old "$STORE_PIN" > /dev/null 2>&1; then
        enrolment_help "the device already has a screen lock that is not PIN $STORE_PIN"
    fi
    adb shell am start -W -a android.settings.FINGERPRINT_ENROLL > /dev/null
    await_node 'class="android.widget.EditText"' || enrolment_help "the PIN prompt did not appear"
    tap_node 'class="android.widget.EditText"'
    adb shell input text "$STORE_PIN"
    adb shell input keyevent KEYCODE_ENTER
    # The introduction offers "I agree" only once "More" has scrolled it to the end.
    local tries=0
    until tap_node 'text="I AGREE"'; do
        tap_node 'text="MORE"' || true
        sleep 1
        tries=$((tries + 1))
        [ "$tries" -lt "$STORE_SCREEN_TIMEOUT_S" ] || enrolment_help "the introduction never offered 'I agree'"
    done
    await_node 'text="Touch the sensor"' || enrolment_help "the sensor screen did not appear"
    # Every console touch is one enrolment step; the virtual sensor wants a handful.
    local touches=0
    until tap_node 'text="DONE"'; do
        adb emu finger touch 1 > /dev/null
        sleep 1
        touches=$((touches + 1))
        [ "$touches" -lt "$STORE_MAX_SENSOR_TOUCHES" ] || enrolment_help "enrolment did not finish after $touches sensor touches"
    done
    adb shell am force-stop com.android.settings
    enrolled=$(fingerprint_count)
    if [ -z "$enrolled" ] || [ "$enrolled" -lt 1 ]; then
        enrolment_help "Settings finished but dumpsys fingerprint still reports none"
    fi
    echo "   enrolled after $touches sensor touches"
}
