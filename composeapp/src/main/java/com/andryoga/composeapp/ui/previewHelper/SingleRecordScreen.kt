package com.andryoga.composeapp.ui.previewHelper

import androidx.compose.ui.text.input.KeyboardType
import com.andryoga.composeapp.R
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.LayoutId
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.models.FieldId
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.models.FieldUiState
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.models.LayoutPlan

fun getLoginLayoutPlan(): LayoutPlan {
    return LayoutPlan(
        id = LayoutId.LOGIN,
        arrangement = listOf(
            listOf(LayoutPlan.Field(fieldId = FieldId.LOGIN_TITLE)),
            listOf(LayoutPlan.Field(fieldId = FieldId.LOGIN_URL)),
            listOf(LayoutPlan.Field(fieldId = FieldId.LOGIN_USER_ID)),
            listOf(LayoutPlan.Field(fieldId = FieldId.LOGIN_PASSWORD)),
            listOf(LayoutPlan.Field(fieldId = FieldId.LOGIN_NOTES))
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

fun getBankAccountLayoutPlan(): LayoutPlan {
    return LayoutPlan(
        id = LayoutId.BANK_ACCOUNT,
        arrangement = listOf(
            listOf(LayoutPlan.Field(fieldId = FieldId.BANK_ACCOUNT_TITLE)),
            listOf(LayoutPlan.Field(fieldId = FieldId.BANK_ACCOUNT_ACCOUNT_NUMBER)),
            listOf(LayoutPlan.Field(fieldId = FieldId.BANK_ACCOUNT_CUSTOMER_NAME)),
            listOf(LayoutPlan.Field(fieldId = FieldId.BANK_ACCOUNT_CUSTOMER_ID)),
            listOf(
                LayoutPlan.Field(fieldId = FieldId.BANK_ACCOUNT_BRANCH_CODE, weight = 0.5f),
                LayoutPlan.Field(fieldId = FieldId.BANK_ACCOUNT_BRANCH_NAME, weight = 0.5f)
            ),
            listOf(LayoutPlan.Field(fieldId = FieldId.BANK_ACCOUNT_BRANCH_ADDRESS)),
            listOf(
                LayoutPlan.Field(fieldId = FieldId.BANK_ACCOUNT_IFSC_CODE, weight = 0.5f),
                LayoutPlan.Field(fieldId = FieldId.BANK_ACCOUNT_MICR_CODE, weight = 0.5f)
            ),
            listOf(LayoutPlan.Field(fieldId = FieldId.BANK_ACCOUNT_NOTES))
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

fun getCardLayoutPlan(): LayoutPlan {
    return LayoutPlan(
        id = LayoutId.CARD,
        arrangement = listOf(
            listOf(LayoutPlan.Field(fieldId = FieldId.CARD_TITLE)),
            listOf(LayoutPlan.Field(fieldId = FieldId.CARD_NAME)),
            listOf(LayoutPlan.Field(fieldId = FieldId.CARD_NUMBER)),
            listOf(
                LayoutPlan.Field(fieldId = FieldId.CARD_PIN, weight = 0.5f),
                LayoutPlan.Field(fieldId = FieldId.CARD_CVV, weight = 0.5f)
            ),
            listOf(LayoutPlan.Field(fieldId = FieldId.CARD_EXPIRY_DATE)),
            listOf(LayoutPlan.Field(fieldId = FieldId.CARD_NOTES))
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

fun getNoteLayoutPlan(withData: Boolean = false): LayoutPlan {
    return LayoutPlan(
        id = LayoutId.NOTE,
        arrangement = listOf(
            listOf(LayoutPlan.Field(fieldId = FieldId.NOTE_TITLE)),
            listOf(LayoutPlan.Field(fieldId = FieldId.NOTE_NOTES)),
            listOf(LayoutPlan.Field(fieldId = FieldId.CREATION_DATE)),
            listOf(LayoutPlan.Field(fieldId = FieldId.UPDATE_DATE))
        ),
        fieldUiState = mapOf(
            FieldId.NOTE_TITLE to FieldUiState(
                cell = FieldUiState.Cell(
                    label = R.string.title, isMandatory = true
                ),
                data = if (withData) "Android learning" else ""
            ),
            FieldId.NOTE_NOTES to FieldUiState(
                cell = FieldUiState.Cell(
                    label = R.string.notes, singleLine = false, minLines = 5
                ),
                data = if (withData) "Keep on learning compose !!" else ""
            ),
            FieldId.CREATION_DATE to FieldUiState(
                cell = FieldUiState.Cell(
                    label = R.string.created_on, isVisibleOnlyInViewMode = true
                ),
                data = if (withData) "Wednesday, 23 Sept 2025 11:39 AM" else ""
            ),
            FieldId.UPDATE_DATE to FieldUiState(
                cell = FieldUiState.Cell(
                    label = R.string.updated_on, isVisibleOnlyInViewMode = true
                ),
                data = if (withData) "Wednesday, 23 Sept 2025 11:40 AM" else ""
            )
        )
    )
}
