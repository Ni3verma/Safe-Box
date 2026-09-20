package com.andryoga.safebox.ui.qrScanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andryoga.safebox.analytics.AnalyticsHelper
import com.andryoga.safebox.common.AnalyticsKey
import com.andryoga.safebox.common.AnalyticsParam
import com.andryoga.safebox.common.CommonConstants
import com.andryoga.safebox.providers.interfaces.PreferenceProvider
import com.andryoga.safebox.totp.models.TotpUriError
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
 * @param scannedTotpHolder Hand-off used to carry a successful scan to the create-record screen.
 */
@HiltViewModel
class QrScannerViewModel @Inject constructor(
    val barcodeScanner: BarcodeScanner,
    private val analyticsHelper: AnalyticsHelper,
    private val preferenceProvider: PreferenceProvider,
    private val scannedTotpHolder: ScannedTotpHolder,
) : ViewModel() {

    private val _isTorchEnabled = MutableStateFlow(false)
    val isTorchEnabled: StateFlow<Boolean> = _isTorchEnabled.asStateFlow()

    private val _hasFlashUnit = MutableStateFlow(false)

    private val _showPermissionRationale = MutableStateFlow(false)
    val showPermissionRationale: StateFlow<Boolean> = _showPermissionRationale.asStateFlow()

    private val _isCameraPermissionAskedBefore = MutableStateFlow<Boolean?>(null)

    private val _isPermissionPermanentlyDenied = MutableStateFlow(false)

    private val _unsupportedQrError = MutableStateFlow<TotpUriError?>(null)

    init {
        viewModelScope.launch {
            _isCameraPermissionAskedBefore.value = preferenceProvider.getBooleanPref(
                CommonConstants.IS_CAMERA_PERMISSION_ASKED_BEFORE,
                false,
            )
        }
    }

    // The torch pair is pre-combined because combine only has typed overloads up to five flows.
    val uiState: StateFlow<QrScannerUiState> = combine(
        combine(_isTorchEnabled, _hasFlashUnit, ::Pair),
        _showPermissionRationale,
        _isCameraPermissionAskedBefore,
        _isPermissionPermanentlyDenied,
        _unsupportedQrError,
    ) { torch, showRationale, isAskedBefore, isPermanentlyDenied, unsupportedQrError ->
        val (isTorchEnabled, hasFlashUnit) = torch
        QrScannerUiState(
            isTorchEnabled = isTorchEnabled,
            hasFlashUnit = hasFlashUnit,
            showPermissionRationale = showRationale,
            isCameraPermissionAskedBefore = isAskedBefore,
            isPermissionPermanentlyDenied = isPermanentlyDenied,
            unsupportedQrError = unsupportedQrError,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = QrScannerUiState(),
    )

    override fun onCleared() {
        super.onCleared()
        barcodeScanner.close()
    }

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

            is QrScannerScreenAction.OnCameraBound -> {
                _hasFlashUnit.value = action.hasFlashUnit
            }

            is QrScannerScreenAction.OnTorchStateChanged -> {
                _isTorchEnabled.value = action.isEnabled
            }

            is QrScannerScreenAction.OnQrCodeScanned -> {
                scannedTotpHolder.put(action.totpData)
                analyticsHelper.logEvent(AnalyticsKey.QR_SCANNER_SUCCESS)
            }

            is QrScannerScreenAction.OnUnsupportedQrCodeScanned -> {
                _unsupportedQrError.value = action.reason
                analyticsHelper.logEvent(AnalyticsKey.QR_SCANNER_UNSUPPORTED_DIALOG_SHOW) {
                    param(AnalyticsParam.REASON, action.reason.toAnalyticsValue())
                }
            }

            QrScannerScreenAction.OnUnsupportedQrCodeDismissed -> {
                _unsupportedQrError.value = null
                analyticsHelper.logEvent(AnalyticsKey.QR_SCANNER_UNSUPPORTED_DIALOG_DISMISS)
            }

            QrScannerScreenAction.OnEnterKeyManuallyClicked -> {
                analyticsHelper.logEvent(AnalyticsKey.QR_SCANNER_MANUAL_CLICK)
            }

            QrScannerScreenAction.OnCloseClicked -> {
                analyticsHelper.logEvent(AnalyticsKey.QR_SCANNER_CANCEL)
            }

            QrScannerScreenAction.OnShowPermissionRationale -> {
                showPermissionRationale()
            }

            QrScannerScreenAction.OnPermissionRationaleDismissed -> {
                _showPermissionRationale.value = false
            }

            QrScannerScreenAction.OnPermissionRationaleAllowClicked -> {
                _showPermissionRationale.value = false
                analyticsHelper.logEvent(AnalyticsKey.CAMERA_PERMISSION_RATIONALE_DIALOG_ALLOW_CLICK)
            }

            QrScannerScreenAction.OnOpenAppSettingsClicked -> {
                _showPermissionRationale.value = false
                analyticsHelper.logEvent(AnalyticsKey.CAMERA_PERMISSION_SETTINGS_OPEN_CLICK)
            }

            QrScannerScreenAction.OnPermissionRationaleCancelClicked -> {
                _showPermissionRationale.value = false
                analyticsHelper.logEvent(AnalyticsKey.CAMERA_PERMISSION_RATIONALE_DIALOG_CANCEL_CLICK)
            }

            is QrScannerScreenAction.OnCameraPermissionResult -> {
                _isCameraPermissionAskedBefore.value = true
                viewModelScope.launch {
                    preferenceProvider.upsertBooleanPref(
                        CommonConstants.IS_CAMERA_PERMISSION_ASKED_BEFORE,
                        true,
                    )
                }
                analyticsHelper.logEvent(AnalyticsKey.CAMERA_PERMISSION_RESULT) {
                    param(
                        AnalyticsParam.RESULT,
                        toPermissionOutcome(action.isGranted, action.canAskAgain),
                    )
                }
                val isPermanentlyDenied = !action.isGranted && !action.canAskAgain
                _isPermissionPermanentlyDenied.value = isPermanentlyDenied
                if (isPermanentlyDenied) {
                    showPermissionRationale()
                }
            }
        }
    }

    /**
     * Shows the rationale dialog, logging the analytics event only when it was not already
     * visible.
     *
     * A single denial can reach here from more than one trigger, such as the permission result
     * and the screen resuming once the system prompt closes, so the event counts dialog
     * appearances rather than requests to show it.
     */
    private fun showPermissionRationale() {
        if (_showPermissionRationale.value) return
        _showPermissionRationale.value = true
        analyticsHelper.logEvent(AnalyticsKey.CAMERA_PERMISSION_RATIONALE_DIALOG_SHOW)
    }

    /**
     * Flattens a permission result into the three outcomes worth measuring separately.
     *
     * A retryable denial can still be recovered by asking again, while a permanent one can only
     * be recovered from system settings, so they are reported apart rather than as one denial.
     *
     * @param isGranted Whether the permission was granted.
     * @param canAskAgain Whether the system will still show a prompt on a further request.
     * @return Stable snake case identifier for the outcome.
     */
    private fun toPermissionOutcome(isGranted: Boolean, canAskAgain: Boolean): String = when {
        isGranted -> "granted"
        canAskAgain -> "denied"
        else -> "permanently_denied"
    }
}

/**
 * Maps a URI rejection reason to the value reported to analytics.
 *
 * Literals, not [Enum.name], so renaming an enum constant cannot silently split a metric across
 * two names.
 *
 * @return Stable snake case identifier for the reason.
 */
private fun TotpUriError.toAnalyticsValue(): String = when (this) {
    TotpUriError.MALFORMED_URI -> "malformed_uri"
    TotpUriError.UNSUPPORTED_OTP_TYPE -> "unsupported_otp_type"
    TotpUriError.INVALID_SECRET -> "invalid_secret"
    TotpUriError.UNSUPPORTED_ALGORITHM -> "unsupported_algorithm"
    TotpUriError.UNSUPPORTED_DIGITS -> "unsupported_digits"
    TotpUriError.UNSUPPORTED_PERIOD -> "unsupported_period"
}
