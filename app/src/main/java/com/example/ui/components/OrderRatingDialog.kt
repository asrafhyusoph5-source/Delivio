package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Order
import com.example.data.model.ReviewDocument
import com.example.data.model.ServiceType
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.TextOnYellow
import com.example.ui.theme.YellowPrimary
import java.util.Locale

@Composable
fun OrderRatingDialog(
    order: Order,
    walletBalance: Double = 0.0,
    onDismiss: () -> Unit,
    onSubmit: (ratingMerchant: Int?, ratingRider: Int?, comment: String, tipAmount: Double) -> Unit,
    isSubmitting: Boolean = false
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = null,
        text = {
            RateOrderForm(
                order = order,
                walletBalance = walletBalance,
                onDismiss = onDismiss,
                onSubmit = onSubmit,
                isSubmitting = isSubmitting,
                inDialog = true
            )
        },
        confirmButton = {},
        dismissButton = {},
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
fun RateOrderForm(
    order: Order,
    walletBalance: Double = 0.0,
    onDismiss: () -> Unit = {},
    onSubmit: (ratingMerchant: Int?, ratingRider: Int?, comment: String, tipAmount: Double) -> Unit,
    isSubmitting: Boolean = false,
    inDialog: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isFoodOrConvenience = order.serviceType == ServiceType.FOOD || order.serviceType == ServiceType.CONVENIENCE
    val isPickup = order.fulfillment.equals("pickup", ignoreCase = true)
    // Stars for the shop: food, convenience, and pick-up orders
    val hasShop = isFoodOrConvenience || isPickup
    // Stars for the driver: every order with a driver, never Pick Up
    val hasDriver = !isPickup

    var merchantRating by remember { mutableIntStateOf(0) }
    var riderRating by remember { mutableIntStateOf(0) }
    var comment by remember { mutableStateOf("") }

    // Tip options: Not now (0), ₱10, ₱30, ₱60, Enter amount (1 to 500)
    var selectedTipPreset by remember { mutableStateOf<String>("0") } // "0", "10", "30", "60", "custom"
    var customTipInput by remember { mutableStateOf("") }

    val resolvedTipAmount: Double = when (selectedTipPreset) {
        "10" -> 10.0
        "30" -> 30.0
        "60" -> 60.0
        "custom" -> customTipInput.toDoubleOrNull() ?: 0.0
        else -> 0.0
    }

    val isTipExceedingBalance = resolvedTipAmount > 0 && resolvedTipAmount > walletBalance
    val isCustomTipInvalid = selectedTipPreset == "custom" && (customTipInput.toDoubleOrNull() == null || (customTipInput.toDoubleOrNull() ?: 0.0) < 1.0 || (customTipInput.toDoubleOrNull() ?: 0.0) > 500.0)

    // Submit enabled once at least 1 star rating given
    val hasGivenRating = (hasShop && merchantRating in 1..5) || (hasDriver && riderRating in 1..5)
    val canSubmit = hasGivenRating && !isTipExceedingBalance && !isCustomTipInvalid

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (inDialog) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Rate your order",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Order ${order.displayOrderNumber}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (inDialog) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 1. Shop Rating (food, convenience, and pick-up orders)
        if (hasShop) {
            val shopName = order.merchantName?.ifBlank { null } ?: order.restaurant.name.ifBlank { "Store" }
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Rate the shop",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = shopName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    StarRatingRow(
                        rating = merchantRating,
                        onRatingChanged = { merchantRating = it },
                        tagPrefix = "shop_star"
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // 2. Driver Rating (every order with a driver, never Pick Up)
        if (hasDriver) {
            val personLabel = if (order.serviceType == ServiceType.TAXI) "driver" else "rider"
            val personName = order.riderName?.ifBlank { null } ?: order.rider?.name?.ifBlank { null } ?: "Assigned $personLabel"
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Rate the $personLabel",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = personName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    StarRatingRow(
                        rating = riderRating,
                        onRatingChanged = { riderRating = it },
                        tagPrefix = "driver_star"
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            // 3. Optional Tip for orders with a driver
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Add a tip for the driver",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountBalanceWallet,
                                contentDescription = null,
                                tint = YellowPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Wallet: ₱${String.format(Locale.US, "%.2f", walletBalance)}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Tip presets row: Not now, ₱10, ₱30, ₱60, Enter amount
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "0" to "Not now",
                            "10" to "₱10",
                            "30" to "₱30",
                            "60" to "₱60",
                            "custom" to "Other"
                        ).forEach { (key, label) ->
                            val isSelected = selectedTipPreset == key
                            val presetAmount = key.toDoubleOrNull() ?: 0.0
                            val isExceeded = key != "0" && key != "custom" && presetAmount > walletBalance

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) YellowPrimary else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) YellowPrimary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable(enabled = !isExceeded) {
                                        selectedTipPreset = key
                                    }
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) TextOnYellow else if (isExceeded) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    if (selectedTipPreset == "custom") {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = customTipInput,
                            onValueChange = {
                                if (it.all { ch -> ch.isDigit() || ch == '.' }) {
                                    customTipInput = it
                                }
                            },
                            label = { Text("Tip amount (₱1 - ₱500)") },
                            placeholder = { Text("e.g. 50") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (isTipExceedingBalance) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Insufficient wallet balance",
                            color = ErrorRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // 4. Optional Comment (max 300 chars)
        OutlinedTextField(
            value = comment,
            onValueChange = {
                if (it.length <= 300) {
                    comment = it
                }
            },
            label = { Text("Add a comment (optional)") },
            placeholder = { Text("Tell us what went well or what can be improved...") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("review_comment_input"),
            minLines = 2,
            maxLines = 4,
            supportingText = {
                Text(
                    text = "${comment.length}/300",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End,
                    fontSize = 11.sp
                )
            },
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Submit button
        Button(
            onClick = {
                if (canSubmit && !isSubmitting) {
                    onSubmit(
                        if (hasShop && merchantRating in 1..5) merchantRating else null,
                        if (hasDriver && riderRating in 1..5) riderRating else null,
                        comment.trim(),
                        if (hasDriver && !isTipExceedingBalance) resolvedTipAmount else 0.0
                    )
                }
            },
            enabled = canSubmit && !isSubmitting,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("submit_review_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = YellowPrimary,
                contentColor = TextOnYellow
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(
                    color = TextOnYellow,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text("Submit Review", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Skip for now button
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.testTag("skip_review_button")
        ) {
            Text(
                "Skip for now",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }
    }
}

/**
 * Read-only review component:
 * Stars for shop and driver, comment, tip given (lateTip), and note:
 * "Reviews and tips can only be done once"
 */
@Composable
fun ReadOnlyReviewView(
    review: ReviewDocument,
    lateTipAmount: Double = 0.0,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Your Review",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = YellowPrimary.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "Submitted",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Shop rating
            if (review.ratingMerchant != null && review.ratingMerchant > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Shop Rating", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        for (i in 1..5) {
                            Icon(
                                imageVector = if (i <= review.ratingMerchant) Icons.Filled.Star else Icons.Outlined.Star,
                                contentDescription = null,
                                tint = if (i <= review.ratingMerchant) YellowPrimary else Color.LightGray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Driver rating
            if (review.ratingRider != null && review.ratingRider > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Driver Rating", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        for (i in 1..5) {
                            Icon(
                                imageVector = if (i <= review.ratingRider) Icons.Filled.Star else Icons.Outlined.Star,
                                contentDescription = null,
                                tint = if (i <= review.ratingRider) YellowPrimary else Color.LightGray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Comment
            if (review.comment.isNotBlank()) {
                Text(
                    text = "\"${review.comment}\"",
                    fontSize = 13.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Tip given
            val finalTip = if (lateTipAmount > 0) lateTipAmount else 0.0
            if (finalTip > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Tip Given", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "₱${String.format(Locale.US, "%.2f", finalTip)} (Wallet)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E7D32)
                    )
                }
            }

            // Note: Reviews and tips can only be done once
            Text(
                text = "Reviews and tips can only be done once",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.outline,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun StarRatingRow(
    rating: Int,
    onRatingChanged: (Int) -> Unit,
    tagPrefix: String
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 1..5) {
            val isSelected = i <= rating
            IconButton(
                onClick = { onRatingChanged(i) },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("${tagPrefix}_$i")
            ) {
                Icon(
                    imageVector = if (isSelected) Icons.Filled.Star else Icons.Outlined.Star,
                    contentDescription = "$i Star",
                    tint = if (isSelected) YellowPrimary else Color.LightGray,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}
