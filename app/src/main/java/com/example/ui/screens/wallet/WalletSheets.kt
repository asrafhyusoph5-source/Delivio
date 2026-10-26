package com.example.ui.screens.wallet

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SendMoneyReceipt
import com.example.data.model.WalletDirectoryEntry
import com.example.data.model.WalletTransfer
import com.example.ui.theme.DarkGreenSuccess
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.TextOnYellow
import com.example.ui.theme.YellowPrimary
import com.example.util.Formatters
import com.example.viewmodel.WalletViewModel

// ----------------------------------------------------
// SEND MONEY SHEET
// ----------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendMoneyBottomSheet(
    walletViewModel: WalletViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sendQuery by walletViewModel.sendQuery.collectAsState()
    val isLookingUp by walletViewModel.isLookingUpRecipient.collectAsState()
    val recipientFound by walletViewModel.recipientFound.collectAsState()
    val lookupError by walletViewModel.sendLookupError.collectAsState()
    val sendAmount by walletViewModel.sendAmount.collectAsState()
    val sendNote by walletViewModel.sendNote.collectAsState()
    val isSending by walletViewModel.isSendingMoney.collectAsState()
    val sendError by walletViewModel.sendError.collectAsState()
    val showConfirmDialog by walletViewModel.showSendConfirmDialog.collectAsState()
    val currentUser by walletViewModel.currentUser.collectAsState()

    val balance = currentUser?.walletBalance ?: 0.0

    // Confirmation Dialog
    if (showConfirmDialog && recipientFound != null) {
        val amountVal = sendAmount.toDoubleOrNull() ?: 0.0
        AlertDialog(
            onDismissRequest = { walletViewModel.dismissSendConfirmDialog() },
            title = {
                Text(
                    text = "Confirm Transfer",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "Send ₱${Formatters.formatCurrency(amountVal)} to ${recipientFound!!.displayName}? This cannot be undone.",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { walletViewModel.executeSendMoney() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = YellowPrimary,
                        contentColor = TextOnYellow
                    ),
                    modifier = Modifier.testTag("btn_confirm_send_transfer")
                ) {
                    Text("Send Now", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { walletViewModel.dismissSendConfirmDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Send Money",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            // Available Balance
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = YellowPrimary.copy(alpha = 0.15f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Available Balance",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "₱${Formatters.formatCurrency(balance)}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Recipient Lookup Field
            Text(
                text = "Recipient",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = sendQuery,
                    onValueChange = { walletViewModel.onSendQueryChanged(it) },
                    placeholder = { Text("Mobile number or email", fontSize = 13.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(onSearch = { walletViewModel.lookupRecipient() }),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("send_recipient_query_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Button(
                    onClick = { walletViewModel.lookupRecipient() },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = YellowPrimary,
                        contentColor = TextOnYellow
                    ),
                    modifier = Modifier
                        .height(56.dp)
                        .testTag("btn_lookup_recipient")
                ) {
                    if (isLookingUp) {
                        CircularProgressIndicator(
                            color = TextOnYellow,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Find", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            // Lookup Error
            if (lookupError != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ErrorRed.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(18.dp))
                        Text(
                            text = lookupError!!,
                            color = ErrorRed,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Recipient Found Card
            if (recipientFound != null) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = DarkGreenSuccess.copy(alpha = 0.08f)
                    ),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DarkGreenSuccess.copy(alpha = 0.4f))),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = DarkGreenSuccess.copy(alpha = 0.2f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    tint = DarkGreenSuccess,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = recipientFound!!.displayName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (recipientFound!!.hint.isNotBlank()) {
                                Text(
                                    text = recipientFound!!.hint,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Verified",
                            tint = DarkGreenSuccess,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Amount Field
                Text(
                    text = "Amount (₱1.00 – ₱10,000.00)",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )

                OutlinedTextField(
                    value = sendAmount,
                    onValueChange = { walletViewModel.onSendAmountChanged(it) },
                    placeholder = { Text("0.00") },
                    prefix = { Text("₱ ", fontWeight = FontWeight.Bold) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("send_amount_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                // Quick Amount Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("50", "100", "200", "500").forEach { preset ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { walletViewModel.onSendAmountChanged(preset) }
                        ) {
                            Text(
                                text = "₱$preset",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }

                // Note Field (Optional)
                Text(
                    text = "Note (Optional)",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )

                OutlinedTextField(
                    value = sendNote,
                    onValueChange = { walletViewModel.onSendNoteChanged(it) },
                    placeholder = { Text("e.g. Lunch treat, splitting dinner") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("send_note_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                // Send Error (real error from server)
                if (sendError != null) {
                    Text(
                        text = sendError!!,
                        color = ErrorRed,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Submit Button
                Button(
                    onClick = { walletViewModel.requestSendConfirmation() },
                    enabled = !isSending,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = YellowPrimary,
                        contentColor = TextOnYellow
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("btn_send_money_submit")
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            color = TextOnYellow,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        val amt = sendAmount.toDoubleOrNull()
                        val amtLabel = if (amt != null && amt > 0) " ₱${Formatters.formatCurrency(amt)}" else ""
                        Text("Send$amtLabel", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------
// RECEIVE MONEY SHEET
// ----------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiveMoneyBottomSheet(
    walletViewModel: WalletViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val currentUser by walletViewModel.currentUser.collectAsState()
    val requestAmount by walletViewModel.receiveRequestAmount.collectAsState()
    val qrBitmap by walletViewModel.generatedQrBitmap.collectAsState()
    val recentIncoming by walletViewModel.recentIncomingTransfers.collectAsState()

    val user = currentUser
    val displayName = walletViewModel.walletRepository.formatDisplayName(user?.name)
    val email = user?.email ?: ""
    val phone = user?.phone ?: ""

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Receive Money",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            // QR Code Box
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                Box(
                    modifier = Modifier.padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap!!.asImageBitmap(),
                            contentDescription = "Delivio Pay QR Code",
                            modifier = Modifier
                                .size(220.dp)
                                .testTag("delivio_receive_qr_image")
                        )
                    } else {
                        Box(
                            modifier = Modifier.size(220.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = YellowPrimary)
                        }
                    }
                }
            }

            // Display Name
            Text(
                text = displayName,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Optional Request Amount
            OutlinedTextField(
                value = requestAmount,
                onValueChange = { walletViewModel.onReceiveAmountChanged(it) },
                label = { Text("Request specific amount (optional)") },
                prefix = { Text("₱ ", fontWeight = FontWeight.Bold) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("receive_request_amount_input")
            )

            // Contact Info with Copy Buttons
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (email.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Email", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(email, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Delivio Email", email))
                                    Toast.makeText(context, "Email copied", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy Email", modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    if (phone.isNotBlank()) {
                        Divider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Mobile Number", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(phone, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Delivio Phone", phone))
                                    Toast.makeText(context, "Phone copied", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy Phone", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            // Share QR Button
            OutlinedButton(
                onClick = {
                    val uid = currentUser?.uid ?: ""
                    val amt = requestAmount.toDoubleOrNull()
                    val payLink = "delivio://pay?uid=$uid" + if (amt != null && amt > 0) "&amount=$amt" else ""
                    val shareText = "Pay me via Delivio Wallet: $payLink\nName: $displayName"
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, shareText)
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "Share Delivio QR"))
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_share_qr")
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Share QR Code", fontWeight = FontWeight.Bold)
            }

            Divider(modifier = Modifier.padding(vertical = 4.dp))

            // Recent Incoming Transfers
            Text(
                text = "Recent Incoming Transfers",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier.align(Alignment.Start)
            )

            if (recentIncoming.isEmpty()) {
                Text(
                    text = "No incoming transfers yet",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    recentIncoming.take(5).forEach { transfer ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "From ${transfer.fromName}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = Formatters.formatTimestamp(transfer.createdAt),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (transfer.note.isNotBlank()) {
                                        Text(
                                            text = "\"${transfer.note}\"",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Text(
                                    text = "+₱${Formatters.formatCurrency(transfer.amount)}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = DarkGreenSuccess
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------
// SCAN PAY SHEET
// ----------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanPayBottomSheet(
    walletViewModel: WalletViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val recipient by walletViewModel.scanRecipient.collectAsState()
    val lockedAmount by walletViewModel.scanLockedAmount.collectAsState()
    val editableAmount by walletViewModel.scanEditableAmount.collectAsState()
    val note by walletViewModel.scanNote.collectAsState()
    val isPaying by walletViewModel.isScanPaying.collectAsState()
    val scanError by walletViewModel.scanError.collectAsState()
    val showConfirmDialog by walletViewModel.showScanConfirmDialog.collectAsState()
    val showManualDialog by walletViewModel.showManualCodeDialog.collectAsState()

    var manualInput by remember { mutableStateOf("") }

    // Manual Code Dialog
    if (showManualDialog) {
        AlertDialog(
            onDismissRequest = { walletViewModel.dismissManualCodeDialog() },
            title = { Text("Enter Delivio Code / ID", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = manualInput,
                    onValueChange = { manualInput = it },
                    label = { Text("User ID, mobile, or email") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manual_code_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = { walletViewModel.submitManualCode(manualInput) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = YellowPrimary,
                        contentColor = TextOnYellow
                    )
                ) {
                    Text("Submit", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { walletViewModel.dismissManualCodeDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Confirm Pay Dialog
    if (showConfirmDialog && recipient != null) {
        val finalAmount = lockedAmount ?: editableAmount.toDoubleOrNull() ?: 0.0
        AlertDialog(
            onDismissRequest = { walletViewModel.dismissScanConfirmDialog() },
            title = { Text("Confirm Payment", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Send ₱${Formatters.formatCurrency(finalAmount)} to ${recipient!!.displayName}? This cannot be undone.",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { walletViewModel.executeScanPay() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = YellowPrimary,
                        contentColor = TextOnYellow
                    ),
                    modifier = Modifier.testTag("btn_confirm_scan_pay")
                ) {
                    Text("Pay Now", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { walletViewModel.dismissScanConfirmDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Scan to Pay",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            if (recipient == null) {
                // Camera Scanner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    QrCodeScannerView(
                        onQrCodeDetected = { scannedCode ->
                            walletViewModel.onQrScanned(scannedCode)
                        }
                    )
                }

                // Error alert (e.g. "This is not a Delivio QR" or "Paying to banks and e-wallets is coming soon.")
                if (scanError != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ErrorRed.copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(18.dp))
                            Text(
                                text = scanError!!,
                                color = ErrorRed,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Manual Code Button
                OutlinedButton(
                    onClick = { walletViewModel.openManualCodeDialog() },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_enter_code_manually")
                ) {
                    Icon(Icons.Default.Keyboard, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Enter code manually", fontWeight = FontWeight.Bold)
                }
            } else {
                // Recipient Scanned & Ready to Pay
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
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = YellowPrimary,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "User",
                                    tint = TextOnYellow,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = recipient!!.displayName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            if (recipient!!.hint.isNotBlank()) {
                                Text(
                                    text = recipient!!.hint,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Amount
                Text(
                    text = if (lockedAmount != null) "Amount (Fixed by Merchant/User)" else "Enter Amount",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )

                OutlinedTextField(
                    value = if (lockedAmount != null) String.format(java.util.Locale.US, "%.2f", lockedAmount) else editableAmount,
                    onValueChange = { walletViewModel.onScanAmountChanged(it) },
                    readOnly = lockedAmount != null,
                    prefix = { Text("₱ ", fontWeight = FontWeight.Bold) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("scan_amount_input")
                )

                // Note
                Text("Note (Optional)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                OutlinedTextField(
                    value = note,
                    onValueChange = { walletViewModel.onScanNoteChanged(it) },
                    placeholder = { Text("Payment description") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("scan_note_input")
                )

                if (scanError != null) {
                    Text(
                        text = scanError!!,
                        color = ErrorRed,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Pay Button
                Button(
                    onClick = { walletViewModel.requestScanPayConfirmation() },
                    enabled = !isPaying,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = YellowPrimary,
                        contentColor = TextOnYellow
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("btn_submit_scan_pay")
                ) {
                    if (isPaying) {
                        CircularProgressIndicator(
                            color = TextOnYellow,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        val finalAmt = lockedAmount ?: editableAmount.toDoubleOrNull()
                        val amtText = if (finalAmt != null && finalAmt > 0) " ₱${Formatters.formatCurrency(finalAmt)}" else ""
                        Text("Pay$amtText", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------
// SUCCESS RECEIPT DIALOG
// ----------------------------------------------------

@Composable
fun ReceiptDialog(
    receipt: SendMoneyReceipt,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = null,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Success Icon
                Surface(
                    shape = CircleShape,
                    color = DarkGreenSuccess.copy(alpha = 0.15f),
                    modifier = Modifier.size(60.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = DarkGreenSuccess,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                Text(
                    text = "Transfer Successful!",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "₱${Formatters.formatCurrency(receipt.amount)}",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Divider(modifier = Modifier.padding(vertical = 4.dp))

                // Receipt Breakdown
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Sent To", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(receipt.toName, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Reference Code", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(receipt.referenceCode, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Date", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Formatters.formatTimestamp(receipt.timestamp), fontSize = 13.sp)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("New Balance", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("₱${Formatters.formatCurrency(receipt.newBalance)}", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                if (receipt.note.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Note", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(receipt.note, fontSize = 13.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = YellowPrimary,
                    contentColor = TextOnYellow
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("btn_receipt_done")
            ) {
                Text("Done", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = {
                    val shareText = """
                        Delivio Wallet Receipt
                        Reference: ${receipt.referenceCode}
                        Sent To: ${receipt.toName}
                        Amount: ₱${Formatters.formatCurrency(receipt.amount)}
                        Date: ${Formatters.formatTimestamp(receipt.timestamp)}
                    """.trimIndent()
                    val intent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, shareText)
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(intent, "Share Receipt"))
                },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("btn_share_receipt")
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Share")
            }
        }
    )
}

// ----------------------------------------------------
// COMING SOON DIALOG
// ----------------------------------------------------

@Composable
fun ComingSoonDialog(
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Coming Soon",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Text(
                text = message,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = YellowPrimary,
                    contentColor = TextOnYellow
                )
            ) {
                Text("OK", fontWeight = FontWeight.Bold)
            }
        }
    )
}
