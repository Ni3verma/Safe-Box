@file:OptIn(ExperimentalCoroutinesApi::class)

package com.andryoga.safebox.ui.home.records

import com.andryoga.safebox.MainDispatcherRule
import com.andryoga.safebox.analytics.AnalyticsHelper
import com.andryoga.safebox.analytics.AnalyticsParamsBuilder
import com.andryoga.safebox.common.AnalyticsKey
import com.andryoga.safebox.common.AnalyticsParam
import com.andryoga.safebox.common.CommonConstants
import com.andryoga.safebox.common.DispatchersProvider
import com.andryoga.safebox.common.Utils
import com.andryoga.safebox.data.db.docs.SearchBankAccountData
import com.andryoga.safebox.data.db.docs.SearchBankCardData
import com.andryoga.safebox.data.db.docs.SearchLoginData
import com.andryoga.safebox.data.db.docs.SearchSecureNoteData
import com.andryoga.safebox.data.repository.interfaces.BackupMetadataRepository
import com.andryoga.safebox.data.repository.interfaces.BankAccountDataRepository
import com.andryoga.safebox.data.repository.interfaces.BankCardDataRepository
import com.andryoga.safebox.data.repository.interfaces.LoginDataRepository
import com.andryoga.safebox.data.repository.interfaces.SecureNoteDataRepository
import com.andryoga.safebox.domain.models.record.RecordListItem
import com.andryoga.safebox.domain.models.record.RecordType
import com.andryoga.safebox.providers.interfaces.PreferenceProvider
import com.andryoga.safebox.ui.core.InAppReviewManager
import com.andryoga.safebox.ui.home.records.RecordsViewModel.Companion.ASK_FOR_REVIEW_AFTER_EVERY_LOGIN
import com.andryoga.safebox.ui.home.records.models.NotificationPermissionState
import com.google.common.truth.Truth.assertThat
import dagger.Lazy
import io.mockk.MockKAnnotations
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Date

class RecordsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @MockK
    lateinit var bankAccountDataRepository: BankAccountDataRepository

    @MockK
    lateinit var secureNoteDataRepository: SecureNoteDataRepository

    @MockK
    lateinit var loginDataRepository: LoginDataRepository

    @MockK
    lateinit var cardDataRepository: BankCardDataRepository

    @MockK
    lateinit var backupMetadataRepository: BackupMetadataRepository

    @MockK
    lateinit var preferenceProvider: PreferenceProvider

    @MockK
    lateinit var inAppReviewManager: Lazy<InAppReviewManager>

    @MockK
    lateinit var analyticsHelper: AnalyticsHelper

    private lateinit var viewModel: RecordsViewModel
    private var job: Job? = null
    private val bankAccountDataFlow = MutableSharedFlow<List<SearchBankAccountData>>(replay = 1)
    private val secureNoteDataFlow = MutableSharedFlow<List<SearchSecureNoteData>>(replay = 1)
    private val loginDataFlow = MutableSharedFlow<List<SearchLoginData>>(replay = 1)
    private val cardDataFlow = MutableSharedFlow<List<SearchBankCardData>>(replay = 1)
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        val dispatchersProvider = object : DispatchersProvider {
            override val main: CoroutineDispatcher
                get() = testDispatcher
            override val default: CoroutineDispatcher
                get() = testDispatcher
            override val io: CoroutineDispatcher
                get() = testDispatcher
        }

        every { bankAccountDataRepository.getAllBankAccountData() } returns bankAccountDataFlow
        every { secureNoteDataRepository.getAllSecureNoteData() } returns secureNoteDataFlow
        every { loginDataRepository.getAllLoginData() } returns loginDataFlow
        every { cardDataRepository.getAllBankCardData() } returns cardDataFlow

        viewModel = RecordsViewModel(
            bankAccountDataRepository,
            secureNoteDataRepository,
            loginDataRepository,
            cardDataRepository,
            dispatchersProvider,
            backupMetadataRepository,
            preferenceProvider,
            analyticsHelper,
            inAppReviewManager,
        )
    }

    @After
    fun tearDown() {
        job?.cancel()
    }

    @Test
    fun `initial state of notificationPermissionState`() {
        val notificationPermissionState = viewModel.notificationPermissionState

        val initialState = notificationPermissionState.value
        assertThat(initialState.isNotificationPermissionAskedBefore).isFalse()
        assertThat(initialState.isNeverAskForNotificationPermission).isTrue()
        assertThat(initialState.isBackupPathSet).isTrue()
    }

    @Test
    fun `vm does not load data when there is no collector`() =
        runTest {
            runCurrent()

            // notification permission state loading
            coVerify(exactly = 0) { preferenceProvider.getBooleanPref(any(), any()) }
            coVerify(exactly = 0) { backupMetadataRepository.isBackupPathSet() }

            // inApp review loading
            coVerify(exactly = 0) { preferenceProvider.getIntPref(any(), any()) }
        }

    @Test
    fun `notificationPermissionState starts fetching data when collector is present`() = runTest {
        val isNotificationPermissionAskedBefore = true
        val isNeverAskForNotificationPermission = true
        val isBackupPathSet = true
        coEvery {
            preferenceProvider.getBooleanPref(
                CommonConstants.IS_NOTIFICATION_PERMISSION_ASKED_BEFORE,
                any()
            )
        } returns isNotificationPermissionAskedBefore
        coEvery {
            preferenceProvider.getBooleanPref(
                CommonConstants.IS_NEVER_ASK_FOR_NOTIFICATION_PERMISSION,
                any()
            )
        } returns isNeverAskForNotificationPermission
        coEvery { backupMetadataRepository.isBackupPathSet() } returns isBackupPathSet

        val items = mutableListOf<NotificationPermissionState>()
        job = backgroundScope.launch {
            viewModel.notificationPermissionState.collect { items.add(it) }
        }

        runCurrent()
        coVerify(exactly = 1) {
            preferenceProvider.getBooleanPref(
                CommonConstants.IS_NOTIFICATION_PERMISSION_ASKED_BEFORE,
                false
            )
        }
        coVerify(exactly = 1) {
            preferenceProvider.getBooleanPref(
                CommonConstants.IS_NEVER_ASK_FOR_NOTIFICATION_PERMISSION,
                false
            )
        }

        coVerify(exactly = 1) { backupMetadataRepository.isBackupPathSet() }
        assertThat(items.size).isEqualTo(2) // an initial state and one after runCurrent
        assertThat(items[0]).isEqualTo(NotificationPermissionState()) // initial state
        assertThat(items[1]).isEqualTo(
            NotificationPermissionState(
                isNotificationPermissionAskedBefore = isNotificationPermissionAskedBefore,
                isNeverAskForNotificationPermission = isNeverAskForNotificationPermission,
                isBackupPathSet = isBackupPathSet
            )
        )
    }

    @Test
    fun `inAppReview does not emit when login count has not reached threshold`() = runTest {
        coEvery {
            preferenceProvider.getIntPref(
                CommonConstants.TOTAL_LOGIN_COUNT,
                any()
            )
        } returns 1

        val items = mutableListOf<Unit>()
        job = backgroundScope.launch {
            viewModel.startInAppReview.collect { items.add(it) }
        }

        runCurrent()

        coVerify(exactly = 1) {
            preferenceProvider.getIntPref(
                CommonConstants.TOTAL_LOGIN_COUNT,
                any()
            )
        }
        assertThat(items).isEmpty()
    }

    @Test
    fun `inAppReview does emit data when login count reaches threshold`() = runTest {
        coEvery {
            preferenceProvider.getIntPref(
                CommonConstants.TOTAL_LOGIN_COUNT,
                any()
            )
        } returns ASK_FOR_REVIEW_AFTER_EVERY_LOGIN

        val items = mutableListOf<Unit>()
        job = backgroundScope.launch {
            viewModel.startInAppReview.collect { items.add(it) }
        }

        runCurrent()

        coVerify(exactly = 1) {
            preferenceProvider.getIntPref(
                CommonConstants.TOTAL_LOGIN_COUNT,
                any()
            )
        }
        assertThat(items.size).isEqualTo(1)
        assertThat(items[0]).isEqualTo(Unit)
    }

    @Test
    fun `inAppReview does not emit data when login count just exceeds threshold`() = runTest {
        coEvery {
            preferenceProvider.getIntPref(
                CommonConstants.TOTAL_LOGIN_COUNT,
                any()
            )
        } returns ASK_FOR_REVIEW_AFTER_EVERY_LOGIN + 1

        val items = mutableListOf<Unit>()
        job = backgroundScope.launch {
            viewModel.startInAppReview.collect { items.add(it) }
        }

        runCurrent()

        coVerify(exactly = 1) {
            preferenceProvider.getIntPref(
                CommonConstants.TOTAL_LOGIN_COUNT,
                any()
            )
        }
        assertThat(items).isEmpty()
    }

    @Test
    fun `initial state of uiState`() = runTest {
        val items = mutableListOf<RecordsUiState>()
        job = backgroundScope.launch {
            viewModel.uiState.collect { items.add(it) }
        }

        runCurrent()

        assertThat(items.size).isEqualTo(1)
        assertThat(items[0]).isEqualTo(RecordsUiState())
    }

    @Test
    fun `records are emitted when db emits data`() = runTest {
        val items = mutableListOf<RecordsUiState>()
        job = backgroundScope.launch { viewModel.uiState.collect { items.add(it) } }

        setupAndEmitDefaultRecords()

        val lastState = items.last()
        assertThat(lastState.isLoading).isFalse()
        assertThat(lastState.totalDbRecords).isEqualTo(8)
        assertThat(lastState.records).hasSize(8)
        assertThat(lastState.records).containsExactlyElementsIn(getExpectedDefaultRecords())
            .inOrder()
    }

    @Test
    fun `uiState filters records based on search text case-insensitively`() = runTest {
        val items = mutableListOf<RecordsUiState>()
        job = backgroundScope.launch { viewModel.uiState.collect { items.add(it) } }

        setupAndEmitDefaultRecords()

        viewModel.onScreenAction(RecordScreenAction.OnSearchTextUpdate("login 1"))
        runCurrent()

        val lastState = items.last()
        assertThat(lastState.totalDbRecords).isEqualTo(8)
        assertThat(lastState.searchText).isEqualTo("login 1")

        val expected =
            getExpectedDefaultRecords().filter { it.title.lowercase().contains("login 1") }
        assertThat(lastState.records).hasSize(1)
        assertThat(lastState.records).containsExactlyElementsIn(expected).inOrder()
    }

    @Test
    fun `uiState filters records when a single record type filter is selected`() = runTest {
        val items = mutableListOf<RecordsUiState>()
        job = backgroundScope.launch { viewModel.uiState.collect { items.add(it) } }

        setupAndEmitDefaultRecords()

        viewModel.onScreenAction(RecordScreenAction.OnToggleRecordTypeFilter(RecordType.NOTE))
        runCurrent()

        val lastState = items.last()
        assertThat(lastState.totalDbRecords).isEqualTo(8)
        assertThat(lastState.recordTypeFilters.first { it.recordType == RecordType.NOTE }.isSelected).isTrue()

        val expected = getExpectedDefaultRecords().filter { it.recordType == RecordType.NOTE }
        assertThat(lastState.records).hasSize(2)
        assertThat(lastState.records).containsExactlyElementsIn(expected).inOrder()
    }

    @Test
    fun `uiState filters records when multiple record type filters are selected`() = runTest {
        val items = mutableListOf<RecordsUiState>()
        job = backgroundScope.launch { viewModel.uiState.collect { items.add(it) } }

        setupAndEmitDefaultRecords()

        viewModel.onScreenAction(RecordScreenAction.OnToggleRecordTypeFilter(RecordType.NOTE))
        viewModel.onScreenAction(RecordScreenAction.OnToggleRecordTypeFilter(RecordType.CARD))
        runCurrent()

        val lastState = items.last()
        assertThat(lastState.totalDbRecords).isEqualTo(8)

        val expected = getExpectedDefaultRecords().filter {
            it.recordType == RecordType.NOTE || it.recordType == RecordType.CARD
        }
        assertThat(lastState.records).hasSize(4)
        assertThat(lastState.records).containsExactlyElementsIn(expected).inOrder()
    }

    @Test
    fun `uiState filters records based on both search text and record type filters`() = runTest {
        val items = mutableListOf<RecordsUiState>()
        job = backgroundScope.launch { viewModel.uiState.collect { items.add(it) } }

        setupAndEmitDefaultRecords()

        // Filter by NOTE, but search for "2" which exists in both NOTE and other types
        viewModel.onScreenAction(RecordScreenAction.OnToggleRecordTypeFilter(RecordType.NOTE))
        viewModel.onScreenAction(RecordScreenAction.OnSearchTextUpdate("note 2"))
        runCurrent()

        val lastState = items.last()
        assertThat(lastState.totalDbRecords).isEqualTo(8)
        assertThat(lastState.searchText).isEqualTo("note 2")

        val expected = getExpectedDefaultRecords().filter {
            it.recordType == RecordType.NOTE && it.title.lowercase().contains("note 2")
        }
        assertThat(lastState.records).hasSize(1)
        assertThat(lastState.records).containsExactlyElementsIn(expected).inOrder()
    }

    @Test
    fun `uiState returns empty list when search text does not match any record`() = runTest {
        val items = mutableListOf<RecordsUiState>()
        job = backgroundScope.launch { viewModel.uiState.collect { items.add(it) } }

        setupAndEmitDefaultRecords()

        viewModel.onScreenAction(RecordScreenAction.OnSearchTextUpdate("non existent test search string"))
        runCurrent()

        val lastState = items.last()
        assertThat(lastState.totalDbRecords).isEqualTo(8)
        assertThat(lastState.searchText).isEqualTo("non existent test search string")
        assertThat(lastState.records).isEmpty()
    }

    @Test
    fun `uiState returns all records when all filters are selected`() = runTest {
        val items = mutableListOf<RecordsUiState>()
        job = backgroundScope.launch { viewModel.uiState.collect { items.add(it) } }

        setupAndEmitDefaultRecords()

        viewModel.onScreenAction(RecordScreenAction.OnToggleRecordTypeFilter(RecordType.NOTE))
        viewModel.onScreenAction(RecordScreenAction.OnToggleRecordTypeFilter(RecordType.CARD))
        viewModel.onScreenAction(RecordScreenAction.OnToggleRecordTypeFilter(RecordType.BANK_ACCOUNT))
        viewModel.onScreenAction(RecordScreenAction.OnToggleRecordTypeFilter(RecordType.LOGIN))
        runCurrent()

        val lastState = items.last()
        assertThat(lastState.totalDbRecords).isEqualTo(8)
        assertThat(lastState.recordTypeFilters.all { it.isSelected }).isTrue()

        val expected = getExpectedDefaultRecords()
        assertThat(lastState.records).hasSize(8)
        assertThat(lastState.records).containsExactlyElementsIn(expected).inOrder()
    }

    @Test
    fun `uiState returns empty list when there are 0 db records`() = runTest {
        val items = mutableListOf<RecordsUiState>()
        job = backgroundScope.launch { viewModel.uiState.collect { items.add(it) } }

        setupAndEmitEmptyRecords()

        val lastState = items.last()
        assertThat(lastState.totalDbRecords).isEqualTo(0)
        assertThat(lastState.records).isEmpty()
    }

    @Test
    fun `uiState returns empty list when there are 0 db records and some search text`() =
        runTest {
            val items = mutableListOf<RecordsUiState>()
            job = backgroundScope.launch { viewModel.uiState.collect { items.add(it) } }

            setupAndEmitEmptyRecords()

            viewModel.onScreenAction(RecordScreenAction.OnSearchTextUpdate("search text"))
            runCurrent()

            val lastState = items.last()
            assertThat(lastState.totalDbRecords).isEqualTo(0)
            assertThat(lastState.searchText).isEqualTo("search text")
            assertThat(lastState.records).isEmpty()
        }

    @Test
    fun `uiState returns empty list when filters match no existing items`() = runTest {
        val items = mutableListOf<RecordsUiState>()
        job = backgroundScope.launch { viewModel.uiState.collect { items.add(it) } }

        // Setup 0 LOGIN records but some of others
        bankAccountDataFlow.emit(getBankAccountDataList(2))
        secureNoteDataFlow.emit(getSecureNoteDataList(2))
        loginDataFlow.emit(emptyList()) // No Logins
        cardDataFlow.emit(getBankCardDataList(2))
        runCurrent()

        // Filter explicitly by LOGIN
        viewModel.onScreenAction(RecordScreenAction.OnToggleRecordTypeFilter(RecordType.LOGIN))
        runCurrent()

        val lastState = items.last()
        assertThat(lastState.totalDbRecords).isEqualTo(6) // Total still reflects non-empty DB list
        assertThat(lastState.records).isEmpty() // Filtering result is 0
    }

    @Test
    fun `uiState retains filters on consecutive search updates`() = runTest {
        val items = mutableListOf<RecordsUiState>()
        job = backgroundScope.launch { viewModel.uiState.collect { items.add(it) } }

        setupAndEmitDefaultRecords()

        viewModel.onScreenAction(RecordScreenAction.OnToggleRecordTypeFilter(RecordType.BANK_ACCOUNT))
        runCurrent()

        // Apply first search string
        viewModel.onScreenAction(RecordScreenAction.OnSearchTextUpdate("Account 1"))
        runCurrent()

        // Apply second search string
        viewModel.onScreenAction(RecordScreenAction.OnSearchTextUpdate("Account 2"))
        runCurrent()

        val lastState = items.last()
        assertThat(lastState.totalDbRecords).isEqualTo(8)
        assertThat(lastState.searchText).isEqualTo("Account 2")

        val expected = getExpectedDefaultRecords().filter {
            it.recordType == RecordType.BANK_ACCOUNT && it.title.lowercase()
                .contains("account 2")
        }
        assertThat(lastState.records).hasSize(1)
        assertThat(lastState.records).containsExactlyElementsIn(expected).inOrder()
    }

    @Test
    fun `uiState updates when db emits new data matching existing search text`() = runTest {
        val items = mutableListOf<RecordsUiState>()
        job = backgroundScope.launch { viewModel.uiState.collect { items.add(it) } }

        // 1. Initial State: DB has no records
        setupAndEmitEmptyRecords()

        // 2. User searches for "Login 1", but DB is empty
        viewModel.onScreenAction(RecordScreenAction.OnSearchTextUpdate("Login 1"))
        runCurrent()

        var lastState = items.last()
        assertThat(lastState.totalDbRecords).isEqualTo(0)
        assertThat(lastState.searchText).isEqualTo("Login 1")
        assertThat(lastState.records).isEmpty()

        // 3. DB suddenly emits records, including the searched term
        bankAccountDataFlow.emit(getBankAccountDataList(1))
        secureNoteDataFlow.emit(getSecureNoteDataList(1))
        loginDataFlow.emit(getLoginDataList(2)) // emits Login 1 & Login 2
        cardDataFlow.emit(getBankCardDataList(1))
        runCurrent()

        lastState = items.last()
        assertThat(lastState.totalDbRecords).isEqualTo(5)
        assertThat(lastState.searchText).isEqualTo("Login 1")

        // 4. Assert UI updates dynamically to include the newly available matched item
        val expected = getExpectedDefaultRecords().filter {
            it.recordType == RecordType.LOGIN && it.title == "Login 1"
        }
        assertThat(lastState.records).hasSize(1)
        assertThat(lastState.records).containsExactlyElementsIn(expected).inOrder()
    }

    @Test
    fun `uiState restores all records when search text is added and then cleared`() = runTest {
        val items = mutableListOf<RecordsUiState>()
        job = backgroundScope.launch { viewModel.uiState.collect { items.add(it) } }

        setupAndEmitDefaultRecords() // Emits 8 records

        // 1. Add Search text
        viewModel.onScreenAction(RecordScreenAction.OnSearchTextUpdate("Login 1"))
        runCurrent()

        var lastState = items.last()
        assertThat(lastState.totalDbRecords).isEqualTo(8)
        assertThat(lastState.records).hasSize(1) // Only matches 1 record

        // 2. Clear Search text
        viewModel.onScreenAction(RecordScreenAction.OnSearchTextUpdate(""))
        runCurrent()

        lastState = items.last()
        assertThat(lastState.totalDbRecords).isEqualTo(8)
        assertThat(lastState.searchText).isEmpty()

        // 3. Assert full list is restored accurately
        val expected = getExpectedDefaultRecords()
        assertThat(lastState.records).hasSize(8)
        assertThat(lastState.records).containsExactlyElementsIn(expected).inOrder()
    }

    @Test
    fun `uiState updates correctly across multiple sequential filter toggles`() = runTest {
        val items = mutableListOf<RecordsUiState>()
        job = backgroundScope.launch { viewModel.uiState.collect { items.add(it) } }

        setupAndEmitDefaultRecords()

        // 1. User taps NOTE -> Only Note records are shown
        viewModel.onScreenAction(RecordScreenAction.OnToggleRecordTypeFilter(RecordType.NOTE))
        runCurrent()
        var lastState = items.last()
        assertThat(lastState.records).hasSize(2)
        assertThat(lastState.records.all { it.recordType == RecordType.NOTE }).isTrue()

        // 2. User taps CARD -> Both Note and Card records are shown
        viewModel.onScreenAction(RecordScreenAction.OnToggleRecordTypeFilter(RecordType.CARD))
        runCurrent()
        lastState = items.last()
        assertThat(lastState.records).hasSize(4)
        assertThat(
            lastState.records.all { it.recordType == RecordType.NOTE || it.recordType == RecordType.CARD }
        ).isTrue()

        // 3. User taps NOTE again -> Unselects NOTE, Only Card records remain shown
        viewModel.onScreenAction(RecordScreenAction.OnToggleRecordTypeFilter(RecordType.NOTE))
        runCurrent()
        lastState = items.last()
        assertThat(lastState.records).hasSize(2)
        assertThat(lastState.records.all { it.recordType == RecordType.CARD }).isTrue()
    }

    @Test
    fun `uiState handles complex interactions of search text filter changes and dynamic db changes`() =
        runTest {
            val items = mutableListOf<RecordsUiState>()
            job = backgroundScope.launch { viewModel.uiState.collect { items.add(it) } }

            setupAndEmitDefaultRecords()

            // 1. Initial State: DB has 8 records. User filters by BANK_ACCOUNT and searches "Account 1"
            viewModel.onScreenAction(RecordScreenAction.OnToggleRecordTypeFilter(RecordType.BANK_ACCOUNT))
            viewModel.onScreenAction(RecordScreenAction.OnSearchTextUpdate("Account 1"))
            runCurrent()

            var lastState = items.last()
            assertThat(lastState.records).hasSize(1) // Only Bank Account 1

            // 2. DB change occurs (user removed all bank accounts)
            bankAccountDataFlow.emit(emptyList())
            runCurrent()

            lastState = items.last()
            assertThat(lastState.totalDbRecords).isEqualTo(6) // 8 original - 2 bank accounts
            assertThat(lastState.records).isEmpty() // The filtered item was deleted, screen is empty

            // 3. User realizes screen is empty, changes category filter to CARD and clears text
            viewModel.onScreenAction(RecordScreenAction.OnToggleRecordTypeFilter(RecordType.BANK_ACCOUNT)) // Unselects BANK_ACCOUNT
            viewModel.onScreenAction(RecordScreenAction.OnToggleRecordTypeFilter(RecordType.CARD)) // Selects CARD
            viewModel.onScreenAction(RecordScreenAction.OnSearchTextUpdate("")) // Clears Text
            runCurrent()

            lastState = items.last()
            val expectedCards =
                getExpectedDefaultRecords().filter { it.recordType == RecordType.CARD }
            assertThat(lastState.totalDbRecords).isEqualTo(6)
            assertThat(lastState.records).hasSize(2)
            assertThat(lastState.records).containsExactlyElementsIn(expectedCards).inOrder()
        }

    @Test
    fun `uiState updates isShowAddNewRecordsBottomSheet when triggered`() = runTest {
        val items = mutableListOf<RecordsUiState>()
        job = backgroundScope.launch { viewModel.uiState.collect { items.add(it) } }

        setupAndEmitDefaultRecords()

        viewModel.onScreenAction(RecordScreenAction.OnUpdateShowAddNewRecordBottomSheet(true))
        runCurrent()

        val lastState = items.last()
        assertThat(lastState.isShowAddNewRecordsBottomSheet).isTrue()
    }

    @Test
    fun `onScreenAction with OnAddNewRecord calls crashInDebugBuild`() = runTest {
        mockkObject(Utils)
        every { Utils.crashInDebugBuild(any()) } just runs

        viewModel.onScreenAction(RecordScreenAction.OnAddNewRecord(RecordType.NOTE))

        verify(exactly = 1) { Utils.crashInDebugBuild(any()) }
        unmockkObject(Utils)
    }

    @Test
    fun `onScreenAction with OnRecordClick calls crashInDebugBuild`() = runTest {
        mockkObject(Utils)
        every { Utils.crashInDebugBuild(any()) } just runs

        viewModel.onScreenAction(RecordScreenAction.OnRecordClick(1, RecordType.NOTE))

        verify(exactly = 1) { Utils.crashInDebugBuild(any()) }
        unmockkObject(Utils)
    }

    @Test
    fun `onScreenAction with OnCancelClickFromRationaleDialog and neverAsk=true logs event and updates permissions`() =
        runTest {
            coEvery { preferenceProvider.getBooleanPref(any(), any()) } returns false
            coEvery { backupMetadataRepository.isBackupPathSet() } returns true
            coEvery { preferenceProvider.upsertBooleanPref(any(), any()) } just runs
            every { analyticsHelper.logEvent(any(), any()) } just runs

            // Pre-requisite to start flow collecting and initialize properties internally
            val items = mutableListOf<NotificationPermissionState>()
            job = backgroundScope.launch {
                viewModel.notificationPermissionState.collect {
                    items.add(it)
                }
            }
            runCurrent()

            viewModel.onScreenAction(
                RecordScreenAction.OnCancelClickFromRationaleDialog(
                    neverAsk = true
                )
            )
            runCurrent()

            val slot = slot<AnalyticsParamsBuilder.() -> Unit>()
            verify {
                analyticsHelper.logEvent(
                    eq(AnalyticsKey.NOTIFICATION_PERMISSION_RATIONALE_DIALOG_CANCEL_CLICK),
                    capture(slot)
                )
            }
            val builder = AnalyticsParamsBuilder()
            slot.captured.invoke(builder)
            assertThat(builder.params[AnalyticsParam.DO_NOT_ASK_AGAIN.paramName]).isEqualTo(true)

            assertThat(viewModel.notificationPermissionState.value.isNeverAskForNotificationPermission).isTrue()
            coVerify(exactly = 1) {
                preferenceProvider.upsertBooleanPref(
                    CommonConstants.IS_NEVER_ASK_FOR_NOTIFICATION_PERMISSION,
                    true
                )
            }
        }

    @Test
    fun `onScreenAction with OnCancelClickFromRationaleDialog and neverAsk=false logs event but does not update permissions`() =
        runTest {
            coEvery { preferenceProvider.getBooleanPref(any(), any()) } returns false
            coEvery { backupMetadataRepository.isBackupPathSet() } returns true
            coEvery { preferenceProvider.upsertBooleanPref(any(), any()) } just runs
            every { analyticsHelper.logEvent(any(), any()) } just runs

            // Pre-requisite to start flow collecting and initialize properties internally
            val items = mutableListOf<NotificationPermissionState>()
            job = backgroundScope.launch {
                viewModel.notificationPermissionState.collect {
                    items.add(it)
                }
            }
            runCurrent()

            val initialNeverAsk =
                viewModel.notificationPermissionState.value.isNeverAskForNotificationPermission
            viewModel.onScreenAction(
                RecordScreenAction.OnCancelClickFromRationaleDialog(
                    neverAsk = false
                )
            )
            runCurrent()

            val slot = slot<AnalyticsParamsBuilder.() -> Unit>()
            verify {
                analyticsHelper.logEvent(
                    eq(AnalyticsKey.NOTIFICATION_PERMISSION_RATIONALE_DIALOG_CANCEL_CLICK),
                    capture(slot)
                )
            }
            val builder = AnalyticsParamsBuilder()
            slot.captured.invoke(builder)
            assertThat(builder.params[AnalyticsParam.DO_NOT_ASK_AGAIN.paramName]).isEqualTo(false)

            assertThat(viewModel.notificationPermissionState.value.isNeverAskForNotificationPermission).isEqualTo(
                initialNeverAsk
            )
            // Ensure no upsert requests run with "true" context
            coVerify(exactly = 0) { preferenceProvider.upsertBooleanPref(any(), true) }
        }

    @Test
    fun `onScreenAction with OnNotificationAllowedFromRationaleDialog logs event and updates permissions if asked first time`() =
        runTest {
            // Override mock properties from before to guarantee logic flows
            coEvery {
                preferenceProvider.getBooleanPref(
                    CommonConstants.IS_NOTIFICATION_PERMISSION_ASKED_BEFORE,
                    any()
                )
            } returns false
            coEvery {
                preferenceProvider.getBooleanPref(
                    CommonConstants.IS_NEVER_ASK_FOR_NOTIFICATION_PERMISSION,
                    any()
                )
            } returns false
            coEvery { backupMetadataRepository.isBackupPathSet() } returns true
            coEvery { preferenceProvider.upsertBooleanPref(any(), any()) } just runs
            every { analyticsHelper.logEvent(any(), any()) } just runs

            val items = mutableListOf<NotificationPermissionState>()
            job = backgroundScope.launch {
                viewModel.notificationPermissionState.collect {
                    items.add(it)
                }
            }
            runCurrent()

            assertThat(viewModel.notificationPermissionState.value.isNotificationPermissionAskedBefore).isFalse()

            viewModel.onScreenAction(
                RecordScreenAction.OnNotificationAllowedFromRationaleDialog(
                    isRedirectingToSettingsPage = true
                )
            )
            runCurrent()

            val slot = slot<AnalyticsParamsBuilder.() -> Unit>()
            verify {
                analyticsHelper.logEvent(
                    eq(AnalyticsKey.NOTIFICATION_PERMISSION_RATIONALE_DIALOG_ALLOW_CLICK),
                    capture(slot)
                )
            }
            val builder = AnalyticsParamsBuilder()
            slot.captured.invoke(builder)
            assertThat(builder.params[AnalyticsParam.PERMISSION_ASKED_BEFORE.paramName]).isEqualTo(
                false
            )
            assertThat(builder.params[AnalyticsParam.REDIRECT_TO_SETTINGS.paramName]).isEqualTo(true)

            assertThat(viewModel.notificationPermissionState.value.isNotificationPermissionAskedBefore).isTrue()
            coVerify(exactly = 1) {
                preferenceProvider.upsertBooleanPref(
                    CommonConstants.IS_NOTIFICATION_PERMISSION_ASKED_BEFORE,
                    true
                )
            }
        }

    @Test
    fun `onScreenAction with OnNotificationAllowedFromRationaleDialog logs event but does not update permissions if already asked`() =
        runTest {
            // This time it was asked before
            coEvery {
                preferenceProvider.getBooleanPref(
                    CommonConstants.IS_NOTIFICATION_PERMISSION_ASKED_BEFORE,
                    any()
                )
            } returns true
            coEvery {
                preferenceProvider.getBooleanPref(
                    CommonConstants.IS_NEVER_ASK_FOR_NOTIFICATION_PERMISSION,
                    any()
                )
            } returns false
            coEvery { backupMetadataRepository.isBackupPathSet() } returns true
            coEvery { preferenceProvider.upsertBooleanPref(any(), any()) } just runs
            every { analyticsHelper.logEvent(any(), any()) } just runs

            val items = mutableListOf<NotificationPermissionState>()
            job = backgroundScope.launch {
                viewModel.notificationPermissionState.collect {
                    items.add(it)
                }
            }
            runCurrent()

            clearMocks(preferenceProvider, answers = false)

            viewModel.onScreenAction(
                RecordScreenAction.OnNotificationAllowedFromRationaleDialog(
                    isRedirectingToSettingsPage = false
                )
            )
            runCurrent()

            val slot = slot<AnalyticsParamsBuilder.() -> Unit>()
            verify {
                analyticsHelper.logEvent(
                    eq(AnalyticsKey.NOTIFICATION_PERMISSION_RATIONALE_DIALOG_ALLOW_CLICK),
                    capture(slot)
                )
            }
            val builder = AnalyticsParamsBuilder()
            slot.captured.invoke(builder)
            assertThat(builder.params[AnalyticsParam.PERMISSION_ASKED_BEFORE.paramName]).isEqualTo(
                true
            )
            assertThat(builder.params[AnalyticsParam.REDIRECT_TO_SETTINGS.paramName]).isEqualTo(
                false
            )

            coVerify(exactly = 0) { preferenceProvider.upsertBooleanPref(any(), any()) }
        }

    // --- Private Helper Methods ---

    private fun getBankAccountDataList(size: Int): List<SearchBankAccountData> {
        return (1..size).map {
            SearchBankAccountData(it, "Bank Account $it", "accountNumber $it", Date(0))
        }
    }

    private fun getSecureNoteDataList(size: Int): List<SearchSecureNoteData> {
        return (1..size).map {
            SearchSecureNoteData(it, "Secure Note $it", Date(0))
        }
    }

    private fun getLoginDataList(size: Int): List<SearchLoginData> {
        return (1..size).map {
            SearchLoginData(it, "Login $it", "userId $it", Date(0))
        }
    }

    private fun getBankCardDataList(size: Int): List<SearchBankCardData> {
        return (1..size).map {
            SearchBankCardData(it, "Card $it", "number $it", Date(0))
        }
    }

    private suspend fun TestScope.setupAndEmitDefaultRecords() {
        bankAccountDataFlow.emit(getBankAccountDataList(2))
        secureNoteDataFlow.emit(getSecureNoteDataList(2))
        loginDataFlow.emit(getLoginDataList(2))
        cardDataFlow.emit(getBankCardDataList(2))
        runCurrent()
    }

    private suspend fun TestScope.setupAndEmitEmptyRecords() {
        bankAccountDataFlow.emit(emptyList())
        secureNoteDataFlow.emit(emptyList())
        loginDataFlow.emit(emptyList())
        cardDataFlow.emit(emptyList())
        runCurrent()
    }

    private fun getExpectedDefaultRecords(): List<RecordListItem> = listOf(
        RecordListItem(
            1,
            "Bank Account 1",
            "accountNumber 1",
            RecordType.BANK_ACCOUNT,
            "BANK_ACCOUNT_1"
        ),
        RecordListItem(
            2,
            "Bank Account 2",
            "accountNumber 2",
            RecordType.BANK_ACCOUNT,
            "BANK_ACCOUNT_2"
        ),
        RecordListItem(1, "Card 1", "number 1", RecordType.CARD, "CARD_1"),
        RecordListItem(2, "Card 2", "number 2", RecordType.CARD, "CARD_2"),
        RecordListItem(1, "Login 1", "userId 1", RecordType.LOGIN, "LOGIN_1"),
        RecordListItem(2, "Login 2", "userId 2", RecordType.LOGIN, "LOGIN_2"),
        RecordListItem(1, "Secure Note 1", null, RecordType.NOTE, "NOTE_1"),
        RecordListItem(2, "Secure Note 2", null, RecordType.NOTE, "NOTE_2")
    ).sortedBy { it.title.lowercase() }
}