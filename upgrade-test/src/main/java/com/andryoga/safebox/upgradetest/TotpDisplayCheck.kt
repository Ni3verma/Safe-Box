package com.andryoga.safebox.upgradetest

/**
 * Group 5: the one-time code an authenticator row displays is the RFC 6238 code for its seed.
 *
 * This is the only check that proves the **seed itself** decrypted correctly, rather than that a
 * row exists: a seed that came back as different bytes still renders a perfectly plausible six
 * digits, and only an independent computation over the *known* seed can tell them apart. The
 * expected value is computed by [Rfc6238], never by app code.
 *
 * Time is not controlled (decided 2026-09-25: no root, no clock change). The device clock is read
 * immediately before and after the screen is read, and the displayed code is accepted if it
 * belongs to any 30-second window in that span. The span is widened by [DISPLAY_LAG_MS] at the
 * start because the app's ticker only recomputes on its next whole second, so for up to a second
 * after a rollover the screen legitimately still shows the previous window's code. A wrong seed
 * matching one of the two or three accepted codes by chance is a one-in-several-hundred-thousand
 * event.
 *
 * The harness runs on the device, so [System.currentTimeMillis] *is* the device clock the app
 * reads; no shell round trip is needed.
 *
 * @param ui shared waiting and failure-description plumbing
 * @param app the installed build's own labels, resolved by resource name
 */
internal class TotpDisplayCheck(private val ui: UiSupport, private val app: AppStrings) {

    /**
     * Finds [record]'s row on the records list and asserts the code it shows.
     *
     * The code is paired with its row by geometry, like everything else on this list: it is the
     * code-shaped text nearest below the title, within one title-height. Anything further away
     * belongs to another row.
     *
     * @param record an authenticator seeded with a fixed Base32 secret
     */
    fun assertListShowsCodeFor(record: SeedRecord) {
        Rfc6238.checkKnownAnswers()
        val key = Base32.decode(record.valueOf(SeedRecord.SECRET_KEY))

        ui.clickText(app.label(RECORDS_TAB))
        ui.scrollToTop()
        checkNotNull(ui.scrollToText(record.title)) {
            "'${record.title}' is not in the records list${ui.describeScreen()}"
        }

        val before = System.currentTimeMillis()
        val screen = ui.textSnapshot()
        val after = System.currentTimeMillis()

        check(screen.none { it.text == app.label(INVALID_SECRET_MESSAGE) }) {
            "'${record.title}' shows the invalid-secret message instead of a code, so its seed " +
                "did not come back as valid Base32${ui.describeScreen()}"
        }
        val title = screen.firstOrNull { it.text == record.title }
            ?: error("'${record.title}' scrolled away before it could be read${ui.describeScreen()}")
        val shown = screen
            .filter { CODE_PATTERN.matches(it.text) }
            .filter {
                it.bounds.top >= title.bounds.top &&
                    it.bounds.top <= title.bounds.bottom + title.bounds.height()
            }
            .minByOrNull { it.bounds.top }
            ?.text
            ?.filterNot { it.isWhitespace() }
            ?: error("no one-time code is shown under '${record.title}'${ui.describeScreen()}")

        val firstStep = (before - DISPLAY_LAG_MS) / MILLIS_PER_SECOND / PERIOD_SECONDS
        val lastStep = after / MILLIS_PER_SECOND / PERIOD_SECONDS
        val expected = (firstStep..lastStep).map { Rfc6238.code(key, it, DIGITS) }
        check(shown in expected) {
            "'${record.title}' shows code $shown, but its seed gives $expected for the time steps " +
                "$firstStep..$lastStep (device clock ${before}ms to ${after}ms). The seed did not " +
                "survive intact${ui.describeScreen()}"
        }
    }

    companion object {
        /** The app's defaults for a hand-entered key, which are also RFC 6238's. */
        const val PERIOD_SECONDS = 30L
        private const val DIGITS = 6

        private const val MILLIS_PER_SECOND = 1_000L
        private const val DISPLAY_LAG_MS = 2_000L

        // formattedCode splits the digits into two halves with a space, e.g. "123 456". The
        // countdown ring beside it is digits without a space, so it can never match.
        private val CODE_PATTERN = Regex("""\d{3} \d{3}""")

        // Resource names, resolved against the installed build. See ADR-0003.
        private const val RECORDS_TAB = "bottom_nav_records"
        private const val INVALID_SECRET_MESSAGE = "totp_invalid_secret_key"
    }
}
