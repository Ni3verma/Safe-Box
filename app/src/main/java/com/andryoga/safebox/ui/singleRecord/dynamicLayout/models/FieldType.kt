package com.andryoga.safebox.ui.singleRecord.dynamicLayout.models

import com.andryoga.safebox.totp.models.TotpConfig

/**
 * Selects which composable renders a field's value inside `RowField`.
 *
 * This is deliberately about *rendering* only. Orthogonal cell attributes such as
 * [FieldUiState.Cell.isPasswordField], [FieldUiState.Cell.isCopyable] or
 * [FieldUiState.Cell.isMandatory] stay as separate properties because they combine freely with
 * any render type.
 *
 * It is a sealed interface rather than an enum so a render type can carry the extra input it needs.
 * Adding a type here intentionally breaks the compile of the exhaustive `when` in `RowField`.
 */
sealed interface FieldType {
    /** Plain label above value, the behavior every existing record type relies on. */
    data object DefaultText : FieldType

    /**
     * Live rolling 2FA code. The cell's `data` holds the Base32 seed, not the code, and the code
     * is derived at render time from [config].
     *
     * @property config Seed plus the record's own generation parameters.
     */
    data class Totp(val config: TotpConfig) : FieldType
}
