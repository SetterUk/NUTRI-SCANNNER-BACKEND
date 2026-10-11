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
import androidx.compose.material3.FloatingActionButton
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
import com.example.healthheatv2.ui.components.TodayMealsCard
import com.example.healthheatv2.ui.components.TodayProgressCard
import com.example.healthheatv2.ui.components.NextStepCard
import com.example.healthheatv2.ui.components.WeekCard
import com.example.healthheatv2.ui.components.CompactScanCard
import com.example.healthheatv2.ui.components.ReminderPromptCard
import com.example.healthheatv2.ui.components.WeightCard
import com.example.healthheatv2.notifications.EveningReminder
import com.example.healthheatv2.services.MealCandidate
import com.example.healthheatv2.services.NutritionEngine
import com.example.healthheatv2.services.NutritionGap
import com.example.healthheatv2.services.RecommendationEngine
import com.example.healthheatv2.services.WeeklySummary
import kotlinx.coroutines.launch
import com.example.healthheatv2.ui.components.ScoreTier
import com.example.healthheatv2.ui.components.color
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
    nutritionEngine: NutritionEngine,
    localProfile: com.example.healthheatv2.data.UserProfile,
    onScanClick: () -> Unit,
    onManualEntryClick: () -> Unit,
    onViewAllHistoryClick: () -> Unit,
    onProductSelected: () -> Unit,
    onProfileClick: () -> Unit,
    onLogout: () -> Unit,
    onFixGap: (NutritionGap) -> Unit
) {
    val colors = LocalAppColors.current
    val history by viewModel.searchHistory
    val isDark by themeViewModel.isDark
    val userProfile by authViewModel.userProfile.collectAsState()
    val latestItem = history.firstOrNull()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showReminderPrompt by remember {
        mutableStateOf(!EveningReminder.promptAnswered(context) && !EveningReminder.isEnabled(context))
    }

    val calorieTarget = localProfile.dailyCalories ?: 2000f
    val proteinTarget = localProfile.dailyProtein ?: 60f
    val fibreTarget = localProfile.dailyFiber ?: 30f
    val profileIncomplete = localProfile.missingFields.any { it in setOf("age", "height", "weight") }

    // Today's meals drive everything on this screen and update live as food is logged or deleted.
    val todayMeals by remember(nutritionEngine) { nutritionEngine.observeTodayMeals() }
        .collectAsState(initial = emptyList())
    val eatenCalories = todayMeals.sumOf { it.calories.toDouble() }.toFloat()
    val eatenProtein = todayMeals.sumOf { it.protein.toDouble() }.toFloat()
    val eatenFibre = todayMeals.sumOf { it.fiber.toDouble() }.toFloat()
    val weights by remember(nutritionEngine) { nutritionEngine.db.weightLogDao().observeRecent(30) }
        .collectAsState(initial = emptyList())

    var nextGap by remember { mutableStateOf<NutritionGap?>(null) }
    var ideas by remember { mutableStateOf<List<MealCandidate>>(emptyList()) }
    var week by remember { mutableStateOf<WeeklySummary?>(null) }
    val recommendationEngine = remember(localProfile) {
        RecommendationEngine(nutritionEngine.db.nutritionDao(), localProfile)
    }

    LaunchedEffect(todayMeals, localProfile) {
        // Only nutrients our food data measures reliably; vitamin D and B12 are rarely listed,
        // so they would always look "missing".
        val gap = nutritionEngine.calculateGaps().gaps
            .filter { it.nutrient in setOf("Protein", "Fiber", "Iron", "Calcium") && it.percentageOfTarget < 0.9f }
            .minByOrNull { it.percentageOfTarget }
        nextGap = gap
        ideas = if (gap != null && todayMeals.isNotEmpty()) {
            val remaining = (calorieTarget - eatenCalories).coerceAtLeast(250f)
            recommendationEngine.generateRecommendations(gap, remaining).take(2)
        } else emptyList()
        val since = WeeklySummary.startOfDay(System.currentTimeMillis()) - 6 * 86_400_000L
        week = WeeklySummary.compute(nutritionEngine.getMealsSince(since), calorieTarget, proteinTarget, fibreTarget)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            item {
                HomeTopBar(
                    authViewModel = authViewModel,
                    isDark = isDark,
                    onThemeToggle = { themeViewModel.toggleTheme() },
                    onProfileClick = onProfileClick,
                    onLogout = onLogout
                )
            }

            item {
                Spacer(Modifier.height(4.dp))
                GreetingSection(userName = userProfile?.preferredName)
            }

            item {
                Spacer(Modifier.height(16.dp))
                TodayProgressCard(
                    calories = eatenCalories,
                    calorieTarget = calorieTarget,
                    protein = eatenProtein,
                    proteinTarget = proteinTarget,
                    fibre = eatenFibre,
                    fibreTarget = fibreTarget,
                    itemsLogged = todayMeals.size,
                    profileIncomplete = profileIncomplete,
                    onCompleteProfile = onProfileClick,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }

            item {
                Spacer(Modifier.height(16.dp))
                Box(Modifier.padding(horizontal = 20.dp)) {
                    TodayMealsCard(
                        meals = todayMeals,
                        onDelete = { meal -> scope.launch { nutritionEngine.deleteMeal(meal.id) } },
                        onAdd = { text -> describeLogResult(nutritionEngine.logFoodsFromText(text)) }
                    )
                }
            }

            if (showReminderPrompt && todayMeals.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(16.dp))
                    ReminderPromptCard(
                        onDone = { showReminderPrompt = false },
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
            }

            val gap = nextGap
            if (gap != null && todayMeals.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(16.dp))
                    NextStepCard(
                        gap = gap,
                        ideas = ideas,
                        onMoreIdeas = { onFixGap(gap) },
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
            }

            item {
                Spacer(Modifier.height(20.dp))
                CompactScanCard(
                    onScan = onScanClick,
                    onManual = onManualEntryClick,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }

            val weekSummary = week
            if (weekSummary != null && weekSummary.daysLogged > 0) {
                item {
                    Spacer(Modifier.height(12.dp))
                    WeekCard(summary = weekSummary, calorieTarget = calorieTarget, modifier = Modifier.padding(horizontal = 20.dp))
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
                WeightCard(
                    entries = weights,
                    goal = localProfile.primaryGoal,
                    onLog = { kg ->
                        scope.launch {
                            nutritionEngine.db.weightLogDao().insert(
                                com.example.healthheatv2.data.WeightLog(measuredAt = System.currentTimeMillis(), weightKg = kg)
                            )
                        }
                        authViewModel.updateWeight(kg)
                    },
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }

            if (latestItem != null) {
                item {
                    Spacer(Modifier.height(24.dp))
                    SectionRow(
                        title = "Last scanned",
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

            item {
                Spacer(Modifier.height(20.dp))
                Text(
                    "General nutrition information, not medical advice. Talk to a doctor or dietitian about medical conditions.",
                    color = colors.textHint,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp)
                )
            }
        }
    }
}

/** One line for the log box: what was logged and what couldn't be matched. */
fun describeLogResult(outcomes: List<NutritionEngine.LogOutcome>): String {
    val logged = outcomes.filter { it !is NutritionEngine.LogOutcome.NotFound }
    val missing = outcomes.filterIsInstance<NutritionEngine.LogOutcome.NotFound>()
    return buildString {
        if (logged.isNotEmpty()) append("Logged ${logged.joinToString(", ") { it.label }}.")
        if (missing.isNotEmpty()) {
            if (isNotEmpty()) append(" ")
            append("Couldn't find ${missing.joinToString(", ") { it.label }}. Ask NutriBot to estimate it.")
        }
        if (isEmpty()) append("Nothing recognised. Try something like \"2 roti, 1 bowl dal\".")
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
        Spacer(Modifier.height(2.dp))
        Text(
            text = "Here's your day",
            color = colors.textPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.5).sp
        )
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
    val tier = ScoreTier.of(product.verdict, product.healthScore)
    val verdictColor = tier?.color() ?: colors.textSecondary
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
                            (tier?.label ?: "Not rated").uppercase(),
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
