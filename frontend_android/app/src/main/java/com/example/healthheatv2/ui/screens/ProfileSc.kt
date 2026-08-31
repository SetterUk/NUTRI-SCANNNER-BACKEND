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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.healthheatv2.ui.theme.AppColors
import com.example.healthheatv2.ui.theme.LocalAppColors
import com.example.healthheatv2.ui.viewmodel.AuthViewModel
import com.example.healthheatv2.mapNetworkProfileToLocal
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

    var preferredName by remember { mutableStateOf(userProfile?.preferredName ?: "") }
    var age by remember { mutableStateOf(userProfile?.age?.toString() ?: "") }
    var weight by remember { mutableStateOf(userProfile?.weightKg?.toString() ?: "") }
    var height by remember { mutableStateOf(userProfile?.height?.toString() ?: "") }
    var allergies by remember { mutableStateOf(userProfile?.allergies?.joinToString(", ") ?: "") }
    var healthGoals by remember { mutableStateOf(userProfile?.healthGoals ?: "") }
    var gender by remember { mutableStateOf(userProfile?.gender ?: "Prefer not to say") }
    var activityLevel by remember { mutableStateOf(userProfile?.activityLevel ?: "Prefer not to say") }

    val diets = listOf("None", "Vegan", "Vegetarian", "Keto", "Diabetic", "Bulking", "Gluten-Free")
    val genders = listOf("Prefer not to say", "Female", "Male", "Non-binary", "Other")
    val activityLevels = listOf("Prefer not to say", "Sedentary", "Lightly Active", "Moderately Active", "Very Active", "Extra Active")
    var selectedDiet by remember { mutableStateOf(userProfile?.dietaryPreferences ?: "None") }

    var isSaving by remember { mutableStateOf(false) }
    var saveMessage by remember { mutableStateOf<String?>(null) }

    // Edit mode toggle — defaults to view mode if profile is already filled
    val isProfileFilled = userProfile != null
    var isEditing by remember { mutableStateOf(!isProfileFilled) }

    LaunchedEffect(userProfile) {
        userProfile?.let { profile ->
            preferredName = profile.preferredName ?: ""
            age = profile.age?.toString() ?: ""
            weight = profile.weightKg?.toString() ?: ""
            height = profile.height?.toString() ?: ""
            allergies = profile.allergies?.joinToString(", ") ?: ""
            healthGoals = profile.healthGoals ?: ""
            selectedDiet = profile.dietaryPreferences.takeIf { !it.isNullOrBlank() } ?: "None"
            gender = profile.gender ?: "Prefer not to say"
            activityLevel = profile.activityLevel ?: "Prefer not to say"
            // Once profile loads, switch to view mode
            isEditing = false
        }
    }

    // Calculate Completion dynamically based on current input
    val fields = listOf(
        age.takeIf { it.isNotBlank() },
        weight.takeIf { it.isNotBlank() },
        height.takeIf { it.isNotBlank() },
        selectedDiet.takeIf { it != "None" && it.isNotBlank() },
        allergies.takeIf { it.isNotBlank() },
        healthGoals.takeIf { it.isNotBlank() },
        gender.takeIf { it != "Prefer not to say" },
        activityLevel.takeIf { it != "Prefer not to say" }
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
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .weight(1f)
                )
                // Pencil / Done button
                if (isEditing) {
                    TextButton(onClick = {
                        // Cancel edit — restore from profile
                        userProfile?.let { profile ->
                            age = profile.age?.toString() ?: ""
                            weight = profile.weightKg?.toString() ?: ""
                            height = profile.height?.toString() ?: ""
                            allergies = profile.allergies?.joinToString(", ") ?: ""
                            healthGoals = profile.healthGoals ?: ""
                            selectedDiet = profile.dietaryPreferences.takeIf { !it.isNullOrBlank() } ?: "None"
                        }
                        saveMessage = null
                        isEditing = false
                    }) {
                        Text("Cancel", color = colors.textSecondary, fontSize = 14.sp)
                    }
                } else {
                    IconButton(onClick = { isEditing = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit Profile", tint = colors.accentGreen)
                    }
                }
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

                if (isEditing) {
                    // ── EDIT MODE ─────────────────────────────────────────────

                    OutlinedTextField(
                        value = preferredName,
                        onValueChange = { preferredName = it },
                        label = { Text("Your Name") },
                        placeholder = { Text("What should we call you?") },
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
                    OutlinedTextField(
                        value = allergies,
                        onValueChange = { allergies = it },
                        label = { Text("Allergies (comma separated, e.g. Peanuts, Dairy)") },
                        placeholder = { Text("Separate multiple allergies with commas") },
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
                    Text(
                        text = "Gender",
                        color = colors.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    genders.forEach { option ->
                        val selected = gender == option
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) colors.accentGreen.copy(alpha = 0.15f) else colors.card)
                                .clickable { gender = option }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = option,
                                color = if (selected) colors.accentGreen else colors.textPrimary,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 16.sp
                            )
                            if (selected) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = colors.accentGreen)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Activity Level",
                        color = colors.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    activityLevels.forEach { option ->
                        val selected = activityLevel == option
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) colors.accentGreen.copy(alpha = 0.15f) else colors.card)
                                .clickable { activityLevel = option }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = option,
                                color = if (selected) colors.accentGreen else colors.textPrimary,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 16.sp
                            )
                            if (selected) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = colors.accentGreen)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = healthGoals,
                        onValueChange = { healthGoals = it },
                        label = { Text("Health Goals & Conditions (comma separated)") },
                        placeholder = { Text("e.g. Weight loss, Diabetic, High blood pressure") },
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
                    Text("Dietary Preference", color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
                            color = if (saveMessage!!.startsWith("Failed")) MaterialTheme.colorScheme.error else colors.accentGreen,
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
                                    if (preferredName.isNotBlank()) profileData["preferred_name"] = preferredName
                                    profileData["dietary_preferences"] = if (selectedDiet == "None") "" else selectedDiet
                                    if (age.isNotBlank()) profileData["age"] = age.toIntOrNull() ?: 0
                                    if (height.isNotBlank()) profileData["height"] = height.toDoubleOrNull() ?: 0.0
                                    if (weight.isNotBlank()) profileData["weight_kg"] = weight.toDoubleOrNull() ?: 0.0
                                    profileData["gender"] = if (gender == "Prefer not to say") "" else gender
                                    profileData["activity_level"] = if (activityLevel == "Prefer not to say") "" else activityLevel
                                    profileData["allergies"] = if (allergies.isNotBlank())
                                        allergies.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                    else emptyList<String>()
                                    if (healthGoals.isNotBlank()) profileData["health_goals"] = healthGoals

                                    RetrofitClient.apiService.updateProfile(profileData)
                                    authViewModel.fetchProfile()
                                    saveMessage = "Profile updated successfully!"
                                    isEditing = false
                                } catch (e: Exception) {
                                    saveMessage = "Failed: ${e.message}"
                                } finally {
                                    isSaving = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
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

                } else {
                    // ── VIEW MODE ──────────────────────────────────────────

                    if (preferredName.isNotBlank()) {
                        ProfileInfoCard(label = "👋 Display Name", value = preferredName, colors = colors)
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    ProfileInfoCard(label = "Age", value = if (age.isNotBlank()) "$age years" else "Not set", colors = colors)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(modifier = Modifier.weight(1f)) {
                            ProfileInfoCard(label = "Height", value = if (height.isNotBlank()) "$height cm" else "Not set", colors = colors)
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            ProfileInfoCard(label = "Weight", value = if (weight.isNotBlank()) "$weight kg" else "Not set", colors = colors)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    ProfileInfoCard(
                        label = "Allergies",
                        value = if (allergies.isNotBlank()) allergies else "None",
                        colors = colors
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ProfileInfoCard(
                        label = "Health Goals & Conditions",
                        value = if (healthGoals.isNotBlank()) healthGoals else "Not set",
                        colors = colors
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ProfileInfoCard(
                        label = "Gender",
                        value = if (gender != "Prefer not to say") gender else "Not set",
                        colors = colors
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ProfileInfoCard(
                        label = "Activity Level",
                        value = if (activityLevel != "Prefer not to say") activityLevel else "Not set",
                        colors = colors
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Dietary Preference", color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.accentGreen.copy(alpha = 0.12f))
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (selectedDiet == "None" || selectedDiet.isBlank()) "No preference set" else selectedDiet,
                            color = colors.accentGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        if (selectedDiet != "None" && selectedDiet.isNotBlank()) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = colors.accentGreen)
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                    Text("Calculated Targets", color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Based on your metrics, here are your personalized daily targets.",
                        color = colors.textSecondary,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    val mappedProfile = userProfile?.let { mapNetworkProfileToLocal(it) }
                    
                    val bmiStr = mappedProfile?.bmi?.let { String.format(java.util.Locale.US, "%.1f", it) } ?: "Not calculated"
                    val bmrStr = mappedProfile?.bmr?.let { "${it.toInt()} kcal/day" } ?: "Not calculated"
                    val tdeeStr = mappedProfile?.tdee?.let { "${it.toInt()} kcal/day" } ?: "Not calculated"
                    val calStr = mappedProfile?.dailyCalories?.let { "${it.toInt()} kcal/day" } ?: "Not calculated"

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(modifier = Modifier.weight(1f)) {
                            ProfileInfoCard(label = "Current BMI", value = bmiStr, colors = colors)
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            ProfileInfoCard(label = "Basal Metabolic Rate (BMR)", value = bmrStr, colors = colors)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(modifier = Modifier.weight(1f)) {
                            ProfileInfoCard(label = "Daily Energy (TDEE)", value = tdeeStr, colors = colors)
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            ProfileInfoCard(label = "Target Calories", value = calStr, colors = colors)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}

@Composable
private fun ProfileInfoCard(
    label: String,
    value: String,
    colors: AppColors
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.card)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            text = label,
            color = colors.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            color = colors.textPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
