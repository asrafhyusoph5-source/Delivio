package com.example.util

import com.example.data.model.CartItem
import com.example.data.model.ItemVariant
import com.example.data.model.ItemVariation
import com.example.data.model.MenuItem
import com.example.data.model.MerchantItem
import kotlin.math.abs

/**
 * Pure utility functions for Delivio convenience item variations following the VARIATIONS DATA CONTRACT:
 * - Item fields: variationsEnabled, variations (1..4), variants (1 per combination).
 * - Variant fields: key, optionIds (in variation order), label (e.g. "Black / 42"), price, stock, salePrice.
 * - Derived on item: price = min variant price, priceMax = max variant price, stockCount = total stock, inStock = total stock > 0.
 * - Price drops on variation items use variants[].salePrice with item's shared saleActive, saleStartsAt, saleEndsAt.
 */
object VariationUtil {

    data class VariationCardSummary(
        val isVariationItem: Boolean,
        val lowestEffectivePrice: Double,
        val lowestRegularPrice: Double,
        val minPrice: Double,
        val maxPrice: Double,
        val maxDiscountPercent: Int,
        val upToBadge: String?,
        val lowestIsOnSale: Boolean
    )

    fun isVariationItem(item: MerchantItem): Boolean =
        item.variationsEnabled && item.variations.isNotEmpty() && item.variants.isNotEmpty()

    fun isVariationItem(item: MenuItem): Boolean =
        item.variationsEnabled && item.variations.isNotEmpty() && item.variants.isNotEmpty()

    fun buildVariantKey(optionIds: List<String>): String =
        optionIds.joinToString("|")

    fun isVariantSaleInEffect(
        variant: ItemVariant,
        saleActive: Boolean,
        saleStartsAt: Long?,
        saleEndsAt: Long?,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean = PriceDropUtil.isSaleInEffect(
        price = variant.price,
        salePrice = variant.salePrice,
        saleActive = saleActive,
        saleStartsAt = saleStartsAt,
        saleEndsAt = saleEndsAt,
        nowMillis = nowMillis
    )

    fun isVariantSaleInEffect(
        variant: ItemVariant,
        item: MerchantItem,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean = isVariantSaleInEffect(variant, item.saleActive, item.saleStartsAt, item.saleEndsAt, nowMillis)

    fun isVariantSaleInEffect(
        variant: ItemVariant,
        item: MenuItem,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean = isVariantSaleInEffect(variant, item.saleActive, item.saleStartsAt, item.saleEndsAt, nowMillis)

    fun effectiveVariantPrice(
        variant: ItemVariant,
        saleActive: Boolean,
        saleStartsAt: Long?,
        saleEndsAt: Long?,
        nowMillis: Long = System.currentTimeMillis()
    ): Double = PriceDropUtil.effectivePrice(
        price = variant.price,
        salePrice = variant.salePrice,
        saleActive = saleActive,
        saleStartsAt = saleStartsAt,
        saleEndsAt = saleEndsAt,
        nowMillis = nowMillis
    )

    fun effectiveVariantPrice(
        variant: ItemVariant,
        item: MerchantItem,
        nowMillis: Long = System.currentTimeMillis()
    ): Double = effectiveVariantPrice(variant, item.saleActive, item.saleStartsAt, item.saleEndsAt, nowMillis)

    fun effectiveVariantPrice(
        variant: ItemVariant,
        item: MenuItem,
        nowMillis: Long = System.currentTimeMillis()
    ): Double = effectiveVariantPrice(variant, item.saleActive, item.saleStartsAt, item.saleEndsAt, nowMillis)

    fun variantDiscountBadge(
        variant: ItemVariant,
        saleActive: Boolean,
        saleStartsAt: Long?,
        saleEndsAt: Long?,
        nowMillis: Long = System.currentTimeMillis()
    ): String? {
        if (!isVariantSaleInEffect(variant, saleActive, saleStartsAt, saleEndsAt, nowMillis)) return null
        return PriceDropUtil.formatDiscountBadge(variant.price, variant.salePrice)
    }

    fun getSummary(
        variants: List<ItemVariant>,
        fallbackPrice: Double,
        saleActive: Boolean,
        saleStartsAt: Long?,
        saleEndsAt: Long?,
        nowMillis: Long = System.currentTimeMillis()
    ): VariationCardSummary {
        if (variants.isEmpty()) {
            return VariationCardSummary(
                isVariationItem = false,
                lowestEffectivePrice = fallbackPrice,
                lowestRegularPrice = fallbackPrice,
                minPrice = fallbackPrice,
                maxPrice = fallbackPrice,
                maxDiscountPercent = 0,
                upToBadge = null,
                lowestIsOnSale = false
            )
        }
        val purchasable = variants.filter { it.stock > 0 }
        val pool = if (purchasable.isNotEmpty()) purchasable else variants

        val lowestVariant = pool.minByOrNull {
            effectiveVariantPrice(it, saleActive, saleStartsAt, saleEndsAt, nowMillis)
        } ?: pool.first()
        val lowestEff = effectiveVariantPrice(lowestVariant, saleActive, saleStartsAt, saleEndsAt, nowMillis)
        val lowestReg = lowestVariant.price
        val lowestIsOnSale = isVariantSaleInEffect(lowestVariant, saleActive, saleStartsAt, saleEndsAt, nowMillis)

        val effectivePrices = pool.map { effectiveVariantPrice(it, saleActive, saleStartsAt, saleEndsAt, nowMillis) }
        val minPrice = effectivePrices.minOrNull() ?: lowestEff
        val maxPrice = effectivePrices.maxOrNull() ?: lowestEff

        val maxPct = pool.filter {
            isVariantSaleInEffect(it, saleActive, saleStartsAt, saleEndsAt, nowMillis)
        }.maxOfOrNull {
            PriceDropUtil.discountPercent(it.price, it.salePrice)
        } ?: 0

        return VariationCardSummary(
            isVariationItem = true,
            lowestEffectivePrice = lowestEff,
            lowestRegularPrice = lowestReg,
            minPrice = minPrice,
            maxPrice = maxPrice,
            maxDiscountPercent = maxPct,
            upToBadge = if (maxPct > 0) "up to -$maxPct%" else null,
            lowestIsOnSale = lowestIsOnSale
        )
    }

    fun getSummary(item: MerchantItem, nowMillis: Long = System.currentTimeMillis()): VariationCardSummary {
        if (!item.variationsEnabled || item.variants.isEmpty()) {
            val eff = PriceDropUtil.effectivePrice(item, nowMillis)
            val onSale = PriceDropUtil.isSaleInEffect(item, nowMillis)
            return VariationCardSummary(
                isVariationItem = false,
                lowestEffectivePrice = eff,
                lowestRegularPrice = item.price,
                minPrice = eff,
                maxPrice = eff,
                maxDiscountPercent = if (onSale) PriceDropUtil.discountPercent(item.price, item.salePrice) else 0,
                upToBadge = if (onSale) PriceDropUtil.discountBadgeLabel(item, nowMillis) else null,
                lowestIsOnSale = onSale
            )
        }
        return getSummary(item.variants, item.price, item.saleActive, item.saleStartsAt, item.saleEndsAt, nowMillis)
    }

    fun getSummary(item: MenuItem, nowMillis: Long = System.currentTimeMillis()): VariationCardSummary {
        if (!item.variationsEnabled || item.variants.isEmpty()) {
            val eff = PriceDropUtil.effectivePrice(item, nowMillis)
            val onSale = PriceDropUtil.isSaleInEffect(item, nowMillis)
            return VariationCardSummary(
                isVariationItem = false,
                lowestEffectivePrice = eff,
                lowestRegularPrice = item.price,
                minPrice = eff,
                maxPrice = eff,
                maxDiscountPercent = if (onSale) PriceDropUtil.discountPercent(item.price, item.salePrice) else 0,
                upToBadge = if (onSale) PriceDropUtil.discountBadgeLabel(item, nowMillis) else null,
                lowestIsOnSale = onSale
            )
        }
        return getSummary(item.variants, item.price, item.saleActive, item.saleStartsAt, item.saleEndsAt, nowMillis)
    }

    fun findCompleteVariant(
        variations: List<ItemVariation>,
        variants: List<ItemVariant>,
        selections: Map<String, String>
    ): ItemVariant? {
        if (variations.isEmpty() || variants.isEmpty()) return null
        val selectedOptionIds = variations.map { variation ->
            val optId = selections[variation.id]
            if (optId.isNullOrBlank()) return null
            optId
        }
        return variants.firstOrNull { variant ->
            if (variant.optionIds.size == variations.size) {
                variant.optionIds == selectedOptionIds
            } else {
                variant.key == buildVariantKey(selectedOptionIds)
            }
        }
    }

    fun matchingVariants(
        variations: List<ItemVariation>,
        variants: List<ItemVariant>,
        selections: Map<String, String>
    ): List<ItemVariant> {
        if (variations.isEmpty() || variants.isEmpty()) return emptyList()
        return variants.filter { variant ->
            variations.indices.all { idx ->
                val vId = variations[idx].id
                val sel = selections[vId]
                sel.isNullOrBlank() || variant.optionIds.getOrNull(idx) == sel
            }
        }
    }

    /**
     * True when NO variant containing this option at variationIndex has stock > 0.
     * UI shows permanently unavailable options greyed + disabled with strikethrough.
     */
    fun isOptionPermanentlyUnavailable(
        variants: List<ItemVariant>,
        variationIndex: Int,
        optionId: String
    ): Boolean {
        return variants.none { variant ->
            variant.stock > 0 && variant.optionIds.getOrNull(variationIndex) == optionId
        }
    }

    /**
     * True when at least one variant with stock > 0 matches this option AND all currently
     * selected options in OTHER variations.
     * When false, the option chip is greyed & disabled.
     */
    fun isOptionSelectable(
        variations: List<ItemVariation>,
        variants: List<ItemVariant>,
        variationIndex: Int,
        optionId: String,
        currentSelections: Map<String, String>
    ): Boolean {
        return variants.any { variant ->
            if (variant.stock <= 0) return@any false
            if (variant.optionIds.getOrNull(variationIndex) != optionId) return@any false
            variations.indices.all { otherIdx ->
                if (otherIdx == variationIndex) return@all true
                val otherSel = currentSelections[variations[otherIdx].id]
                otherSel.isNullOrBlank() || variant.optionIds.getOrNull(otherIdx) == otherSel
            }
        }
    }

    /**
     * Returns the display image URL: if Variation 1 (first variation) has a selected option
     * with a non-blank imageUrl, return it; otherwise fall back to itemImageUrl.
     */
    fun resolveDisplayImageUrl(
        itemImageUrl: String,
        variations: List<ItemVariation>,
        currentSelections: Map<String, String>
    ): String {
        val firstVariation = variations.firstOrNull()
        val selectedFirstOptId = firstVariation?.id?.let { currentSelections[it] }
        if (!selectedFirstOptId.isNullOrBlank()) {
            val opt = firstVariation.options.firstOrNull { it.id == selectedFirstOptId }
            if (opt != null && opt.imageUrl.isNotBlank()) {
                return opt.imageUrl.trim()
            }
        }
        return itemImageUrl.trim()
    }

    fun resolveVariantImageUrl(
        itemImageUrl: String,
        variations: List<ItemVariation>,
        variant: ItemVariant
    ): String {
        val firstVariation = variations.firstOrNull() ?: return itemImageUrl.trim()
        val firstOptId = variant.optionIds.firstOrNull() ?: return itemImageUrl.trim()
        val opt = firstVariation.options.firstOrNull { it.id == firstOptId }
        return if (opt != null && opt.imageUrl.isNotBlank()) opt.imageUrl.trim() else itemImageUrl.trim()
    }

    fun formatIncompletePriceRange(
        item: MenuItem,
        selections: Map<String, String>,
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        val matching = matchingVariants(item.variations, item.variants, selections)
        val inStockPool = matching.filter { it.stock > 0 }
        val pool = when {
            inStockPool.isNotEmpty() -> inStockPool
            matching.isNotEmpty() -> matching
            else -> item.variants
        }
        if (pool.isEmpty()) return Formatters.formatPrice(item.price)
        val prices = pool.map { effectiveVariantPrice(it, item, nowMillis) }
        val minP = prices.minOrNull() ?: item.price
        val maxP = prices.maxOrNull() ?: item.price
        return if (abs(maxP - minP) < 0.01) {
            Formatters.formatPrice(minP)
        } else {
            "${Formatters.formatPrice(minP)} - ${Formatters.formatPrice(maxP)}"
        }
    }

    fun missingVariationNames(
        variations: List<ItemVariation>,
        selections: Map<String, String>
    ): List<String> = variations
        .filter { selections[it.id].isNullOrBlank() }
        .map { it.name.ifBlank { "Option" } }

    fun firstMissingVariation(
        variations: List<ItemVariation>,
        selections: Map<String, String>
    ): ItemVariation? = variations.firstOrNull { selections[it.id].isNullOrBlank() }

    fun formatMissingHint(
        variations: List<ItemVariation>,
        selections: Map<String, String>
    ): String? {
        val missing = missingVariationNames(variations, selections)
        if (missing.isEmpty()) return null
        return "Select ${missing.joinToString(", ")}"
    }

    fun initialSelectionsForCartItem(item: MenuItem, cartItem: CartItem): Map<String, String> {
        val key = cartItem.variantKey
        if (!key.isNullOrBlank()) {
            val matched = item.variants.firstOrNull { it.key == key }
            if (matched != null && matched.optionIds.size == item.variations.size) {
                return item.variations.mapIndexed { idx, v -> v.id to matched.optionIds[idx] }.toMap()
            }
        }
        val label = cartItem.variantLabel
        if (!label.isNullOrBlank()) {
            val parts = label.split(" / ").map { it.trim() }
            if (parts.size == item.variations.size) {
                val map = mutableMapOf<String, String>()
                item.variations.forEachIndexed { idx, variation ->
                    val opt = variation.options.firstOrNull { it.name.equals(parts[idx], ignoreCase = true) }
                    if (opt != null) map[variation.id] = opt.id
                }
                if (map.size == item.variations.size) return map
            }
        }
        return emptyMap()
    }

    fun formatStockCountForPartial(
        variations: List<ItemVariation>,
        variants: List<ItemVariant>,
        selections: Map<String, String>
    ): Int {
        val matching = matchingVariants(variations, variants, selections)
        val pool = if (matching.isNotEmpty()) matching else variants
        return pool.sumOf { it.stock.coerceAtLeast(0) }
    }

    fun findVariantByKey(variants: List<ItemVariant>, variantKey: String?): ItemVariant? {
        if (variantKey.isNullOrBlank()) return null
        return variants.firstOrNull { it.key == variantKey }
    }
}
