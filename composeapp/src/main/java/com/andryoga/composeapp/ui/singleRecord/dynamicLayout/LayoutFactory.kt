package com.andryoga.composeapp.ui.singleRecord.dynamicLayout

import com.andryoga.composeapp.data.repository.BankAccountDataRepositoryImpl
import com.andryoga.composeapp.data.repository.BankCardDataRepositoryImpl
import com.andryoga.composeapp.data.repository.LoginDataRepositoryImpl
import com.andryoga.composeapp.data.repository.SecureNoteDataRepositoryImpl
import com.andryoga.composeapp.domain.models.record.RecordType
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
    /**
     * Returns the layout for the given record type. Data is pre filled if recordId is passed as well
     *
     * @param recordId: id of the record. If this is passed then data for the record will be
     * pre-filled in the Layout. This is passed when user clicks on a record in the list
     * and record screen should be pre filled with it's data.
     *
     * @param recordType: type of the record
     *
     * @return Layout for the given record type with pre-filled data if recordId is also passed.
     * */
    fun getLayout(recordId: Int?, recordType: RecordType): Layout {
        return when (recordType) {
            RecordType.LOGIN -> LoginLayoutImpl(recordId, loginDataRepository.get())
            RecordType.CARD -> BankCardLayoutImpl(recordId, bankCardDataRepository.get())
            RecordType.BANK_ACCOUNT -> BankAccountLayoutImpl(
                recordId,
                bankAccountDataRepository.get()
            )
            RecordType.NOTE -> NoteLayoutImpl(recordId, noteDataRepository.get())
        }
    }
}
