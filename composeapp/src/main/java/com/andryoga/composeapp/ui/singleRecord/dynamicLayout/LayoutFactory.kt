package com.andryoga.composeapp.ui.singleRecord.dynamicLayout

import androidx.compose.ui.text.input.KeyboardType
import com.andryoga.composeapp.R
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.models.FieldId
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.models.FieldUiState
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.models.Layout

object LayoutFactory {
    fun getLoginRecordLayout(): Layout {
        return Layout(
            id = LayoutId.LOGIN,
            arrangement = listOf(
                listOf(Layout.Field(fieldId = FieldId.LOGIN_TITLE)),
                listOf(Layout.Field(fieldId = FieldId.LOGIN_URL)),
                listOf(Layout.Field(fieldId = FieldId.LOGIN_USER_ID)),
                listOf(Layout.Field(fieldId = FieldId.LOGIN_PASSWORD)),
                listOf(Layout.Field(fieldId = FieldId.LOGIN_NOTES))
            ),
            fieldUiState = mapOf(
                FieldId.LOGIN_TITLE to FieldUiState(
                    cell = FieldUiState.Cell(
                        label = R.string.title, isMandatory = true
                    )
                ),
                FieldId.LOGIN_URL to FieldUiState(cell = FieldUiState.Cell(label = R.string.url)),
                FieldId.LOGIN_USER_ID to FieldUiState(
                    cell = FieldUiState.Cell(
                        label = R.string.user_id, isMandatory = true
                    )
                ),
                FieldId.LOGIN_PASSWORD to FieldUiState(
                    cell = FieldUiState.Cell(
                        label = R.string.password, isPasswordField = true
                    )
                ),
                FieldId.LOGIN_NOTES to FieldUiState(
                    cell = FieldUiState.Cell(
                        label = R.string.notes, singleLine = false, minLines = 5
                    )
                )
            )
        )
    }

    fun getBankAccountRecordLayout(): Layout {
        return Layout(
            id = LayoutId.BANK_ACCOUNT,
            arrangement = listOf(
                listOf(Layout.Field(fieldId = FieldId.BANK_ACCOUNT_TITLE)),
                listOf(Layout.Field(fieldId = FieldId.BANK_ACCOUNT_ACCOUNT_NUMBER)),
                listOf(Layout.Field(fieldId = FieldId.BANK_ACCOUNT_CUSTOMER_NAME)),
                listOf(Layout.Field(fieldId = FieldId.BANK_ACCOUNT_CUSTOMER_ID)),
                listOf(
                    Layout.Field(fieldId = FieldId.BANK_ACCOUNT_BRANCH_CODE, weight = 0.5f),
                    Layout.Field(fieldId = FieldId.BANK_ACCOUNT_BRANCH_NAME, weight = 0.5f)
                ),
                listOf(Layout.Field(fieldId = FieldId.BANK_ACCOUNT_BRANCH_ADDRESS)),
                listOf(
                    Layout.Field(fieldId = FieldId.BANK_ACCOUNT_IFSC_CODE, weight = 0.5f),
                    Layout.Field(fieldId = FieldId.BANK_ACCOUNT_MICR_CODE, weight = 0.5f)
                ),
                listOf(Layout.Field(fieldId = FieldId.BANK_ACCOUNT_NOTES))
            ),
            fieldUiState = mapOf(
                FieldId.BANK_ACCOUNT_TITLE to FieldUiState(
                    cell = FieldUiState.Cell(
                        label = R.string.title, isMandatory = true
                    )
                ),
                FieldId.BANK_ACCOUNT_ACCOUNT_NUMBER to FieldUiState(
                    cell = FieldUiState.Cell(
                        label = R.string.account_number,
                        isMandatory = true,
                        keyboardType = KeyboardType.Number
                    )
                ),
                FieldId.BANK_ACCOUNT_CUSTOMER_NAME to FieldUiState(
                    cell = FieldUiState.Cell(label = R.string.customer_name)
                ),
                FieldId.BANK_ACCOUNT_CUSTOMER_ID to FieldUiState(
                    cell = FieldUiState.Cell(label = R.string.customer_id)
                ),
                FieldId.BANK_ACCOUNT_BRANCH_CODE to FieldUiState(
                    cell = FieldUiState.Cell(label = R.string.branch_code)
                ),
                FieldId.BANK_ACCOUNT_BRANCH_NAME to FieldUiState(
                    cell = FieldUiState.Cell(label = R.string.branch_name)
                ),
                FieldId.BANK_ACCOUNT_BRANCH_ADDRESS to FieldUiState(
                    cell = FieldUiState.Cell(label = R.string.branch_address)
                ),
                FieldId.BANK_ACCOUNT_IFSC_CODE to FieldUiState(
                    cell = FieldUiState.Cell(label = R.string.ifsc_code)
                ),
                FieldId.BANK_ACCOUNT_MICR_CODE to FieldUiState(
                    cell = FieldUiState.Cell(label = R.string.micr_code)
                ),
                FieldId.BANK_ACCOUNT_NOTES to FieldUiState(
                    cell = FieldUiState.Cell(
                        label = R.string.notes, singleLine = false, minLines = 5
                    )
                )
            )
        )
    }

    fun getCardRecordLayout(): Layout {
        return Layout(
            id = LayoutId.CARD,
            arrangement = listOf(
                listOf(Layout.Field(fieldId = FieldId.CARD_TITLE)),
                listOf(Layout.Field(fieldId = FieldId.CARD_NAME)),
                listOf(Layout.Field(fieldId = FieldId.CARD_NUMBER)),
                listOf(
                    Layout.Field(fieldId = FieldId.CARD_PIN, weight = 0.5f),
                    Layout.Field(fieldId = FieldId.CARD_CVV, weight = 0.5f)
                ),
                listOf(Layout.Field(fieldId = FieldId.CARD_EXPIRY_DATE)),
                listOf(Layout.Field(fieldId = FieldId.CARD_NOTES))
            ),
            fieldUiState = mapOf(
                FieldId.CARD_TITLE to FieldUiState(
                    cell = FieldUiState.Cell(
                        label = R.string.title, isMandatory = true
                    )
                ),
                FieldId.CARD_NAME to FieldUiState(cell = FieldUiState.Cell(label = R.string.name)),
                FieldId.CARD_NUMBER to FieldUiState(
                    cell = FieldUiState.Cell(
                        label = R.string.number,
                        isMandatory = true,
                        keyboardType = KeyboardType.Number,
                    )
                ),
                FieldId.CARD_PIN to FieldUiState(
                    cell = FieldUiState.Cell(
                        label = R.string.pin,
                        keyboardType = KeyboardType.Number,
                    )
                ),
                FieldId.CARD_CVV to FieldUiState(
                    cell = FieldUiState.Cell(
                        label = R.string.cvv,
                        keyboardType = KeyboardType.Number,
                    )
                ),
                FieldId.CARD_EXPIRY_DATE to FieldUiState(
                    cell = FieldUiState.Cell(
                        label = R.string.expiryDate, keyboardType = KeyboardType.Number
                    )
                ),
                FieldId.CARD_NOTES to FieldUiState(
                    cell = FieldUiState.Cell(
                        label = R.string.notes, singleLine = false, minLines = 5
                    )
                )
            )
        )
    }

    fun getNoteRecordLayout(): Layout {
        return Layout(
            id = LayoutId.NOTE,
            arrangement = listOf(
                listOf(Layout.Field(fieldId = FieldId.NOTE_TITLE)),
                listOf(
                    Layout.Field(fieldId = FieldId.NOTE_NOTES)
                )
            ),
            fieldUiState = mapOf(
                FieldId.NOTE_TITLE to FieldUiState(
                    cell = FieldUiState.Cell(
                        label = R.string.title, isMandatory = true
                    )
                ),
                FieldId.NOTE_NOTES to FieldUiState(
                    cell = FieldUiState.Cell(
                        label = R.string.notes, singleLine = false, minLines = 5
                    )
                )
            )
        )
    }
}
