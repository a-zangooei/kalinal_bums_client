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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.password
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
    passwordVisible: Boolean,
    captchaInput: String,
    captchaBitmap: Bitmap?,
    rememberMe: Boolean,
    isEncryptionAvailable: Boolean,
    requiresBiometricUnlock: Boolean,
    canQuickFillSavedCredentials: Boolean,
    isLoading: Boolean,
    isCaptchaRefreshing: Boolean,
    onBiometricUnlockClick: () -> Unit,
    onQuickFillSavedCredentials: () -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onCaptchaChange: (String) -> Unit,
    onRefreshCaptcha: () -> Unit,
    onToggleRememberMe: () -> Unit,
    onSubmitLogin: () -> Unit
) {
    val passwordFocusRequester = remember { FocusRequester() }
    val captchaFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

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
            // Biometric / Device PIN unlock button when saved credentials are locked
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
                        contentDescription = null,
                        tint = TealPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "بازیابی رمز عبور با اثر انگشت یا قفل دستگاه",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TealPrimary
                    )
                }
            }

            // Explicit quick-fill option after manual logout when Remember Me is ON and Biometric Lock is OFF
            AnimatedVisibility(visible = canQuickFillSavedCredentials && !requiresBiometricUnlock) {
                OutlinedButton(
                    onClick = onQuickFillSavedCredentials,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                        .heightIn(min = 48.dp)
                        .testTag("quick_fill_saved_credentials_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = TealPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "تکمیل خودکار اطلاعات ورود ذخیره‌شده",
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
                label = { Text("نام کاربری") },
                leadingIcon = {
                    Icon(Icons.Default.Person, contentDescription = null)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { passwordFocusRequester.requestFocus() }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown &&
                            (event.key == Key.Enter || event.key == Key.NumPadEnter)
                        ) {
                            passwordFocusRequester.requestFocus()
                            true
                        } else {
                            false
                        }
                    }
                    .testTag("username_input"),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TealPrimary,
                    focusedLabelColor = TealPrimary
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Password Field (strict password keyboard, non-autocorrect, semantics)
            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text("رمز عبور") },
                leadingIcon = {
                    Icon(Icons.Default.Lock, contentDescription = null)
                },
                trailingIcon = {
                    IconButton(
                        onClick = onTogglePasswordVisibility,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .semantics {
                                stateDescription = if (passwordVisible) "رمز عبور قابل مشاهده است" else "رمز عبور مخفی است"
                            }
                            .testTag("password_visibility_toggle")
                    ) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (passwordVisible) "پنهان‌کردن رمز عبور" else "نمایش رمز عبور"
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { captchaFocusRequester.requestFocus() }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(passwordFocusRequester)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown &&
                            (event.key == Key.Enter || event.key == Key.NumPadEnter)
                        ) {
                            captchaFocusRequester.requestFocus()
                            true
                        } else {
                            false
                        }
                    }
                    .semantics {
                        if (!passwordVisible) {
                            password()
                        }
                    }
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
                            contentDescription = "تصویر کد امنیتی",
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
                        TextButton(
                            onClick = onRefreshCaptcha,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Text(
                                text = "دریافت کد امنیتی",
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
                        contentDescription = "دریافت مجدد کد امنیتی",
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
                label = { Text("کد امنیتی") },
                placeholder = { Text("کد ۴ رقمی") },
                leadingIcon = {
                    Icon(Icons.Default.Security, contentDescription = null)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        if (!isLoading) {
                            onSubmitLogin()
                        }
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(captchaFocusRequester)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown &&
                            (event.key == Key.Enter || event.key == Key.NumPadEnter)
                        ) {
                            focusManager.clearFocus()
                            if (!isLoading) {
                                onSubmitLogin()
                            }
                            true
                        } else {
                            false
                        }
                    }
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
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(10.dp))
                    .toggleable(
                        value = rememberMe,
                        enabled = isEncryptionAvailable,
                        role = Role.Checkbox,
                        onValueChange = { onToggleRememberMe() }
                    )
                    .padding(horizontal = 4.dp, vertical = 4.dp)
                    .testTag("remember_me_checkbox"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = rememberMe,
                    enabled = isEncryptionAvailable,
                    onCheckedChange = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isEncryptionAvailable) {
                        "ذخیره اطلاعات ورود"
                    } else {
                        "ذخیره اطلاعات ورود (غیرفعال به علت نبود رمزنگاری)"
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
                    passwordVisible = false,
                    captchaInput = "4821",
                    captchaBitmap = null,
                    rememberMe = true,
                    isEncryptionAvailable = true,
                    requiresBiometricUnlock = true,
                    canQuickFillSavedCredentials = false,
                    isLoading = false,
                    isCaptchaRefreshing = false,
                    onBiometricUnlockClick = {},
                    onQuickFillSavedCredentials = {},
                    onUsernameChange = {},
                    onPasswordChange = {},
                    onTogglePasswordVisibility = {},
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
                    passwordVisible = false,
                    captchaInput = "",
                    captchaBitmap = null,
                    rememberMe = false,
                    isEncryptionAvailable = false,
                    requiresBiometricUnlock = false,
                    canQuickFillSavedCredentials = true,
                    isLoading = false,
                    isCaptchaRefreshing = true,
                    onBiometricUnlockClick = {},
                    onQuickFillSavedCredentials = {},
                    onUsernameChange = {},
                    onPasswordChange = {},
                    onTogglePasswordVisibility = {},
                    onCaptchaChange = {},
                    onRefreshCaptcha = {},
                    onToggleRememberMe = {},
                    onSubmitLogin = {}
                )
            }
        }
    }
}
