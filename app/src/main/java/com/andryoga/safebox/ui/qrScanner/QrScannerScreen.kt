package com.andryoga.safebox.ui.qrScanner

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.andryoga.safebox.R
import com.andryoga.safebox.totp.models.ParsedTotpData
import com.andryoga.safebox.ui.previewHelper.LightDarkModePreview
import com.andryoga.safebox.ui.qrScanner.components.CameraPermissionRationaleDialog
import com.andryoga.safebox.ui.theme.SafeBoxTheme
import com.andryoga.safebox.ui.utils.findActivity
import com.google.mlkit.vision.barcode.BarcodeScanner
import timber.log.Timber
import java.util.concurrent.Executors

/**
 * Entry composable for the QR Code Scanner screen.
 *
 * @param onQrCodeScanned Callback invoked when a valid TOTP QR code is scanned and parsed.
 * @param onEnterKeyManually Callback invoked when the user selects manual credential entry.
 * @param onClose Callback invoked to dismiss or navigate back from the scanner.
 */
@Composable
fun QrScannerScreenRoot(
    onQrCodeScanned: (ParsedTotpData) -> Unit,
    onEnterKeyManually: () -> Unit,
    onClose: () -> Unit,
) {
    val viewModel = hiltViewModel<QrScannerViewModel>()
    val uiState by viewModel.uiState.collectAsState()

    QrScannerScreen(
        uiState = uiState,
        barcodeScanner = viewModel.barcodeScanner,
        onAction = viewModel::onAction,
        onQrCodeScanned = { totpData ->
            viewModel.onAction(QrScannerScreenAction.OnQrCodeScanned(totpData))
            onQrCodeScanned(totpData)
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
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasCameraPermission = isGranted
            if (!isGranted) {
                Timber.w("Camera permission denied by user")
            }
        },
    )

    var camera by remember { mutableStateOf<Camera?>(null) }

    LaunchedEffect(Unit) {
        onAction(QrScannerScreenAction.OnScannerVisible)
        if (!hasCameraPermission) {
            val activity = try {
                context.findActivity()
            } catch (_: Exception) {
                null
            }
            // Check if user previously rejected permission without selecting "Don't ask again".
            // If true, display educational rationale dialog before asking again per Android guidelines.
            val shouldShowRationale = activity?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
            } ?: false

            if (shouldShowRationale) {
                Timber.i("Camera permission previously denied; showing educational rationale dialog")
                onAction(QrScannerScreenAction.OnShowPermissionRationale)
            } else {
                Timber.i("Directly launching system camera permission prompt")
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    LaunchedEffect(uiState.isTorchEnabled) {
        camera?.let {
            if (it.cameraInfo.hasFlashUnit()) {
                it.cameraControl.enableTorch(uiState.isTorchEnabled)
            }
        }
    }

    QrScannerViewfinderContent(
        uiState = uiState,
        onClose = onClose,
        onToggleTorch = { onAction(QrScannerScreenAction.OnToggleTorch) },
        onEnterKeyManually = onEnterKeyManually,
        onPermissionRationaleAllow = { isRedirectingToSettings ->
            onAction(QrScannerScreenAction.OnPermissionRationaleAllowClicked(isRedirectingToSettings))
        },
        onPermissionRationaleCancel = {
            onAction(QrScannerScreenAction.OnPermissionRationaleCancelClicked)
        },
        onPermissionRationaleDismiss = {
            onAction(QrScannerScreenAction.OnPermissionRationaleDismissed)
        },
        cameraPreview = {
            if (hasCameraPermission) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }

                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        val cameraExecutor = Executors.newSingleThreadExecutor()

                        cameraProviderFuture.addListener(
                            {
                                val cameraProvider = cameraProviderFuture.get()
                                val preview = Preview.Builder().build().also {
                                    it.surfaceProvider = previewView.surfaceProvider
                                }

                                val imageAnalysis = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()
                                    .also { analysis ->
                                        analysis.setAnalyzer(
                                            cameraExecutor,
                                            QrCodeAnalyzer(
                                                scanner = barcodeScanner,
                                                onQrCodeScanned = onQrCodeScanned,
                                            ),
                                        )
                                    }

                                try {
                                    cameraProvider.unbindAll()
                                    camera = cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.DEFAULT_BACK_CAMERA,
                                        preview,
                                        imageAnalysis,
                                    )
                                } catch (e: Exception) {
                                    Timber.e(e, "Use case binding failed")
                                }
                            },
                            ContextCompat.getMainExecutor(ctx),
                        )

                        previewView
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                DisposableEffect(Unit) {
                    onDispose {
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                        if (cameraProviderFuture.isDone) {
                            try {
                                cameraProviderFuture.get().unbindAll()
                            } catch (e: Exception) {
                                Timber.w(e, "Error unbinding CameraX on dispose")
                            }
                        }
                    }
                }
            }
        },
    )
}

/**
 * Viewfinder UI rendering camera background slot, cutout overlay, top navigation controls,
 * bottom instruction with manual entry button, and permission rationale dialog.
 */
@Composable
fun QrScannerViewfinderContent(
    uiState: QrScannerUiState,
    onClose: () -> Unit,
    onToggleTorch: () -> Unit,
    onEnterKeyManually: () -> Unit,
    onPermissionRationaleAllow: (isRedirectingToSettings: Boolean) -> Unit,
    onPermissionRationaleCancel: () -> Unit,
    onPermissionRationaleDismiss: () -> Unit,
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

            IconButton(onClick = onToggleTorch) {
                Icon(
                    imageVector = if (uiState.isTorchEnabled) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                    contentDescription = stringResource(R.string.flash_toggle_description),
                    tint = if (uiState.isTorchEnabled) MaterialTheme.colorScheme.primary else Color.White,
                )
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
                onAllowClick = onPermissionRationaleAllow,
                onCancelClick = onPermissionRationaleCancel,
                dismissDialogAction = onPermissionRationaleDismiss,
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
    Canvas(modifier = modifier) {
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
                uiState = QrScannerUiState(isTorchEnabled = false),
                onClose = {},
                onToggleTorch = {},
                onEnterKeyManually = {},
                onPermissionRationaleAllow = {},
                onPermissionRationaleCancel = {},
                onPermissionRationaleDismiss = {},
            )
        }
    }
}
