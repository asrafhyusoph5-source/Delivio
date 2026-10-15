package com.example.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import kotlinx.coroutines.launch
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PabiliDetails
import com.example.data.model.PabiliItem
import com.example.data.repository.OrderRepository
import com.example.ui.components.InteractiveLocationPickerMap
import com.example.ui.theme.TextOnYellow
import com.example.ui.theme.YellowPrimary
import com.example.util.AuthConstants
import com.example.util.Formatters

@Composable
fun PabiliBookingView(
    orderRepository: OrderRepository,
    cityId: String = "puerto-princesa",
    onOrderBooked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    // Store
    var storeName by remember { mutableStateOf("Nearest Pharmacy / Grocery") }
    var storeAddress by remember { mutableStateOf("Any store near my area") }

    // Items list
    val items = remember {
        mutableStateListOf(
            PabiliItem(name = "Paracetamol 500mg (10 tabs)", quantity = 1, estimatedPrice = 4.50),
            PabiliItem(name = "Vitamin C Effervescent", quantity = 1, estimatedPrice = 6.00)
        )
    }

    // Recipient & Delivery "Where to"
    var recipientName by remember { mutableStateOf(AuthConstants.DEMO_USER_NAME) }
    var recipientPhone by remember { mutableStateOf(AuthConstants.DEMO_USER_PHONE) }
    var deliveryAddress by remember { mutableStateOf("742 Evergreen Terrace, Springfield") }
    var deliveryLat by remember { mutableFloatStateOf(0.75f) }
    var deliveryLng by remember { mutableFloatStateOf(0.70f) }
    var riderNotes by remember { mutableStateOf("Please ask for receipt and check expiry date") }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    val estimatedItemsTotal = items.sumOf { it.estimatedPrice * it.quantity }
    val pabiliServiceFee = 4.50
    val convenienceFee = 1.50
    val totalEstimated = estimatedItemsTotal + pabiliServiceFee + convenienceFee

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Banner Header
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = YellowPrimary.copy(alpha = 0.15f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(YellowPrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🛍️", fontSize = 24.sp)
                }
                Column {
                    Text(
                        text = "Pabili Service (Buy For Me)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Assign a rider to purchase medicine, groceries, or essentials and deliver to you",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // STORE DETAILS
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(YellowPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("1", color = TextOnYellow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = "Store or Merchant to Buy From",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedTextField(
                    value = storeName,
                    onValueChange = { storeName = it },
                    label = { Text("Store / Merchant Name") },
                    leadingIcon = { Icon(Icons.Default.Store, contentDescription = null, tint = YellowPrimary) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("pabili_store_name")
                )

                OutlinedTextField(
                    value = storeAddress,
                    onValueChange = { storeAddress = it },
                    label = { Text("Store Location / Area (e.g. Near Downtown)") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("pabili_store_address")
                )
            }
        }

        // LIST OF ITEMS TO BUY
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(YellowPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("2", color = TextOnYellow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = "List of Items to Buy (${items.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                items.forEachIndexed { index, item ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Item #${index + 1}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = YellowPrimary
                                )
                                if (items.size > 1) {
                                    IconButton(
                                        onClick = { items.removeAt(index) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete item",
                                            tint = Color.Red.copy(alpha = 0.8f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = item.name,
                                onValueChange = { newName -> items[index] = item.copy(name = newName) },
                                label = { Text("Item Name & Brand") },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Qty stepper
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surface)
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    IconButton(
                                        onClick = { if (item.quantity > 1) items[index] = item.copy(quantity = item.quantity - 1) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(14.dp))
                                    }
                                    Text(
                                        text = "${item.quantity}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp)
                                    )
                                    IconButton(
                                        onClick = { items[index] = item.copy(quantity = item.quantity + 1) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(14.dp))
                                    }
                                }

                                // Estimated price field
                                OutlinedTextField(
                                    value = if (item.estimatedPrice > 0) String.format("%.2f", item.estimatedPrice) else "",
                                    onValueChange = { priceStr ->
                                        val parsed = priceStr.toDoubleOrNull() ?: 0.0
                                        items[index] = item.copy(estimatedPrice = parsed)
                                    },
                                    label = { Text("Est. Price ($)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = { items.add(PabiliItem(name = "", quantity = 1, estimatedPrice = 0.0)) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("add_pabili_item_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Another Item", fontWeight = FontWeight.Bold)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Total Estimated Items:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Formatters.formatPrice(estimatedItemsTotal), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }

        // STEP 3: RECIPIENT & "WHERE TO" DESTINATION MAP
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF43A047)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("3", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = "Delivery Destination (Where to)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedTextField(
                    value = recipientName,
                    onValueChange = { recipientName = it },
                    label = { Text("Recipient Name") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = YellowPrimary) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("pabili_recipient_name")
                )

                OutlinedTextField(
                    value = recipientPhone,
                    onValueChange = { recipientPhone = it },
                    label = { Text("Recipient Contact Number") },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = YellowPrimary) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("pabili_recipient_phone")
                )

                OutlinedTextField(
                    value = deliveryAddress,
                    onValueChange = { deliveryAddress = it },
                    label = { Text("Delivery Street Address") },
                    leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFF43A047)) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("pabili_delivery_address")
                )

                Text(
                    text = "Map of \"Where to\" (Dropoff Pin)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Interactive map of "where to"
                InteractiveLocationPickerMap(
                    title = "Destination Pin (Where to)",
                    initialAddress = deliveryAddress,
                    pinColor = Color(0xFF43A047),
                    onLocationSelected = { newAddress, x, y ->
                        deliveryAddress = newAddress
                        deliveryLat = x
                        deliveryLng = y
                    }
                )

                OutlinedTextField(
                    value = riderNotes,
                    onValueChange = { riderNotes = it },
                    label = { Text("Instructions for Rider") },
                    leadingIcon = { Icon(Icons.Default.EditNote, contentDescription = null, tint = YellowPrimary) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // FARE & BOOKING
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Estimated Order Summary",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Items Estimate", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    Text(Formatters.formatPrice(estimatedItemsTotal), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Rider Pabili Service Fee", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    Text(Formatters.formatPrice(pabiliServiceFee), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Processing & App Fee", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    Text(Formatters.formatPrice(convenienceFee), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Estimated Total", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(text = Formatters.formatPrice(totalEstimated), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = YellowPrimary)
                }

                if (errorMessage != null) {
                    Text(text = errorMessage!!, color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }

                Spacer(modifier = Modifier.height(6.dp))

                Button(
                    onClick = {
                        val validItems = items.filter { it.name.isNotBlank() }
                        if (validItems.isEmpty()) {
                            errorMessage = "Please enter at least one item to purchase"
                            return@Button
                        }
                        if (recipientName.isBlank() || recipientPhone.isBlank()) {
                            errorMessage = "Please enter recipient name and contact number"
                            return@Button
                        }
                        errorMessage = null

                        val pabili = PabiliDetails(
                            storeName = storeName.ifBlank { "Nearest Mart" },
                            storeAddress = storeAddress.ifBlank { "Nearby Store" },
                            items = validItems,
                            recipientName = recipientName.trim(),
                            recipientPhone = recipientPhone.trim(),
                            deliveryAddress = deliveryAddress.trim(),
                            deliveryLat = deliveryLat,
                            deliveryLng = deliveryLng,
                            riderNotes = riderNotes.trim()
                        )

                        coroutineScope.launch {
                            val result = orderRepository.bookPabiliOrder(
                                customerId = "cust-1",
                                customerName = recipientName.trim(),
                                customerPhone = recipientPhone.trim(),
                                pabiliDetails = pabili,
                                itemTotal = estimatedItemsTotal,
                                deliveryFee = pabiliServiceFee,
                                serviceFee = convenienceFee,
                                cityId = cityId
                            )
                            if (result.isSuccess) {
                                onOrderBooked()
                            } else {
                                errorMessage = result.exceptionOrNull()?.message ?: "Failed to book pabili errand"
                            }
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = YellowPrimary,
                        contentColor = TextOnYellow
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("book_pabili_order_button")
                ) {
                    Text(
                        text = "Book Pabili Rider • ${Formatters.formatPrice(totalEstimated)}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}
