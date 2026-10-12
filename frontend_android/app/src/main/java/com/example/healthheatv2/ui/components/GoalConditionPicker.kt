package com.example.healthheatv2.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.healthheatv2.services.ProfileNormalizer
import com.example.healthheatv2.ui.theme.LocalAppColors

/**
 * Structured goal (single choice) and health conditions (multi choice).
 * Values are the canonical keys calculateTargets and icmr_rules.json expect,
 * replacing the old free-text box whose contents the app could not act on.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GoalConditionPicker(
    selectedGoal: String,
    onGoalChange: (String) -> Unit,
    selectedConditions: Set<String>,
    onConditionsChange: (Set<String>) -> Unit
) {
    val colors = LocalAppColors.current
    val chipColors = FilterChipDefaults.filterChipColors(
        containerColor = colors.card,
        labelColor = colors.textPrimary,
        selectedContainerColor = colors.accentGreen.copy(alpha = 0.18f),
        selectedLabelColor = colors.accentGreen
    )

    Column {
        Text("Your main goal", color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProfileNormalizer.GOAL_OPTIONS.forEach { (key, label) ->
                FilterChip(
                    selected = selectedGoal == key,
                    onClick = { onGoalChange(key) },
                    label = { Text(label) },
                    colors = chipColors
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Health conditions (optional)", color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(
            "Used to follow ICMR diet guidance for your conditions. Pick all that apply.",
            color = colors.textSecondary, fontSize = 13.sp
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProfileNormalizer.CONDITION_OPTIONS.forEach { (key, label) ->
                val selected = key in selectedConditions
                FilterChip(
                    selected = selected,
                    onClick = { onConditionsChange(if (selected) selectedConditions - key else selectedConditions + key) },
                    label = { Text(label) },
                    colors = chipColors
                )
            }
        }
    }
}
