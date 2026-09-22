package com.andryoga.safebox.upgradetest

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

/**
 * Minimal end-to-end proof that the harness can drive the shipped QA APK across an in-place
 * upgrade. This is the MR1 skeleton: it establishes just enough state on the baseline build to make
 * the post-upgrade screen meaningful, and asserts nothing about the vault's contents. Data
 * integrity assertions arrive in MR3.
 *
 * The two tests are **ordered and stateful across processes**, which is unusual and deliberate:
 * [signUpOnBaselineBuild] runs against the baseline APK and [unlockScreenAppearsAfterUpgrade] runs
 * against the build under test, after the host has replaced the app underneath. They are therefore
 * never run in the same invocation — scripts/run-upgrade-test.sh selects one at a time by method
 * filter, which is also why a filter typo has to be fatal rather than a silent zero-test pass.
 *
 * Everything is selected by visible text, because production code contains no `Modifier.testTag`
 * and the APK under test is minified.
 */
@RunWith(AndroidJUnit4::class)
class UpgradeSmokeTest {

    private lateinit var device: UiDevice

    @Before
    fun setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    /**
     * Phase A. Creates an account on the baseline build.
     *
     * Signing up is the cheapest action that makes the upgrade worth testing at all: it writes the
     * password hash to `EncryptedSharedPreferences` and generates the `symmetricDataKey`
     * AndroidKeyStore alias. Without it the upgraded app would land on signup and the phase B
     * assertion would be vacuous.
     */
    @Test
    fun signUpOnBaselineBuild() {
        launchAppUnderTest(SIGNUP_HEADING)

        textField(SIGNUP_PASSWORD_LABEL).text = MASTER_PASSWORD
        textField(SIGNUP_HINT_LABEL).text = PASSWORD_HINT
        awaitObject(By.text(SIGNUP_BUTTON).enabled(true)).click()

        // Leaving the signup heading behind is the only "signed up" signal available without
        // asserting on home-screen content, which belongs to MR2.
        check(device.wait(Until.gone(By.text(SIGNUP_HEADING)), SIGN_UP_TIMEOUT_MS)) {
            "still on the signup screen after tapping '$SIGNUP_BUTTON'${describeScreen()}"
        }
    }

    /**
     * Phase B. Asserts the upgraded build still knows an account exists.
     *
     * Landing on unlock rather than signup is the cheapest possible proof that `/data` survived the
     * in-place install. If preferences were wiped, this is where it shows.
     */
    @Test
    fun unlockScreenAppearsAfterUpgrade() {
        launchAppUnderTest(UNLOCK_HEADING)

        // Waits rather than querying once: the heading and the field are separate semantics nodes,
        // and a slow device can publish them in different frames. A one-shot `findObject` here
        // would report "no password field" for a screen that was merely a frame behind.
        assertNotNull(
            "unlock screen has no '$UNLOCK_PASSWORD_LABEL' field${describeScreen()}",
            device.wait(Until.findObject(By.text(UNLOCK_PASSWORD_LABEL)), FIND_TIMEOUT_MS),
        )
    }

    /**
     * Starts the app under test and waits for the screen the caller expects, re-launching it if
     * the screen does not appear.
     *
     * Waiting on the *expected screen* rather than on "the app owns the foreground window" is
     * deliberate. On any device with an enrolled fingerprint the unlock screen immediately raises a
     * system biometric sheet, which is drawn by SystemUI — the app stops being the foreground
     * package, and a `By.pkg(APP_PACKAGE).depth(0)` wait can never succeed even though the app is
     * running perfectly. Observed on a Pixel 8 API 35 emulator on 2026-09-22; CI's `aosp-atd` image
     * has nothing enrolled, so this would have been a flake that only ever reproduced locally.
     *
     * Backing out of the prompt is exactly what a user who wants to type their password does, and
     * the app handles it through `onErrorOrCancel`. The back press only happens after a wait has
     * fully timed out, so it cannot cut short a merely slow launch. It is not reliable on its own
     * though: on a gesture-navigation device the same press was seen to dismiss the sheet *and*
     * send the task to the launcher (`RecentsController.finishInner: toHome=true`), after which
     * the test was asserting against the home screen. Hence every attempt starts the launch intent
     * again — bringing a backgrounded task forward is harmless, and the prompt is not re-armed
     * because the app only offers biometric unlock once per process.
     *
     * @param expectedHeading text that identifies the screen the app should land on
     * @return the heading node, so callers can assert further against it
     */
    private fun launchAppUnderTest(expectedHeading: String): UiObject2 {
        val context = InstrumentationRegistry.getInstrumentation().context
        val intent = context.packageManager.getLaunchIntentForPackage(APP_PACKAGE)
            ?: error(
                "no launch intent for $APP_PACKAGE - it is not installed, or the <queries> entry " +
                    "in this module's manifest no longer matches its applicationId",
            )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        repeat(LAUNCH_ATTEMPTS) { attempt ->
            context.startActivity(intent)
            val timeout = if (attempt == 0) LAUNCH_TIMEOUT_MS else FIND_TIMEOUT_MS
            device.wait(Until.findObject(By.text(expectedHeading)), timeout)
                ?.let { return it }
            device.pressBack()
        }
        error(
            "'$expectedHeading' never appeared in $LAUNCH_ATTEMPTS attempts at launching " +
                "$APP_PACKAGE, even after dismissing a possible system prompt${describeScreen()}",
        )
    }

    private fun awaitText(text: String): UiObject2 = awaitObject(By.text(text))

    private fun awaitObject(selector: BySelector): UiObject2 =
        device.wait(Until.findObject(selector), FIND_TIMEOUT_MS)
            ?: error("could not find $selector within ${FIND_TIMEOUT_MS}ms${describeScreen()}")

    /**
     * Resolves the editable field belonging to a Compose `OutlinedTextField`.
     *
     * The label and the editable node are separate leaves, so the label is located first and its
     * container searched for the `EditText` that Compose exposes to the accessibility layer.
     */
    private fun textField(label: String): UiObject2 {
        val labelNode = awaitText(label)
        return labelNode.parent?.findObject(By.clazz("android.widget.EditText"))
            ?: error("no editable field beside the '$label' label${describeScreen()}")
    }

    /**
     * A CI failure on an emulator nobody can attach to is only actionable if it carries the screen
     * with it, so every failure message in this class ends with the window hierarchy.
     */
    private fun describeScreen(): String = buildString {
        append("\ncurrent window hierarchy:\n")
        append(runCatching { device.windowHierarchy() }.getOrElse { "  <unavailable: $it>" })
    }

    private fun UiDevice.windowHierarchy(): String {
        val sink = ByteArrayOutputStream()
        dumpWindowHierarchy(sink)
        return sink.toString(Charsets.UTF_8.name())
    }

    private companion object {
        const val APP_PACKAGE = "com.andryoga.safebox.qa"

        // Credentials are fixed by docs/testing/upgrade-testing.md section 11, decision 2. The
        // password must satisfy PasswordValidator: mixed case, two digits, a symbol, length >= 7.
        const val MASTER_PASSWORD = "Upgrade@Test12"
        const val PASSWORD_HINT = "upgrade fixture"

        const val SIGNUP_HEADING = "Welcome !"
        const val UNLOCK_HEADING = "Welcome Back !"
        const val SIGNUP_BUTTON = "Sign Up"

        // Mandatory signup fields are labelled by MandatoryLabelText, which appends a red asterisk
        // inside the same text node, so the accessibility text is "Password*" and not "Password".
        // The unlock screen uses a plain Text for its label, hence two different constants - and
        // usefully, they also tell the two screens apart.
        const val SIGNUP_PASSWORD_LABEL = "Password*"
        const val SIGNUP_HINT_LABEL = "Hint*"
        const val UNLOCK_PASSWORD_LABEL = "Password"

        const val LAUNCH_TIMEOUT_MS = 30_000L
        const val FIND_TIMEOUT_MS = 15_000L
        const val SIGN_UP_TIMEOUT_MS = 15_000L

        // Two extra tries are enough for the one recoverable cause seen so far (a system prompt
        // stealing the window, plus the back press that dismisses it landing on the launcher).
        // Anything still failing after that is a real defect and should fail fast.
        const val LAUNCH_ATTEMPTS = 3
    }
}
