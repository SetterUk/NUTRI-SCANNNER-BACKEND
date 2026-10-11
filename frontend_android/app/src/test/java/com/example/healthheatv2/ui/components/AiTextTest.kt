package com.example.healthheatv2.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AiTextTest {
    // Shape of the plan in the old Nutribot screenshot
    private val plan = "⚡ [Gemma 4 E2B]\n**Breakfast**\n- 1 cup mixed millet and berries\n\n\n## One tip:\n* Eat small meals"

    @Test
    fun removesEngineTagAndMarkdownSymbols() {
        val plain = AiText.plain(plan)
        assertFalse(plain.contains("Gemma"))
        assertFalse(plain.contains("*"))
        assertFalse(plain.contains("#"))
        assertEquals("Breakfast\n• 1 cup mixed millet and berries\n\nOne tip:\n• Eat small meals", plain)
    }

    @Test
    fun keepsBoldAsStyleNotSymbols() {
        val annotated = AiText.annotated("Have **dal** with rice")
        assertEquals("Have dal with rice", annotated.text)
        assertEquals(1, annotated.spanStyles.size)
        assertEquals("dal", annotated.text.substring(annotated.spanStyles[0].start, annotated.spanStyles[0].end))
    }

    @Test
    fun otherEngineTags() {
        assertEquals("Hi", AiText.plain("☁️ [Cloud AI]\nHi"))
        assertEquals("Hi", AiText.plain("🔋 [Offline]\nHi"))
    }
}
