package com.ferdousmunny.cgpacalculator

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ferdousmunny.cgpacalculator.ui.theme.CGPATheme
import com.google.firebase.auth.FirebaseAuth

private data class OnboardingPage(val icon: ImageVector, val title: String, val description: String)

/** Shown once, the very first time the app is opened, before Login/Home. */
class OnboardingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CGPATheme(darkTheme = false) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    OnboardingScreen {
                        markOnboardingShown(this@OnboardingActivity)
                        val destination = if (FirebaseAuth.getInstance().currentUser != null) {
                            MainActivity::class.java
                        } else {
                            LoginActivity::class.java
                        }
                        startActivity(Intent(this@OnboardingActivity, destination))
                        finish()
                    }
                }
            }
        }
    }
}

/** Marks that the user has completed onboarding, so it never shows again on this device. */
fun markOnboardingShown(context: Context) {
    context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        .edit().putBoolean("onboarding_shown", true).apply()
}

fun hasSeenOnboarding(context: Context): Boolean {
    return context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        .getBoolean("onboarding_shown", false)
}

@Composable
private fun OnboardingScreen(onFinish: () -> Unit) {
    val pages = listOf(
        OnboardingPage(
            Icons.Default.MenuBook,
            "Track Every Course",
            "Add and organize your courses by semester, with your live CGPA always at the top."
        ),
        OnboardingPage(
            Icons.Default.Refresh,
            "Plan a Retake",
            "Pick new grades for courses you're retaking and preview the CGPA change before saving anything."
        ),
        OnboardingPage(
            Icons.Default.TrendingUp,
            "Target CGPA & Trend",
            "Set a CGPA goal and see exactly what average you need -- plus a chart of your progress each semester."
        ),
        OnboardingPage(
            Icons.Default.UploadFile,
            "Import & Backup",
            "Bulk import from CSV, PDF, or a photo of your transcript, and back up your data anytime."
        )
    )
    var pageIndex by remember { mutableStateOf(0) }
    val page = pages[pageIndex]

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onFinish) { Text("Skip") }
        }

        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(page.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(56.dp))
            }
            Spacer(modifier = Modifier.height(32.dp))
            Text(page.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                page.description,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            pages.indices.forEach { i ->
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .size(if (i == pageIndex) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (i == pageIndex) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant
                        )
                )
            }
        }

        Button(
            onClick = {
                if (pageIndex < pages.lastIndex) {
                    pageIndex++
                } else {
                    onFinish()
                }
            },
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text(if (pageIndex < pages.lastIndex) "Next" else "Get Started")
        }
    }
}
