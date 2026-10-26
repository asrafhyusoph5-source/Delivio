package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.InboxMessage
import com.example.data.repository.InboxRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class InboxViewModel(
    private val inboxRepository: InboxRepository,
    private val authViewModel: AuthViewModel
) : ViewModel() {

    private val _selectedFilter = MutableStateFlow("All")
    val selectedFilter: StateFlow<String> = _selectedFilter.asStateFlow()

    private val _selectedMessageForDetail = MutableStateFlow<InboxMessage?>(null)
    val selectedMessageForDetail: StateFlow<InboxMessage?> = _selectedMessageForDetail.asStateFlow()

    private val _allMessages = MutableStateFlow<List<InboxMessage>>(emptyList())
    val allMessages: StateFlow<List<InboxMessage>> = _allMessages.asStateFlow()

    private val _readMessageIds = MutableStateFlow<Set<String>>(emptySet())
    val readMessageIds: StateFlow<Set<String>> = _readMessageIds.asStateFlow()

    private val _newMessageBanner = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val newMessageBanner: SharedFlow<String> = _newMessageBanner.asSharedFlow()

    private val seenMessageIds = mutableSetOf<String>()
    private var isFirstSnapshot = true

    val unreadCount: StateFlow<Int> = combine(_allMessages, _readMessageIds) { msgs, reads ->
        msgs.count { it.id !in reads }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val filteredMessages: StateFlow<List<InboxMessage>> = combine(
        _allMessages,
        _selectedFilter
    ) { msgs, filter ->
        when (filter) {
            "Promos" -> msgs.filter { it.category.equals("promo", ignoreCase = true) }
            "Orders" -> msgs.filter { it.category.equals("order", ignoreCase = true) }
            "Wallet" -> msgs.filter { it.category.equals("wallet", ignoreCase = true) }
            "Updates" -> msgs.filter {
                it.category.lowercase() in listOf("safety", "system", "admin", "update", "updates")
            }
            else -> msgs // "All"
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Observe current user UID and listen to merged inbox and read states
        viewModelScope.launch {
            authViewModel.uiState.collect { authState ->
                val uid = authState.currentUser?.uid ?: ""
                launch {
                    inboxRepository.getMergedInboxFlow(uid).collect { messages ->
                        if (isFirstSnapshot) {
                            seenMessageIds.addAll(messages.map { it.id })
                            isFirstSnapshot = false
                        } else {
                            // Find any brand new message arrived while app is open
                            for (msg in messages) {
                                if (msg.id !in seenMessageIds) {
                                    seenMessageIds.add(msg.id)
                                    _newMessageBanner.tryEmit("New message: ${msg.title}")
                                }
                            }
                        }
                        _allMessages.value = messages
                    }
                }

                launch {
                    inboxRepository.getReadMessageIdsFlow(uid).collect { readIds ->
                        _readMessageIds.value = readIds
                    }
                }
            }
        }
    }

    fun selectFilter(filter: String) {
        _selectedFilter.value = filter
    }

    fun openMessage(message: InboxMessage) {
        _selectedMessageForDetail.value = message
        val uid = authViewModel.uiState.value.currentUser?.uid ?: ""
        if (uid.isNotBlank() && message.id !in _readMessageIds.value) {
            viewModelScope.launch {
                inboxRepository.markMessageAsRead(uid, message.id)
            }
        }
    }

    fun closeDetail() {
        _selectedMessageForDetail.value = null
    }

    fun markAllAsRead() {
        val uid = authViewModel.uiState.value.currentUser?.uid ?: ""
        if (uid.isBlank()) return
        val unreadIds = _allMessages.value.filter { it.id !in _readMessageIds.value }.map { it.id }
        if (unreadIds.isEmpty()) return

        viewModelScope.launch {
            inboxRepository.markAllAsRead(uid, unreadIds)
        }
    }
}
