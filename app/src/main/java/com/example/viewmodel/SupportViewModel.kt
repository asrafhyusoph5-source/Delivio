package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Order
import com.example.data.model.SupportReasons
import com.example.data.model.SupportTicket
import com.example.data.model.TicketMessage
import com.example.data.repository.SupportRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SupportViewModel(
    private val supportRepository: SupportRepository
) : ViewModel() {

    private var currentCustomerId: String = ""
    private var customerTicketsJob: Job? = null
    private var activeTicketJob: Job? = null
    private var activeMessagesJob: Job? = null

    private val _customerTickets = MutableStateFlow<List<SupportTicket>>(emptyList())
    val customerTickets: StateFlow<List<SupportTicket>> = _customerTickets.asStateFlow()

    private val _unresolvedTickets = MutableStateFlow<List<SupportTicket>>(emptyList())
    val unresolvedTickets: StateFlow<List<SupportTicket>> = _unresolvedTickets.asStateFlow()

    private val _activeTicketId = MutableStateFlow<String?>(null)
    val activeTicketId: StateFlow<String?> = _activeTicketId.asStateFlow()

    private val _activeTicket = MutableStateFlow<SupportTicket?>(null)
    val activeTicket: StateFlow<SupportTicket?> = _activeTicket.asStateFlow()

    private val _activeMessages = MutableStateFlow<List<TicketMessage>>(emptyList())
    val activeMessages: StateFlow<List<TicketMessage>> = _activeMessages.asStateFlow()

    private val _newAdminMessageBanner = MutableSharedFlow<String>(extraBufferCapacity = 5)
    val newAdminMessageBanner: SharedFlow<String> = _newAdminMessageBanner.asSharedFlow()

    // Dialog & Screen navigation state
    val selectedHelpOrder = MutableStateFlow<Order?>(null)
    val isReasonDialogOpen = MutableStateFlow(false)
    val selectedReason = MutableStateFlow<String?>(null)
    val isCreateTicketOpen = MutableStateFlow(false)
    val isChatScreenOpen = MutableStateFlow(false)
    val isReportsListOpen = MutableStateFlow(false)

    private val seenAdminMessageIds = mutableSetOf<String>()

    fun setCustomerId(uid: String) {
        if (uid.isEmpty() || uid == currentCustomerId) return
        currentCustomerId = uid
        customerTicketsJob?.cancel()

        customerTicketsJob = viewModelScope.launch {
            supportRepository.observeCustomerTickets(uid).collect { tickets ->
                _customerTickets.value = tickets
                _unresolvedTickets.value = tickets.filter { !it.status.equals("resolved", ignoreCase = true) }
            }
        }
    }

    fun openHelpForOrder(order: Order, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            // Rule: At most 3 unresolved reports per customer
            val count = supportRepository.getUnresolvedTicketCount(order.customerId.ifEmpty { currentCustomerId })
            if (count >= 3) {
                onError("You already have 3 unresolved support reports. Please wait for support to resolve them.")
                return@launch
            }

            // Rule: At most 1 unresolved report per order
            val existing = supportRepository.getUnresolvedTicketForOrder(order.id)
            if (existing != null) {
                openTicketChat(existing.id)
                return@launch
            }

            selectedHelpOrder.value = order
            selectedReason.value = null
            isReasonDialogOpen.value = true
        }
    }

    fun selectReason(reasonKey: String) {
        selectedReason.value = reasonKey
        isReasonDialogOpen.value = false
        isCreateTicketOpen.value = true
    }

    fun dismissReasonDialog() {
        isReasonDialogOpen.value = false
    }

    fun dismissCreateTicket() {
        isCreateTicketOpen.value = false
    }

    fun submitTicket(
        title: String,
        details: String,
        photosBase64: List<String>,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val trimmedTitle = title.trim()
        if (trimmedTitle.length < 3 || trimmedTitle.length > 80) {
            onError("Title must be between 3 and 80 characters")
            return
        }

        val order = selectedHelpOrder.value
        val reasonKey = selectedReason.value ?: "other"
        val reasonLabel = SupportReasons.getLabel(reasonKey)

        val ticket = SupportTicket(
            customerId = order?.customerId ?: currentCustomerId,
            customerName = order?.customerName ?: "Customer",
            orderId = order?.id ?: "",
            orderNumber = order?.orderNumber?.ifBlank { order.id.takeLast(6).uppercase() } ?: "",
            serviceType = order?.serviceType?.name?.lowercase() ?: "food",
            reason = reasonKey,
            reasonLabel = reasonLabel,
            title = trimmedTitle,
            status = "open"
        )

        viewModelScope.launch {
            val result = supportRepository.createTicket(ticket, details.trim(), photosBase64)
            result.onSuccess { ticketId ->
                isCreateTicketOpen.value = false
                openTicketChat(ticketId)
                onSuccess(ticketId)
            }.onFailure { err ->
                onError(err.message ?: "Failed to submit support report")
            }
        }
    }

    fun openTicketChat(ticketId: String) {
        _activeTicketId.value = ticketId
        isChatScreenOpen.value = true

        activeTicketJob?.cancel()
        activeTicketJob = viewModelScope.launch {
            supportRepository.observeTicket(ticketId).collect { t ->
                _activeTicket.value = t
            }
        }

        activeMessagesJob?.cancel()
        activeMessagesJob = viewModelScope.launch {
            supportRepository.observeTicketMessages(ticketId).collect { msgs ->
                // Check if new admin message arrived
                for (msg in msgs) {
                    if (msg.senderType.equals("admin", ignoreCase = true) && !seenAdminMessageIds.contains(msg.id)) {
                        seenAdminMessageIds.add(msg.id)
                        val text = msg.text.ifBlank { "Support sent an attachment" }
                        _newAdminMessageBanner.tryEmit("Support: $text")
                    }
                }
                _activeMessages.value = msgs
            }
        }
    }

    fun closeTicketChat() {
        // Safe exit without losing active ticket reference or ongoing listeners
        isChatScreenOpen.value = false
    }

    fun openReportsList() {
        isReportsListOpen.value = true
    }

    fun closeReportsList() {
        isReportsListOpen.value = false
    }

    fun sendMessage(
        text: String,
        imageData: String? = null,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val ticketId = _activeTicketId.value ?: return
        val currentTicket = _activeTicket.value
        if (currentTicket == null || !currentTicket.status.equals("accepted", ignoreCase = true)) {
            onError("Cannot reply until support accepts your report")
            return
        }

        if (text.isBlank() && imageData == null) {
            return
        }

        viewModelScope.launch {
            val result = supportRepository.sendMessage(
                ticketId = ticketId,
                customerId = currentCustomerId,
                text = text.trim(),
                imageData = imageData
            )
            result.onSuccess {
                onSuccess()
            }.onFailure { err ->
                onError(err.message ?: "Failed to send message")
            }
        }
    }
}
