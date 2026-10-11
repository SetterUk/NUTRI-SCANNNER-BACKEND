package com.example.healthheatv2.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.healthheatv2.MainActivity
import com.example.healthheatv2.R
import com.example.healthheatv2.data.UserDatabase
import com.example.healthheatv2.network.UserProfileResponse
import com.example.healthheatv2.services.WeeklySummary
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * One gentle nudge around 8:30 pm, only when it can still change today: nothing logged yet, or
 * protein well short. Opt-in; nothing is sent on days that are on track.
 */
object EveningReminder {
    private const val PREFS = "reminders"
    private const val KEY_ENABLED = "evening_enabled"
    private const val KEY_ASKED = "evening_prompt_answered"
    private const val WORK_NAME = "evening_reminder"
    private const val CHANNEL_ID = "daily_reminder"
    private const val HOUR = 20
    private const val MINUTE = 30

    fun isEnabled(context: Context) = prefs(context).getBoolean(KEY_ENABLED, false)

    /** True once the user has said yes or no to the Home prompt, so it is only shown once. */
    fun promptAnswered(context: Context) = prefs(context).getBoolean(KEY_ASKED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).putBoolean(KEY_ASKED, true).apply()
        val wm = WorkManager.getInstance(context)
        if (enabled) {
            val request = PeriodicWorkRequestBuilder<EveningReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(millisUntilNext(HOUR, MINUTE), TimeUnit.MILLISECONDS)
                .build()
            wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        } else {
            wm.cancelUniqueWork(WORK_NAME)
        }
    }

    fun canPostNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** The text to send tonight, or null when no reminder is useful. */
    fun message(itemsToday: Int, protein: Float, proteinTarget: Float): String? = when {
        itemsToday == 0 -> "Nothing logged today yet. Type what you ate, it takes ten seconds."
        proteinTarget > 0 && protein < proteinTarget * 0.6f ->
            "You're at ${(protein / proteinTarget * 100).roundToInt()}% of today's protein. " +
                "Dal, paneer, curd, chana or eggs at dinner would close the gap."
        else -> null
    }

    internal fun post(context: Context, text: String) {
        if (!canPostNotifications(context)) return
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Daily reminder", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "An evening reminder to log meals, only when something is missing"
                }
            )
        }
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("HealthHeat")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(1001, notification)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the post
        }
    }

    private fun millisUntilNext(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (!after(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        return next.timeInMillis - now.timeInMillis
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

class EveningReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (!EveningReminder.isEnabled(ctx)) return Result.success()

        val start = WeeklySummary.startOfDay(System.currentTimeMillis())
        val meals = UserDatabase.getDatabase(ctx).mealLogDao().getTodayMeals(start, start + 86_400_000L - 1)
        val protein = meals.sumOf { it.protein.toDouble() }.toFloat()

        // Same targets the Home screen shows, from the profile cached at sign-in
        val cached = ctx.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE).getString("cached_profile_json", null)
        val profile = try {
            cached?.let { com.google.gson.Gson().fromJson(it, UserProfileResponse::class.java) }
        } catch (e: Exception) {
            null
        }
        val proteinTarget = com.example.healthheatv2.mapNetworkProfileToLocal(profile).dailyProtein ?: 60f

        EveningReminder.message(meals.size, protein, proteinTarget)?.let { EveningReminder.post(ctx, it) }
        return Result.success()
    }
}
