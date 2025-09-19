package com.andryoga.composeapp.ui.singleRecord.dynamicLayout.layouts

import androidx.compose.ui.text.input.KeyboardType
import com.andryoga.composeapp.R
import com.andryoga.composeapp.data.repository.interfaces.BankCardDataRepository
import com.andryoga.composeapp.ui.core.models.CardData
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.LayoutId
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.models.FieldId
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.models.FieldUiState
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.models.LayoutPlan
import java.util.Date
import javax.inject.Inject

class BankCardLayoutImpl @Inject constructor(
    private val bankCardDataRepository: BankCardDataRepository
) : Layout {
    private var layoutPlan: LayoutPlan? = null

    override fun getLayoutPlan(): LayoutPlan {
        return layoutPlan ?: getLayoutPlanInternal()
    }

    override suspend fun saveLayout(data: Map<FieldId, String>) {
        bankCardDataRepository.upsertBankCardData(
            CardData(
                id = 0,
                title = data[FieldId.CARD_TITLE] ?: "",
                name = data[FieldId.CARD_NAME],
                number = data[FieldId.CARD_NUMBER] ?: "",
                expiryDate = data[FieldId.CARD_EXPIRY_DATE],
                cvv = data[FieldId.CARD_CVV],
                pin = data[FieldId.CARD_PIN],
                notes = data[FieldId.CARD_NOTES],
                creationDate = Date(),
            )
        )
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