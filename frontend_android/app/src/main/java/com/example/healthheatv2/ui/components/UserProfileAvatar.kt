package com.example.healthheatv2.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import coil.compose.AsyncImage
import com.example.healthheatv2.ui.viewmodel.AuthViewModel

// Theme constants
private val PrimaryGold = Color(0xFFFFD79B)
private val SurfaceContainerHighest = Color(0xFF353534)
private val SurfaceContainerLow = Color(0xFF1C1B1B)
private val OutlineVariant = Color(0xFF514532)
private val OnSurfaceVariant = Color(0xFFD6C4AC)
private val ErrorText = Color(0xFFFFB4AB)
private val ErrorBg = Color(0xFF93000A).copy(alpha = 0.25f)

@Composable
fun UserProfileAvatar(
    viewModel: AuthViewModel,
    onLogout: () -> Unit
) {
    val user = viewModel.getCurrentUser()
    var showPopup by remember { mutableStateOf(false) }

    Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
        // ---- Avatar circle ----
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(SurfaceContainerHighest, CircleShape)
                .border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFFFFD79B), Color(0xFFFFB300))
                    ),
                    shape = CircleShape
                )
                .clickable { showPopup = !showPopup },
            contentAlignment = Alignment.Center
        ) {
            if (user?.photoUrl != null) {
                AsyncImage(
                    model = user.photoUrl,
                    contentDescription = "Profile picture",
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = "Profile",
                    tint = PrimaryGold,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // ---- Popup card ----
        if (showPopup) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = androidx.compose.ui.unit.IntOffset(x = 0, y = 120),
                onDismissRequest = { showPopup = false },
                properties = PopupProperties(focusable = true)
            ) {
                AnimatedVisibility(
                    visible = showPopup,
                    enter = fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.92f),
                    exit = fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.92f)
                ) {
                    ProfilePopupCard(
                        displayName = user?.displayName,
                        email = user?.email,
                        photoUrl = user?.photoUrl?.toString(),
                        onLogout = {
                            showPopup = false
                            viewModel.signOut()
                            onLogout()
                        },
                        onDismiss = { showPopup = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfilePopupCard(
    displayName: String?,
    email: String?,
    photoUrl: String?,
    onLogout: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(220.dp)
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = Color.Black.copy(alpha = 0.6f),
                spotColor = Color.Black.copy(alpha = 0.6f)
            )
            .background(SurfaceContainerLow, RoundedCornerShape(20.dp))
            .border(1.dp, OutlineVariant.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Column {
            // User info header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(SurfaceContainerHighest, CircleShape)
                        .border(1.dp, OutlineVariant.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (photoUrl != null) {
                        AsyncImage(
                            model = photoUrl,
                            contentDescription = null,
                            modifier = Modifier.size(44.dp).clip(CircleShape)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            tint = PrimaryGold,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName ?: "User",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (email != null) {
                        Text(
                            text = email,
                            color = OnSurfaceVariant,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            HorizontalDivider(color = OutlineVariant.copy(alpha = 0.3f), thickness = 0.5.dp)

            Spacer(modifier = Modifier.height(12.dp))

            // Logout button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ErrorBg, RoundedCornerShape(12.dp))
                    .border(1.dp, ErrorText.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                    .clickable { onLogout() }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = "Logout",
                        tint = ErrorText,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Sign Out",
                        color = ErrorText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
