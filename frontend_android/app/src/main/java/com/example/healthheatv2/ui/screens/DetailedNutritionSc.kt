package com.example.healthheatv2.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.healthheatv2.network.FoodResponse
import com.example.healthheatv2.ui.theme.LocalAppColors
import com.example.healthheatv2.ui.viewmodel.ApiState
import com.example.healthheatv2.ui.viewmodel.ScannerViewModel
import kotlin.math.roundToInt

@Composable
fun DetailedNutritionScreen(
    viewModel: ScannerViewModel,
    onBackClick: () -> Unit
) {
    val apiState by viewModel.apiState
    val colors = LocalAppColors.current

    if (apiState !is ApiState.Success) {
        Box(Modifier.fillMaxSize().background(colors.background), contentAlignment = Alignment.Center) {
            Text("No data available.", color = colors.textSecondary)
        }
        return
    }

    val product = (apiState as ApiState.Success).data

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
        contentPadding = PaddingValues(bottom = 60.dp)
    ) {
        // ── Top Bar ──────────────────────────────
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
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
                Column {
                    Text("Full Report", color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(product.name ?: "", color = colors.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        // ── Safety First: Allergens & Additives ──
        val hasAllergens = !product.allergens.isNullOrBlank()
        val hasAdditives = !product.additivesTags.isNullOrEmpty()
        if (hasAllergens || hasAdditives) {
            item {
                Spacer(Modifier.height(8.dp))
                SafetyWarningSection(allergens = product.allergens, additives = product.additivesTags)
            }
        }

        // ── AI Summary ────────────────────────────
        val summary = product.summary
        if (!summary.isNullOrEmpty()) {
            item {
                Spacer(Modifier.height(20.dp))
                SectionHeader("AI Summary", Icons.Filled.AutoAwesome, colors.accentAmber)
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.accentAmberSubtle)
                        .border(1.dp, colors.accentAmber.copy(0.25f), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Text(summary, color = colors.textPrimary, fontSize = 14.sp, lineHeight = 22.sp)
                }
            }
        }

        // ── Consumption Advice ────────────────────
        item {
            Spacer(Modifier.height(24.dp))
            SectionHeader("Consumption Advice", Icons.Filled.HealthAndSafety, colors.accentBlue)
            Spacer(Modifier.height(10.dp))
            ConsumptionAdviceCard(product = product)
        }

        // ── Macro Bar Chart ───────────────────────
        val nutrients = product.nutrients
        if (!nutrients.isNullOrEmpty()) {
            item {
                Spacer(Modifier.height(24.dp))
                SectionHeader("Macronutrients (per 100g)", Icons.Filled.Analytics, colors.accentGreen)
                Spacer(Modifier.height(10.dp))
                MacroBarChart(nutrients = nutrients)
            }
        }


        // ── Nutrient Levels Traffic Light ─────────
        val nutrientLevels = product.nutrientLevels
        if (!nutrientLevels.isNullOrEmpty()) {
            item {
                Spacer(Modifier.height(24.dp))
                SectionHeader("Traffic Light", Icons.Filled.Lens, colors.textSecondary)
                Spacer(Modifier.height(10.dp))
                TrafficLightGrid(levels = nutrientLevels)
            }
        }

        // ── Full Ingredients ─────────────────────
        val ingredientsText = product.ingredientsText
        if (!ingredientsText.isNullOrBlank()) {
            item {
                Spacer(Modifier.height(24.dp))
                SectionHeader("Full Ingredients", Icons.Filled.List, colors.textSecondary)
                Spacer(Modifier.height(10.dp))
                FullIngredientsSection(ingredientsText = ingredientsText)
            }
        }

        // ── Energy & Macro Analysis ───────────────
        val nutritionAnalysis = product.nutritionAnalysis
        if (nutritionAnalysis != null) {
            item {
                Spacer(Modifier.height(24.dp))
                SectionHeader("Nutrition Analysis", Icons.Filled.ElectricBolt, colors.accentAmber)
                Spacer(Modifier.height(10.dp))
                if (!nutritionAnalysis.energyEstimation.isNullOrBlank()) {
                    InfoCard(title = "Energy Profile", text = nutritionAnalysis.energyEstimation, icon = Icons.Filled.ElectricBolt, color = colors.accentAmber)
                    Spacer(Modifier.height(10.dp))
                }
                if (!nutritionAnalysis.macronutrientBalance.isNullOrBlank()) {
                    InfoCard(title = "Macro Balance", text = nutritionAnalysis.macronutrientBalance, icon = Icons.Filled.Scale, color = colors.accentBlue)
                }
            }
        }

        // ── Alternatives ─────────────────────────
        val alternatives = product.alternatives
        if (!alternatives.isNullOrEmpty()) {
            item {
                Spacer(Modifier.height(24.dp))
                SectionHeader("Better Alternatives", Icons.Filled.SwapHoriz, colors.accentGreen)
                Spacer(Modifier.height(10.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(alternatives) { alt ->
                        AltItemCard(alt = alt)
                    }
                }
            }
        }

        // ── Raw Nutrients Table ───────────────────
        if (!nutrients.isNullOrEmpty()) {
            item {
                Spacer(Modifier.height(24.dp))
                SectionHeader("All Nutrients", Icons.Filled.TableChart, colors.textSecondary)
                Spacer(Modifier.height(10.dp))
                RawNutrientsTable(nutrients = nutrients)
            }
        }

        // ── Product Metadata ─────────────────────
        item {
            Spacer(Modifier.height(24.dp))
            SectionHeader("Product Details", Icons.Filled.Info, colors.textSecondary)
            Spacer(Modifier.height(10.dp))
            ProductMetadataSection(product = product)
            Spacer(Modifier.height(40.dp))
        }
    }
}

// ─────────────────────────────────────────────────
//  Local Alternative Card
// ─────────────────────────────────────────────────
@Composable
private fun AltItemCard(alt: com.example.healthheatv2.network.AlternativeProduct) {
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
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = alt.name?.take(1)?.uppercase() ?: "?",
                    color = nutriColor, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold
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
            Text(alt.nutriScore?.uppercase() ?: "?", color = nutriColor, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

// ─────────────────────────────────────────────────
//  Section Header
// ─────────────────────────────────────────────────
@Composable
private fun SectionHeader(title: String, icon: ImageVector, tintColor: Color) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tintColor, modifier = Modifier.size(18.dp))
        Text(title, color = colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

// ─────────────────────────────────────────────────
//  Safety Warning Card
// ─────────────────────────────────────────────────
@Composable
private fun SafetyWarningSection(allergens: String?, additives: List<String>?) {
    val colors = LocalAppColors.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.accentRedSubtle)
            .border(1.dp, colors.accentRed.copy(0.3f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = colors.accentRed, modifier = Modifier.size(18.dp))
            Text("Safety Alerts", color = colors.accentRed, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }

        if (!allergens.isNullOrBlank()) {
            Column {
                Text("ALLERGENS", color = colors.accentRed, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                Spacer(Modifier.height(4.dp))
                Text(allergens, color = colors.textPrimary, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }

        if (!additives.isNullOrEmpty()) {
            if (!allergens.isNullOrBlank()) {
                HorizontalDivider(color = colors.accentRed.copy(0.2f))
            }
            Column {
                Text("ADDITIVES (${additives.size})", color = colors.accentRed, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                Spacer(Modifier.height(8.dp))
                // Chips for each additive
                var line = mutableListOf<String>()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    additives.take(6).forEach { additive ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.accentRed.copy(0.15f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(additive.removePrefix("en:").uppercase(), color = colors.accentRed, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                if (additives.size > 6) {
                    Spacer(Modifier.height(4.dp))
                    Text("+${additives.size - 6} more additives", color = colors.textSecondary, fontSize = 11.sp)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────
//  Consumption Advice Card
// ─────────────────────────────────────────────────
@Composable
private fun ConsumptionAdviceCard(product: FoodResponse) {
    val colors = LocalAppColors.current
    val isGood = product.isGoodForHealth ?: false
    val statusColor = if (isGood) colors.accentGreen else colors.accentRed

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.card)
            .border(1.dp, statusColor.copy(0.25f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Health scale
        if (product.healthScale != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Health Scale", color = colors.textSecondary, fontSize = 13.sp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "${product.healthScale}",
                        color = statusColor,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text("/10", color = colors.textSecondary, fontSize = 14.sp)
                }
            }
            // Scale bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (colors.isDark) Color.White.copy(0.08f) else Color.Black.copy(0.08f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth((product.healthScale / 10.0).toFloat().coerceIn(0f, 1f))
                        .clip(RoundedCornerShape(3.dp))
                        .background(statusColor)
                )
            }
        }

        if (!product.healthReason.isNullOrBlank()) {
            HorizontalDivider(color = colors.border)
            Text(product.healthReason, color = colors.textPrimary, fontSize = 13.sp, lineHeight = 20.sp)
        }

        if (!product.safeConsumptionFrequency.isNullOrBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Schedule, contentDescription = null, tint = colors.accentAmber, modifier = Modifier.size(16.dp))
                Text("Safe to consume: ", color = colors.textSecondary, fontSize = 13.sp)
                Text(product.safeConsumptionFrequency, color = colors.accentAmber, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ─────────────────────────────────────────────────
//  Macro Bar Chart  (horizontal bars — always visible in LazyColumn)
// ─────────────────────────────────────────────────
@Composable
private fun MacroBarChart(nutrients: Map<String, Any>) {
    val colors = LocalAppColors.current
    val kcal    = (nutrients["energy-kcal_100g"]    as? Number)?.toFloat() ?: 0f
    val carbs   = (nutrients["carbohydrates_100g"] as? Number)?.toFloat() ?: 0f
    val protein = (nutrients["proteins_100g"]       as? Number)?.toFloat() ?: 0f
    val fat     = (nutrients["fat_100g"]            as? Number)?.toFloat() ?: 0f
    val sugar   = (nutrients["sugars_100g"]         as? Number)?.toFloat() ?: 0f
    val fiber   = (nutrients["fiber_100g"]          as? Number)?.toFloat() ?: 0f
    val salt    = (nutrients["salt_100g"]           as? Number)?.toFloat() ?: 0f

    val maxVal = maxOf(carbs, protein, fat, sugar, fiber, 1f)

    val barData = listOfNotNull(
        if (carbs   > 0) MacroEntry("Carbohydrates", carbs,   "${carbs.roundToInt()}g",   colors.accentBlue)            else null,
        if (fat     > 0) MacroEntry("Total Fat",     fat,     "${fat.roundToInt()}g",     colors.accentRed)             else null,
        if (protein > 0) MacroEntry("Protein",       protein, "${protein.roundToInt()}g", colors.accentGreen)           else null,
        if (sugar   > 0) MacroEntry("Sugars",        sugar,   "${sugar.roundToInt()}g",   Color(0xFF9B59B6))            else null,
        if (fiber   > 0) MacroEntry("Fiber",         fiber,   "${fiber.roundToInt()}g",   colors.accentAmber)           else null,
        if (salt    > 0) MacroEntry("Salt",          salt,    "${"%.2f".format(salt)}g",  colors.textSecondary)         else null,
    )

    // Animate each bar
    val anims = barData.map { remember { Animatable(0f) } }
    LaunchedEffect(Unit) {
        anims.forEachIndexed { i, anim ->
            kotlinx.coroutines.delay(i * 80L)
            anim.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(colors.card)
            .border(1.dp, colors.border, RoundedCornerShape(20.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header row
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Per 100g", color = colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            if (kcal > 0) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.accentAmberSubtle)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("${kcal.roundToInt()} kcal", color = colors.accentAmber, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        HorizontalDivider(color = colors.border)

        // Horizontal bar rows
        barData.forEachIndexed { i, entry ->
            val animPct = (entry.value / maxVal * anims[i].value).coerceIn(0f, 1f)
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.size(8.dp).background(entry.color, CircleShape))
                        Text(entry.label, color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                    Text(entry.display, color = entry.color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                // Horizontal bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (colors.isDark) Color.White.copy(0.07f) else Color.Black.copy(0.06f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animPct)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(entry.color.copy(0.8f), entry.color)
                                )
                            )
                    )
                }
            }
        }
    }
}

private data class MacroEntry(val label: String, val value: Float, val display: String, val color: Color)

// ─────────────────────────────────────────────────
//  Raw Nutrients Table
// ─────────────────────────────────────────────────
@Composable
private fun RawNutrientsTable(nutrients: Map<String, Any>) {
    val colors = LocalAppColors.current
    val displayKeys = nutrients.entries
        .filter { it.key.endsWith("_100g") }
        .map { (k, v) ->
            val label = k.removeSuffix("_100g").replace("-", " ").replace("_", " ")
                .split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
            val value = when (val num = v) {
                is Number -> if (num.toFloat() == num.toFloat().toLong().toFloat()) "${num.toLong()}" else "%.1f".format(num.toFloat())
                else -> v.toString()
            }
            label to value
        }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.card)
            .border(1.dp, colors.border, RoundedCornerShape(16.dp))
    ) {
        displayKeys.forEachIndexed { index, (label, value) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (index % 2 == 0) Color.Transparent else colors.background.copy(0.5f))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(label, color = colors.textSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(value, color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            if (index < displayKeys.lastIndex) {
                HorizontalDivider(color = colors.border.copy(0.5f))
            }
        }
    }
}

// ─────────────────────────────────────────────────
//  Traffic Light Grid
// ─────────────────────────────────────────────────
@Composable
private fun TrafficLightGrid(levels: Map<String, String>) {
    val colors = LocalAppColors.current
    val nutrients = mapOf(
        "sugars" to "Sugar",
        "fat" to "Fat",
        "saturated-fat" to "Saturated Fat",
        "salt" to "Salt",
        "fiber" to "Fiber",
        "proteins" to "Protein"
    )

    Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Group into rows of 2
        nutrients.entries.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (key, label) ->
                    val level = levels[key]?.lowercase() ?: "unknown"
                    val levelColor = when (level) {
                        "low" -> colors.accentGreen
                        "moderate" -> colors.accentAmber
                        "high" -> colors.accentRed
                        else -> colors.textHint
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(levelColor.copy(0.1f))
                            .border(1.dp, levelColor.copy(0.3f), RoundedCornerShape(14.dp))
                            .padding(14.dp)
                    ) {
                        Box(modifier = Modifier.size(10.dp).background(levelColor, CircleShape))
                        Spacer(Modifier.height(8.dp))
                        Text(label, color = colors.textSecondary, fontSize = 11.sp)
                        Text(level.replaceFirstChar { it.uppercase() }, color = levelColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
                // Pad if odd number
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

// ─────────────────────────────────────────────────
//  Full Ingredients Section
// ─────────────────────────────────────────────────
@Composable
private fun FullIngredientsSection(ingredientsText: String) {
    val colors = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.card)
            .border(1.dp, colors.border, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Text(ingredientsText, color = colors.textPrimary, fontSize = 13.sp, lineHeight = 21.sp)
    }
}

// ─────────────────────────────────────────────────
//  Info Card (Energy/Macro analysis)
// ─────────────────────────────────────────────────
@Composable
private fun InfoCard(title: String, text: String, icon: ImageVector, color: Color) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.card)
            .border(1.dp, colors.border, RoundedCornerShape(14.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp).padding(top = 2.dp))
        Column {
            Text(title, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(text, color = colors.textSecondary, fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
}

// ─────────────────────────────────────────────────
//  Product Metadata Section
// ─────────────────────────────────────────────────
@Composable
private fun ProductMetadataSection(product: FoodResponse) {
    val colors = LocalAppColors.current
    val rows = listOfNotNull(
        product.barcode?.let { Icons.Filled.QrCode to Pair("Barcode", it) },
        product.quantity?.let { Icons.Filled.Inventory2 to Pair("Quantity", it) },
        product.servingSize?.let { Icons.Filled.SetMeal to Pair("Serving Size", it) },
        product.packaging?.let { Icons.Filled.Recycling to Pair("Packaging", it) },
        product.categories?.let { Icons.Filled.Category to Pair("Categories", it.take(80)) },
        product.countries?.let { Icons.Filled.Public to Pair("Countries", it) }
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.card)
            .border(1.dp, colors.border, RoundedCornerShape(16.dp))
    ) {
        rows.forEachIndexed { index, (icon, pair) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(icon, contentDescription = null, tint = colors.textHint, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                Column {
                    Text(pair.first, color = colors.textSecondary, fontSize = 11.sp)
                    Text(pair.second, color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
            if (index < rows.lastIndex) {
                HorizontalDivider(color = colors.border)
            }
        }
    }
}
