package io.freewheel.launcher.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.freewheel.launcher.data.UserProfile
import io.freewheel.launcher.ui.theme.*

private enum class WizardStep { WELCOME, PROFILE, BODY, FITNESS, DONE }

@Composable
fun SetupWizardScreen(
    onComplete: (UserProfile) -> Unit,
    onSkip: () -> Unit,
) {
    var step by remember { mutableStateOf(WizardStep.WELCOME) }
    var name by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("") }
    var weightLbs by remember { mutableStateOf("") }
    var heightFeet by remember { mutableStateOf("") }
    var heightInches by remember { mutableStateOf("") }
    var ftp by remember { mutableStateOf("") }
    var maxHr by remember { mutableStateOf("") }

    fun buildProfile() = UserProfile(
        displayName = name.trim(),
        age = age.toIntOrNull() ?: 0,
        gender = gender,
        weightLbs = weightLbs.toIntOrNull() ?: 0,
        heightInches = ((heightFeet.toIntOrNull() ?: 0) * 12) + (heightInches.toIntOrNull() ?: 0),
        ftp = ftp.toIntOrNull() ?: 0,
        maxHeartRate = maxHr.toIntOrNull()
            ?: if ((age.toIntOrNull() ?: 0) > 0) 220 - (age.toIntOrNull() ?: 30) else 0,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0A0B14), Color(0xFF0D0E1A)),
                )
            )
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF22D3EE).copy(alpha = 0.08f),
                            Color.Transparent,
                        ),
                    ),
                    radius = size.maxDimension * 0.5f,
                    center = Offset(size.width * 0.3f, size.height * 0.2f),
                )
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Progress dots
            if (step != WizardStep.WELCOME && step != WizardStep.DONE) {
                StepIndicator(
                    current = WizardStep.entries.indexOf(step),
                    total = WizardStep.entries.size,
                )
                Spacer(Modifier.height(24.dp))
            }

            // Content
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        slideInHorizontally { it / 2 } + fadeIn() togetherWith
                            slideOutHorizontally { -it / 2 } + fadeOut()
                    },
                    label = "wizard",
                ) { currentStep ->
                    when (currentStep) {
                        WizardStep.WELCOME -> WelcomeStep()
                        WizardStep.PROFILE -> ProfileStep(name, age, gender,
                            onNameChange = { name = it },
                            onAgeChange = { age = it },
                            onGenderChange = { gender = it },
                        )
                        WizardStep.BODY -> BodyStep(weightLbs, heightFeet, heightInches,
                            onWeightChange = { weightLbs = it },
                            onHeightFeetChange = { heightFeet = it },
                            onHeightInchesChange = { heightInches = it },
                        )
                        WizardStep.FITNESS -> FitnessStep(ftp, maxHr,
                            onFtpChange = { ftp = it },
                            onMaxHrChange = { maxHr = it },
                        )
                        WizardStep.DONE -> DoneStep(name)
                    }
                }
            }

            // Bottom navigation
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Skip / Back
                if (step == WizardStep.WELCOME) {
                    TextButton(onClick = onSkip) {
                        Text("Skip Setup", color = TextMuted, fontSize = 14.sp)
                    }
                } else if (step != WizardStep.DONE) {
                    TextButton(onClick = {
                        step = WizardStep.entries[WizardStep.entries.indexOf(step) - 1]
                    }) {
                        Text("Back", color = TextSecondary, fontSize = 14.sp)
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }

                // Next / Finish
                val isLast = step == WizardStep.DONE
                val nextLabel = when (step) {
                    WizardStep.WELCOME -> "Get Started"
                    WizardStep.DONE -> "Start Riding"
                    else -> "Continue"
                }

                Button(
                    onClick = {
                        if (isLast) {
                            onComplete(buildProfile())
                        } else {
                            step = WizardStep.entries[WizardStep.entries.indexOf(step) + 1]
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF22D3EE),
                        contentColor = Color(0xFF0A0B14),
                    ),
                    modifier = Modifier.height(48.dp).widthIn(min = 160.dp),
                ) {
                    Text(
                        nextLabel,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        letterSpacing = 0.5.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun StepIndicator(current: Int, total: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (i in 0 until total) {
            Box(
                modifier = Modifier
                    .size(if (i == current) 10.dp else 8.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            i == current -> Color(0xFF22D3EE)
                            i < current -> Color(0xFF22D3EE).copy(alpha = 0.4f)
                            else -> Color.White.copy(alpha = 0.15f)
                        }
                    )
            )
        }
    }
}

@Composable
private fun WelcomeStep() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.widthIn(max = 600.dp),
    ) {
        Text(
            text = "VELOLAUNCHER",
            style = MaterialTheme.typography.labelMedium.copy(
                letterSpacing = 4.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = Color(0xFF22D3EE).copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Welcome to your bike",
            style = MaterialTheme.typography.displaySmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 36.sp,
            ),
            color = Color.White,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Let's set up your profile so we can personalize your experience, track your fitness, and calculate accurate metrics.",
            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
            color = Color.White.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ProfileStep(
    name: String, age: String, gender: String,
    onNameChange: (String) -> Unit,
    onAgeChange: (String) -> Unit,
    onGenderChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier.widthIn(max = 500.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SectionTitle("WHO ARE YOU?", "Tell us about yourself")

        WizardTextField(
            value = name,
            onValueChange = onNameChange,
            label = "Display Name",
            placeholder = "Rider",
        )
        WizardTextField(
            value = age,
            onValueChange = { if (it.length <= 3 && it.all { c -> c.isDigit() }) onAgeChange(it) },
            label = "Age",
            placeholder = "30",
            keyboardType = KeyboardType.Number,
        )

        // Gender selector
        Column {
            Text("Gender", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GenderChip("Male", "male", gender, Icons.Default.Male, onGenderChange)
                GenderChip("Female", "female", gender, Icons.Default.Female, onGenderChange)
                GenderChip("Other", "other", gender, Icons.Default.Person, onGenderChange)
            }
        }
    }
}

@Composable
private fun GenderChip(
    label: String, value: String, selected: String,
    icon: ImageVector, onSelect: (String) -> Unit,
) {
    val isSelected = selected == value
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) Color(0xFF22D3EE).copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f))
            .border(
                1.dp,
                if (isSelected) Color(0xFF22D3EE).copy(alpha = 0.5f) else Color.White.copy(alpha = 0.1f),
                RoundedCornerShape(12.dp),
            )
            .clickable { onSelect(value) }
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon, null,
                tint = if (isSelected) Color(0xFF22D3EE) else TextMuted,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                color = if (isSelected) Color.White else TextSecondary,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun BodyStep(
    weight: String, heightFeet: String, heightInches: String,
    onWeightChange: (String) -> Unit,
    onHeightFeetChange: (String) -> Unit,
    onHeightInchesChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier.widthIn(max = 500.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SectionTitle("BODY METRICS", "Used for calorie and power zone calculations")

        WizardTextField(
            value = weight,
            onValueChange = { if (it.length <= 3 && it.all { c -> c.isDigit() }) onWeightChange(it) },
            label = "Weight (lbs)",
            placeholder = "175",
            keyboardType = KeyboardType.Number,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            WizardTextField(
                value = heightFeet,
                onValueChange = { if (it.length <= 1 && it.all { c -> c.isDigit() }) onHeightFeetChange(it) },
                label = "Height (feet)",
                placeholder = "5",
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
            )
            WizardTextField(
                value = heightInches,
                onValueChange = { if (it.length <= 2 && it.all { c -> c.isDigit() }) onHeightInchesChange(it) },
                label = "Height (inches)",
                placeholder = "10",
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun FitnessStep(
    ftp: String, maxHr: String,
    onFtpChange: (String) -> Unit,
    onMaxHrChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier.widthIn(max = 500.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SectionTitle("FITNESS LEVEL", "Optional — we'll estimate if you skip these")

        WizardTextField(
            value = ftp,
            onValueChange = { if (it.length <= 3 && it.all { c -> c.isDigit() }) onFtpChange(it) },
            label = "FTP (Functional Threshold Power)",
            placeholder = "Leave blank to estimate later",
            keyboardType = KeyboardType.Number,
        )
        WizardTextField(
            value = maxHr,
            onValueChange = { if (it.length <= 3 && it.all { c -> c.isDigit() }) onMaxHrChange(it) },
            label = "Max Heart Rate",
            placeholder = "Auto-calculated from age if blank",
            keyboardType = KeyboardType.Number,
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E293B), RoundedCornerShape(12.dp))
                .padding(16.dp),
        ) {
            Text(
                text = "Don't know your FTP? No worries — ride for a few sessions and we'll help you estimate it from your power data.",
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                color = Color.White.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun DoneStep(name: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.widthIn(max = 600.dp),
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(Color(0xFF22D3EE).copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = Color(0xFF22D3EE),
                modifier = Modifier.size(40.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = "You're all set${if (name.isNotBlank()) ", $name" else ""}!",
            style = MaterialTheme.typography.displaySmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
            ),
            color = Color.White,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Your profile is ready. Time to ride.",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun SectionTitle(label: String, subtitle: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                letterSpacing = 3.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = Color(0xFF22D3EE).copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
            ),
            color = Color.White,
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun WizardTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(label, color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(
                color = Color.White,
                fontSize = 16.sp,
            ),
            cursorBrush = SolidColor(Color(0xFF22D3EE)),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            singleLine = true,
            decorationBox = { inner ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    if (value.isEmpty()) {
                        Text(placeholder, color = TextMuted, fontSize = 16.sp)
                    }
                    inner()
                }
            },
        )
    }
}
