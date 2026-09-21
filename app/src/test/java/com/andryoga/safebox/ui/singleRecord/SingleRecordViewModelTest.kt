@file:OptIn(ExperimentalCoroutinesApi::class)

package com.andryoga.safebox.ui.singleRecord

import android.content.Context
import app.cash.turbine.test
import com.andryoga.safebox.MainDispatcherRule
import com.andryoga.safebox.common.AnalyticsKey
import com.andryoga.safebox.common.AnalyticsParam
import com.andryoga.safebox.domain.models.record.RecordType
import com.andryoga.safebox.test.fakes.FakeAnalyticsHelper
import com.andryoga.safebox.ui.core.ActiveSessionManager
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.LayoutFactory
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.layouts.Layout
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldId
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldUiState
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.LayoutPlan
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.ShareableField
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
    private lateinit var analyticsHelper: FakeAnalyticsHelper

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        analyticsHelper = FakeAnalyticsHelper()

        every { layoutFactory.getLayout(any(), any()) } returns layout
        coEvery { layout.getLayoutPlan() } returns LayoutPlan(fieldUiState = emptyMap())
        every { layout.checkMandatoryFields(any()) } returns false
        every { context.getString(any()) } returns "some string"
    }

    private fun initViewModel() {
        viewModel = SingleRecordViewModel(
            activeSessionManager,
            singleRecordRouteProvider,
            layoutFactory,
            context,
            mainDispatcherRule.testDispatcherProvider,
            analyticsHelper,
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
            // checkMandatoryFields says no for an empty create screen, so seeding the flag at init
            // must not hand the user a Save button for a record with nothing in it.
            assertThat(uiState.topAppBarUiState.isSaveButtonEnabled).isFalse()
        }
    }

    @Test
    fun initialState_whenLayoutOpensAlreadyComplete_enablesSaveWithoutAnEdit() = runTest {
        // how a scanned QR code arrives: the layout prefills title and seed, so the user never
        // types anything and OnCellValueUpdate, the only other place Save is recomputed, never
        // fires. Save has to be usable from the first frame.
        every { layout.checkMandatoryFields(any()) } returns true
        every { singleRecordRouteProvider.getRoute() } returns
            SingleRecordScreenRoute(RecordType.AUTHENTICATOR)
        initViewModel()

        viewModel.uiState.test {
            awaitItem()
            advanceUntilIdle()

            val uiState = expectMostRecentItem()
            assertThat(uiState.viewMode).isEqualTo(ViewMode.NEW)
            assertThat(uiState.topAppBarUiState.isSaveButtonEnabled).isTrue()
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

            viewModel.onAction(SingleRecordScreenAction.OnEditClicked)
            advanceUntilIdle()

            val uiState = expectMostRecentItem()
            assertThat(uiState.viewMode).isEqualTo(ViewMode.EDIT)
            assertThat(uiState.topAppBarUiState.isSaveButtonVisible).isTrue()
        }
    }

    @Test
    fun `handleShareRecord emits shareContentEvent with layout shareable fields and app link`() =
        runTest {
            coEvery { layout.getShareableFields() } returns listOf(
                ShareableField(label = 101, value = "copy me"),
                ShareableField(label = 102, value = "copy me too"),
            )
            every { context.getString(101) } returns "First"
            every { context.getString(102) } returns "Second"
            every { context.getString(any(), any()) } returns "some app link"
            every { singleRecordRouteProvider.getRoute() } returns SingleRecordScreenRoute(
                RecordType.LOGIN,
                1,
            )

            initViewModel()

            viewModel.shareContentEvent.test {
                viewModel.onAction(SingleRecordScreenAction.OnShareClicked)
                advanceUntilIdle()

                val emittedEvent = awaitItem()
                assertThat(emittedEvent).isNotNull()
                assertThat(emittedEvent).contains("First : copy me")
                assertThat(emittedEvent).contains("Second : copy me too")
                assertThat(emittedEvent).contains("some app link")
            }
        }

    @Test
    fun `initial state is correct for existing authenticator record`() = runTest {
        every { singleRecordRouteProvider.getRoute() } returns SingleRecordScreenRoute(
            RecordType.AUTHENTICATOR,
            1,
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
    fun `handleShareRecord shares authenticator code from layout without exposing secret`() =
        runTest {
            coEvery { layout.getShareableFields() } returns listOf(
                ShareableField(label = 101, value = "Google Authenticator"),
                ShareableField(label = 102, value = "654321"),
            )
            every { context.getString(101) } returns "Title"
            every { context.getString(102) } returns "One-time code"
            every { context.getString(any(), any()) } returns "some app link"
            every { singleRecordRouteProvider.getRoute() } returns SingleRecordScreenRoute(
                RecordType.AUTHENTICATOR,
                1,
            )

            initViewModel()

            viewModel.shareContentEvent.test {
                viewModel.onAction(SingleRecordScreenAction.OnShareClicked)
                advanceUntilIdle()

                val emittedEvent = awaitItem()
                assertThat(emittedEvent).isNotNull()
                assertThat(emittedEvent).contains("Title : Google Authenticator")
                assertThat(emittedEvent).contains("One-time code : 654321")
                assertThat(emittedEvent).doesNotContain("JBSWY3DPEHPK3PXP")
                assertThat(emittedEvent).contains("some app link")
            }
        }

    @Test
    fun onCopyTotpCode_shouldLogCopyClickWithRecordDetailSource() {
        every { singleRecordRouteProvider.getRoute() } returns SingleRecordScreenRoute(
            RecordType.AUTHENTICATOR,
            1,
        )
        initViewModel()

        viewModel.onAction(SingleRecordScreenAction.OnCopyTotpCode)

        val event = analyticsHelper.loggedEvents
            .first { it.key == AnalyticsKey.AUTHENTICATOR_COPY_CLICK }
        assertThat(event.params[AnalyticsParam.SOURCE.paramName]).isEqualTo("record_detail")
    }
}