package com.andryoga.composeapp.ui.singleRecord.dynamicLayout

import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.models.FieldId
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.models.FieldUiState
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.models.Layout
import org.junit.Before
import org.junit.Test


class LayoutFactoryTest {
    lateinit var loginLayout: Layout
    lateinit var bankAccountLayout: Layout
    lateinit var cardLayout: Layout
    lateinit var noteLayout: Layout

    @Before
    fun setUp() {
        loginLayout = LayoutFactory.getLoginRecordLayout()
        cardLayout = LayoutFactory.getCardRecordLayout()
        noteLayout = LayoutFactory.getNoteRecordLayout()
        bankAccountLayout = LayoutFactory.getBankAccountRecordLayout()

    }

    @Test
    fun eachRecordLayout_hasUniqueLayoutId() {
        assert(loginLayout.id == LayoutId.LOGIN)
        assert(bankAccountLayout.id == LayoutId.BANK_ACCOUNT)
        assert(cardLayout.id == LayoutId.CARD)
        assert(noteLayout.id == LayoutId.NOTE)
    }

    @Test
    fun titleValidations_allLayouts() {
        validateTitleField(loginLayout.fieldUiState[FieldId.LOGIN_TITLE]!!)
        validateTitleField(bankAccountLayout.fieldUiState[FieldId.BANK_ACCOUNT_TITLE]!!)
        validateTitleField(cardLayout.fieldUiState[FieldId.CARD_TITLE]!!)
        validateTitleField(noteLayout.fieldUiState[FieldId.NOTE_TITLE]!!)
    }

    @Test
    fun rowFieldWeight_addsToOne() {
        loginLayout.arrangement.forEach { row ->
            assert(row.sumOf { it.weight.toDouble() } == 1.0)
        }
        bankAccountLayout.arrangement.forEach { row ->
            assert(row.sumOf { it.weight.toDouble() } == 1.0)
        }
        cardLayout.arrangement.forEach { row ->
            assert(row.sumOf { it.weight.toDouble() } == 1.0)
        }
        noteLayout.arrangement.forEach { row ->
            assert(row.sumOf { it.weight.toDouble() } == 1.0)
        }
    }

    private fun validateTitleField(field: FieldUiState) {
        assert(field.cell.isMandatory)
        assert(!field.cell.isPasswordField)
        assert(field.cell.singleLine)
    }
}