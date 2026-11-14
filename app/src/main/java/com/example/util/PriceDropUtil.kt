package com.example.util

import com.example.data.model.MenuItem
import com.example.data.model.MerchantItem
import kotlin.math.roundToInt

/**
 * Price drop helpers following the Delivio DATA CONTRACT:
 * - Item fields: price (regular), salePrice (number?), saleStartsAt (Long?), saleEndsAt (Long?), saleActive (Boolean).
 * - A sale is in effect when saleActive == true AND 0 < salePrice < price AND now inside [saleStartsAt, saleEndsAt].
 * - Discount percent is calculated, never stored: round((price - salePrice) / price * 100), formatted "-62%".
 */
object PriceDropUtil {

    fun isSaleInEffect(
        price: Double,
        salePrice: Double?,
        saleActive: Boolean,
        saleStartsAt: Long?,
        saleEndsAt: Long?,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (!saleActive) return false
        if (salePrice == null || salePrice <= 0.0 || salePrice >= price) return false
        if (saleStartsAt != null && nowMillis < saleStartsAt) return false
        if (saleEndsAt != null && nowMillis > saleEndsAt) return false
        return true
    }

    fun effectivePrice(
        price: Double,
        salePrice: Double?,
        saleActive: Boolean,
        saleStartsAt: Long?,
        saleEndsAt: Long?,
        nowMillis: Long = System.currentTimeMillis()
    ): Double {
        return if (isSaleInEffect(price, salePrice, saleActive, saleStartsAt, saleEndsAt, nowMillis)) {
            salePrice!!
        } else {
            price
        }
    }

    fun discountPercent(price: Double, salePrice: Double?): Int {
        if (salePrice == null || price <= 0.0 || salePrice <= 0.0 || salePrice >= price) return 0
        return (((price - salePrice) / price) * 100.0).roundToInt().coerceIn(1, 99)
    }

    fun formatDiscountBadge(price: Double, salePrice: Double?): String? {
        val pct = discountPercent(price, salePrice)
        return if (pct > 0) "-$pct%" else null
    }

    fun isSaleInEffect(item: MerchantItem, nowMillis: Long = System.currentTimeMillis()): Boolean =
        isSaleInEffect(item.price, item.salePrice, item.saleActive, item.saleStartsAt, item.saleEndsAt, nowMillis)

    fun effectivePrice(item: MerchantItem, nowMillis: Long = System.currentTimeMillis()): Double =
        effectivePrice(item.price, item.salePrice, item.saleActive, item.saleStartsAt, item.saleEndsAt, nowMillis)

    fun effectiveFromPrice(item: MerchantItem, nowMillis: Long = System.currentTimeMillis()): Double {
        if (VariationUtil.isVariationItem(item)) {
            return VariationUtil.getSummary(item, nowMillis).lowestEffectivePrice
        }
        val base = effectivePrice(item, nowMillis)
        if (!item.hasSets || item.sets.isEmpty()) return base
        val requiredAddons = item.sets.filter { it.required }.sumOf { it.cheapestChoicePrice }
        return base + requiredAddons
    }

    fun regularFromPrice(item: MerchantItem): Double {
        if (VariationUtil.isVariationItem(item)) {
            return VariationUtil.getSummary(item).lowestRegularPrice
        }
        if (!item.hasSets || item.sets.isEmpty()) return item.price
        val requiredAddons = item.sets.filter { it.required }.sumOf { it.cheapestChoicePrice }
        return item.price + requiredAddons
    }

    fun discountBadgeLabel(item: MerchantItem, nowMillis: Long = System.currentTimeMillis()): String? {
        if (VariationUtil.isVariationItem(item)) {
            return VariationUtil.getSummary(item, nowMillis).upToBadge
        }
        if (!isSaleInEffect(item, nowMillis)) return null
        return formatDiscountBadge(item.price, item.salePrice)
    }

    fun isSaleInEffect(item: MenuItem, nowMillis: Long = System.currentTimeMillis()): Boolean =
        isSaleInEffect(item.price, item.salePrice, item.saleActive, item.saleStartsAt, item.saleEndsAt, nowMillis)

    fun effectivePrice(item: MenuItem, nowMillis: Long = System.currentTimeMillis()): Double =
        effectivePrice(item.price, item.salePrice, item.saleActive, item.saleStartsAt, item.saleEndsAt, nowMillis)

    fun effectiveFromPrice(item: MenuItem, nowMillis: Long = System.currentTimeMillis()): Double {
        if (VariationUtil.isVariationItem(item)) {
            return VariationUtil.getSummary(item, nowMillis).lowestEffectivePrice
        }
        val base = effectivePrice(item, nowMillis)
        if (!item.hasSets || item.sets.isEmpty()) return base
        val requiredAddons = item.sets.filter { it.required }.sumOf { it.cheapestChoicePrice }
        return base + requiredAddons
    }

    fun regularFromPrice(item: MenuItem): Double {
        if (VariationUtil.isVariationItem(item)) {
            return VariationUtil.getSummary(item).lowestRegularPrice
        }
        if (!item.hasSets || item.sets.isEmpty()) return item.price
        val requiredAddons = item.sets.filter { it.required }.sumOf { it.cheapestChoicePrice }
        return item.price + requiredAddons
    }

    fun discountBadgeLabel(item: MenuItem, nowMillis: Long = System.currentTimeMillis()): String? {
        if (VariationUtil.isVariationItem(item)) {
            return VariationUtil.getSummary(item, nowMillis).upToBadge
        }
        if (!isSaleInEffect(item, nowMillis)) return null
        return formatDiscountBadge(item.price, item.salePrice)
    }
}
