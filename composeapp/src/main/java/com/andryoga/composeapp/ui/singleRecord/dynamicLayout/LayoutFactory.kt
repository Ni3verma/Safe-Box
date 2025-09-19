package com.andryoga.composeapp.ui.singleRecord.dynamicLayout

import com.andryoga.composeapp.data.repository.BankAccountDataRepositoryImpl
import com.andryoga.composeapp.data.repository.BankCardDataRepositoryImpl
import com.andryoga.composeapp.data.repository.LoginDataRepositoryImpl
import com.andryoga.composeapp.data.repository.SecureNoteDataRepositoryImpl
import com.andryoga.composeapp.ui.core.models.RecordType
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.layouts.BankAccountLayoutImpl
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.layouts.BankCardLayoutImpl
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.layouts.Layout
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.layouts.LoginLayoutImpl
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.layouts.NoteLayoutImpl
import dagger.Lazy
import javax.inject.Inject

class LayoutFactory @Inject constructor(
    private val loginDataRepository: Lazy<LoginDataRepositoryImpl>,
    private val bankAccountDataRepository: Lazy<BankAccountDataRepositoryImpl>,
    private val bankCardDataRepository: Lazy<BankCardDataRepositoryImpl>,
    private val noteDataRepository: Lazy<SecureNoteDataRepositoryImpl>
) {
    fun getLayout(recordType: RecordType): Layout {
        return when (recordType) {
            RecordType.LOGIN -> LoginLayoutImpl(loginDataRepository.get())
            RecordType.CARD -> BankCardLayoutImpl(bankAccountDataRepository.get())
            RecordType.BANK_ACCOUNT -> BankAccountLayoutImpl(bankCardDataRepository.get())
            RecordType.NOTE -> NoteLayoutImpl(noteDataRepository.get())
        }
    }
}
