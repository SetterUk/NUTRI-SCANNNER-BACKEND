package com.example.healthheatv2.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.google.accompanist.permissions.isGranted
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.healthheatv2.ai.NanoNutritionistCoach
import com.example.healthheatv2.data.UserProfile
import com.example.healthheatv2.services.NutritionEngine
import com.example.healthheatv2.services.NutritionGap
import com.example.healthheatv2.ui.theme.LocalAppColors
import kotlinx.coroutines.launch

data class ChatMessage(val text: String, val isUser: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutritionistChatScreen(
    nanoCoach: NanoNutritionistCoach,
    voiceCoach: com.example.healthheatv2.ai.VoiceNutritionistCoach,
    userProfile: UserProfile,
    nutritionEngine: NutritionEngine,
    onFixMyNutritionClick: (NutritionGap) -> Unit,
    onBackClick: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) } // 0 = Dashboard, 1 = Chat
    val colors = LocalAppColors.current

    Scaffold(
        containerColor = colors.background,
        contentWindowInsets = WindowInsets.ime, // Let insets handle keyboard
        topBar = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
                    }
                    Text(
                        text = "Nutribot",
                        color = colors.textPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                // Custom Segmented Control Tab Switcher
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(colors.card)
                        .border(1.dp, colors.border, RoundedCornerShape(24.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TabSegment(
                        text = "Dashboard",
                        isSelected = selectedTab == 0,
                        modifier = Modifier.weight(1f),
                        onClick = { selectedTab = 0 }
                    )
                    TabSegment(
                        text = "AI Coach",
                        isSelected = selectedTab == 1,
                        modifier = Modifier.weight(1f),
                        onClick = { selectedTab = 1 }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        },
        // We use bottomBar for the Chat input so it floats above keyboard
        bottomBar = {
            AnimatedVisibility(
                visible = selectedTab == 1,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                ChatInputBar(nanoCoach, voiceCoach, userProfile, nutritionEngine)
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // We use crossfade for a smooth transition between Dashboard and Chat
            Crossfade(
                targetState = selectedTab,
                animationSpec = tween(durationMillis = 300),
                label = "TabSwitcher"
            ) { tab ->
                when (tab) {
                    0 -> {
                        // Dashboard Content
                        // Added bottom padding to clear the bottom navigation bar gracefully
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 120.dp, top = 8.dp)
                        ) {
                            item {
                                DashboardSc(
                                    userProfile = userProfile,
                                    nutritionEngine = nutritionEngine,
                                    onFixMyNutritionClick = onFixMyNutritionClick
                                )
                            }
                        }
                    }
                    1 -> {
                        // Chat Content
                        ChatContent(coach = nanoCoach)
                    }
                }
            }
        }
    }
}

@Composable
private fun TabSegment(text: String, isSelected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val backgroundColor by animateColorAsState(if (isSelected) colors.accentGreen else Color.Transparent)
    val textColor by animateColorAsState(if (isSelected) Color.White else colors.textSecondary)

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(24.dp))
            .background(backgroundColor)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )
    }
}

// Global state for chat messages so they persist when switching tabs
private val sharedChatMessages = mutableStateListOf<ChatMessage>()
private var hasInitializedChat = false

@Composable
private fun ChatContent(coach: NanoNutritionistCoach) {
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        if (!hasInitializedChat) {
            coach.initialize()
            sharedChatMessages.add(ChatMessage("Hi! I'm your AI Nutritionist. Check your dashboard to see your daily streaks and missing nutrients, or ask me anything here!", false))
            hasInitializedChat = true
        }
    }

    LaunchedEffect(sharedChatMessages.size) {
        if (sharedChatMessages.isNotEmpty()) {
            listState.animateScrollToItem(sharedChatMessages.size - 1)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp, start = 16.dp, end = 16.dp, top = 8.dp)
    ) {
        items(sharedChatMessages) { msg ->
            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + slideInHorizontally(initialOffsetX = { if (msg.isUser) it else -it }),
            ) {
                ChatBubble(msg)
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@OptIn(com.google.accompanist.permissions.ExperimentalPermissionsApi::class)
@Composable
private fun ChatInputBar(nanoCoach: NanoNutritionistCoach, voiceCoach: com.example.healthheatv2.ai.VoiceNutritionistCoach, userProfile: UserProfile, nutritionEngine: NutritionEngine) {
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val colors = LocalAppColors.current

    Surface(
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .navigationBarsPadding()
                .padding(bottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding().let { if (it > 0.dp) 0.dp else 16.dp }),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(32.dp),
                color = colors.card.copy(alpha = 0.8f),
                border = BorderStroke(1.dp, colors.border),
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    TextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Ask Nutribot...", color = colors.textHint, fontSize = 15.sp) },
                        enabled = !isLoading,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                            cursorColor = colors.accentGreen
                        )
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (isLoading || inputText.isBlank()) colors.surface else colors.accentGreen)
                            .clickable(enabled = !isLoading && inputText.isNotBlank()) {
                                val userMsg = inputText
                                sharedChatMessages.add(ChatMessage(userMsg, true))
                                inputText = ""
                                isLoading = true
                                scope.launch {
                                    try {
                                        val response = nanoCoach.generateNutriBotResponse(sharedChatMessages.toList(), userProfile)
                                        val logFoodRegex = "\\[LOG_FOOD:\\s*(.*?)\\]".toRegex()
                                        val match = logFoodRegex.find(response)
                                        var finalResponse = response
                                        if (match != null) {
                                            val foodName = match.groupValues[1]
                                            finalResponse = finalResponse.replace(match.value, "").trim()
                                            val success = nutritionEngine.searchAndLogFood(foodName)
                                            if (success) {
                                                finalResponse += "\n\n*(Logged $foodName to your daily tracker!)*"
                                            } else {
                                                finalResponse += "\n\n*(Could not find $foodName in the database to log.)*"
                                            }
                                        }
                                        sharedChatMessages.add(ChatMessage(finalResponse, false))
                                    } catch (e: Exception) {
                                        sharedChatMessages.add(ChatMessage("Error generating response.", false))
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = colors.textSecondary, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                Icons.AutoMirrored.Filled.Send, 
                                contentDescription = "Send", 
                                tint = if (inputText.isBlank()) colors.textHint else Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // Microphone Button
                    val micPermissionState = com.google.accompanist.permissions.rememberPermissionState(android.Manifest.permission.RECORD_AUDIO)
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (isListening) Color.Red else colors.surface)
                            .clickable(enabled = !isLoading && !isListening) {
                                if (!micPermissionState.status.isGranted) {
                                    micPermissionState.launchPermissionRequest()
                                } else {
                                    isListening = true
                                    voiceCoach.startVoiceInput(
                                        onResult = { result ->
                                        isListening = false
                                        val userMsg = result
                                        sharedChatMessages.add(ChatMessage(userMsg, true))
                                        isLoading = true
                                        scope.launch {
                                            try {
                                                val response = voiceCoach.chatWithVoice(sharedChatMessages.toList(), userProfile)
                                                val logFoodRegex = "\\[LOG_FOOD:\\s*(.*?)\\]".toRegex()
                                                val match = logFoodRegex.find(response)
                                                var finalResponse = response
                                                if (match != null) {
                                                    val foodName = match.groupValues[1]
                                                    finalResponse = finalResponse.replace(match.value, "").trim()
                                                    val success = nutritionEngine.searchAndLogFood(foodName)
                                                    if (success) {
                                                        finalResponse += "\n\n*(Logged $foodName to your daily tracker!)*"
                                                    } else {
                                                        finalResponse += "\n\n*(Could not find $foodName in the database to log.)*"
                                                    }
                                                }
                                                sharedChatMessages.add(ChatMessage(finalResponse, false))
                                            } catch (e: Exception) {
                                                sharedChatMessages.add(ChatMessage("Error generating response.", false))
                                            } finally {
                                                isLoading = false
                                            }
                                        }
                                    },
                                    onError = { errorMsg ->
                                        isListening = false
                                        sharedChatMessages.add(ChatMessage("Voice Error: $errorMsg", false))
                                    }
                                )
                                } // closes else block
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Mic, 
                            contentDescription = "Mic", 
                            tint = if (isListening) Color.White else colors.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val colors = LocalAppColors.current
    val timeFormatter = remember { java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()) }
    val timeString = remember { timeFormatter.format(java.util.Date()) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!message.isUser) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(colors.accentGreenSubtle),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.SmartToy, contentDescription = "Bot", tint = colors.accentGreen, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
        }

        Column(
            horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 20.dp,
                    topEnd = 20.dp,
                    bottomStart = if (message.isUser) 20.dp else 4.dp,
                    bottomEnd = if (message.isUser) 4.dp else 20.dp
                ),
                color = if (message.isUser) colors.accentGreen else colors.card,
                border = if (!message.isUser) BorderStroke(1.dp, colors.border) else null,
                modifier = Modifier.widthIn(max = 270.dp)
            ) {
                Text(
                    text = message.text,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    color = if (message.isUser) Color.White else colors.textPrimary,
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = timeString,
                color = colors.textHint,
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        if (message.isUser) {
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(colors.surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Person, contentDescription = "User", tint = colors.textSecondary, modifier = Modifier.size(20.dp))
            }
        }
    }
}
