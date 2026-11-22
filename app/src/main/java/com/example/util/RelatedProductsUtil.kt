package com.example.util

import com.example.data.model.MenuItem
import com.example.data.model.MerchantItem
import com.example.data.model.MerchantMenu
import java.util.Locale
import kotlin.math.abs

data class RecommendedProduct(
    val item: MerchantItem,
    val isOrderable: Boolean,
    val tier: Int,
    val effectiveSoldCount: Int
)

object RelatedProductsUtil {

    private val IGNORED_WORDS = setOf(
        "with", "and", "the", "for", "from", "per",
        "pc", "pcs", "pack", "packs", "pkt", "piece", "pieces",
        "regular", "reg", "large", "lrg", "small", "sml", "medium", "med",
        "size", "sizes", "mini", "jumbo", "xxl", "xxxl",
        "gram", "grams", "kilogram", "kilograms",
        "liter", "liters", "litre", "litres",
        "milliliter", "milliliters", "millilitre", "millilitres",
        "ounce", "ounces"
    )

    private val TOKEN_SPLIT_REGEX = Regex("[^a-z0-9]+")

    fun extractKeywords(name: String, tags: List<String> = emptyList()): Set<String> {
        val keywords = LinkedHashSet<String>()

        if (name.isNotBlank()) {
            name.lowercase(Locale.ROOT)
                .split(TOKEN_SPLIT_REGEX)
                .forEach { token ->
                    if (token.length >= 3 &&
                        token.none { it.isDigit() } &&
                        !IGNORED_WORDS.contains(token)
                    ) {
                        keywords.add(token)
                    }
                }
        }

        tags.forEach { rawTag ->
            val normTag = rawTag.trim().lowercase(Locale.ROOT)
            if (normTag.isNotBlank() &&
                normTag.any { it.isLetter() } &&
                !IGNORED_WORDS.contains(normTag)
            ) {
                keywords.add(normTag)
            }
        }

        return keywords
    }

    fun buildRecommendations(
        currentItem: MenuItem,
        shopItems: List<MerchantItem>,
        shopMenus: List<MerchantMenu> = emptyList(),
        soldCountMap: Map<String, Int> = emptyMap(),
        timezone: String = "Asia/Manila",
        nowMillis: Long = System.currentTimeMillis(),
        maxCount: Int = 12
    ): List<RecommendedProduct> {
        if (shopItems.isEmpty() || maxCount <= 0) return emptyList()

        val matchedCurrentMerchantItem = shopItems.firstOrNull { it.id == currentItem.id }
        val currentTags = (matchedCurrentMerchantItem?.tags ?: currentItem.tags).ifEmpty { currentItem.tags }
        val currentKeywords = extractKeywords(
            name = matchedCurrentMerchantItem?.name?.ifBlank { currentItem.name } ?: currentItem.name,
            tags = currentTags
        )

        val currentCatId = MainCategoriesUtil.normalizeKey(matchedCurrentMerchantItem?.categoryId ?: currentItem.categoryId)
        val currentCatName = MainCategoriesUtil.normalizeKey(matchedCurrentMerchantItem?.categoryName?.ifBlank { currentItem.category } ?: currentItem.category)
        val currentSubcatId = MainCategoriesUtil.normalizeKey(matchedCurrentMerchantItem?.subcategoryId ?: currentItem.subcategoryId)
        val currentSubcatName = MainCategoriesUtil.normalizeKey(matchedCurrentMerchantItem?.subcategoryName ?: currentItem.subcategoryName)

        val currentPrice = if (matchedCurrentMerchantItem != null) {
            PriceDropUtil.effectiveFromPrice(matchedCurrentMerchantItem, nowMillis)
        } else {
            PriceDropUtil.effectiveFromPrice(currentItem, nowMillis)
        }

        val candidates = shopItems
            .asSequence()
            .filter { it.id.isNotBlank() && it.id != currentItem.id }
            .distinctBy { it.id }
            .toList()

        if (candidates.isEmpty()) return emptyList()

        fun isOrderableNow(item: MerchantItem): Boolean =
            MenuAvailabilityUtil.isItemOrderable(item, shopMenus, nowMillis, timezone)

        fun effectiveSold(item: MerchantItem): Int =
            maxOf(item.soldCount, soldCountMap[item.id] ?: 0)

        fun isPopularItem(item: MerchantItem): Boolean =
            item.isPopular || item.tags.any {
                it.contains("popular", ignoreCase = true) || it.contains("best", ignoreCase = true)
            }

        fun sharesCategoryClassOrMainCategory(candidate: MerchantItem): Boolean {
            val candSubId = MainCategoriesUtil.normalizeKey(candidate.subcategoryId)
            val candSubName = MainCategoriesUtil.normalizeKey(candidate.subcategoryName)
            val candCatId = MainCategoriesUtil.normalizeKey(candidate.categoryId)
            val candCatName = MainCategoriesUtil.normalizeKey(candidate.categoryName)

            val sameSubId = currentSubcatId.isNotEmpty() && candSubId.isNotEmpty() && currentSubcatId == candSubId
            val sameSubName = currentSubcatName.isNotEmpty() && candSubName.isNotEmpty() && currentSubcatName == candSubName
            val sameCatId = currentCatId.isNotEmpty() && candCatId.isNotEmpty() && currentCatId == candCatId
            val sameCatName = currentCatName.isNotEmpty() && (
                (candCatName.isNotEmpty() && currentCatName == candCatName) ||
                    (candSubName.isNotEmpty() && currentCatName == candSubName)
                )
            val subToCatName = currentSubcatName.isNotEmpty() && candCatName.isNotEmpty() && currentSubcatName == candCatName
            return sameSubId || sameSubName || sameCatId || sameCatName || subToCatName
        }

        fun priceDiffWithin30Percent(candidate: MerchantItem): Double? {
            if (currentPrice <= 0.0) return null
            val candPrice = PriceDropUtil.effectiveFromPrice(candidate, nowMillis)
            if (candPrice <= 0.0) return null
            val diff = abs(candPrice - currentPrice)
            return if (diff <= currentPrice * 0.30 + 1e-6) diff else null
        }

        data class ScoredCandidate(
            val item: MerchantItem,
            val isOrderable: Boolean,
            val tier: Int,
            val keywordMatches: Int,
            val priceDiff: Double,
            val soldCount: Int,
            val popularFlag: Int,
            val normalizedName: String
        )

        fun classifyPool(pool: List<MerchantItem>): List<RecommendedProduct> {
            val scored = pool.map { item ->
                val itemKeywords = extractKeywords(item.name, item.tags)
                val keywordOverlap = if (currentKeywords.isNotEmpty() && itemKeywords.isNotEmpty()) {
                    currentKeywords.count { itemKeywords.contains(it) }
                } else 0
                val sameCategory = keywordOverlap == 0 && sharesCategoryClassOrMainCategory(item)
                val simPriceDiff = if (keywordOverlap == 0 && !sameCategory) priceDiffWithin30Percent(item) else null

                val tier = when {
                    keywordOverlap > 0 -> 1
                    sameCategory -> 2
                    simPriceDiff != null -> 3
                    else -> 4
                }
                ScoredCandidate(
                    item = item,
                    isOrderable = isOrderableNow(item),
                    tier = tier,
                    keywordMatches = keywordOverlap,
                    priceDiff = simPriceDiff ?: Double.MAX_VALUE,
                    soldCount = effectiveSold(item),
                    popularFlag = if (isPopularItem(item)) 1 else 0,
                    normalizedName = item.name.lowercase(Locale.ROOT)
                )
            }

            val tier1 = scored.filter { it.tier == 1 }.sortedWith(
                compareByDescending<ScoredCandidate> { it.keywordMatches }
                    .thenByDescending { it.soldCount }
                    .thenByDescending { it.popularFlag }
                    .thenBy { it.normalizedName }
            )
            val tier2 = scored.filter { it.tier == 2 }.sortedWith(
                compareByDescending<ScoredCandidate> { it.soldCount }
                    .thenByDescending { it.popularFlag }
                    .thenBy { it.normalizedName }
            )
            val tier3 = scored.filter { it.tier == 3 }.sortedWith(
                compareBy<ScoredCandidate> { it.priceDiff }
                    .thenByDescending { it.soldCount }
                    .thenByDescending { it.popularFlag }
                    .thenBy { it.normalizedName }
            )
            val tier4 = scored.filter { it.tier == 4 }.sortedWith(
                compareByDescending<ScoredCandidate> { it.soldCount }
                    .thenByDescending { it.popularFlag }
                    .thenBy { it.normalizedName }
            )

            return (tier1 + tier2 + tier3 + tier4).map {
                RecommendedProduct(
                    item = it.item,
                    isOrderable = it.isOrderable,
                    tier = it.tier,
                    effectiveSoldCount = it.soldCount
                )
            }
        }

        val (availableItems, unavailableItems) = candidates.partition { isOrderableNow(it) }
        val orderedAvailable = classifyPool(availableItems)
        val orderedUnavailable = classifyPool(unavailableItems)

        return (orderedAvailable + orderedUnavailable)
            .distinctBy { it.item.id }
            .take(maxCount)
    }
}
