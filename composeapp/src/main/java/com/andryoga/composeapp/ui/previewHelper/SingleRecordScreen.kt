package com.andryoga.composeapp.ui.previewHelper

import androidx.compose.ui.text.input.KeyboardType
import com.andryoga.composeapp.R
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.LayoutId
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.models.FieldId
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.models.FieldUiState
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.models.LayoutPlan

fun getLoginLayoutPlan(withData: Boolean = false): LayoutPlan {
    return LayoutPlan(
        id = LayoutId.LOGIN,
        arrangement = listOf(
            listOf(LayoutPlan.Field(fieldId = FieldId.LOGIN_TITLE)),
            listOf(LayoutPlan.Field(fieldId = FieldId.LOGIN_URL)),
            listOf(LayoutPlan.Field(fieldId = FieldId.LOGIN_USER_ID)),
            listOf(LayoutPlan.Field(fieldId = FieldId.LOGIN_PASSWORD)),
            listOf(LayoutPlan.Field(fieldId = FieldId.LOGIN_NOTES)),
            listOf(LayoutPlan.Field(fieldId = FieldId.CREATION_DATE)),
            listOf(LayoutPlan.Field(fieldId = FieldId.UPDATE_DATE))
        ),
        fieldUiState = mapOf(
            FieldId.LOGIN_TITLE to FieldUiState(
                cell = FieldUiState.Cell(
                    label = R.string.title, isMandatory = true
                ),
                data = if (withData) "Google Account" else ""
            ),
            FieldId.LOGIN_URL to FieldUiState(
                cell = FieldUiState.Cell(label = R.string.url),
                data = if (withData) "www.google.com" else ""
            ),
            FieldId.LOGIN_USER_ID to FieldUiState(
                cell = FieldUiState.Cell(
                    label = R.string.user_id, isMandatory = true
                ), data = if (withData) "canvas.nv@gmail.com" else ""
            ),
            FieldId.LOGIN_PASSWORD to FieldUiState(
                cell = FieldUiState.Cell(
                    label = R.string.password, isPasswordField = true
                ), data = if (withData) "woahhh" else ""
            ),
            FieldId.LOGIN_NOTES to FieldUiState(
                cell = FieldUiState.Cell(
                    label = R.string.notes, singleLine = false, minLines = 5
                ), data = if (withData) "this is my primary google account" else ""
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

fun getBankAccountLayoutPlan(withData: Boolean = false): LayoutPlan {
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
            listOf(LayoutPlan.Field(fieldId = FieldId.BANK_ACCOUNT_NOTES)),
            listOf(LayoutPlan.Field(fieldId = FieldId.CREATION_DATE)),
            listOf(LayoutPlan.Field(fieldId = FieldId.UPDATE_DATE))
        ),
        fieldUiState = mapOf(
            FieldId.BANK_ACCOUNT_TITLE to FieldUiState(
                cell = FieldUiState.Cell(
                    label = R.string.title, isMandatory = true
                ), data = if (withData) "HDFC salary" else ""
            ),
            FieldId.BANK_ACCOUNT_ACCOUNT_NUMBER to FieldUiState(
                cell = FieldUiState.Cell(
                    label = R.string.account_number,
                    isMandatory = true,
                    keyboardType = KeyboardType.Number
                ), data = if (withData) "2384798243021" else ""
            ),
            FieldId.BANK_ACCOUNT_CUSTOMER_NAME to FieldUiState(
                cell = FieldUiState.Cell(label = R.string.customer_name),
                data = if (withData) "NITIN" else ""
            ),
            FieldId.BANK_ACCOUNT_CUSTOMER_ID to FieldUiState(
                cell = FieldUiState.Cell(label = R.string.customer_id),
                data = if (withData) "4623784" else ""
            ),
            FieldId.BANK_ACCOUNT_BRANCH_CODE to FieldUiState(
                cell = FieldUiState.Cell(label = R.string.branch_code),
                data = if (withData) "8724" else ""
            ),
            FieldId.BANK_ACCOUNT_BRANCH_NAME to FieldUiState(
                cell = FieldUiState.Cell(label = R.string.branch_name),
                data = if (withData) "ROPAR REDCORSS 3" else ""
            ),
            FieldId.BANK_ACCOUNT_BRANCH_ADDRESS to FieldUiState(
                cell = FieldUiState.Cell(label = R.string.branch_address),
                data = if (withData) "#fj, jsndjka, damnd" else ""
            ),
            FieldId.BANK_ACCOUNT_IFSC_CODE to FieldUiState(
                cell = FieldUiState.Cell(label = R.string.ifsc_code),
                data = if (withData) "BJH432874y" else ""
            ),
            FieldId.BANK_ACCOUNT_MICR_CODE to FieldUiState(
                cell = FieldUiState.Cell(label = R.string.micr_code),
                data = if (withData) "1231" else ""
            ),
            FieldId.BANK_ACCOUNT_NOTES to FieldUiState(
                cell = FieldUiState.Cell(
                    label = R.string.notes, singleLine = false, minLines = 5
                ),
                data = if (withData) "This is my main HDFC salary account that I made in my first job" else ""
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

fun getCardLayoutPlan(withData: Boolean = false): LayoutPlan {
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
            listOf(LayoutPlan.Field(fieldId = FieldId.CARD_NOTES)),
            listOf(LayoutPlan.Field(fieldId = FieldId.CREATION_DATE)),
            listOf(LayoutPlan.Field(fieldId = FieldId.UPDATE_DATE)),
        ),
        fieldUiState = mapOf(
            FieldId.CARD_TITLE to FieldUiState(
                cell = FieldUiState.Cell(
                    label = R.string.title, isMandatory = true
                ), data = if (withData) "HDFC regalia gold cc" else ""
            ),
            FieldId.CARD_NAME to FieldUiState(
                cell = FieldUiState.Cell(label = R.string.name),
                data = if (withData) "NITIN" else ""
            ),
            FieldId.CARD_NUMBER to FieldUiState(
                cell = FieldUiState.Cell(
                    label = R.string.number,
                    isMandatory = true,
                    keyboardType = KeyboardType.Number,
                ), data = if (withData) "23470129841924" else ""
            ),
            FieldId.CARD_PIN to FieldUiState(
                cell = FieldUiState.Cell(
                    label = R.string.pin,
                    keyboardType = KeyboardType.Number,
                ), data = if (withData) "23423" else ""
            ),
            FieldId.CARD_CVV to FieldUiState(
                cell = FieldUiState.Cell(
                    label = R.string.cvv,
                    keyboardType = KeyboardType.Number,
                ), data = if (withData) "4234" else ""
            ),
            FieldId.CARD_EXPIRY_DATE to FieldUiState(
                cell = FieldUiState.Cell(
                    label = R.string.expiryDate, keyboardType = KeyboardType.Number
                ), data = if (withData) "4343" else ""
            ),
            FieldId.CARD_NOTES to FieldUiState(
                cell = FieldUiState.Cell(
                    label = R.string.notes, singleLine = false, minLines = 5
                ), data = if (withData) "dsfnkso sdfmnpsdmf" else ""
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
