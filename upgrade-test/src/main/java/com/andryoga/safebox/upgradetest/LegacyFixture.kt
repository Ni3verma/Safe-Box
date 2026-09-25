package com.andryoga.safebox.upgradetest

/**
 * What `v1_legacy.bak` must look like once restored into the build under test (plan MR5).
 *
 * The fixture was captured by hand from `v1.4.4.0`, the first release with backup, and is described
 * in `upgrade-test/src/main/assets/fixtures/README.md`. It is the only protection for users who
 * still hold a v1 export: a `.bak` outlives the install that wrote it, and the floor that retired v1
 * installs did not retire v1 files. It carries `BACKUP_VERSION` 1 with the single-byte
 * `creationDate`, and no authenticator key at all.
 *
 * The values are what the current build **displays**, not what the file stores, because that is
 * what [VaultOracle] reads. They were derived from the decoded file and the app's transformations,
 * then confirmed against a real restore:
 * - card numbers, account numbers, customer ids and IFSC codes gain a space after every four
 *   characters (`SpaceAfterEveryFourCharsTransformation`);
 * - v1 stored the expiry as `12/30`. The current build strips the slash when it reads a legacy row
 *   (`BankCardDataDaoSecure.decrypt`) and adds it back for display, so it still reads `12/30`. A
 *   build that stopped stripping would show a doubled or truncated date instead;
 * - v1's form capitalised the card holder's name as it was typed, so the file holds `V1 HOLDER`.
 *
 * An empty value is a field the file holds as `null`, which the detail screen must not render.
 * The records reuse [SeedRecord] because [VaultOracle] reads any record through the same shape,
 * but nothing here is ever typed into a form.
 */
internal object LegacyFixture {

    /** The backup password, which the capture also used as the v1 vault's master password. */
    const val PASSWORD = "Upgrade@@Test123"

    private const val TITLE = SeedRecord.TITLE
    private const val NOTES = "notes"

    val RECORDS = listOf(
        SeedRecord(
            typeResourceName = "type_display_login",
            fields = listOf(
                SeedField(TITLE, "v1 login min"),
                SeedField("url", ""),
                SeedField("user_id", "v1-user-min"),
                SeedField("password", ""),
                SeedField(NOTES, ""),
            ),
        ),
        SeedRecord(
            typeResourceName = "type_display_login",
            fields = listOf(
                SeedField(TITLE, "v1 login full"),
                SeedField("url", "https://v1.example.com"),
                SeedField("user_id", "v1-user"),
                SeedField("password", "V1Login@1"),
                SeedField(NOTES, "line one\nline two ^&*("),
            ),
        ),
        SeedRecord(
            typeResourceName = "type_display_account",
            fields = listOf(
                SeedField(TITLE, "v1 bank min"),
                SeedField("account_number", "1111 2222 3333"),
                SeedField("customer_name", ""),
                SeedField("customer_id", ""),
                SeedField("branch_code", ""),
                SeedField("branch_name", ""),
                SeedField("branch_address", ""),
                SeedField("ifsc_code", ""),
                SeedField("micr_code", ""),
                SeedField(NOTES, ""),
            ),
        ),
        SeedRecord(
            typeResourceName = "type_display_account",
            fields = listOf(
                SeedField(TITLE, "v1 bank full"),
                SeedField("account_number", "4444 5555 6666"),
                SeedField("customer_name", "V1 Customer"),
                SeedField("customer_id", "C-10 01"),
                SeedField("branch_code", "BR01"),
                SeedField("branch_name", "V1 Branch"),
                SeedField("branch_address", "1 Legacy Road"),
                SeedField("ifsc_code", "IFSC 0001 234"),
                SeedField("micr_code", "400002001"),
                SeedField(NOTES, "bank\nnotes %$#"),
            ),
        ),
        SeedRecord(
            typeResourceName = "type_display_card",
            fields = listOf(
                SeedField(TITLE, "v1 card min"),
                SeedField("name", ""),
                SeedField("number", "4111 1111 1111 1111"),
                SeedField("pin", ""),
                SeedField("cvv", ""),
                SeedField("expiryDate", ""),
                SeedField(NOTES, ""),
            ),
        ),
        SeedRecord(
            typeResourceName = "type_display_card",
            fields = listOf(
                SeedField(TITLE, "v1 card full"),
                SeedField("name", "V1 HOLDER"),
                SeedField("number", "5500 0055 5555 5559"),
                SeedField("pin", "4321"),
                SeedField("cvv", "123"),
                SeedField("expiryDate", "12/30"),
                SeedField(NOTES, "card\nnotes @!"),
            ),
        ),
        SeedRecord(
            typeResourceName = "type_display_note",
            fields = listOf(
                SeedField(TITLE, "v1 note"),
                SeedField(NOTES, "legacy note\nsecond line &^%"),
            ),
        ),
    )

    val TITLES: Set<String> = RECORDS.map { it.title }.toSet()
}
