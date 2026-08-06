package com.example.healthheatv2.ui.screens

import android.Manifest
import androidx.camera.core.ImageAnalysis
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.healthheatv2.ui.theme.LocalAppColors
import com.example.healthheatv2.ui.viewmodel.ApiState
import com.example.healthheatv2.ui.viewmodel.ScannerViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun BarcodeScannerScreen(
    viewModel: ScannerViewModel,
    onScanSuccess: () -> Unit
) {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    when {
        cameraPermissionState.status.isGranted -> {
            CameraPreviewWithOverlay(viewModel = viewModel, onScanSuccess = onScanSuccess)
        }
        cameraPermissionState.status.shouldShowRationale -> {
            PermissionRationaleScreen(onRequestPermission = { cameraPermissionState.launchPermissionRequest() })
        }
        else -> {
            LaunchedEffect(Unit) { cameraPermissionState.launchPermissionRequest() }
            PermissionRequestScreen(onRequestPermission = { cameraPermissionState.launchPermissionRequest() })
        }
    }
}

@Composable
private fun CameraPreviewWithOverlay(
    viewModel: ScannerViewModel,
    onScanSuccess: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val colors = LocalAppColors.current

    val apiState by viewModel.apiState
    var lastDetectedBarcode by remember { mutableStateOf("") }

    LaunchedEffect(apiState) {
        if (apiState is ApiState.Success) onScanSuccess()
    }

    val cameraController = remember {
        LifecycleCameraController(context).apply {
            val barcodeScanner = BarcodeScanning.getClient(
                BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS).build()
            )
            setImageAnalysisAnalyzer(
                mainExecutor,
                MlKitAnalyzer(listOf(barcodeScanner), ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED, mainExecutor) { result ->
                    if (apiState is ApiState.Idle) {
                        val barcodes = result.getValue(barcodeScanner)
                        if (!barcodes.isNullOrEmpty()) {
                            val rawValue = barcodes.first().rawValue
                            if (rawValue != null && rawValue != lastDetectedBarcode) {
                                lastDetectedBarcode = rawValue
                                viewModel.lookupBarcode(rawValue)
                            }
                        }
                    }
                }
            )
            bindToLifecycle(lifecycleOwner)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera view
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx -> PreviewView(ctx).apply { this.controller = cameraController } }
        )

        // Scanning overlay
        ScannerOverlay(isScanning = apiState is ApiState.Idle)

        // Top bar: back button
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(16.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { viewModel.resetState() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(20.dp))
        }

        // Top center: title
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 16.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black.copy(0.6f))
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Text(
                "Point camera at barcode",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Bottom status area
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 40.dp)
                .fillMaxWidth()
        ) {
            when (val state = apiState) {
                is ApiState.Idle -> {
                    // Idle hint
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(32.dp))
                            .background(Color.Black.copy(0.6f))
                            .padding(horizontal = 24.dp, vertical = 14.dp)
                    ) {
                        Text(
                            text = "Align barcode within frame",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                is ApiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(32.dp))
                            .background(Color.Black.copy(0.7f))
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = colors.accentGreen,
                                strokeWidth = 2.5.dp
                            )
                            Text("Analysing nutrition…", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                is ApiState.Error -> {
                    ScanErrorCard(
                        message = state.message,
                        onDismiss = {
                            viewModel.resetState()
                            lastDetectedBarcode = ""
                        }
                    )
                }
                else -> {}
            }
        }
    }
}

// ─────────────────────────────────────────────────
//  Scanner Overlay with animated laser
// ─────────────────────────────────────────────────
@Composable
private fun ScannerOverlay(isScanning: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "laser")
    val laserY by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laserY"
    )

    // Breathing animation for corners
    val breathScale by infiniteTransition.animateFloat(
        initialValue = 0.98f, targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath"
    )
    val accentGreen = LocalAppColors.current.accentGreen
    Canvas(modifier = Modifier.fillMaxSize().scale(1f)) {
        val canvasW = size.width
        val canvasH = size.height

        // The scan box
        val boxW = canvasW * 0.75f * breathScale
        val boxH = boxW * 0.75f // Make it a neat square for barcodes/QR
        val boxLeft = (canvasW - boxW) / 2
        val boxTop = (canvasH - boxH) / 2
        
        // Semi-transparent dark overlay (except the scan box)
        val outerPath = androidx.compose.ui.graphics.Path().apply {
            addRect(androidx.compose.ui.geometry.Rect(0f, 0f, canvasW, canvasH))
            addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    left = boxLeft, top = boxTop, right = boxLeft + boxW, bottom = boxTop + boxH,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(60f, 60f)
                )
            )
            fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
        }
        drawPath(outerPath, Color.Black.copy(alpha = 0.65f))

        // Outline of the scan box
        drawRoundRect(
            color = Color.White.copy(alpha = 0.3f),
            topLeft = Offset(boxLeft, boxTop),
            size = Size(boxW, boxH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(60f, 60f),
            style = Stroke(width = 4f)
        )
        
        // Corner bracket accents
        val cornerLen = 60f
        val strokeW = 10f
        val cornerColor = accentGreen

        // Top-left
        drawArc(
            color = cornerColor,
            startAngle = 180f, sweepAngle = 90f, useCenter = false,
            topLeft = Offset(boxLeft, boxTop),
            size = Size(cornerLen, cornerLen),
            style = Stroke(width = strokeW, cap = StrokeCap.Round)
        )
        // Top-right
        drawArc(
            color = cornerColor,
            startAngle = 270f, sweepAngle = 90f, useCenter = false,
            topLeft = Offset(boxLeft + boxW - cornerLen, boxTop),
            size = Size(cornerLen, cornerLen),
            style = Stroke(width = strokeW, cap = StrokeCap.Round)
        )
        // Bottom-left
        drawArc(
            color = cornerColor,
            startAngle = 90f, sweepAngle = 90f, useCenter = false,
            topLeft = Offset(boxLeft, boxTop + boxH - cornerLen),
            size = Size(cornerLen, cornerLen),
            style = Stroke(width = strokeW, cap = StrokeCap.Round)
        )
        // Bottom-right
        drawArc(
            color = cornerColor,
            startAngle = 0f, sweepAngle = 90f, useCenter = false,
            topLeft = Offset(boxLeft + boxW - cornerLen, boxTop + boxH - cornerLen),
            size = Size(cornerLen, cornerLen),
            style = Stroke(width = strokeW, cap = StrokeCap.Round)
        )

        if (isScanning) {
            // Animated laser line inside the box
            val laserCurrentY = boxTop + 30f + (boxH - 60f) * laserY
            drawLine(
                brush = Brush.horizontalGradient(
                    listOf(Color.Transparent, accentGreen, Color.Transparent),
                    startX = boxLeft,
                    endX = boxLeft + boxW
                ),
                start = Offset(boxLeft + 20f, laserCurrentY),
                end = Offset(boxLeft + boxW - 20f, laserCurrentY),
                strokeWidth = 6f,
                cap = StrokeCap.Round
            )
            
            // Add a soft glow behind the laser
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, accentGreen.copy(alpha = 0.3f), Color.Transparent),
                    startY = laserCurrentY - 40f,
                    endY = laserCurrentY + 40f
                ),
                topLeft = Offset(boxLeft + 20f, laserCurrentY - 40f),
                size = Size(boxW - 40f, 80f)
            )
        }
    }
}

// ─────────────────────────────────────────────────
//  Error Card
// ─────────────────────────────────────────────────
@Composable
fun ScanErrorCard(message: String, onDismiss: () -> Unit) {
    val colors = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.card)
            .border(1.dp, colors.accentRed.copy(0.4f), RoundedCornerShape(20.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Scan Failed", color = colors.accentRed, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(message, color = colors.textSecondary, fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 18.sp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(colors.accentRed)
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Text("Try Again", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

// ─────────────────────────────────────────────────
//  Permission screens
// ─────────────────────────────────────────────────
@Composable
private fun PermissionRequestScreen(onRequestPermission: () -> Unit) {
    val colors = LocalAppColors.current
    Box(modifier = Modifier.fillMaxSize().background(colors.background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                modifier = Modifier.size(80.dp).background(colors.card, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = null, tint = colors.accentGreen, modifier = Modifier.size(36.dp))
            }
            Text("Camera Required", color = colors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Text("To scan barcodes and analyse nutrition, we need access to your camera.", color = colors.textSecondary, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(28.dp))
                    .background(colors.accentGreen)
                    .clickable { onRequestPermission() }
                    .padding(horizontal = 32.dp, vertical = 16.dp)
            ) {
                Text("Allow Camera Access", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun PermissionRationaleScreen(onRequestPermission: () -> Unit) {
    PermissionRequestScreen(onRequestPermission = onRequestPermission)
}

// Keep for compatibility — old ErrorCard ref
@Composable
fun ErrorCard(errorMessage: String, onDismiss: () -> Unit) {
    ScanErrorCard(message = errorMessage, onDismiss = onDismiss)
}

