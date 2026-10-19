package com.example.ui.components

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.data.model.MerchantItem
import com.example.data.model.Restaurant
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextOnYellow
import com.example.ui.theme.YellowPrimary

/**
 * Image URL input field with a live preview below it.
 * Explicitly displays status: Loading, OK, or Failed.
 * Saves the URL as typed, and NEVER clears the URL if the image fails to load.
 */
@Composable
fun ImageUrlLivePreviewField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "https://...",
    testTag: String = "image_url_field"
) {
    val trimmed = value.trim()
    val context = LocalContext.current

    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(context)
            .data(trimmed.ifEmpty { null })
            .crossfade(true)
            .build()
    )
    val painterState = painter.state

    // Log errors on failure
    LaunchedEffect(painterState) {
        if (painterState is AsyncImagePainter.State.Error) {
            val err = painterState.result.throwable
            Log.e("ImageLoad", "Failed to load image preview from URL: '$trimmed': ${err.message}", err)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            singleLine = true,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = null,
                    tint = YellowPrimary
                )
            },
            trailingIcon = {
                if (value.isNotEmpty()) {
                    IconButton(onClick = { onValueChange("") }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Clear")
                    }
                }
            },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = YellowPrimary,
                focusedLabelColor = YellowPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag)
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Live Preview Box and Status
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                // Status Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Live Preview",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Status Indicator: Loading / OK / Failed / Empty
                    when {
                        trimmed.isEmpty() -> {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = "No URL",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        painterState is AsyncImagePainter.State.Loading -> {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = YellowPrimary.copy(alpha = 0.2f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(10.dp),
                                        strokeWidth = 2.dp,
                                        color = YellowPrimary
                                    )
                                    Text(
                                        text = "Loading",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = YellowPrimary
                                    )
                                }
                            }
                        }
                        painterState is AsyncImagePainter.State.Success -> {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = SuccessGreen.copy(alpha = 0.2f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "OK",
                                        tint = SuccessGreen,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "OK",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SuccessGreen
                                    )
                                }
                            }
                        }
                        painterState is AsyncImagePainter.State.Error -> {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = ErrorRed.copy(alpha = 0.15f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Error,
                                        contentDescription = "Failed",
                                        tint = ErrorRed,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "Failed",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ErrorRed
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Preview Container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(95.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(
                            1.dp,
                            when {
                                painterState is AsyncImagePainter.State.Success -> SuccessGreen.copy(alpha = 0.4f)
                                painterState is AsyncImagePainter.State.Error -> ErrorRed.copy(alpha = 0.4f)
                                else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            },
                            RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        trimmed.isEmpty() -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Preview will appear here once typed",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                        painterState is AsyncImagePainter.State.Loading -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                                color = YellowPrimary
                            )
                        }
                        painterState is AsyncImagePainter.State.Success -> {
                            Image(
                                painter = painter,
                                contentDescription = "Live Preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().height(95.dp)
                            )
                        }
                        painterState is AsyncImagePainter.State.Error -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BrokenImage,
                                    contentDescription = "Image unavailable",
                                    tint = ErrorRed,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = "Image unavailable (URL preserved)",
                                    fontSize = 11.sp,
                                    color = ErrorRed
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dialog for editing Merchant Shop Profile (name, address, logoUrl, bannerUrl, imageUrl).
 */
@Composable
fun MerchantShopProfileDialog(
    restaurant: Restaurant,
    onDismiss: () -> Unit,
    onSave: (name: String, address: String, logoUrl: String, bannerUrl: String, imageUrl: String) -> Unit
) {
    var name by remember { mutableStateOf(restaurant.name) }
    var address by remember { mutableStateOf(restaurant.address) }
    var logoUrl by remember { mutableStateOf(restaurant.logoUrl) }
    var bannerUrl by remember { mutableStateOf(restaurant.bannerUrl) }
    var imageUrl by remember { mutableStateOf(restaurant.imageUrl) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storefront,
                            contentDescription = null,
                            tint = YellowPrimary
                        )
                        Text(
                            text = "Shop Profile",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Text(
                    text = "Update your storefront profile. Images will be live previewed with Coil.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Shop / Merchant Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = YellowPrimary,
                        focusedLabelColor = YellowPrimary
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("shop_name_input")
                )

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Store Address") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = YellowPrimary,
                        focusedLabelColor = YellowPrimary
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("shop_address_input")
                )

                // Logo URL Field with live preview
                ImageUrlLivePreviewField(
                    label = "Logo URL (merchants.logoUrl)",
                    value = logoUrl,
                    onValueChange = { logoUrl = it },
                    testTag = "shop_logo_url_input"
                )

                // Banner URL Field with live preview
                ImageUrlLivePreviewField(
                    label = "Banner URL (merchants.bannerUrl)",
                    value = bannerUrl,
                    onValueChange = { bannerUrl = it },
                    testTag = "shop_banner_url_input"
                )

                // Image URL Field with live preview
                ImageUrlLivePreviewField(
                    label = "Image URL (merchants.imageUrl)",
                    value = imageUrl,
                    onValueChange = { imageUrl = it },
                    testTag = "shop_image_url_input"
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel")
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Button(
                        onClick = {
                            onSave(name.trim(), address.trim(), logoUrl.trim(), bannerUrl.trim(), imageUrl.trim())
                            onDismiss()
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = YellowPrimary,
                            contentColor = TextOnYellow
                        ),
                        modifier = Modifier.testTag("save_shop_profile_button")
                    ) {
                        Text("Save Shop Profile", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Dialog for adding or editing a Menu Item ("Merchant menu form").
 */
@Composable
fun MerchantMenuFormDialog(
    initialItem: MerchantItem? = null,
    merchantId: String,
    onDismiss: () -> Unit,
    onSave: (MerchantItem) -> Unit
) {
    var name by remember { mutableStateOf(initialItem?.name ?: "") }
    var priceText by remember { mutableStateOf(initialItem?.let { if (it.price > 0) it.price.toString() else "" } ?: "") }
    var description by remember { mutableStateOf(initialItem?.description ?: "") }
    var categoryId by remember { mutableStateOf(initialItem?.categoryId ?: "cat-all") }
    var imageUrl by remember { mutableStateOf(initialItem?.imageUrl ?: "") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (initialItem != null) "Edit Menu Item" else "Add Menu Item",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )

                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Text(
                    text = "Fill in menu item details and specify the Image URL.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Item Name *") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = YellowPrimary,
                        focusedLabelColor = YellowPrimary
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("menu_item_name_input")
                )

                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text("Price (PHP) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = YellowPrimary,
                        focusedLabelColor = YellowPrimary
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("menu_item_price_input")
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = YellowPrimary,
                        focusedLabelColor = YellowPrimary
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("menu_item_desc_input")
                )

                // Menu Item Image URL with Live Preview & Status: Loading, OK, Failed
                ImageUrlLivePreviewField(
                    label = "Image URL (items.imageUrl)",
                    value = imageUrl,
                    onValueChange = { imageUrl = it },
                    testTag = "menu_item_image_url_input"
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel")
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Button(
                        onClick = {
                            val price = priceText.toDoubleOrNull() ?: 0.0
                            val item = MerchantItem(
                                id = initialItem?.id ?: "item-${System.currentTimeMillis()}",
                                categoryId = categoryId,
                                name = name.trim(),
                                price = price,
                                description = description.trim(),
                                imageUrl = imageUrl.trim(),
                                isAvailable = true
                            )
                            onSave(item)
                            onDismiss()
                        },
                        enabled = name.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = YellowPrimary,
                            contentColor = TextOnYellow
                        ),
                        modifier = Modifier.testTag("save_menu_item_button")
                    ) {
                        Text("Save Item", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
