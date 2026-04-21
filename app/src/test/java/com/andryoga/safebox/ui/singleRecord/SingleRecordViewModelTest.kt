@file:OptIn(ExperimentalCoroutinesApi::class)

package com.andryoga.safebox.ui.singleRecord

import android.content.Context
import com.andryoga.safebox.MainDispatcherRule
import com.andryoga.safebox.common.DispatchersProvider
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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
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

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: SingleRecordViewModel
    private lateinit var dispatchersProvider: DispatchersProvider
    private var job: Job? = null


    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        dispatchersProvider = object : DispatchersProvider {
            override val main: CoroutineDispatcher
                get() = testDispatcher
            override val default: CoroutineDispatcher
                get() = testDispatcher
            override val io: CoroutineDispatcher
                get() = testDispatcher
        }

        every { layoutFactory.getLayout(any(), any()) } returns layout
        coEvery { layout.getLayoutPlan() } returns LayoutPlan(fieldUiState = emptyMap())
        every { context.getString(any()) } returns "some string"
    }

    @After
    fun tearDown() {
        job?.cancel()
    }

    private fun initViewModel() {
        viewModel = SingleRecordViewModel(
            activeSessionManager,
            singleRecordRouteProvider,
            layoutFactory,
            context,
            dispatchersProvider
        )
    }

    @Test
    fun `initial state is correct for existing record`() = runTest {
        every { singleRecordRouteProvider.getRoute() } returns SingleRecordScreenRoute(
            RecordType.LOGIN,
            1
        )
        initViewModel()
        runCurrent()
        val uiState = viewModel.uiState.value
        assertThat(uiState.isLoading).isFalse()
        assertThat(uiState.viewMode).isEqualTo(ViewMode.VIEW)
        assertThat(uiState.topAppBarUiState.isSaveButtonVisible).isFalse()
    }

    @Test
    fun `initial state is correct for new record`() = runTest {
        every { singleRecordRouteProvider.getRoute() } returns SingleRecordScreenRoute(RecordType.LOGIN)
        initViewModel()
        runCurrent()
        val uiState = viewModel.uiState.value
        assertThat(uiState.isLoading).isFalse()
        assertThat(uiState.viewMode).isEqualTo(ViewMode.NEW)
        assertThat(uiState.topAppBarUiState.isSaveButtonVisible).isTrue()
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

        // 3. Act: Run coroutines and perform action
        runCurrent() // for init
        viewModel.onAction(
            SingleRecordScreenAction.OnCellValueUpdate(
                FieldId.LOGIN_TITLE,
                "new data"
            )
        )
        runCurrent()

        // 4. Assert: Check the results
        val uiState = viewModel.uiState.value
        assertThat(uiState.layoutPlan.fieldUiState[FieldId.LOGIN_TITLE]?.data).isEqualTo("new data")
        assertThat(uiState.topAppBarUiState.isSaveButtonEnabled).isTrue()
    }

    @Test
    fun `OnSaveClicked calls saveLayout and emits screenCloseEvent`() = runTest {
        every { singleRecordRouteProvider.getRoute() } returns SingleRecordScreenRoute(RecordType.LOGIN)
        initViewModel()
        coEvery { layout.saveLayout(any()) } just runs
        job = launch {
            val event = viewModel.screenCloseEvent.first()
            assertThat(event).isEqualTo(Unit)
        }
        runCurrent()

        viewModel.onAction(SingleRecordScreenAction.OnSaveClicked)
        runCurrent()

        coVerify { layout.saveLayout(any()) }
    }

    @Test
    fun `OnDeleteClicked calls deleteLayout and emits screenCloseEvent`() = runTest {
        every { singleRecordRouteProvider.getRoute() } returns SingleRecordScreenRoute(
            RecordType.LOGIN,
            1
        )
        initViewModel()
        coEvery { layout.deleteLayout() } just runs
        job = launch {
            val event = viewModel.screenCloseEvent.first()
            assertThat(event).isEqualTo(Unit)
        }
        runCurrent()

        viewModel.onAction(SingleRecordScreenAction.OnDeleteClicked)
        runCurrent()

        coVerify { layout.deleteLayout() }
    }

    @Test
    fun `OnEditClicked updates viewMode and save button visibility`() = runTest {
        every { singleRecordRouteProvider.getRoute() } returns SingleRecordScreenRoute(
            RecordType.LOGIN,
            1
        )
        initViewModel()
        runCurrent()

        viewModel.onAction(SingleRecordScreenAction.OnEditClicked)
        runCurrent()

        val uiState = viewModel.uiState.value
        assertThat(uiState.viewMode).isEqualTo(ViewMode.EDIT)
        assertThat(uiState.topAppBarUiState.isSaveButtonVisible).isTrue()
    }

    @Test
    fun `onBackClick from EDIT mode goes to VIEW mode`() = runTest {
        every { singleRecordRouteProvider.getRoute() } returns SingleRecordScreenRoute(
            RecordType.LOGIN,
            1
        )
        initViewModel()
        runCurrent() // init
        viewModel.onAction(SingleRecordScreenAction.OnEditClicked)
        runCurrent() // switch to edit mode

        viewModel.onBackClick()
        runCurrent()

        val uiState = viewModel.uiState.value
        assertThat(uiState.viewMode).isEqualTo(ViewMode.VIEW)
    }

    @Test
    fun `onBackClick from VIEW mode emits screenCloseEvent`() = runTest {
        every { singleRecordRouteProvider.getRoute() } returns SingleRecordScreenRoute(
            RecordType.LOGIN,
            1
        )
        initViewModel()
        job = launch {
            val event = viewModel.screenCloseEvent.first()
            assertThat(event).isEqualTo(Unit)
        }
        runCurrent()

        viewModel.onBackClick()
        runCurrent()
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
            var emittedEvent: String? = null
            job = launch {
                emittedEvent = viewModel.shareContentEvent.first()
            }

            // 3. Act: Run coroutines and perform action
            runCurrent()
            viewModel.onAction(SingleRecordScreenAction.OnShareClicked)
            runCurrent()

            // 4. Assert: Check that only the correct field was included
            assertThat(emittedEvent).isNotNull()
            assertThat(emittedEvent).contains("Copyable : copy me")
            assertThat(emittedEvent).doesNotContain("secret")
            assertThat(emittedEvent).doesNotContain("don't copy me")
            assertThat(emittedEvent).contains("some app link")
        }
}