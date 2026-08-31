package com.example.healthheatv2.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.google.accompanist.permissions.isGranted
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
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
    val context = androidx.compose.ui.platform.LocalContext.current
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var showClearDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // ── Local Model Detection on Tab Open ──────────────────────────────────────
    val modelManager = remember { com.example.healthheatv2.ai.LLMModelManager(context) }
    var detectedModelInfo by remember { mutableStateOf<com.example.healthheatv2.ai.LLMModelManager.ModelInfo?>(null) }
    var showModelDetectedDialog by remember { mutableStateOf(false) }
    val useLocalModel by nanoCoach.useLocalModel.collectAsState()

    LaunchedEffect(Unit) {
        val detected = modelManager.findSideloadedModel()
        detectedModelInfo = detected
        // If a local model is detected on disk, local mode is off, and user hasn't made a choice yet:
        if (detected != null && !nanoCoach.useLocalModel.value && !nanoCoach.hasPromptedLocalModel()) {
            showModelDetectedDialog = true
        }
    }

    Scaffold(
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
                    val statusLabel by nanoCoach.modelState.collectAsState()
                    val statusText = nanoCoach.getStatusLabel()
                    var showDiagnosticsDialog by remember { mutableStateOf(false) }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp)
                            .clickable { showDiagnosticsDialog = true }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Nutribot",
                                color = colors.textPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = "AI Diagnostics",
                                tint = colors.textHint,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        AnimatedVisibility(visible = statusText.isNotBlank()) {
                            Text(
                                text = "$statusText  ›",
                                color = if (statusText.contains("Gemma") || statusText.contains("Ready"))
                                    colors.accentGreen else colors.textHint,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (showDiagnosticsDialog) {
                        GemmaDiagnosticsDialog(
                            nanoCoach = nanoCoach,
                            userProfile = userProfile,
                            nutritionEngine = nutritionEngine,
                            onDismiss = { showDiagnosticsDialog = false }
                        )
                    }

                    // Clear Chat Button on the right end
                    IconButton(
                        onClick = { showClearDialog = true }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteSweep,
                            contentDescription = "Clear Chat",
                            tint = colors.textSecondary
                        )
                    }
                }

                if (showClearDialog) {
                    AlertDialog(
                        onDismissRequest = { showClearDialog = false },
                        title = { Text("Clear Chat History?", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
                        text = { Text("Are you sure you want to delete all messages with Nutribot?", color = colors.textSecondary) },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showClearDialog = false
                                    scope.launch {
                                        nutritionEngine.clearChatHistory()
                                        sharedChatMessages.clear()
                                        val welcomeMsg = "Hi! I'm your AI Nutritionist. Check your dashboard to see your daily streaks and missing nutrients, or ask me anything here!"
                                        sharedChatMessages.add(ChatMessage(welcomeMsg, false))
                                        nutritionEngine.saveChatMessage(welcomeMsg, false)
                                    }
                                }
                            ) {
                                Text("Clear", color = colors.accentRed, fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showClearDialog = false }) {
                                Text("Cancel", color = colors.textSecondary)
                            }
                        },
                        containerColor = colors.card,
                        shape = RoundedCornerShape(16.dp)
                    )
                }

                // Dialog prompting user if a local Gemma model file is detected on disk
                if (showModelDetectedDialog && detectedModelInfo != null) {
                    AlertDialog(
                        onDismissRequest = {
                            showModelDetectedDialog = false
                            nanoCoach.setHasPromptedLocalModel(true)
                        },
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "⚡ Local Gemma Model Detected",
                                    color = colors.textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            }
                        },
                        text = {
                            Column {
                                Text(
                                    text = "Found on-device model on disk:\n• ${detectedModelInfo?.name} (${detectedModelInfo?.sizeMb?.toInt()} MB)",
                                    color = colors.accentGreen,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "By default, Nutribot uses fast Cloud AI (0 MB RAM). Would you like to load Gemma into GPU/RAM memory for on-device inference, or keep using Cloud AI?",
                                    color = colors.textSecondary,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showModelDetectedDialog = false
                                    nanoCoach.setHasPromptedLocalModel(true)
                                    scope.launch {
                                        nanoCoach.loadLocalModel()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = colors.accentGreen),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("⚡ Load Model", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        },
                        dismissButton = {
                            OutlinedButton(
                                onClick = {
                                    showModelDetectedDialog = false
                                    nanoCoach.setHasPromptedLocalModel(true)
                                    nanoCoach.unloadLocalModel()
                                },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("☁️ Keep Cloud (Default)", color = colors.textSecondary, fontSize = 13.sp)
                            }
                        },
                        containerColor = colors.card,
                        shape = RoundedCornerShape(20.dp)
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
                ChatInputBar(nanoCoach, voiceCoach, userProfile, nutritionEngine) {
                    refreshTrigger++
                }
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
                                    nanoCoach = nanoCoach,
                                    onFixMyNutritionClick = onFixMyNutritionClick,
                                    refreshTrigger = refreshTrigger
                                )
                            }
                        }
                    }
                    1 -> {
                        // Chat Content
                        ChatContent(coach = nanoCoach, voiceCoach = voiceCoach, nutritionEngine = nutritionEngine)
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
private fun ChatContent(
    coach: NanoNutritionistCoach, 
    voiceCoach: com.example.healthheatv2.ai.VoiceNutritionistCoach,
    nutritionEngine: NutritionEngine
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val isImeVisible = WindowInsets.ime.getBottom(density) > 0
    val currentlySpeakingText by voiceCoach.speakingMessageText.collectAsState()

    DisposableEffect(Unit) {
        onDispose {
            voiceCoach.stopTTS()
        }
    }

    LaunchedEffect(Unit) {
        val history = nutritionEngine.getChatHistory()
        sharedChatMessages.clear()
        if (history.isEmpty()) {
            val welcomeMsg = "Hi! I'm your AI Nutritionist. Check your dashboard to see your daily streaks and missing nutrients, or ask me anything here!"
            sharedChatMessages.add(ChatMessage(welcomeMsg, false))
            nutritionEngine.saveChatMessage(welcomeMsg, false)
        } else {
            sharedChatMessages.addAll(history.map { ChatMessage(it.text, it.isUser) })
        }
        coach.initialize()
    }

    LaunchedEffect(sharedChatMessages.size) {
        if (sharedChatMessages.isNotEmpty()) {
            listState.animateScrollToItem(sharedChatMessages.size - 1)
        }
    }

    LaunchedEffect(isImeVisible) {
        if (isImeVisible && sharedChatMessages.isNotEmpty()) {
            listState.animateScrollToItem(sharedChatMessages.size - 1)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp, start = 16.dp, end = 16.dp, top = 8.dp)
    ) {
        items(sharedChatMessages) { msg ->
            val isSpeakingThis = currentlySpeakingText == msg.text
            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + slideInHorizontally(initialOffsetX = { if (msg.isUser) it else -it }),
            ) {
                ChatBubble(
                    message = msg,
                    isSpeaking = isSpeakingThis,
                    onToggleTTS = {
                        voiceCoach.toggleTTS(msg.text, msg.text)
                    }
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@OptIn(com.google.accompanist.permissions.ExperimentalPermissionsApi::class)
@Composable
private fun ChatInputBar(
    nanoCoach: NanoNutritionistCoach, 
    voiceCoach: com.example.healthheatv2.ai.VoiceNutritionistCoach, 
    userProfile: UserProfile, 
    nutritionEngine: NutritionEngine,
    onFoodLogged: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val colors = LocalAppColors.current

    val density = LocalDensity.current
    val isKeyboardOpen = WindowInsets.ime.getBottom(density) > 0

    val animatedBottomPadding by animateDpAsState(
        targetValue = if (isKeyboardOpen) 10.dp else 90.dp,
        label = "chatInputBottomPadding"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "sendGlow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    val isSendActive = !isLoading && (isListening || inputText.isNotBlank())
    val sendButtonColor = when {
        isLoading -> colors.surface
        isListening -> colors.accentGreen.copy(alpha = glowAlpha)
        inputText.isNotBlank() -> colors.accentGreen
        else -> colors.surface
    }

    Surface(
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = animatedBottomPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cancel / Delete Button appearing on the other end (Left) of the textbox while recording
            AnimatedVisibility(
                visible = isListening,
                enter = scaleIn() + fadeIn() + expandHorizontally(),
                exit = scaleOut() + fadeOut() + shrinkHorizontally()
            ) {
                Box(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(colors.accentRed.copy(alpha = 0.15f))
                        .border(1.dp, colors.accentRed.copy(alpha = 0.4f), CircleShape)
                        .clickable {
                            voiceCoach.cancelVoiceInput()
                            isListening = false
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Cancel Recording",
                        tint = colors.accentRed,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(32.dp),
                color = colors.card.copy(alpha = 0.8f),
                border = BorderStroke(1.dp, if (isListening) colors.accentRed.copy(alpha = 0.5f) else colors.border),
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
                        placeholder = { 
                            Text(
                                if (isListening) "Listening... (tap 🗑️ to cancel)" else "Ask Nutribot...", 
                                color = if (isListening) colors.accentRed else colors.textHint, 
                                fontSize = 15.sp 
                            ) 
                        },
                        enabled = !isLoading && !isListening,
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
                    
                    fun sendUserMessage(text: String) {
                        val clean = text.trim()
                        if (clean.isBlank() || isLoading) return
                        sharedChatMessages.add(ChatMessage(clean, true))
                        inputText = ""
                        isLoading = true
                        scope.launch {
                            nutritionEngine.saveChatMessage(clean, true)
                            try {
                                val response = nanoCoach.generateNutriBotResponse(sharedChatMessages.toList(), userProfile, nutritionEngine)
                                val logFoodRegex = "\\[LOG_FOOD:\\s*(.*?)\\]".toRegex()
                                val match = logFoodRegex.find(response)
                                var finalResponse = response
                                if (match != null) {
                                    val foodName = match.groupValues[1]
                                    finalResponse = finalResponse.replace(match.value, "").trim()
                                    val success = nutritionEngine.searchAndLogFood(foodName)
                                    if (success) {
                                        finalResponse += "\n\n*(Logged $foodName to your daily tracker!)*"
                                        onFoodLogged()
                                    } else {
                                        finalResponse += "\n\n*(Could not find $foodName in the database to log.)*"
                                    }
                                }
                                sharedChatMessages.add(ChatMessage(finalResponse, false))
                                nutritionEngine.saveChatMessage(finalResponse, false)
                            } catch (e: Exception) {
                                val errMsg = "Sorry, I encountered an error: ${e.message}"
                                sharedChatMessages.add(ChatMessage(errMsg, false))
                                nutritionEngine.saveChatMessage(errMsg, false)
                            } finally {
                                isLoading = false
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(sendButtonColor)
                            .then(
                                if (isListening) Modifier.border(1.5.dp, colors.accentGreen, CircleShape)
                                else Modifier
                            )
                            .clickable(enabled = isSendActive && !isLoading) {
                                if (isListening) {
                                    voiceCoach.finishVoiceInput()
                                } else {
                                    sendUserMessage(inputText)
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
                                tint = if (isSendActive) Color.White else colors.textHint,
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
                            .clickable(enabled = !isLoading) {
                                if (isListening) {
                                    voiceCoach.cancelVoiceInput()
                                    isListening = false
                                } else if (!micPermissionState.status.isGranted) {
                                    micPermissionState.launchPermissionRequest()
                                } else {
                                    isListening = true
                                    voiceCoach.startVoiceInput(
                                        onResult = { result ->
                                            isListening = false
                                            sendUserMessage(result)
                                        },
                                        onError = { _ ->
                                            isListening = false
                                        }
                                    )
                                }
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
fun ChatBubble(
    message: ChatMessage,
    isSpeaking: Boolean = false,
    onToggleTTS: () -> Unit = {}
) {
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
            Spacer(modifier = Modifier.width(8.dp))
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
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    val textParts = message.text.split("</think>")
                    val hasThink = textParts.size > 1 && message.text.contains("<think>")
                    val thinkText = if (hasThink) textParts[0].substringAfter("<think>").trim() else null
                    val rawMainText = if (hasThink) textParts[1].trim() else message.text
                    val mainText = rawMainText.replace(Regex("[*#_~`]"), "") // Strip all markdown symbols to ensure purely clean text
                    
                    if (thinkText != null && thinkText.isNotBlank()) {
                        var showThinking by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showThinking = !showThinking }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (showThinking) androidx.compose.material.icons.Icons.Filled.KeyboardArrowUp else androidx.compose.material.icons.Icons.Filled.KeyboardArrowDown,
                                contentDescription = "Toggle Thinking",
                                tint = colors.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "View AI Reasoning",
                                color = colors.textSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        
                        AnimatedVisibility(visible = showThinking) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = colors.background.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                            ) {
                                Text(
                                    text = thinkText,
                                    modifier = Modifier.padding(8.dp),
                                    color = colors.textSecondary,
                                    fontSize = 12.sp,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }

                    if (mainText.isNotBlank()) {
                        var displayedText by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
                        var animationCompleted by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
                        
                        LaunchedEffect(mainText) {
                            if (!message.isUser && !animationCompleted) {
                                displayedText = ""
                                // Typewriter effect
                                for (i in mainText.indices) {
                                    displayedText += mainText[i]
                                    kotlinx.coroutines.delay(10) // Typewriter speed
                                }
                                animationCompleted = true
                            } else {
                                displayedText = mainText
                            }
                        }

                        Text(
                            text = displayedText,
                            color = if (message.isUser) Color.White else colors.textPrimary,
                            fontSize = 15.sp,
                            lineHeight = 22.sp
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = timeString,
                color = colors.textHint,
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        // Speaker / Stop button on the right of bot messages
        if (!message.isUser) {
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(if (isSpeaking) colors.accentGreen.copy(alpha = 0.2f) else colors.surface)
                    .border(1.dp, if (isSpeaking) colors.accentGreen else colors.border, CircleShape)
                    .clickable { onToggleTTS() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isSpeaking) Icons.Filled.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = if (isSpeaking) "Stop Reading" else "Read Aloud",
                    tint = if (isSpeaking) colors.accentGreen else colors.textSecondary,
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        if (message.isUser) {
            val userPhotoUrl = remember {
                try {
                    FirebaseAuth.getInstance().currentUser?.photoUrl
                } catch (e: Exception) {
                    null
                }
            }

            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(colors.surface)
                    .border(1.dp, colors.border, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (userPhotoUrl != null) {
                    AsyncImage(
                        model = userPhotoUrl,
                        contentDescription = "User Profile",
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Icon(
                        Icons.Filled.Person, 
                        contentDescription = "User", 
                        tint = colors.textSecondary, 
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun GemmaDiagnosticsDialog(
    nanoCoach: NanoNutritionistCoach,
    userProfile: UserProfile,
    nutritionEngine: NutritionEngine,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()

    val modelState by nanoCoach.modelState.collectAsState()
    val useLocalModel by nanoCoach.useLocalModel.collectAsState()
    val modelManager = remember { com.example.healthheatv2.ai.LLMModelManager(context) }
    var sideloadedInfo by remember { mutableStateOf<com.example.healthheatv2.ai.LLMModelManager.ModelInfo?>(null) }
    var isCheckingSideload by remember { mutableStateOf(true) }

    var testBenchmarkResult by remember { mutableStateOf<String?>(null) }
    var testBenchmarkDurationMs by remember { mutableStateOf<Long?>(null) }
    var isTestingBenchmark by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isCheckingSideload = true
        sideloadedInfo = modelManager.findSideloadedModel()
        isCheckingSideload = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🧠 AI Engine & Model Settings", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // 1. Current State
                Text("CURRENT INFERENCE STATE", color = colors.textHint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val dotColor = if (useLocalModel && modelState is com.example.healthheatv2.ai.GemmaInferenceManager.ModelState.Ready) {
                                colors.accentGreen
                            } else if (useLocalModel && modelState is com.example.healthheatv2.ai.GemmaInferenceManager.ModelState.Initializing) {
                                colors.accentBlue
                            } else {
                                colors.textSecondary
                            }
                            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(dotColor))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(nanoCoach.getStatusLabel(), color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (useLocalModel) "On-device Gemma inference active (offline & private)."
                                   else "Cloud AI active (default, fast, 0 MB device memory).",
                            color = colors.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 2. Sideloaded File Status & Memory Control
                Text("ON-DEVICE MODEL MEMORY CONTROLS", color = colors.textHint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        if (isCheckingSideload) {
                            Text("Scanning for model files...", color = colors.textSecondary, fontSize = 13.sp)
                        } else if (sideloadedInfo != null) {
                            Text("✅ Local Model Detected on Disk:", color = colors.accentGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("${sideloadedInfo?.name} (${sideloadedInfo?.sizeMb?.toInt()} MB)", color = colors.textPrimary, fontSize = 13.sp)
                            Text("${sideloadedInfo?.file?.absolutePath}", color = colors.textHint, fontSize = 11.sp)

                            Spacer(modifier = Modifier.height(10.dp))

                            if (useLocalModel) {
                                Button(
                                    onClick = {
                                        nanoCoach.unloadLocalModel()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentRed.copy(alpha = 0.85f)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth().height(38.dp)
                                ) {
                                    Text("🛑 Unload Model (Free GPU/RAM Memory)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            nanoCoach.loadLocalModel()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentGreen),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth().height(38.dp)
                                ) {
                                    Text("⚡ Load Model into GPU Memory", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            Text("❌ No sideloaded model found on disk.", color = colors.accentAmber, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Cloud AI is active. Sideload a Gemma model via ADB to test on-device execution.", color = colors.textSecondary, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 3. ADB Command Helper
                Text("DEVELOPER ADB PUSH COMMAND", color = colors.textHint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, colors.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        val adbCmd = modelManager.getAdbSideloadCommand()
                        Text(
                            text = adbCmd,
                            color = Color(0xFF64FFDA),
                            fontSize = 11.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("ADB Command", adbCmd)
                                clipboard.setPrimaryClip(clip)
                                android.widget.Toast.makeText(context, "Copied ADB command to clipboard!", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth().height(36.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("📋 Copy Command", fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 4. Live Benchmark
                Text("TEST INFERENCE BENCHMARK", color = colors.textHint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = {
                        scope.launch {
                            isTestingBenchmark = true
                            testBenchmarkResult = null
                            val start = System.currentTimeMillis()
                            val testHistory = listOf(ChatMessage("What is the protein content of 100g of moong dal?", true))
                            val result = nanoCoach.generateNutriBotResponse(testHistory, userProfile, nutritionEngine)
                            val end = System.currentTimeMillis()
                            testBenchmarkDurationMs = end - start
                            testBenchmarkResult = result
                            isTestingBenchmark = false
                        }
                    },
                    enabled = !isTestingBenchmark,
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentGreen)
                ) {
                    if (isTestingBenchmark) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Running Inference...", color = Color.White, fontSize = 13.sp)
                    } else {
                        Text("⚡ Run Test Inference", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                if (testBenchmarkResult != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = colors.card,
                        border = BorderStroke(1.dp, colors.accentGreen.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("⏱️ Latency: ${testBenchmarkDurationMs}ms", color = colors.accentGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(testBenchmarkResult ?: "", color = colors.textPrimary, fontSize = 12.sp, lineHeight = 17.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = colors.accentGreen, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = colors.card,
        shape = RoundedCornerShape(20.dp)
    )
}
