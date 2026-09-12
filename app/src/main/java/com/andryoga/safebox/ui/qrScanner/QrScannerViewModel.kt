package com.andryoga.safebox.ui.qrScanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andryoga.safebox.analytics.AnalyticsHelper
import com.andryoga.safebox.common.AnalyticsKey
import com.andryoga.safebox.common.AnalyticsParam
import com.andryoga.safebox.common.CommonConstants
import com.andryoga.safebox.common.DispatchersProvider
import com.andryoga.safebox.providers.interfaces.PreferenceProvider
import com.google.mlkit.vision.barcode.BarcodeScanner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel managing the QR scanner viewfinder UI state, camera controls (torch toggle),
 * and logging user-facing analytics events for camera permissions and scanner interactions.
 *
 * @param barcodeScanner Injected ML Kit [BarcodeScanner] instance for QR code scanning.
 * @param analyticsHelper Injected analytics logger for tracking user actions.
 * @param preferenceProvider Injected preference store to track permission request history.
 * @param dispatchersProvider Injected coroutine dispatcher abstraction for predictable virtual-time testing.
 */
@HiltViewModel
class QrScannerViewModel @Inject constructor(
    val barcodeScanner: BarcodeScanner,
    private val analyticsHelper: AnalyticsHelper,
    private val preferenceProvider: PreferenceProvider,
    private val dispatchersProvider: DispatchersProvider,
) : ViewModel() {

    private val _isTorchEnabled = MutableStateFlow(false)
    val isTorchEnabled: StateFlow<Boolean> = _isTorchEnabled.asStateFlow()

    private val _showPermissionRationale = MutableStateFlow(false)
    val showPermissionRationale: StateFlow<Boolean> = _showPermissionRationale.asStateFlow()

    private val _isCameraPermissionAskedBefore = MutableStateFlow<Boolean?>(null)

    init {
        viewModelScope.launch(dispatchersProvider.io) {
            _isCameraPermissionAskedBefore.value = preferenceProvider.getBooleanPref(
                CommonConstants.IS_CAMERA_PERMISSION_ASKED_BEFORE,
                false,
            )
        }
    }

    val uiState: StateFlow<QrScannerUiState> = combine(
        _isTorchEnabled,
        _showPermissionRationale,
        _isCameraPermissionAskedBefore,
    ) { isTorchEnabled, showRationale, isAskedBefore ->
        QrScannerUiState(
            isTorchEnabled = isTorchEnabled,
            showPermissionRationale = showRationale,
            isCameraPermissionAskedBefore = isAskedBefore,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = QrScannerUiState(),
    )

    fun onAction(action: QrScannerScreenAction) {
        when (action) {
            QrScannerScreenAction.OnScannerVisible -> {
                analyticsHelper.logEvent(AnalyticsKey.QR_SCANNER_SHOW)
            }

            QrScannerScreenAction.OnToggleTorch -> {
                _isTorchEnabled.value = !_isTorchEnabled.value
                analyticsHelper.logEvent(AnalyticsKey.QR_SCANNER_TORCH_TOGGLE) {
                    param(AnalyticsParam.IS_ENABLED, _isTorchEnabled.value)
                }
            }

            is QrScannerScreenAction.OnQrCodeScanned -> {
                analyticsHelper.logEvent(AnalyticsKey.QR_SCANNER_SUCCESS)
            }

            QrScannerScreenAction.OnEnterKeyManuallyClicked -> {
                analyticsHelper.logEvent(AnalyticsKey.QR_SCANNER_MANUAL_CLICK)
            }

            QrScannerScreenAction.OnCloseClicked -> {
                analyticsHelper.logEvent(AnalyticsKey.QR_SCANNER_CANCEL)
            }

            QrScannerScreenAction.OnShowPermissionRationale -> {
                _showPermissionRationale.value = true
                analyticsHelper.logEvent(AnalyticsKey.CAMERA_PERMISSION_RATIONALE_DIALOG_SHOW)
            }

            QrScannerScreenAction.OnPermissionRationaleDismissed -> {
                _showPermissionRationale.value = false
            }

            is QrScannerScreenAction.OnPermissionRationaleAllowClicked -> {
                _showPermissionRationale.value = false
                if (action.isRedirectingToSettings) {
                    analyticsHelper.logEvent(AnalyticsKey.CAMERA_PERMISSION_SETTINGS_OPEN_CLICK)
                } else {
                    analyticsHelper.logEvent(AnalyticsKey.CAMERA_PERMISSION_RATIONALE_DIALOG_ALLOW_CLICK)
                }
            }

            QrScannerScreenAction.OnPermissionRationaleCancelClicked -> {
                _showPermissionRationale.value = false
                analyticsHelper.logEvent(AnalyticsKey.CAMERA_PERMISSION_RATIONALE_DIALOG_CANCEL_CLICK)
            }

            QrScannerScreenAction.OnInitialCameraPermissionRequested -> {
                _isCameraPermissionAskedBefore.value = true
                viewModelScope.launch(dispatchersProvider.io) {
                    preferenceProvider.upsertBooleanPref(
                        CommonConstants.IS_CAMERA_PERMISSION_ASKED_BEFORE,
                        true,
                    )
                }
            }
        }
    }
}
