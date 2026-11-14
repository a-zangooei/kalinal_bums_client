package com.example.ui.screens.menu

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FoodOption
import com.example.data.model.MealItem
import com.example.data.model.MealType
import com.example.data.model.ReservationStatus
import com.example.data.model.SelfOption
import com.example.data.parser.ReservationHtmlParser
import com.example.ui.components.PersianRtlLayout
import com.example.ui.theme.CullinanTheme
import com.example.ui.theme.TealPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReservationBottomSheet(
    meal: MealItem,
    dayName: String,
    dateJalali: String,
    currentCreditRials: Long,
    onDismiss: () -> Unit,
    onConfirm: (selectedFoodId: String, selectedSelfId: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        PersianRtlLayout {
            ReservationBottomSheetContent(
                meal = meal,
                dayName = dayName,
                dateJalali = dateJalali,
                currentCreditRials = currentCreditRials,
                onDismiss = onDismiss,
                onConfirm = onConfirm
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReservationBottomSheetContent(
    meal: MealItem,
    dayName: String,
    dateJalali: String,
    currentCreditRials: Long,
    onDismiss: () -> Unit,
    onConfirm: (selectedFoodId: String, selectedSelfId: String) -> Unit
) {
    val foodOptions = meal.availableFoodOptions.ifEmpty {
        if (meal.selectedFoodId != "-1" && meal.foodName.isNotEmpty()) {
            listOf(FoodOption(meal.selectedFoodId, meal.foodName, meal.priceRials))
        } else {
            emptyList()
        }
    }

    var selectedFoodId by remember {
        mutableStateOf(
            meal.selectedFoodId.takeIf { it != "-1" && foodOptions.any { f -> f.id == it } }
                ?: foodOptions.firstOrNull()?.id
                ?: "-1"
        )
    }

    val selfOptions = meal.availableSelfOptions.ifEmpty {
        listOf(SelfOption("1", meal.diningHall.ifEmpty { "سلف پردیس" }, isDefault = true))
    }

    var selectedSelfId by remember {
        mutableStateOf(
            meal.selectedSelfId.takeIf { it != "-1" && selfOptions.any { s -> s.id == it } }
                ?: selfOptions.firstOrNull { it.isDefault }?.id
                ?: selfOptions.firstOrNull()?.id
                ?: "1"
        )
    }

    var selfDropdownExpanded by remember { mutableStateOf(false) }

    val selectedFood = foodOptions.find { it.id == selectedFoodId }
    val foodPrice = selectedFood?.priceRials ?: 0L
    val remainingCredit = currentCreditRials - foodPrice
    val isInsufficientCredit = currentCreditRials > 0 && remainingCredit < 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp)
    ) {
        // Sheet Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "رزرو ${meal.mealType.titlePersian} $dayName",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (dateJalali.isNotEmpty()) {
                    Text(
                        text = "تاریخ: $dateJalali",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(TealPrimary.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Fastfood,
                    contentDescription = null,
                    tint = TealPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(14.dp))

        // 1. Food Options Selection
        Text(
            text = "انتخاب غذا و مقدار:",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (foodOptions.isEmpty()) {
            Text(
                text = "گزینه غذایی برای این روز تعریف نشده است.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.error
            )
        } else {
            Column(modifier = Modifier.selectableGroup()) {
                foodOptions.forEach { option ->
                    val isSelected = option.id == selectedFoodId
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(12.dp))
                            .selectable(
                                selected = isSelected,
                                role = Role.RadioButton,
                                onClick = { selectedFoodId = option.id }
                            )
                            .testTag("food_option_${option.id}"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) TealPrimary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        ),
                        border = if (isSelected) BorderStroke(1.5.dp, TealPrimary) else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = TealPrimary)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = option.name,
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (option.isHalfPortion) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .background(Color(0xFFE0F2F1), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "نیم‌پرس",
                                                fontSize = 10.sp,
                                                color = TealPrimary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                            Text(
                                text = ReservationHtmlParser.formatRials(option.priceRials),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D32)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Dining Hall Selection
        Text(
            text = "سلف توزیع غذا:",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))

        ExposedDropdownMenuBox(
            expanded = selfDropdownExpanded,
            onExpandedChange = { selfDropdownExpanded = !selfDropdownExpanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            val currentSelfName = selfOptions.find { it.id == selectedSelfId }?.name ?: "پردیس"
            OutlinedTextField(
                value = currentSelfName,
                onValueChange = {},
                readOnly = true,
                leadingIcon = {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = TealPrimary)
                },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = selfDropdownExpanded) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            ExposedDropdownMenu(
                expanded = selfDropdownExpanded,
                onDismissRequest = { selfDropdownExpanded = false }
            ) {
                selfOptions.forEach { selfOpt ->
                    DropdownMenuItem(
                        text = { Text(selfOpt.name) },
                        onClick = {
                            selectedSelfId = selfOpt.id
                            selfDropdownExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Price & Credit Breakdown Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("اعتبار فعلی شما:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(ReservationHtmlParser.formatRials(currentCreditRials), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("مبلغ غذا:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(ReservationHtmlParser.formatRials(foodPrice), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
                }
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 6.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("مانده پس از رزرو:", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = ReservationHtmlParser.formatRials(remainingCredit),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (remainingCredit >= 0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        if (isInsufficientCredit) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "توجه: مانده اعتبار شما برای این رزرو کافی نیست، اما درخواست ارسال خواهد شد.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 4. Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Text("انصراف")
            }

            Button(
                onClick = { onConfirm(selectedFoodId, selectedSelfId) },
                enabled = selectedFoodId != "-1",
                modifier = Modifier
                    .weight(2f)
                    .height(48.dp)
                    .testTag("submit_reservation_button"),
                colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("ثبت و تأیید رزرو", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
internal fun CancelMealConfirmationDialog(
    meal: MealItem,
    dayName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val mealTitle = meal.mealType.titlePersian
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("لغو رزرو $mealTitle", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                Text(
                    text = "آیا از لغو رزرو وعده $mealTitle روز «$dayName» اطمینان دارید؟",
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "غذای فعلی: ${meal.foodName}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (meal.priceRials > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "مبلغ ${meal.priceFormatted} به مانده اعتبار شما بازگردانده خواهد شد.",
                        fontSize = 13.sp,
                        color = Color(0xFF2E7D32)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("confirm_cancel_lunch_button")
            ) {
                Text("تأیید لغو رزرو", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text("انصراف")
            }
        }
    )
}

@Composable
internal fun StudentProfileDialog(
    studentName: String,
    studentNumber: String,
    creditFormatted: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = TealPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("اطلاعات حساب کاربری", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                ProfileDetailRow("نام و نام خانوادگی", studentName)
                ProfileDetailRow("شناسه کاربری", studentNumber)
                ProfileDetailRow("وضعیت مانده اعتبار", creditFormatted)
                ProfileDetailRow("سامانه متصل", "سامانه تغذیه دانشگاه (jeton.bums.ac.ir)")
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text("بستن", color = TealPrimary, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun ProfileDetailRow(title: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = title, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

private val previewMeal = MealItem(
    id = "preview_lunch",
    mealType = MealType.LUNCH,
    dayIndex = 4,
    foodName = "چلو جوجه کباب",
    diningHall = "سلف پردیس",
    priceRials = 180000L,
    priceFormatted = "۱۸۰,۰۰۰ ریال",
    calories = 650,
    status = ReservationStatus.NOT_RESERVED,
    count = 0,
    availableFoodOptions = listOf(
        FoodOption(id = "89247", name = "چلو جوجه کباب", priceRials = 180000L, isHalfPortion = false),
        FoodOption(id = "89268", name = "چلو جوجه کباب", priceRials = 90000L, isHalfPortion = true)
    ),
    availableSelfOptions = listOf(SelfOption(id = "1", name = "سلف پردیس", isDefault = true))
)

@Preview(showBackground = true, name = "ReservationSheet - Light RTL")
@Composable
private fun ReservationSheetLightPreview() {
    CullinanTheme(darkTheme = false) {
        PersianRtlLayout {
            Surface(color = MaterialTheme.colorScheme.surface) {
                ReservationBottomSheetContent(
                    meal = previewMeal,
                    dayName = "چهارشنبه",
                    dateJalali = "۱۴۰۵/۰۸/۲۰",
                    currentCreditRials = 590000L,
                    onDismiss = {},
                    onConfirm = { _, _ -> }
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "ReservationSheet - Dark RTL")
@Composable
private fun ReservationSheetDarkPreview() {
    CullinanTheme(darkTheme = true) {
        PersianRtlLayout {
            Surface(color = MaterialTheme.colorScheme.surface) {
                ReservationBottomSheetContent(
                    meal = previewMeal,
                    dayName = "چهارشنبه",
                    dateJalali = "۱۴۰۵/۰۸/۲۰",
                    currentCreditRials = 590000L,
                    onDismiss = {},
                    onConfirm = { _, _ -> }
                )
            }
        }
    }
}
