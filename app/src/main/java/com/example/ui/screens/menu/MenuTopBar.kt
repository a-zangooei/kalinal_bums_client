package com.example.ui.screens.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.PersianRtlLayout
import com.example.ui.theme.AccentGold
import com.example.ui.theme.CullinanTheme
import com.example.ui.theme.TealPrimary
import com.example.ui.theme.TealPrimaryDark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MenuTopBar(
    onOpenProfile: () -> Unit,
    onLogout: () -> Unit,
    isLoggingOut: Boolean
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(AccentGold, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.RestaurantMenu,
                        contentDescription = "کالینان",
                        tint = TealPrimaryDark,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "برنامه غذایی هفتگی",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        },
        actions = {
            IconButton(
                onClick = onOpenProfile,
                modifier = Modifier.testTag("profile_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "مشخصات دانشجو",
                    tint = Color.White
                )
            }
            IconButton(
                onClick = onLogout,
                enabled = !isLoggingOut,
                modifier = Modifier.testTag("logout_button")
            ) {
                if (isLoggingOut) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Default.ExitToApp,
                        contentDescription = "خروج از حساب",
                        tint = Color.White
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = TealPrimary,
            titleContentColor = Color.White,
            actionIconContentColor = Color.White
        )
    )
}

@Preview(showBackground = true, name = "MenuTopBar - Light RTL")
@Composable
private fun MenuTopBarLightPreview() {
    CullinanTheme(darkTheme = false) {
        PersianRtlLayout {
            MenuTopBar(onOpenProfile = {}, onLogout = {}, isLoggingOut = false)
        }
    }
}

@Preview(showBackground = true, name = "MenuTopBar - Dark RTL")
@Composable
private fun MenuTopBarDarkPreview() {
    CullinanTheme(darkTheme = true) {
        PersianRtlLayout {
            MenuTopBar(onOpenProfile = {}, onLogout = {}, isLoggingOut = true)
        }
    }
}
