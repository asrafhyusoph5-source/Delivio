package com.example.util

import com.example.data.model.MainCategoriesConfig
import com.example.data.model.MainCategory
import com.example.data.model.Restaurant
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

object MainCategoriesUtil {

    fun normalizeKey(value: String?): String {
        if (value.isNullOrBlank()) return ""
        return value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
    }

    fun isOthersNameOrId(value: String?): Boolean {
        val norm = normalizeKey(value)
        return norm == "others" || norm == "other"
    }

    fun isOthersCategory(cat: MainCategory): Boolean {
        return isOthersNameOrId(cat.id) || isOthersNameOrId(cat.name)
    }

    val defaultFoodCategories: List<MainCategory> = listOf(
        MainCategory(
            id = "dessert",
            name = "Dessert",
            type = "food",
            sortOrder = 1,
            active = true,
            imageUrl = DelivioIconConstants.getCategoryIconUrl("Dessert"),
            aliases = listOf("desserts", "sweets", "cakes", "ice cream", "pastries")
        ),
        MainCategory(
            id = "burgers",
            name = "Burgers",
            type = "food",
            sortOrder = 2,
            active = true,
            imageUrl = DelivioIconConstants.getCategoryIconUrl("Burgers"),
            aliases = listOf("burger", "sandwiches")
        ),
        MainCategory(
            id = "pizza",
            name = "Pizza",
            type = "food",
            sortOrder = 3,
            active = true,
            imageUrl = DelivioIconConstants.getCategoryIconUrl("Pizza"),
            aliases = listOf("pizzas")
        ),
        MainCategory(
            id = "chicken",
            name = "Chicken",
            type = "food",
            sortOrder = 4,
            active = true,
            imageUrl = DelivioIconConstants.getCategoryIconUrl("Chicken"),
            aliases = listOf("wings", "fried chicken")
        ),
        MainCategory(
            id = "drinks",
            name = "Drinks",
            type = "food",
            sortOrder = 5,
            active = true,
            imageUrl = DelivioIconConstants.getCategoryIconUrl("Drinks"),
            aliases = listOf("drink", "beverages", "milk tea", "coffee", "tea", "boba")
        ),
        MainCategory(
            id = "others",
            name = "Others",
            type = "food",
            sortOrder = 999,
            active = true,
            imageUrl = DelivioIconConstants.getCategoryIconUrl("Others"),
            aliases = listOf("other", "food")
        )
    )

    val defaultConvenienceCategories: List<MainCategory> = listOf(
        MainCategory(
            id = "grocery-stores",
            name = "Grocery Stores",
            type = "convenience",
            sortOrder = 1,
            active = true,
            imageUrl = DelivioIconConstants.getCategoryIconUrl("Grocery Stores"),
            aliases = listOf("grocery", "groceries", "supermarket")
        ),
        MainCategory(
            id = "pharmacies",
            name = "Pharmacies",
            type = "convenience",
            sortOrder = 2,
            active = true,
            imageUrl = DelivioIconConstants.getCategoryIconUrl("Pharmacies"),
            aliases = listOf("pharmacy", "medicine", "drugstore")
        ),
        MainCategory(
            id = "convenience-stores",
            name = "Convenience Stores",
            type = "convenience",
            sortOrder = 3,
            active = true,
            imageUrl = DelivioIconConstants.getCategoryIconUrl("Convenience Stores"),
            aliases = listOf("convenience", "convenience store", "mart", "mini mart")
        ),
        MainCategory(
            id = "school-supplies",
            name = "School Supplies",
            type = "convenience",
            sortOrder = 4,
            active = true,
            imageUrl = DelivioIconConstants.getCategoryIconUrl("School Supplies"),
            aliases = listOf("stationery", "office supplies")
        ),
        MainCategory(
            id = "flowers",
            name = "Flowers",
            type = "convenience",
            sortOrder = 5,
            active = true,
            imageUrl = DelivioIconConstants.getCategoryIconUrl("Flowers"),
            aliases = listOf("flower", "florist", "gifts")
        ),
        MainCategory(
            id = "pet-supplies",
            name = "Pet Supplies",
            type = "convenience",
            sortOrder = 6,
            active = true,
            imageUrl = DelivioIconConstants.getCategoryIconUrl("Pet Supplies"),
            aliases = listOf("pets", "pet shop", "pet food")
        ),
        MainCategory(
            id = "beauty",
            name = "Beauty",
            type = "convenience",
            sortOrder = 7,
            active = true,
            imageUrl = DelivioIconConstants.getCategoryIconUrl("Beauty"),
            aliases = listOf("cosmetics", "skincare", "personal care")
        ),
        MainCategory(
            id = "fresh-frozen",
            name = "Fresh & Frozen",
            type = "convenience",
            sortOrder = 8,
            active = true,
            imageUrl = DelivioIconConstants.getCategoryIconUrl("Fresh & Frozen"),
            aliases = listOf("fresh", "frozen", "produce", "meat", "seafood")
        ),
        MainCategory(
            id = "electronics",
            name = "Electronics",
            type = "convenience",
            sortOrder = 9,
            active = true,
            imageUrl = DelivioIconConstants.getCategoryIconUrl("Electronics"),
            aliases = listOf("gadgets", "accessories")
        ),
        MainCategory(
            id = "others",
            name = "Others",
            type = "convenience",
            sortOrder = 999,
            active = true,
            imageUrl = DelivioIconConstants.getCategoryIconUrl("Others"),
            aliases = listOf("other", "essentials")
        )
    )

    fun defaultConfig(): MainCategoriesConfig = MainCategoriesConfig(
        food = defaultFoodCategories,
        convenience = defaultConvenienceCategories
    )

    /**
     * Filters to only active categories, sorts by sortOrder (with "Others" always last).
     * If no "Others" entry was defined at all in rawList, appends the built-in "Others" last
     * so items/shops with unmatched categories are always accessible under "Others".
     */
    fun prepareActiveCategories(
        rawList: List<MainCategory>,
        type: String
    ): List<MainCategory> {
        val normType = if (normalizeKey(type) == "convenience") "convenience" else "food"
        val hasExplicitOthers = rawList.any { isOthersCategory(it) }
        val activeOnly = rawList
            .filter { it.active && (it.name.isNotBlank() || it.id.isNotBlank()) }
            .map {
                it.copy(
                    id = it.id.ifBlank { normalizeKey(it.name) },
                    name = it.name.ifBlank { it.id },
                    type = normType
                )
            }

        val (othersList, standardList) = activeOnly.partition { isOthersCategory(it) }
        val sortedStandard = standardList.sortedWith(
            compareBy<MainCategory> { it.sortOrder }.thenBy { normalizeKey(it.name) }
        )
        val finalOthers = when {
            othersList.isNotEmpty() -> othersList
            !hasExplicitOthers -> listOf(
                MainCategory(
                    id = "others",
                    name = "Others",
                    type = normType,
                    sortOrder = 999,
                    active = true,
                    imageUrl = DelivioIconConstants.getCategoryIconUrl("Others"),
                    aliases = listOf("other")
                )
            )
            else -> emptyList()
        }
        return sortedStandard + finalOthers
    }

    fun activeForType(config: MainCategoriesConfig, type: String): List<MainCategory> {
        val normType = if (normalizeKey(type) == "convenience") "convenience" else "food"
        val raw = if (normType == "convenience") config.convenience else config.food
        return prepareActiveCategories(raw, normType)
    }

    /**
     * Single unified matching check: compares normalized input against a category's id, name, and aliases.
     */
    fun categoryMatchesToken(category: MainCategory, rawValue: String?): Boolean {
        val norm = normalizeKey(rawValue)
        if (norm.isEmpty()) return false
        if (normalizeKey(category.id) == norm) return true
        if (normalizeKey(category.name) == norm) return true
        return category.aliases.any { normalizeKey(it) == norm }
    }

    /**
     * Resolves a raw tag or item category field against active categories of its type.
     * Any value that matches no active non-"Others" category resolves to the "Others" category.
     */
    fun resolveCategory(rawValue: String?, activeCategoriesOfType: List<MainCategory>): MainCategory {
        val norm = normalizeKey(rawValue)
        if (norm.isNotEmpty()) {
            val matchedStandard = activeCategoriesOfType
                .filterNot { isOthersCategory(it) }
                .firstOrNull { categoryMatchesToken(it, norm) }
            if (matchedStandard != null) return matchedStandard
        }
        return activeCategoriesOfType.firstOrNull { isOthersCategory(it) }
            ?: MainCategory(
                id = "others",
                name = "Others",
                type = activeCategoriesOfType.firstOrNull()?.type ?: "food",
                sortOrder = 999,
                active = true,
                imageUrl = DelivioIconConstants.getCategoryIconUrl("Others"),
                aliases = listOf("other")
            )
    }

    fun resolveShopCategories(shop: Restaurant, activeCategoriesOfType: List<MainCategory>): List<MainCategory> {
        val cleanTags = shop.cuisineTags.mapNotNull { it.trim().takeIf { t -> t.isNotBlank() } }
        if (cleanTags.isEmpty()) {
            return listOf(resolveCategory(null, activeCategoriesOfType))
        }
        return cleanTags
            .map { tag -> resolveCategory(tag, activeCategoriesOfType) }
            .distinctBy { normalizeKey(it.id).ifEmpty { normalizeKey(it.name) } }
    }

    /**
     * Single matching function for Home chips, Shops screens, and Category filtering.
     * - If selectedCategory is null or "All", returns true (includes both Food and Convenience).
     * - Otherwise verifies the shop's service type matches selectedCategory.type AND at least one
     *   cuisineTag (or unmatched fallback to "Others") resolves to selectedCategory.
     */
    fun shopMatchesCategory(
        shop: Restaurant,
        selectedCategory: MainCategory?,
        config: MainCategoriesConfig
    ): Boolean {
        if (selectedCategory == null) return true
        val normSelectedName = normalizeKey(selectedCategory.name)
        val normSelectedId = normalizeKey(selectedCategory.id)
        if (normSelectedName == "all" || normSelectedId == "all") return true

        val shopType = if (shop.isConvenience || normalizeKey(shop.type) == "convenience") "convenience" else "food"
        val targetType = if (normalizeKey(selectedCategory.type) == "convenience") "convenience" else "food"
        if (shopType != targetType) return false

        val activeCats = activeForType(config, targetType)
        val resolvedShopCats = resolveShopCategories(shop, activeCats)
        return resolvedShopCats.any { resolved ->
            normalizeKey(resolved.id) == normSelectedId ||
                normalizeKey(resolved.name) == normSelectedName ||
                (isOthersCategory(selectedCategory) && isOthersCategory(resolved))
        }
    }

    fun shopMatchesCategoryNameOrId(
        shop: Restaurant,
        categoryQuery: String?,
        config: MainCategoriesConfig
    ): Boolean {
        val norm = normalizeKey(categoryQuery)
        if (norm.isEmpty() || norm == "all") return true
        val shopType = if (shop.isConvenience || normalizeKey(shop.type) == "convenience") "convenience" else "food"
        val activeCats = activeForType(config, shopType)
        val targetCat = activeCats.firstOrNull { categoryMatchesToken(it, norm) } ?: return false
        return shopMatchesCategory(shop, targetCat, config)
    }

    /**
     * Unified matching for Search queries across shop names, tags, and resolved active category metadata (id, name, aliases).
     */
    fun shopMatchesSearchQuery(
        shop: Restaurant,
        rawQuery: String,
        config: MainCategoriesConfig
    ): Boolean {
        val normQ = normalizeKey(rawQuery)
        if (normQ.isEmpty()) return true
        if (normalizeKey(shop.displayName).contains(normQ) ||
            normalizeKey(shop.name).contains(normQ) ||
            normalizeKey(shop.branchName).contains(normQ) ||
            normalizeKey(shop.category).contains(normQ) ||
            shop.cuisineTags.any { normalizeKey(it).contains(normQ) }
        ) {
            return true
        }
        val shopType = if (shop.isConvenience || normalizeKey(shop.type) == "convenience") "convenience" else "food"
        val activeCats = activeForType(config, shopType)
        val resolved = resolveShopCategories(shop, activeCats)
        return resolved.any { cat ->
            normalizeKey(cat.id).contains(normQ) ||
                normalizeKey(cat.name).contains(normQ) ||
                cat.aliases.any { normalizeKey(it).contains(normQ) }
        }
    }

    /**
     * Unified matching function for items (used in shop menus and item category filtering).
     */
    fun itemMatchesCategory(
        itemCategoryField: String?,
        targetCategory: MainCategory?,
        activeCategoriesOfType: List<MainCategory>
    ): Boolean {
        if (targetCategory == null) return true
        val normTargetName = normalizeKey(targetCategory.name)
        val normTargetId = normalizeKey(targetCategory.id)
        if (normTargetName == "all" || normTargetId == "all") return true

        val resolved = resolveCategory(itemCategoryField, activeCategoriesOfType)
        return normalizeKey(resolved.id) == normTargetId ||
            normalizeKey(resolved.name) == normTargetName ||
            (isOthersCategory(targetCategory) && isOthersCategory(resolved))
    }

    fun itemMatchesCategoryQuery(
        itemCategoryField: String?,
        query: String?,
        activeCategoriesOfType: List<MainCategory>
    ): Boolean {
        val normQ = normalizeKey(query)
        if (normQ.isEmpty()) return true
        if (normalizeKey(itemCategoryField) == normQ) return true
        val resolved = resolveCategory(itemCategoryField, activeCategoriesOfType)
        return normalizeKey(resolved.id) == normQ ||
            normalizeKey(resolved.name) == normQ ||
            resolved.aliases.any { normalizeKey(it) == normQ }
    }

    fun configToJson(config: MainCategoriesConfig): String {
        val root = JSONObject()
        root.put("food", listToJsonArray(config.food))
        root.put("convenience", listToJsonArray(config.convenience))
        return root.toString()
    }

    fun configFromJson(json: String?): MainCategoriesConfig? {
        if (json.isNullOrBlank()) return null
        return try {
            val root = JSONObject(json)
            val food = jsonArrayToList(root.optJSONArray("food"), "food")
            val conv = jsonArrayToList(root.optJSONArray("convenience"), "convenience")
            if (food.isEmpty() && conv.isEmpty()) null else MainCategoriesConfig(food = food, convenience = conv)
        } catch (_: Exception) {
            null
        }
    }

    private fun listToJsonArray(list: List<MainCategory>): JSONArray {
        val arr = JSONArray()
        list.forEach { cat ->
            val obj = JSONObject()
            obj.put("id", cat.id)
            obj.put("name", cat.name)
            obj.put("type", cat.type)
            obj.put("sortOrder", cat.sortOrder)
            obj.put("active", cat.active)
            obj.put("imageUrl", cat.imageUrl)
            val aliasesArr = JSONArray()
            cat.aliases.forEach { aliasesArr.put(it) }
            obj.put("aliases", aliasesArr)
            arr.put(obj)
        }
        return arr
    }

    private fun jsonArrayToList(arr: JSONArray?, defaultType: String): List<MainCategory> {
        if (arr == null) return emptyList()
        val result = mutableListOf<MainCategory>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val aliasesArr = obj.optJSONArray("aliases")
            val aliases = mutableListOf<String>()
            if (aliasesArr != null) {
                for (j in 0 until aliasesArr.length()) {
                    val a = aliasesArr.optString(j, "").trim()
                    if (a.isNotEmpty()) aliases.add(a)
                }
            }
            result.add(
                MainCategory(
                    id = obj.optString("id", ""),
                    name = obj.optString("name", ""),
                    type = obj.optString("type", defaultType),
                    sortOrder = obj.optInt("sortOrder", i),
                    active = obj.optBoolean("active", true),
                    imageUrl = obj.optString("imageUrl", ""),
                    aliases = aliases
                )
            )
        }
        return result
    }
}
