package com.example.healthheatv2.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.healthheatv2.data.UserProfile
import com.example.healthheatv2.services.GapAnalysis
import com.example.healthheatv2.services.NutritionEngine
import com.example.healthheatv2.services.NutritionGap
import com.example.healthheatv2.services.NutritionIntake
import com.example.healthheatv2.data.LoggedMeal
import com.example.healthheatv2.ui.theme.LocalAppColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun DashboardSc(
    userProfile: UserProfile,
    nutritionEngine: NutritionEngine,
    nanoCoach: com.example.healthheatv2.ai.NanoNutritionistCoach,
    onFixMyNutritionClick: (NutritionGap) -> Unit,
    refreshTrigger: Int = 0
) {
    var gaps by remember { mutableStateOf<GapAnalysis?>(null) }
    var intake by remember { mutableStateOf<NutritionIntake?>(null) }
    var historicalMeals by remember { mutableStateOf<List<LoggedMeal>>(emptyList()) }
    var aiPlanText by remember { mutableStateOf<String?>(null) }
    var aiPlanLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val colors = LocalAppColors.current

    val todayKey = remember {
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .format(java.util.Date())
    }

    LaunchedEffect(refreshTrigger) {
        gaps = nutritionEngine.calculateGaps()
        intake = nutritionEngine.getTodayIntake()
        historicalMeals = nutritionEngine.getHistoricalMeals()
        // Load persisted plan for today
        val saved = nutritionEngine.getDailyPlan(todayKey)
        if (saved != null) aiPlanText = saved.planText
    }

    val streakDays = remember(historicalMeals) {
        if (historicalMeals.isEmpty()) return@remember 0
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        var currentDayStart = cal.timeInMillis

        val uniqueDays = historicalMeals.map { meal ->
            val mealCal = Calendar.getInstance()
            mealCal.timeInMillis = meal.date
            mealCal.set(Calendar.HOUR_OF_DAY, 0)
            mealCal.set(Calendar.MINUTE, 0)
            mealCal.set(Calendar.SECOND, 0)
            mealCal.set(Calendar.MILLISECOND, 0)
            mealCal.timeInMillis
        }.distinct().sortedDescending()

        var streak = 0
        for (day in uniqueDays) {
            if (day == currentDayStart) {
                streak++
                currentDayStart -= 86400000L
            } else if (day == currentDayStart - 86400000L && streak == 0) {
                streak++
                currentDayStart = day - 86400000L
            } else {
                break
            }
        }
        streak
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        if (userProfile.bmi != null && userProfile.bmi > 0f) {
            Text(
                text = "Health Profile",
                color = colors.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            PremiumHealthProfileCard(userProfile)
            Spacer(modifier = Modifier.height(24.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PremiumMetricCard(
                title = "Streak",
                value = "$streakDays",
                subtitle = "Days",
                icon = Icons.Filled.LocalFireDepartment,
                iconTint = colors.accentAmber,
                modifier = Modifier.weight(1f)
            )

            PremiumMetricCard(
                title = "Today",
                value = "${intake?.calories?.toInt() ?: 0}",
                subtitle = "kcal",
                icon = Icons.Filled.AutoAwesome,
                iconTint = colors.accentBlue,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (historicalMeals.isNotEmpty()) {
            Text(
                text = "Weekly Calories",
                color = colors.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            PremiumWeeklyChart(historicalMeals)
            Spacer(modifier = Modifier.height(32.dp))
        }

        gaps?.let { gapData ->
            Text(
                text = "What Am I Missing?",
                color = colors.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            gapData.gaps.filter { it.percentageOfTarget < 0.9f }.sortedByDescending { it.gap }.take(4).forEach { gap ->
                PremiumGapRow(gap)
            }
            
            Spacer(modifier = Modifier.height(24.dp))

            // ── AI Daily Meal Plan Card ────────────────────────────────────
            AiMealPlanCard(
                planText = aiPlanText,
                isLoading = aiPlanLoading,
                onGenerateClick = {
                    scope.launch {
                        aiPlanLoading = true
                        try {
                            val plan = nanoCoach.generateMealPlanSuggestion(userProfile, nutritionEngine)
                            aiPlanText = plan
                            // Detect which AI tier responded
                            val tier = when {
                                plan.startsWith("⚡") -> "gemma4_e2b"
                                plan.startsWith("☁️") -> "cloud"
                                else -> "offline"
                            }
                            nutritionEngine.saveDailyPlan(todayKey, plan, tier)
                        } catch (e: Exception) {
                            aiPlanText = "Could not generate plan. Please try again."
                        } finally {
                            aiPlanLoading = false
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            gapData.actionableGap?.let { actionable ->
                HeroActionCard(actionable, onFixMyNutritionClick)
            }
        }
    }
}

@Composable
fun AiMealPlanCard(
    planText: String?,
    isLoading: Boolean,
    onGenerateClick: () -> Unit
) {
    val colors = LocalAppColors.current
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = colors.card,
        border = BorderStroke(1.dp, colors.accentGreen.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Today's AI Meal Plan",
                        color = colors.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Personalized for your goals",
                        color = colors.textHint,
                        fontSize = 12.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(colors.accentGreen.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("⚡", fontSize = 18.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            color = colors.accentGreen,
                            modifier = androidx.compose.ui.Modifier.size(28.dp),
                            strokeWidth = 2.5.dp
                        )
                    }
                }
                planText != null -> {
                    // Strip the tier prefix [⚡ [Gemma 4 E2B], ☁️ [Cloud AI], 🔋 [Offline]]
                    val cleanPlan = planText
                        .replace(Regex("^(⚡ \\[Gemma 4 E2B\\]|☁️ \\[Cloud AI\\]|🔋 \\[Offline\\])\\s*\n"), "")
                        .trim()
                    Text(
                        text = cleanPlan,
                        color = colors.textPrimary,
                        fontSize = 14.sp,
                        lineHeight = 22.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(
                        onClick = onGenerateClick,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Regenerate", color = colors.accentGreen, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                else -> {
                    Button(
                        onClick = onGenerateClick,
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = androidx.compose.foundation.shape.CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accentGreen)
                    ) {
                        Text("Get My Meal Plan", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun PremiumMetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    modifier: Modifier = Modifier
) {
    val colors = LocalAppColors.current
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = colors.card,
        border = BorderStroke(1.dp, colors.border),
        modifier = modifier.aspectRatio(1f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, color = colors.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(iconTint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
                }
            }
            
            Column {
                Text(
                    text = value,
                    color = colors.textPrimary,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    color = colors.textSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun PremiumGapRow(gap: NutritionGap) {
    val colors = LocalAppColors.current
    var isAnimated by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        delay(100)
        isAnimated = true
    }

    val progress = gap.percentageOfTarget.coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = if (isAnimated) progress else 0f,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing)
    )

    val progressColor = when {
        progress > 0.8f -> colors.accentGreen
        progress > 0.5f -> colors.accentAmber
        else -> colors.accentRed
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = gap.nutrient,
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${(progress * 100).toInt()}%",
                color = colors.textSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .background(colors.card)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(Brush.horizontalGradient(listOf(progressColor.copy(alpha=0.7f), progressColor)))
            )
        }
    }
}

@Composable
fun PremiumWeeklyChart(historicalMeals: List<LoggedMeal>) {
    val colors = LocalAppColors.current
    val caloriesPerDay = remember(historicalMeals) {
        val map = mutableMapOf<Long, Float>()
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        
        for (i in 0..6) {
            val dayStart = cal.timeInMillis
            val dayEnd = dayStart + 86400000L - 1
            val dayMeals = historicalMeals.filter { it.date in dayStart..dayEnd }
            map[dayStart] = dayMeals.sumOf { it.calories.toDouble() }.toFloat()
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        map.entries.sortedBy { it.key } 
    }
    
    val maxCalories = caloriesPerDay.maxOfOrNull { it.value }?.takeIf { it > 0 } ?: 2000f
    var isAnimated by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(150)
        isAnimated = true
    }
    
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = colors.card,
        border = BorderStroke(1.dp, colors.border),
        modifier = Modifier.fillMaxWidth().height(220.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            val format = SimpleDateFormat("EEE", Locale.getDefault())
            for ((timestamp, calories) in caloriesPerDay) {
                val heightPercent = (calories / maxCalories).coerceIn(0f, 1f)
                val animatedHeight by animateFloatAsState(
                    targetValue = if (isAnimated) heightPercent else 0f,
                    animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing)
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.55f)
                            .weight(1f, fill = false)
                            .fillMaxHeight(animatedHeight)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(colors.accentBlue, colors.accentBlue.copy(alpha = 0.3f))
                                )
                            )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = format.format(Date(timestamp)).take(1),
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun HeroActionCard(gap: NutritionGap, onFixClick: (NutritionGap) -> Unit) {
    val colors = LocalAppColors.current

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .background(Brush.linearGradient(
                    colors = listOf(colors.accentAmber.copy(alpha = 0.15f), colors.accentRed.copy(alpha = 0.05f)),
                    start = Offset(0f, 0f),
                    end = Offset(1000f, 1000f)
                ))
                .border(1.dp, colors.accentAmber.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = colors.accentAmber, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Priority Gap",
                        color = colors.accentAmber,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "You need ${gap.gap.toInt()}${gap.unit} of ${gap.nutrient}",
                    color = colors.textPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 28.sp
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = { onFixClick(gap) },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentAmber)
                ) {
                    Text("FIX MY NUTRITION", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                }
            }
        }
    }
}

@Composable
fun PremiumHealthProfileCard(profile: UserProfile) {
    val colors = LocalAppColors.current
    val bmi = profile.bmi ?: 0f
    val bmr = profile.bmr ?: 0f
    
    val bmiCategory = when {
        bmi < 18.5f -> "Underweight"
        bmi < 25f -> "Normal"
        bmi < 30f -> "Overweight"
        else -> "Obese"
    }

    val bmiColor = when {
        bmi < 18.5f -> colors.accentBlue
        bmi < 25f -> colors.accentGreen
        bmi < 30f -> colors.accentAmber
        else -> Color(0xFFE53935)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.card)
            .padding(20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("BMI", color = colors.textSecondary, fontSize = 14.sp)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    String.format(java.util.Locale.US, "%.1f", bmi),
                    color = colors.textPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    bmiCategory,
                    color = bmiColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }
        
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(40.dp)
                .background(colors.border)
        )
        
        Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
            Text("BMR (Metabolism)", color = colors.textSecondary, fontSize = 14.sp)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "${bmr.toInt()}",
                    color = colors.textPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    "kcal/day",
                    color = colors.textSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }
    }
}
