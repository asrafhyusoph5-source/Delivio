package com.example.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ChatArgs
import com.example.data.model.ChatMessage
import com.example.data.model.OrderChatThread
import com.example.data.repository.ChatRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel(
    val chatRepository: ChatRepository = ChatRepository()
) : ViewModel() {

    private val _currentChatArgs = MutableStateFlow<ChatArgs?>(null)
    val currentChatArgs: StateFlow<ChatArgs?> = _currentChatArgs.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _currentThread = MutableStateFlow<OrderChatThread?>(null)
    val currentThread: StateFlow<OrderChatThread?> = _currentThread.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _isRateLimited = MutableStateFlow(false)
    val isRateLimited: StateFlow<Boolean> = _isRateLimited.asStateFlow()

    private val _previewPhoto = MutableStateFlow<Pair<Bitmap, String>?>(null)
    val previewPhoto: StateFlow<Pair<Bitmap, String>?> = _previewPhoto.asStateFlow()

    private val _photoSource = MutableStateFlow<String?>("gallery")
    val photoSource: StateFlow<String?> = _photoSource.asStateFlow()

    private val _fullScreenImage = MutableStateFlow<String?>(null)
    val fullScreenImage: StateFlow<String?> = _fullScreenImage.asStateFlow()

    private val _customerThreads = MutableStateFlow<List<OrderChatThread>>(emptyList())
    val customerThreads: StateFlow<List<OrderChatThread>> = _customerThreads.asStateFlow()

    private var messagesJob: Job? = null
    private var threadJob: Job? = null
    private var customerThreadsJob: Job? = null
    private var currentCustomerId: String = ""

    fun setCustomerId(customerId: String) {
        if (customerId == currentCustomerId && customerThreadsJob != null) return
        currentCustomerId = customerId
        customerThreadsJob?.cancel()
        if (customerId.isNotBlank()) {
            customerThreadsJob = viewModelScope.launch {
                // Background automatic cleanup of expired chats for signed-in user
                launch {
                    chatRepository.cleanupExpiredChatsForCustomer(customerId)
                }

                chatRepository.getCustomerThreadsFlow(customerId).collect { threads ->
                    _customerThreads.value = threads
                }
            }
        } else {
            _customerThreads.value = emptyList()
        }
    }

    fun isThreadUnread(chatId: String): Boolean {
        val t = _customerThreads.value.firstOrNull { it.chatId == chatId } ?: return false
        return t.isUnreadForCustomer()
    }

    fun openChat(args: ChatArgs) {
        _currentChatArgs.value = args
        _inputText.value = ""
        _previewPhoto.value = null

        messagesJob?.cancel()
        threadJob?.cancel()

        // Cleanup expired messages and thread when chat screen opens
        viewModelScope.launch {
            if (currentCustomerId.isNotBlank()) {
                chatRepository.cleanupExpiredChatsForCustomer(currentCustomerId)
            } else {
                chatRepository.cleanupSingleThreadIfExpired(args.chatId)
            }
        }

        // Mark as read immediately on open
        viewModelScope.launch {
            chatRepository.markAsRead(args.chatId)
        }

        // Collect thread
        threadJob = viewModelScope.launch {
            chatRepository.getThreadFlow(args.chatId).collect { thread ->
                _currentThread.value = thread
            }
        }

        // Collect messages
        messagesJob = viewModelScope.launch {
            chatRepository.getMessagesFlow(args.chatId).collect { msgs ->
                _messages.value = msgs
            }
        }
    }

    fun closeChat() {
        val chatId = _currentChatArgs.value?.chatId
        if (!chatId.isNullOrBlank()) {
            viewModelScope.launch {
                chatRepository.markAsRead(chatId)
            }
        }
        _currentChatArgs.value = null
        _messages.value = emptyList()
        _currentThread.value = null
        _previewPhoto.value = null
        _fullScreenImage.value = null
        messagesJob?.cancel()
        threadJob?.cancel()
    }

    fun onInputTextChanged(text: String) {
        // Enforce 1000-character limit
        _inputText.value = text.take(1000)
    }

    fun sendTextMessage() {
        val args = _currentChatArgs.value ?: return
        if (args.isOrderClosed || args.isDeletedAfter24h) return
        val text = _inputText.value.trim()
        if (text.isEmpty() || text.length > 1000) return
        if (_isSending.value || _isRateLimited.value) return

        _isSending.value = true
        _inputText.value = ""

        viewModelScope.launch {
            chatRepository.sendMessage(
                chatId = args.chatId,
                orderId = args.orderId,
                orderNumber = args.orderNumber,
                channel = args.channel,
                customerId = args.customerId,
                customerName = args.customerName,
                merchantId = args.merchantId,
                merchantName = args.merchantName,
                riderId = args.riderId,
                riderName = args.riderName,
                senderId = args.customerId,
                senderRole = "customer",
                type = "text",
                text = text
            )
            _isSending.value = false

            // Gentle one-second cooldown between messages
            _isRateLimited.value = true
            delay(1000L)
            _isRateLimited.value = false
        }
    }

    fun onPhotoSelected(bitmap: Bitmap, dataUri: String, source: String) {
        _previewPhoto.value = Pair(bitmap, dataUri)
        _photoSource.value = source
    }

    fun dismissPhotoPreview() {
        _previewPhoto.value = null
    }

    fun sendPhotoMessage(caption: String = "") {
        val args = _currentChatArgs.value ?: return
        if (args.isOrderClosed || args.isDeletedAfter24h) return
        val preview = _previewPhoto.value ?: return
        if (_isSending.value || _isRateLimited.value) return

        _isSending.value = true
        val dataUri = preview.second
        val source = _photoSource.value ?: "gallery"
        _previewPhoto.value = null

        viewModelScope.launch {
            chatRepository.sendMessage(
                chatId = args.chatId,
                orderId = args.orderId,
                orderNumber = args.orderNumber,
                channel = args.channel,
                customerId = args.customerId,
                customerName = args.customerName,
                merchantId = args.merchantId,
                merchantName = args.merchantName,
                riderId = args.riderId,
                riderName = args.riderName,
                senderId = args.customerId,
                senderRole = "customer",
                type = "image",
                text = caption.ifBlank { "Photo" }.take(1000),
                image = dataUri,
                source = source
            )
            _isSending.value = false

            // Gentle one-second cooldown between messages
            _isRateLimited.value = true
            delay(1000L)
            _isRateLimited.value = false
        }
    }

    fun openFullScreenImage(imageUriOrUrl: String) {
        _fullScreenImage.value = imageUriOrUrl
    }

    fun dismissFullScreenImage() {
        _fullScreenImage.value = null
    }
}
