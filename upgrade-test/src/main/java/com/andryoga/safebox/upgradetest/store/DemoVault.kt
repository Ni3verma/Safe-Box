package com.andryoga.safebox.upgradetest.store

/**
 * The records the store screenshots show. Content lives here and nowhere else: the tour creates
 * every record through the app's own add flow, so what the screenshots show is exactly what the
 * shipped UI renders for this data.
 *
 * The persona is a fictional "Aarav Mehta"; every value is invented or a published test number
 * (the card is Stripe's `4242…` test PAN, the IFSC/MICR codes follow the format but belong to no
 * branch). Titles are unique across every type so that a search or a tap by title is unambiguous,
 * which is also why the logins and the authenticators name different services.
 *
 * Field values are keyed by the label's **string resource name**, resolved against the installed
 * build like every other label in the harness (ADR-0003). Optional fields that should stay empty
 * are simply absent.
 */
internal object DemoVault {

    /**
     * The record types, in the order the add sheet lists them, each with the resource of the
     * sheet item's text (which is also the type chip's and the row badge's text).
     *
     * @property sheetItemResource string resource of the type's display name
     */
    enum class RecordType(val sheetItemResource: String) {
        LOGIN("type_display_login"),
        CARD("type_display_card"),
        BANK_ACCOUNT("type_display_account"),
        NOTE("type_display_note"),
        AUTHENTICATOR("type_display_authenticator"),
    }

    /**
     * One value to type into a form field.
     *
     * @property labelResource string resource of the field's label
     * @property value the text to type
     */
    data class Field(val labelResource: String, val value: String)

    /**
     * One record to create, as a type and the fields to fill, in form order.
     *
     * @property type which add-sheet item to choose
     * @property fields the values to type; the first one is always the title
     */
    data class Record(val type: RecordType, val fields: List<Field>) {
        /** The record's title, which is what the list shows and what search matches. */
        val title: String get() = fields.first { it.labelResource == TITLE }.value
    }

    const val TITLE = "title"

    /** The login whose detail screen the tour photographs; must be a [RecordType.LOGIN] title. */
    const val OPENED_LOGIN = "Netflix"

    /** Typed into search for the search scene; matches [SEARCH_HITS] and nothing else. */
    const val SEARCH_QUERY = "ma"
    val SEARCH_HITS = listOf("Amazon", "Gmail")

    /** A title that [SEARCH_QUERY] does not match, used to see that the filter took effect. */
    const val SEARCH_MISS = "AWS"

    /**
     * The login typed into the add form for the add-form scene but never saved, so the list keeps
     * exactly [records].
     */
    val UNSAVED_LOGIN = Record(
        RecordType.LOGIN,
        listOf(
            Field(TITLE, "Spotify"),
            Field("url", "spotify.com"),
            Field("user_id", "aarav.mehta@gmail.com"),
            Field("password", "Pl4ylist&Repeat"),
        ),
    )

    val records: List<Record> = listOf(
        Record(
            RecordType.LOGIN,
            listOf(
                Field(TITLE, "Netflix"),
                Field("url", "netflix.com"),
                Field("user_id", "aarav.mehta@gmail.com"),
                Field("password", "Str3am!ng-2026"),
                Field("notes", "Family plan, renews on the 5th"),
            ),
        ),
        Record(
            RecordType.LOGIN,
            listOf(
                Field(TITLE, "Amazon"),
                Field("url", "amazon.in"),
                Field("user_id", "aarav.mehta@gmail.com"),
                Field("password", "Pr1me&Delivery7"),
            ),
        ),
        Record(
            RecordType.LOGIN,
            listOf(
                Field(TITLE, "Gmail"),
                Field("url", "mail.google.com"),
                Field("user_id", "aarav.mehta@gmail.com"),
                Field("password", "M4il-Cl0ud!9"),
            ),
        ),
        Record(
            RecordType.CARD,
            listOf(
                Field(TITLE, "HDFC Visa"),
                Field("name", "Aarav Mehta"),
                Field("number", "4242424242424242"),
                Field("pin", "4821"),
                Field("cvv", "317"),
                Field("expiryDate", "1228"),
                Field("notes", "Billing cycle ends on the 18th"),
            ),
        ),
        Record(
            RecordType.BANK_ACCOUNT,
            listOf(
                Field(TITLE, "SBI Savings"),
                Field("account_number", "301245879921"),
                Field("customer_name", "Aarav Mehta"),
                Field("customer_id", "88214507"),
                Field("branch_code", "04311"),
                Field("branch_name", "MG Road"),
                Field("branch_address", "12 MG Road, Bengaluru 560001"),
                Field("ifsc_code", "SBIN0004311"),
                Field("micr_code", "560002045"),
            ),
        ),
        Record(
            RecordType.NOTE,
            listOf(
                Field(TITLE, "Home Wi-Fi"),
                Field(
                    "notes",
                    "Network: Mehta_5G\nPassword: wifi-R0cks-2026\nRouter admin: 192.168.1.1",
                ),
            ),
        ),
        Record(
            RecordType.NOTE,
            listOf(
                Field(TITLE, "Passport"),
                Field(
                    "notes",
                    "Number: P1234567\nIssued: 14 Mar 2021, Bengaluru\nExpires: 13 Mar 2031",
                ),
            ),
        ),
        // Valid Base32 seeds, so the list renders live codes and countdown rings rather than the
        // invalid-seed message.
        Record(
            RecordType.AUTHENTICATOR,
            listOf(Field(TITLE, "GitHub"), Field("secret_key", "JBSWY3DPEHPK3PXP")),
        ),
        Record(
            RecordType.AUTHENTICATOR,
            listOf(Field(TITLE, "Google"), Field("secret_key", "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ")),
        ),
        Record(
            RecordType.AUTHENTICATOR,
            listOf(Field(TITLE, "AWS"), Field("secret_key", "KRSXG5CTMVRXEZLUKRSXG5CTMVRXEZLU")),
        ),
    )

    /**
     * The title the list shows first — the app sorts rows by lower-cased title — so waiting for
     * it is how "the list has loaded" is read.
     */
    val firstTitle: String get() = records.minBy { it.title.lowercase() }.title
}
