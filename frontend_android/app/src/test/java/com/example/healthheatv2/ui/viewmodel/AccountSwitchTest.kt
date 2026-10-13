package com.example.healthheatv2.ui.viewmodel

import com.example.healthheatv2.data.UserDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountSwitchTest {
    private fun jwt(payloadJson: String): String {
        val enc = java.util.Base64.getUrlEncoder().withoutPadding()
        fun part(s: String) = enc.encodeToString(s.toByteArray())
        return part("""{"alg":"HS256","typ":"JWT"}""") + "." + part(payloadJson) + ".c2lnbmF0dXJl"
    }

    @Test
    fun readsUserIdFromBackendToken() {
        assertEquals("5", backendUserId(jwt("""{"sub":"5","exp":1793000000}""")))
        // Payload lengths that would need base64 padding
        assertEquals("123", backendUserId(jwt("""{"sub":"123"}""")))
        assertEquals("42", backendUserId(jwt("""{"sub":42,"exp":1}""")))
    }

    @Test
    fun badTokensGiveNoUserId() {
        assertNull(backendUserId(null))
        assertNull(backendUserId(""))
        assertNull(backendUserId("not-a-jwt"))
        assertNull(backendUserId("a.%%%.c"))
        assertNull(backendUserId(jwt("""{"exp":1}""")))
        assertNull(backendUserId(jwt("""{"sub":null}""")))
        assertNull(backendUserId(jwt("[1,2]")))
    }

    @Test
    fun sameAccountKeepsEverything() {
        assertEquals(LocalDataWipe.NONE, wipeOnSignIn("5", "5"))
    }

    @Test
    fun differentAccountWipesEverything() {
        assertEquals(LocalDataWipe.ALL, wipeOnSignIn("5", "7"))
        assertEquals(LocalDataWipe.ALL, wipeOnSignIn("5", null))
    }

    @Test
    fun unknownOwnerClearsScansButKeepsTheDiary() {
        // The case that leaked scans: everyone was signed out by the server's new login key, and this
        // phone hadn't recorded whose data it held, so the next sign-in kept the previous person's scans.
        assertEquals(LocalDataWipe.ALL_BUT_DIARY, wipeOnSignIn(null, "5"))
        val cleared = UserDatabase.PERSONAL_TABLES - UserDatabase.DIARY_TABLES
        assertTrue("scanned_products" in cleared)
        assertTrue("SavedChatMessage" in cleared)
        assertFalse("meal_logs" in cleared)
        assertTrue(UserDatabase.PERSONAL_TABLES.containsAll(UserDatabase.DIARY_TABLES))
    }
}
