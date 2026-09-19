package com.andryoga.safebox.ui.singleRecord.dynamicLayout.models

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for [FieldUiState.isVisibleIn], the single predicate that decides both whether a
 * field renders and how much row width it is given.
 */
class FieldUiStateTest {

    @Test
    fun isVisibleIn_defaultCellWithData_isVisibleInEveryMode() {
        val field = FieldUiState(cell = FieldUiState.Cell(), data = "some value")

        ViewMode.entries.forEach { viewMode ->
            assertThat(field.isVisibleIn(viewMode)).isTrue()
        }
    }

    @Test
    fun isVisibleIn_blankDataInViewMode_isHidden() {
        val field = FieldUiState(cell = FieldUiState.Cell(), data = "   ")

        assertThat(field.isVisibleIn(ViewMode.VIEW)).isFalse()
    }

    @Test
    fun isVisibleIn_blankDataInEditAndNewModes_isVisible() {
        val field = FieldUiState(cell = FieldUiState.Cell(), data = "")

        assertThat(field.isVisibleIn(ViewMode.EDIT)).isTrue()
        assertThat(field.isVisibleIn(ViewMode.NEW)).isTrue()
    }

    @Test
    fun isVisibleIn_viewOnlyCell_isHiddenOutsideViewMode() {
        val field = FieldUiState(
            cell = FieldUiState.Cell(visibleIn = setOf(ViewMode.VIEW)),
            data = "01 Jan 2026",
        )

        assertThat(field.isVisibleIn(ViewMode.VIEW)).isTrue()
        assertThat(field.isVisibleIn(ViewMode.EDIT)).isFalse()
        assertThat(field.isVisibleIn(ViewMode.NEW)).isFalse()
    }

    @Test
    fun isVisibleIn_createOnlyCellWithData_isHiddenInViewAndEditModes() {
        val field = FieldUiState(
            cell = FieldUiState.Cell(visibleIn = setOf(ViewMode.NEW)),
            data = "JBSWY3DPEHPK3PXP",
        )

        assertThat(field.isVisibleIn(ViewMode.NEW)).isTrue()
        assertThat(field.isVisibleIn(ViewMode.VIEW)).isFalse()
        assertThat(field.isVisibleIn(ViewMode.EDIT)).isFalse()
    }
}
