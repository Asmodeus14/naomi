package com.naomi.app.presentation.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun OnboardingScreen(
    onFinish: () -> Unit
) {
    var step by remember { mutableIntStateOf(1) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 80.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedContent(targetState = step, label = "onboarding_anim") { currentStep ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    when (currentStep) {
                        1 -> {
                            Text(
                                text = "Naomi",
                                style = MaterialTheme.typography.displayLarge,
                                fontWeight = FontWeight.Light,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = "You talk.\nNaomi remembers.",
                                style = MaterialTheme.typography.headlineMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 34.sp
                            )
                        }
                        2 -> {
                            Text(
                                text = "Private by design.",
                                style = MaterialTheme.typography.displayLarge,
                                fontWeight = FontWeight.Light,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = "Your voice and memories\nstay on your device.",
                                style = MaterialTheme.typography.headlineMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 34.sp
                            )
                        }
                        3 -> {
                            Text(
                                text = "Add Naomi to your\nhome screen.",
                                style = MaterialTheme.typography.displayLarge,
                                fontWeight = FontWeight.Light,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onBackground,
                                lineHeight = 42.sp
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = "Tap the widget once to instantly capture any thought without opening the app.",
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 24.sp
                            )
                        }
                    }
                }
            }
        }

        // Indicators and Continue Button
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                (1..3).forEach { dotIndex ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (dotIndex == step) 16.dp else 6.dp, 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (dotIndex == step) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (step < 3) {
                        step++
                    } else {
                        onFinish()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    text = if (step == 3) "Get Started" else "Continue",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
