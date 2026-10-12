package com.example.healthheatv2.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.healthheatv2.data.LoggedMeal
import com.example.healthheatv2.ui.theme.LocalAppColors
import kotlinx.coroutines.launch

/**
 * Shows what the user has logged today, lets them delete mistakes, and add food
 * by typing ("2 roti, 1 bowl dal") without going through the chatbot.
 *
 * @param onAdd logs the text and returns a short human-readable result line.
 */
@Composable
fun TodayMealsCard(
    meals: List<LoggedMeal>,
    onDelete: (LoggedMeal) -> Unit,
    onAdd: suspend (String) -> String
) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var resultLine by remember { mutableStateOf<String?>(null) }

    fun submit() {
        val text = input.trim()
        if (text.isBlank() || busy) return
        busy = true
        scope.launch {
            resultLine = try { onAdd(text) } catch (e: Exception) { "Couldn't log that: ${e.message}" }
            input = ""
            busy = false
        }
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.card,
        border = BorderStroke(1.dp, colors.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Today's Meals", color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${meals.sumOf { it.calories.toDouble() }.toInt()} kcal", color = colors.textSecondary, fontSize = 14.sp)
            }
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !busy,
                placeholder = { Text("e.g. 2 roti, 1 bowl dal, 200g curd", color = colors.textHint, fontSize = 14.sp) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                trailingIcon = {
                    if (busy) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = colors.accentGreen)
                    } else {
                        IconButton(onClick = { submit() }, enabled = input.isNotBlank()) {
                            Icon(Icons.Filled.Add, contentDescription = "Log food", tint = colors.accentGreen)
                        }
                    }
                },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    focusedBorderColor = colors.accentGreen,
                    unfocusedBorderColor = colors.border,
                    cursorColor = colors.accentGreen
                )
            )
            resultLine?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = colors.textSecondary, fontSize = 13.sp)
            }

            Spacer(Modifier.height(8.dp))
            if (meals.isEmpty()) {
                Text("Nothing logged yet today.", color = colors.textHint, fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp))
            } else {
                val slotOrder = listOf("breakfast", "lunch", "snack", "dinner")
                meals.groupBy { it.mealSlot }.toSortedMap(compareBy { slotOrder.indexOf(it).let { i -> if (i < 0) 99 else i } })
                    .forEach { (slot, slotMeals) ->
                        Text(
                            slot.replaceFirstChar { it.uppercase() },
                            color = colors.accentGreen, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                        )
                        slotMeals.forEach { meal ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(meal.foodName, color = colors.textPrimary, fontSize = 15.sp)
                                    Text(
                                        "${meal.quantity.toInt()}g · ${meal.calories.toInt()} kcal · ${meal.protein.toInt()}g protein" +
                                            if (meal.isEstimated) " · estimated" else "",
                                        color = if (meal.isEstimated) colors.accentAmber else colors.textSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                                IconButton(onClick = { onDelete(meal) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete ${meal.foodName}", tint = colors.textHint)
                                }
                            }
                        }
                    }
            }
        }
    }
}
