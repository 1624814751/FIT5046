package com.example.movesmart.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.movesmart.AppAccent
import com.example.movesmart.AppBackground
import com.example.movesmart.AppPrimary
import com.example.movesmart.AppSurface
import com.example.movesmart.ButtonShape
import com.example.movesmart.CardShape
import com.example.movesmart.TextInk
import com.example.movesmart.TextMuted

@Composable
fun LoginScreen(onLogin: () -> Unit, onSignUp: () -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val submit = {
        submitted = true
        if (email.isNotBlank() && password.isNotBlank()) {
            focusManager.clearFocus()
            onLogin()
        }
    }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = TextInk,
        unfocusedTextColor = TextInk,
        focusedContainerColor = AppSurface,
        unfocusedContainerColor = AppSurface,
        errorContainerColor = AppSurface,
        focusedBorderColor = AppPrimary,
        unfocusedBorderColor = TextMuted,
        focusedLabelColor = AppPrimary,
        unfocusedLabelColor = TextMuted,
        cursorColor = AppPrimary
    )

    Column(
        modifier = Modifier.fillMaxSize().background(AppBackground)
            .safeDrawingPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(40.dp))
        Text("MoveSmart", color = AppPrimary, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
        Text("Move smarter. Stay active.", color = TextMuted, fontSize = 16.sp)
        Spacer(Modifier.height(24.dp))
        Text("Log In", color = TextInk, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            isError = submitted && email.isBlank(),
            supportingText = if (submitted && email.isBlank()) {
                { Text("Email is required") }
            } else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            shape = CardShape,
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            isError = submitted && password.isBlank(),
            supportingText = if (submitted && password.isBlank()) {
                { Text("Password is required") }
            } else null,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password",
                        tint = TextMuted
                    )
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            shape = CardShape,
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = submit,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = ButtonShape,
            colors = ButtonDefaults.buttonColors(containerColor = AppAccent, contentColor = TextInk)
        ) {
            Text("Log In", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        TextButton(
            onClick = { focusManager.clearFocus(); onSignUp() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.textButtonColors(contentColor = AppPrimary)
        ) {
            Text("Don't have an account? Sign Up")
        }
    }
}
