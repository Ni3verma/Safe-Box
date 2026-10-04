package com.andryoga.safebox.upgradetest.store

/**
 * File name stems of every capture the tour writes, before the `-<theme>.png` suffix. Shared by
 * [Scene] and the tour so the two cannot name a capture differently.
 *
 * One capture is not listed because the tour cannot take it: the biometric sheet is a
 * `FLAG_SECURE` window and `screencap` renders it black, so `scripts/take-store-screenshots.sh`
 * grabs it through the emulator console and names it `unlock_biometric-<theme>.png` itself.
 */
internal object Captures {
    const val UNLOCK_PASSWORD = "unlock_password"
    const val RECORDS = "records"
    const val AUTHENTICATORS = "authenticators"
    const val SEARCH = "search"
    const val LOGIN_DETAIL = "login_detail"
    const val ADD_SHEET = "add_sheet"
    const val ADD_FORM = "add_form"
    const val BACKUP = "backup"
    const val SETTINGS = "settings"
}

/**
 * The scenes the tour can photograph, in the order it walks them. A scene is a place in the app;
 * a scene may produce more than one capture (the add scene photographs the type sheet and the
 * form).
 *
 * The host names scenes by [id] in the `scenes` instrumentation argument, and the renderer's
 * `scenes.json` refers to the [captures] by name, so both are stable identifiers: rename one and
 * the other side must follow in the same change.
 *
 * [PALETTE] has no captures of its own: it is the password screen under other Material You seed
 * colours, and only the host can change the device's palette. The host runs [UNLOCK] once per seed
 * with the `variant` argument set to the seed, which names those captures
 * `unlock_password-<theme>-<seed>.png`.
 *
 * @property id the name used on the command line
 * @property captures the file name stems this scene writes, before the `-<theme>.png` suffix
 */
internal enum class Scene(val id: String, val captures: List<String>) {
    UNLOCK("unlock", listOf(Captures.UNLOCK_PASSWORD)),
    RECORDS("records", listOf(Captures.RECORDS)),
    AUTHENTICATORS("authenticators", listOf(Captures.AUTHENTICATORS)),
    SEARCH("search", listOf(Captures.SEARCH)),
    LOGIN_DETAIL("login_detail", listOf(Captures.LOGIN_DETAIL)),
    ADD("add", listOf(Captures.ADD_SHEET, Captures.ADD_FORM)),
    BACKUP("backup", listOf(Captures.BACKUP)),
    SETTINGS("settings", listOf(Captures.SETTINGS)),
    PALETTE("palette", emptyList()),
    ;

    /** True for scenes that live behind the master password, i.e. need the vault opened. */
    val behindUnlock: Boolean get() = this != UNLOCK && this != PALETTE

    companion object {
        const val ALL = "all"

        /**
         * Parses the host's `scenes` argument.
         *
         * @param argument `all`, or a comma-separated list of [id]s
         * @return the scenes to capture, in walk order regardless of how they were listed
         * @throws IllegalStateException naming the unknown id and the valid ones
         */
        fun parse(argument: String): Set<Scene> {
            if (argument.trim() == ALL) return entries.toSet()
            val byId = entries.associateBy { it.id }
            return argument.split(',').map { it.trim() }.filter { it.isNotEmpty() }.map { id ->
                byId[id] ?: error(
                    "unknown scene '$id'; valid scenes are $ALL or any of ${byId.keys}",
                )
            }.toSortedSet()
        }
    }
}
