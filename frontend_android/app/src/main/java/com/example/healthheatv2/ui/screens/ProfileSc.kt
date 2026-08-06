package com.example.healthheatv2.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.healthheatv2.network.RetrofitClient
import com.example.healthheatv2.ui.theme.LocalAppColors
import kotlinx.coroutines.launch

@Composable
fun ProfileSc(
    onNavigateBack: () -> Unit
) {
    val colors = LocalAppColors.current
    val coroutineScope = rememberCoroutineScope()
    
    var diet by remember { mutableStateOf("") }
    var allergies by remember { mutableStateOf("") }
    var healthGoal by remember { mutableStateOf("") }
    
    var isSaving by remember { mutableStateOf(false) }
    var saveMessage by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(24.dp)
    ) {
        // Top Bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = colors.textPrimary
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "Health Profile",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "Dietary Preferences",
            color = colors.textSecondary,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = diet,
            onValueChange = { diet = it },
            placeholder = { Text("e.g. Vegan, Keto, None") },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = colors.surface,
                unfocusedContainerColor = colors.surface,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary
            )
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Allergies (comma separated)",
            color = colors.textSecondary,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = allergies,
            onValueChange = { allergies = it },
            placeholder = { Text("e.g. Peanuts, Gluten") },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = colors.surface,
                unfocusedContainerColor = colors.surface,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary
            )
        )

        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Health Goal",
            color = colors.textSecondary,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = healthGoal,
            onValueChange = { healthGoal = it },
            placeholder = { Text("e.g. Lose weight, Build muscle") },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = colors.surface,
                unfocusedContainerColor = colors.surface,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary
            )
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        if (saveMessage.isNotEmpty()) {
            Text(
                text = saveMessage,
                color = colors.accentGreen,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        Button(
            onClick = {
                isSaving = true
                coroutineScope.launch {
                    try {
                        val allergyList = allergies.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        val data = mapOf(
                            "dietary_preferences" to diet,
                            "health_goals" to healthGoal,
                            "allergies" to allergyList
                        )
                        RetrofitClient.apiService.updateProfile(data)
                        saveMessage = "Profile saved successfully!"
                    } catch (e: Exception) {
                        saveMessage = "Error saving profile: ${e.localizedMessage}"
                    } finally {
                        isSaving = false
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(16.dp)),
            colors = ButtonDefaults.buttonColors(containerColor = colors.accentBlue),
            enabled = !isSaving
        ) {
            Text(
                text = if (isSaving) "Saving..." else "Save Profile",
                color = colors.background,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
