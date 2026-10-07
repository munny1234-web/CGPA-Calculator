package com.ferdousmunny.cgpacalculator

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ferdousmunny.cgpacalculator.data.UserPreferences
import com.ferdousmunny.cgpacalculator.model.Departments
import com.ferdousmunny.cgpacalculator.ui.theme.CGPATheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.userProfileChangeRequest

class LoginActivity : ComponentActivity() {
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CGPATheme(darkTheme = false) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AuthScreen(
                        onLogin = { email, password, onError, onLoading ->
                            onLoading(true)
                            auth.signInWithEmailAndPassword(email, password)
                                .addOnSuccessListener {
                                    onLoading(false)
                                    goToMain()
                                }
                                .addOnFailureListener { e ->
                                    onLoading(false)
                                    onError(e.localizedMessage ?: "Login failed. Please check your details.")
                                }
                        },
                        onRegister = { name, email, password, department, onError, onLoading ->
                            onLoading(true)
                            auth.createUserWithEmailAndPassword(email, password)
                                .addOnSuccessListener { result ->
                                    val user = result.user
                                    user?.updateProfile(userProfileChangeRequest { displayName = name })
                                    user?.sendEmailVerification()
                                    val uid = user?.uid
                                    if (uid != null) {
                                        UserPreferences(applicationContext, uid).apply {
                                            setFullName(name)
                                            setDepartment(department)
                                        }
                                    }
                                    onLoading(false)
                                    goToMain()
                                }
                                .addOnFailureListener { e ->
                                    onLoading(false)
                                    onError(e.localizedMessage ?: "Registration failed. Please try again.")
                                }
                        },
                        onForgotPassword = { email, onResult ->
                            auth.sendPasswordResetEmail(email)
                                .addOnSuccessListener { onResult(true) }
                                .addOnFailureListener { onResult(false) }
                        }
                    )
                }
            }
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    onLogin: (email: String, password: String, onError: (String) -> Unit, onLoading: (Boolean) -> Unit) -> Unit,
    onRegister: (name: String, email: String, password: String, department: String, onError: (String) -> Unit, onLoading: (Boolean) -> Unit) -> Unit,
    onForgotPassword: (email: String, onResult: (Boolean) -> Unit) -> Unit
) {
    var isRegisterMode by remember { mutableStateOf(false) }
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var department by remember { mutableStateOf(Departments.list.first()) }
    var departmentExpanded by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.School,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(44.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            if (isRegisterMode) "Create Account" else "Welcome Back",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            "CGPA Calculator",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))

        if (isRegisterMode) {
            OutlinedTextField(
                value = fullName,
                onValueChange = { fullName = it },
                label = { Text("Full Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            SimpleDropdownField(
                label = "Department",
                selectedText = department,
                options = Departments.list,
                expanded = departmentExpanded,
                onExpandedChange = { departmentExpanded = it },
                onOptionSelected = { department = it }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password"
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        if (isRegisterMode) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text("Confirm Password") },
                singleLine = true,
                visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                        Icon(
                            imageVector = if (confirmPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (confirmPasswordVisible) "Hide password" else "Show password"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (errorMessage.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                errorMessage,
                color = MaterialTheme.colorScheme.error,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                errorMessage = ""
                when {
                    email.isBlank() || password.isBlank() -> errorMessage = "Please enter email and password"
                    isRegisterMode && fullName.isBlank() -> errorMessage = "Please enter your full name"
                    isRegisterMode && password != confirmPassword -> errorMessage = "Passwords do not match"
                    isRegisterMode && password.length < 6 -> errorMessage = "Password must be at least 6 characters"
                    else -> {
                        if (isRegisterMode) {
                            onRegister(fullName, email, password, department, { errorMessage = it }, { isLoading = it })
                        } else {
                            onLogin(email, password, { errorMessage = it }, { isLoading = it })
                        }
                    }
                }
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text(if (isRegisterMode) "Create Account" else "Log In")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (!isRegisterMode) {
            TextButton(onClick = {
                if (email.isBlank()) {
                    errorMessage = "Enter your email above first, then tap Forgot Password"
                } else {
                    onForgotPassword(email) { success ->
                        Toast.makeText(
                            context,
                            if (success) "Password reset email sent" else "Could not send reset email",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }) {
                Text("Forgot Password?")
            }
        }

        TextButton(onClick = { isRegisterMode = !isRegisterMode; errorMessage = "" }) {
            Text(
                if (isRegisterMode) "Already have an account? Log in"
                else "Don't have an account? Sign up"
            )
        }
    }
}
