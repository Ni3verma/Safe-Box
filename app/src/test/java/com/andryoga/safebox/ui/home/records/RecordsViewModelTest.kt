@file:OptIn(ExperimentalCoroutinesApi::class)

package com.andryoga.safebox.ui.home.records

import app.cash.turbine.test
import com.andryoga.safebox.MainDispatcherRule
import com.andryoga.safebox.analytics.AnalyticsHelper
import com.andryoga.safebox.analytics.AnalyticsParamsBuilder
import com.andryoga.safebox.common.AnalyticsKey
import com.andryoga.safebox.common.AnalyticsParam
import com.andryoga.safebox.common.CommonConstants
import com.andryoga.safebox.common.Exceptions
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
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
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
    private val bankAccountDataFlow = MutableSharedFlow<List<SearchBankAccountData>>(replay = 1)
    private val secureNoteDataFlow = MutableSharedFlow<List<SearchSecureNoteData>>(replay = 1)
    private val loginDataFlow = MutableSharedFlow<List<SearchLoginData>>(replay = 1)
    private val cardDataFlow = MutableSharedFlow<List<SearchBankCardData>>(replay = 1)

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        every { bankAccountDataRepository.getAllBankAccountData() } returns bankAccountDataFlow
        every { secureNoteDataRepository.getAllSecureNoteData() } returns secureNoteDataFlow
        every { loginDataRepository.getAllLoginData() } returns loginDataFlow
        every { cardDataRepository.getAllBankCardData() } returns cardDataFlow

        viewModel = RecordsViewModel(
            bankAccountDataRepository,
            secureNoteDataRepository,
            loginDataRepository,
            cardDataRepository,
            mainDispatcherRule.testDispatcherProvider,
            backupMetadataRepository,
            preferenceProvider,
            analyticsHelper,
            inAppReviewManager,
        )
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

        viewModel.notificationPermissionState.test {
            val initial = awaitItem()
            assertThat(initial).isEqualTo(NotificationPermissionState())

            advanceUntilIdle()

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

            val loaded = expectMostRecentItem()
            assertThat(loaded).isEqualTo(
                NotificationPermissionState(
                    isNotificationPermissionAskedBefore = isNotificationPermissionAskedBefore,
                    isNeverAskForNotificationPermission = isNeverAskForNotificationPermission,
                    isBackupPathSet = isBackupPathSet
                )
            )
        }
    }

    @Test
    fun `inAppReview does not emit when login count has not reached threshold`() = runTest {
        coEvery {
            preferenceProvider.getIntPref(
                CommonConstants.TOTAL_LOGIN_COUNT,
                any()
            )
        } returns 1

        viewModel.startInAppReview.test {
            advanceUntilIdle()
            coVerify(exactly = 1) {
                preferenceProvider.getIntPref(
                    CommonConstants.TOTAL_LOGIN_COUNT,
                    any()
                )
            }
            expectNoEvents()
        }
    }

    @Test
    fun `inAppReview does emit data when login count reaches threshold`() = runTest {
        coEvery {
            preferenceProvider.getIntPref(
                CommonConstants.TOTAL_LOGIN_COUNT,
                any()
            )
        } returns ASK_FOR_REVIEW_AFTER_EVERY_LOGIN

        viewModel.startInAppReview.test {
            advanceUntilIdle()
            coVerify(exactly = 1) {
                preferenceProvider.getIntPref(
                    CommonConstants.TOTAL_LOGIN_COUNT,
                    any()
                )
            }
            assertThat(awaitItem()).isEqualTo(Unit)
        }
    }

    @Test
    fun `inAppReview does not emit data when login count just exceeds threshold`() = runTest {
        coEvery {
            preferenceProvider.getIntPref(
                CommonConstants.TOTAL_LOGIN_COUNT,
                any()
            )
        } returns ASK_FOR_REVIEW_AFTER_EVERY_LOGIN + 1

        viewModel.startInAppReview.test {
            advanceUntilIdle()
            coVerify(exactly = 1) {
                preferenceProvider.getIntPref(
                    CommonConstants.TOTAL_LOGIN_COUNT,
                    any()
                )
            }
            expectNoEvents()
        }
    }

    @Test
    fun `initial state of uiState`() = runTest {
        viewModel.uiState.test {
            assertThat(awaitItem()).isEqualTo(RecordsUiState())
        }
    }

    @Test
    fun `records are emitted when db emits data`() = runTest {
        viewModel.uiState.test {
            awaitItem()
            setupAndEmitDefaultRecords()
            advanceUntilIdle()

            val lastState = expectMostRecentItem()
            assertThat(lastState.isLoading).isFalse()
            assertThat(lastState.totalDbRecords).isEqualTo(8)
            assertThat(lastState.records).hasSize(8)
            assertThat(lastState.records).containsExactlyElementsIn(getExpectedDefaultRecords())
                .inOrder()
        }
    }

    @Test
    fun `uiState filters records based on search text case-insensitively`() = runTest {
        viewModel.uiState.test {
            awaitItem()
            setupAndEmitDefaultRecords()

            viewModel.onScreenAction(RecordScreenAction.OnSearchTextUpdate("login 1"))
            advanceUntilIdle()

            val lastState = expectMostRecentItem()
            assertThat(lastState.totalDbRecords).isEqualTo(8)
            assertThat(lastState.searchText).isEqualTo("login 1")

            val expected =
                getExpectedDefaultRecords().filter { it.title.lowercase().contains("login 1") }
            assertThat(lastState.records).hasSize(1)
            assertThat(lastState.records).containsExactlyElementsIn(expected).inOrder()
        }
    }

    @Test
    fun `uiState filters records when a single record type filter is selected`() = runTest {
        viewModel.uiState.test {
            awaitItem()
            setupAndEmitDefaultRecords()

            viewModel.onScreenAction(RecordScreenAction.OnToggleRecordTypeFilter(RecordType.NOTE))
            advanceUntilIdle()

            val lastState = expectMostRecentItem()
            assertThat(lastState.totalDbRecords).isEqualTo(8)
            assertThat(lastState.recordTypeFilters.first { it.recordType == RecordType.NOTE }.isSelected).isTrue()

            val expected = getExpectedDefaultRecords().filter { it.recordType == RecordType.NOTE }
            assertThat(lastState.records).hasSize(2)
            assertThat(lastState.records).containsExactlyElementsIn(expected).inOrder()
        }
    }

    @Test
    fun `uiState filters records when multiple record type filters are selected`() = runTest {
        viewModel.uiState.test {
            awaitItem()
            setupAndEmitDefaultRecords()

            viewModel.onScreenAction(RecordScreenAction.OnToggleRecordTypeFilter(RecordType.NOTE))
            viewModel.onScreenAction(RecordScreenAction.OnToggleRecordTypeFilter(RecordType.CARD))
            advanceUntilIdle()

            val lastState = expectMostRecentItem()
            assertThat(lastState.totalDbRecords).isEqualTo(8)

            val expected = getExpectedDefaultRecords().filter {
                it.recordType == RecordType.NOTE || it.recordType == RecordType.CARD
            }
            assertThat(lastState.records).hasSize(4)
            assertThat(lastState.records).containsExactlyElementsIn(expected).inOrder()
        }
    }

    @Test
    fun `uiState filters records based on both search text and record type filters`() = runTest {
        viewModel.uiState.test {
            awaitItem()
            setupAndEmitDefaultRecords()

            // Filter by NOTE, but search for "2" which exists in both NOTE and other types
            viewModel.onScreenAction(RecordScreenAction.OnToggleRecordTypeFilter(RecordType.NOTE))
            viewModel.onScreenAction(RecordScreenAction.OnSearchTextUpdate("note 2"))
            advanceUntilIdle()

            val lastState = expectMostRecentItem()
            assertThat(lastState.totalDbRecords).isEqualTo(8)
            assertThat(lastState.searchText).isEqualTo("note 2")

            val expected = getExpectedDefaultRecords().filter {
                it.recordType == RecordType.NOTE && it.title.lowercase().contains("note 2")
            }
            assertThat(lastState.records).hasSize(1)
            assertThat(lastState.records).containsExactlyElementsIn(expected).inOrder()
        }
    }

    @Test
    fun `uiState returns empty list when search text does not match any record`() = runTest {
        viewModel.uiState.test {
            awaitItem()
            setupAndEmitDefaultRecords()

            viewModel.onScreenAction(RecordScreenAction.OnSearchTextUpdate("non existent test search string"))
            advanceUntilIdle()

            val lastState = expectMostRecentItem()
            assertThat(lastState.totalDbRecords).isEqualTo(8)
            assertThat(lastState.searchText).isEqualTo("non existent test search string")
            assertThat(lastState.records).isEmpty()
        }
    }

    @Test
    fun `uiState returns all records when all filters are selected`() = runTest {
        viewModel.uiState.test {
            awaitItem()
            setupAndEmitDefaultRecords()

            viewModel.onScreenAction(RecordScreenAction.OnToggleRecordTypeFilter(RecordType.NOTE))
            viewModel.onScreenAction(RecordScreenAction.OnToggleRecordTypeFilter(RecordType.CARD))
            viewModel.onScreenAction(RecordScreenAction.OnToggleRecordTypeFilter(RecordType.BANK_ACCOUNT))
            viewModel.onScreenAction(RecordScreenAction.OnToggleRecordTypeFilter(RecordType.LOGIN))
            advanceUntilIdle()

            val lastState = expectMostRecentItem()
            assertThat(lastState.totalDbRecords).isEqualTo(8)
            assertThat(lastState.recordTypeFilters.all { it.isSelected }).isTrue()

            val expected = getExpectedDefaultRecords()
            assertThat(lastState.records).hasSize(8)
            assertThat(lastState.records).containsExactlyElementsIn(expected).inOrder()
        }
    }

    @Test
    fun `uiState returns empty list when there are 0 db records`() = runTest {
        viewModel.uiState.test {
            awaitItem()
            setupAndEmitEmptyRecords()
            advanceUntilIdle()

            val lastState = expectMostRecentItem()
            assertThat(lastState.totalDbRecords).isEqualTo(0)
            assertThat(lastState.records).isEmpty()
        }
    }

    @Test
    fun `uiState returns empty list when there are 0 db records and some search text`() =
        runTest {
            viewModel.uiState.test {
                awaitItem()
                setupAndEmitEmptyRecords()

                viewModel.onScreenAction(RecordScreenAction.OnSearchTextUpdate("search text"))
                advanceUntilIdle()

                val lastState = expectMostRecentItem()
                assertThat(lastState.totalDbRecords).isEqualTo(0)
                assertThat(lastState.searchText).isEqualTo("search text")
                assertThat(lastState.records).isEmpty()
            }
        }

    @Test
    fun `uiState returns empty list when filters match no existing items`() = runTest {
        viewModel.uiState.test {
            awaitItem()
            // Setup 0 LOGIN records but some of others
            bankAccountDataFlow.emit(getBankAccountDataList(2))
            secureNoteDataFlow.emit(getSecureNoteDataList(2))
            loginDataFlow.emit(emptyList()) // No Logins
            cardDataFlow.emit(getBankCardDataList(2))
            advanceUntilIdle()

            // Filter explicitly by LOGIN
            viewModel.onScreenAction(RecordScreenAction.OnToggleRecordTypeFilter(RecordType.LOGIN))
            advanceUntilIdle()

            val lastState = expectMostRecentItem()
            assertThat(lastState.totalDbRecords).isEqualTo(6) // Total still reflects non-empty DB list
            assertThat(lastState.records).isEmpty() // Filtering result is 0
        }
    }

    @Test
    fun `uiState retains filters on consecutive search updates`() = runTest {
        viewModel.uiState.test {
            awaitItem()
            setupAndEmitDefaultRecords()

            viewModel.onScreenAction(RecordScreenAction.OnToggleRecordTypeFilter(RecordType.BANK_ACCOUNT))

            // Apply first search string
            viewModel.onScreenAction(RecordScreenAction.OnSearchTextUpdate("Account 1"))

            // Apply second search string
            viewModel.onScreenAction(RecordScreenAction.OnSearchTextUpdate("Account 2"))
            advanceUntilIdle()

            val lastState = expectMostRecentItem()
            assertThat(lastState.totalDbRecords).isEqualTo(8)
            assertThat(lastState.searchText).isEqualTo("Account 2")

            val expected = getExpectedDefaultRecords().filter {
                it.recordType == RecordType.BANK_ACCOUNT && it.title.lowercase()
                    .contains("account 2")
            }
            assertThat(lastState.records).hasSize(1)
            assertThat(lastState.records).containsExactlyElementsIn(expected).inOrder()
        }
    }

    @Test
    fun `uiState updates when db emits new data matching existing search text`() = runTest {
        viewModel.uiState.test {
            awaitItem()
            // 1. Initial State: DB has no records
            setupAndEmitEmptyRecords()

            // 2. User searches for "Login 1", but DB is empty
            viewModel.onScreenAction(RecordScreenAction.OnSearchTextUpdate("Login 1"))
            advanceUntilIdle()

            var lastState = expectMostRecentItem()
            assertThat(lastState.totalDbRecords).isEqualTo(0)
            assertThat(lastState.searchText).isEqualTo("Login 1")
            assertThat(lastState.records).isEmpty()

            // 3. DB suddenly emits records, including the searched term
            bankAccountDataFlow.emit(getBankAccountDataList(1))
            secureNoteDataFlow.emit(getSecureNoteDataList(1))
            loginDataFlow.emit(getLoginDataList(2)) // emits Login 1 & Login 2
            cardDataFlow.emit(getBankCardDataList(1))
            advanceUntilIdle()

            lastState = expectMostRecentItem()
            assertThat(lastState.totalDbRecords).isEqualTo(5)
            assertThat(lastState.searchText).isEqualTo("Login 1")

            // 4. Assert UI updates dynamically to include the newly available matched item
            val expected = getExpectedDefaultRecords().filter {
                it.recordType == RecordType.LOGIN && it.title == "Login 1"
            }
            assertThat(lastState.records).hasSize(1)
            assertThat(lastState.records).containsExactlyElementsIn(expected).inOrder()
        }
    }

    @Test
    fun `uiState restores all records when search text is added and then cleared`() = runTest {
        viewModel.uiState.test {
            awaitItem()
            setupAndEmitDefaultRecords() // Emits 8 records

            // 1. Add Search text
            viewModel.onScreenAction(RecordScreenAction.OnSearchTextUpdate("Login 1"))
            advanceUntilIdle()

            var lastState = expectMostRecentItem()
            assertThat(lastState.totalDbRecords).isEqualTo(8)
            assertThat(lastState.records).hasSize(1) // Only matches 1 record

            // 2. Clear Search text
            viewModel.onScreenAction(RecordScreenAction.OnSearchTextUpdate(""))
            advanceUntilIdle()

            lastState = expectMostRecentItem()
            assertThat(lastState.totalDbRecords).isEqualTo(8)
            assertThat(lastState.searchText).isEmpty()

            // 3. Assert full list is restored accurately
            val expected = getExpectedDefaultRecords()
            assertThat(lastState.records).hasSize(8)
            assertThat(lastState.records).containsExactlyElementsIn(expected).inOrder()
        }
    }

    @Test
    fun `uiState updates correctly across multiple sequential filter toggles`() = runTest {
        viewModel.uiState.test {
            awaitItem()
            setupAndEmitDefaultRecords()

            // 1. User taps NOTE -> Only Note records are shown
            viewModel.onScreenAction(RecordScreenAction.OnToggleRecordTypeFilter(RecordType.NOTE))
            advanceUntilIdle()
            var lastState = expectMostRecentItem()
            assertThat(lastState.records).hasSize(2)
            assertThat(lastState.records.all { it.recordType == RecordType.NOTE }).isTrue()

            // 2. User taps CARD -> Both Note and Card records are shown
            viewModel.onScreenAction(RecordScreenAction.OnToggleRecordTypeFilter(RecordType.CARD))
            advanceUntilIdle()
            lastState = expectMostRecentItem()
            assertThat(lastState.records).hasSize(4)
            assertThat(
                lastState.records.all { it.recordType == RecordType.NOTE || it.recordType == RecordType.CARD }
            ).isTrue()

            // 3. User taps NOTE again -> Unselects NOTE, Only Card records remain shown
            viewModel.onScreenAction(RecordScreenAction.OnToggleRecordTypeFilter(RecordType.NOTE))
            advanceUntilIdle()
            lastState = expectMostRecentItem()
            assertThat(lastState.records).hasSize(2)
            assertThat(lastState.records.all { it.recordType == RecordType.CARD }).isTrue()
        }
    }

    @Test
    fun `uiState handles complex interactions of search text filter changes and dynamic db changes`() =
        runTest {
            viewModel.uiState.test {
                awaitItem()
                setupAndEmitDefaultRecords()

                // 1. Initial State: DB has 8 records. User filters by BANK_ACCOUNT and searches "Account 1"
                viewModel.onScreenAction(RecordScreenAction.OnToggleRecordTypeFilter(RecordType.BANK_ACCOUNT))
                viewModel.onScreenAction(RecordScreenAction.OnSearchTextUpdate("Account 1"))
                advanceUntilIdle()

                var lastState = expectMostRecentItem()
                assertThat(lastState.records).hasSize(1) // Only Bank Account 1

                // 2. DB change occurs (user removed all bank accounts)
                bankAccountDataFlow.emit(emptyList())
                advanceUntilIdle()

                lastState = expectMostRecentItem()
                assertThat(lastState.totalDbRecords).isEqualTo(6) // 8 original - 2 bank accounts
                assertThat(lastState.records).isEmpty() // The filtered item was deleted, screen is empty

                // 3. User realizes screen is empty, changes category filter to CARD and clears text
                viewModel.onScreenAction(RecordScreenAction.OnToggleRecordTypeFilter(RecordType.BANK_ACCOUNT)) // Unselects BANK_ACCOUNT
                viewModel.onScreenAction(RecordScreenAction.OnToggleRecordTypeFilter(RecordType.CARD)) // Selects CARD
                viewModel.onScreenAction(RecordScreenAction.OnSearchTextUpdate("")) // Clears Text
                advanceUntilIdle()

                lastState = expectMostRecentItem()
                val expectedCards =
                    getExpectedDefaultRecords().filter { it.recordType == RecordType.CARD }
                assertThat(lastState.totalDbRecords).isEqualTo(6)
                assertThat(lastState.records).hasSize(2)
                assertThat(lastState.records).containsExactlyElementsIn(expectedCards).inOrder()
            }
        }

    @Test
    fun `uiState updates isShowAddNewRecordsBottomSheet when triggered`() = runTest {
        viewModel.uiState.test {
            awaitItem()
            setupAndEmitDefaultRecords()

            viewModel.onScreenAction(RecordScreenAction.OnUpdateShowAddNewRecordBottomSheet(true))
            advanceUntilIdle()

            val lastState = expectMostRecentItem()
            assertThat(lastState.isShowAddNewRecordsBottomSheet).isTrue()
        }
    }

    @Test
    fun `onScreenAction with OnAddNewRecord calls crashInDebugBuild`() = runTest {
        assertThrows(Exceptions.DebugFatalException::class.java) {
            viewModel.onScreenAction(RecordScreenAction.OnAddNewRecord(RecordType.NOTE))
        }
    }

    @Test
    fun `onScreenAction with OnRecordClick calls crashInDebugBuild`() = runTest {
        assertThrows(Exceptions.DebugFatalException::class.java) {
            viewModel.onScreenAction(RecordScreenAction.OnRecordClick(1, RecordType.NOTE))
        }
    }

    @Test
    fun `onScreenAction with OnCancelClickFromRationaleDialog and neverAsk=true logs event and updates permissions`() =
        runTest {
            coEvery { preferenceProvider.getBooleanPref(any(), any()) } returns false
            coEvery { backupMetadataRepository.isBackupPathSet() } returns true
            coEvery { preferenceProvider.upsertBooleanPref(any(), any()) } just runs
            every { analyticsHelper.logEvent(any(), any()) } just runs

            viewModel.notificationPermissionState.test {
                awaitItem()
                advanceUntilIdle()

                viewModel.onScreenAction(
                    RecordScreenAction.OnCancelClickFromRationaleDialog(
                        neverAsk = true
                    )
                )
                advanceUntilIdle()

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

                val updatedState = expectMostRecentItem()
                assertThat(updatedState.isNeverAskForNotificationPermission).isTrue()
                coVerify(exactly = 1) {
                    preferenceProvider.upsertBooleanPref(
                        CommonConstants.IS_NEVER_ASK_FOR_NOTIFICATION_PERMISSION,
                        true
                    )
                }
            }
        }

    @Test
    fun `onScreenAction with OnCancelClickFromRationaleDialog and neverAsk=false logs event but does not update permissions`() =
        runTest {
            coEvery { preferenceProvider.getBooleanPref(any(), any()) } returns false
            coEvery { backupMetadataRepository.isBackupPathSet() } returns true
            coEvery { preferenceProvider.upsertBooleanPref(any(), any()) } just runs
            every { analyticsHelper.logEvent(any(), any()) } just runs

            viewModel.notificationPermissionState.test {
                val initial = awaitItem()
                advanceUntilIdle()
                val initialNeverAsk = expectMostRecentItem().isNeverAskForNotificationPermission

                viewModel.onScreenAction(
                    RecordScreenAction.OnCancelClickFromRationaleDialog(
                        neverAsk = false
                    )
                )
                advanceUntilIdle()

                val slot = slot<AnalyticsParamsBuilder.() -> Unit>()
                verify {
                    analyticsHelper.logEvent(
                        eq(AnalyticsKey.NOTIFICATION_PERMISSION_RATIONALE_DIALOG_CANCEL_CLICK),
                        capture(slot)
                    )
                }
                val builder = AnalyticsParamsBuilder()
                slot.captured.invoke(builder)
                assertThat(builder.params[AnalyticsParam.DO_NOT_ASK_AGAIN.paramName]).isEqualTo(
                    false
                )

                expectNoEvents()
                assertThat(viewModel.notificationPermissionState.value.isNeverAskForNotificationPermission).isEqualTo(
                    initialNeverAsk
                )
                // Ensure no upsert requests run with "true" context
                coVerify(exactly = 0) { preferenceProvider.upsertBooleanPref(any(), true) }
            }
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

            viewModel.notificationPermissionState.test {
                val initial = awaitItem()
                advanceUntilIdle()
                val loaded = expectMostRecentItem()
                assertThat(loaded.isNotificationPermissionAskedBefore).isFalse()

                viewModel.onScreenAction(
                    RecordScreenAction.OnNotificationAllowedFromRationaleDialog(
                        isRedirectingToSettingsPage = true
                    )
                )
                advanceUntilIdle()

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
                assertThat(builder.params[AnalyticsParam.REDIRECT_TO_SETTINGS.paramName]).isEqualTo(
                    true
                )

                val updated = expectMostRecentItem()
                assertThat(updated.isNotificationPermissionAskedBefore).isTrue()
                coVerify(exactly = 1) {
                    preferenceProvider.upsertBooleanPref(
                        CommonConstants.IS_NOTIFICATION_PERMISSION_ASKED_BEFORE,
                        true
                    )
                }
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

            viewModel.notificationPermissionState.test {
                awaitItem()
                advanceUntilIdle()
                expectMostRecentItem()

                clearMocks(preferenceProvider, answers = false)

                viewModel.onScreenAction(
                    RecordScreenAction.OnNotificationAllowedFromRationaleDialog(
                        isRedirectingToSettingsPage = false
                    )
                )
                advanceUntilIdle()

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

                expectNoEvents()
                coVerify(exactly = 0) { preferenceProvider.upsertBooleanPref(any(), any()) }
            }
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
        advanceUntilIdle()
    }

    private suspend fun TestScope.setupAndEmitEmptyRecords() {
        bankAccountDataFlow.emit(emptyList())
        secureNoteDataFlow.emit(emptyList())
        loginDataFlow.emit(emptyList())
        cardDataFlow.emit(emptyList())
        advanceUntilIdle()
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