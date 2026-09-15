package com.andryoga.safebox.ui.singleRecord.dynamicLayout.layouts

import com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldId
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldUiState
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.LayoutPlan
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Verifies the default behaviour supplied by the [Layout] interface itself.
 *
 * These defaults are shared by every record type, so they are tested once here against a minimal
 * fake implementation instead of being re-asserted in each concrete layout's test suite.
 */
class LayoutDefaultsTest {

    /**
     * Bare bones [Layout] that only supplies a fixed [LayoutPlan] so that the interface default
     * implementations can be exercised in isolation.
     */
    private class FakeLayout(private val plan: LayoutPlan) : Layout {
        override suspend fun getLayoutPlan(): LayoutPlan = plan
        override suspend fun saveLayout(data: Map<FieldId, String>) = Unit
        override suspend fun deleteLayout() = Unit
    }

    @Test
    fun getShareableFields_returnsOnlyCopyableNonPasswordNonEmptyFields() = runTest {
        val layout = FakeLayout(
            LayoutPlan(
                fieldUiState = mapOf(
                    FieldId.LOGIN_TITLE to FieldUiState(
                        cell = FieldUiState.Cell(label = 101, isCopyable = true),
                        data = "copy me",
                    ),
                    FieldId.LOGIN_PASSWORD to FieldUiState(
                        cell = FieldUiState.Cell(
                            label = 102,
                            isCopyable = true,
                            isPasswordField = true,
                        ),
                        data = "secret",
                    ),
                    FieldId.LOGIN_NOTES to FieldUiState(
                        cell = FieldUiState.Cell(label = 103, isCopyable = false),
                        data = "don't copy me",
                    ),
                    FieldId.LOGIN_URL to FieldUiState(
                        cell = FieldUiState.Cell(label = 104, isCopyable = true),
                        data = "",
                    ),
                ),
            ),
        )

        val shareableFields = layout.getShareableFields()

        assertThat(shareableFields).hasSize(1)
        assertThat(shareableFields.first().label).isEqualTo(101)
        assertThat(shareableFields.first().value).isEqualTo("copy me")
    }
}
