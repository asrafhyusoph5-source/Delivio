package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.TextOnYellow
import com.example.ui.theme.YellowPrimary
import com.example.viewmodel.AppTab

@Composable
fun DelivioBottomNavBar(
    selectedTab: AppTab,
    cartItemCount: Int,
    inboxUnreadCount: Int = 0,
    onTabSelected: (AppTab) -> Unit,
    modifier: Modifier = Modifier
) {
    // 5 tabs in exact order: Inbox, Wallet, Home, Cart, Profile
    val tabs = listOf(
        AppTab.INBOX,
        AppTab.WALLET,
        AppTab.HOME,
        AppTab.CART,
        AppTab.PROFILE
    )

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 10.dp,
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { tab ->
                val isSelected = tab == selectedTab
                val label = tab.title

                val icon: ImageVector = when (tab) {
                    AppTab.INBOX -> if (isSelected) Icons.Default.Email else Icons.Outlined.Email
                    AppTab.WALLET -> if (isSelected) Icons.Default.AccountBalanceWallet else Icons.Outlined.AccountBalanceWallet
                    AppTab.HOME -> if (isSelected) Icons.Default.Home else Icons.Outlined.Home
                    AppTab.CART -> if (isSelected) Icons.Default.ShoppingBag else Icons.Outlined.ShoppingBag
                    AppTab.PROFILE -> if (isSelected) Icons.Default.Person else Icons.Outlined.Person
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onTabSelected(tab)
                        }
                        .testTag("tab_${tab.route}"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Active Pill indicator
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSelected) YellowPrimary else Color.Transparent)
                                .padding(horizontal = 14.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (tab == AppTab.INBOX && inboxUnreadCount > 0) {
                                BadgedBox(
                                    badge = {
                                        Badge(
                                            containerColor = ErrorRed,
                                            contentColor = Color.White
                                        ) {
                                            Text(
                                                text = if (inboxUnreadCount > 99) "99+" else "$inboxUnreadCount",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = label,
                                        tint = if (isSelected) TextOnYellow else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            } else if (tab == AppTab.CART && cartItemCount > 0) {
                                BadgedBox(
                                    badge = {
                                        Badge(
                                            containerColor = if (isSelected) TextOnYellow else YellowPrimary,
                                            contentColor = if (isSelected) YellowPrimary else TextOnYellow
                                        ) {
                                            Text(
                                                text = if (cartItemCount > 99) "99+" else "$cartItemCount",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = label,
                                        tint = if (isSelected) TextOnYellow else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    tint = if (isSelected) TextOnYellow else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
