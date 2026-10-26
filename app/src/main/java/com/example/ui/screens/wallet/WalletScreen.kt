package com.example.ui.screens.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AddCard
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.WalletActivityCategory
import com.example.data.model.WalletActivityItem
import com.example.ui.theme.DarkGreenSuccess
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.TextOnYellow
import com.example.ui.theme.YellowPrimary
import com.example.util.Formatters
import com.example.viewmodel.WalletViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(
    walletViewModel: WalletViewModel,
    modifier: Modifier = Modifier
) {
    val currentUser by walletViewModel.currentUser.collectAsState()
    val selectedFilter by walletViewModel.selectedFilter.collectAsState()
    val filteredActivity by walletViewModel.filteredActivityItems.collectAsState()

    val showSendSheet by walletViewModel.showSendSheet.collectAsState()
    val showReceiveSheet by walletViewModel.showReceiveSheet.collectAsState()
    val showScanSheet by walletViewModel.showScanSheet.collectAsState()
    val showComingSoonDialog by walletViewModel.showComingSoonDialog.collectAsState()
    val comingSoonMessage by walletViewModel.comingSoonMessage.collectAsState()
    val lastReceipt by walletViewModel.lastReceipt.collectAsState()

    val balance = currentUser?.walletBalance ?: 0.0
    val userName = currentUser?.name?.ifEmpty { "Delivio Customer" } ?: "Delivio Customer"

    // Dialogs & Sheets
    if (showComingSoonDialog) {
        ComingSoonDialog(
            message = comingSoonMessage,
            onDismiss = { walletViewModel.dismissComingSoon() }
        )
    }

    if (showSendSheet) {
        SendMoneyBottomSheet(
            walletViewModel = walletViewModel,
            onDismiss = { walletViewModel.dismissSendSheet() }
        )
    }

    if (showReceiveSheet) {
        ReceiveMoneyBottomSheet(
            walletViewModel = walletViewModel,
            onDismiss = { walletViewModel.dismissReceiveSheet() }
        )
    }

    if (showScanSheet) {
        ScanPayBottomSheet(
            walletViewModel = walletViewModel,
            onDismiss = { walletViewModel.dismissScanSheet() }
        )
    }

    if (lastReceipt != null) {
        ReceiptDialog(
            receipt = lastReceipt!!,
            onDismiss = { walletViewModel.dismissReceiptDialog() }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Wallet",
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. YELLOW BALANCE CARD
            item {
                DelivioWalletBalanceCard(
                    customerName = userName,
                    balance = balance,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("delivio_wallet_balance_card")
                )
            }

            // 2. ACTION ROW: Top Up, Scan, Transfer, Send, Receive
            item {
                WalletActionRow(
                    onTopUp = { walletViewModel.openComingSoon("Top Up") },
                    onScan = { walletViewModel.openScanSheet() },
                    onTransfer = { walletViewModel.openComingSoon("Transfer") },
                    onSend = { walletViewModel.openSendSheet() },
                    onReceive = { walletViewModel.openReceiveSheet() },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 3. TRANSACTION HISTORY TITLE & FILTER CHIPS
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Transaction History",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Filter Chips: All, Sent, Received, Payments, Top-ups, Refunds
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        WalletActivityCategory.values().forEach { category ->
                            val isSelected = category == selectedFilter
                            FilterChip(
                                selected = isSelected,
                                onClick = { walletViewModel.setFilter(category) },
                                label = {
                                    Text(
                                        text = category.title,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 12.sp
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = YellowPrimary,
                                    selectedLabelColor = TextOnYellow
                                ),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.testTag("filter_chip_${category.name.lowercase()}")
                            )
                        }
                    }
                }
            }

            // 4. TRANSACTION HISTORY LIST
            if (filteredActivity.isEmpty()) {
                item {
                    EmptyWalletActivityCard()
                }
            } else {
                items(filteredActivity, key = { it.id }) { item ->
                    WalletActivityRow(item = item)
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

// ----------------------------------------------------
// BALANCE CARD
// ----------------------------------------------------

@Composable
fun DelivioWalletBalanceCard(
    customerName: String,
    balance: Double,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = YellowPrimary
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
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
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.12f),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.AccountBalanceWallet,
                                contentDescription = "Wallet",
                                tint = TextOnYellow,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Text(
                        text = "Delivio Wallet",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        color = TextOnYellow
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.08f)
                ) {
                    Text(
                        text = "Instant Pay",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = TextOnYellow,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Column {
                Text(
                    text = "Current Balance",
                    fontSize = 12.sp,
                    color = TextOnYellow.copy(alpha = 0.8f)
                )
                Text(
                    text = "₱${Formatters.formatCurrency(balance)}",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextOnYellow
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = customerName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextOnYellow.copy(alpha = 0.9f)
                )
                Text(
                    text = "Verified Customer",
                    fontSize = 11.sp,
                    color = TextOnYellow.copy(alpha = 0.7f)
                )
            }
        }
    }
}

// ----------------------------------------------------
// ACTION ROW
// ----------------------------------------------------

@Composable
fun WalletActionRow(
    onTopUp: () -> Unit,
    onScan: () -> Unit,
    onTransfer: () -> Unit,
    onSend: () -> Unit,
    onReceive: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.Top
        ) {
            WalletActionButton(
                icon = Icons.Default.AddCard,
                label = "Top Up",
                badgeText = "Soon",
                onClick = onTopUp,
                testTag = "wallet_action_top_up"
            )

            WalletActionButton(
                icon = Icons.Default.QrCodeScanner,
                label = "Scan",
                onClick = onScan,
                testTag = "wallet_action_scan"
            )

            WalletActionButton(
                icon = Icons.Default.SwapHoriz,
                label = "Transfer",
                badgeText = "Soon",
                onClick = onTransfer,
                testTag = "wallet_action_transfer"
            )

            WalletActionButton(
                icon = Icons.AutoMirrored.Filled.Send,
                label = "Send",
                onClick = onSend,
                testTag = "wallet_action_send"
            )

            WalletActionButton(
                icon = Icons.Default.CallReceived,
                label = "Receive",
                onClick = onReceive,
                testTag = "wallet_action_receive"
            )
        }
    }
}

@Composable
fun WalletActionButton(
    icon: ImageVector,
    label: String,
    badgeText: String? = null,
    onClick: () -> Unit,
    testTag: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp)
            .testTag(testTag)
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Surface(
                shape = CircleShape,
                color = YellowPrimary.copy(alpha = 0.16f),
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            if (badgeText != null) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = YellowPrimary,
                    modifier = Modifier.padding(top = 0.dp)
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextOnYellow,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ----------------------------------------------------
// ACTIVITY ROW & EMPTY STATE
// ----------------------------------------------------

@Composable
fun WalletActivityRow(item: WalletActivityItem) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("wallet_activity_row_${item.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Category Icon
            val isPositive = item.signedAmount >= 0
            val iconBg = if (isPositive) DarkGreenSuccess.copy(alpha = 0.15f) else ErrorRed.copy(alpha = 0.12f)
            val iconTint = if (isPositive) DarkGreenSuccess else ErrorRed

            val iconVector: ImageVector = when (item.category) {
                WalletActivityCategory.SENT -> Icons.AutoMirrored.Filled.Send
                WalletActivityCategory.RECEIVED -> Icons.Default.CallReceived
                WalletActivityCategory.PAYMENTS -> Icons.Default.ShoppingBag
                WalletActivityCategory.TOP_UPS -> Icons.Default.AddCard
                WalletActivityCategory.REFUNDS -> Icons.Default.Refresh
                else -> Icons.Default.ReceiptLong
            }

            Surface(
                shape = CircleShape,
                color = iconBg,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = iconVector,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = Formatters.formatTimestamp(item.timestamp),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (item.note.isNotBlank()) {
                    Text(
                        text = item.note,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Signed Amount
            val amountPrefix = if (isPositive) "+₱" else "-₱"
            val absAmount = kotlin.math.abs(item.signedAmount)
            Text(
                text = "$amountPrefix${Formatters.formatCurrency(absAmount)}",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = if (isPositive) DarkGreenSuccess else ErrorRed
            )
        }
    }
}

@Composable
fun EmptyWalletActivityCard() {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Text(
                text = "No wallet activity yet",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
