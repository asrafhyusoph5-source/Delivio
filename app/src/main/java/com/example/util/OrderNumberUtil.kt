package com.example.util

import java.security.SecureRandom

object OrderNumberUtil {
    // 6 random characters (A to Z and 2 to 9, no O, 0, I, or 1), for example "K7PM2X"
    private const val CHARSET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private val random = SecureRandom()

    fun generateOrderNumber(): String {
        val chars = CharArray(6)
        for (i in 0 until 6) {
            chars[i] = CHARSET[random.nextInt(CHARSET.length)]
        }
        return String(chars)
    }

    fun formatOrderNumber(orderNumber: String?, orderId: String?): String {
        val num = orderNumber?.trim().takeIf { !it.isNullOrBlank() }
            ?: orderId?.takeLast(6)?.uppercase()?.takeIf { it.isNotBlank() }
            ?: "------"
        return "#$num"
    }
}
