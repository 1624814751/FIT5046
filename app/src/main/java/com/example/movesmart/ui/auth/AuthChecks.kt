package com.example.movesmart.ui.auth

private val emailPattern = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

fun checkLogin(email: String, password: String): String? {
    if (email.isBlank()) {
        return "Email is required."
    }
    if (!emailPattern.matches(email.trim())) {
        return "Enter a valid email address."
    }
    if (password.isBlank()) {
        return "Password is required."
    }
    return null
}

fun checkSignup(name: String, email: String, password: String, confirmPassword: String): String? {
    if (name.isBlank()) {
        return "Name is required."
    }
    val loginError = checkLogin(email, password)
    if (loginError != null) {
        return loginError
    }
    if (password.length < 12) {
        return "Use a password with at least 12 characters."
    }
    if (confirmPassword.isBlank()) {
        return "Please confirm your password."
    }
    if (password != confirmPassword) {
        return "Passwords must match."
    }
    return null
}
