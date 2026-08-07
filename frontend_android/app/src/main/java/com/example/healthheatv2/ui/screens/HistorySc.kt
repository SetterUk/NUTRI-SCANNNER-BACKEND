package com.example.healthheatv2.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.example.healthheatv2.R
import com.example.healthheatv2.data.ProductCacheEntity
import com.example.healthheatv2.ui.components.UserProfileAvatar
import com.example.healthheatv2.ui.theme.LocalAppColors
import com.example.healthheatv2.ui.viewmodel.AuthViewModel
import com.example.healthheatv2.ui.viewmodel.ScannerViewModel
import com.example.healthheatv2.ui.viewmodel.ThemeViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    viewModel: ScannerViewModel,
    authViewModel: AuthViewModel,
    themeViewModel: ThemeViewModel,
    onBackClick: () -> Unit,
    onProductSelected: () -> Unit,
    onProfileClick: () -> Unit,
    onLogout: () -> Unit
) {
    val colors = LocalAppColors.current
    val history by viewModel.searchHistory
    val isDark by themeViewModel.isDark

    var searchQuery by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf("All") }

    val filters = listOf("All", "SMASH", "PASS", "Today")

    val smashThreshold by com.example.healthheatv2.data.RemoteConfigManager.smashThreshold.collectAsState()
    val filteredHistory = remember(searchQuery, activeFilter, history, smashThreshold) {
        var list = history
        // Filter by verdict
        if (activeFilter == "SMASH") list = list.filter { (it.foodResponse.healthScore ?: 0) >= smashThreshold }
        else if (activeFilter == "PASS") list = list.filter { (it.foodResponse.healthScore ?: 0) < smashThreshold }
        else if (activeFilter == "Today") {
            val todayStart = System.currentTimeMillis() - 86_400_000L
            list = list.filter { it.scannedAt >= todayStart }
        }
        // Filter by search query
        if (searchQuery.isNotBlank()) {
            list = list.filter {
                it.foodResponse.name?.contains(searchQuery, ignoreCase = true) == true ||
                it.foodResponse.brand?.contains(searchQuery, ignoreCase = true) == true
            }
        }
        list
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        // ── Top Bar ──────────────────────────────
        item {
            HistoryTopBar(
                authViewModel = authViewModel,
                isDark = isDark,
                onThemeToggle = { themeViewModel.toggleTheme() },
                onProfileClick = onProfileClick,
                onLogout = onLogout
            )
        }

        // ── Header ───────────────────────────────
        item {
            Spacer(Modifier.height(8.dp))
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text("Your History", color = colors.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${history.size} products scanned",
                    color = colors.textSecondary,
                    fontSize = 13.sp
                )
            }
        }

        // ── Search Bar ───────────────────────────
        item {
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(colors.card)
                        .border(1.dp, colors.border, RoundedCornerShape(24.dp))
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        textStyle = TextStyle(color = colors.textPrimary, fontSize = 14.sp),
                        cursorBrush = SolidColor(colors.accentGreen),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        decorationBox = { inner ->
                            if (searchQuery.isEmpty()) Text("Search by name or brand…", color = colors.textHint, fontSize = 14.sp)
                            inner()
                        }
                    )
                    if (searchQuery.isNotEmpty()) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Clear",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(16.dp).clickable { searchQuery = "" }
                        )
                    }
                }
            }
        }

        // ── Filter Chips ─────────────────────────
        item {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filters.forEach { filter ->
                    val isActive = activeFilter == filter
                    val chipColor = when {
                        !isActive -> colors.card
                        filter == "SMASH" -> colors.accentGreen
                        filter == "PASS" -> colors.accentRed
                        else -> colors.accentBlue
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isActive) chipColor else colors.card)
                            .border(1.dp, if (isActive) chipColor else colors.border, RoundedCornerShape(20.dp))
                            .clickable { activeFilter = filter }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            filter,
                            color = if (isActive) Color.White else colors.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        // ── Result count ─────────────────────────
        item {
            Spacer(Modifier.height(20.dp))
            if (filteredHistory.isEmpty()) {
                Spacer(Modifier.height(60.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.SearchOff, contentDescription = null, tint = colors.textHint, modifier = Modifier.size(40.dp))
                    Text("No products found", color = colors.textSecondary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text("Try a different search or filter", color = colors.textHint, fontSize = 13.sp)
                }
            } else {
                Text(
                    text = "${filteredHistory.size} result${if (filteredHistory.size != 1) "s" else ""}",
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
        }

        // ── History Items ─────────────────────────
        itemsIndexed(filteredHistory) { index, item ->
            HistoryItemCard(
                item = item,
                index = index,
                onClick = {
                    viewModel.loadFromHistory(item.foodResponse)
                    onProductSelected()
                }
            )
            Spacer(Modifier.height(10.dp))
        }
    }
}

// ─────────────────────────────────────────────────
//  Top Bar
// ─────────────────────────────────────────────────
@Composable
private fun HistoryTopBar(
    authViewModel: AuthViewModel,
    isDark: Boolean,
    onThemeToggle: () -> Unit,
    onProfileClick: () -> Unit,
    onLogout: () -> Unit
) {
    val colors = LocalAppColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(colors.accentGreen, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Eco, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Text("HealthHeat", color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(colors.card)
                    .border(1.dp, colors.border, CircleShape)
                    .clickable { onThemeToggle() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isDark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                    contentDescription = "Toggle theme",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
            UserProfileAvatar(viewModel = authViewModel, onProfileClick = onProfileClick, onLogout = onLogout)
        }
    }
}

// ─────────────────────────────────────────────────
//  History Item Card
// ─────────────────────────────────────────────────
@Composable
private fun HistoryItemCard(
    item: ProductCacheEntity,
    index: Int,
    onClick: () -> Unit
) {
    val colors = LocalAppColors.current
    val product = item.foodResponse
    val score = product.healthScore ?: 0
    val smashThreshold by com.example.healthheatv2.data.RemoteConfigManager.smashThreshold.collectAsState()
    val isSmash = score >= smashThreshold
    val verdictColor = if (isSmash) colors.accentGreen else colors.accentRed
    val nutriScore = product.nutriScore?.uppercase() ?: "?"
    val nutriColor = nutriScoreColor(nutriScore, colors)

    // Stagger animation
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(index * 50L)
        visible = true
    }
    val offsetY by animateDpAsState(
        targetValue = if (visible) 0.dp else 16.dp,
        animationSpec = tween(300)
    )
    val alpha by animateFloatAsState(targetValue = if (visible) 1f else 0f, animationSpec = tween(300))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .offset(y = offsetY)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Left color accent bar
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(60.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(verdictColor)
        )

        // Product avatar
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(verdictColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(if (product.imageUrl?.startsWith("/") == true) {
                        com.example.healthheatv2.network.RetrofitClient.BASE_URL.dropLast(1) + product.imageUrl
                    } else {
                        product.imageUrl
                    })
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                error = {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = product.name?.take(1)?.uppercase() ?: "?",
                            color = verdictColor,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            )
        }

        // Main info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = product.name ?: "Unknown",
                color = colors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = product.brand ?: "Brand N/A",
                color = colors.textSecondary,
                fontSize = 12.sp,
                maxLines = 1
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                // SMASH/PASS chip
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(verdictColor.copy(0.12f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(if (isSmash) "SMASH" else "PASS", color = verdictColor, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                }
                // Nutriscore chip
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(nutriColor.copy(0.12f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("N:$nutriScore", color = nutriColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    text = formatDate(item.scannedAt),
                    color = colors.textHint,
                    fontSize = 10.sp
                )
            }
        }

        // Score circle
        Box(contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Canvas(modifier = Modifier.size(52.dp)) {
                val strokeW = 5f
                drawArc(
                    color = if (colors.isDark) Color.White.copy(0.08f) else Color.Black.copy(0.08f),
                    startAngle = 0f, sweepAngle = 360f, useCenter = false,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(strokeW)
                )
                drawArc(
                    color = verdictColor,
                    startAngle = -90f,
                    sweepAngle = 360f * (score / 100f),
                    useCenter = false,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(strokeW, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                )
            }
            Text(score.toString(), color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

private fun formatDate(timestamp: Long): String =
    SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(timestamp))
