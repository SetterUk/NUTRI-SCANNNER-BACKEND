package com.example.healthheatv2.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.healthheatv2.ui.theme.LocalAppColors
import com.example.healthheatv2.ui.viewmodel.AuthViewModel
import kotlinx.coroutines.launch
import com.example.healthheatv2.network.RetrofitClient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    authViewModel: AuthViewModel,
    onBackClick: () -> Unit
) {
    val colors = LocalAppColors.current
    val coroutineScope = rememberCoroutineScope()
    
    val userProfile by authViewModel.userProfile.collectAsState()
    
    var age by remember { mutableStateOf(userProfile?.age?.toString() ?: "") }
    var weight by remember { mutableStateOf(userProfile?.weightKg?.toString() ?: "") }
    var height by remember { mutableStateOf(userProfile?.height?.toString() ?: "") }
    var allergies by remember { mutableStateOf(userProfile?.allergies?.joinToString(", ") ?: "") }
    var healthGoals by remember { mutableStateOf(userProfile?.healthGoals ?: "") }

    val diets = listOf("None", "Vegan", "Vegetarian", "Keto", "Diabetic", "Bulking", "Gluten-Free")
    var selectedDiet by remember { mutableStateOf(userProfile?.dietaryPreferences ?: "None") }
    
    var isSaving by remember { mutableStateOf(false) }
    var saveMessage by remember { mutableStateOf<String?>(null) }

    // Calculate Completion dynamically based on current input
    val fields = listOf(
        age.takeIf { it.isNotBlank() },
        weight.takeIf { it.isNotBlank() },
        height.takeIf { it.isNotBlank() },
        selectedDiet.takeIf { it != "None" && it.isNotBlank() },
        allergies.takeIf { it.isNotBlank() },
        healthGoals.takeIf { it.isNotBlank() }
    )
    val filledCount = fields.count { it != null }
    val completionPercentage = if (fields.isNotEmpty()) filledCount.toFloat() / fields.size else 0f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
                }
                Text(
                    text = "Health Profile",
                    color = colors.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                // Profile Completion Bar
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Profile Completion", color = colors.textPrimary, fontWeight = FontWeight.Bold)
                    Text("${(completionPercentage * 100).toInt()}%", color = colors.accentGreen, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { completionPercentage },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = colors.accentGreen,
                    trackColor = colors.card
                )
                
                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = "Personalize Your AI Nutritionist",
                    color = colors.textPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "The AI will tailor its verdicts specifically to your health goals, allergies, and metrics.",
                    color = colors.textSecondary,
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )
                
                Spacer(modifier = Modifier.height(24.dp))

                // Basic Metrics
                OutlinedTextField(
                    value = age,
                    onValueChange = { age = it },
                    label = { Text("Age") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accentGreen,
                        unfocusedBorderColor = colors.border,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        cursorColor = colors.accentGreen
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = height,
                        onValueChange = { height = it },
                        label = { Text("Height (cm)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.accentGreen,
                            unfocusedBorderColor = colors.border,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                            cursorColor = colors.accentGreen
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = weight,
                        onValueChange = { weight = it },
                        label = { Text("Weight (kg)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.accentGreen,
                            unfocusedBorderColor = colors.border,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                            cursorColor = colors.accentGreen
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Allergies & Goals
                OutlinedTextField(
                    value = allergies,
                    onValueChange = { allergies = it },
                    label = { Text("Allergies (e.g. Peanuts, Dairy)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accentGreen,
                        unfocusedBorderColor = colors.border,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        cursorColor = colors.accentGreen
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = healthGoals,
                    onValueChange = { healthGoals = it },
                    label = { Text("Health Goals & Conditions") },
                    placeholder = { Text("e.g. Weight loss, Diabetic") },
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accentGreen,
                        unfocusedBorderColor = colors.border,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        cursorColor = colors.accentGreen
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))
                
                Text(
                    text = "Dietary Preference",
                    color = colors.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

                diets.forEach { diet ->
                    val isSelected = selectedDiet == diet
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) colors.accentGreen.copy(alpha = 0.15f) else colors.card)
                            .clickable { selectedDiet = diet }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = diet,
                            color = if (isSelected) colors.accentGreen else colors.textPrimary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 16.sp
                        )
                        if (isSelected) {
                            Icon(Icons.Filled.Check, contentDescription = "Selected", tint = colors.accentGreen)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                if (saveMessage != null) {
                    Text(
                        text = saveMessage!!,
                        color = colors.accentGreen,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 16.dp)
                    )
                }

                Button(
                    onClick = {
                        coroutineScope.launch {
                            isSaving = true
                            saveMessage = null
                            try {
                                val profileData = mutableMapOf<String, Any>()
                                profileData["dietary_preferences"] = if (selectedDiet == "None") "" else selectedDiet
                                if (age.isNotBlank()) profileData["age"] = age.toIntOrNull() ?: 0
                                if (height.isNotBlank()) profileData["height"] = height.toDoubleOrNull() ?: 0.0
                                if (weight.isNotBlank()) profileData["weight_kg"] = weight.toDoubleOrNull() ?: 0.0
                                if (allergies.isNotBlank()) {
                                    profileData["allergies"] = allergies.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                }
                                if (healthGoals.isNotBlank()) profileData["health_goals"] = healthGoals

                                RetrofitClient.apiService.updateProfile(profileData)
                                authViewModel.fetchProfile()
                                saveMessage = "Profile updated successfully!"
                            } catch (e: Exception) {
                                saveMessage = "Failed: ${e.message}"
                            } finally {
                                isSaving = false
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentGreen),
                    enabled = !isSaving
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(color = colors.background, modifier = Modifier.size(24.dp))
                    } else {
                        Text("Save Profile", color = colors.background, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}
