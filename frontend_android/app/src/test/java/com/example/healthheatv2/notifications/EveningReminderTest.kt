package com.example.healthheatv2.notifications

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EveningReminderTest {
    @Test
    fun remindsWhenNothingLogged() {
        assertTrue(EveningReminder.message(0, 0f, 60f)!!.contains("Nothing logged"))
    }

    @Test
    fun remindsWhenProteinFarBehind() {
        assertTrue(EveningReminder.message(3, 20f, 60f)!!.contains("33%"))
    }

    @Test
    fun staysQuietWhenOnTrack() {
        assertNull(EveningReminder.message(3, 45f, 60f))
    }
}
