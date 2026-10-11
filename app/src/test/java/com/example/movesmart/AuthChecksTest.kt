package com.example.movesmart

import com.example.movesmart.ui.auth.checkLogin
import com.example.movesmart.ui.auth.checkSignup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthChecksTest {
    @Test
    fun loginRejectsEmptyEmail() {
        assertEquals("Email is required.", checkLogin("", "password"))
    }

    @Test
    fun loginRejectsInvalidEmail() {
        assertEquals("Enter a valid email address.", checkLogin("alex", "password"))
    }

    @Test
    fun loginRejectsEmptyPassword() {
        assertEquals("Password is required.", checkLogin("alex@example.com", ""))
    }

    @Test
    fun signupRejectsPasswordMismatch() {
        val error = checkSignup("Alex", "alex@example.com", "long-password", "different-pass")
        assertEquals("Passwords must match.", error)
    }

    @Test
    fun validSignupPasses() {
        assertNull(checkSignup("Alex", "alex@example.com", "long-password", "long-password"))
    }
}
