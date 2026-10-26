package com.example.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DinnerDining
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ElectricalServices
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.LocalPizza
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocalTaxi
import androidx.compose.material.icons.filled.LunchDining
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Yard
import androidx.compose.ui.graphics.vector.ImageVector

object DelivioIconConstants {
    const val ICON_BASE_URL = "https://raw.githubusercontent.com/asrafhyusoph5-source/Delivio-Assets/main/"

    // Services PNG filenames
    const val SVC_FOOD = "svc_food.png"
    const val SVC_CONVENIENCE = "svc_convenience.png"
    const val SVC_PARCEL = "svc_parcel.png"
    const val SVC_PABILI = "svc_pabili.png"
    const val SVC_TAXI = "svc_taxi.png"

    val SERVICE_ICONS: Map<String, String> = mapOf(
        "food" to SVC_FOOD,
        "convenience" to SVC_CONVENIENCE,
        "parcel" to SVC_PARCEL,
        "pabili" to SVC_PABILI,
        "taxi" to SVC_TAXI
    )

    val SERVICE_FALLBACK_ICONS: Map<String, ImageVector> = mapOf(
        "food" to Icons.Default.Restaurant,
        "convenience" to Icons.Default.Storefront,
        "parcel" to Icons.Default.LocalShipping,
        "pabili" to Icons.Default.ShoppingBag,
        "taxi" to Icons.Default.LocalTaxi
    )

    // Category PNG filenames
    const val CAT_ALL = "cat_all.png"
    const val CAT_DESSERT = "cat_dessert.png"
    const val CAT_BURGERS = "cat_burgers.png"
    const val CAT_PIZZA = "cat_pizza.png"
    const val CAT_CHICKEN = "cat_chicken.png"
    const val CAT_DRINKS = "cat_drinks.png"
    const val CAT_GROCERY = "cat_grocery.png"
    const val CAT_PHARMACIES = "cat_pharmacies.png"
    const val CAT_CONVENIENCE_STORES = "cat_convenience_stores.png"
    const val CAT_SCHOOL_SUPPLIES = "cat_school_supplies.png"
    const val CAT_FLOWERS = "cat_flowers.png"
    const val CAT_PET_SUPPLIES = "cat_pet_supplies.png"
    const val CAT_BEAUTY = "cat_beauty.png"
    const val CAT_FRESH_FROZEN = "cat_fresh_frozen.png"
    const val CAT_ELECTRONICS = "cat_electronics.png"

    val CATEGORY_ICONS: Map<String, String> = mapOf(
        "All" to CAT_ALL,
        "Dessert" to CAT_DESSERT,
        "Burgers" to CAT_BURGERS,
        "Pizza" to CAT_PIZZA,
        "Chicken" to CAT_CHICKEN,
        "Drinks" to CAT_DRINKS,
        "Grocery Stores" to CAT_GROCERY,
        "Pharmacies" to CAT_PHARMACIES,
        "Convenience Stores" to CAT_CONVENIENCE_STORES,
        "School Supplies" to CAT_SCHOOL_SUPPLIES,
        "Flowers" to CAT_FLOWERS,
        "Pet Supplies" to CAT_PET_SUPPLIES,
        "Beauty" to CAT_BEAUTY,
        "Fresh & Frozen" to CAT_FRESH_FROZEN,
        "Electronics" to CAT_ELECTRONICS
    )

    val CATEGORY_FALLBACK_ICONS: Map<String, ImageVector> = mapOf(
        "All" to Icons.Default.Apps,
        "Dessert" to Icons.Default.Cake,
        "Burgers" to Icons.Default.LunchDining,
        "Pizza" to Icons.Default.LocalPizza,
        "Chicken" to Icons.Default.DinnerDining,
        "Drinks" to Icons.Default.LocalBar,
        "Grocery Stores" to Icons.Default.ShoppingCart,
        "Pharmacies" to Icons.Default.LocalPharmacy,
        "Convenience Stores" to Icons.Default.Store,
        "School Supplies" to Icons.Default.Edit,
        "Flowers" to Icons.Default.Yard,
        "Pet Supplies" to Icons.Default.Pets,
        "Beauty" to Icons.Default.Face,
        "Fresh & Frozen" to Icons.Default.AcUnit,
        "Electronics" to Icons.Default.ElectricalServices
    )

    val FOOD_CATEGORIES: List<String> = listOf(
        "Dessert",
        "Burgers",
        "Pizza",
        "Chicken",
        "Drinks"
    )

    val CONVENIENCE_CATEGORIES: List<String> = listOf(
        "Grocery Stores",
        "Pharmacies",
        "Convenience Stores",
        "School Supplies",
        "Flowers",
        "Pet Supplies",
        "Beauty",
        "Fresh & Frozen",
        "Electronics"
    )

    fun getCategoryIconUrl(category: String): String {
        val fileName = CATEGORY_ICONS[category] ?: CAT_ALL
        return ICON_BASE_URL + fileName
    }

    fun getServiceIconUrl(service: String): String {
        val fileName = SERVICE_ICONS[service.lowercase()] ?: SVC_FOOD
        return ICON_BASE_URL + fileName
    }

    fun getCategoryFallback(category: String): ImageVector {
        return CATEGORY_FALLBACK_ICONS[category] ?: Icons.Default.Category
    }

    fun getServiceFallback(service: String): ImageVector {
        return SERVICE_FALLBACK_ICONS[service.lowercase()] ?: Icons.Default.Category
    }
}
