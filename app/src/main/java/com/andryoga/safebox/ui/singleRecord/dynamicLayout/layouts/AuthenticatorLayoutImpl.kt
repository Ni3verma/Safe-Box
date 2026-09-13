package com.andryoga.safebox.ui.singleRecord.dynamicLayout.layouts

import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.andryoga.safebox.R
import com.andryoga.safebox.common.Utils
import com.andryoga.safebox.data.repository.interfaces.AuthenticatorDataRepository
import com.andryoga.safebox.domain.models.record.AuthenticatorData
import com.andryoga.safebox.totp.engine.interfaces.TotpGenerator
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.LayoutId
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldId
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldUiState
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.LayoutPlan
import java.util.Date

/**
 * Implementation of [Layout] for 2FA TOTP Authenticator records.
 *
 * Manages the dynamic layout plan across view, edit, and creation modes for authenticator entries.
 * Enforces mandatory Base32 format validation for secrets and persists encrypted entries
 * via [AuthenticatorDataRepository].
 *
 * @param recordId Unique identifier of the record, or null when creating a new record.
 * @param authenticatorDataRepository Repository for loading, upserting, and deleting authenticator entities.
 * @param totpGenerator Generator used to validate RFC 4648 Base32 secret seeds.
 * @param initialTitle Optional initial title value (e.g., pre-filled from QR code scan).
 * @param initialSecretKey Optional initial secret key value (e.g., pre-filled from QR code scan).
 */
class AuthenticatorLayoutImpl(
    private val recordId: Int?,
    private val authenticatorDataRepository: AuthenticatorDataRepository,
    private val totpGenerator: TotpGenerator,
    private val initialTitle: String? = null,
    private val initialSecretKey: String? = null,
) : Layout {
    private var recordData: AuthenticatorData? = null

    override suspend fun getLayoutPlan(): LayoutPlan {
        recordData = recordId?.let { authenticatorDataRepository.getAuthenticatorDataByKey(it) }
        return getLayoutPlanInternal()
    }

    override suspend fun saveLayout(data: Map<FieldId, String>) {
        val title = data[FieldId.AUTHENTICATOR_TITLE]?.trim().orEmpty()
        val secretKey = data[FieldId.AUTHENTICATOR_SECRET_KEY]?.trim().orEmpty()

        authenticatorDataRepository.upsertAuthenticatorData(
            AuthenticatorData(
                id = recordId,
                title = title,
                secretKey = secretKey,
                creationDate = recordData?.creationDate ?: Date(),
                updateDate = Date(),
            ),
        )
    }

    override suspend fun deleteLayout() {
        if (recordId != null) {
            authenticatorDataRepository.deleteAuthenticatorDataByKey(recordId)
        } else {
            Utils.crashInDebugBuild("recordId is null, cannot delete")
        }
    }

    override fun checkMandatoryFields(fieldUiState: Collection<FieldUiState>): Boolean {
        val mandatoryFilled = super.checkMandatoryFields(fieldUiState)
        if (!mandatoryFilled) return false

        val secretKey = fieldUiState
            .firstOrNull { it.cell.label == R.string.secret_key }
            ?.data
            ?.trim()
            .orEmpty()
        return totpGenerator.isValidSecret(secretKey)
    }

    private fun getLayoutPlanInternal(): LayoutPlan {
        val resolvedTitle = recordData?.title ?: initialTitle.orEmpty()
        val resolvedSecretKey = recordData?.secretKey ?: initialSecretKey.orEmpty()

        return LayoutPlan(
            id = LayoutId.AUTHENTICATOR,
            arrangement = listOf(
                listOf(LayoutPlan.Field(fieldId = FieldId.AUTHENTICATOR_TITLE)),
                listOf(LayoutPlan.Field(fieldId = FieldId.AUTHENTICATOR_TOTP_CODE)),
                listOf(LayoutPlan.Field(fieldId = FieldId.AUTHENTICATOR_SECRET_KEY)),
                listOf(LayoutPlan.Field(fieldId = FieldId.CREATION_DATE)),
                listOf(LayoutPlan.Field(fieldId = FieldId.UPDATE_DATE)),
            ),
            fieldUiState = mapOf(
                FieldId.AUTHENTICATOR_TITLE to FieldUiState(
                    cell = FieldUiState.Cell(
                        label = R.string.title,
                        isMandatory = true,
                        isCopyable = true,
                    ),
                    data = resolvedTitle,
                ),
                FieldId.AUTHENTICATOR_TOTP_CODE to FieldUiState(
                    cell = FieldUiState.Cell(
                        label = R.string.totp_code,
                        isVisibleOnlyInViewMode = true,
                        isTotpCodeField = true,
                        isCopyable = true,
                    ),
                    data = resolvedSecretKey,
                ),
                FieldId.AUTHENTICATOR_SECRET_KEY to FieldUiState(
                    cell = FieldUiState.Cell(
                        label = R.string.secret_key,
                        isMandatory = true,
                        isPasswordField = true,
                        visualTransformation = PasswordVisualTransformation(),
                    ),
                    data = resolvedSecretKey,
                ),
                FieldId.CREATION_DATE to FieldUiState(
                    cell = FieldUiState.Cell(
                        label = R.string.created_on,
                        isVisibleOnlyInViewMode = true,
                    ),
                    data = recordData?.creationDate?.toString().orEmpty(),
                ),
                FieldId.UPDATE_DATE to FieldUiState(
                    cell = FieldUiState.Cell(
                        label = R.string.updated_on,
                        isVisibleOnlyInViewMode = true,
                    ),
                    data = recordData?.updateDate?.toString().orEmpty(),
                ),
            ),
        )
    }
}
