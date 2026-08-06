package com.example.healthheatv2.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.healthheatv2.ui.theme.LocalAppColors
import com.example.healthheatv2.ui.viewmodel.ApiState
import com.example.healthheatv2.ui.viewmodel.ScannerViewModel

@Composable
fun ManualSearchScreen(
    viewModel: ScannerViewModel,
    onBackClick: () -> Unit,
    onSearchSuccess: () -> Unit
) {
    val colors = LocalAppColors.current
    val apiState by viewModel.apiState
    var barcodeInput by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf("") }

    LaunchedEffect(apiState) {
        if (apiState is ApiState.Success || apiState is ApiState.Error) onSearchSuccess()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
    ) {
        // ── Top Bar ──────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(colors.card)
                    .border(1.dp, colors.border, CircleShape)
                    .clickable { onBackClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = colors.textPrimary, modifier = Modifier.size(18.dp))
            }
            Text("Manual Entry", color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(32.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(colors.accentGreenSubtle),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.QrCode,
                    contentDescription = null,
                    tint = colors.accentGreen,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = "Enter Barcode",
                color = colors.textPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Type the barcode number from the product packaging",
                color = colors.textSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            Spacer(Modifier.height(36.dp))

            // Barcode Input Field
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.card)
                        .border(
                            1.dp,
                            if (validationError.isNotEmpty()) colors.accentRed.copy(0.5f)
                            else if (barcodeInput.isNotEmpty()) colors.accentGreen.copy(0.5f)
                            else colors.border,
                            RoundedCornerShape(16.dp)
                        )
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    BasicTextField(
                        value = barcodeInput,
                        onValueChange = {
                            barcodeInput = it.filter { c -> c.isDigit() }
                            validationError = ""
                        },
                        textStyle = TextStyle(
                            color = colors.textPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 3.sp
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        cursorBrush = SolidColor(colors.accentGreen),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        decorationBox = { inner ->
                            if (barcodeInput.isEmpty()) {
                                Text(
                                    "e.g. 8901234567890",
                                    color = colors.textHint,
                                    fontSize = 16.sp,
                                    letterSpacing = 2.sp
                                )
                            }
                            inner()
                        }
                    )
                }
                if (validationError.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(validationError, color = colors.accentRed, fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(16.dp))

            // Search Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        if (apiState is ApiState.Loading) colors.card else colors.accentGreen
                    )
                    .clickable(enabled = apiState !is ApiState.Loading) {
                        when {
                            barcodeInput.isBlank() -> validationError = "Please enter a barcode"
                            barcodeInput.length < 8 -> validationError = "Barcode too short (min 8 digits)"
                            else -> viewModel.lookupBarcode(barcodeInput.trim())
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (apiState is ApiState.Loading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = colors.accentGreen, strokeWidth = 2.dp)
                        Text("Analysing…", color = colors.textPrimary, fontSize = 15.sp)
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Analyse Product", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // Removed inline error state because it navigates to ProductScreen now

            Spacer(Modifier.height(32.dp))

            // Helper info box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.card)
                    .border(1.dp, colors.border, RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Filled.Info, contentDescription = null, tint = colors.accentBlue, modifier = Modifier.size(16.dp).padding(top = 1.dp))
                    Text(
                        text = "The barcode is the series of numbers printed below the barcode stripes on the product packaging. It's usually 8-13 digits.",
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}
