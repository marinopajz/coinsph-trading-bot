package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.CustomCredential
import androidx.credentials.exceptions.GetCredentialException
import com.example.ui.dashboard.BotDashboardViewModel
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.theme.TradingBotTheme
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainActivity : ComponentActivity() {

    private val viewModel: BotDashboardViewModel by viewModels {
        BotDashboardViewModel.provideFactory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            TradingBotTheme {
                FirebaseAuthGate(
                    viewModel = viewModel,
                    auth = FirebaseAuth.getInstance()
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onAppForegrounded()
    }

    override fun onStop() {
        super.onStop()
        viewModel.onAppBackgrounded()
    }
}

@androidx.compose.runtime.Composable
private fun FirebaseAuthGate(
    viewModel: BotDashboardViewModel,
    auth: FirebaseAuth
) {
    var currentUser by remember(auth) {
        mutableStateOf(auth.currentUser)
    }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            currentUser = firebaseAuth.currentUser
        }

        auth.addAuthStateListener(listener)

        onDispose {
            auth.removeAuthStateListener(listener)
        }
    }

    if (currentUser == null) {
        AuthScreen(auth = auth)
    } else {
        DashboardScreen(
            viewModel = viewModel,
            userEmail = currentUser?.email,
            onSignOut = { auth.signOut() },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@androidx.compose.runtime.Composable
private fun AuthScreen(auth: FirebaseAuth) {
    val context = LocalContext.current
    val credentialManager = remember(context) {
        CredentialManager.create(context)
    }
    val scope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isRegisterMode by remember { mutableStateOf(false) }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "COINS.PH TRADING BOT",
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Sign in to monitor Paper Trading.",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email Address") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                enabled = !isLoading
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                enabled = !isLoading
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
            }

            errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            Button(
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val trimmedEmail = email.trim()
                    if (trimmedEmail.isEmpty() || password.isEmpty()) {
                        errorMessage = "Please enter both email and password."
                        return@Button
                    }
                    scope.launch {
                        isLoading = true
                        errorMessage = null
                        try {
                            if (isRegisterMode) {
                                auth.createUserWithEmailAndPassword(trimmedEmail, password).await()
                            } else {
                                auth.signInWithEmailAndPassword(trimmedEmail, password).await()
                            }
                            // The AuthStateListener in FirebaseAuthGate will automatically detect the sign-in
                        } catch (e: Exception) {
                            errorMessage = e.message ?: "Authentication failed."
                        } finally {
                            isLoading = false
                        }
                    }
                }
            ) {
                Text(if (isRegisterMode) "Register New Account" else "Sign In with Email")
            }

            TextButton(
                enabled = !isLoading,
                onClick = {
                    isRegisterMode = !isRegisterMode
                    errorMessage = null
                }
            ) {
                Text(if (isRegisterMode) "Already have an account? Sign In" else "Create a Test Account")
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(text = "— OR —", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(24.dp))

            // Keep Google Sign-In as alternative
            Button(
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    scope.launch {
                        isLoading = true
                        errorMessage = null

                        try {
                            val webClientId = context.getString(
                                R.string.default_web_client_id
                            )

                            val googleIdOption = GetGoogleIdOption.Builder()
                                .setFilterByAuthorizedAccounts(false)
                                .setServerClientId(webClientId)
                                .setAutoSelectEnabled(false)
                                .build()

                            val request = GetCredentialRequest.Builder()
                                .addCredentialOption(googleIdOption)
                                .build()

                            val result = credentialManager.getCredential(
                                context = context,
                                request = request
                            )

                            val credential = result.credential

                            if (
                                credential is CustomCredential &&
                                credential.type ==
                                GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                            ) {
                                val googleCredential =
                                    GoogleIdTokenCredential.createFrom(
                                        credential.data
                                    )

                                val firebaseCredential =
                                    GoogleAuthProvider.getCredential(
                                        googleCredential.idToken,
                                        null
                                    )

                                auth.signInWithCredential(
                                    firebaseCredential
                                ).await()
                            } else {
                                errorMessage =
                                    "Hindi suportado ang credential na ibinalik. Subukan muli."
                            }
                        } catch (e: GetCredentialException) {
                            errorMessage =
                                "Hindi nakumpleto ang Google Sign-In. " +
                                "Tingnan ang Google Sign-In configuration at subukan muli."
                        } catch (e: Exception) {
                            errorMessage =
                                "Hindi nakumpleto ang authentication. " +
                                "Tingnan ang Firebase configuration at internet connection."
                        } finally {
                            isLoading = false
                        }
                    }
                }
            ) {
                Text("Continue with Google")
            }
        }
    }
}
