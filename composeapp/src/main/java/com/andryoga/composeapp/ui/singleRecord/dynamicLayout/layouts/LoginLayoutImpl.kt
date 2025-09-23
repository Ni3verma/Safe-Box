package com.andryoga.composeapp.ui.singleRecord.dynamicLayout.layouts

import com.andryoga.composeapp.R
import com.andryoga.composeapp.data.repository.interfaces.LoginDataRepository
import com.andryoga.composeapp.domain.models.record.LoginData
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.LayoutId
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.models.FieldId
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.models.FieldUiState
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.models.LayoutPlan
import java.util.Date
import javax.inject.Inject

class LoginLayoutImpl @Inject constructor(
    private val loginDataRepository: LoginDataRepository
) : Layout {
    private var layoutPlan: LayoutPlan? = null

    override fun getLayoutPlan(): LayoutPlan {
        return layoutPlan ?: getLayoutPlanInternal()
    }

    override suspend fun saveLayout(data: Map<FieldId, String>) {
        loginDataRepository.upsertLoginData(
            LoginData(
                id = 0,
                title = data[FieldId.LOGIN_TITLE] ?: "",
                url = data[FieldId.LOGIN_URL],
                userId = data[FieldId.LOGIN_USER_ID] ?: "",
                password = data[FieldId.LOGIN_PASSWORD],
                notes = data[FieldId.LOGIN_NOTES],
                creationDate = Date()
            )
        )
    }

    private fun getLayoutPlanInternal(): LayoutPlan {
        val plan = LayoutPlan(
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

        layoutPlan = plan
        return plan
    }
}