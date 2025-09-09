package com.andryoga.composeapp.ui.record.dynamicLayout

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
        validateTitleField(loginLayout.rows[0]!!)
        validateTitleField(bankAccountLayout.rows[0]!!)
        validateTitleField(cardLayout.rows[0]!!)
        validateTitleField(noteLayout.rows[0]!!)
    }

    @Test
    fun rowFieldWeight_addsToOne() {
        loginLayout.rows.forEach { row ->
            assert(row.value.sumOf { it.weight.toDouble() } == 1.0)
        }
        bankAccountLayout.rows.forEach { row ->
            assert(row.value.sumOf { it.weight.toDouble() } == 1.0)
        }
        cardLayout.rows.forEach { row ->
            assert(row.value.sumOf { it.weight.toDouble() } == 1.0)
        }
        noteLayout.rows.forEach { row ->
            assert(row.value.sumOf { it.weight.toDouble() } == 1.0)
        }
    }

    private fun validateTitleField(firstRow: List<Layout.Field>) {
        assert(firstRow.size == 1)
        assert(firstRow[0].weight == 1f)
        assert(firstRow[0].uiState.cell.isMandatory)
        assert(!firstRow[0].uiState.cell.isPasswordField)
        assert(firstRow[0].uiState.cell.singleLine)
    }
}