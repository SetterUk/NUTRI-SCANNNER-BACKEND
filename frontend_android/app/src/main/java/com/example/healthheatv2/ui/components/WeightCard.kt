package com.example.healthheatv2.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.healthheatv2.data.WeightLog
import com.example.healthheatv2.ui.theme.LocalAppColors
import java.util.Locale
import kotlin.math.abs

/** Change over the readings from roughly the last four weeks; null with fewer than two readings. */
fun weightChange(entries: List<WeightLog>, now: Long = System.currentTimeMillis()): Pair<Float, Int>? {
    val sorted = entries.sortedBy { it.measuredAt }
    val latest = sorted.lastOrNull() ?: return null
    val windowStart = now - 35L * 86_400_000L
    val first = sorted.firstOrNull { it.measuredAt >= windowStart && it.id != latest.id && it.clientId != latest.clientId }
        ?: return null
    val days = ((latest.measuredAt - first.measuredAt) / 86_400_000L).toInt().coerceAtLeast(1)
    return (latest.weightKg - first.weightKg) to days
}

@Composable
fun WeightCard(
    entries: List<WeightLog>,
    goal: String,
    onLog: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAppColors.current
    var showDialog by remember { mutableStateOf(false) }
    val latest = entries.maxByOrNull { it.measuredAt }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.card)
            .border(1.dp, colors.border, RoundedCornerShape(20.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Weight", color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = { showDialog = true }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(if (latest == null) "Log weight" else "Update", color = colors.accentGreen, fontWeight = FontWeight.SemiBold)
            }
        }
        if (latest == null) {
            Text(
                "Log your weight once a week. It's the simplest way to see whether your changes are working.",
                color = colors.textSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        } else {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(String.format(Locale.US, "%.1f kg", latest.weightKg), color = colors.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                weightChange(entries)?.let { (delta, days) ->
                    val towardGoal = when (goal) {
                        "weight_loss" -> delta < 0
                        "weight_gain", "muscle_gain" -> delta > 0
                        else -> abs(delta) < 1f
                    }
                    val sign = if (delta > 0) "+" else if (delta < 0) "−" else ""
                    Text(
                        "$sign${String.format(Locale.US, "%.1f", abs(delta))} kg in ${if (days >= 7) "${days / 7} wk" else "$days d"}",
                        color = if (towardGoal) colors.accentGreen else colors.accentAmber,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 5.dp)
                    )
                }
            }
            val points = entries.sortedBy { it.measuredAt }.takeLast(12)
            if (points.size >= 2) {
                val lineColor = colors.accentGreen
                Canvas(Modifier.fillMaxWidth().height(48.dp)) {
                    val min = points.minOf { it.weightKg }
                    val max = points.maxOf { it.weightKg }
                    val range = (max - min).coerceAtLeast(0.5f)
                    val step = size.width / (points.size - 1)
                    val path = Path()
                    points.forEachIndexed { i, p ->
                        val x = i * step
                        val y = size.height - (p.weightKg - min) / range * size.height
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, lineColor, style = Stroke(width = 4f, cap = StrokeCap.Round))
                    val last = points.last()
                    drawCircle(lineColor, 7f, Offset(size.width, size.height - (last.weightKg - min) / range * size.height))
                }
            }
        }
    }

    if (showDialog) {
        var text by remember { mutableStateOf(latest?.let { String.format(Locale.US, "%.1f", it.weightKg) } ?: "") }
        val value = text.replace(',', '.').toFloatOrNull()
        val valid = value != null && value in 25f..300f
        AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor = colors.card,
            title = { Text("Today's weight", color = colors.textPrimary) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(6) },
                    suffix = { Text("kg") },
                    singleLine = true,
                    isError = text.isNotBlank() && !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    supportingText = { Text("Weigh yourself at the same time of day for a fair comparison.", fontSize = 11.sp) }
                )
            },
            confirmButton = {
                TextButton(enabled = valid, onClick = {
                    onLog(value!!)
                    showDialog = false
                }) { Text("Save", color = colors.accentGreen, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Cancel", color = colors.textSecondary) } }
        )
    }
}
