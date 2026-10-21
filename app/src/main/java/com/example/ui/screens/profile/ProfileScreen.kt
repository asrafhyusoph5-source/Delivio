package com.example.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.foundation.BorderStroke
import android.widget.Toast
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import java.io.File
import androidx.compose.ui.platform.LocalContext
import com.example.data.model.OrderStatus
import com.example.util.DelivioStatusConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.datasource.MockData
import com.example.data.model.Address
import com.example.data.model.Order
import com.example.ui.components.SetManualAddressDialog
import com.example.ui.theme.BadgeOrange
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.TextOnYellow
import com.example.ui.theme.YellowPrimary
import com.example.util.AuthConstants
import com.example.util.Formatters
import com.example.viewmodel.AuthViewModel
import com.example.viewmodel.CartViewModel
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.OrderViewModel
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    authViewModel: AuthViewModel,
    mainViewModel: MainViewModel,
    orderViewModel: OrderViewModel,
    cartViewModel: CartViewModel,
    onLogout: () -> Unit,
    onNavigateToCart: () -> Unit,
    modifier: Modifier = Modifier
) {
    val authState by authViewModel.uiState.collectAsState()
    val isDarkMode by mainViewModel.isDarkMode.collectAsState()
    val orderHistory by orderViewModel.orderHistory.collectAsState()
    val currentAddress by mainViewModel.currentAddress.collectAsState()
    val savedAddresses by mainViewModel.savedAddresses.collectAsState()

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showPhotoOptionsDialog by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            authViewModel.updateProfilePicture(
                uri = uri,
                context = context,
                onSuccess = {
                    mainViewModel.showSnackbar("Profile picture updated! 📸")
                },
                onError = { err ->
                    mainViewModel.showSnackbar(err)
                }
            )
        }
    }

    if (showPhotoOptionsDialog) {
        val currentPhotoPath = authState.profilePicturePath
        val hasCurrentPhoto = !currentPhotoPath.isNullOrEmpty() && File(currentPhotoPath).exists()

        AlertDialog(
            onDismissRequest = { showPhotoOptionsDialog = false },
            title = {
                Text(
                    text = "Profile Picture",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Select a photo from your device to save as your profile picture.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPhotoOptionsDialog = false
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = YellowPrimary, contentColor = TextOnYellow),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("choose_photo_button")
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (hasCurrentPhoto) "Choose New Photo" else "Choose Photo")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (hasCurrentPhoto) {
                        TextButton(
                            onClick = {
                                showPhotoOptionsDialog = false
                                authViewModel.removeProfilePicture(
                                    onSuccess = {
                                        mainViewModel.showSnackbar("Profile picture removed")
                                    }
                                )
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = ErrorRed),
                            modifier = Modifier.testTag("remove_photo_button")
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Remove")
                        }
                    }
                    TextButton(
                        onClick = { showPhotoOptionsDialog = false }
                    ) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showManualAddressDialog by remember { mutableStateOf(false) }
    var editingAddress by remember { mutableStateOf<Address?>(null) }
    var selectedOrderDetail by remember { mutableStateOf<Order?>(null) }

    val user = authState.currentUser
    val userEmail = user?.email ?: AuthConstants.DEMO_EMAIL
    val userName = user?.name ?: AuthConstants.DEMO_USER_NAME
    val userPhone = user?.phone ?: AuthConstants.DEMO_USER_PHONE

    // Edit Profile Modal Bottom Sheet
    if (showEditProfileDialog && user != null) {
        EditProfileBottomSheet(
            currentUser = user,
            profilePicturePath = authState.profilePicturePath,
            onChangePhoto = {
                val photoPath = authState.profilePicturePath
                val hasAvatar = !photoPath.isNullOrEmpty() && File(photoPath).exists()
                if (hasAvatar) {
                    showPhotoOptionsDialog = true
                } else {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
            },
            onDismiss = { showEditProfileDialog = false },
            onSaveProfile = { name, phone, secondaryPhone, gender, birthday, deliveryNotes ->
                authViewModel.updateProfile(
                    name = name,
                    phone = phone,
                    secondaryPhone = secondaryPhone,
                    gender = gender,
                    birthday = birthday,
                    deliveryNotes = deliveryNotes,
                    onSuccess = {
                        mainViewModel.showSnackbar("Profile details updated successfully! ✨")
                    },
                    onError = { err ->
                        mainViewModel.showSnackbar(err)
                    }
                )
            }
        )
    }

    // Set / Add Manual Address Dialog
    if (showManualAddressDialog) {
        SetManualAddressDialog(
            initialTitle = if (savedAddresses.none { it.title.equals("Home", ignoreCase = true) }) "Home"
                           else if (savedAddresses.none { it.title.equals("Work", ignoreCase = true) }) "Work"
                           else "Location",
            initialAddress = "",
            initialDetails = "",
            initialLat = 9.7392,
            initialLng = 118.7353,
            isEditing = false,
            onDismiss = { showManualAddressDialog = false },
            onSaveAddress = { title, fullAddress, details, setAsActive, lat, lng ->
                mainViewModel.addManualAddress(title, fullAddress, details, setAsActive, lat, lng)
                showManualAddressDialog = false
            }
        )
    }

    // Edit Existing Address Dialog
    val currentEditing = editingAddress
    if (currentEditing != null) {
        SetManualAddressDialog(
            initialTitle = currentEditing.title,
            initialAddress = currentEditing.fullAddress,
            initialDetails = currentEditing.details,
            initialLat = currentEditing.lat,
            initialLng = currentEditing.lng,
            isEditing = true,
            onDismiss = { editingAddress = null },
            onSaveAddress = { title, fullAddress, details, setAsActive, lat, lng ->
                mainViewModel.updateAddress(
                    id = currentEditing.id,
                    title = title,
                    fullAddress = fullAddress,
                    details = details,
                    setAsActive = setAsActive,
                    lat = lat,
                    lng = lng
                )
                editingAddress = null
            }
        )
    }

    val currentSelectedOrder = selectedOrderDetail?.let { sel ->
        orderHistory.find { it.id == sel.id } ?: sel
    }
    if (currentSelectedOrder != null) {
        OrderDetailBottomSheet(
            order = currentSelectedOrder,
            orderViewModel = orderViewModel,
            onDismiss = { selectedOrderDetail = null },
            onReorder = {
                currentSelectedOrder.items.forEach { cartItem ->
                    cartViewModel.addItem(cartItem.menuItem, currentSelectedOrder.restaurant, cartItem.quantity)
                }
                mainViewModel.showSnackbar("Items from ${currentSelectedOrder.restaurant.name} added to cart!")
                selectedOrderDetail = null
                onNavigateToCart()
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // User Profile Header
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
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
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val currentAvatarPath = authState.profilePicturePath
                        val avatarFile = remember(currentAvatarPath) {
                            if (!currentAvatarPath.isNullOrEmpty()) File(currentAvatarPath) else null
                        }
                        val hasAvatar = avatarFile != null && avatarFile.exists() && avatarFile.length() > 0

                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .testTag("profile_avatar_container")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .align(Alignment.Center)
                                    .clip(CircleShape)
                                    .background(YellowPrimary)
                                    .clickable {
                                        if (hasAvatar) {
                                            showPhotoOptionsDialog = true
                                        } else {
                                            photoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (hasAvatar && avatarFile != null) {
                                    AsyncImage(
                                        model = avatarFile,
                                        contentDescription = "Profile Picture",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    val initials = userName.split(" ")
                                        .mapNotNull { it.firstOrNull()?.toString() }
                                        .take(2)
                                        .joinToString("")
                                        .ifEmpty { "CD" }
                                    Text(
                                        text = initials,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 22.sp,
                                        color = TextOnYellow
                                    )
                                }
                            }

                            // Camera edit badge at bottom corner
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .align(Alignment.BottomEnd)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .background(YellowPrimary)
                                        .clickable {
                                            if (hasAvatar) {
                                                showPhotoOptionsDialog = true
                                            } else {
                                                photoPickerLauncher.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                )
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CameraAlt,
                                        contentDescription = "Edit profile photo",
                                        tint = TextOnYellow,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = userName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            // Email with security badge (unchangeable)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = userEmail,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = YellowPrimary.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = user?.memberTier ?: "Gold Foodie Member",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = YellowPrimary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Contact & Personal Details Overview
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Phone, contentDescription = null, tint = YellowPrimary, modifier = Modifier.size(14.dp))
                                Text(
                                    text = userPhone,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            if (!user?.secondaryPhone.isNullOrBlank()) {
                                Text(
                                    text = "Backup: ${user?.secondaryPhone}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 20.dp)
                                )
                            }
                            if (!user?.deliveryNotes.isNullOrBlank()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(top = 2.dp)
                                ) {
                                    Icon(Icons.Default.Notes, contentDescription = null, tint = YellowPrimary, modifier = Modifier.size(14.dp))
                                    Text(
                                        text = user?.deliveryNotes ?: "",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        // EDIT PROFILE BUTTON
                        Button(
                            onClick = { showEditProfileDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = YellowPrimary,
                                contentColor = TextOnYellow
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .height(38.dp)
                                .testTag("edit_profile_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text("Edit Profile", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }



        // APP PREFERENCES & THEME TOGGLE
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Appearance",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (isDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                                contentDescription = null,
                                tint = YellowPrimary
                            )
                            Column {
                                Text(
                                    text = if (isDarkMode) "Dark Theme" else "Light Theme",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Toggle instant contrast mode",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Switch(
                            checked = isDarkMode,
                            onCheckedChange = { mainViewModel.toggleDarkMode(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = TextOnYellow,
                                checkedTrackColor = YellowPrimary
                            ),
                            modifier = Modifier.testTag("dark_mode_switch")
                        )
                    }
                }
            }
        }

        // SAVED ADDRESSES
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Saved Addresses (${if (savedAddresses.isEmpty()) "None" else "${savedAddresses.size}/3"})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )

                        // + SET MANUALLY BUTTON
                        Button(
                            onClick = {
                                if (savedAddresses.size >= 3) {
                                    mainViewModel.showSnackbar("Maximum of 3 saved addresses reached. Tap Edit on an address to modify it.")
                                } else {
                                    showManualAddressDialog = true
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (savedAddresses.size >= 3) MaterialTheme.colorScheme.surfaceVariant else YellowPrimary,
                                contentColor = if (savedAddresses.size >= 3) MaterialTheme.colorScheme.outline else TextOnYellow
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("set_address_manually_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(13.dp))
                                Text(
                                    text = if (savedAddresses.size >= 3) "Max (3/3)" else "+ Set Manually",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (savedAddresses.isEmpty()) {
                        // NONE STATE
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 18.dp, horizontal = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(46.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.LocationOff,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Text(
                                text = "None",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "No saved addresses yet. You can save up to 3 delivery addresses.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        // SAVED ADDRESSES LIST (UP TO 3, EDITABLE)
                        savedAddresses.forEachIndexed { index, address ->
                            val isSelected = address.id == currentAddress.id

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { mainViewModel.setAddress(address) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = if (isSelected) YellowPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = address.title,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 13.sp
                                        )
                                        if (isSelected) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = YellowPrimary
                                            ) {
                                                Text(
                                                    text = "Active",
                                                    color = TextOnYellow,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = address.fullAddress,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (address.details.isNotBlank()) {
                                        Text(
                                            text = address.details,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }

                                // EDIT BUTTON
                                IconButton(
                                    onClick = { editingAddress = address },
                                    modifier = Modifier
                                        .size(34.dp)
                                        .testTag("edit_address_${address.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit ${address.title}",
                                        tint = YellowPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // DELETE BUTTON
                                IconButton(
                                    onClick = { mainViewModel.deleteAddress(address.id) },
                                    modifier = Modifier
                                        .size(34.dp)
                                        .testTag("delete_address_${address.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Delete ${address.title}",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            if (index < savedAddresses.size - 1) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // SET / PIN ADDRESS MANUALLY BUTTON (OR MAX REACHED BANNER)
                    if (savedAddresses.size < 3) {
                        OutlinedButton(
                            onClick = { showManualAddressDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .testTag("set_pin_address_manually_button")
                        ) {
                            Icon(Icons.Default.AddLocationAlt, contentDescription = null, modifier = Modifier.size(16.dp), tint = YellowPrimary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (savedAddresses.isEmpty()) "📍 Set / Pin Address Manually" else "📍 Set / Pin Address Manually (${savedAddresses.size}/3)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = YellowPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Maximum of 3 addresses saved. Tap ✏️ to edit or 🗑️ to delete.",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // PAYMENT METHODS (MOCK)
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Payment Methods",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CreditCard,
                            contentDescription = null,
                            tint = YellowPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Visa ending in •••• 4242",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Expires 12/28 • Default",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("💵", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Cash on Delivery",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Available for orders under ₱50",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // ORDER HISTORY
        item {
            Text(
                text = "Recent Order History (${orderHistory.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (orderHistory.isEmpty()) {
            item {
                Text(
                    text = "No completed past orders yet.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(orderHistory) { pastOrder ->
                PastOrderCard(
                    order = pastOrder,
                    onClick = { selectedOrderDetail = pastOrder },
                    onReorder = {
                        // Re-add items to cart
                        pastOrder.items.forEach { cartItem ->
                            cartViewModel.addItem(cartItem.menuItem, pastOrder.restaurant, cartItem.quantity)
                        }
                        mainViewModel.showSnackbar("Items from ${pastOrder.restaurant.name} added to cart!")
                        onNavigateToCart()
                    }
                )
            }
        }

        // LOG OUT BUTTON
        item {
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = {
                    authViewModel.logout()
                    onLogout()
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = ErrorRed
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("logout_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ExitToApp,
                        contentDescription = "Log Out",
                        tint = ErrorRed
                    )
                    Text(
                        text = "Log Out",
                        fontWeight = FontWeight.Bold,
                        color = ErrorRed
                    )
                }
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun PastOrderCard(
    order: Order,
    onClick: () -> Unit,
    onReorder: () -> Unit
) {
    val isTerminalCancelled = order.status == OrderStatus.CANCELLED ||
            order.status == OrderStatus.REJECTED ||
            order.rawStatus.equals("Cancelled", ignoreCase = true) ||
            order.rawStatus.equals("Rejected", ignoreCase = true) ||
            order.rawStatus.equals("canceled", ignoreCase = true)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("past_order_card_${order.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Text(order.restaurant.bannerEmoji, fontSize = 20.sp)
                    Text(
                        text = order.restaurant.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }

                if (isTerminalCancelled) {
                    val label = if (order.cancelledByCustomer) "Cancelled by you" else "Cancelled by merchant"
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = ErrorRed.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ErrorRed,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                } else if (order.status == OrderStatus.DELIVERED || order.rawStatus.equals("Delivered", ignoreCase = true)) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF2E7D32).copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "Delivered",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = YellowPrimary.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = order.status.title,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = YellowPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            if (isTerminalCancelled) {
                val reason = order.rejectReason?.ifBlank { null } ?: order.cancelReason?.ifBlank { null }
                if (!reason.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Reason: $reason",
                        fontSize = 11.sp,
                        color = ErrorRed.copy(alpha = 0.85f),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "${order.items.joinToString(", ") { "${it.quantity}x ${it.menuItem.name}" }} • ${Formatters.formatPrice(order.total)}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = Formatters.formatDate(order.createdAt),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 2.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tap to view details",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
                Button(
                    onClick = onReorder,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = YellowPrimary,
                        contentColor = TextOnYellow
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Text("Reorder", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrderDetailBottomSheet(
    order: Order,
    orderViewModel: OrderViewModel,
    onDismiss: () -> Unit,
    onReorder: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showConfirmCancelDialog by remember { mutableStateOf(false) }
    var cancelReasonInput by remember { mutableStateOf("") }
    var cannotCancelMessage by remember { mutableStateOf<String?>(null) }
    var isCheckingCancelStatus by remember { mutableStateOf(false) }

    val isTerminal = DelivioStatusConstants.isTerminal(order.rawStatus) ||
            DelivioStatusConstants.isTerminal(order.status.title) ||
            order.status == OrderStatus.DELIVERED ||
            order.status == OrderStatus.CANCELLED ||
            order.status == OrderStatus.REJECTED

    val isTerminalCancelled = order.status == OrderStatus.CANCELLED ||
            order.status == OrderStatus.REJECTED ||
            order.rawStatus.equals("Cancelled", ignoreCase = true) ||
            order.rawStatus.equals("Rejected", ignoreCase = true) ||
            order.rawStatus.equals("canceled", ignoreCase = true)

    fun handleCancelClick() {
        isCheckingCancelStatus = true
        scope.launch {
            val latest = orderViewModel.getLatestOrder(order.id)
            isCheckingCancelStatus = false
            val freshStatus = latest?.status ?: order.rawStatus
            val isPlaced = freshStatus.equals("Placed Order", ignoreCase = true) ||
                    freshStatus.equals("placed", ignoreCase = true) ||
                    freshStatus.equals("pending", ignoreCase = true)

            val serviceType = (latest?.serviceType ?: order.serviceType.name).lowercase()
            val isParcelOrPabili = serviceType in listOf("parcel", "pabili") ||
                    (latest?.merchantId.isNullOrEmpty() && order.restaurant.id.startsWith("service_partner"))
            val riderAssigned = latest?.riderId != null || order.rider != null

            if (!isPlaced) {
                val msg = if (isParcelOrPabili && (riderAssigned || !isPlaced)) {
                    "You can't cancel the order because the rider already accepted it."
                } else {
                    "You can't cancel the order because the merchant already prepared the order."
                }
                cannotCancelMessage = msg
            } else {
                cancelReasonInput = ""
                showConfirmCancelDialog = true
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 700.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = order.restaurant.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Order #${order.id}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Status chip
            if (isTerminalCancelled) {
                val label = if (order.cancelledByCustomer) "Cancelled by you" else "Cancelled by merchant"
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = ErrorRed.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Cancel, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(18.dp))
                            Text(text = label, fontWeight = FontWeight.Bold, color = ErrorRed, fontSize = 14.sp)
                        }
                        val reason = order.rejectReason?.ifBlank { null } ?: order.cancelReason?.ifBlank { null }
                        if (!reason.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Reason: $reason",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = YellowPrimary.copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Status: ${order.status.title}",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Delivery Address
            Text(
                text = "Delivery Address",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Text(
                text = order.deliveryAddress.fullAddress,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Items List
            Text(
                text = "Items Ordered",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            order.items.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${item.quantity}x ${item.menuItem.name}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = Formatters.formatPrice(item.menuItem.price * item.quantity),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Pricing Breakdown
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Subtotal", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Formatters.formatPrice(order.subtotal), fontSize = 13.sp)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Delivery Fee", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Formatters.formatPrice(order.deliveryFee), fontSize = 13.sp)
            }
            if (order.serviceFee > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Service Fee", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Formatters.formatPrice(order.serviceFee), fontSize = 13.sp)
                }
            }
            if (order.discount > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Discount", fontSize = 13.sp, color = Color(0xFF2E7D32))
                    Text("-${Formatters.formatPrice(order.discount)}", fontSize = 13.sp, color = Color(0xFF2E7D32))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(Formatters.formatPrice(order.total), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = YellowPrimary)
            }

            Spacer(modifier = Modifier.height(20.dp))

            // CANCEL ORDER BUTTON (shown for every active order, hidden once Delivered, Rejected, or Cancelled)
            if (!isTerminal) {
                OutlinedButton(
                    onClick = { handleCancelClick() },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = ErrorRed
                    ),
                    border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.65f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_cancel_order_detail")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cancel,
                            contentDescription = "Cancel order",
                            tint = ErrorRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (isCheckingCancelStatus) "Checking status..." else "Cancel order",
                            fontWeight = FontWeight.Bold,
                            color = ErrorRed
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Reorder Button
            Button(
                onClick = onReorder,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = YellowPrimary,
                    contentColor = TextOnYellow
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Reorder Items", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // CONFIRM CANCEL DIALOG WITH OPTIONAL REASON
        if (showConfirmCancelDialog) {
            AlertDialog(
                onDismissRequest = { showConfirmCancelDialog = false },
                title = {
                    Text("Cancel order?", fontWeight = FontWeight.Bold)
                },
                text = {
                    Column {
                        Text(
                            text = "Are you sure you want to cancel this order?",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = cancelReasonInput,
                            onValueChange = { cancelReasonInput = it },
                            label = { Text("Reason (optional)") },
                            placeholder = { Text("e.g. Changed mind, placed by mistake") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("cancel_reason_input_detail")
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            orderViewModel.cancelOrder(
                                orderId = order.id,
                                reason = cancelReasonInput,
                                onSuccess = {
                                    showConfirmCancelDialog = false
                                    Toast.makeText(context, "Order cancelled", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                },
                                onCannotCancel = { msg ->
                                    showConfirmCancelDialog = false
                                    cannotCancelMessage = msg
                                },
                                onError = { err ->
                                    showConfirmCancelDialog = false
                                    Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                }
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ErrorRed,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.testTag("confirm_cancel_detail_button")
                    ) {
                        Text("Cancel order", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmCancelDialog = false }) {
                        Text("Keep Order")
                    }
                }
            )
        }

        // POPUP DIALOG WHEN ORDER CANNOT BE CANCELLED
        if (cannotCancelMessage != null) {
            AlertDialog(
                onDismissRequest = { cannotCancelMessage = null },
                title = {
                    Text("Cannot Cancel Order", fontWeight = FontWeight.Bold)
                },
                text = {
                    Text(
                        text = cannotCancelMessage!!,
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { cannotCancelMessage = null },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = YellowPrimary,
                            contentColor = TextOnYellow
                        ),
                        modifier = Modifier.testTag("cannot_cancel_detail_ok_button")
                    ) {
                        Text("OK", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditProfileBottomSheet(
    currentUser: com.example.data.model.User,
    profilePicturePath: String?,
    onChangePhoto: () -> Unit,
    onDismiss: () -> Unit,
    onSaveProfile: (name: String, phone: String, secondaryPhone: String, gender: String, birthday: String, deliveryNotes: String) -> Unit
) {
    var name by remember { mutableStateOf(currentUser.name) }
    var phone by remember { mutableStateOf(currentUser.phone) }
    var secondaryPhone by remember { mutableStateOf(currentUser.secondaryPhone) }
    var gender by remember { mutableStateOf(currentUser.gender) }
    var birthday by remember { mutableStateOf(currentUser.birthday) }
    var deliveryNotes by remember { mutableStateOf(currentUser.deliveryNotes) }
    val genderOptions = listOf("Male", "Female", "Other", "Prefer not to say")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
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
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(YellowPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = TextOnYellow,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Edit Profile Details",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Update your personal and contact details",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(20.dp))
                }
            }

            // Profile Picture Editor Card
            val avatarFile = remember(profilePicturePath) {
                if (!profilePicturePath.isNullOrEmpty()) File(profilePicturePath) else null
            }
            val hasAvatar = avatarFile != null && avatarFile.exists() && avatarFile.length() > 0

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(YellowPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        if (hasAvatar && avatarFile != null) {
                            AsyncImage(
                                model = avatarFile,
                                contentDescription = "Profile Picture",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            val initials = name.split(" ")
                                .mapNotNull { it.firstOrNull()?.toString() }
                                .take(2)
                                .joinToString("")
                                .ifEmpty { "CD" }
                            Text(
                                text = initials,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp,
                                color = TextOnYellow
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Profile Picture",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = if (hasAvatar) "Saved in local storage" else "Tap button to upload",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    OutlinedButton(
                        onClick = onChangePhoto,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("edit_profile_change_photo_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (hasAvatar) "Change" else "Upload", fontSize = 12.sp)
                    }
                }
            }

            // Name
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Full Name *") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = YellowPrimary) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("edit_profile_name_input")
            )

            // Email (Strictly Unchangeable / Disabled)
            OutlinedTextField(
                value = currentUser.email,
                onValueChange = {},
                enabled = false,
                label = { Text("Email Address (Unchangeable)") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.outline) },
                supportingText = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color(0xFFE53935),
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "Email address cannot be changed (account security identifier).",
                            color = Color(0xFFE53935),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("edit_profile_email_disabled")
            )

            // Phone
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Primary Phone Number *") },
                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = YellowPrimary) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("edit_profile_phone_input")
            )

            // Secondary Phone
            OutlinedTextField(
                value = secondaryPhone,
                onValueChange = { secondaryPhone = it },
                label = { Text("Secondary Contact Number (Optional)") },
                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = MaterialTheme.colorScheme.outline) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // Gender Chips
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Gender",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    genderOptions.forEach { opt ->
                        val isSel = gender.equals(opt, ignoreCase = true)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSel) YellowPrimary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { gender = opt }
                        ) {
                            Text(
                                text = opt,
                                fontSize = 11.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSel) TextOnYellow else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            // Birthday
            OutlinedTextField(
                value = birthday,
                onValueChange = { birthday = it },
                label = { Text("Birthday (YYYY-MM-DD)") },
                leadingIcon = { Icon(Icons.Default.Cake, contentDescription = null, tint = YellowPrimary) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // Default Delivery Notes / Landmark
            OutlinedTextField(
                value = deliveryNotes,
                onValueChange = { deliveryNotes = it },
                label = { Text("Default Delivery Instructions / Landmark") },
                leadingIcon = { Icon(Icons.Default.Notes, contentDescription = null, tint = YellowPrimary) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // Action Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = {
                        onSaveProfile(name, phone, secondaryPhone, gender, birthday, deliveryNotes)
                        onDismiss()
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = YellowPrimary,
                        contentColor = TextOnYellow
                    ),
                    modifier = Modifier
                        .weight(1.5f)
                        .height(48.dp)
                        .testTag("save_profile_button")
                ) {
                    Text("Save Changes", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

