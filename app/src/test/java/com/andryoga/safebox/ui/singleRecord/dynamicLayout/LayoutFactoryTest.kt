package com.andryoga.safebox.ui.singleRecord.dynamicLayout

import com.andryoga.safebox.data.repository.interfaces.BankAccountDataRepository
import com.andryoga.safebox.data.repository.interfaces.BankCardDataRepository
import com.andryoga.safebox.data.repository.interfaces.LoginDataRepository
import com.andryoga.safebox.data.repository.interfaces.SecureNoteDataRepository
import com.andryoga.safebox.domain.models.record.RecordType
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.layouts.BankAccountLayoutImpl
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.layouts.BankCardLayoutImpl
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.layouts.LoginLayoutImpl
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.layouts.NoteLayoutImpl
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldUiState
import com.google.common.truth.Truth.assertThat
import dagger.Lazy
import io.mockk.every
import io.mockk.mockk
import org.junit.Before
import org.junit.Test

class LayoutFactoryTest {

    private val loginRepo: LoginDataRepository = mockk(relaxed = true)
    private val bankAccountRepo: BankAccountDataRepository = mockk(relaxed = true)
    private val bankCardRepo: BankCardDataRepository = mockk(relaxed = true)
    private val noteRepo: SecureNoteDataRepository = mockk(relaxed = true)

    private val lazyLoginRepo: Lazy<LoginDataRepository> = mockk()
    private val lazyBankAccountRepo: Lazy<BankAccountDataRepository> = mockk()
    private val lazyBankCardRepo: Lazy<BankCardDataRepository> = mockk()
    private val lazyNoteRepo: Lazy<SecureNoteDataRepository> = mockk()

    private lateinit var layoutFactory: LayoutFactory

    @Before
    fun setUp() {
        every { lazyLoginRepo.get() } returns loginRepo
        every { lazyBankAccountRepo.get() } returns bankAccountRepo
        every { lazyBankCardRepo.get() } returns bankCardRepo
        every { lazyNoteRepo.get() } returns noteRepo

        layoutFactory = LayoutFactory(
            loginDataRepository = lazyLoginRepo,
            bankAccountDataRepository = lazyBankAccountRepo,
            bankCardDataRepository = lazyBankCardRepo,
            noteDataRepository = lazyNoteRepo
        )
    }

    @Test
    fun getLayout_withLoginRecordType_returnsLoginLayoutImpl() {
        val layout = layoutFactory.getLayout(101, RecordType.LOGIN)

        assertThat(layout).isInstanceOf(LoginLayoutImpl::class.java)
    }

    @Test
    fun getLayout_withCardRecordType_returnsBankCardLayoutImpl() {
        val layout = layoutFactory.getLayout(202, RecordType.CARD)

        assertThat(layout).isInstanceOf(BankCardLayoutImpl::class.java)
    }

    @Test
    fun getLayout_withBankAccountRecordType_returnsBankAccountLayoutImpl() {
        val layout = layoutFactory.getLayout(303, RecordType.BANK_ACCOUNT)

        assertThat(layout).isInstanceOf(BankAccountLayoutImpl::class.java)
    }

    @Test
    fun getLayout_withNoteRecordType_returnsNoteLayoutImpl() {
        val layout = layoutFactory.getLayout(404, RecordType.NOTE)

        assertThat(layout).isInstanceOf(NoteLayoutImpl::class.java)
    }

    @Test
    fun checkMandatoryFields_allMandatoryFieldsFilled_evaluatesTrue() {
        val layout = layoutFactory.getLayout(null, RecordType.NOTE)

        val fields = listOf(
            FieldUiState(cell = FieldUiState.Cell(isMandatory = true), data = "Title Value"),
            FieldUiState(cell = FieldUiState.Cell(isMandatory = false), data = "")
        )

        val isValid = layout.checkMandatoryFields(fields)

        assertThat(isValid).isTrue()
    }

    @Test
    fun checkMandatoryFields_missingMandatoryField_evaluatesFalse() {
        val layout = layoutFactory.getLayout(null, RecordType.NOTE)

        val fields = listOf(
            FieldUiState(cell = FieldUiState.Cell(isMandatory = true), data = "   "),
            FieldUiState(cell = FieldUiState.Cell(isMandatory = false), data = "Optional Value")
        )

        val isValid = layout.checkMandatoryFields(fields)

        assertThat(isValid).isFalse()
    }
}