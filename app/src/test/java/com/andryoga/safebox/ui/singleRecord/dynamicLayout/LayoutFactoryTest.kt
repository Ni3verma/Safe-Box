package com.andryoga.safebox.ui.singleRecord.dynamicLayout

import com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldId
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldUiState
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.LayoutPlan
import org.junit.Before
import org.junit.Test

class LayoutFactoryTest {
    lateinit var loginLayoutPlan: LayoutPlan
    lateinit var bankAccountLayoutPlan: LayoutPlan
    lateinit var cardLayoutPlan: LayoutPlan
    lateinit var noteLayoutPlan: LayoutPlan

    @Before
    fun setUp() {
//        loginLayoutPlan = LayoutFactory.getLoginRecordLayout()
//        cardLayoutPlan = LayoutFactory.getCardRecordLayout()
//        noteLayoutPlan = LayoutFactory.getNoteRecordLayout()
//        bankAccountLayoutPlan = LayoutFactory.getBankAccountRecordLayout()

    }

    @Test
    fun eachRecordLayout_hasUniqueLayoutId() {
        assert(loginLayoutPlan.id == LayoutId.LOGIN)
        assert(bankAccountLayoutPlan.id == LayoutId.BANK_ACCOUNT)
        assert(cardLayoutPlan.id == LayoutId.CARD)
        assert(noteLayoutPlan.id == LayoutId.NOTE)
    }

    @Test
    fun titleValidations_allLayouts() {
        validateTitleField(loginLayoutPlan.fieldUiState[FieldId.LOGIN_TITLE]!!)
        validateTitleField(bankAccountLayoutPlan.fieldUiState[FieldId.BANK_ACCOUNT_TITLE]!!)
        validateTitleField(cardLayoutPlan.fieldUiState[FieldId.CARD_TITLE]!!)
        validateTitleField(noteLayoutPlan.fieldUiState[FieldId.NOTE_TITLE]!!)
    }

    private fun validateTitleField(field: FieldUiState) {
        assert(field.cell.isMandatory)
        assert(!field.cell.isPasswordField)
        assert(field.cell.singleLine)
    }
}