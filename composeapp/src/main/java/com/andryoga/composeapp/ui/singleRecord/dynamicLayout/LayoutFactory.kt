package com.andryoga.composeapp.ui.singleRecord.dynamicLayout

import com.andryoga.composeapp.ui.core.models.RecordType
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.layouts.BankAccountLayoutImpl
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.layouts.BankCardLayoutImpl
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.layouts.Layout
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.layouts.LoginLayoutImpl
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.layouts.NoteLayoutImpl

object LayoutFactory {
    fun getLayout(recordType: RecordType): Layout {
        return when (recordType) {
            RecordType.LOGIN -> LoginLayoutImpl()
            RecordType.CARD -> BankCardLayoutImpl()
            RecordType.BANK_ACCOUNT -> BankAccountLayoutImpl()
            RecordType.NOTE -> NoteLayoutImpl()
        }
    }
}
