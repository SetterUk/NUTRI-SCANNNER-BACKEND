package com.example.healthheatv2.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.healthheatv2.services.MealCandidate
import com.example.healthheatv2.services.NutritionGap
import com.example.healthheatv2.services.RecommendationEngine
import kotlinx.coroutines.launch

@Composable
fun FixMyNutritionSc(
    gap: NutritionGap,
    recommendationEngine: RecommendationEngine,
    onBackClick: () -> Unit
) {
    var candidates by remember { mutableStateOf<List<MealCandidate>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(gap) {
        scope.launch {
            isLoading = true
            // Generate recommendations filtering for budget and user allergies
            candidates = recommendationEngine.generateRecommendations(
                biggestGap = gap,
                remainingCalories = 500f, // Example target, typically from GapAnalysis
                budgetPref = 100f // "Under ₹100" demo constraint
            )
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFAFAFA))
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = onBackClick, colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.Black)) {
                Text("← Back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Fix My Nutrition",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        }

        PaddingValues(16.dp)
        
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Addressing Gap: ${gap.nutrient}",
                style = MaterialTheme.typography.titleMedium,
                color = Color.Gray
            )
            Text(
                text = "You need ${gap.gap.toInt()}${gap.unit}.",
                style = MaterialTheme.typography.bodyLarge
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFFFF9800))
                }
            } else if (candidates.isEmpty()) {
                Text("No safe, budget-friendly options found that fit your remaining calories.")
            } else {
                Text("Personalized Recommendations", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(8.dp))
                
                LazyColumn {
                    items(candidates) { candidate ->
                        MealCandidateCard(candidate)
                    }
                }
            }
        }
    }
}

@Composable
fun MealCandidateCard(candidate: MealCandidate) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = candidate.name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Suggested: ${candidate.amountToConsume}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
                Text(
                    text = "+${candidate.gapReduced.toInt()}g",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF4CAF50)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // "Why?" Tags
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text("Fits Calorie Budget") })
                AssistChip(onClick = {}, label = { Text("Safe (No Allergens)") })
            }
        }
    }
}
