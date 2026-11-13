package com.example.ui.components

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BreakfastDining
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.DinnerDining
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.LunchDining
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MealItem
import com.example.data.model.MealType
import com.example.data.model.ReservationStatus
import com.example.ui.screens.isMealActionable

private val MealCardShape = RoundedCornerShape(16.dp)
private val MealChipShape = RoundedCornerShape(6.dp)
private val MealButtonShape = RoundedCornerShape(10.dp)
private val ZeroCardElevationDp = 0.dp

private val BreakfastColor = Color(0xFFF57C00)
private val BreakfastBg = BreakfastColor.copy(alpha = 0.12f)
private val LunchColor = Color(0xFF00796B)
private val LunchBg = LunchColor.copy(alpha = 0.12f)
private val DinnerColor = Color(0xFF512DA8)
private val DinnerBg = DinnerColor.copy(alpha = 0.12f)

private val CaloriesBg = Color(0xFFFFF3E0)
private val CaloriesFg = Color(0xFFE65100)
private val PriceBg = Color(0xFFE8F5E9)
private val PriceFg = Color(0xFF2E7D32)

@Composable
fun MealCard(
    meal: MealItem,
    isProcessing: Boolean = false,
    onReserveClick: (MealItem) -> Unit = {},
    onCancelClick: (MealItem) -> Unit = {},
    onChangeFoodClick: (MealItem) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val mealIcon = when (meal.mealType) {
        MealType.BREAKFAST -> Icons.Default.BreakfastDining
        MealType.LUNCH -> Icons.Default.LunchDining
        MealType.DINNER -> Icons.Default.DinnerDining
    }
    val mealColor = when (meal.mealType) {
        MealType.BREAKFAST -> BreakfastColor
        MealType.LUNCH -> LunchColor
        MealType.DINNER -> DinnerColor
    }
    val mealIconBg = when (meal.mealType) {
        MealType.BREAKFAST -> BreakfastBg
        MealType.LUNCH -> LunchBg
        MealType.DINNER -> DinnerBg
    }

    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    val cardBorder = remember(outlineColor) {
        BorderStroke(1.dp, outlineColor.copy(alpha = 0.7f))
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("meal_card_${meal.id}"),
        shape = MealCardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = ZeroCardElevationDp),
        border = cardBorder
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Meal Type Icon & Title, and Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(mealIconBg, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = mealIcon,
                            contentDescription = meal.mealType.titlePersian,
                            tint = mealColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = meal.mealType.titlePersian,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = mealColor
                    )
                }

                StatusBadge(status = meal.status)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Food Name
            Text(
                text = meal.foodName,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Info Chips: Dining Hall, Calories, Price
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Dining hall tag
                if (meal.diningHall.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant, MealChipShape)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "سلف توزیع",
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = meal.diningHall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Calories chip if available
                    if (meal.calories > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(CaloriesBg, MealChipShape)
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocalFireDepartment,
                                contentDescription = "کالری",
                                modifier = Modifier.size(13.dp),
                                tint = CaloriesFg
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "${meal.calories} کالری",
                                fontSize = 11.sp,
                                color = CaloriesFg,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    // Price tag
                    if (meal.priceRials > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(PriceBg, MealChipShape)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Payments,
                                contentDescription = "قیمت",
                                modifier = Modifier.size(13.dp),
                                tint = PriceFg
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = meal.priceFormatted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = PriceFg
                            )
                        }
                    }
                }
            }

            // Interactive Actions Section (for Breakfast, Lunch, Dinner when editable and available)
            if (isMealActionable(meal)) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = outlineColor.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(10.dp))

                val mealTag = meal.mealType.name.lowercase()
                val onReserve = remember(meal, onReserveClick) { { onReserveClick(meal) } }
                val onCancel = remember(meal, onCancelClick) { { onCancelClick(meal) } }
                val onChangeFood = remember(meal, onChangeFoodClick) { { onChangeFoodClick(meal) } }

                if (isProcessing) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = mealColor
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "در حال ارتباط با سرور سلف...",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (meal.status == ReservationStatus.NOT_RESERVED) {
                    Button(
                        onClick = onReserve,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("reserve_${mealTag}_button_${meal.dayIndex}"),
                        colors = ButtonDefaults.buttonColors(containerColor = mealColor),
                        shape = MealButtonShape
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestaurantMenu,
                            contentDescription = "رزرو",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "رزرو ${meal.mealType.titlePersian}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else if (meal.status == ReservationStatus.RESERVED) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .testTag("cancel_${mealTag}_button_${meal.dayIndex}"),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            shape = MealButtonShape
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cancel,
                                contentDescription = "لغو رزرو",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "لغو رزرو",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (meal.availableFoodOptions.size > 1) {
                            OutlinedButton(
                                onClick = onChangeFood,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .testTag("change_${mealTag}_button_${meal.dayIndex}"),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = mealColor
                                ),
                                shape = MealButtonShape
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "تغییر غذا",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "تغییر غذا",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
