package com.example.getyourmoneyupnotyourfunnyup.ui.screens.auth

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.GetYourMoneyUpNotYourFunnyUpTheme

/**
 * S2 Registration Screen: Creates a Firebase Auth account, inserts the user into
 * `/users/{uid}` in Firestore & local SQLite, and navigates to the Counter page.
 */
@Composable
fun RegistrationScreen(
    modifier: Modifier = Modifier,
    displayName: String,
    email: String,
    password: String,
    isLoading: Boolean,
    errorMessage: String?,
    onDisplayNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onRegisterClick: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Create Account",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Register to create your user profile in Firestore",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (!errorMessage.isNullOrBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        OutlinedTextField(
            value = displayName,
            onValueChange = onDisplayNameChange,
            label = { Text("Display Name") },
            singleLine = true,
            enabled = !isLoading,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = email,
            onValueChange = onEmailChange,
            label = { Text("Email") },
            singleLine = true,
            enabled = !isLoading,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text("Password (6+ characters)") },
            singleLine = true,
            enabled = !isLoading,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onRegisterClick,
            enabled = !isLoading && displayName.isNotBlank() && email.isNotBlank() && password.length >= 6,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text("Register")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(
            onClick = onNavigateToLogin,
            enabled = !isLoading
        ) {
            Text("Already have an account? Login")
        }
    }
}

@Preview(showBackground = true, name = "Registration Light")
@Preview(
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    name = "Registration Dark"
)
@Composable
private fun RegistrationScreenPreview() {
    GetYourMoneyUpNotYourFunnyUpTheme {
        Surface {
            RegistrationScreen(
                displayName = "Alex",
                email = "alex@example.com",
                password = "password123",
                isLoading = false,
                errorMessage = null,
                onDisplayNameChange = {},
                onEmailChange = {},
                onPasswordChange = {},
                onRegisterClick = {},
                onNavigateToLogin = {}
            )
        }
    }
}
