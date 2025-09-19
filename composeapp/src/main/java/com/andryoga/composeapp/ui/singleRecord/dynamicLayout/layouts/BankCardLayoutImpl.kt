package com.andryoga.composeapp.ui.singleRecord.dynamicLayout.layouts

import androidx.compose.ui.text.input.KeyboardType
import com.andryoga.composeapp.R
import com.andryoga.composeapp.data.repository.BankAccountDataRepositoryImpl
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.LayoutId
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.models.FieldId
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.models.FieldUiState
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.models.LayoutPlan
import javax.inject.Inject

class BankCardLayoutImpl @Inject constructor(
    private val bankAccountDataRepositoryImpl: BankAccountDataRepositoryImpl
) : Layout {
    private var layoutPlan: LayoutPlan? = null

    override fun getLayoutPlan(): LayoutPlan {
        return layoutPlan ?: getLayoutPlanInternal()
    }

    override suspend fun saveLayout(data: Map<FieldId, String>) {

    }

    private fun getLayoutPlanInternal(): LayoutPlan {
        val plan = LayoutPlan(
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
        layoutPlan = plan
        return plan
    }
}