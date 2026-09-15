package com.andryoga.safebox.ui.singleRecord.dynamicLayout.models

/**
 * Selects which composable renders a field's value inside `RowField`.
 *
 * This is deliberately about *rendering* only. Orthogonal cell attributes such as
 * [FieldUiState.Cell.isPasswordField], [FieldUiState.Cell.isCopyable] or
 * [FieldUiState.Cell.isMandatory] stay as separate properties because they combine freely with
 * any render type. Keeping them out of this enum stops it from becoming a dumping ground.
 *
 * Adding an entry here intentionally breaks the compile of the exhaustive `when` in `RowField`,
 * forcing every new type to declare how it renders.
 */
enum class FieldType {
    /** Plain label above value, the behaviour every existing record type relies on. */
    DEFAULT_TEXT,

    /**
     * Live rolling 2FA code. The cell's `data` holds the Base32 seed, not the code, and the code
     * is derived at render time.
     */
    TOTP,
}
