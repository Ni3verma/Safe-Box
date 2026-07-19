@file:OptIn(ExperimentalCoroutinesApi::class)

package com.andryoga.safebox.ui.singleRecord

import android.content.Context
import app.cash.turbine.test
import com.andryoga.safebox.MainDispatcherRule
import com.andryoga.safebox.domain.models.record.RecordType
import com.andryoga.safebox.ui.core.ActiveSessionManager
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.LayoutFactory
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.layouts.Layout
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldId
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldUiState
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.LayoutPlan
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.ViewMode
import com.google.common.truth.Truth.assertThat
import dagger.Lazy
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.spyk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SingleRecordViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @MockK
    lateinit var context: Context

    @MockK
    lateinit var layoutFactory: LayoutFactory

    @MockK
    lateinit var activeSessionManager: Lazy<ActiveSessionManager>

    @MockK
    lateinit var layout: Layout

    @MockK
    lateinit var singleRecordRouteProvider: SingleRecordRouteProvider

    private lateinit var viewModel: SingleRecordViewModel


    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        every { layoutFactory.getLayout(any(), any()) } returns layout
        coEvery { layout.getLayoutPlan() } returns LayoutPlan(fieldUiState = emptyMap())
        every { context.getString(any()) } returns "some string"
    }

    private fun initViewModel() {
        viewModel = SingleRecordViewModel(
            activeSessionManager,
            singleRecordRouteProvider,
            layoutFactory,
            context,
            mainDispatcherRule.testDispatcherProvider
        )
    }

    @Test
    fun `initial state is correct for existing record`() = runTest {
        every { singleRecordRouteProvider.getRoute() } returns SingleRecordScreenRoute(
            RecordType.LOGIN,
            1
        )
        initViewModel()
        viewModel.uiState.test {
            awaitItem() // initial state
            advanceUntilIdle()
            val uiState = expectMostRecentItem()
            assertThat(uiState.isLoading).isFalse()
            assertThat(uiState.viewMode).isEqualTo(ViewMode.VIEW)
            assertThat(uiState.topAppBarUiState.isSaveButtonVisible).isFalse()
        }
    }

    @Test
    fun `initial state is correct for new record`() = runTest {
        every { singleRecordRouteProvider.getRoute() } returns SingleRecordScreenRoute(RecordType.LOGIN)
        initViewModel()
        viewModel.uiState.test {
            awaitItem() // initial state
            advanceUntilIdle()
            val uiState = expectMostRecentItem()
            assertThat(uiState.isLoading).isFalse()
            assertThat(uiState.viewMode).isEqualTo(ViewMode.NEW)
            assertThat(uiState.topAppBarUiState.isSaveButtonVisible).isTrue()
        }
    }

    @Test
    fun `OnCellValueUpdate updates uiState and enables save button`() = runTest {
        // 1. Arrange: Set up test-specific mocks first
        val layoutPlan = LayoutPlan(
            fieldUiState = mapOf(
                FieldId.LOGIN_TITLE to FieldUiState(
                    cell = mockk(relaxed = true),
                    data = "initial data"
                )
            )
        )
        coEvery { layout.getLayoutPlan() } returns layoutPlan
        every { layout.checkMandatoryFields(any()) } returns true
        every { singleRecordRouteProvider.getRoute() } returns SingleRecordScreenRoute(RecordType.LOGIN)

        // 2. Arrange: Initialize the ViewModel AFTER mocks are set
        initViewModel()

        // 3. Act & Assert with Turbine
        viewModel.uiState.test {
            awaitItem() // initial state
            advanceUntilIdle()
            awaitItem() // post-init state

            viewModel.onAction(
                SingleRecordScreenAction.OnCellValueUpdate(
                    FieldId.LOGIN_TITLE,
                    "new data"
                )
            )
            advanceUntilIdle()

            val uiState = expectMostRecentItem()
            assertThat(uiState.layoutPlan.fieldUiState[FieldId.LOGIN_TITLE]?.data).isEqualTo("new data")
            assertThat(uiState.topAppBarUiState.isSaveButtonEnabled).isTrue()
        }
    }

    @Test
    fun `OnSaveClicked calls saveLayout and emits screenCloseEvent`() = runTest {
        every { singleRecordRouteProvider.getRoute() } returns SingleRecordScreenRoute(RecordType.LOGIN)
        initViewModel()
        coEvery { layout.saveLayout(any()) } just runs

        viewModel.screenCloseEvent.test {
            viewModel.onAction(SingleRecordScreenAction.OnSaveClicked)
            advanceUntilIdle()

            assertThat(awaitItem()).isEqualTo(Unit)
            coVerify { layout.saveLayout(any()) }
        }
    }

    @Test
    fun `OnDeleteClicked calls deleteLayout and emits screenCloseEvent`() = runTest {
        every { singleRecordRouteProvider.getRoute() } returns SingleRecordScreenRoute(
            RecordType.LOGIN,
            1
        )
        initViewModel()
        coEvery { layout.deleteLayout() } just runs

        viewModel.screenCloseEvent.test {
            viewModel.onAction(SingleRecordScreenAction.OnDeleteClicked)
            advanceUntilIdle()

            assertThat(awaitItem()).isEqualTo(Unit)
            coVerify { layout.deleteLayout() }
        }
    }

    @Test
    fun `OnEditClicked updates viewMode and save button visibility`() = runTest {
        every { singleRecordRouteProvider.getRoute() } returns SingleRecordScreenRoute(
            RecordType.LOGIN,
            1
        )
        initViewModel()

        viewModel.uiState.test {
            awaitItem() // initial state
            advanceUntilIdle()
            awaitItem() // post-init state

            viewModel.onAction(SingleRecordScreenAction.OnEditClicked)
            advanceUntilIdle()

            val uiState = expectMostRecentItem()
            assertThat(uiState.viewMode).isEqualTo(ViewMode.EDIT)
            assertThat(uiState.topAppBarUiState.isSaveButtonVisible).isTrue()
        }
    }

    @Test
    fun `handleShareRecord emits shareContentEvent with only copyable non-password and non-empty fields`() =
        runTest {
            // 1. Arrange: Set up a mix of different field types
            val copyableField = spyk(
                FieldUiState(
                    cell = mockk(relaxed = true) {
                        every { isCopyable } returns true
                        every { isPasswordField } returns false
                        every { label } returns 101
                    },
                    data = "copy me"
                )
            )
            every { copyableField.getFormattedData() } returns "copy me"

            val passwordField = spyk(
                FieldUiState(
                    cell = mockk(relaxed = true) {
                        every { isCopyable } returns true
                        every { isPasswordField } returns true
                        every { label } returns 102
                    },
                    data = "secret"
                )
            )
            every { passwordField.getFormattedData() } returns "secret"

            val notCopyableField = spyk(
                FieldUiState(
                    cell = mockk(relaxed = true) {
                        every { isCopyable } returns false
                        every { isPasswordField } returns false
                        every { label } returns 103
                    },
                    data = "don't copy me"
                )
            )
            every { notCopyableField.getFormattedData() } returns "don't copy me"

            val emptyField = spyk(
                FieldUiState(
                    cell = mockk(relaxed = true) {
                        every { isCopyable } returns true
                        every { isPasswordField } returns false
                        every { label } returns 104
                    },
                    data = ""
                )
            )
            every { emptyField.getFormattedData() } returns ""


            val layoutPlan = LayoutPlan(
                fieldUiState = mapOf(
                    FieldId.LOGIN_TITLE to copyableField,
                    FieldId.LOGIN_PASSWORD to passwordField,
                    FieldId.LOGIN_NOTES to notCopyableField,
                    FieldId.LOGIN_URL to emptyField
                )
            )
            coEvery { layout.getLayoutPlan() } returns layoutPlan
            every { context.getString(101) } returns "Copyable"
            every { context.getString(any(), any()) } returns "some app link"
            every { singleRecordRouteProvider.getRoute() } returns SingleRecordScreenRoute(
                RecordType.LOGIN,
                1
            )


            // 2. Arrange: Initialize the ViewModel AFTER mocks are set
            initViewModel()

            // 3. Act & Assert with Turbine
            viewModel.shareContentEvent.test {
                viewModel.onAction(SingleRecordScreenAction.OnShareClicked)
                advanceUntilIdle()

                val emittedEvent = awaitItem()
                assertThat(emittedEvent).isNotNull()
                assertThat(emittedEvent).contains("Copyable : copy me")
                assertThat(emittedEvent).doesNotContain("secret")
                assertThat(emittedEvent).doesNotContain("don't copy me")
                assertThat(emittedEvent).contains("some app link")
            }
        }
}