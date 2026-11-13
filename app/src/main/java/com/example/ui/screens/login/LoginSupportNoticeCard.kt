package com.example.ui.screens.login

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.PersianRtlLayout
import com.example.ui.theme.CullinanTheme
import com.example.ui.theme.TealPrimary

@Composable
internal fun LoginSupportNoticeCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "راهنما",
                    tint = TealPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "راهنمای دانشجویان",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TealPrimary
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "این برنامه مستقیماً به سامانه کالینان دانشگاه علوم پزشکی بیرجند (jeton.bums.ac.ir) متصل می‌شود. نام کاربری و گذرواژه شما با کلید RSA سرور پیش از ارسال رمزنگاری شده و تنها در صورت انتخاب «مرا به خاطر بسپار» در مخزن رمزنگاری‌شده دستگاه نگهداری می‌شوند.",
                fontSize = 11.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(showBackground = true, name = "LoginSupportNotice - Light RTL")
@Composable
private fun LoginSupportNoticeLightPreview() {
    CullinanTheme(darkTheme = false) {
        PersianRtlLayout {
            Column(modifier = Modifier.padding(16.dp)) {
                LoginSupportNoticeCard()
            }
        }
    }
}

@Preview(showBackground = true, name = "LoginSupportNotice - Dark RTL")
@Composable
private fun LoginSupportNoticeDarkPreview() {
    CullinanTheme(darkTheme = true) {
        PersianRtlLayout {
            Column(modifier = Modifier.padding(16.dp)) {
                LoginSupportNoticeCard()
            }
        }
    }
}
