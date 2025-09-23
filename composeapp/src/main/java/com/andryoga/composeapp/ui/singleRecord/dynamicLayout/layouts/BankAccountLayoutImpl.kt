package com.andryoga.composeapp.ui.singleRecord.dynamicLayout.layouts

import androidx.compose.ui.text.input.KeyboardType
import com.andryoga.composeapp.R
import com.andryoga.composeapp.data.repository.interfaces.BankAccountDataRepository
import com.andryoga.composeapp.domain.models.record.BankAccountData
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.LayoutId
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.models.FieldId
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.models.FieldUiState
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.models.LayoutPlan
import java.util.Date
import javax.inject.Inject

class BankAccountLayoutImpl @Inject constructor(
    private val bankAccountDataRepository: BankAccountDataRepository
) : Layout {
    private var layoutPlan: LayoutPlan? = null

    override suspend fun getLayoutPlan(): LayoutPlan {
        return layoutPlan ?: getLayoutPlanInternal()
    }

    override suspend fun saveLayout(data: Map<FieldId, String>) {
        bankAccountDataRepository.upsertBankAccountData(
            BankAccountData(
                id = 0,
                title = data[FieldId.BANK_ACCOUNT_TITLE] ?: "",
                accountNo = data[FieldId.BANK_ACCOUNT_ACCOUNT_NUMBER] ?: "",
                customerName = data[FieldId.BANK_ACCOUNT_CUSTOMER_NAME],
                customerId = data[FieldId.BANK_ACCOUNT_CUSTOMER_ID],
                branchCode = data[FieldId.BANK_ACCOUNT_BRANCH_CODE],
                branchName = data[FieldId.BANK_ACCOUNT_BRANCH_NAME],
                branchAddress = data[FieldId.BANK_ACCOUNT_BRANCH_ADDRESS],
                ifscCode = data[FieldId.BANK_ACCOUNT_IFSC_CODE],
                micrCode = data[FieldId.BANK_ACCOUNT_MICR_CODE],
                notes = data[FieldId.BANK_ACCOUNT_NOTES],
                creationDate = Date(),
            )
        )
    }

    private fun getLayoutPlanInternal(): LayoutPlan {
        val plan = LayoutPlan(
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
        layoutPlan = plan
        return plan
    }

}