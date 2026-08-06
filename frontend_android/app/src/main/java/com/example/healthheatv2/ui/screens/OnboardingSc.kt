package com.example.healthheatv2.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.healthheatv2.ui.theme.LocalAppColors
import com.example.healthheatv2.ui.viewmodel.AuthViewModel
import kotlinx.coroutines.launch
import com.example.healthheatv2.network.RetrofitClient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    authViewModel: AuthViewModel,
    onFinish: () -> Unit
) {
    val colors = LocalAppColors.current
    val coroutineScope = rememberCoroutineScope()

    var currentStep by remember { mutableStateOf(0) }
    val totalSteps = 3

    var preferredName by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var allergies by remember { mutableStateOf("") }
    var healthGoals by remember { mutableStateOf("") }

    var isSaving by remember { mutableStateOf(false) }
    var saveMessage by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // ── Top Bar ───────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Step dots
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(totalSteps) { i ->
                        Box(
                            modifier = Modifier
                                .size(if (i == currentStep) 24.dp else 8.dp, 8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (i <= currentStep) colors.accentGreen
                                    else colors.card
                                )
                        )
                    }
                }
                IconButton(onClick = {
                    authViewModel.markOnboardingSeen()
                    onFinish()
                }) {
                    Icon(Icons.Filled.Close, contentDescription = "Skip all", tint = colors.textSecondary)
                }
            }

            // ── Page Content ─────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                when (currentStep) {

                    // ═══════════════════════════════════════════════════════
                    // PAGE 1 — WHY WE EXIST
                    // ═══════════════════════════════════════════════════════
                    0 -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 28.dp, vertical = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            Text(
                                text = "💚",
                                fontSize = 52.sp,
                            )
                            Text(
                                text = "We care about you.\nReally.",
                                color = colors.textPrimary,
                                fontSize = 30.sp,
                                fontWeight = FontWeight.ExtraBold,
                                lineHeight = 36.sp
                            )
                            Text(
                                text = "Someone who loves you has probably said 'watch what you eat' — but never explained how.\n\nWe built HealthHeat because that gap breaks our hearts. People get sick from things they never knew were harmful. Families make choices they wouldn't make if they just had the right information.\n\nYou deserve better than that. Tell us about yourself — your allergies, your goals, your life — and we'll make sure every scan speaks directly to you. Because your health isn't generic. And neither are we. 💚",
                                color = colors.textSecondary,
                                fontSize = 16.sp,
                                lineHeight = 26.sp
                            )
                        }
                    }

                    // ═══════════════════════════════════════════════════════
                    // PAGE 2 — HOW COMPANIES FOOL YOU
                    // ═══════════════════════════════════════════════════════
                    1 -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 28.dp, vertical = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            Text(
                                text = "🔍",
                                fontSize = 52.sp,
                            )
                            Text(
                                text = "The label lies.\nHere's the truth.",
                                color = colors.textPrimary,
                                fontSize = 30.sp,
                                fontWeight = FontWeight.ExtraBold,
                                lineHeight = 36.sp
                            )
                            Text(
                                text = "Food companies are legally allowed to mislead you — and they do it every single day on supermarket shelves.",
                                color = colors.textSecondary,
                                fontSize = 16.sp,
                                lineHeight = 26.sp
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            val tricks = listOf(
                                Pair("\"0g Trans Fat\" 🎩", "A product can have up to 0.5g of trans fat per serving and still legally print ZERO on the label. Their secret? Just make the serving size hilariously tiny. One chip. Enjoy."),
                                Pair("\"Natural\" or \"Natural Flavors\" 🌿", "Sounds wholesome, right? Legally, this can include highly processed chemicals — as long as they were once derived from something natural. Your shoe is also technically natural. Just saying."),
                                Pair("\"Low Fat\" 😇", "Fat removed = taste removed. So companies dump in sugar, salt, and a cocktail of additives to fix it. Congrats, you bought the unhealthier version of the unhealthy thing."),
                                Pair("\"Made with Real Fruit\" 🍓", "That beautiful strawberry on the box? The product might contain 2% real fruit. The rest is artificial flavoring, red dye, and the audacity to put a strawberry on the packaging."),
                                Pair("\"Multigrain\" or \"Wheat\" 🌾", "Spoiler: multigrain just means multiple types of refined flour. It's like calling a pizza 'multivegetable' because it has tomato sauce. A few whole grains are sprinkled in for show."),
                                Pair("Hidden Sugar has 60+ aliases 🕵️", "Corn syrup. Dextrose. Maltose. Fructose. Cane juice. Evaporated cane juice (same thing, fancier name). Companies split sugar across 10 names so none appear first on the list. Pure villain behaviour."),
                            )

                            tricks.forEach { (title, desc) ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(colors.card)
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Warning,
                                            contentDescription = null,
                                            tint = Color(0xFFF59E0B),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = title,
                                            color = Color(0xFFF59E0B),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }
                                    Text(
                                        text = desc,
                                        color = colors.textSecondary,
                                        fontSize = 14.sp,
                                        lineHeight = 22.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "We see all of it. Every scan, every trick, every sneaky name. You're welcome. 🔍",
                                color = colors.accentGreen,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                lineHeight = 24.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // ═══════════════════════════════════════════════════════
                    // PAGE 3 — PROFILE FORM
                    // ═══════════════════════════════════════════════════════
                    2 -> {
                        Column(modifier = Modifier.padding(24.dp)) {
                            Text(
                                text = "Tell us about yourself",
                                color = colors.textPrimary,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Every detail helps us give you scans that actually mean something for your life. Skip anything you prefer.",
                                color = colors.textSecondary,
                                fontSize = 15.sp,
                                lineHeight = 22.sp
                            )

                            Spacer(modifier = Modifier.height(28.dp))

                            OutlinedTextField(
                                value = preferredName,
                                onValueChange = { preferredName = it },
                                label = { Text("Your name") },
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
                                modifier = Modifier.fillMaxWidth().height(120.dp),
                                maxLines = 4,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = colors.accentGreen,
                                    unfocusedBorderColor = colors.border,
                                    focusedTextColor = colors.textPrimary,
                                    unfocusedTextColor = colors.textPrimary,
                                    cursorColor = colors.accentGreen
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )

                            Spacer(modifier = Modifier.height(48.dp))

                            if (saveMessage != null) {
                                Text(
                                    text = saveMessage!!,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp),
                                    fontSize = 14.sp
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
                                            if (age.isNotBlank()) profileData["age"] = age.toIntOrNull() ?: 0
                                            if (height.isNotBlank()) profileData["height"] = height.toDoubleOrNull() ?: 0.0
                                            if (weight.isNotBlank()) profileData["weight_kg"] = weight.toDoubleOrNull() ?: 0.0
                                            if (allergies.isNotBlank()) {
                                                profileData["allergies"] = allergies.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                            }
                                            if (healthGoals.isNotBlank()) profileData["health_goals"] = healthGoals

                                            RetrofitClient.apiService.updateProfile(profileData)
                                            authViewModel.fetchProfile()
                                            authViewModel.markOnboardingSeen()
                                            onFinish()
                                        } catch (e: Exception) {
                                            saveMessage = "Could not save profile. ${e.localizedMessage ?: "Please try again."}"
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
                                    Text("Save & Continue", color = colors.background, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }
            }

            // ── Bottom Nav (Next / Back) ──────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .navigationBarsPadding(),
                horizontalArrangement = if (currentStep > 0) Arrangement.SpaceBetween else Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentStep > 0) {
                    OutlinedButton(
                        onClick = { currentStep-- },
                        shape = RoundedCornerShape(12.dp),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            width = 1.dp
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textPrimary)
                    ) {
                        Text("← Back")
                    }
                }

                // Only show Next on pages 0 and 1
                if (currentStep < totalSteps - 1) {
                    Button(
                        onClick = { currentStep++ },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accentGreen)
                    ) {
                        Text("Next →", color = colors.background, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
