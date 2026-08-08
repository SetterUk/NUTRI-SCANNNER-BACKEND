package com.example.healthheatv2.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.healthheatv2.data.*
import com.example.healthheatv2.ui.theme.LocalAppColors

@Composable
fun FoodGuideListScreen(
    onFoodSelected: (String) -> Unit,
    onBackClick: () -> Unit
) {
    val colors = LocalAppColors.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<FoodCategory?>(null) }

    val displayedFoods = remember(searchQuery, selectedCategory) {
        val baseList = if (selectedCategory != null)
            allFoodGuideEntries.filter { it.category == selectedCategory }
        else allFoodGuideEntries
        if (searchQuery.isBlank()) baseList
        else baseList.filter {
            it.name.lowercase().contains(searchQuery.trim().lowercase()) ||
            it.commonAdulterants.any { a -> a.lowercase().contains(searchQuery.trim().lowercase()) }
        }
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
            // ── Top Bar ──────────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
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
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Food Purity Guide",
                            color = colors.textPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            "FSSAI-based home tests",
                            color = colors.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                    // Shield icon badge
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFFF3E0)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.VerifiedUser,
                            contentDescription = null,
                            tint = Color(0xFFE65100),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // ── Hero Banner ───────────────────────────
            item {
                FoodGuideHeroBanner()
            }

            // ── Search Bar ───────────────────────────
            item {
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .height(50.dp)
                        .clip(RoundedCornerShape(25.dp))
                        .background(colors.card)
                        .border(1.dp, colors.border, RoundedCornerShape(25.dp))
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        textStyle = TextStyle(color = colors.textPrimary, fontSize = 14.sp),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        decorationBox = { inner ->
                            if (searchQuery.isEmpty()) Text("Search any food (milk, honey, turmeric...)", color = colors.textHint, fontSize = 14.sp)
                            inner()
                        }
                    )
                    if (searchQuery.isNotEmpty()) {
                        Icon(
                            Icons.Filled.Close, contentDescription = "Clear",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(16.dp).clickable { searchQuery = "" }
                        )
                    }
                }
            }

            // ── Category Filter Chips ─────────────────
            item {
                Spacer(Modifier.height(16.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // "All" chip
                    item {
                        CategoryChip(
                            label = "All",
                            emoji = "🍽️",
                            isSelected = selectedCategory == null,
                            onClick = { selectedCategory = null }
                        )
                    }
                    items(FoodCategory.values().toList()) { cat ->
                        CategoryChip(
                            label = cat.displayName,
                            emoji = cat.emoji,
                            isSelected = selectedCategory == cat,
                            onClick = { selectedCategory = if (selectedCategory == cat) null else cat }
                        )
                    }
                }
            }

            // ── Results count ─────────────────────────
            item {
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${displayedFoods.size} foods",
                        color = colors.textSecondary,
                        fontSize = 13.sp
                    )
                    if (searchQuery.isNotEmpty() || selectedCategory != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.accentAmber.copy(0.15f))
                                .clickable { searchQuery = ""; selectedCategory = null }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Clear filters", color = colors.accentAmber, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // ── Risk Legend ───────────────────────────
            item {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Risk:", color = colors.textSecondary, fontSize = 11.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFE53935)))
                        Text("High", color = colors.textSecondary, fontSize = 11.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFF57F17)))
                        Text("Medium", color = colors.textSecondary, fontSize = 11.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF388E3C)))
                        Text("Low", color = colors.textSecondary, fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // ── Food Grid ─────────────────────────────
            if (displayedFoods.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 60.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("🔍", fontSize = 48.sp)
                        Text("No results for \"$searchQuery\"", color = colors.textSecondary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text("Try a different food name", color = colors.textHint, fontSize = 13.sp)
                    }
                }
            } else {
                // 2-column grid using chunked list
                val chunked = displayedFoods.chunked(2)
                items(chunked) { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        row.forEachIndexed { index, entry ->
                            FoodGuideCard(
                                entry = entry,
                                modifier = Modifier.weight(1f),
                                onClick = { onFoodSelected(entry.id) }
                            )
                        }
                        // Fill empty slot in odd row
                        if (row.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────
//  Hero Banner
// ─────────────────────────────────────────────────
@Composable
private fun FoodGuideHeroBanner() {
    val facts = listOf(
        "68% of milk samples tested by FSSAI contained water or detergent.",
        "Argemone oil mixed in mustard oil has caused mass deaths in India.",
        "Metanil yellow, banned in India, is still found in 30% of loose spices.",
        "Honey fraud is rampant — most cheap honey brands fail purity tests.",
        "Kesari dal mixed in pulses causes irreversible paralysis (lathyrism).",
        "Formalin is used to preserve fish — it is a carcinogen used in embalming.",
        "Watermelons are injected with red dye to make flesh appear redder."
    )
    var factIndex by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(5000)
            factIndex = (factIndex + 1) % facts.size
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFFE65100), Color(0xFFF57F17))
                )
            )
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("⚠️", fontSize = 20.sp)
                Text(
                    "Know What's In Your Food",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Text(
                "63 foods · 10 categories · FSSAI-based home tests you can do in minutes",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
            // Animated Did You Know
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.2f))
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("💡 Did You Know?", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(
                        facts[factIndex],
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────
//  Category Chip
// ─────────────────────────────────────────────────
@Composable
private fun CategoryChip(
    label: String,
    emoji: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalAppColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) Color(0xFFE65100) else colors.card)
            .border(
                1.dp,
                if (isSelected) Color(0xFFE65100) else colors.border,
                RoundedCornerShape(20.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(emoji, fontSize = 14.sp)
            Text(
                label,
                color = if (isSelected) Color.White else colors.textSecondary,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

// ─────────────────────────────────────────────────
//  Food Guide Card (2-col grid item)
// ─────────────────────────────────────────────────
@Composable
private fun FoodGuideCard(
    entry: FoodGuideEntry,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = LocalAppColors.current

    val riskColor = when (entry.riskLevel) {
        RiskLevel.HIGH -> Color(0xFFE53935)
        RiskLevel.MEDIUM -> Color(0xFFF57F17)
        RiskLevel.LOW -> Color(0xFF388E3C)
    }
    val riskEmoji = when (entry.riskLevel) {
        RiskLevel.HIGH -> "🔴"
        RiskLevel.MEDIUM -> "🟡"
        RiskLevel.LOW -> "🟢"
    }

    // Stagger animation
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val alpha by animateFloatAsState(targetValue = if (visible) 1f else 0f, animationSpec = tween(400))

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(colors.card)
            .border(1.dp, colors.border, RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(16.dp)
            .alpha(alpha),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Emoji in a colored circle
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(riskColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Text(entry.emoji, fontSize = 26.sp)
        }

        // Name
        Text(
            entry.name,
            color = colors.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 18.sp
        )

        // Category label
        Text(
            entry.category.emoji + " " + entry.category.displayName,
            color = colors.textSecondary,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Bottom row: risk badge + test count
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Risk badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(riskColor.copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Text(
                    "$riskEmoji ${entry.riskLevel.label.replace(" Risk", "")}",
                    color = riskColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            // Test count
            Text(
                "${entry.tests.size} test${if (entry.tests.size != 1) "s" else ""}",
                color = colors.textHint,
                fontSize = 10.sp
            )
        }
    }
}
