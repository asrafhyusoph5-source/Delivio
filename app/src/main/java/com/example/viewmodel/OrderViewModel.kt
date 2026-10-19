package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Order
import com.example.data.model.PabiliDetails
import com.example.data.model.ParcelDetails
import com.example.data.repository.OrderRepository
import com.example.data.repository.TrackerDisplayState
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class OrderViewModel(
    private val orderRepository: OrderRepository
) : ViewModel() {

    val activeOrder: StateFlow<Order?> = orderRepository.activeOrder
    val activeOrders: StateFlow<List<Order>> = orderRepository.activeOrders
    val orderHistory: StateFlow<List<Order>> = orderRepository.orderHistory
    val trackerState: StateFlow<TrackerDisplayState> = orderRepository.trackerState
    val isMerchantCollapsed: StateFlow<Boolean> = orderRepository.isMerchantCollapsed
    val isRiderCollapsed: StateFlow<Boolean> = orderRepository.isRiderCollapsed
    val statusNotificationEvent: SharedFlow<String> = orderRepository.statusNotificationEvent
    val merchantCancellationAlert: StateFlow<com.example.data.repository.MerchantCancellationAlert?> = orderRepository.merchantCancellationAlert

    fun dismissMerchantCancellationAlert() {
        orderRepository.dismissMerchantCancellationAlert()
    }

    suspend fun getLatestOrder(orderId: String) = orderRepository.firestoreService.getLatestOrder(orderId)

    fun setCustomerId(customerId: String) {
        orderRepository.setCustomerId(customerId)
    }

    fun setTrackerState(state: TrackerDisplayState) {
        orderRepository.setTrackerState(state)
    }

    fun toggleMerchantCollapsed() {
        orderRepository.toggleMerchantCollapsed()
    }

    fun toggleRiderCollapsed() {
        orderRepository.toggleRiderCollapsed()
    }

    fun dismissActiveOrder() {
        orderRepository.dismissActiveOrder()
    }

    fun selectActiveOrder(orderId: String) {
        orderRepository.selectActiveOrder(orderId)
    }

    fun cancelOrder(
        orderId: String,
        reason: String = "Customer requested cancellation",
        onSuccess: () -> Unit = {},
        onCannotCancel: (message: String) -> Unit = {},
        onError: (errorMessage: String) -> Unit = {},
        onResult: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            val res = orderRepository.cancelOrder(orderId, reason)
            res.onSuccess {
                onResult(true, null)
                onSuccess()
            }.onFailure { e ->
                if (e is com.example.data.repository.OrderCannotBeCancelledException) {
                    val msg = if (e.reasonMessage == "PERMISSION_DENIED") {
                        val latest = orderRepository.firestoreService.getLatestOrder(orderId)
                        val isParcelOrPabili = latest?.serviceType?.lowercase() in listOf("parcel", "pabili") || latest?.merchantId.isNullOrEmpty()
                        if (isParcelOrPabili) {
                            "You can't cancel the order because the rider already accepted it."
                        } else {
                            "You can't cancel the order because the merchant already prepared the order."
                        }
                    } else {
                        e.reasonMessage
                    }
                    onResult(false, msg)
                    onCannotCancel(msg)
                } else {
                    val err = e.localizedMessage ?: e.message ?: "Failed to cancel order"
                    onResult(false, err)
                    onError(err)
                }
            }
        }
    }

    fun bookParcelOrder(
        customerId: String,
        customerName: String,
        customerPhone: String,
        parcelDetails: ParcelDetails,
        deliveryFee: Double,
        onComplete: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            val res = orderRepository.bookParcelOrder(
                customerId = customerId,
                customerName = customerName,
                customerPhone = customerPhone,
                parcelDetails = parcelDetails,
                deliveryFee = deliveryFee
            )
            onComplete(res.isSuccess, res.exceptionOrNull()?.message)
        }
    }

    fun bookPabiliOrder(
        customerId: String,
        customerName: String,
        customerPhone: String,
        pabiliDetails: PabiliDetails,
        itemTotal: Double,
        deliveryFee: Double,
        onComplete: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            val res = orderRepository.bookPabiliOrder(
                customerId = customerId,
                customerName = customerName,
                customerPhone = customerPhone,
                pabiliDetails = pabiliDetails,
                itemTotal = itemTotal,
                deliveryFee = deliveryFee
            )
            onComplete(res.isSuccess, res.exceptionOrNull()?.message)
        }
    }

    val repository: OrderRepository
        get() = orderRepository
}
