package com.andryoga.safebox.ui.qrScanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import app.cash.turbine.test
import com.andryoga.safebox.MainDispatcherRule
import com.andryoga.safebox.common.AnalyticsKey
import com.andryoga.safebox.common.AnalyticsParam
import com.andryoga.safebox.common.CommonConstants
import com.andryoga.safebox.test.fakes.FakeAnalyticsHelper
import com.andryoga.safebox.test.fakes.FakePreferenceProvider
import com.andryoga.safebox.totp.models.ParsedTotpData
import com.andryoga.safebox.totp.models.TotpAlgorithm
import com.andryoga.safebox.totp.models.TotpConfig
import com.andryoga.safebox.totp.models.TotpUriError
import com.google.common.truth.Truth.assertThat
import com.google.mlkit.vision.barcode.BarcodeScanner
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QrScannerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var analyticsHelper: FakeAnalyticsHelper
    private lateinit var fakePreferenceProvider: FakePreferenceProvider
    private lateinit var mockBarcodeScanner: BarcodeScanner
    private lateinit var scannedTotpHolder: ScannedTotpHolder
    private lateinit var viewModel: QrScannerViewModel

    @Before
    fun setUp() {
        analyticsHelper = FakeAnalyticsHelper()
        fakePreferenceProvider = FakePreferenceProvider()
        mockBarcodeScanner = mockk(relaxed = true)
        scannedTotpHolder = ScannedTotpHolder()
        viewModel = QrScannerViewModel(
            barcodeScanner = mockBarcodeScanner,
            analyticsHelper = analyticsHelper,
            preferenceProvider = fakePreferenceProvider,
            scannedTotpHolder = scannedTotpHolder,
        )
    }

    @Test
    fun initialState_shouldHaveTorchDisabledAndRationaleHidden() {
        assertThat(viewModel.isTorchEnabled.value).isFalse()
        assertThat(viewModel.showPermissionRationale.value).isFalse()
    }

    @Test
    fun onScannerVisible_shouldLogQrScannerShowAnalyticsEvent() {
        viewModel.onAction(QrScannerScreenAction.OnScannerVisible)

        assertThat(analyticsHelper.hasLogged(AnalyticsKey.QR_SCANNER_SHOW)).isTrue()
    }

    @Test
    fun toggleTorch_shouldToggleTorchStateAndLogAnalyticsEventWithEnabledParam() {
        viewModel.onAction(QrScannerScreenAction.OnToggleTorch)

        assertThat(viewModel.isTorchEnabled.value).isTrue()
        assertThat(analyticsHelper.hasLogged(AnalyticsKey.QR_SCANNER_TORCH_TOGGLE)).isTrue()
        val event =
            analyticsHelper.loggedEvents.first { it.key == AnalyticsKey.QR_SCANNER_TORCH_TOGGLE }
        assertThat(event.params[AnalyticsParam.IS_ENABLED.paramName]).isEqualTo(true)
    }

    @Test
    fun toggleTorch_whenCalledTwice_shouldRestoreTorchStateAndLogParam() {
        viewModel.onAction(QrScannerScreenAction.OnToggleTorch)
        assertThat(viewModel.isTorchEnabled.value).isTrue()

        viewModel.onAction(QrScannerScreenAction.OnToggleTorch)
        assertThat(viewModel.isTorchEnabled.value).isFalse()
        assertThat(analyticsHelper.count(AnalyticsKey.QR_SCANNER_TORCH_TOGGLE)).isEqualTo(2)
        val secondEvent =
            analyticsHelper.loggedEvents.filter { it.key == AnalyticsKey.QR_SCANNER_TORCH_TOGGLE }[1]
        assertThat(secondEvent.params[AnalyticsParam.IS_ENABLED.paramName]).isEqualTo(false)
    }

    @Test
    fun onQrCodeScanned_shouldLogQrScannerSuccessAnalyticsEvent() {
        val dummyData = ParsedTotpData(
            title = "GitHub",
            config = TotpConfig(secretKey = "JBSWY3DPEHPK3PXP"),
        )

        viewModel.onAction(QrScannerScreenAction.OnQrCodeScanned(dummyData))

        assertThat(analyticsHelper.hasLogged(AnalyticsKey.QR_SCANNER_SUCCESS)).isTrue()
    }

    @Test
    fun onQrCodeScanned_shouldHandScannedDataToHolderWithIssuerParameters() {
        val dummyData = ParsedTotpData(
            title = "GitHub",
            config = TotpConfig(
                secretKey = "JBSWY3DPEHPK3PXP",
                algorithm = TotpAlgorithm.SHA512,
                digits = 8,
                period = 60,
            ),
        )

        viewModel.onAction(QrScannerScreenAction.OnQrCodeScanned(dummyData))

        assertThat(scannedTotpHolder.consume()).isEqualTo(dummyData)
    }

    @Test
    fun onEnterKeyManuallyClicked_shouldLogQrScannerManualClickAnalyticsEvent() {
        viewModel.onAction(QrScannerScreenAction.OnEnterKeyManuallyClicked)

        assertThat(analyticsHelper.hasLogged(AnalyticsKey.QR_SCANNER_MANUAL_CLICK)).isTrue()
    }

    @Test
    fun onCloseClicked_shouldLogQrScannerCancelAnalyticsEvent() {
        viewModel.onAction(QrScannerScreenAction.OnCloseClicked)

        assertThat(analyticsHelper.hasLogged(AnalyticsKey.QR_SCANNER_CANCEL)).isTrue()
    }

    @Test
    fun onShowPermissionRationale_shouldShowRationaleDialogAndLogAnalyticsEvent() {
        viewModel.onAction(QrScannerScreenAction.OnShowPermissionRationale)

        assertThat(viewModel.showPermissionRationale.value).isTrue()
        assertThat(analyticsHelper.hasLogged(AnalyticsKey.CAMERA_PERMISSION_RATIONALE_DIALOG_SHOW)).isTrue()
    }

    @Test
    fun onPermissionRationaleDismissed_shouldHideRationaleDialog() {
        viewModel.onAction(QrScannerScreenAction.OnShowPermissionRationale)
        assertThat(viewModel.showPermissionRationale.value).isTrue()

        viewModel.onAction(QrScannerScreenAction.OnPermissionRationaleDismissed)
        assertThat(viewModel.showPermissionRationale.value).isFalse()
    }

    @Test
    fun onShowPermissionRationale_whenAlreadyVisible_shouldLogShowEventOnlyOnce() {
        viewModel.onAction(QrScannerScreenAction.OnShowPermissionRationale)
        viewModel.onAction(QrScannerScreenAction.OnShowPermissionRationale)
        viewModel.onAction(permanentlyDeniedResult())

        assertThat(viewModel.showPermissionRationale.value).isTrue()
        assertThat(analyticsHelper.count(AnalyticsKey.CAMERA_PERMISSION_RATIONALE_DIALOG_SHOW))
            .isEqualTo(1)
    }

    @Test
    fun onShowPermissionRationale_afterDismissal_shouldLogShowEventAgain() {
        viewModel.onAction(QrScannerScreenAction.OnShowPermissionRationale)
        viewModel.onAction(QrScannerScreenAction.OnPermissionRationaleDismissed)

        viewModel.onAction(QrScannerScreenAction.OnShowPermissionRationale)

        assertThat(analyticsHelper.count(AnalyticsKey.CAMERA_PERMISSION_RATIONALE_DIALOG_SHOW))
            .isEqualTo(2)
    }

    @Test
    fun onPermissionRationaleAllowClicked_shouldDismissDialogAndLogAllowClick() {
        viewModel.onAction(QrScannerScreenAction.OnShowPermissionRationale)

        viewModel.onAction(QrScannerScreenAction.OnPermissionRationaleAllowClicked)

        assertThat(viewModel.showPermissionRationale.value).isFalse()
        assertThat(analyticsHelper.hasLogged(AnalyticsKey.CAMERA_PERMISSION_RATIONALE_DIALOG_ALLOW_CLICK)).isTrue()
        assertThat(analyticsHelper.hasLogged(AnalyticsKey.CAMERA_PERMISSION_SETTINGS_OPEN_CLICK)).isFalse()
    }

    @Test
    fun onCameraPermissionResult_whenPermanentlyDenied_shouldReShowRationaleOfferingSettings() =
        runTest {
            viewModel.uiState.test {
                awaitItem()
                awaitItem()

                viewModel.onAction(permanentlyDeniedResult())

                val denied = awaitItem()
                assertThat(denied.isPermissionPermanentlyDenied).isTrue()
                assertThat(denied.showPermissionRationale).isTrue()
                cancelAndIgnoreRemainingEvents()
            }

            assertThat(
                analyticsHelper.hasLogged(AnalyticsKey.CAMERA_PERMISSION_RATIONALE_DIALOG_SHOW),
            ).isTrue()
        }

    @Test
    fun onCameraPermissionResult_whenGrantedAfterPermanentDenial_shouldClearPermanentDenial() =
        runTest {
            viewModel.uiState.test {
                awaitItem()
                awaitItem()

                viewModel.onAction(permanentlyDeniedResult())
                assertThat(awaitItem().isPermissionPermanentlyDenied).isTrue()

                viewModel.onAction(
                    QrScannerScreenAction.OnCameraPermissionResult(
                        isGranted = true,
                        canAskAgain = false,
                    ),
                )

                assertThat(awaitItem().isPermissionPermanentlyDenied).isFalse()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun onCameraPermissionResult_whenDeniedButRetryable_shouldNotOfferSettingsOrShowRationale() {
        viewModel.onAction(
            QrScannerScreenAction.OnCameraPermissionResult(
                isGranted = false,
                canAskAgain = true,
            ),
        )

        assertThat(viewModel.showPermissionRationale.value).isFalse()
        assertThat(analyticsHelper.hasLogged(AnalyticsKey.CAMERA_PERMISSION_RATIONALE_DIALOG_SHOW))
            .isFalse()
    }

    @Test
    fun onCameraPermissionResult_whenGranted_shouldReportGrantedOutcome() {
        viewModel.onAction(
            QrScannerScreenAction.OnCameraPermissionResult(
                isGranted = true,
                canAskAgain = false,
            ),
        )

        assertThat(loggedPermissionOutcome()).isEqualTo("granted")
    }

    @Test
    fun onCameraPermissionResult_whenDeniedButRetryable_shouldReportDeniedOutcome() {
        viewModel.onAction(
            QrScannerScreenAction.OnCameraPermissionResult(
                isGranted = false,
                canAskAgain = true,
            ),
        )

        assertThat(loggedPermissionOutcome()).isEqualTo("denied")
    }

    @Test
    fun onCameraPermissionResult_whenPermanentlyDenied_shouldReportPermanentlyDeniedOutcome() {
        viewModel.onAction(permanentlyDeniedResult())

        assertThat(loggedPermissionOutcome()).isEqualTo("permanently_denied")
    }

    @Test
    fun onOpenAppSettingsClicked_shouldDismissDialogAndLogSettingsOpenClick() {
        viewModel.onAction(permanentlyDeniedResult())

        viewModel.onAction(QrScannerScreenAction.OnOpenAppSettingsClicked)

        assertThat(viewModel.showPermissionRationale.value).isFalse()
        assertThat(analyticsHelper.hasLogged(AnalyticsKey.CAMERA_PERMISSION_SETTINGS_OPEN_CLICK)).isTrue()
    }

    @Test
    fun onCameraBound_shouldExposeFlashCapabilityOfBoundCamera() = runTest {
        viewModel.uiState.test {
            awaitItem()
            awaitItem()

            viewModel.onAction(QrScannerScreenAction.OnCameraBound(hasFlashUnit = true))

            assertThat(awaitItem().hasFlashUnit).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onCameraBound_whenCameraHasNoFlashUnit_shouldKeepFlashCapabilityDisabled() = runTest {
        viewModel.uiState.test {
            awaitItem()
            val loaded = awaitItem()
            assertThat(loaded.hasFlashUnit).isFalse()

            viewModel.onAction(QrScannerScreenAction.OnCameraBound(hasFlashUnit = false))

            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onTorchStateChanged_shouldReconcileTorchFlagWithHardwareWithoutLoggingToggle() {
        viewModel.onAction(QrScannerScreenAction.OnToggleTorch)
        assertThat(viewModel.isTorchEnabled.value).isTrue()

        viewModel.onAction(QrScannerScreenAction.OnTorchStateChanged(isEnabled = false))

        assertThat(viewModel.isTorchEnabled.value).isFalse()
        assertThat(analyticsHelper.count(AnalyticsKey.QR_SCANNER_TORCH_TOGGLE)).isEqualTo(1)
    }

    @Test
    fun onCleared_shouldCloseBarcodeScanner() {
        val viewModelStore = ViewModelStore()
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = viewModel as T
        }
        ViewModelProvider(viewModelStore, factory)[QrScannerViewModel::class.java]

        viewModelStore.clear()

        verify(exactly = 1) { mockBarcodeScanner.close() }
    }

    @Test
    fun onPermissionRationaleCancelClicked_shouldDismissDialogAndLogCancelClick() {
        viewModel.onAction(QrScannerScreenAction.OnShowPermissionRationale)

        viewModel.onAction(QrScannerScreenAction.OnPermissionRationaleCancelClicked)

        assertThat(viewModel.showPermissionRationale.value).isFalse()
        assertThat(analyticsHelper.hasLogged(AnalyticsKey.CAMERA_PERMISSION_RATIONALE_DIALOG_CANCEL_CLICK)).isTrue()
    }

    @Test
    fun initialState_whenPermissionNeverAsked_shouldHaveAskedBeforeFalseInUiState() = runTest {
        viewModel.uiState.test {
            val initial = awaitItem()
            assertThat(initial.isCameraPermissionAskedBefore).isNull()

            val loaded = awaitItem()
            assertThat(loaded.isCameraPermissionAskedBefore).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun initialState_whenPermissionAskedBefore_shouldHaveAskedBeforeTrueInUiState() = runTest {
        val prefProvider = FakePreferenceProvider().apply {
            upsertBooleanPref(CommonConstants.IS_CAMERA_PERMISSION_ASKED_BEFORE, true)
        }
        val vm = QrScannerViewModel(
            barcodeScanner = mockBarcodeScanner,
            analyticsHelper = analyticsHelper,
            preferenceProvider = prefProvider,
            scannedTotpHolder = scannedTotpHolder,
        )
        vm.uiState.test {
            val initial = awaitItem()
            assertThat(initial.isCameraPermissionAskedBefore).isNull()

            val loaded = awaitItem()
            assertThat(loaded.isCameraPermissionAskedBefore).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onCameraPermissionResult_shouldUpdateUiStateAndPersistPreference() = runTest {
        viewModel.uiState.test {
            val initial = awaitItem()
            assertThat(initial.isCameraPermissionAskedBefore).isNull()

            val loaded = awaitItem()
            assertThat(loaded.isCameraPermissionAskedBefore).isFalse()

            viewModel.onAction(
                QrScannerScreenAction.OnCameraPermissionResult(
                    isGranted = true,
                    canAskAgain = false,
                ),
            )

            val updated = awaitItem()
            assertThat(updated.isCameraPermissionAskedBefore).isTrue()
            assertThat(
                fakePreferenceProvider.getBooleanPref(
                    CommonConstants.IS_CAMERA_PERMISSION_ASKED_BEFORE,
                    false,
                ),
            ).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun uiState_shouldReflectCombinedDrivingState() = runTest {
        viewModel.uiState.test {
            val initial = awaitItem()
            assertThat(initial.isTorchEnabled).isFalse()
            assertThat(initial.showPermissionRationale).isFalse()
            assertThat(initial.isCameraPermissionAskedBefore).isNull()

            val loaded = awaitItem()
            assertThat(loaded.isCameraPermissionAskedBefore).isFalse()

            viewModel.onAction(QrScannerScreenAction.OnToggleTorch)
            val withTorch = awaitItem()
            assertThat(withTorch.isTorchEnabled).isTrue()

            viewModel.onAction(QrScannerScreenAction.OnShowPermissionRationale)
            val withRationale = awaitItem()
            assertThat(withRationale.showPermissionRationale).isTrue()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onUnsupportedQrCodeScanned_shouldExposeReasonAndLogShowEventWithReasonParam() = runTest {
        viewModel.uiState.test {
            awaitItem()
            awaitItem()

            viewModel.onAction(
                QrScannerScreenAction.OnUnsupportedQrCodeScanned(
                    TotpUriError.UNSUPPORTED_ALGORITHM,
                ),
            )

            val withError = awaitItem()
            assertThat(withError.unsupportedQrError).isEqualTo(TotpUriError.UNSUPPORTED_ALGORITHM)
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(analyticsHelper.hasLogged(AnalyticsKey.QR_SCANNER_UNSUPPORTED_DIALOG_SHOW))
            .isTrue()
        val event = analyticsHelper.loggedEvents
            .first { it.key == AnalyticsKey.QR_SCANNER_UNSUPPORTED_DIALOG_SHOW }
        assertThat(event.params[AnalyticsParam.REASON.paramName]).isEqualTo("unsupported_algorithm")
    }

    @Test
    fun onUnsupportedQrCodeScanned_shouldReportEachReasonWithItsOwnAnalyticsValue() {
        TotpUriError.entries.forEach { reason ->
            viewModel.onAction(QrScannerScreenAction.OnUnsupportedQrCodeScanned(reason))
        }

        val reportedValues = analyticsHelper.loggedEvents
            .filter { it.key == AnalyticsKey.QR_SCANNER_UNSUPPORTED_DIALOG_SHOW }
            .map { it.params[AnalyticsParam.REASON.paramName] }

        assertThat(reportedValues).containsNoDuplicates()
        assertThat(reportedValues).hasSize(TotpUriError.entries.size)
    }

    @Test
    fun onUnsupportedQrCodeDismissed_shouldClearErrorAndLogDismissEvent() = runTest {
        viewModel.uiState.test {
            awaitItem()
            awaitItem()

            viewModel.onAction(
                QrScannerScreenAction.OnUnsupportedQrCodeScanned(TotpUriError.INVALID_SECRET),
            )
            assertThat(awaitItem().unsupportedQrError).isEqualTo(TotpUriError.INVALID_SECRET)

            viewModel.onAction(QrScannerScreenAction.OnUnsupportedQrCodeDismissed)
            assertThat(awaitItem().unsupportedQrError).isNull()
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(analyticsHelper.hasLogged(AnalyticsKey.QR_SCANNER_UNSUPPORTED_DIALOG_DISMISS))
            .isTrue()
    }

    private fun permanentlyDeniedResult() = QrScannerScreenAction.OnCameraPermissionResult(
        isGranted = false,
        canAskAgain = false,
    )

    private fun loggedPermissionOutcome(): Any? = analyticsHelper.loggedEvents
        .first { it.key == AnalyticsKey.CAMERA_PERMISSION_RESULT }
        .params[AnalyticsParam.RESULT.paramName]
}
