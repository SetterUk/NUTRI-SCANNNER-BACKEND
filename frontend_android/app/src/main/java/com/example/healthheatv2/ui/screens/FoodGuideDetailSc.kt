package com.example.healthheatv2.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.healthheatv2.data.*
import com.example.healthheatv2.ui.theme.LocalAppColors

@Composable
fun FoodGuideDetailScreen(
    foodId: String,
    onBackClick: () -> Unit
) {
    val colors = LocalAppColors.current
    val entry = remember(foodId) { allFoodGuideEntries.firstOrNull { it.id == foodId } }

    if (entry == null) {
        Box(Modifier.fillMaxSize().background(colors.background), contentAlignment = Alignment.Center) {
            Text("Food not found", color = colors.textSecondary)
        }
        return
    }

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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        // ── Hero Section ───────────────────────────
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                riskColor.copy(alpha = 0.25f),
                                colors.background
                            )
                        )
                    )
            ) {
                // Back button
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(16.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.3f))
                        .clickable { onBackClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(20.dp))
                }

                // FSSAI badge top-right
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(16.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.3f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                        Text("FSSAI Based", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                // Center: big emoji + name
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .clip(CircleShape)
                            .background(riskColor.copy(alpha = 0.15f))
                            .border(2.dp, riskColor.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(entry.emoji, fontSize = 46.sp)
                    }
                    Text(
                        entry.name,
                        color = colors.textPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    // Risk badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(riskColor.copy(alpha = 0.15f))
                            .border(1.dp, riskColor.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            "$riskEmoji ${entry.riskLevel.label}",
                            color = riskColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // ── What Is Commonly Added Section ─────────
        item {
            Spacer(Modifier.height(4.dp))
            SectionHeader(icon = "⚗️", title = "Common Adulterants")
            Spacer(Modifier.height(12.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(entry.commonAdulterants) { adulterant ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(riskColor.copy(alpha = 0.1f))
                            .border(1.dp, riskColor.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(adulterant, color = riskColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        // ── Home Tests ─────────────────────────────
        item {
            Spacer(Modifier.height(24.dp))
            SectionHeader(icon = "🧪", title = "Home Tests (${entry.tests.size})")
            Spacer(Modifier.height(4.dp))
            Text(
                "These tests can be performed at home with common items.",
                color = colors.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(12.dp))
        }

        // Each test card
        items(entry.tests.size) { index ->
            val test = entry.tests[index]
            var expanded by remember { mutableStateOf(index == 0) } // First test expanded by default

            TestCard(
                test = test,
                index = index,
                isExpanded = expanded,
                onToggle = { expanded = !expanded },
                riskColor = riskColor
            )
            Spacer(Modifier.height(10.dp))
        }

        // ── Health Risks ───────────────────────────
        item {
            Spacer(Modifier.height(8.dp))
            SectionHeader(icon = "⚠️", title = "Health Risks")
            Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFE53935).copy(alpha = 0.08f))
                    .border(1.dp, Color(0xFFE53935).copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = Color(0xFFE53935),
                        modifier = Modifier.size(20.dp).padding(top = 2.dp)
                    )
                    Text(
                        entry.healthRisks,
                        color = Color(0xFFE53935),
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                }
            }
        }

        // ── Buying Tip ─────────────────────────────
        item {
            Spacer(Modifier.height(16.dp))
            SectionHeader(icon = "💡", title = "Smart Buying Tip")
            Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1565C0).copy(alpha = 0.08f))
                    .border(1.dp, Color(0xFF1565C0).copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("💡", fontSize = 18.sp)
                    Text(
                        entry.buyingTip,
                        color = Color(0xFF1565C0),
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }

        // ── Disclaimer ─────────────────────────────
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Gray.copy(alpha = 0.08f))
                    .padding(12.dp)
            ) {
                Text(
                    "📋 This guide is based on FSSAI (Food Safety and Standards Authority of India) guidelines. Tests are for preliminary detection only. For legal/medical purposes, use a certified laboratory.",
                    color = Color.Gray,
                    fontSize = 10.sp,
                    lineHeight = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────
//  Section Header
// ─────────────────────────────────────────────────
@Composable
private fun SectionHeader(icon: String, title: String) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier.padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 18.sp)
        Text(
            title,
            color = colors.textPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

// ─────────────────────────────────────────────────
//  Test Card (expandable)
// ─────────────────────────────────────────────────
@Composable
private fun TestCard(
    test: AdulterationTest,
    index: Int,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    riskColor: Color
) {
    val colors = LocalAppColors.current

    val difficultyColor = when (test.difficulty) {
        TestDifficulty.EASY -> Color(0xFF388E3C)
        TestDifficulty.MEDIUM -> Color(0xFFF57F17)
        TestDifficulty.ADVANCED -> Color(0xFFE53935)
    }

    val rotationDegrees by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(300)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.card)
            .border(1.dp, colors.border, RoundedCornerShape(18.dp))
    ) {
        // Header row (always visible)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Number circle
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(riskColor.copy(alpha = 0.15f))
                    .border(1.5.dp, riskColor.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "${index + 1}",
                    color = riskColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    test.testName,
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${test.difficulty.emoji} ${test.difficulty.label}",
                        color = difficultyColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text("·", color = colors.textHint, fontSize = 11.sp)
                    Text(
                        "Need: ${test.whatYouNeed}",
                        color = colors.textSecondary,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
            }

            Icon(
                Icons.Filled.ExpandMore,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = colors.textSecondary,
                modifier = Modifier
                    .size(22.dp)
                    .rotate(rotationDegrees)
            )
        }

        // Expandable content
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                HorizontalDivider(color = colors.border, modifier = Modifier.padding(bottom = 14.dp))

                // What you need
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.background)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text("🧰", fontSize = 16.sp)
                    Column {
                        Text("What You Need", color = colors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Text(test.whatYouNeed, color = colors.textPrimary, fontSize = 13.sp, lineHeight = 18.sp)
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Steps
                Text("Steps", color = colors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                test.steps.forEachIndexed { i, step ->
                    Row(
                        modifier = Modifier.padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(colors.accentBlue.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("${i + 1}", color = colors.accentBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(step, color = colors.textPrimary, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.weight(1f))
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Results
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Pure result
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF388E3C).copy(alpha = 0.1f))
                            .border(1.dp, Color(0xFF388E3C).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("✅", fontSize = 12.sp)
                            Text("Pure", color = Color(0xFF388E3C), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(test.pureResult, color = Color(0xFF388E3C), fontSize = 12.sp, lineHeight = 17.sp)
                    }
                    // Adulterated result
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFE53935).copy(alpha = 0.1f))
                            .border(1.dp, Color(0xFFE53935).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("❌", fontSize = 12.sp)
                            Text("Adulterated", color = Color(0xFFE53935), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(test.adulteratedResult, color = Color(0xFFE53935), fontSize = 12.sp, lineHeight = 17.sp)
                    }
                }
            }
        }
    }
}
