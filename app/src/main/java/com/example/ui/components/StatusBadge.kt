package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ReservationStatus
import com.example.ui.theme.StatusAvailableBlue
import com.example.ui.theme.StatusAvailableBlueBg
import com.example.ui.theme.StatusConsumedOrange
import com.example.ui.theme.StatusConsumedOrangeBg
import com.example.ui.theme.StatusLockedGrey
import com.example.ui.theme.StatusLockedGreyBg
import com.example.ui.theme.StatusReservedGreen
import com.example.ui.theme.StatusReservedGreenBg

private val BadgeShape = RoundedCornerShape(8.dp)

@Composable
fun StatusBadge(
    status: ReservationStatus,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor, icon) = when (status) {
        ReservationStatus.RESERVED -> Triple(StatusReservedGreenBg, StatusReservedGreen, Icons.Default.CheckCircle)
        ReservationStatus.RESERVED_LOCKED -> Triple(StatusReservedGreenBg, StatusReservedGreen, Icons.Default.Lock)
        ReservationStatus.NOT_RESERVED -> Triple(StatusAvailableBlueBg, StatusAvailableBlue, Icons.Default.RadioButtonUnchecked)
        ReservationStatus.LOCKED -> Triple(StatusLockedGreyBg, StatusLockedGrey, Icons.Default.Lock)
        ReservationStatus.CONSUMED -> Triple(StatusConsumedOrangeBg, StatusConsumedOrange, Icons.Default.Restaurant)
    }

    Box(
        modifier = modifier
            .background(bgColor, BadgeShape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = status.titlePersian,
                tint = textColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = status.titlePersian,
                color = textColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
