package com.andryoga.safebox.ui.singleRecord.dynamicLayout.layouts

import com.andryoga.safebox.R
import com.andryoga.safebox.data.repository.interfaces.AuthenticatorDataRepository
import com.andryoga.safebox.domain.models.record.AuthenticatorData
import com.andryoga.safebox.totp.engine.Base32Utils
import com.andryoga.safebox.totp.engine.interfaces.TotpGenerator
import com.andryoga.safebox.totp.models.TotpAlgorithm
import com.andryoga.safebox.totp.models.TotpConfig
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.LayoutId
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldId
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldType
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldUiState
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.util.Date

class AuthenticatorLayoutImplTest {

    private val repository: AuthenticatorDataRepository = mockk(relaxed = true)
    private val totpGenerator: TotpGenerator = mockk(relaxed = true)

    private val sampleDate = Date(1700000000000L)

    // deliberately non default so that any param dropped on the way to the UI or to save shows up.
    private val sampleConfig = TotpConfig(
        secretKey = "JBSWY3DPEHPK3PXP",
        algorithm = TotpAlgorithm.SHA256,
        digits = 8,
        period = 60,
    )
    private val sampleAuthenticator = AuthenticatorData(
        id = 10,
        title = "Google",
        config = sampleConfig,
        creationDate = sampleDate,
        updateDate = sampleDate,
    )

    @Before
    fun setUp() {
        coEvery { repository.getAuthenticatorDataByKey(10) } returns sampleAuthenticator
        every { totpGenerator.isValidSecret(any()) } returns true
        every { totpGenerator.isValidConfig(any()) } returns true
        every { totpGenerator.normalizeSecret(any()) } answers { Base32Utils.sanitize(firstArg()) }
    }

    private fun createLayout(recordId: Int? = null) = AuthenticatorLayoutImpl(
        recordId = recordId,
        authenticatorDataRepository = repository,
        totpGenerator = totpGenerator,
    )

    private fun mandatoryFields(title: String, secretKey: String) = mapOf(
        FieldId.AUTHENTICATOR_TITLE to FieldUiState(
            cell = FieldUiState.Cell(label = R.string.title, isMandatory = true),
            data = title,
        ),
        FieldId.AUTHENTICATOR_SECRET_KEY to FieldUiState(
            cell = FieldUiState.Cell(label = R.string.secret_key, isMandatory = true),
            data = secretKey,
        ),
    )

    @Test
    fun getLayoutPlan_existingRecord_returnsPopulatedLayoutPlan() = runTest {
        val plan = createLayout(recordId = 10).getLayoutPlan()

        assertThat(plan.id).isEqualTo(LayoutId.AUTHENTICATOR)
        assertThat(plan.fieldUiState[FieldId.AUTHENTICATOR_TITLE]?.data).isEqualTo("Google")

        val totpField = plan.fieldUiState[FieldId.AUTHENTICATOR_TOTP_DISPLAY]
        assertThat(totpField?.data).isEqualTo("JBSWY3DPEHPK3PXP")
        assertThat(totpField?.cell?.type).isEqualTo(FieldType.Totp(sampleConfig))
        assertThat(totpField?.cell?.isVisibleOnlyInViewMode).isTrue()

        assertThat(plan.fieldUiState[FieldId.AUTHENTICATOR_SECRET_KEY]?.data).isEqualTo("JBSWY3DPEHPK3PXP")
        assertThat(plan.fieldUiState[FieldId.AUTHENTICATOR_SECRET_KEY]?.cell?.isPasswordField).isTrue()
        assertThat(plan.fieldUiState[FieldId.CREATION_DATE]?.data).isEqualTo(sampleDate.toString())
        assertThat(plan.fieldUiState[FieldId.UPDATE_DATE]?.data).isEqualTo(sampleDate.toString())
    }

    @Test
    fun getLayoutPlan_newRecord_returnsEmptyLayoutPlan() = runTest {
        val plan = createLayout().getLayoutPlan()

        assertThat(plan.id).isEqualTo(LayoutId.AUTHENTICATOR)
        assertThat(plan.fieldUiState[FieldId.AUTHENTICATOR_TITLE]?.data).isEmpty()
        assertThat(plan.fieldUiState[FieldId.AUTHENTICATOR_SECRET_KEY]?.data).isEmpty()
        assertThat(plan.fieldUiState[FieldId.CREATION_DATE]?.data).isEmpty()
        assertThat(plan.fieldUiState[FieldId.UPDATE_DATE]?.data).isEmpty()
    }

    @Test
    fun getLayoutPlan_totpCodeField_isNotMarkedCopyable() = runTest {
        val plan = createLayout(recordId = 10).getLayoutPlan()

        // the field holds the secret seed, so the generic share path must never pick it up
        val totpField = plan.fieldUiState[FieldId.AUTHENTICATOR_TOTP_DISPLAY]
        assertThat(totpField?.cell?.isCopyable).isFalse()
    }

    @Test
    fun saveLayout_newRecord_callsRepositoryUpsertWithTrimmedData() = runTest {
        val dataSlot = slot<AuthenticatorData>()
        coEvery { repository.upsertAuthenticatorData(capture(dataSlot)) } returns Unit

        createLayout().saveLayout(
            mapOf(
                FieldId.AUTHENTICATOR_TITLE to "  My AWS  ",
                FieldId.AUTHENTICATOR_SECRET_KEY to "  JBSWY3DPEHPK3PXP  ",
            ),
        )

        coVerify(exactly = 1) { repository.upsertAuthenticatorData(any()) }
        val captured = dataSlot.captured
        assertThat(captured.id).isNull()
        assertThat(captured.title).isEqualTo("My AWS")
        assertThat(captured.config.secretKey).isEqualTo("JBSWY3DPEHPK3PXP")
    }

    @Test
    fun saveLayout_secretKeyFormattedByIssuer_persistsCanonicalBase32() = runTest {
        val dataSlot = slot<AuthenticatorData>()
        coEvery { repository.upsertAuthenticatorData(capture(dataSlot)) } returns Unit

        createLayout().saveLayout(
            mapOf(
                FieldId.AUTHENTICATOR_TITLE to "My AWS",
                FieldId.AUTHENTICATOR_SECRET_KEY to " jbsw y3dp-ehpk3pxp== ",
            ),
        )

        assertThat(dataSlot.captured.config.secretKey).isEqualTo("JBSWY3DPEHPK3PXP")
    }

    @Test
    fun saveLayout_existingRecord_preservesCreationDate() = runTest {
        val layout = createLayout(recordId = 10)
        layout.getLayoutPlan() // populates recordData

        val dataSlot = slot<AuthenticatorData>()
        coEvery { repository.upsertAuthenticatorData(capture(dataSlot)) } returns Unit

        layout.saveLayout(
            mapOf(
                FieldId.AUTHENTICATOR_TITLE to "Google Updated",
                FieldId.AUTHENTICATOR_SECRET_KEY to "JBSWY3DPEHPK3PXP",
            ),
        )

        val captured = dataSlot.captured
        assertThat(captured.id).isEqualTo(10)
        assertThat(captured.title).isEqualTo("Google Updated")
        assertThat(captured.creationDate).isEqualTo(sampleDate)
    }

    @Test
    fun saveLayout_existingRecord_preservesStoredGenerationParams() = runTest {
        // the params are not editable on this screen, so an edit must not silently reset them.
        val layout = createLayout(recordId = 10)
        layout.getLayoutPlan() // populates recordData

        val dataSlot = slot<AuthenticatorData>()
        coEvery { repository.upsertAuthenticatorData(capture(dataSlot)) } returns Unit

        layout.saveLayout(
            mapOf(
                FieldId.AUTHENTICATOR_TITLE to "Google Updated",
                FieldId.AUTHENTICATOR_SECRET_KEY to "JBSWY3DPEHPK3PXP",
            ),
        )

        assertThat(dataSlot.captured.config).isEqualTo(sampleConfig)
    }

    @Test
    fun saveLayout_newRecord_fallsBackToDefaultGenerationParams() = runTest {
        val dataSlot = slot<AuthenticatorData>()
        coEvery { repository.upsertAuthenticatorData(capture(dataSlot)) } returns Unit

        createLayout().saveLayout(
            mapOf(
                FieldId.AUTHENTICATOR_TITLE to "My AWS",
                FieldId.AUTHENTICATOR_SECRET_KEY to "JBSWY3DPEHPK3PXP",
            ),
        )

        assertThat(dataSlot.captured.config).isEqualTo(TotpConfig(secretKey = "JBSWY3DPEHPK3PXP"))
    }

    @Test
    fun deleteLayout_existingRecord_callsRepositoryDelete() = runTest {
        createLayout(recordId = 10).deleteLayout()

        coVerify(exactly = 1) { repository.deleteAuthenticatorDataByKey(10) }
    }

    @Test
    fun getShareableFields_sharesGeneratedCodeAndNeverTheSecret() = runTest {
        every { totpGenerator.generateCode(sampleConfig, any()) } returns "654321"

        val shareableFields = createLayout(recordId = 10).getShareableFields()

        assertThat(shareableFields).hasSize(2)
        assertThat(shareableFields[0].label).isEqualTo(R.string.title)
        assertThat(shareableFields[0].value).isEqualTo("Google")
        assertThat(shareableFields[1].label).isEqualTo(R.string.totp_code)
        assertThat(shareableFields[1].value).isEqualTo("654321")
        assertThat(shareableFields.map { it.value }).doesNotContain("JBSWY3DPEHPK3PXP")
    }

    @Test
    fun getShareableFields_invalidConfig_skipsCodeInsteadOfThrowing() = runTest {
        // restore-from-backup can insert an unusable seed or param, generateCode would throw on it.
        every { totpGenerator.isValidConfig(any()) } returns false
        every {
            totpGenerator.generateCode(any(), any())
        } throws IllegalArgumentException("invalid")

        val shareableFields = createLayout(recordId = 10).getShareableFields()

        assertThat(shareableFields).hasSize(1)
        assertThat(shareableFields.single().label).isEqualTo(R.string.title)
    }

    @Test
    fun checkMandatoryFields_validSecretAndTitle_evaluatesTrue() {
        every { totpGenerator.isValidSecret("JBSWY3DPEHPK3PXP") } returns true

        val isValid = createLayout().checkMandatoryFields(
            mandatoryFields(title = "Google", secretKey = "JBSWY3DPEHPK3PXP"),
        )

        assertThat(isValid).isTrue()
    }

    @Test
    fun checkMandatoryFields_secretNotDecodableAsBase32_evaluatesFalse() {
        every { totpGenerator.isValidSecret("INVALID_BASE32_189") } returns false

        val isValid = createLayout().checkMandatoryFields(
            mandatoryFields(title = "Google", secretKey = "INVALID_BASE32_189"),
        )

        assertThat(isValid).isFalse()
    }

    @Test
    fun checkMandatoryFields_blankTitle_evaluatesFalse() {
        every { totpGenerator.isValidSecret("JBSWY3DPEHPK3PXP") } returns true

        val isValid = createLayout().checkMandatoryFields(
            mandatoryFields(title = "   ", secretKey = "JBSWY3DPEHPK3PXP"),
        )

        assertThat(isValid).isFalse()
    }
}
