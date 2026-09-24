package com.andryoga.safebox.upgradetest

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/**
 * Where each phase's oracle lives, and how the post-upgrade one is judged against the baseline's.
 *
 * Both files sit in the harness's own app-private storage. The harness APK is installed once, before
 * Phase A, and is never replaced, so the file Phase A wrote is still there when Phase C runs against
 * the upgraded app. It is written by the instrumentation rather than printed, because the host needs
 * the exact bytes: logcat adds timestamps and truncates long lines, and both the ten-run acceptance
 * and the upgrade comparison work on bytes. The harness is debuggable, so `adb run-as` can read
 * either file back out without granting anything on the device.
 */
internal object OracleFiles {
    /** What Phase A records on the baseline. scripts/run-upgrade-test.sh reads it by this name. */
    const val BASELINE = "phase-a-oracle.txt"

    /** What Phase C records on the upgraded build. scripts/run-upgrade-test.sh reads it by this name. */
    const val UPGRADED = "phase-c-oracle.txt"

    private const val LOG_TAG = "OracleFiles"

    /**
     * Writes an oracle, replacing any earlier file of the same name.
     *
     * @param name one of [BASELINE] or [UPGRADED]
     * @param text the complete oracle
     */
    fun write(name: String, text: String) {
        val file = fileNamed(name)
        file.writeText(text)
        Log.i(LOG_TAG, "wrote ${text.length} bytes of oracle to $file")
    }

    /**
     * Fails unless the upgraded build describes exactly what the baseline described.
     *
     * Exact equality, deliberately. Every key is a resource name (ADR-0003) and every value is
     * what the screen rendered, so a copy edit cannot cause a difference; what can is a record
     * that stopped decrypting, a field that came back different, a setting that reset, or a record
     * that appeared or vanished. Any tolerance added here to absorb one of those becomes the blind
     * spot the next regression walks through, which is how the previous attempt at upgrade
     * testing stopped testing anything.
     *
     * The message lists the lines each side has that the other lacks, rather than just saying the
     * files differ: on CI the device is gone by the time anyone reads the failure.
     *
     * @param upgraded the oracle just captured on the upgraded build
     */
    fun assertMatchesBaseline(upgraded: String) {
        val baselineFile = fileNamed(BASELINE)
        check(baselineFile.exists()) {
            "no baseline oracle at $baselineFile. Phase A writes it, and the harness is never " +
                "reinstalled between phases, so it is missing only if Phase A did not run in this " +
                "installation of the harness"
        }
        val baseline = baselineFile.readText()
        if (baseline == upgraded) return

        val before = baseline.lines()
        val after = upgraded.lines()
        val lost = before - after.toSet()
        val gained = after - before.toSet()
        error(
            buildString {
                appendLine("the upgraded build does not show what the baseline showed.")
                if (lost.isEmpty() && gained.isEmpty()) {
                    appendLine("Same lines, different order - the records list sorts differently.")
                }
                lost.forEach { appendLine("- $it") }
                gained.forEach { appendLine("+ $it") }
                append("Both files are pulled into the run's artifacts as $BASELINE and $UPGRADED.")
            },
        )
    }

    private fun fileNamed(name: String): File =
        File(InstrumentationRegistry.getInstrumentation().context.filesDir, name)
}
