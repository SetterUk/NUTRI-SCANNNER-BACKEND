package com.example.healthheatv2.ui.screens

import android.util.Log
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.healthheatv2.ui.theme.LocalAppColors
import com.example.healthheatv2.ui.viewmodel.AuthState
import com.example.healthheatv2.ui.viewmodel.AuthViewModel
import com.example.healthheatv2.ui.viewmodel.ThemeViewModel
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    themeViewModel: ThemeViewModel,
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val authState by viewModel.authState.collectAsStateWithLifecycle()

    LaunchedEffect(authState) {
        if (authState is AuthState.Success) onAuthSuccess()
    }

    AuthScreenContent(
        authState = authState,
        onGoogleSignInClick = {
            coroutineScope.launch {
                try {
                    viewModel.setLoading()
                    val webClientId = "637255913649-a4i4r1bqd74319livs80vos9ico5mcm3.apps.googleusercontent.com"
                    val credentialManager = CredentialManager.create(context)
                    
                    // For explicit button clicks, GetSignInWithGoogleOption is required
                    val signInWithGoogleOption = com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
                        .Builder(webClientId)
                        .build()
                        
                    val request = GetCredentialRequest.Builder()
                        .addCredentialOption(signInWithGoogleOption)
                        .build()
                        
                    val result = credentialManager.getCredential(context, request)
                    val credential = result.credential
                    if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                        viewModel.signInWithGoogleToken(googleIdTokenCredential.idToken)
                    } else {
                        viewModel.setError("Unexpected credential type")
                    }
                } catch (e: Exception) {
                    Log.e("Auth", "Sign In Failed", e)
                    viewModel.setError("Error: ${e.message}")
                }
            }
        }
    )
}

@Composable
fun AuthScreenContent(
    authState: AuthState,
    onGoogleSignInClick: () -> Unit
) {
    val colors = LocalAppColors.current

    // Pulse animation for the logo glow
    val infiniteTransition = rememberInfiniteTransition(label = "logoPulse")
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 0.9f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // Background glow
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-80).dp)
                .size(240.dp)
                .scale(glowScale)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(colors.accentGreen.copy(alpha = 0.12f), Color.Transparent)
                    ),
                    shape = CircleShape
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(colors.accentGreen),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Eco,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(42.dp)
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = "HealthHeat",
                color = colors.textPrimary,
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-1).sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Know what you eat.",
                color = colors.textSecondary,
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(60.dp))

            // Error display
            if (authState is AuthState.Error) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.accentRedSubtle)
                        .border(1.dp, colors.accentRed.copy(0.3f), RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = authState.message,
                        color = colors.accentRed,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.height(20.dp))
            }

            // Google Sign-In Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        if (authState is AuthState.Loading) colors.card
                        else colors.textPrimary
                    )
                    .clickable(enabled = authState !is AuthState.Loading) { onGoogleSignInClick() },
                contentAlignment = Alignment.Center
            ) {
                if (authState is AuthState.Loading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = colors.accentGreen,
                            strokeWidth = 2.dp
                        )
                        Text("Signing in…", color = colors.textPrimary, fontSize = 15.sp)
                    }
                } else {
                    Text(
                        text = "Continue with Google",
                        color = colors.background,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = "By continuing, you agree to our Terms of Service",
                color = colors.textHint,
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
