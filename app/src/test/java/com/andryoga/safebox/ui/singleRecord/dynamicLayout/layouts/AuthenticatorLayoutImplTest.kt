package com.andryoga.safebox.ui.singleRecord.dynamicLayout.layouts

import com.andryoga.safebox.R
import com.andryoga.safebox.data.repository.interfaces.AuthenticatorDataRepository
import com.andryoga.safebox.domain.models.record.AuthenticatorData
import com.andryoga.safebox.totp.engine.interfaces.TotpGenerator
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.LayoutId
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldId
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
    private val sampleAuthenticator = AuthenticatorData(
        id = 10,
        title = "Google",
        secretKey = "JBSWY3DPEHPK3PXP",
        creationDate = sampleDate,
        updateDate = sampleDate,
    )

    @Before
    fun setUp() {
        coEvery { repository.getAuthenticatorDataByKey(10) } returns sampleAuthenticator
        every { totpGenerator.isValidSecret(any()) } returns true
    }

    @Test
    fun getLayoutPlan_existingRecord_returnsPopulatedLayoutPlan() = runTest {
        val layout = AuthenticatorLayoutImpl(
            recordId = 10,
            authenticatorDataRepository = repository,
            totpGenerator = totpGenerator,
        )

        val plan = layout.getLayoutPlan()

        assertThat(plan.id).isEqualTo(LayoutId.AUTHENTICATOR)
        assertThat(plan.fieldUiState[FieldId.AUTHENTICATOR_TITLE]?.data).isEqualTo("Google")
        assertThat(plan.fieldUiState[FieldId.AUTHENTICATOR_TOTP_CODE]?.data).isEqualTo("JBSWY3DPEHPK3PXP")
        assertThat(plan.fieldUiState[FieldId.AUTHENTICATOR_TOTP_CODE]?.cell?.isTotpCodeField).isTrue()
        assertThat(plan.fieldUiState[FieldId.AUTHENTICATOR_TOTP_CODE]?.cell?.isVisibleOnlyInViewMode).isTrue()
        assertThat(plan.fieldUiState[FieldId.AUTHENTICATOR_SECRET_KEY]?.data).isEqualTo("JBSWY3DPEHPK3PXP")
        assertThat(plan.fieldUiState[FieldId.AUTHENTICATOR_SECRET_KEY]?.cell?.isPasswordField).isTrue()
        assertThat(plan.fieldUiState[FieldId.CREATION_DATE]?.data).isEqualTo(sampleDate.toString())
        assertThat(plan.fieldUiState[FieldId.UPDATE_DATE]?.data).isEqualTo(sampleDate.toString())
    }

    @Test
    fun getLayoutPlan_newRecordWithInitialData_returnsPreFilledLayoutPlan() = runTest {
        val layout = AuthenticatorLayoutImpl(
            recordId = null,
            authenticatorDataRepository = repository,
            totpGenerator = totpGenerator,
            initialTitle = "GitHub",
            initialSecretKey = "HXDMVJECJJWSRB3H",
        )

        val plan = layout.getLayoutPlan()

        assertThat(plan.id).isEqualTo(LayoutId.AUTHENTICATOR)
        assertThat(plan.fieldUiState[FieldId.AUTHENTICATOR_TITLE]?.data).isEqualTo("GitHub")
        assertThat(plan.fieldUiState[FieldId.AUTHENTICATOR_TOTP_CODE]?.data).isEqualTo("HXDMVJECJJWSRB3H")
        assertThat(plan.fieldUiState[FieldId.AUTHENTICATOR_SECRET_KEY]?.data).isEqualTo("HXDMVJECJJWSRB3H")
        assertThat(plan.fieldUiState[FieldId.CREATION_DATE]?.data).isEmpty()
        assertThat(plan.fieldUiState[FieldId.UPDATE_DATE]?.data).isEmpty()
    }

    @Test
    fun saveLayout_newRecord_callsRepositoryUpsertWithTrimmedData() = runTest {
        val layout = AuthenticatorLayoutImpl(
            recordId = null,
            authenticatorDataRepository = repository,
            totpGenerator = totpGenerator,
        )
        val dataSlot = slot<AuthenticatorData>()
        coEvery { repository.upsertAuthenticatorData(capture(dataSlot)) } returns Unit

        layout.saveLayout(
            mapOf(
                FieldId.AUTHENTICATOR_TITLE to "  My AWS  ",
                FieldId.AUTHENTICATOR_SECRET_KEY to "  JBSWY3DPEHPK3PXP  ",
            ),
        )

        coVerify(exactly = 1) { repository.upsertAuthenticatorData(any()) }
        val captured = dataSlot.captured
        assertThat(captured.id).isNull()
        assertThat(captured.title).isEqualTo("My AWS")
        assertThat(captured.secretKey).isEqualTo("JBSWY3DPEHPK3PXP")
    }

    @Test
    fun saveLayout_existingRecord_preservesCreationDate() = runTest {
        val layout = AuthenticatorLayoutImpl(
            recordId = 10,
            authenticatorDataRepository = repository,
            totpGenerator = totpGenerator,
        )
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
    fun deleteLayout_existingRecord_callsRepositoryDelete() = runTest {
        val layout = AuthenticatorLayoutImpl(
            recordId = 10,
            authenticatorDataRepository = repository,
            totpGenerator = totpGenerator,
        )

        layout.deleteLayout()

        coVerify(exactly = 1) { repository.deleteAuthenticatorDataByKey(10) }
    }

    @Test
    fun checkMandatoryFields_validSecretAndTitle_evaluatesTrue() {
        val layout = AuthenticatorLayoutImpl(
            recordId = null,
            authenticatorDataRepository = repository,
            totpGenerator = totpGenerator,
        )
        every { totpGenerator.isValidSecret("JBSWY3DPEHPK3PXP") } returns true

        val fields = listOf(
            FieldUiState(
                cell = FieldUiState.Cell(label = R.string.title, isMandatory = true),
                data = "Google",
            ),
            FieldUiState(
                cell = FieldUiState.Cell(label = R.string.secret_key, isMandatory = true),
                data = "JBSWY3DPEHPK3PXP",
            ),
        )

        val isValid = layout.checkMandatoryFields(fields)

        assertThat(isValid).isTrue()
    }

    @Test
    fun checkMandatoryFields_invalidSecret_evaluatesFalse() {
        val layout = AuthenticatorLayoutImpl(
            recordId = null,
            authenticatorDataRepository = repository,
            totpGenerator = totpGenerator,
        )
        every { totpGenerator.isValidSecret("INVALID_BASE32_189") } returns false

        val fields = listOf(
            FieldUiState(
                cell = FieldUiState.Cell(label = R.string.title, isMandatory = true),
                data = "Google",
            ),
            FieldUiState(
                cell = FieldUiState.Cell(label = R.string.secret_key, isMandatory = true),
                data = "INVALID_BASE32_189",
            ),
        )

        val isValid = layout.checkMandatoryFields(fields)

        assertThat(isValid).isFalse()
    }

    @Test
    fun checkMandatoryFields_blankTitle_evaluatesFalse() {
        val layout = AuthenticatorLayoutImpl(
            recordId = null,
            authenticatorDataRepository = repository,
            totpGenerator = totpGenerator,
        )
        every { totpGenerator.isValidSecret("JBSWY3DPEHPK3PXP") } returns true

        val fields = listOf(
            FieldUiState(
                cell = FieldUiState.Cell(label = R.string.title, isMandatory = true),
                data = "   ",
            ),
            FieldUiState(
                cell = FieldUiState.Cell(label = R.string.secret_key, isMandatory = true),
                data = "JBSWY3DPEHPK3PXP",
            ),
        )

        val isValid = layout.checkMandatoryFields(fields)

        assertThat(isValid).isFalse()
    }
}
