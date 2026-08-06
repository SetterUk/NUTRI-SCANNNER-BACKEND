package com.example.healthheatv2.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
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
import coil.compose.AsyncImage
import com.example.healthheatv2.network.AlternativeProduct
import com.example.healthheatv2.network.FoodResponse
import com.example.healthheatv2.network.IngredientAnalysis
import com.example.healthheatv2.ui.components.UserProfileAvatar
import com.example.healthheatv2.ui.theme.AppColors
import com.example.healthheatv2.ui.theme.LocalAppColors
import com.example.healthheatv2.ui.viewmodel.ApiState
import com.example.healthheatv2.ui.viewmodel.AuthViewModel
import com.example.healthheatv2.ui.viewmodel.ScannerViewModel

@Composable
fun ProductScreen(
    viewModel: ScannerViewModel,
    authViewModel: AuthViewModel,
    onScanAnother: () -> Unit,
    onViewDetails: () -> Unit,
    onLogout: () -> Unit
) {
    val apiState by viewModel.apiState
    val colors = LocalAppColors.current

    if (apiState is ApiState.Success) {
        val product = (apiState as ApiState.Success).data
        val isSmash = product.verdict?.uppercase() == "SMASH"
        val verdictColor = if (isSmash) colors.accentGreen else colors.accentRed

        val scrollState = androidx.compose.foundation.lazy.rememberLazyListState()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
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
                    ProductHero(product = product, verdictColor = verdictColor, onScanAnother = onScanAnother, scrollState = scrollState)
                }

                // ── Verdict + Score row ───────────
                item {
                    Spacer(Modifier.height(20.dp))
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
                        AISummaryCard(
                            summary = summary,
                            safeFrequency = product.safeConsumptionFrequency,
                            healthReason = product.healthReason
                        )
                    }
                }

                // ── Ingredient Forensics ──────────
                val ingredients = product.ingredientsAnalysis ?: emptyList()
                if (ingredients.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(24.dp))
                        SectionTitle("Ingredient Analysis", padding = 20.dp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${ingredients.count { it.status?.uppercase() == "GOOD" }} good · " +
                            "${ingredients.count { it.status?.uppercase() == "BAD" }} concerning · " +
                            "${ingredients.count { it.status?.uppercase() == "NEUTRAL" }} neutral",
                            color = colors.textSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    itemsIndexed(ingredients) { index, ingredient ->
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
    } else {
        Box(Modifier.fillMaxSize().background(colors.background), contentAlignment = Alignment.Center) {
            Text("No product data.", color = colors.textSecondary)
        }
    }
}

// ─────────────────────────────────────────────────
//  Hero Section
// ─────────────────────────────────────────────────
@Composable
private fun ProductHero(product: FoodResponse, verdictColor: Color, onScanAnother: () -> Unit, scrollState: LazyListState) {
    val colors = LocalAppColors.current

    // Parallax calculation
    val scrollOffset = if (scrollState.firstVisibleItemIndex == 0) scrollState.firstVisibleItemScrollOffset else 0
    val parallaxOffset = scrollOffset * 0.5f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
    ) {
        // Background: image or coloured gradient
        if (!product.imageUrl.isNullOrEmpty()) {
            AsyncImage(
                model = product.imageUrl,
                contentDescription = "Product",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationY = parallaxOffset }
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationY = parallaxOffset }
                    .background(
                        Brush.verticalGradient(
                            listOf(verdictColor.copy(alpha = 0.3f), colors.background)
                        )
                    )
            )
        }

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
            Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = colors.textPrimary, modifier = Modifier.size(20.dp))
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
            Text(
                text = product.name ?: "Unknown Product",
                color = if (colors.isDark) Color.White else colors.textPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 30.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
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
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
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
            val nutriGrade = nutriScore?.uppercase() ?: "?"
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
                    text = novaGroup?.toString() ?: "?",
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
            Text(grade, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
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
private fun AISummaryCard(summary: String, safeFrequency: String?, healthReason: String?) {
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
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(index * 40L)
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
    val nutriColor = nutriScoreColor(alt.nutriScore?.uppercase() ?: "?", colors)

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
                .background(nutriColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            if (!alt.imageUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = alt.imageUrl,
                    contentDescription = alt.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = (alt.name?.firstOrNull() ?: "?").toString().uppercase(),
                    color = nutriColor,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
        Text(alt.name ?: "Product", color = colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, lineHeight = 16.sp)
        Text(alt.brand ?: "", color = colors.textSecondary, fontSize = 11.sp, maxLines = 1)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(nutriColor.copy(alpha = 0.12f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text("${alt.nutriScore?.uppercase() ?: "?"}", color = nutriColor, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
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
    
    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
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
