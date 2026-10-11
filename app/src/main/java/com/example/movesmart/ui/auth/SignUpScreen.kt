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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.movesmart.AccentInk
import com.example.movesmart.AppAccent
import com.example.movesmart.AppBackground
import com.example.movesmart.AppPrimary
import com.example.movesmart.AppSurface
import com.example.movesmart.ButtonShape
import com.example.movesmart.CardShape
import com.example.movesmart.TextInk
import com.example.movesmart.TextMuted

@Composable
fun SignUpScreen(onCreateAccount: (String, String) -> Unit, onLogin: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmVisible by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val submit = {
        submitted = true
        if (name.isNotBlank() && android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() && password.length >= 12 &&
            confirmPassword.isNotBlank() && password == confirmPassword
        ) {
            focusManager.clearFocus()
            onCreateAccount(name.trim(), email.trim())
        }
    }
    val confirmError = when {
        !submitted -> null
        confirmPassword.isBlank() -> "Please confirm your password"
        password != confirmPassword -> "Passwords must match"
        else -> null
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
            .imePadding()
            .verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Text("Join MoveSmart", color = AppPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Make room for a more active you.", color = TextMuted, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            singleLine = true,
            isError = submitted && name.isBlank(),
            supportingText = if (submitted && name.isBlank()) {
                { Text("Name is required") }
            } else null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            shape = CardShape,
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            isError = submitted && !android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches(),
            supportingText = if (submitted && !android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
                { Text("Enter an email like alex@example.com") }
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
            isError = submitted && password.length < 12,
            supportingText = { Text(if (password.length >= 12) "Passphrase length met" else "Use a memorable passphrase of at least 12 characters.") },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password")
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            shape = CardShape,
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = { Text("Confirm Password") },
            singleLine = true,
            isError = confirmError != null,
            supportingText = confirmError?.let { message -> { Text(message) } },
            visualTransformation = if (confirmVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { confirmVisible = !confirmVisible }) {
                    Icon(if (confirmVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (confirmVisible) "Hide confirmation password" else "Show confirmation password")
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            shape = CardShape,
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = submit,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = ButtonShape,
            colors = ButtonDefaults.buttonColors(containerColor = AppAccent, contentColor = AccentInk)
        ) {
            Text("Create Account", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        TextButton(
            onClick = { focusManager.clearFocus(); onLogin() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.textButtonColors(contentColor = AppPrimary)
        ) {
            Text("Already have an account? Log In")
        }
    }
}
