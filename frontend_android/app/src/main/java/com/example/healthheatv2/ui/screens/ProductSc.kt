package com.example.healthheatv2.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.example.healthheatv2.R
import kotlinx.coroutines.delay
import com.example.healthheatv2.network.AlternativeProduct
import com.example.healthheatv2.network.FoodResponse
import com.example.healthheatv2.network.IngredientAnalysis
import com.example.healthheatv2.ui.components.UserProfileAvatar
import com.example.healthheatv2.ui.theme.AppColors
import com.example.healthheatv2.ui.theme.LocalAppColors
import com.example.healthheatv2.ui.viewmodel.ApiState
import com.example.healthheatv2.ui.viewmodel.AuthViewModel
import com.example.healthheatv2.ui.viewmodel.ScannerViewModel
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import android.util.Base64
import java.io.ByteArrayOutputStream

fun Bitmap.toBase64(): String {
    val byteArrayOutputStream = ByteArrayOutputStream()
    this.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream)
    val byteArray = byteArrayOutputStream.toByteArray()
    return Base64.encodeToString(byteArray, Base64.DEFAULT)
}

enum class CaptureState { NONE, PRODUCT_FRONT, PRODUCT_LABEL }

@Composable
fun ProductScreen(
    viewModel: ScannerViewModel,
    onScanAnother: () -> Unit,
    onViewDetails: () -> Unit,
) {
    val apiState by viewModel.apiState
    val colors = LocalAppColors.current

    var captureState by remember { mutableStateOf(CaptureState.NONE) }
    var productImageBase64 by remember { mutableStateOf<String?>(null) }
    var capturedOcrText by remember { mutableStateOf("") }
    var isProcessingOCR by remember { mutableStateOf(false) }

    when (captureState) {
        CaptureState.PRODUCT_FRONT -> {
            CameraCaptureScreen(
                title = "Capture Product Front",
                onCapture = { bitmap ->
                    productImageBase64 = bitmap.toBase64()
                    captureState = CaptureState.PRODUCT_LABEL
                },
                onCancel = { captureState = CaptureState.NONE }
            )
            return
        }
        CaptureState.PRODUCT_LABEL -> {
            CameraCaptureScreen(
                title = "Capture Product Label (Ingredients)",
                onCapture = { bitmap ->
                    isProcessingOCR = true
                    captureState = CaptureState.NONE
                    // Process OCR
                    val image = InputImage.fromBitmap(bitmap, 0)
                    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    recognizer.process(image)
                        .addOnSuccessListener { visionText ->
                            capturedOcrText = visionText.text.replace("\n", ", ")
                            isProcessingOCR = false
                        }
                        .addOnFailureListener {
                            isProcessingOCR = false
                        }
                },
                onCancel = { captureState = CaptureState.NONE }
            )
            return
        }
        CaptureState.NONE -> {
            // Proceed to render ProductScreen normally
        }
    }

    when (val state = apiState) {
        is ApiState.Success -> {
            val product = state.data
        val smashThreshold by com.example.healthheatv2.data.RemoteConfigManager.smashThreshold.collectAsState()
        val score = product.healthScore ?: 0
        val isSmash = score >= smashThreshold
        val verdictColor = if (isSmash) colors.accentGreen else colors.accentRed

        val scrollState = androidx.compose.foundation.lazy.rememberLazyListState()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background),
        ) {
            // ── Dynamic Background ────────────
            AnimatedAmbientBackground(verdictColor = verdictColor)

            LazyColumn(
                state = scrollState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                // ── Hero ──────────────────────────
                item {
                    ProductHero(
                        product = product,
                        verdictColor = verdictColor,
                        onScanAnother = onScanAnother,
                        scrollState = scrollState,
                        onRequestCamera = { captureState = CaptureState.PRODUCT_FRONT },
                        capturedOcrText = capturedOcrText,
                        isProcessingOCR = isProcessingOCR
                    ) { newName, newIngredients ->
                        viewModel.contribute(viewModel.lastScannedBarcode, newName, newIngredients, productImageBase64)
                    }
                }

                // ── Verdict + Score row ───────────
                val isNonFood = (product.healthScore == 0) && (product.isGoodForHealth == false) && (product.safeConsumptionFrequency == "Not applicable")
                
                item {
                    Spacer(Modifier.height(20.dp))
                    if (isNonFood) {
                        NonFoodCard(modifier = Modifier.padding(horizontal = 20.dp))
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            VerdictCard(
                                modifier = Modifier.weight(1.1f),
                                product = product,
                                verdictColor = verdictColor,
                                isSmash = isSmash
                            )
                            ScoreGauge(
                                modifier = Modifier.weight(0.9f),
                                score = product.healthScore ?: 0,
                                onClick = onViewDetails
                            )
                        }
                    }
                }

                // ── Official Ratings ──────────────
                item {
                    Spacer(Modifier.height(20.dp))
                    SectionTitle("Official Ratings", padding = 20.dp)
                    Spacer(Modifier.height(12.dp))
                    OfficialRatingsRow(
                        nutriScore = product.nutriScore,
                        novaGroup = product.novaGroup,
                        ecoScore = product.ecoscoreGrade
                    )
                }

                // ── AI Summary ────────────────────
                val summary = product.summary
                if (!summary.isNullOrEmpty()) {
                    item {
                        Spacer(Modifier.height(24.dp))
                        SectionTitle("AI Analysis", padding = 20.dp)
                        Spacer(Modifier.height(12.dp))
                        AISummaryCard(summary = summary)
                    }
                }

                // ── Ingredient Forensics ──────────
                val ingredients = product.ingredientsAnalysis ?: emptyList()
                item {
                    Spacer(Modifier.height(24.dp))
                    SectionTitle("Ingredient Analysis", padding = 20.dp)
                    Spacer(Modifier.height(4.dp))
                    
                    if (product.ingredientsText.isNullOrEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.accentAmber.copy(alpha = 0.1f))
                                .border(1.dp, colors.accentAmber.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .padding(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Filled.WarningAmber, contentDescription = "Warning", tint = colors.accentAmber)
                                Text("Incomplete Data: Ingredients are missing from the global database. Score confidence is reduced.", color = colors.accentAmber, fontSize = 14.sp, lineHeight = 20.sp)
                            }
                        }
                    } else if (ingredients.isNotEmpty()) {
                        Text(
                            "${ingredients.count { it.status?.uppercase() == "GOOD" }} good · " +
                            "${ingredients.count { it.status?.uppercase() == "NEUTRAL" }} neutral · " +
                            "${ingredients.count { it.status?.uppercase() == "BAD" }} bad",
                            color = colors.textSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(colors.card)
                        ) {
                            ingredients.forEachIndexed { index, ingredient ->
                                IngredientRow(ingredient = ingredient, index = index)
                                if (index < ingredients.lastIndex) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 20.dp)
                                            .height(1.dp)
                                            .background(colors.border)
                                    )
                                }
                            }
                        }
                    } else {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "No ingredient analysis available.",
                            color = colors.textSecondary,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                }

                // ── Alternatives ──────────────────
                val alternatives = product.alternatives ?: emptyList()
                if (alternatives.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(24.dp))
                        SectionTitle("Better Alternatives", padding = 20.dp)
                        Spacer(Modifier.height(12.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(alternatives) { alt ->
                                AlternativeCard(alt = alt)
                            }
                        }
                    }
                }

                // ── Nutrient Danger Map ───────────
                val nutrientLevels = product.nutrientLevels
                if (!nutrientLevels.isNullOrEmpty()) {
                    item {
                        Spacer(Modifier.height(24.dp))
                        SectionTitle("Nutrient Levels", padding = 20.dp)
                        Spacer(Modifier.height(12.dp))
                        NutrientLevelsCard(nutrientLevels = nutrientLevels)
                    }
                }

                // ── Full details CTA ──────────────
                item {
                    Spacer(Modifier.height(24.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.accentBlueSubtle)
                            .border(1.dp, colors.accentBlue.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                            .clickable { onViewDetails() }
                            .padding(18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Filled.Science, contentDescription = null, tint = colors.accentBlue, modifier = Modifier.size(20.dp))
                            Text(
                                "Full Nutrition Report & Raw Data",
                                color = colors.accentBlue,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = colors.accentBlue, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // ── Floating Scan Another Button ──────
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 32.dp, vertical = 24.dp)
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(colors.textPrimary)
                    .clickable { onScanAnother() },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Filled.QrCodeScanner, contentDescription = null, tint = colors.background, modifier = Modifier.size(20.dp))
                    Text("Scan Another", color = colors.background, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
    is ApiState.Error -> {
        ProductNotFoundScreen(
            errorMessage = state.message,
            onScanAnother = onScanAnother,
            onRequestCamera = { captureState = CaptureState.PRODUCT_FRONT },
            capturedOcrText = capturedOcrText,
            isProcessingOCR = isProcessingOCR,
            onContribute = { name, ingredients ->
                viewModel.contribute(viewModel.lastScannedBarcode, name, ingredients, productImageBase64)
            }
        )
    }
    else -> {
        Box(Modifier.fillMaxSize().background(colors.background), contentAlignment = Alignment.Center) {
            androidx.compose.material3.CircularProgressIndicator(color = colors.accentBlue)
        }
    }
}
}

@Composable
private fun ProductNotFoundScreen(
    errorMessage: String, 
    onScanAnother: () -> Unit,
    onRequestCamera: () -> Unit,
    capturedOcrText: String,
    isProcessingOCR: Boolean,
    onContribute: (String, String) -> Unit
) {
    val colors = LocalAppColors.current
    
    // State for Contribution Dialog
    var showDialog by remember { mutableStateOf(false) }
    var ocrText by remember { mutableStateOf(capturedOcrText) }
    var productName by remember { mutableStateOf("") }

    // When OCR text updates from parent camera, update local and show dialog
    LaunchedEffect(capturedOcrText) {
        if (capturedOcrText.isNotEmpty()) {
            ocrText = capturedOcrText
            showDialog = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Broken barcode graphic
            Icon(
                imageVector = Icons.Filled.QrCodeScanner,
                contentDescription = "Not Found",
                modifier = Modifier.size(80.dp),
                tint = colors.textSecondary.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Product Not Found",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = if (errorMessage.contains("404")) "This item isn't in our global database yet." else errorMessage,
                fontSize = 15.sp,
                color = colors.textSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 22.sp
            )
            
            Spacer(modifier = Modifier.height(40.dp))
            
            // "Take a Picture Instead" / Contribute CTA
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(if (isProcessingOCR) colors.card else colors.accentBlue)
                    .clickable(enabled = !isProcessingOCR) { onRequestCamera() },
                contentAlignment = Alignment.Center
            ) {
                if (isProcessingOCR) {
                    androidx.compose.material3.CircularProgressIndicator(
                        color = colors.accentBlue,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = colors.background, modifier = Modifier.size(20.dp))
                                Text("Take a Photo of the Product Label", color = colors.background, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }
    }

    // Contribution Dialog
    if (showDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor = colors.card,
            title = {
                Text(
                    "Contribute to Database",
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            },
            text = {
                Column {
                    Text(
                        "Please verify the extracted ingredients and provide a product name.",
                        color = colors.textSecondary,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = productName,
                        onValueChange = { productName = it },
                        label = { Text("Product Name", color = colors.textHint) },
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                            focusedBorderColor = colors.accentBlue,
                            unfocusedBorderColor = colors.border
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(16.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = ocrText,
                        onValueChange = { ocrText = it },
                        label = { Text("Ingredients (comma separated)", color = colors.textHint) },
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                            focusedBorderColor = colors.accentBlue,
                            unfocusedBorderColor = colors.border
                        ),
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        if (productName.isNotBlank() && ocrText.isNotBlank()) {
                            onContribute(productName, ocrText)
                            showDialog = false
                        }
                    }
                ) {
                    Text("Submit to Database", color = colors.accentBlue, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }
}

// ─────────────────────────────────────────────────
//  Hero Section
// ─────────────────────────────────────────────────
@Composable
private fun ProductHero(
    product: FoodResponse,
    verdictColor: Color,
    onScanAnother: () -> Unit,
    scrollState: LazyListState,
    onRequestCamera: () -> Unit,
    capturedOcrText: String,
    isProcessingOCR: Boolean,
    onContribute: (String, String) -> Unit
) {
    val colors = LocalAppColors.current

    // Parallax calculation
    val parallaxOffset by remember {
        derivedStateOf {
            val scrollOffset = if (scrollState.firstVisibleItemIndex == 0) scrollState.firstVisibleItemScrollOffset else 0
            scrollOffset * 0.5f
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
    ) {
        // Background: image or coloured gradient
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(product.imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "Product",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationY = parallaxOffset },
                error = {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(verdictColor.copy(alpha = 0.3f), colors.background)
                                )
                            )
                    ) {
                        Text(
                            text = product.name?.take(1)?.uppercase() ?: "?",
                            color = verdictColor.copy(alpha = 0.5f),
                            fontSize = 80.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            )

        // Dark gradient overlay at bottom
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Transparent, colors.background),
                        startY = 0f
                    )
                )
        )

        // Back button (top-left)
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(16.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(colors.background.copy(alpha = 0.7f))
                .clickable { onScanAnother() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.textPrimary, modifier = Modifier.size(20.dp))
        }

        // Product name at bottom
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            if (!product.brand.isNullOrEmpty()) {
                Text(
                    text = product.brand.uppercase(),
                    color = verdictColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Spacer(Modifier.height(4.dp))
            }
            var showRenameDialog by remember { mutableStateOf(false) }
            var ocrText by remember { mutableStateOf(capturedOcrText) }
            var newName by remember { mutableStateOf(if (product.name == "Unknown Product") "" else product.name ?: "") }

            LaunchedEffect(capturedOcrText) {
                if (capturedOcrText.isNotEmpty()) {
                    ocrText = capturedOcrText
                    showRenameDialog = true
                }
            }

            val isUnknown = (product.name.isNullOrEmpty()) || (product.name == "Unknown Product")

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = product.name ?: "Unidentified Item",
                    color = if (colors.isDark) Color.White else colors.textPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 30.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (isUnknown) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(colors.accentBlue.copy(alpha = 0.2f))
                            .clickable { onRequestCamera() }, 
                        contentAlignment = Alignment.Center
                    ) {
                        if (isProcessingOCR) {
                            androidx.compose.material3.CircularProgressIndicator(
                                color = colors.accentBlue,
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Filled.CameraAlt, contentDescription = "Contribute", tint = colors.accentBlue, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            if (showRenameDialog) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showRenameDialog = false },
                    title = { Text("Contribute to Database") },
                    text = {
                        Column {
                            Text(
                                "Please capture the product label image. We will extract ingredients or nutrition facts from the label. The extracted label text is optional, but helpful for faster analysis.",
                                color = colors.textSecondary,
                                fontSize = 14.sp
                            )
                            Spacer(Modifier.height(16.dp))
                            androidx.compose.material3.OutlinedTextField(
                                value = newName,
                                onValueChange = { newName = it },
                                label = { Text("Product Name", color = colors.textHint) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(16.dp))
                            androidx.compose.material3.OutlinedTextField(
                                value = ocrText,
                                onValueChange = { ocrText = it },
                                label = { Text("Label text / Ingredients (optional)", color = colors.textHint) },
                                placeholder = { Text("Optional: label ingredients, nutrients or nutrition facts") },
                                minLines = 4,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(
                            onClick = {
                                if (newName.isNotBlank()) {
                                    val finalName = newName
                                    onContribute(finalName, ocrText)
                                    showRenameDialog = false
                                }
                            }
                        ) { Text("Submit Label for Analysis", color = colors.accentBlue) }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = { showRenameDialog = false }) {
                            Text("Cancel", color = colors.textSecondary)
                        }
                    },
                    containerColor = colors.card,
                    titleContentColor = colors.textPrimary,
                    textContentColor = colors.textPrimary
                )
            }
            if (!product.quantity.isNullOrEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = product.quantity,
                    color = if (colors.isDark) Color.White.copy(0.6f) else colors.textSecondary,
                    fontSize = 13.sp
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────
//  Verdict Card
// ─────────────────────────────────────────────────
@Composable
private fun VerdictCard(
    modifier: Modifier,
    product: FoodResponse,
    verdictColor: Color,
    isSmash: Boolean
) {
    val colors = LocalAppColors.current



    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(colors.card)
            .border(1.dp, verdictColor.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Text("VERDICT", color = colors.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (isSmash) "SMASH" else "PASS",
            color = verdictColor,
            fontSize = 34.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-1).sp
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(verdictColor.copy(alpha = 0.12f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = if (isSmash) "✓ Eat it!" else "✗ Avoid",
                color = verdictColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        if (!product.healthReason.isNullOrEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = product.healthReason,
                color = colors.textSecondary,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (!product.safeConsumptionFrequency.isNullOrEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Filled.Schedule, contentDescription = null, tint = colors.accentAmber, modifier = Modifier.size(12.dp))
                Text(
                    text = product.safeConsumptionFrequency,
                    color = colors.accentAmber,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────
//  Score Gauge
// ─────────────────────────────────────────────────
@Composable
private fun ScoreGauge(modifier: Modifier, score: Int, onClick: () -> Unit) {
    val colors = LocalAppColors.current

    val animatedScore = remember { Animatable(0f) }
    LaunchedEffect(score) {
        animatedScore.animateTo(
            targetValue = score.toFloat(),
            animationSpec = tween(1200, easing = FastOutSlowInEasing)
        )
    }
    val scoreColor = when {
        score >= 70 -> colors.accentGreen
        score >= 40 -> colors.accentAmber
        else -> colors.accentRed
    }



    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(colors.card)
            .border(1.dp, colors.border, RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("SCORE", color = colors.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, modifier = Modifier.align(Alignment.Start))
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 18f
                drawArc(
                    color = colors.card.copy(alpha = 0f),
                    startAngle = 135f, sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth * 1.5f, cap = StrokeCap.Round)
                )
                // Background track
                drawArc(
                    color = if (colors.isDark) Color.White.copy(0.08f) else Color.Black.copy(0.08f),
                    startAngle = 135f, sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
                // Score arc
                drawArc(
                    color = scoreColor,
                    startAngle = 135f,
                    sweepAngle = 270f * (animatedScore.value / 100f),
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = animatedScore.value.toInt().toString(),
                    color = scoreColor,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text("/ 100", color = colors.textSecondary, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("Tap for full report", color = colors.textHint, fontSize = 10.sp)
    }
}

// ─────────────────────────────────────────────────
//  Official Ratings Row
// ─────────────────────────────────────────────────
@Composable
private fun OfficialRatingsRow(nutriScore: String?, novaGroup: Int?, ecoScore: String?) {
    val colors = LocalAppColors.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Top row: NutriScore + EcoScore
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val nutriGrade = nutriScore?.uppercase()?.takeIf { it.isNotBlank() && it != "?" } ?: "N/A"
            val nutriColor = nutriScoreColor(nutriGrade, colors)
            GradeCard(
                modifier = Modifier.weight(1f),
                label = "NUTRI-SCORE",
                grade = nutriGrade,
                description = nutriScoreDesc(nutriGrade),
                color = nutriColor
            )

            val ecoGrade = ecoScore?.uppercase()?.takeIf { it != "UNKNOWN" && it != "NOT-APPLICABLE" }
            if (ecoGrade != null) {
                val ecoColor = nutriScoreColor(ecoGrade, colors)
                GradeCard(
                    modifier = Modifier.weight(1f),
                    label = "ECO-SCORE",
                    grade = ecoGrade,
                    description = ecoScoreDesc(ecoGrade),
                    color = ecoColor
                )
            }
        }
        // Bottom row: NOVA full-width
        val novaColor = when (novaGroup) {
            1 -> colors.accentGreen
            2 -> Color(0xFF52BE80)
            3 -> colors.accentAmber
            4 -> colors.accentRed
            else -> colors.textSecondary
        }
        val novaDesc = when (novaGroup) {
            1 -> "Unprocessed or minimally processed"
            2 -> "Processed culinary ingredients"
            3 -> "Processed foods"
            4 -> "Ultra-processed foods — limit consumption"
            else -> "Processing level unknown"
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(novaColor.copy(0.08f))
                .border(1.dp, novaColor.copy(0.25f), RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(novaColor.copy(0.15f))
                    .border(1.5.dp, novaColor.copy(0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = novaGroup?.toString() ?: "N/A",
                    color = novaColor,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Column {
                Text("NOVA GROUP", color = colors.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text(novaDesc, color = colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun GradeCard(modifier: Modifier, label: String, grade: String, description: String, color: Color) {
    val colors = LocalAppColors.current

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colors.card)
            .border(1.dp, color.copy(0.25f), RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(color)
                .border(1.dp, color.copy(0.3f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(grade, color = Color.White, fontSize = if (grade.length > 1) 16.sp else 24.sp, fontWeight = FontWeight.ExtraBold)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = colors.textSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text(description, color = colors.textPrimary, fontSize = 10.sp, lineHeight = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

private fun nutriScoreDesc(grade: String) = when (grade) {
    "A" -> "Excellent nutrition"
    "B" -> "Good nutrition"
    "C" -> "Average nutrition"
    "D" -> "Poor nutrition"
    "E" -> "Bad nutrition"
    else -> "Not rated"
}

private fun ecoScoreDesc(grade: String) = when (grade) {
    "A" -> "Very low impact"
    "B" -> "Low impact"
    "C" -> "Moderate impact"
    "D" -> "High impact"
    "E" -> "Very high impact"
    else -> "Not assessed"
}

// ─────────────────────────────────────────────────
//  Nutrient Levels Card
// ─────────────────────────────────────────────────
@Composable
private fun NutrientLevelsCard(nutrientLevels: Map<String, String>) {
    val colors = LocalAppColors.current



    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(colors.card)
            .border(1.dp, colors.border, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        nutrientLevels.entries.forEachIndexed { index, (nutrient, level) ->
            val levelColor = when (level.lowercase()) {
                "low" -> colors.accentGreen
                "moderate" -> colors.accentAmber
                "high" -> colors.accentRed
                else -> colors.textSecondary
            }
            val levelLabel = level.replaceFirstChar { it.uppercase() }
            val levelProgress = when (level.lowercase()) {
                "low" -> 0.25f
                "moderate" -> 0.6f
                "high" -> 1f
                else -> 0f
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = nutrient.replace("-", " ").split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } },
                    color = colors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                // Progress bar
                Box(
                    modifier = Modifier
                        .width(80.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (colors.isDark) Color.White.copy(0.08f) else Color.Black.copy(0.08f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(levelProgress)
                            .clip(RoundedCornerShape(3.dp))
                            .background(levelColor)
                    )
                }
                Text(
                    text = levelLabel,
                    color = levelColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(56.dp)
                )
            }

            if (index < nutrientLevels.size - 1) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
            }
        }
    }
}

// ─────────────────────────────────────────────────
//  AI Summary Card
// ─────────────────────────────────────────────────
@Composable
private fun AISummaryCard(summary: String) {
    val colors = LocalAppColors.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(colors.card)
            .border(1.dp, colors.border, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = colors.accentAmber, modifier = Modifier.size(16.dp))
            Text("AI Insight", color = colors.accentAmber, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        Text(summary, color = colors.textPrimary, fontSize = 13.sp, lineHeight = 20.sp)
    }
}

// ─────────────────────────────────────────────────
//  Ingredient Row
// ─────────────────────────────────────────────────
@Composable
private fun IngredientRow(ingredient: IngredientAnalysis, index: Int) {
    val colors = LocalAppColors.current
    val status = ingredient.status?.uppercase() ?: "NEUTRAL"
    val statusColor = when (status) {
        "GOOD" -> colors.accentGreen
        "BAD" -> colors.accentRed
        else -> colors.accentAmber
    }
    val statusEmoji = when (status) {
        "GOOD" -> "✓"
        "BAD" -> "✗"
        else -> "~"
    }
    var expanded by remember { mutableStateOf(false) }

    // Stagger animation
    var visible by remember { mutableStateOf(value = false) }
    LaunchedEffect(Unit) {
        delay(index * 40L)
        visible = true
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .animateContentSize(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Status indicator
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(statusColor.copy(0.12f))
                    .border(1.dp, statusColor.copy(0.3f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(statusEmoji, color = statusColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = ingredient.name ?: "Unknown ingredient",
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (!ingredient.quantity.isNullOrBlank() && ingredient.quantity.uppercase() != "UNKNOWN") {
                    Text(
                        text = ingredient.quantity,
                        color = colors.textSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            // Status badge + expand icon
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(statusColor.copy(0.12f))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(status, color = statusColor, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp)
                }
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = colors.textHint,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Reason (always show short, expand for full)
        if (!ingredient.reason.isNullOrBlank()) {
            Text(
                text = ingredient.reason,
                color = colors.textSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 44.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────
//  Alternative Card
// ─────────────────────────────────────────────────
@Composable
private fun AlternativeCard(alt: AlternativeProduct) {
    val colors = LocalAppColors.current
    val scoreColor = nutriScoreColor(alt.nutriScore?.uppercase() ?: "?", colors)

    Column(
        modifier = Modifier
            .width(130.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.card)
            .border(1.dp, colors.border, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Image or placeholder
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(scoreColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(alt.imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = alt.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                error = {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = if (!alt.name.isNullOrEmpty()) alt.name[0].toString().uppercase() else "?",
                            color = scoreColor,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            )
        }
        Text(alt.name ?: "Product", color = colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, lineHeight = 16.sp)
        Text(alt.brand ?: "", color = colors.textSecondary, fontSize = 11.sp, maxLines = 1)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(scoreColor.copy(alpha = 0.12f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(alt.nutriScore?.uppercase() ?: "?", color = scoreColor, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

// ─────────────────────────────────────────────────
//  Helpers
// ─────────────────────────────────────────────────
@Composable
fun SectionTitle(title: String, padding: androidx.compose.ui.unit.Dp = 0.dp) {
    val colors = LocalAppColors.current
    Text(
        text = title,
        color = colors.textPrimary,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = padding)
    )
}

// ─────────────────────────────────────────────────
//  Animated Ambient Background
// ─────────────────────────────────────────────────
@Composable
private fun AnimatedAmbientBackground(verdictColor: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "ambient")

    val offsetX1 by infiniteTransition.animateFloat(
        initialValue = -0.2f, targetValue = 1.2f,
        animationSpec = infiniteRepeatable(tween(12000, easing = LinearEasing), RepeatMode.Reverse),
        label = "x1"
    )
    val offsetY1 by infiniteTransition.animateFloat(
        initialValue = -0.1f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(15000, easing = LinearEasing), RepeatMode.Reverse),
        label = "y1"
    )

    val offsetX2 by infiniteTransition.animateFloat(
        initialValue = 1.2f, targetValue = -0.2f,
        animationSpec = infiniteRepeatable(tween(18000, easing = LinearEasing), RepeatMode.Reverse),
        label = "x2"
    )
    val offsetY2 by infiniteTransition.animateFloat(
        initialValue = 0.8f, targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Reverse),
        label = "y2"
    )

    val colors = LocalAppColors.current
    val isDark = colors.isDark

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        val orbColor = if (isDark) verdictColor.copy(alpha = 0.15f) else verdictColor.copy(alpha = 0.08f)

        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(orbColor, Color.Transparent),
                center = Offset(width * offsetX1, height * offsetY1),
                radius = width * 1.2f
            )
        )

        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(orbColor, Color.Transparent),
                center = Offset(width * offsetX2, height * offsetY2),
                radius = width * 1.0f
            )
        )
    }
}
@Composable
private fun NonFoodCard(modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.card)
            .padding(24.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Filled.Block,
                contentDescription = "Not Food",
                tint = colors.textSecondary.copy(alpha = 0.5f),
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Not a Food Item",
                color = colors.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "This item is not a food or beverage and cannot be nutritionally evaluated.",
                color = colors.textSecondary,
                fontSize = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}