package com.andryoga.safebox.ui.singleRecord.dynamicLayout.layouts

import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.andryoga.safebox.R
import com.andryoga.safebox.common.Utils
import com.andryoga.safebox.data.repository.interfaces.AuthenticatorDataRepository
import com.andryoga.safebox.domain.models.record.AuthenticatorData
import com.andryoga.safebox.totp.engine.interfaces.TotpGenerator
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.LayoutId
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldId
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldType
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldUiState
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.LayoutPlan
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.ShareableField
import java.util.Date

/**
 * Implementation of [Layout] for 2FA TOTP Authenticator records.
 *
 * Manages the dynamic layout plan across view, edit, and creation modes for authenticator entries.
 * Enforces Base32 format validation for the secret seed and persists encrypted entries via
 * [AuthenticatorDataRepository].
 *
 * @param recordId Unique identifier of the record, or null when creating a new record.
 * @param authenticatorDataRepository Repository for loading, upserting, and deleting authenticator entities.
 * @param totpGenerator Generator used to validate the Base32 seed and derive the shareable one-time code.
 */
class AuthenticatorLayoutImpl(
    private val recordId: Int?,
    private val authenticatorDataRepository: AuthenticatorDataRepository,
    private val totpGenerator: TotpGenerator,
) : Layout {
    private var recordData: AuthenticatorData? = null

    override suspend fun getLayoutPlan(): LayoutPlan {
        recordData = recordId?.let { authenticatorDataRepository.getAuthenticatorDataByKey(it) }
        return getLayoutPlanInternal()
    }

    override suspend fun saveLayout(data: Map<FieldId, String>) {
        val secretKey = data[FieldId.AUTHENTICATOR_SECRET_KEY].orEmpty()
        authenticatorDataRepository.upsertAuthenticatorData(
            AuthenticatorData(
                id = recordId,
                title = data[FieldId.AUTHENTICATOR_TITLE]?.trim().orEmpty(),
                secretKey = totpGenerator.normalizeSecret(secretKey),
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

    /**
     * Additionally enforces that the secret seed is valid RFC 4648 Base32.
     *
     * This runs on every keystroke in create/edit mode, where the user can hand type an arbitrary
     * seed via the "enter key manually" fallback. Blocking Save here prevents persisting a record
     * that could never produce a code.
     *
     * @param fieldUiState Current state of every field in the layout, keyed by [FieldId].
     * @return true when the mandatory fields are filled and the secret seed is decodable.
     */
    override fun checkMandatoryFields(fieldUiState: Map<FieldId, FieldUiState>): Boolean {
        if (super.checkMandatoryFields(fieldUiState).not()) return false

        val secretKey = fieldUiState[FieldId.AUTHENTICATOR_SECRET_KEY]?.data?.trim().orEmpty()
        return totpGenerator.isValidSecret(secretKey)
    }

    /**
     * Shares the derived one-time code instead of the stored seed.
     *
     * The layout stores the Base32 secret in [FieldId.AUTHENTICATOR_TOTP_CODE] so that the UI can
     * roll the code locally every second. Sharing that raw seed would hand over permanent access to
     * the second factor, so the code valid at share time is shared instead.
     *
     * Blank values are skipped to match the interface default, and an undecodable seed is skipped
     * because [TotpGenerator.generateCode] throws on one. Both are reachable for records inserted
     * by restore-from-backup, which does not validate the seed.
     *
     * @return title and the currently valid one-time code, omitting either if unusable.
     */
    override suspend fun getShareableFields(): List<ShareableField> {
        val fieldUiState = getLayoutPlan().fieldUiState
        val title = fieldUiState[FieldId.AUTHENTICATOR_TITLE]?.data.orEmpty()
        val secretKey = fieldUiState[FieldId.AUTHENTICATOR_SECRET_KEY]?.data.orEmpty()

        return buildList {
            if (title.isNotBlank()) {
                add(ShareableField(label = R.string.title, value = title))
            }
            if (totpGenerator.isValidSecret(secretKey)) {
                add(
                    ShareableField(
                        label = R.string.totp_code,
                        value = totpGenerator.generateCode(secretKey),
                    ),
                )
            }
        }
    }

    private fun getLayoutPlanInternal(): LayoutPlan {
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
                    data = recordData?.title.orEmpty(),
                ),
                FieldId.AUTHENTICATOR_TOTP_CODE to FieldUiState(
                    cell = FieldUiState.Cell(
                        label = R.string.totp_code,
                        isVisibleOnlyInViewMode = true,
                        type = FieldType.TOTP,
                        // holds the secret seed, never the code, so it must never be shared as is.
                        isCopyable = false,
                    ),
                    data = recordData?.secretKey.orEmpty(),
                ),
                FieldId.AUTHENTICATOR_SECRET_KEY to FieldUiState(
                    cell = FieldUiState.Cell(
                        label = R.string.secret_key,
                        isMandatory = true,
                        isPasswordField = true,
                        visualTransformation = PasswordVisualTransformation(),
                    ),
                    data = recordData?.secretKey.orEmpty(),
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
