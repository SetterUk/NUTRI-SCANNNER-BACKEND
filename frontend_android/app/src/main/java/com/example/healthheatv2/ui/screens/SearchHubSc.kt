package com.example.healthheatv2.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.healthheatv2.R
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.example.healthheatv2.data.ProductCacheEntity
import com.example.healthheatv2.network.FoodResponse
import com.example.healthheatv2.ui.components.UserProfileAvatar
import com.example.healthheatv2.ui.theme.LocalAppColors
import com.example.healthheatv2.ui.viewmodel.AuthViewModel
import com.example.healthheatv2.ui.viewmodel.ScannerViewModel
import com.example.healthheatv2.ui.viewmodel.ThemeViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun SearchHubScreen(
    viewModel: ScannerViewModel,
    authViewModel: AuthViewModel,
    themeViewModel: ThemeViewModel,
    onScanClick: () -> Unit,
    onManualEntryClick: () -> Unit,
    onViewAllHistoryClick: () -> Unit,
    onProductSelected: () -> Unit,
    onProfileClick: () -> Unit,
    onLogout: () -> Unit,
) {
    val colors = LocalAppColors.current
    val history by viewModel.searchHistory
    val isDark by themeViewModel.isDark

    val avgScore = if (history.isNotEmpty()) {
        history.asSequence()
            .take(10)
            .mapNotNull { it.foodResponse.healthScore }
            .average()
            .toInt()
    } else 0
    val smashThreshold by com.example.healthheatv2.data.RemoteConfigManager.smashThreshold.collectAsState()
    val smashCount = history.count { (it.foodResponse.healthScore ?: 0) >= smashThreshold }
    val passCount = history.count { (it.foodResponse.healthScore ?: 0) < smashThreshold }
    val userProfile by authViewModel.userProfile.collectAsState()
    val latestItem = history.firstOrNull()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // Removed AnimatedAmbientBackground to look more native/less AI-generated

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
        // ── Top Bar ──────────────────────────────
        item {
            HomeTopBar(
                authViewModel = authViewModel,
                isDark = isDark,
                onThemeToggle = { themeViewModel.toggleTheme() },
                onProfileClick = onProfileClick,
                onLogout = onLogout
            )
        }

        // ── Greeting + headline ───────────────────
        item {
            Spacer(Modifier.height(12.dp))
            GreetingSection(userName = userProfile?.preferredName)
        }

        // ── Featured CTA Card ─────────────────────
        item {
            Spacer(Modifier.height(20.dp))
            FeaturedScanCard(onScanClick = onScanClick, onManualClick = onManualEntryClick)
        }

        // ── Stats Panel ───────────────────────────
        if (history.size >= 3) {
            item {
                Spacer(Modifier.height(24.dp))
                StatsPanel(
                    totalScans = history.size,
                    avgScore = avgScore,
                    smashCount = smashCount,
                    passCount = passCount
                )
            }
        }

        // ── Latest scan card ─────────────────────
        if (latestItem != null) {
            item {
                Spacer(Modifier.height(28.dp))
                SectionRow(
                    title = "Last Scanned",
                    action = if (history.size > 1) "View all →" else null,
                    onActionClick = onViewAllHistoryClick
                )
                Spacer(Modifier.height(12.dp))
                FeaturedProductCard(
                    item = latestItem,
                    onClick = {
                        viewModel.loadFromHistory(latestItem.foodResponse)
                        onProductSelected()
                    }
                )
            }
        }


        // ── Health tips strip ─────────────────────
        item {
            Spacer(Modifier.height(28.dp))
            SectionRow(title = "Health Tips", action = null, onActionClick = {})
            Spacer(Modifier.height(12.dp))
            HealthTipsStrip()
        }

        // ── Empty state ───────────────────────────
        if (history.isEmpty()) {
            item {
                Spacer(Modifier.height(28.dp))
                EmptyHistoryState()
            }
        }
    }
    }
}

// ─────────────────────────────────────────────────
//  Top Bar
// ─────────────────────────────────────────────────
@Composable
private fun HomeTopBar(
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = "App Logo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Text("HealthHeat", color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .size(36.dp).clip(CircleShape)
                    .background(colors.card)
                    .border(1.dp, colors.border, CircleShape)
                    .clickable { onThemeToggle() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isDark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                    contentDescription = "Toggle",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
            UserProfileAvatar(viewModel = authViewModel, onProfileClick = onProfileClick, onLogout = onLogout)
        }
    }
}

// ─────────────────────────────────────────────────
//  Greeting
// ─────────────────────────────────────────────────
@Composable
private fun GreetingSection(userName: String? = null) {
    val colors = LocalAppColors.current
    val hour = Calendar.getInstance()[Calendar.HOUR_OF_DAY]
    val greeting = when {
        hour < 12 -> "Good Morning"
        hour < 17 -> "Good Afternoon"
        else -> "Good Evening"
    }
    val emoji = when {
        hour < 12 -> "☀️"
        hour < 17 -> "🌤️"
        else -> "🌙"
    }
    val displayName = userName?.trim()?.takeIf { it.isNotBlank() }

    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Text(
            text = if (displayName != null) "$emoji $greeting, $displayName!"
                   else "$emoji $greeting",
            color = colors.textSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = buildAnnotatedString {
                append("Know what\nyou're ")
                withStyle(SpanStyle(color = colors.accentGreen, fontWeight = FontWeight.ExtraBold)) {
                    append("eating.")
                }
            },
            color = colors.textPrimary,
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            lineHeight = 36.sp,
            letterSpacing = (-1).sp
        )
    }
}

// ─────────────────────────────────────────────────
//  Featured Scan CTA Card
// ─────────────────────────────────────────────────
@Composable
private fun FeaturedScanCard(onScanClick: () -> Unit, onManualClick: () -> Unit) {
    val colors = LocalAppColors.current
    
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Main Scan Button
        var pressed by remember { mutableStateOf(false) }
        val btnScale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(stiffness = Spring.StiffnessMediumLow))
        LaunchedEffect(pressed) { if (pressed) { delay(150L); pressed = false } }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .scale(btnScale)
                .clip(RoundedCornerShape(32.dp))
                .background(colors.accentGreen)
                .clickable { pressed = true; onScanClick() },
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Icon Wrapper
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color.White.copy(0.25f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.QrCodeScanner, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                }
                Column {
                    Text("Scan Product", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
                    Text("Instant AI Nutrition Analysis", color = Color.White.copy(0.85f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        // Secondary action
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .clickable { onManualClick() }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Filled.Keyboard, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
            Text("Enter barcode manually", color = colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ─────────────────────────────────────────────────
//  Stats Panel (Unified 2x2 Grid)
// ─────────────────────────────────────────────────
@Composable
private fun StatsPanel(totalScans: Int, avgScore: Int, smashCount: Int, passCount: Int) {
    val colors = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(colors.card)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            StatItem(modifier = Modifier.weight(1f), title = "Total Scans", value = totalScans.toString(), icon = Icons.Filled.QrCode, color = colors.accentBlue)
            Box(modifier = Modifier.width(1.dp).height(30.dp).background(colors.border))
            StatItem(modifier = Modifier.weight(1f), title = "Avg Score", value = avgScore.toString(), icon = Icons.Filled.Analytics, color = colors.accentAmber)
        }
        androidx.compose.material3.HorizontalDivider(color = colors.border)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            StatItem(modifier = Modifier.weight(1f), title = "Healthy", value = smashCount.toString(), icon = Icons.Filled.ThumbUp, color = colors.accentGreen)
            Box(modifier = Modifier.width(1.dp).height(30.dp).background(colors.border))
            StatItem(modifier = Modifier.weight(1f), title = "Avoided", value = passCount.toString(), icon = Icons.Filled.ThumbDown, color = colors.accentRed)
        }
    }
}

@Composable
private fun StatItem(modifier: Modifier, title: String, value: String, icon: ImageVector, color: Color) {
    val colors = LocalAppColors.current
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(color.copy(0.12f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        }
        Column {
            Text(value, color = colors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
            Text(title, color = colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

// ─────────────────────────────────────────────────
//  Section Row (title + action)
// ─────────────────────────────────────────────────
@Composable
private fun SectionRow(title: String, action: String?, onActionClick: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        if (action != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.accentGreenSubtle)
                    .clickable { onActionClick() }
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Text(action, color = colors.accentGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ─────────────────────────────────────────────────
//  Featured Product Card (latest scan, full-width)
// ─────────────────────────────────────────────────
@Composable
private fun FeaturedProductCard(item: ProductCacheEntity, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val product = item.foodResponse
    val score = product.healthScore ?: 0
    val smashThreshold by com.example.healthheatv2.data.RemoteConfigManager.smashThreshold.collectAsState()
    val isSmash = score >= smashThreshold
    val verdictColor = if (isSmash) colors.accentGreen else colors.accentRed
    val nutriScore = product.nutriScore?.uppercase() ?: "?"
    val nutriColor = nutriScoreColor(nutriScore, colors)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(colors.card)
            .border(1.dp, verdictColor.copy(0.25f), RoundedCornerShape(24.dp))
            .clickable { onClick() }
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Product image
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .height(130.dp)
                    .background(verdictColor.copy(0.1f)),
                contentAlignment = Alignment.Center
            ) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(product.imageUrl)
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
                                fontSize = 36.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                )
            }

            // Details
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    product.brand?.uppercase() ?: "",
                    color = colors.textSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    product.name ?: "Unknown",
                    color = colors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp
                )

                // Verdict + Nutriscore row
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(verdictColor.copy(0.12f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            if (isSmash) "SMASH ✓" else "PASS ✗",
                            color = verdictColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(nutriColor.copy(0.12f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text("N-$nutriScore", color = nutriColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Score progress bar
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Score", color = colors.textSecondary, fontSize = 11.sp)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (colors.isDark) Color.White.copy(0.08f) else Color.Black.copy(0.06f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(score / 100f)
                                .clip(RoundedCornerShape(3.dp))
                                .background(verdictColor)
                        )
                    }
                    Text("$score", color = verdictColor, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                }

                Text(
                    formatDate(item.scannedAt),
                    color = colors.textHint,
                    fontSize = 11.sp
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────
//  Health Tips Strip
// ─────────────────────────────────────────────────
private val healthTips = listOf(
    Triple("🥦", "Eat the Rainbow", "Varied coloured vegetables ensure diverse nutrient intake."),
    Triple("💧", "Stay Hydrated", "Aim for 8 glasses of water daily for optimal metabolism."),
    Triple("🔍", "Read Labels", "NOVA Group 1-2 products are minimally processed and healthiest."),
    Triple("🚫", "Limit Additives", "Avoid products with many E-numbers or artificial flavours."),
    Triple("⚡", "Energy Balance", "Match calorie intake to your activity level for healthy weight.")
)

@Composable
private fun HealthTipsStrip() {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(healthTips) { _, (emoji, title, desc) ->
            HealthTipCard(emoji = emoji, title = title, desc = desc)
        }
    }
}

@Composable
private fun HealthTipCard(emoji: String, title: String, desc: String) {
    val colors = LocalAppColors.current
    Column(
        modifier = Modifier
            .width(170.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.card)
            .border(1.dp, colors.border, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(emoji, fontSize = 26.sp)
        Text(title, color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(desc, color = colors.textSecondary, fontSize = 11.sp, lineHeight = 16.sp)
    }
}

// ─────────────────────────────────────────────────
//  Empty State
// ─────────────────────────────────────────────────
@Composable
private fun EmptyHistoryState() {
    val colors = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(colors.card)
            .border(1.dp, colors.border, RoundedCornerShape(24.dp))
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("🛒", fontSize = 48.sp)
        Text("No scans yet", color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(
            "Scan your first product to start tracking your nutrition intake.",
            color = colors.textSecondary,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )
    }
}

// ─────────────────────────────────────────────────
//  Helpers
// ─────────────────────────────────────────────────
fun nutriScoreColor(score: String, colors: com.example.healthheatv2.ui.theme.AppColors): Color {
    return when (score.uppercase()) {
        "A" -> colors.accentGreen
        "B" -> Color(0xFF52BE80)
        "C" -> colors.accentAmber
        "D" -> Color(0xFFCA6F1E)
        "E" -> colors.accentRed
        "N/A" -> colors.textSecondary
        "?" -> colors.textSecondary
        else -> colors.textSecondary
    }
}

private fun formatDate(timestamp: Long): String =
    SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(timestamp))
