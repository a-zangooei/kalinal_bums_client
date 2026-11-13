package com.example.ui.screens.login

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.PersianRtlLayout
import com.example.ui.theme.CullinanTheme
import com.example.ui.theme.TealPrimary

@Composable
internal fun LoginCredentialsCard(
    username: String,
    password: String,
    captchaInput: String,
    captchaBitmap: Bitmap?,
    rememberMe: Boolean,
    isEncryptionAvailable: Boolean,
    requiresBiometricUnlock: Boolean,
    isLoading: Boolean,
    isCaptchaRefreshing: Boolean,
    onBiometricUnlockClick: () -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onCaptchaChange: (String) -> Unit,
    onRefreshCaptcha: () -> Unit,
    onToggleRememberMe: () -> Unit,
    onSubmitLogin: () -> Unit
) {
    var passwordVisible by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Biometric / Device PIN unlock card when saved credentials are locked
            AnimatedVisibility(visible = requiresBiometricUnlock) {
                OutlinedButton(
                    onClick = onBiometricUnlockClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                        .heightIn(min = 48.dp)
                        .testTag("biometric_unlock_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = "تایید هویت بیومتریک",
                        tint = TealPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "بازیابی رمز ذخیره‌شده با اثر انگشت / قفل دستگاه",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TealPrimary
                    )
                }
            }

            // Username Field
            OutlinedTextField(
                value = username,
                onValueChange = onUsernameChange,
                label = { Text("نام کاربری (شماره دانشجویی)") },
                leadingIcon = {
                    Icon(Icons.Default.Person, contentDescription = "نام کاربری")
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("username_input"),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TealPrimary,
                    focusedLabelColor = TealPrimary
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Password Field
            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text("گذرواژه (کد ملی)") },
                leadingIcon = {
                    Icon(Icons.Default.Lock, contentDescription = "گذرواژه")
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (passwordVisible) "مخفی کردن گذرواژه" else "نمایش گذرواژه"
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("password_input"),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TealPrimary,
                    focusedLabelColor = TealPrimary
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Captcha Box & Refresh Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                        .background(Color(0xFFF5F7FA)),
                    contentAlignment = Alignment.Center
                ) {
                    if (captchaBitmap != null) {
                        Image(
                            bitmap = captchaBitmap.asImageBitmap(),
                            contentDescription = "کد امنیتی",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp)
                        )
                    } else if (isLoading || isCaptchaRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = TealPrimary
                        )
                    } else {
                        TextButton(onClick = onRefreshCaptcha) {
                            Text(
                                text = "بارگذاری مجدد کد",
                                fontSize = 11.sp,
                                color = TealPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                val infiniteTransition = rememberInfiniteTransition(label = "refresh_rotate")
                val rotation by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "rotate"
                )

                IconButton(
                    onClick = onRefreshCaptcha,
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .testTag("refresh_captcha_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "تازه‌سازی کد امنیتی",
                        tint = TealPrimary,
                        modifier = Modifier.rotate(if (isCaptchaRefreshing) rotation else 0f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Captcha Input Field
            OutlinedTextField(
                value = captchaInput,
                onValueChange = onCaptchaChange,
                label = { Text("کد امنیتی تصویر") },
                placeholder = { Text("۴ رقم") },
                leadingIcon = {
                    Icon(Icons.Default.Security, contentDescription = "کد امنیتی")
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("captcha_input"),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TealPrimary,
                    focusedLabelColor = TealPrimary
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Remember Me Checkbox (Disabled when encryption unavailable)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = isEncryptionAvailable, onClick = onToggleRememberMe),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = rememberMe,
                    enabled = isEncryptionAvailable,
                    onCheckedChange = { onToggleRememberMe() },
                    modifier = Modifier.testTag("remember_me_checkbox")
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isEncryptionAvailable) {
                        "مرا به خاطر بسپار (ذخیره رمزنگاری‌شده)"
                    } else {
                        "مرا به خاطر بسپار (غیرفعال به علت نبود رمزنگاری)"
                    },
                    fontSize = 13.sp,
                    color = if (isEncryptionAvailable) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Submit Login Button
            Button(
                onClick = onSubmitLogin,
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("login_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Text(
                        text = "ورود به سامانه",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "LoginFormCard - Light RTL")
@Composable
private fun LoginFormCardLightPreview() {
    CullinanTheme(darkTheme = false) {
        PersianRtlLayout {
            Column(modifier = Modifier.padding(16.dp)) {
                LoginCredentialsCard(
                    username = "4031001234",
                    password = "••••••••",
                    captchaInput = "4821",
                    captchaBitmap = null,
                    rememberMe = true,
                    isEncryptionAvailable = true,
                    requiresBiometricUnlock = true,
                    isLoading = false,
                    isCaptchaRefreshing = false,
                    onBiometricUnlockClick = {},
                    onUsernameChange = {},
                    onPasswordChange = {},
                    onCaptchaChange = {},
                    onRefreshCaptcha = {},
                    onToggleRememberMe = {},
                    onSubmitLogin = {}
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "LoginFormCard - Dark RTL")
@Composable
private fun LoginFormCardDarkPreview() {
    CullinanTheme(darkTheme = true) {
        PersianRtlLayout {
            Column(modifier = Modifier.padding(16.dp)) {
                LoginCredentialsCard(
                    username = "",
                    password = "",
                    captchaInput = "",
                    captchaBitmap = null,
                    rememberMe = false,
                    isEncryptionAvailable = false,
                    requiresBiometricUnlock = false,
                    isLoading = false,
                    isCaptchaRefreshing = true,
                    onBiometricUnlockClick = {},
                    onUsernameChange = {},
                    onPasswordChange = {},
                    onCaptchaChange = {},
                    onRefreshCaptcha = {},
                    onToggleRememberMe = {},
                    onSubmitLogin = {}
                )
            }
        }
    }
}
