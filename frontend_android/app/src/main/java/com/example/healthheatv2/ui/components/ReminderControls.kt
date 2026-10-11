package com.example.healthheatv2.ui.components

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.healthheatv2.notifications.EveningReminder
import com.example.healthheatv2.ui.theme.LocalAppColors

/** Returns a function that turns the reminder on, asking for notification permission if needed. */
@Composable
fun rememberReminderEnabler(onResult: (Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        EveningReminder.setEnabled(context, granted)
        onResult(granted)
    }
    return {
        if (EveningReminder.canPostNotifications(context)) {
            EveningReminder.setEnabled(context, true)
            onResult(true)
        } else {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
fun ReminderSettingRow(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    var enabled by remember { mutableStateOf(EveningReminder.isEnabled(context)) }
    val enable = rememberReminderEnabler { enabled = it }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.card)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("Evening reminder", color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Around 8:30 pm, only if nothing is logged or protein is low",
                color = colors.textSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = { on ->
                if (on) enable() else {
                    EveningReminder.setEnabled(context, false)
                    enabled = false
                }
            },
            colors = SwitchDefaults.colors(checkedTrackColor = colors.accentGreen)
        )
    }
}

/** Shown once on Home after the first logged meal. */
@Composable
fun ReminderPromptCard(onDone: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    val enable = rememberReminderEnabler { onDone() }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.accentBlue.copy(alpha = 0.08f))
            .border(1.dp, colors.accentBlue.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = colors.accentBlue, modifier = Modifier.size(18.dp))
            Text("Want a nudge in the evening?", color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            "People who log every day are the ones who change how they eat. We'll remind you around 8:30 pm, " +
                "only if you haven't logged or protein is low. You can turn it off in Profile.",
            color = colors.textSecondary,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = enable,
                colors = ButtonDefaults.buttonColors(containerColor = colors.accentBlue),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Turn on", color = Color.White, fontWeight = FontWeight.SemiBold) }
            TextButton(onClick = {
                EveningReminder.setEnabled(context, false)
                onDone()
            }) { Text("Not now", color = colors.textSecondary) }
        }
    }
}
