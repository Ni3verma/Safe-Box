package com.andryoga.safebox.ui.singleRecord.dynamicLayout.layouts

import androidx.compose.ui.text.input.KeyboardType
import com.andryoga.safebox.R
import com.andryoga.safebox.common.Utils
import com.andryoga.safebox.data.repository.interfaces.BankAccountDataRepository
import com.andryoga.safebox.domain.models.record.BankAccountData
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.LayoutId
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldId
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldUiState
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.LayoutPlan
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.visualTransformers.SpaceAfterEveryFourCharsTransformation
import java.util.Date

class BankAccountLayoutImpl(
    private val recordId: Int?,
    private val bankAccountDataRepository: BankAccountDataRepository
) : Layout {
    private var recordData: BankAccountData? = null

    override suspend fun getLayoutPlan(): LayoutPlan {
        recordData =
            recordId?.let { bankAccountDataRepository.getBankAccountDataByKey(recordId) }
        return getLayoutPlanInternal()
    }

    override suspend fun saveLayout(data: Map<FieldId, String>) {
        bankAccountDataRepository.upsertBankAccountData(
            BankAccountData(
                id = recordId,
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
                creationDate = recordData?.creationDate ?: Date(),
                updateDate = Date(),
            )
        )
    }

    override suspend fun deleteLayout() {
        if (recordId != null) {
            bankAccountDataRepository.deleteBankAccountDataByKey(recordId)
        } else {
            Utils.crashInDebugBuild("recordId is null, cannot delete")
        }
    }

    private fun getLayoutPlanInternal(): LayoutPlan {
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
                listOf(LayoutPlan.Field(fieldId = FieldId.UPDATE_DATE)),
            ),
            fieldUiState = mapOf(
                FieldId.BANK_ACCOUNT_TITLE to FieldUiState(
                    cell = FieldUiState.Cell(
                        label = R.string.title, isMandatory = true, isCopyable = true,
                    ),
                    data = recordData?.title.orEmpty()
                ),
                FieldId.BANK_ACCOUNT_ACCOUNT_NUMBER to FieldUiState(
                    cell = FieldUiState.Cell(
                        label = R.string.account_number,
                        isMandatory = true,
                        keyboardType = KeyboardType.Number, isCopyable = true,
                        visualTransformation = SpaceAfterEveryFourCharsTransformation(),
                    ),
                    data = recordData?.accountNo.orEmpty()
                ),
                FieldId.BANK_ACCOUNT_CUSTOMER_NAME to FieldUiState(
                    cell = FieldUiState.Cell(label = R.string.customer_name, isCopyable = true),
                    data = recordData?.customerName.orEmpty()
                ),
                FieldId.BANK_ACCOUNT_CUSTOMER_ID to FieldUiState(
                    cell = FieldUiState.Cell(
                        label = R.string.customer_id,
                        visualTransformation = SpaceAfterEveryFourCharsTransformation(),
                    ),
                    data = recordData?.customerId.orEmpty(),
                ),
                FieldId.BANK_ACCOUNT_BRANCH_CODE to FieldUiState(
                    cell = FieldUiState.Cell(label = R.string.branch_code, isCopyable = true),
                    data = recordData?.branchCode.orEmpty()
                ),
                FieldId.BANK_ACCOUNT_BRANCH_NAME to FieldUiState(
                    cell = FieldUiState.Cell(label = R.string.branch_name, isCopyable = true),
                    data = recordData?.branchName.orEmpty()
                ),
                FieldId.BANK_ACCOUNT_BRANCH_ADDRESS to FieldUiState(
                    cell = FieldUiState.Cell(label = R.string.branch_address, isCopyable = true),
                    data = recordData?.branchAddress.orEmpty()
                ),
                FieldId.BANK_ACCOUNT_IFSC_CODE to FieldUiState(
                    cell = FieldUiState.Cell(
                        label = R.string.ifsc_code, isCopyable = true,
                        visualTransformation = SpaceAfterEveryFourCharsTransformation(),
                    ),
                    data = recordData?.ifscCode.orEmpty()
                ),
                FieldId.BANK_ACCOUNT_MICR_CODE to FieldUiState(
                    cell = FieldUiState.Cell(label = R.string.micr_code),
                    data = recordData?.micrCode.orEmpty()
                ),
                FieldId.BANK_ACCOUNT_NOTES to FieldUiState(
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
    }

}