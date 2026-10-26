package com.example.data.model

import com.google.firebase.Timestamp

data class InboxLink(
    val merchantId: String? = null,
    val category: String? = null
)

data class InboxMessage(
    val id: String = "",
    val customerId: String? = null,
    val audience: String? = null,
    val title: String = "",
    val body: String = "",
    val category: String = "system", // promo, order, wallet, safety, system, admin
    val imageUrl: String? = null,
    val link: InboxLink? = null,
    val createdAt: Timestamp? = null
)
