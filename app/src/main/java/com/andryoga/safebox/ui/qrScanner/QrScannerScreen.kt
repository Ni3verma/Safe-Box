package com.andryoga.safebox.ui.qrScanner

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.andryoga.safebox.R
import com.andryoga.safebox.totp.models.ParsedTotpData
import com.andryoga.safebox.totp.models.TotpUriError
import com.andryoga.safebox.ui.MainViewModel
import com.andryoga.safebox.ui.previewHelper.LightDarkModePreview
import com.andryoga.safebox.ui.qrScanner.components.CameraPermissionRationaleDialog
import com.andryoga.safebox.ui.qrScanner.components.UnsupportedQrCodeDialog
import com.andryoga.safebox.ui.theme.SafeBoxTheme
import com.andryoga.safebox.ui.utils.OnResume
import com.andryoga.safebox.ui.utils.OnStart
import com.andryoga.safebox.ui.utils.findActivity
import com.andryoga.safebox.ui.utils.openAppSettings
import com.google.mlkit.vision.barcode.BarcodeScanner
import timber.log.Timber
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Entry composable for the QR Code Scanner screen.
 *
 * @param mainViewModel Shared view model owning the app bar state for the home nav graph.
 * @param onQrCodeScanned Callback invoked once a usable TOTP QR code has been scanned and handed
 * to [ScannedTotpHolder]. The parsed payload is deliberately not passed here so the seed never
 * reaches the navigation layer.
 * @param onEnterKeyManually Callback invoked when the user selects manual credential entry.
 * @param onClose Callback invoked to dismiss or navigate back from the scanner.
 */
@Composable
fun QrScannerScreenRoot(
    mainViewModel: MainViewModel,
    onQrCodeScanned: () -> Unit,
    onEnterKeyManually: () -> Unit,
    onClose: () -> Unit,
) {
    val viewModel = hiltViewModel<QrScannerViewModel>()
    val uiState by viewModel.uiState.collectAsState()

    OnStart {
        mainViewModel.hideTopBar()
    }

    QrScannerScreen(
        uiState = uiState,
        barcodeScanner = viewModel.barcodeScanner,
        onAction = viewModel::onAction,
        onQrCodeScanned = { totpData ->
            viewModel.onAction(QrScannerScreenAction.OnQrCodeScanned(totpData))
            onQrCodeScanned()
        },
        onEnterKeyManually = {
            viewModel.onAction(QrScannerScreenAction.OnEnterKeyManuallyClicked)
            onEnterKeyManually()
        },
        onClose = {
            viewModel.onAction(QrScannerScreenAction.OnCloseClicked)
            onClose()
        },
    )
}

/**
 * Stateless viewfinder composable that manages camera permission requests, lifecycle binding,
 * and passes the active camera preview into [QrScannerViewfinderContent].
 */
@Composable
fun QrScannerScreen(
    uiState: QrScannerUiState,
    barcodeScanner: BarcodeScanner,
    onAction: (QrScannerScreenAction) -> Unit,
    onQrCodeScanned: (ParsedTotpData) -> Unit,
    onEnterKeyManually: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }

    var hasLaunchedInitialPrompt by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasCameraPermission = isGranted
            if (!isGranted) {
                Timber.w("Camera permission denied by user")
            }
            // Read after the denial rather than before the request. Beforehand, false is
            // ambiguous between "never asked" and "permanently denied", which is why a permission
            // revoked from system settings used to read as permanently denied. Granted results
            // report false too, hence the guard.
            val canAskAgain = !isGranted && ActivityCompat.shouldShowRequestPermissionRationale(
                context.findActivity(),
                Manifest.permission.CAMERA,
            )
            onAction(
                QrScannerScreenAction.OnCameraPermissionResult(
                    isGranted = isGranted,
                    canAskAgain = canAskAgain,
                ),
            )
        },
    )

    OnResume {
        val isGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        if (isGranted) {
            if (!hasCameraPermission) {
                hasCameraPermission = true
            }
        } else if (uiState.isCameraPermissionAskedBefore == true && !uiState.showPermissionRationale) {
            onAction(QrScannerScreenAction.OnShowPermissionRationale)
        }
    }

    LaunchedEffect(Unit) {
        onAction(QrScannerScreenAction.OnScannerVisible)
    }

    LaunchedEffect(hasCameraPermission, uiState.isCameraPermissionAskedBefore) {
        if (!hasCameraPermission && !hasLaunchedInitialPrompt) {
            val askedBefore = uiState.isCameraPermissionAskedBefore ?: return@LaunchedEffect
            hasLaunchedInitialPrompt = true
            if (askedBefore) {
                Timber.i("Camera permission previously asked; showing educational rationale dialog")
                onAction(QrScannerScreenAction.OnShowPermissionRationale)
            } else {
                Timber.i("Directly launching system camera permission prompt for the first time")
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    QrScannerViewfinderContent(
        uiState = uiState,
        onClose = onClose,
        onToggleTorch = { onAction(QrScannerScreenAction.OnToggleTorch) },
        onEnterKeyManually = onEnterKeyManually,
        onPermissionRationaleAllow = {
            onAction(QrScannerScreenAction.OnPermissionRationaleAllowClicked)
            permissionLauncher.launch(Manifest.permission.CAMERA)
        },
        onPermissionRationaleOpenSettings = {
            onAction(QrScannerScreenAction.OnOpenAppSettingsClicked)
            context.openAppSettings()
        },
        onPermissionRationaleCancel = {
            onAction(QrScannerScreenAction.OnPermissionRationaleCancelClicked)
        },
        onPermissionRationaleDismiss = {
            onAction(QrScannerScreenAction.OnPermissionRationaleDismissed)
        },
        onUnsupportedQrCodeDismiss = {
            onAction(QrScannerScreenAction.OnUnsupportedQrCodeDismissed)
        },
        cameraPreview = {
            if (hasCameraPermission) {
                QrCameraPreview(
                    barcodeScanner = barcodeScanner,
                    isTorchEnabled = uiState.isTorchEnabled,
                    isScanPaused = uiState.unsupportedQrError != null,
                    onCameraBound = { hasFlashUnit ->
                        onAction(QrScannerScreenAction.OnCameraBound(hasFlashUnit))
                    },
                    onTorchStateChanged = { isEnabled ->
                        onAction(QrScannerScreenAction.OnTorchStateChanged(isEnabled))
                    },
                    onQrCodeScanned = onQrCodeScanned,
                    onUnsupportedQrCode = { reason ->
                        onAction(QrScannerScreenAction.OnUnsupportedQrCodeScanned(reason))
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        },
    )
}

/**
 * Encapsulated CameraX preview composable that manages the CameraX [PreviewView],
 * background [ImageAnalysis.Analyzer] single-thread executor, hardware lifecycle binding,
 * async disposal guards, and flashlight/torch controls.
 *
 * @param isScanPaused Whether detection is currently suspended because an unusable code is being
 * explained to the user. Clearing it re-arms the analyzer without rebuilding the camera session.
 * @param onCameraBound Reports whether the bound camera has a flash unit, so the torch control is
 * only offered on hardware that can honour it.
 * @param onTorchStateChanged Reports the torch state read back from the camera, which is how the
 * flag recovers after CameraX turns the torch off on an unbind.
 */
@Composable
private fun QrCameraPreview(
    barcodeScanner: BarcodeScanner,
    isTorchEnabled: Boolean,
    isScanPaused: Boolean,
    onCameraBound: (hasFlashUnit: Boolean) -> Unit,
    onTorchStateChanged: (isEnabled: Boolean) -> Unit,
    onQrCodeScanned: (ParsedTotpData) -> Unit,
    onUnsupportedQrCode: (TotpUriError) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnQrCodeScanned by rememberUpdatedState(onQrCodeScanned)
    val currentOnUnsupportedQrCode by rememberUpdatedState(onUnsupportedQrCode)
    val currentOnCameraBound by rememberUpdatedState(onCameraBound)
    val currentOnTorchStateChanged by rememberUpdatedState(onTorchStateChanged)
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val isDisposed = remember { AtomicBoolean(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }

    val qrCodeAnalyzer = remember(barcodeScanner) {
        QrCodeAnalyzer(
            scanner = barcodeScanner,
            onQrCodeScanned = { currentOnQrCodeScanned(it) },
            onUnsupportedQrCode = { currentOnUnsupportedQrCode(it) },
        )
    }

    // Hoisted out of the binding callback so disposal can detach the analyzer from it.
    val imageAnalysis = remember {
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
    }

    LaunchedEffect(isScanPaused, qrCodeAnalyzer) {
        if (!isScanPaused) {
            qrCodeAnalyzer.reset()
        }
    }

    LaunchedEffect(camera) {
        // Only report a bound camera. Reporting false while camera is still null would drop the
        // torch control on first composition and again on every configuration change.
        val boundCamera = camera ?: return@LaunchedEffect
        currentOnCameraBound(boundCamera.cameraInfo.hasFlashUnit())
    }

    LaunchedEffect(isTorchEnabled, camera) {
        camera?.let {
            if (it.cameraInfo.hasFlashUnit()) {
                it.cameraControl.enableTorch(isTorchEnabled)
            }
        }
    }

    DisposableEffect(camera, lifecycleOwner) {
        val boundCamera = camera
        val torchState = boundCamera?.cameraInfo?.torchState
        val observer = Observer<Int> { state ->
            currentOnTorchStateChanged(state == TorchState.ON)
        }
        torchState?.observe(lifecycleOwner, observer)
        onDispose { torchState?.removeObserver(observer) }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener(
                {
                    if (isDisposed.get()) {
                        try {
                            cameraProviderFuture.get().unbindAll()
                        } catch (e: Exception) {
                            Timber.w(e, "Error unbinding CameraX when disposed")
                        }
                        return@addListener
                    }

                    try {
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }
                        imageAnalysis.setAnalyzer(cameraExecutor, qrCodeAnalyzer)

                        cameraProvider.unbindAll()
                        camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis,
                        )
                    } catch (e: Exception) {
                        Timber.e(e, "CameraX initialization or binding failed")
                    }
                },
                ContextCompat.getMainExecutor(ctx),
            )

            previewView
        },
        modifier = modifier,
    )

    DisposableEffect(cameraExecutor) {
        onDispose {
            isDisposed.set(true)
            camera = null
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener(
                {
                    try {
                        cameraProviderFuture.get().unbindAll()
                    } catch (e: Exception) {
                        Timber.w(e, "Error unbinding CameraX on dispose")
                    }
                    // Both steps must follow the unbind: CameraX keeps feeding frames to the
                    // analyzer until then, and a shut down executor rejects them.
                    imageAnalysis.clearAnalyzer()
                    cameraExecutor.shutdown()
                },
                ContextCompat.getMainExecutor(context),
            )
        }
    }
}

/**
 * Viewfinder UI rendering camera background slot, cutout overlay, top navigation controls,
 * bottom instruction with manual entry button, permission rationale dialog, and the unusable
 * QR code explanation.
 */
@Composable
fun QrScannerViewfinderContent(
    uiState: QrScannerUiState,
    onClose: () -> Unit,
    onToggleTorch: () -> Unit,
    onEnterKeyManually: () -> Unit,
    onPermissionRationaleAllow: () -> Unit,
    onPermissionRationaleOpenSettings: () -> Unit,
    onPermissionRationaleCancel: () -> Unit,
    onPermissionRationaleDismiss: () -> Unit,
    onUnsupportedQrCodeDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    cameraPreview: @Composable () -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        cameraPreview()

        // Viewfinder Cutout Overlay
        QrScannerOverlay(
            modifier = Modifier.fillMaxSize(),
            primaryColor = MaterialTheme.colorScheme.primary,
        )

        // Top Controls Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.close),
                    tint = Color.White,
                )
            }

            Text(
                text = stringResource(R.string.qr_scanner_title),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            if (uiState.hasFlashUnit) {
                IconButton(onClick = onToggleTorch) {
                    Icon(
                        imageVector = if (uiState.isTorchEnabled) {
                            Icons.Filled.FlashOn
                        } else {
                            Icons.Filled.FlashOff
                        },
                        contentDescription = stringResource(
                            if (uiState.isTorchEnabled) {
                                R.string.cd_turn_flash_off
                            } else {
                                R.string.cd_turn_flash_on
                            },
                        ),
                        tint = if (uiState.isTorchEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.White
                        },
                    )
                }
            } else {
                // Keeps the title centred now that the trailing control can be absent.
                Spacer(modifier = Modifier.size(48.dp))
            }
        }

        // Bottom Instruction & Manual Entry Button
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 32.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.qr_scanner_instruction),
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(24.dp))

            ElevatedButton(
                onClick = onEnterKeyManually,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.elevatedButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Icon(
                    imageVector = Icons.Filled.Keyboard,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.enter_key_manually),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        if (uiState.showPermissionRationale) {
            CameraPermissionRationaleDialog(
                isPermanentlyDenied = uiState.isPermissionPermanentlyDenied,
                onAllowClick = onPermissionRationaleAllow,
                onOpenSettingsClick = onPermissionRationaleOpenSettings,
                onCancelClick = onPermissionRationaleCancel,
                dismissDialogAction = onPermissionRationaleDismiss,
            )
        }

        uiState.unsupportedQrError?.let { reason ->
            UnsupportedQrCodeDialog(
                reason = reason,
                onDismiss = onUnsupportedQrCodeDismiss,
            )
        }
    }
}

/**
 * Visual viewfinder overlay providing a semi-transparent dark backdrop
 * with a transparent centered cutout and colored border frame.
 */
@Composable
private fun QrScannerOverlay(
    modifier: Modifier = Modifier,
    primaryColor: Color,
) {
    Canvas(
        modifier = modifier.graphicsLayer {
            compositingStrategy = CompositingStrategy.Offscreen
        },
    ) {
        val cutoutSize = (size.minDimension * 0.7f).coerceAtMost(300.dp.toPx())
        val left = (size.width - cutoutSize) / 2f
        val top = (size.height - cutoutSize) / 2.4f
        val cornerRadius = 20.dp.toPx()

        // Background mask
        drawRect(
            color = Color.Black.copy(alpha = 0.65f),
            size = size,
        )

        // Cutout hole
        val cutoutPath = Path().apply {
            addRoundRect(
                RoundRect(
                    rect = Rect(
                        offset = Offset(left, top),
                        size = Size(cutoutSize, cutoutSize),
                    ),
                    cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                ),
            )
        }

        drawPath(
            path = cutoutPath,
            color = Color.Transparent,
            blendMode = BlendMode.Clear,
        )

        // Target Frame Border
        drawRoundRect(
            color = primaryColor,
            topLeft = Offset(left, top),
            size = Size(cutoutSize, cutoutSize),
            cornerRadius = CornerRadius(cornerRadius, cornerRadius),
            style = Stroke(width = 3.dp.toPx()),
        )
    }
}

@LightDarkModePreview
@Composable
private fun QrScannerScreenPreview() {
    SafeBoxTheme {
        Surface {
            QrScannerViewfinderContent(
                uiState = QrScannerUiState(isTorchEnabled = false, hasFlashUnit = true),
                onClose = {},
                onToggleTorch = {},
                onEnterKeyManually = {},
                onPermissionRationaleAllow = {},
                onPermissionRationaleOpenSettings = {},
                onPermissionRationaleCancel = {},
                onPermissionRationaleDismiss = {},
                onUnsupportedQrCodeDismiss = {},
            )
        }
    }
}

@LightDarkModePreview
@Composable
private fun QrScannerScreenPreviewWithoutFlash() {
    SafeBoxTheme {
        Surface {
            QrScannerViewfinderContent(
                uiState = QrScannerUiState(isTorchEnabled = false, hasFlashUnit = false),
                onClose = {},
                onToggleTorch = {},
                onEnterKeyManually = {},
                onPermissionRationaleAllow = {},
                onPermissionRationaleOpenSettings = {},
                onPermissionRationaleCancel = {},
                onPermissionRationaleDismiss = {},
                onUnsupportedQrCodeDismiss = {},
            )
        }
    }
}
