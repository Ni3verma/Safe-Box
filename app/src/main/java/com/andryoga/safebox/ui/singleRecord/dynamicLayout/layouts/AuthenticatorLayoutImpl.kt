package com.andryoga.safebox.ui.singleRecord.dynamicLayout.layouts

import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.andryoga.safebox.R
import com.andryoga.safebox.common.Utils
import com.andryoga.safebox.data.repository.interfaces.AuthenticatorDataRepository
import com.andryoga.safebox.domain.models.record.AuthenticatorData
import com.andryoga.safebox.totp.engine.interfaces.TotpGenerator
import com.andryoga.safebox.totp.models.ParsedTotpData
import com.andryoga.safebox.totp.models.TotpConfig
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.LayoutId
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldId
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldType
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldUiState
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.LayoutPlan
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.ShareableField
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.ViewMode
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
 * @param scannedTotpData Payload of a QR code the user just scanned, or null when the record is
 * being created by hand or opened for view/edit.
 */
class AuthenticatorLayoutImpl(
    private val recordId: Int?,
    private val authenticatorDataRepository: AuthenticatorDataRepository,
    private val totpGenerator: TotpGenerator,
    private val scannedTotpData: ParsedTotpData? = null,
) : Layout {
    private var recordData: AuthenticatorData? = null

    override suspend fun getLayoutPlan(): LayoutPlan {
        recordData = recordId?.let { authenticatorDataRepository.getAuthenticatorDataByKey(it) }
        return getLayoutPlanInternal()
    }

    override suspend fun saveLayout(data: Map<FieldId, String>) {
        val secretKey = data[FieldId.AUTHENTICATOR_SECRET_KEY]
            ?.takeIf { it.isNotBlank() }
            ?: recordData?.config?.secretKey.orEmpty()
        authenticatorDataRepository.upsertAuthenticatorData(
            AuthenticatorData(
                id = recordId,
                title = data[FieldId.AUTHENTICATOR_TITLE]?.trim().orEmpty(),
                config = buildConfig(totpGenerator.normalizeSecret(secretKey)),
                creationDate = recordData?.creationDate ?: Date(),
                updateDate = Date(),
            ),
        )
    }

    /**
     * Pairs a seed with the generation parameters that belong to this record.
     *
     * The parameters are never edited on this screen, so they are read from the loaded record, then
     * from the scanned QR code, and only fall back to the RFC 6238 defaults for a record the user
     * is typing in by hand.
     *
     * @param secretKey Canonical Base32 seed to pair with the parameters.
     * @return Config for this record.
     */
    private fun buildConfig(secretKey: String): TotpConfig {
        val sourceConfig = recordData?.config
            ?: scannedTotpData?.config
            ?: return TotpConfig(secretKey = secretKey)
        return sourceConfig.copy(secretKey = secretKey)
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
     * The layout stores the Base32 secret in [FieldId.AUTHENTICATOR_TOTP_DISPLAY] so that the UI
     * can roll the code locally every second. Sharing that raw seed would hand over permanent
     * access to the second factor, so the code valid at share time is shared instead.
     *
     * Blank values are skipped to match the interface default, and an unusable config is skipped
     * because [TotpGenerator.generateCode] throws on one. Both are reachable for records inserted
     * by restore-from-backup, which does not validate the seed.
     *
     * @return title and the currently valid one-time code, omitting either if unusable.
     */
    override suspend fun getShareableFields(): List<ShareableField> {
        val fieldUiState = getLayoutPlan().fieldUiState
        val title = fieldUiState[FieldId.AUTHENTICATOR_TITLE]?.data.orEmpty()
        val secretKey = fieldUiState[FieldId.AUTHENTICATOR_SECRET_KEY]?.data.orEmpty()
        val config = buildConfig(secretKey)

        return buildList {
            if (title.isNotBlank()) {
                add(ShareableField(label = R.string.title, value = title))
            }
            if (totpGenerator.isValidConfig(config)) {
                add(
                    ShareableField(
                        label = R.string.totp_code,
                        value = totpGenerator.generateCode(config),
                    ),
                )
            }
        }
    }

    private fun getLayoutPlanInternal(): LayoutPlan {
        val title = recordData?.title ?: scannedTotpData?.title.orEmpty()
        val secretKey = recordData?.config?.secretKey
            ?: scannedTotpData?.config?.secretKey.orEmpty()

        return LayoutPlan(
            id = LayoutId.AUTHENTICATOR,
            arrangement = listOf(
                listOf(LayoutPlan.Field(fieldId = FieldId.AUTHENTICATOR_TITLE)),
                listOf(LayoutPlan.Field(fieldId = FieldId.AUTHENTICATOR_TOTP_DISPLAY)),
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
                    data = title,
                ),
                FieldId.AUTHENTICATOR_TOTP_DISPLAY to FieldUiState(
                    cell = FieldUiState.Cell(
                        label = R.string.totp_code,
                        visibleIn = setOf(ViewMode.VIEW),
                        type = FieldType.Totp(buildConfig(secretKey)),
                        // holds the secret seed, never the code, so it must never be shared as is.
                        isCopyable = false,
                    ),
                    data = secretKey,
                ),
                FieldId.AUTHENTICATOR_SECRET_KEY to FieldUiState(
                    cell = FieldUiState.Cell(
                        label = R.string.secret_key,
                        isMandatory = true,
                        isPasswordField = true,
                        // the seed is entered once and never shown again, so viewing or editing a
                        // saved record cannot put the second factor back on screen.
                        visibleIn = setOf(ViewMode.NEW),
                        visualTransformation = PasswordVisualTransformation(),
                    ),
                    data = secretKey,
                ),
                FieldId.CREATION_DATE to FieldUiState(
                    cell = FieldUiState.Cell(
                        label = R.string.created_on,
                        visibleIn = setOf(ViewMode.VIEW),
                    ),
                    data = recordData?.creationDate?.toString().orEmpty(),
                ),
                FieldId.UPDATE_DATE to FieldUiState(
                    cell = FieldUiState.Cell(
                        label = R.string.updated_on,
                        visibleIn = setOf(ViewMode.VIEW),
                    ),
                    data = recordData?.updateDate?.toString().orEmpty(),
                ),
            ),
        )
    }
}
