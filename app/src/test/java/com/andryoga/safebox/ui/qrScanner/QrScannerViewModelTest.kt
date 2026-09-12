package com.andryoga.safebox.ui.qrScanner

import app.cash.turbine.test
import com.andryoga.safebox.MainDispatcherRule
import com.andryoga.safebox.common.AnalyticsKey
import com.andryoga.safebox.common.AnalyticsParam
import com.andryoga.safebox.common.CommonConstants
import com.andryoga.safebox.test.fakes.FakeAnalyticsHelper
import com.andryoga.safebox.test.fakes.FakePreferenceProvider
import com.andryoga.safebox.totp.models.ParsedTotpData
import com.google.common.truth.Truth.assertThat
import com.google.mlkit.vision.barcode.BarcodeScanner
import io.mockk.mockk
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
    private lateinit var viewModel: QrScannerViewModel

    @Before
    fun setUp() {
        analyticsHelper = FakeAnalyticsHelper()
        fakePreferenceProvider = FakePreferenceProvider()
        mockBarcodeScanner = mockk(relaxed = true)
        viewModel = QrScannerViewModel(
            barcodeScanner = mockBarcodeScanner,
            analyticsHelper = analyticsHelper,
            preferenceProvider = fakePreferenceProvider,
            dispatchersProvider = mainDispatcherRule.testDispatcherProvider,
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
        val dummyData = ParsedTotpData(title = "GitHub", secretKey = "JBSWY3DPEHPK3PXP")

        viewModel.onAction(QrScannerScreenAction.OnQrCodeScanned(dummyData))

        assertThat(analyticsHelper.hasLogged(AnalyticsKey.QR_SCANNER_SUCCESS)).isTrue()
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
    fun onPermissionRationaleAllowClicked_whenNotRedirectingToSettings_shouldDismissDialogAndLogAllowClick() {
        viewModel.onAction(QrScannerScreenAction.OnShowPermissionRationale)

        viewModel.onAction(
            QrScannerScreenAction.OnPermissionRationaleAllowClicked(
                isRedirectingToSettings = false
            )
        )

        assertThat(viewModel.showPermissionRationale.value).isFalse()
        assertThat(analyticsHelper.hasLogged(AnalyticsKey.CAMERA_PERMISSION_RATIONALE_DIALOG_ALLOW_CLICK)).isTrue()
        assertThat(analyticsHelper.hasLogged(AnalyticsKey.CAMERA_PERMISSION_SETTINGS_OPEN_CLICK)).isFalse()
    }

    @Test
    fun onPermissionRationaleAllowClicked_whenRedirectingToSettings_shouldDismissDialogAndLogOpenSettingsClick() {
        viewModel.onAction(QrScannerScreenAction.OnShowPermissionRationale)

        viewModel.onAction(
            QrScannerScreenAction.OnPermissionRationaleAllowClicked(
                isRedirectingToSettings = true
            )
        )

        assertThat(viewModel.showPermissionRationale.value).isFalse()
        assertThat(analyticsHelper.hasLogged(AnalyticsKey.CAMERA_PERMISSION_SETTINGS_OPEN_CLICK)).isTrue()
        assertThat(analyticsHelper.hasLogged(AnalyticsKey.CAMERA_PERMISSION_RATIONALE_DIALOG_ALLOW_CLICK)).isFalse()
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
            dispatchersProvider = mainDispatcherRule.testDispatcherProvider,
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
    fun onInitialCameraPermissionRequested_shouldUpdateUiStateAndPersistPreference() = runTest {
        viewModel.uiState.test {
            val initial = awaitItem()
            assertThat(initial.isCameraPermissionAskedBefore).isNull()

            val loaded = awaitItem()
            assertThat(loaded.isCameraPermissionAskedBefore).isFalse()

            viewModel.onAction(QrScannerScreenAction.OnInitialCameraPermissionRequested)

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
}
