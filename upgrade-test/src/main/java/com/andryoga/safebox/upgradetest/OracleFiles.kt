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
 *
 * The same holds across Phase D's `pm clear`: that clears the *app under test*, not the harness, so
 * Phase C's files are still there for Phase D to be judged against.
 */
internal object OracleFiles {
    /** What Phase A records on the baseline. scripts/run-upgrade-test.sh reads it by this name. */
    const val BASELINE = "phase-a-oracle.txt"

    /** What Phase C records on the upgraded build. scripts/run-upgrade-test.sh reads it by this name. */
    const val UPGRADED = "phase-c-oracle.txt"

    /**
     * What Phase C records after adding the authenticator (step 12), and what Phase D's restored
     * vault is compared against. scripts/run-upgrade-test.sh reads it by this name.
     */
    const val WITH_AUTHENTICATOR = "phase-c-authenticator-oracle.txt"

    /** What Phase D records after the round trip. scripts/run-upgrade-test.sh reads it by this name. */
    const val ROUND_TRIP = "phase-d-oracle.txt"

    /**
     * What Phase D records after restoring `v1_legacy.bak` over the round-tripped vault.
     * scripts/run-upgrade-test.sh reads it by this name.
     */
    const val LEGACY = "phase-d-legacy-oracle.txt"

    private const val LOG_TAG = "OracleFiles"

    /**
     * Writes an oracle, replacing any earlier file of the same name.
     *
     * @param name one of the file names above
     * @param text the complete oracle
     */
    fun write(name: String, text: String) {
        val file = fileNamed(name)
        file.writeText(text)
        Log.i(LOG_TAG, "wrote ${text.length} bytes of oracle to $file")
    }

    /**
     * Reads an oracle an earlier phase wrote.
     *
     * @param name one of the file names above
     * @return its complete text
     */
    fun read(name: String): String {
        val file = fileNamed(name)
        check(file.exists()) {
            "no oracle at $file. An earlier phase writes it, and the harness is never reinstalled " +
                "between phases, so it is missing only if that phase did not run in this " +
                "installation of the harness"
        }
        return file.readText()
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
        val baseline = read(BASELINE)
        if (baseline == upgraded) return
        failWithDiff(
            "the upgraded build does not show what the baseline showed.",
            baseline.lines(),
            upgraded.lines(),
            "$BASELINE and $UPGRADED",
        )
    }

    /**
     * Fails unless two sets of oracle lines are identical, in order.
     *
     * The comparison used wherever the expectation is *derived* rather than read whole from one
     * file — the baseline plus one added record, or Phase C's record lines against Phase D's — so
     * it gets the same exactness and the same diff-shaped message as [assertMatchesBaseline].
     *
     * @param summary what failed, in one sentence
     * @param expected the lines that should have been captured
     * @param actual the lines that were
     * @param artifacts the pulled files a reader should open to see both sides
     */
    fun assertSameLines(
        summary: String,
        expected: List<String>,
        actual: List<String>,
        artifacts: String,
    ) {
        if (expected != actual) failWithDiff(summary, expected, actual, artifacts)
    }

    private fun failWithDiff(
        summary: String,
        before: List<String>,
        after: List<String>,
        artifacts: String,
    ): Nothing {
        val lost = before - after.toSet()
        val gained = after - before.toSet()
        error(
            buildString {
                appendLine(summary)
                if (lost.isEmpty() && gained.isEmpty()) {
                    appendLine(
                        "Same lines, different order or count - the records list sorts differently.",
                    )
                }
                lost.forEach { appendLine("- $it") }
                gained.forEach { appendLine("+ $it") }
                append("Both sides are pulled into the run's artifacts as $artifacts.")
            },
        )
    }

    private fun fileNamed(name: String): File =
        File(InstrumentationRegistry.getInstrumentation().context.filesDir, name)
}
