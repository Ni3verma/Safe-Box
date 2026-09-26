package com.andryoga.safebox.upgradetest

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources

/**
 * Resolves the app's visible labels from its own string resources, by resource name.
 *
 * The harness holds `user_id`, never `"User Id"`, and asks the *installed APK* what that resource
 * renders as. This is what lets the same step run against two different builds: the N-1 phase
 * resolves against the previous release, the later phases against the build under test, and a
 * product-side copy edit between the two is invisible to the test. The reasoning, and what it
 * deliberately costs, is [ADR-0003](../../../../../../../../docs/decisions/0003-ui-labels-from-resource-names.md).
 *
 * Resolution goes through [PackageManager.getResourcesForApplication], so there is no compile
 * dependency on `:app` and ADR-0002's black-box rule still holds: this reads the shipped artifact,
 * not the project. The harness manifest's `<queries>` entry is what makes the target package
 * visible enough to ask.
 *
 * One instance must not outlive an install. The [Resources] handle is bound to the APK that was
 * installed when it was created, so a single instance spanning the upgrade would keep answering
 * with the old build's strings. Each phase runs in its own instrumentation process and builds its
 * own, which is why that is safe by construction rather than by discipline.
 *
 * @param context any context belonging to the *harness*, used only to reach the package manager
 * @param packageName the app under test
 */
internal class AppStrings(context: Context, val packageName: String) {

    private val appResources: Resources = runCatching {
        context.packageManager.getResourcesForApplication(packageName)
    }.getOrElse { failure ->
        error(
            "cannot read the resources of '$packageName'. Either it is not installed, or the " +
                "harness manifest no longer declares it under <queries> - without that, package " +
                "visibility hides it and this fails on API 30+ only: $failure",
        )
    }

    private val cache = mutableMapOf<String, String>()

    /**
     * The text the installed build renders for a string resource.
     *
     * Trimmed, because a resource and its rendering are not the same string: `user_id` is
     * `"User Id "` with a trailing space in every release, while the accessibility tree exposes
     * `User Id`. Matching the raw resource would find nothing.
     *
     * @param name the resource's name, as it appears in `strings.xml`
     * @return the rendered text, trimmed
     * @throws IllegalStateException if the installed build has no such string resource, which
     * means either a rename that needs handling in the same change or a removed element
     */
    fun label(name: String): String = cache.getOrPut(name) {
        val id = appResources.getIdentifier(name, STRING_TYPE, packageName)
        check(id != 0) {
            "'$packageName' has no string resource named '$name'. Resource names are the harness's " +
                "key precisely so copy edits do not break it, so this means the name itself " +
                "changed - handle the rename in the same change - or the element was removed, " +
                "which is a real regression"
        }
        appResources.getString(id).trim()
    }

    /**
     * The text an editable *form* renders for a field's label.
     *
     * Forms are the one place where the label on screen is not just the resource: mandatory
     * fields go through `MandatoryLabelText`, which appends a red `*` **inside the same text
     * node**, so the accessibility tree exposes `Password*` rather than `Password`. The marker is
     * appended in Compose rather than stored in any resource, which is why the harness has to
     * reproduce it instead of resolving it. The only form the harness fills is sign-up.
     *
     * @param name the resource's name, as it appears in `strings.xml`
     * @param isMandatory whether the form marks this field mandatory
     * @return the text the form's label node carries
     */
    fun formLabel(name: String, isMandatory: Boolean): String =
        label(name) + if (isMandatory) MANDATORY_MARKER else ""

    private companion object {
        const val STRING_TYPE = "string"

        // Appended by ui/core/Text.kt's MandatoryLabelText, not by any string resource.
        const val MANDATORY_MARKER = "*"
    }
}
