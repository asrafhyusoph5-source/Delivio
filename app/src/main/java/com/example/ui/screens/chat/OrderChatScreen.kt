package com.example.ui.screens.chat

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.data.model.ChatArgs
import com.example.data.model.ChatMessage
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextOnYellow
import com.example.ui.theme.YellowPrimary
import com.example.util.ChatImageUtil
import com.example.viewmodel.ChatViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun OrderChatScreen(
    chatViewModel: ChatViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val args by chatViewModel.currentChatArgs.collectAsState()
    val messages by chatViewModel.messages.collectAsState()
    val inputText by chatViewModel.inputText.collectAsState()
    val isSending by chatViewModel.isSending.collectAsState()
    val isRateLimited by chatViewModel.isRateLimited.collectAsState()
    val previewPhoto by chatViewModel.previewPhoto.collectAsState()
    val fullScreenImage by chatViewModel.fullScreenImage.collectAsState()

    BackHandler {
        chatViewModel.closeChat()
        onBack()
    }

    if (args == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No active conversation")
        }
        return
    }

    val currentArgs = args!!
    val listState = rememberLazyListState()

    // Auto-scroll to bottom on new messages
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Camera capture URI helper
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraUri != null) {
            val processed = ChatImageUtil.processImageUri(context, tempCameraUri!!)
            if (processed != null) {
                chatViewModel.onPhotoSelected(processed.first, processed.second, "camera")
            }
        }
    }

    // Gallery picker helper
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val processed = ChatImageUtil.processImageUri(context, uri)
            if (processed != null) {
                chatViewModel.onPhotoSelected(processed.first, processed.second, "gallery")
            }
        }
    }

    fun launchCamera() {
        try {
            val file = File(context.cacheDir, "camera_chat_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            tempCameraUri = uri
            cameraLauncher.launch(uri)
        } catch (_: Exception) {
            // Fallback to gallery if camera fails or fileprovider not ready
            galleryLauncher.launch("image/*")
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
        topBar = {
            ChatHeader(
                args = currentArgs,
                onBack = {
                    chatViewModel.closeChat()
                    onBack()
                }
            )
        },
        bottomBar = {
            if (currentArgs.isDeletedAfter24h) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "This conversation was deleted after 24 hours.",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else if (currentArgs.isOrderClosed) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "This chat is closed because the order is complete.",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                ChatInputBar(
                    text = inputText,
                    onTextChanged = { chatViewModel.onInputTextChanged(it) },
                    onSend = { chatViewModel.sendTextMessage() },
                    isSending = isSending,
                    isRateLimited = isRateLimited,
                    canSendImages = currentArgs.canSendImages,
                    onCameraClick = { launchCamera() },
                    onGalleryClick = { galleryLauncher.launch("image/*") }
                )
            }
        }
    ) { paddingValues ->
        if (currentArgs.isDeletedAfter24h) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "This conversation was deleted after 24 hours.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                var lastDateStr: String? = null

                items(messages, key = { it.id.ifBlank { "${it.createdAt}_${it.text.hashCode()}" } }) { msg ->
                    val dateStr = formatDaySeparator(msg.createdAt)
                    if (dateStr != null && dateStr != lastDateStr) {
                        lastDateStr = dateStr
                        DaySeparator(label = dateStr)
                    }

                    ChatMessageItem(
                        message = msg,
                        isMine = msg.senderRole.equals("customer", ignoreCase = true),
                        onImageClick = { url ->
                            chatViewModel.openFullScreenImage(url)
                        }
                    )
                }

                if (messages.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No messages yet.\nSay hello regarding order #${currentArgs.orderNumber}!",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    // Photo Preview Dialog before sending
    if (previewPhoto != null) {
        PhotoPreviewDialog(
            bitmap = previewPhoto!!.first,
            onDismiss = { chatViewModel.dismissPhotoPreview() },
            onSend = { caption ->
                chatViewModel.sendPhotoMessage(caption)
            },
            isSending = isSending
        )
    }

    // Full Screen Image Viewer
    if (fullScreenImage != null) {
        FullScreenImageViewer(
            imageUrl = fullScreenImage!!,
            onDismiss = { chatViewModel.dismissFullScreenImage() }
        )
    }
}

@Composable
private fun ChatHeader(
    args: ChatArgs,
    onBack: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("chat_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            // Role Icon Badge
            Surface(
                shape = CircleShape,
                color = if (args.channel == "merchant") YellowPrimary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (args.channel == "merchant") Icons.Default.Storefront else Icons.Default.DeliveryDining,
                        contentDescription = null,
                        tint = if (args.channel == "merchant") YellowPrimary else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = args.recipientName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = YellowPrimary.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = args.recipientRole,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = "Order #${args.orderNumber}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ChatMessageItem(
    message: ChatMessage,
    isMine: Boolean,
    onImageClick: (String) -> Unit
) {
    val bubbleColor = if (isMine) YellowPrimary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isMine) TextOnYellow else MaterialTheme.colorScheme.onSurfaceVariant
    val alignment = if (isMine) Alignment.End else Alignment.Start

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isMine) 16.dp else 4.dp,
                bottomEnd = if (isMine) 4.dp else 16.dp
            ),
            color = bubbleColor,
            shadowElevation = 1.dp,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                if (message.type == "image" && !message.image.isNullOrBlank()) {
                    AsyncImage(
                        model = message.image,
                        contentDescription = "Chat photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onImageClick(message.image) }
                    )
                    if (message.text.isNotBlank() && message.text != "Photo") {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = message.text,
                            color = textColor,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    Text(
                        text = message.text,
                        color = textColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }
        }

        // Time stamp
        if (message.createdAt != null) {
            val sdf = SimpleDateFormat("h:mm a", Locale.US)
            Text(
                text = sdf.format(Date(message.createdAt)),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun DaySeparator(label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

private fun formatDaySeparator(timestamp: Long?): String? {
    if (timestamp == null || timestamp <= 0L) return null
    val msgCal = Calendar.getInstance().apply { timeInMillis = timestamp }
    val nowCal = Calendar.getInstance()

    return when {
        msgCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR) &&
                msgCal.get(Calendar.DAY_OF_YEAR) == nowCal.get(Calendar.DAY_OF_YEAR) -> "Today"
        msgCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR) &&
                msgCal.get(Calendar.DAY_OF_YEAR) == nowCal.get(Calendar.DAY_OF_YEAR) - 1 -> "Yesterday"
        else -> SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(timestamp))
    }
}

@Composable
private fun ChatInputBar(
    text: String,
    onTextChanged: (String) -> Unit,
    onSend: () -> Unit,
    isSending: Boolean,
    isRateLimited: Boolean,
    canSendImages: Boolean,
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // DRIVER CHAT ONLY: camera and gallery buttons
                if (canSendImages) {
                    IconButton(
                        onClick = onCameraClick,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("chat_camera_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Take photo",
                            tint = YellowPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    IconButton(
                        onClick = onGalleryClick,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("chat_gallery_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = "Choose from gallery",
                            tint = YellowPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChanged,
                    placeholder = { Text("Type a message...", fontSize = 14.sp) },
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = YellowPrimary,
                        cursorColor = YellowPrimary
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (!isSending && !isRateLimited && text.isNotBlank()) onSend()
                    }),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_input_field")
                )

                val canSend = text.isNotBlank() && !isSending && !isRateLimited
                IconButton(
                    onClick = onSend,
                    enabled = canSend,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (canSend) YellowPrimary else MaterialTheme.colorScheme.surfaceVariant)
                        .testTag("chat_send_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (canSend) TextOnYellow else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (text.length >= 900) {
                Text(
                    text = "${text.length}/1000",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(end = 16.dp, top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun PhotoPreviewDialog(
    bitmap: Bitmap,
    onDismiss: () -> Unit,
    onSend: (String) -> Unit,
    isSending: Boolean
) {
    var caption by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Send Photo", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Photo preview",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(12.dp))
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it.take(1000) },
                    placeholder = { Text("Add a caption (optional)...") },
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = YellowPrimary,
                        cursorColor = YellowPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSend(caption) },
                enabled = !isSending,
                colors = ButtonDefaults.buttonColors(
                    containerColor = YellowPrimary,
                    contentColor = TextOnYellow
                ),
                modifier = Modifier.testTag("confirm_send_photo_button")
            ) {
                Text(if (isSending) "Sending..." else "Send", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun FullScreenImageViewer(
    imageUrl: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color.Black)
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = "Full-screen photo",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(16.dp)
                    .align(Alignment.TopEnd)
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f), CircleShape)
                    .size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = androidx.compose.ui.graphics.Color.White
                )
            }
        }
    }
}
