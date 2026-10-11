package com.example.healthheatv2.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.healthheatv2.services.MealCandidate
import com.example.healthheatv2.services.NutritionGap
import com.example.healthheatv2.services.WeeklySummary
import com.example.healthheatv2.ui.theme.LocalAppColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Calories, protein and fibre eaten today against the user's targets. */
@Composable
fun TodayProgressCard(
    calories: Float,
    calorieTarget: Float,
    protein: Float,
    proteinTarget: Float,
    fibre: Float,
    fibreTarget: Float,
    itemsLogged: Int,
    profileIncomplete: Boolean,
    onCompleteProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAppColors.current
    val left = calorieTarget - calories
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.card)
            .border(1.dp, colors.border, RoundedCornerShape(20.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Today", color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date()), color = colors.textSecondary, fontSize = 12.sp)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${calories.roundToInt()}", color = colors.textPrimary, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                " / ${calorieTarget.roundToInt()} kcal",
                color = colors.textSecondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 6.dp).weight(1f)
            )
            Text(
                if (left >= 0) "${left.roundToInt()} left" else "${(-left).roundToInt()} over",
                color = if (left >= 0) colors.accentGreen else colors.accentAmber,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        ProgressRow("Calories", calories, calorieTarget, "kcal", colors.accentBlue, overIsBad = true)
        ProgressRow("Protein", protein, proteinTarget, "g", colors.accentGreen)
        ProgressRow("Fibre", fibre, fibreTarget, "g", colors.accentAmber)

        if (itemsLogged == 0) {
            Text(
                "Nothing logged yet today. Type what you ate below, for example \"2 roti, 1 bowl dal\".",
                color = colors.textSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
        if (profileIncomplete) {
            Row(
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { onCompleteProfile() }.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Targets use default values. Add your age, height and weight for your own",
                    color = colors.accentBlue,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = colors.accentBlue, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun ProgressRow(label: String, value: Float, target: Float, unit: String, color: Color, overIsBad: Boolean = false) {
    val colors = LocalAppColors.current
    val fraction = if (target > 0) value / target else 0f
    val barColor = if (overIsBad && fraction > 1.15f) colors.accentAmber else color
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row {
            Text(label, color = colors.textSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text("${value.roundToInt()} / ${target.roundToInt()} $unit", color = colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Box(
            Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp))
                .background(if (colors.isDark) Color.White.copy(0.08f) else Color.Black.copy(0.07f))
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).clip(RoundedCornerShape(4.dp)).background(barColor))
        }
    }
}

private val NUTRIENT_WORDS = mapOf("Fiber" to "fibre", "Protein" to "protein", "Iron" to "iron", "Calcium" to "calcium")

/** The one thing to do next: the nutrient furthest behind, with foods that fit the remaining calories. */
@Composable
fun NextStepCard(
    gap: NutritionGap,
    ideas: List<MealCandidate>,
    onMoreIdeas: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAppColors.current
    val word = NUTRIENT_WORDS[gap.nutrient] ?: gap.nutrient.lowercase()
    val pct = (gap.percentageOfTarget * 100).roundToInt().coerceIn(0, 100)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.accentGreen.copy(alpha = 0.08f))
            .border(1.dp, colors.accentGreen.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = colors.accentGreen, modifier = Modifier.size(18.dp))
            Text("Next step", color = colors.accentGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            "You're at $pct% of today's $word (${gap.current.roundToInt()} of ${gap.target.roundToInt()} ${gap.unit}).",
            color = colors.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 21.sp
        )
        if (ideas.isNotEmpty()) {
            Text("Easy ways to close the gap:", color = colors.textSecondary, fontSize = 12.sp)
            ideas.forEach { idea ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(colors.accentGreen))
                    Text(
                        "${idea.name}, ${idea.amountToConsume} (+${idea.gapReduced.roundToInt()} ${gap.unit})",
                        color = colors.textPrimary,
                        fontSize = 13.sp
                    )
                }
            }
        }
        TextButton(onClick = onMoreIdeas, contentPadding = PaddingValues(0.dp)) {
            Text("More ideas and why", color = colors.accentGreen, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Last 7 days: calories per day against the target, and how many days hit protein and fibre. */
@Composable
fun WeekCard(summary: WeeklySummary, calorieTarget: Float, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    val maxValue = maxOf(calorieTarget * 1.3f, summary.days.maxOfOrNull { it.calories } ?: 0f, 1f)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.card)
            .border(1.dp, colors.border, RoundedCornerShape(20.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("This week", color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth().height(90.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            summary.days.forEach { day ->
                val inRange = day.logged && day.calories in (calorieTarget * 0.85f)..(calorieTarget * 1.15f)
                val barColor = when {
                    !day.logged -> colors.border
                    inRange -> colors.accentGreen
                    else -> colors.accentAmber
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.fillMaxWidth()
                            .height((70 * (if (day.logged) day.calories / maxValue else 0.04f)).coerceAtLeast(3f).dp)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(barColor)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        SimpleDateFormat("EEEEE", Locale.getDefault()).format(Date(day.dayStart)),
                        color = colors.textSecondary,
                        fontSize = 10.sp
                    )
                }
            }
        }
        Text(
            "Logged ${summary.daysLogged} of 7 days · calories on target ${summary.caloriesInRangeDays} days",
            color = colors.textSecondary,
            fontSize = 12.sp
        )
        Text(
            "Protein goal met ${summary.proteinDaysHit} days · fibre goal met ${summary.fibreDaysHit} days",
            color = colors.textSecondary,
            fontSize = 12.sp
        )
    }
}

@Composable
fun CompactScanCard(onScan: () -> Unit, onManual: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.accentGreen)
                .clickable { onScan() }
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("Scan a packaged food", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("Get a rating and healthier picks", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color.White)
        }
        TextButton(onClick = onManual, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Type a barcode instead", color = colors.textSecondary, fontSize = 13.sp)
        }
    }
}
