package com.example.healthheatv2.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.healthheatv2.services.MealCandidate
import com.example.healthheatv2.services.NutritionGap
import com.example.healthheatv2.services.RecommendationEngine
import com.example.healthheatv2.ui.theme.LocalAppColors
import kotlinx.coroutines.launch

@Composable
fun FixMyNutritionSc(
    gap: NutritionGap,
    recommendationEngine: RecommendationEngine,
    nanoCoach: com.example.healthheatv2.ai.NanoNutritionistCoach,
    userProfile: com.example.healthheatv2.data.UserProfile,
    onBackClick: () -> Unit
) {
    val colors = LocalAppColors.current
    var candidates by remember { mutableStateOf<List<MealCandidate>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var aiRationale by remember { mutableStateOf<String?>(null) }
    var rationaleLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(gap) {
        scope.launch {
            isLoading = true
            candidates = recommendationEngine.generateRecommendations(
                biggestGap = gap,
                remainingCalories = 500f
            )
            isLoading = false
            // Auto-generate AI rationale once we have food candidates
            if (candidates.isNotEmpty()) {
                rationaleLoading = true
                try {
                    aiRationale = nanoCoach.generateFixMyNutritionRationale(gap, candidates, userProfile)
                } catch (e: Exception) {
                    aiRationale = null
                } finally {
                    rationaleLoading = false
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // Shadcn-style Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, bottom = 12.dp, start = 8.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
            }
            Text(
                text = "Target: ${gap.nutrient.replaceFirstChar { it.uppercase() }}",
                color = colors.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        Divider(color = colors.border, thickness = 1.dp)

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Gap Summary Card
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.accentRed.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Warning, contentDescription = null, tint = colors.accentRed)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Current Deficit",
                                color = colors.textHint,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${gap.gap.toInt()}${gap.unit} ${gap.nutrient}",
                                color = colors.textPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            
            item {
                Text(
                    text = "Recommended Options",
                    color = colors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // AI Rationale Card
            item {
                AiRationaleCard(
                    rationale = aiRationale,
                    isLoading = rationaleLoading
                )
            }

            if (isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = colors.accentGreen)
                    }
                }
            } else if (candidates.isEmpty()) {
                item {
                    Text(
                        text = "No safe options found that fit your diet restrictions and remaining calories.",
                        color = colors.textSecondary,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                items(candidates) { candidate ->
                    MealCandidateCard(candidate)
                }
            }
        }
    }
}

@Composable
fun MealCandidateCard(candidate: MealCandidate) {
    val colors = LocalAppColors.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.card,
        border = BorderStroke(1.dp, colors.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = candidate.name.replaceFirstChar { it.uppercase() },
                        color = colors.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Suggested Serving: ${candidate.amountToConsume}",
                        color = colors.textSecondary,
                        fontSize = 14.sp
                    )
                }
                
                // Gain Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = colors.accentGreenSubtle,
                    border = BorderStroke(1.dp, colors.accentGreen.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = "+${candidate.gapReduced.toInt()}g",
                        color = colors.accentGreen,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 14.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Info Tags
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.LocalFireDepartment, contentDescription = null, tint = colors.accentAmber, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Fits Diet", color = colors.textSecondary, fontSize = 12.sp)
                    }
                }
                
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("Allergen Free", color = colors.textSecondary, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun AiRationaleCard(rationale: String?, isLoading: Boolean) {
    val colors = LocalAppColors.current
    if (!isLoading && rationale == null) return

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.accentGreen.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, colors.accentGreen.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⚡", fontSize = 16.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Why These Foods?",
                    color = colors.accentGreen,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = colors.accentGreen,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }
            } else if (rationale != null) {
                val cleanRationale = rationale
                    .replace(Regex("^(⚡ \\[Gemma 4 E2B\\]|☁️ \\[Cloud AI\\]|🔋 \\[Offline\\])\\s*\n"), "")
                    .trim()
                Text(
                    text = cleanRationale,
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    lineHeight = 21.sp
                )
            }
        }
    }
}
