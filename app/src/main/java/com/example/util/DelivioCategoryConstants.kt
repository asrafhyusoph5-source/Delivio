package com.example.util

object DelivioCategoryConstants {
    val FOOD_CATEGORIES = DelivioIconConstants.FOOD_CATEGORIES
    val CONVENIENCE_CATEGORIES = DelivioIconConstants.CONVENIENCE_CATEGORIES

    // Keep backwards-compatibility maps pointing to image URLs instead of emojis
    val FOOD_ICONS = DelivioIconConstants.CATEGORY_ICONS.filterKeys {
        it == "All" || it in FOOD_CATEGORIES
    }

    val CONVENIENCE_ICONS = DelivioIconConstants.CATEGORY_ICONS.filterKeys {
        it in CONVENIENCE_CATEGORIES
    }
}
