package com.andryoga.composeapp.ui.singleRecord.dynamicLayout.layouts

import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.andryoga.composeapp.R
import com.andryoga.composeapp.common.Utils.crashInDebugBuild
import com.andryoga.composeapp.data.repository.interfaces.LoginDataRepository
import com.andryoga.composeapp.domain.models.record.LoginData
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.LayoutId
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.models.FieldId
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.models.FieldUiState
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.models.LayoutPlan
import java.util.Date

class LoginLayoutImpl(
    private val recordId: Int?,
    private val loginDataRepository: LoginDataRepository
) : Layout {
    private var recordData: LoginData? = null

    override suspend fun getLayoutPlan(): LayoutPlan {
        recordData =
            recordId?.let { loginDataRepository.getLoginDataByKey(recordId) }
        return getLayoutPlanInternal()
    }

    override suspend fun saveLayout(data: Map<FieldId, String>) {
        loginDataRepository.upsertLoginData(
            LoginData(
                id = recordId,
                title = data[FieldId.LOGIN_TITLE] ?: "",
                url = data[FieldId.LOGIN_URL],
                userId = data[FieldId.LOGIN_USER_ID] ?: "",
                password = data[FieldId.LOGIN_PASSWORD],
                notes = data[FieldId.LOGIN_NOTES],
                creationDate = recordData?.creationDate ?: Date(),
                updateDate = Date(),
            )
        )
    }

    override suspend fun deleteLayout() {
        if (recordId != null) {
            loginDataRepository.deleteLoginDataByKey(recordId)
        } else {
            crashInDebugBuild("recordId is null, cannot delete")
        }
    }

    private fun getLayoutPlanInternal(): LayoutPlan {
        return LayoutPlan(
            id = LayoutId.LOGIN,
            arrangement = listOf(
                listOf(LayoutPlan.Field(fieldId = FieldId.LOGIN_TITLE)),
                listOf(LayoutPlan.Field(fieldId = FieldId.LOGIN_URL)),
                listOf(LayoutPlan.Field(fieldId = FieldId.LOGIN_USER_ID)),
                listOf(LayoutPlan.Field(fieldId = FieldId.LOGIN_PASSWORD)),
                listOf(LayoutPlan.Field(fieldId = FieldId.LOGIN_NOTES)),
                listOf(LayoutPlan.Field(fieldId = FieldId.CREATION_DATE)),
                listOf(LayoutPlan.Field(fieldId = FieldId.UPDATE_DATE)),
            ),
            fieldUiState = mapOf(
                FieldId.LOGIN_TITLE to FieldUiState(
                    cell = FieldUiState.Cell(
                        label = R.string.title, isMandatory = true, isCopyable = true,
                    ),
                    data = recordData?.title.orEmpty()
                ),
                FieldId.LOGIN_URL to FieldUiState(
                    cell = FieldUiState.Cell(label = R.string.url, isCopyable = true),
                    data = recordData?.url.orEmpty()
                ),
                FieldId.LOGIN_USER_ID to FieldUiState(
                    cell = FieldUiState.Cell(
                        label = R.string.user_id, isMandatory = true, isCopyable = true,
                    ),
                    data = recordData?.userId.orEmpty()
                ),
                FieldId.LOGIN_PASSWORD to FieldUiState(
                    cell = FieldUiState.Cell(
                        label = R.string.password,
                        isPasswordField = true,
                        visualTransformation = PasswordVisualTransformation()
                    ),
                    data = recordData?.password.orEmpty()
                ),
                FieldId.LOGIN_NOTES to FieldUiState(
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