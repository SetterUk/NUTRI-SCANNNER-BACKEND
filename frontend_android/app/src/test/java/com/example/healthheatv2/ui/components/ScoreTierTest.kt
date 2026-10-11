package com.example.healthheatv2.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScoreTierTest {
    @Test
    fun bandsMatchBackend() {
        assertEquals(ScoreTier.GREAT, ScoreTier.fromScore(80))
        assertEquals(ScoreTier.GOOD, ScoreTier.fromScore(79))
        assertEquals(ScoreTier.GOOD, ScoreTier.fromScore(65))
        assertEquals(ScoreTier.OKAY, ScoreTier.fromScore(57))
        assertEquals(ScoreTier.LIMIT, ScoreTier.fromScore(35))
        assertEquals(ScoreTier.AVOID, ScoreTier.fromScore(34))
    }

    @Test
    fun serverVerdictWinsOverScore() {
        // An allergy override makes a high-scoring product AVOID for this user
        assertEquals(ScoreTier.AVOID, ScoreTier.of("AVOID", 90))
        assertEquals(ScoreTier.OKAY, ScoreTier.of("okay", 10))
    }

    @Test
    fun legacyVerdictsFallBackToScore() {
        assertEquals(ScoreTier.OKAY, ScoreTier.of("PASS", 57))
        assertEquals(ScoreTier.GOOD, ScoreTier.of("SMASH", 70))
        assertNull(ScoreTier.of(null, null))
    }
}
