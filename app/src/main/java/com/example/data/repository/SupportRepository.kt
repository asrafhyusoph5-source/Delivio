package com.example.data.repository

import com.example.data.model.SupportTicket
import com.example.data.model.TicketMessage
import kotlinx.coroutines.flow.Flow

class SupportRepository(
    private val firestoreService: FirestoreService
) {
    suspend fun getUnresolvedTicketCount(customerId: String): Int {
        return firestoreService.getUnresolvedTicketCountForCustomer(customerId)
    }

    suspend fun getUnresolvedTicketForOrder(orderId: String): SupportTicket? {
        return firestoreService.getUnresolvedTicketForOrder(orderId)
    }

    suspend fun createTicket(
        ticket: SupportTicket,
        details: String,
        photosBase64: List<String>
    ): Result<String> {
        return firestoreService.createSupportTicketBatch(ticket, details, photosBase64)
    }

    fun observeTicket(ticketId: String): Flow<SupportTicket?> {
        return firestoreService.observeTicket(ticketId)
    }

    fun observeTicketMessages(ticketId: String): Flow<List<TicketMessage>> {
        return firestoreService.observeTicketMessages(ticketId)
    }

    fun observeCustomerTickets(customerId: String): Flow<List<SupportTicket>> {
        return firestoreService.observeCustomerTickets(customerId)
    }

    suspend fun sendMessage(
        ticketId: String,
        customerId: String,
        text: String,
        imageData: String? = null
    ): Result<Unit> {
        return firestoreService.sendTicketMessage(ticketId, customerId, text, imageData)
    }
}
