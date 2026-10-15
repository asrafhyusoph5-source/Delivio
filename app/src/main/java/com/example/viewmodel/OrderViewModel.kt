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
    val orderHistory: StateFlow<List<Order>> = orderRepository.orderHistory
    val trackerState: StateFlow<TrackerDisplayState> = orderRepository.trackerState
    val isMerchantCollapsed: StateFlow<Boolean> = orderRepository.isMerchantCollapsed
    val isRiderCollapsed: StateFlow<Boolean> = orderRepository.isRiderCollapsed
    val statusNotificationEvent: SharedFlow<String> = orderRepository.statusNotificationEvent

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

    fun cancelOrder(orderId: String, reason: String = "Customer requested cancellation", onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            val res = orderRepository.cancelOrder(orderId, reason)
            res.onSuccess { onResult(true, null) }
                .onFailure { onResult(false, it.message) }
        }
    }

    fun bookParcelOrder(
        customerId: String,
        customerName: String,
        customerPhone: String,
        parcelDetails: ParcelDetails,
        deliveryFee: Double,
        onComplete: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            val res = orderRepository.bookParcelOrder(
                customerId = customerId,
                customerName = customerName,
                customerPhone = customerPhone,
                parcelDetails = parcelDetails,
                deliveryFee = deliveryFee
            )
            onComplete(res.isSuccess)
        }
    }

    fun bookPabiliOrder(
        customerId: String,
        customerName: String,
        customerPhone: String,
        pabiliDetails: PabiliDetails,
        itemTotal: Double,
        deliveryFee: Double,
        onComplete: (Boolean) -> Unit = {}
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
            onComplete(res.isSuccess)
        }
    }

    val repository: OrderRepository
        get() = orderRepository
}
