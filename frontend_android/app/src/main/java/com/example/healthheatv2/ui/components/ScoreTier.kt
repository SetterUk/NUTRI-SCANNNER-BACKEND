package com.example.healthheatv2.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.healthheatv2.ui.theme.LocalAppColors

/**
 * The one rating scale used everywhere in the app. Bands match the backend
 * (app/services/scoring.py VERDICT_BANDS) so the label, colour and suggested frequency
 * always agree with each other and with the server.
 */
enum class ScoreTier(val label: String, val frequency: String) {
    GREAT("Great choice", "Fine to eat daily"),
    GOOD("Good choice", "3–4 times a week"),
    OKAY("Okay sometimes", "Once or twice a week"),
    LIMIT("Limit", "Once or twice a month"),
    AVOID("Avoid", "Only on rare occasions");

    val isGoodPick: Boolean get() = this == GREAT || this == GOOD

    companion object {
        fun fromScore(score: Int): ScoreTier = when {
            score >= 80 -> GREAT
            score >= 65 -> GOOD
            score >= 50 -> OKAY
            score >= 35 -> LIMIT
            else -> AVOID
        }

        /**
         * Prefers the server's verdict, which already includes allergy/diet overrides.
         * Results cached before the five-band scale ("SMASH"/"PASS") fall back to the score.
         */
        fun of(verdict: String?, score: Int?): ScoreTier? {
            val v = verdict?.trim()?.uppercase()
            entries.firstOrNull { it.name == v }?.let { return it }
            return score?.let { fromScore(it) }
        }
    }
}

@Composable
fun ScoreTier.color(): Color {
    val c = LocalAppColors.current
    return when (this) {
        ScoreTier.GREAT -> c.accentGreen
        ScoreTier.GOOD -> Color(0xFF7CB342)
        ScoreTier.OKAY -> c.accentAmber
        ScoreTier.LIMIT -> Color(0xFFE67E22)
        ScoreTier.AVOID -> c.accentRed
    }
}
