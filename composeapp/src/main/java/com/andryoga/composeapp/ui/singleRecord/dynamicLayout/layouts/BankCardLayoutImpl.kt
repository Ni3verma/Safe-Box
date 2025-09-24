package com.andryoga.composeapp.ui.singleRecord.dynamicLayout.layouts

import androidx.compose.ui.text.input.KeyboardType
import com.andryoga.composeapp.R
import com.andryoga.composeapp.data.repository.interfaces.BankCardDataRepository
import com.andryoga.composeapp.domain.models.record.CardData
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.LayoutId
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.models.FieldId
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.models.FieldUiState
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.models.LayoutPlan
import java.util.Date

class BankCardLayoutImpl(
    private val recordId: Int?,
    private val bankCardDataRepository: BankCardDataRepository
) : Layout {
    private var layoutPlan: LayoutPlan? = null

    override suspend fun getLayoutPlan(): LayoutPlan {
        val recordData =
            recordId?.let { bankCardDataRepository.getBankCardDataByKey(recordId) }
        return layoutPlan ?: getLayoutPlanInternal(recordData)
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
                updateDate = Date(),
            )
        )
    }

    private fun getLayoutPlanInternal(recordData: CardData?): LayoutPlan {
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
                listOf(LayoutPlan.Field(fieldId = FieldId.CARD_NOTES)),
                listOf(LayoutPlan.Field(fieldId = FieldId.CREATION_DATE)),
                listOf(LayoutPlan.Field(fieldId = FieldId.UPDATE_DATE)),
            ),
            fieldUiState = mapOf(
                FieldId.CARD_TITLE to FieldUiState(
                    cell = FieldUiState.Cell(
                        label = R.string.title, isMandatory = true
                    ),
                    data = recordData?.title.orEmpty()
                ),
                FieldId.CARD_NAME to FieldUiState(
                    cell = FieldUiState.Cell(label = R.string.name),
                    data = recordData?.name.orEmpty()
                ),
                FieldId.CARD_NUMBER to FieldUiState(
                    cell = FieldUiState.Cell(
                        label = R.string.number,
                        isMandatory = true,
                        keyboardType = KeyboardType.Number,
                    ),
                    data = recordData?.number.orEmpty()
                ),
                FieldId.CARD_PIN to FieldUiState(
                    cell = FieldUiState.Cell(
                        label = R.string.pin,
                        keyboardType = KeyboardType.Number,
                    ),
                    data = recordData?.pin.orEmpty()
                ),
                FieldId.CARD_CVV to FieldUiState(
                    cell = FieldUiState.Cell(
                        label = R.string.cvv,
                        keyboardType = KeyboardType.Number,
                    ),
                    data = recordData?.cvv.orEmpty()
                ),
                FieldId.CARD_EXPIRY_DATE to FieldUiState(
                    cell = FieldUiState.Cell(
                        label = R.string.expiryDate, keyboardType = KeyboardType.Number
                    ),
                    data = recordData?.expiryDate.orEmpty()
                ),
                FieldId.CARD_NOTES to FieldUiState(
                    cell = FieldUiState.Cell(
                        label = R.string.notes, singleLine = false, minLines = 5
                    ),
                    data = recordData?.notes.orEmpty()
                ),
                FieldId.CREATION_DATE to FieldUiState(
                    cell = FieldUiState.Cell(
                        label = R.string.created_on, isVisibleOnlyInViewMode = true
                    ),
                    data = recordData?.creationDate?.toString().orEmpty()
                ),
                FieldId.UPDATE_DATE to FieldUiState(
                    cell = FieldUiState.Cell(
                        label = R.string.updated_on, isVisibleOnlyInViewMode = true
                    ),
                    data = recordData?.updateDate?.toString().orEmpty()
                ),
            )
        )
        layoutPlan = plan
        return plan
    }
}