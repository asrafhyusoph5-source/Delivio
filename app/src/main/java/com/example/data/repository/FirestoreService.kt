package com.example.data.repository

import android.util.Log
import com.example.data.model.Banner
import com.example.data.model.BannerLink
import com.example.data.model.City
import com.example.data.model.DeliveryRates
import com.example.data.model.DelivioAddress
import com.example.data.model.DelivioUser
import com.example.data.model.Merchant
import com.example.data.model.MerchantCategory
import com.example.data.model.MerchantItem
import com.example.data.model.OrderDocument
import com.example.data.model.RateConfig
import com.example.data.model.StatusHistoryEntry
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreService {
    private val tag = "DelivioFirestore"

    private val firestore: FirebaseFirestore? by lazy {
        try {
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            Log.w(tag, "FirebaseFirestore not initialized: ${e.message}")
            null
        }
    }

    // Default Fallback Data if Firestore is not provisioned or offline
    internal val defaultCity = City(
        id = "puerto-princesa",
        name = "Puerto Princesa",
        province = "Palawan",
        status = "active",
        lat = 9.7392,
        lng = 118.7353,
        radiusKm = 25.0
    )

    internal val defaultDeliveryRates = DeliveryRates(
        id = "puerto-princesa",
        cityId = "puerto-princesa",
        food = RateConfig(39.0, 2.0, 10.0, 39.0, 250.0, 10.0, 0.0),
        convenience = RateConfig(45.0, 2.0, 12.0, 45.0, 280.0, 12.0, 0.0),
        parcel = RateConfig(50.0, 3.0, 14.0, 50.0, 350.0, 15.0, 0.0),
        pabili = RateConfig(55.0, 2.0, 15.0, 55.0, 400.0, 20.0, 0.0)
    )

    internal val defaultBanners = listOf(
        Banner(
            id = "banner-burger-frenzy",
            title = "Flame Grilled Burgers 20% OFF",
            imageUrl = "https://images.unsplash.com/photo-1568901346375-23c9450c58cd?w=800&q=80",
            status = "active",
            priority = 1,
            link = BannerLink(merchantId = "merchant-burger-shack", category = "food")
        ),
        Banner(
            id = "banner-convenience-express",
            title = "24/7 Groceries & Daily Essentials Delivered",
            imageUrl = "https://images.unsplash.com/photo-1542838132-92c53300491e?w=800&q=80",
            status = "active",
            priority = 2,
            link = BannerLink(category = "convenience")
        )
    )

    internal val defaultMerchants = listOf(
        Merchant(
            id = "merchant-burger-shack",
            name = "Palawan Burger Shack",
            type = "food",
            status = "active",
            cityId = "puerto-princesa",
            isOpen = true,
            rating = 4.8,
            deliveryTime = "20-30 min",
            address = "Rizal Avenue, Puerto Princesa, Palawan",
            lat = 9.7420,
            lng = 118.7400,
            cuisineTags = listOf("Burgers", "Drinks", "Dessert"),
            imageUrl = "https://images.unsplash.com/photo-1550547660-d9450f859349?w=600&q=80",
            categories = listOf(
                MerchantCategory("cat-signature-burgers", "Signature Burgers", 1),
                MerchantCategory("cat-sides-fries", "Loaded Sides", 2),
                MerchantCategory("cat-milkshakes", "Artisan Shakes", 3),
                MerchantCategory("cat-sweet-treats", "Desserts", 4)
            ),
            items = listOf(
                MerchantItem("item-b1", "cat-signature-burgers", "Classic Angus Smash", 189.0, "Double smash beef patty, aged cheddar, pickles, house sauce", "https://images.unsplash.com/photo-1568901346375-23c9450c58cd?w=400&q=80", true),
                MerchantItem("item-b2", "cat-signature-burgers", "Truffle Bacon Melt", 249.0, "Smoked bacon, truffle aioli, Swiss cheese, brioche bun", "https://images.unsplash.com/photo-1586190848861-99aa4a171e90?w=400&q=80", true),
                MerchantItem("item-b3", "cat-sides-fries", "Crispy Cajun Fries", 89.0, "Skin-on potatoes seasoned with authentic cajun spice blend", "https://images.unsplash.com/photo-1576107232684-1279f3908594?w=400&q=80", true),
                MerchantItem("item-b4", "cat-sides-fries", "Cheesy Bacon Poppers", 129.0, "Deep-fried melted mozzarella bites with bacon crumble", "https://images.unsplash.com/photo-1531749668029-2db88e4276c7?w=400&q=80", false),
                MerchantItem("item-b5", "cat-milkshakes", "Salted Caramel Shake", 119.0, "Real dairy soft serve blended with sea-salt caramel drizzle", "https://images.unsplash.com/photo-1572490122747-3968b75cc699?w=400&q=80", true),
                MerchantItem("item-b6", "cat-sweet-treats", "Choco Fudge Lava Cup", 99.0, "Warm melted chocolate center with vanilla icing swirl", "https://images.unsplash.com/photo-1606313564200-e75d5e30476c?w=400&q=80", true)
            )
        ),
        Merchant(
            id = "merchant-pizza-haven",
            name = "Haven Pizza & Crispy Wings",
            type = "food",
            status = "active",
            cityId = "puerto-princesa",
            isOpen = false,
            rating = 4.7,
            deliveryTime = "30-45 min",
            address = "Malvar Street, Puerto Princesa, Palawan",
            lat = 9.7450,
            lng = 118.7380,
            cuisineTags = listOf("Pizza", "Chicken", "Dessert"),
            imageUrl = "https://images.unsplash.com/photo-1513104890138-7c749659a591?w=600&q=80",
            categories = listOf(
                MerchantCategory("cat-brick-oven-pizza", "Hand-Tossed Pizzas", 1),
                MerchantCategory("cat-crispy-wings", "Crispy Glazed Wings", 2),
                MerchantCategory("cat-pasta-salads", "Pasta Specials", 3),
                MerchantCategory("cat-italian-desserts", "Dolci & Desserts", 4)
            ),
            items = listOf(
                MerchantItem("item-p1", "cat-brick-oven-pizza", "Pepperoni Overload 12\"", 389.0, "Generous cured pepperoni, marinara, double mozzarella", "https://images.unsplash.com/photo-1628840042765-356cda07504e?w=400&q=80", true),
                MerchantItem("item-p2", "cat-brick-oven-pizza", "Four Cheese Truffle 12\"", 429.0, "Gorgonzola, parmesan, mozzarella, ricotta with truffle oil", "https://images.unsplash.com/photo-1573821663912-569905455b1c?w=400&q=80", true),
                MerchantItem("item-p3", "cat-crispy-wings", "Honey Garlic Glazed Wings (6pcs)", 219.0, "Golden fried wings tossed in sweet garlic glaze", "https://images.unsplash.com/photo-1567620832903-9fc6debc209f?w=400&q=80", true),
                MerchantItem("item-p4", "cat-crispy-wings", "Fiery Buffalo Wings (6pcs)", 219.0, "Spicy cayenne buffalo butter with ranch dipping dip", "https://images.unsplash.com/photo-1527477378698-4ca04080721c?w=400&q=80", true),
                MerchantItem("item-p5", "cat-pasta-salads", "Creamy Truffle Carbonara", 259.0, "Spaghetti pasta with pancetta, egg yolk, pecorino cheese", "https://images.unsplash.com/photo-1612874742237-6526221588e3?w=400&q=80", true),
                MerchantItem("item-p6", "cat-italian-desserts", "Classic Tiramisu Slice", 149.0, "Espresso-soaked ladyfingers, mascarpone cream, dark cocoa", "https://images.unsplash.com/photo-1571877227200-a0d98ea607e9?w=400&q=80", true)
            )
        ),
        Merchant(
            id = "merchant-palawan-mart",
            name = "QuickPal Convenience & Groceries",
            type = "convenience",
            status = "active",
            cityId = "puerto-princesa",
            isOpen = true,
            rating = 4.9,
            deliveryTime = "15-25 min",
            address = "National Highway, San Pedro, Puerto Princesa, Palawan",
            lat = 9.7500,
            lng = 118.7450,
            cuisineTags = listOf("Drinks", "Dessert"),
            imageUrl = "https://images.unsplash.com/photo-1604719312566-8912e9227c6a?w=600&q=80",
            categories = listOf(
                MerchantCategory("cat-chilled-drinks", "Chilled Beverages", 1),
                MerchantCategory("cat-snacks-crisps", "Snacks & Chips", 2),
                MerchantCategory("cat-dairy-bakery", "Bakery & Sweets", 3),
                MerchantCategory("cat-quick-meals", "Quick Ready Meals", 4)
            ),
            items = listOf(
                MerchantItem("item-c1", "cat-chilled-drinks", "Cold Brew Coffee Latte 330ml", 75.0, "Rich arabica cold brew with fresh dairy milk", "https://images.unsplash.com/photo-1517701550927-30cf4ba1dba5?w=400&q=80", true),
                MerchantItem("item-c2", "cat-chilled-drinks", "Sparkling Lemon Iced Tea 500ml", 45.0, "Refreshing brewed black tea infused with natural lemon", "https://images.unsplash.com/photo-1556679343-c7306c1976bc?w=400&q=80", true),
                MerchantItem("item-c3", "cat-snacks-crisps", "Sea Salt Cassava Crisps 120g", 55.0, "Locally harvested crisp cassava chips lightly salted", "https://images.unsplash.com/photo-1566478989037-eec170784d0b?w=400&q=80", true),
                MerchantItem("item-c4", "cat-snacks-crisps", "Cheese Nacho Chips Supreme", 65.0, "Crunchy corn tortilla chips with intense cheddar seasoning", "https://images.unsplash.com/photo-1513456852971-30c0b8199d4d?w=400&q=80", true),
                MerchantItem("item-c5", "cat-dairy-bakery", "Fresh Butter Croissant", 60.0, "Flaky oven-baked French pastry butter croissant", "https://images.unsplash.com/photo-1555507036-ab1f4038808a?w=400&q=80", true),
                MerchantItem("item-c6", "cat-quick-meals", "Chicken Teriyaki Bento Box", 145.0, "Steamed Japanese rice, grilled chicken thigh, pickled veggies", "https://images.unsplash.com/photo-1546069901-ba9599a7e63c?w=400&q=80", true)
            )
        )
    )

    // Listen to Cities
    fun getCitiesFlow(): Flow<List<City>> = callbackFlow {
        val fs = firestore
        if (fs == null) {
            trySend(listOf(defaultCity))
            awaitClose {}
            return@callbackFlow
        }

        val listener: ListenerRegistration = fs.collection("cities")
            .whereEqualTo("status", "active")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(tag, "Error loading cities", error)
                    trySend(listOf(defaultCity))
                    return@addSnapshotListener
                }
                if (snapshot != null && !snapshot.isEmpty) {
                    val cities = snapshot.documents.mapNotNull { doc ->
                        val center = doc.get("center") as? Map<*, *>
                        val lat = (center?.get("lat") as? Number)?.toDouble() ?: doc.getDouble("lat") ?: 9.7392
                        val lng = (center?.get("lng") as? Number)?.toDouble() ?: doc.getDouble("lng") ?: 118.7353
                        City(
                            id = doc.id,
                            name = doc.getString("name") ?: "",
                            province = doc.getString("province") ?: "",
                            status = doc.getString("status") ?: "active",
                            lat = lat,
                            lng = lng,
                            radiusKm = doc.getDouble("radiusKm") ?: 25.0
                        )
                    }
                    trySend(cities)
                } else {
                    trySend(listOf(defaultCity))
                }
            }

        awaitClose { listener.remove() }
    }

    // Listen to Delivery Rates for City
    fun getDeliveryRatesFlow(cityId: String): Flow<DeliveryRates> = callbackFlow {
        val fs = firestore
        if (fs == null) {
            trySend(defaultDeliveryRates)
            awaitClose {}
            return@callbackFlow
        }

        val listener = fs.collection("deliveryRates").document(cityId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) {
                    trySend(defaultDeliveryRates)
                    return@addSnapshotListener
                }
                try {
                    val foodMap = snapshot.get("food") as? Map<*, *>
                    val convMap = snapshot.get("convenience") as? Map<*, *>
                    val parcelMap = snapshot.get("parcel") as? Map<*, *>
                    val pabiliMap = snapshot.get("pabili") as? Map<*, *>

                    fun parseRate(m: Map<*, *>?): RateConfig {
                        if (m == null) return RateConfig()
                        return RateConfig(
                            baseFare = (m["baseFare"] as? Number)?.toDouble() ?: 39.0,
                            includedKm = (m["includedKm"] as? Number)?.toDouble() ?: 2.0,
                            perKmRate = (m["perKmRate"] as? Number)?.toDouble() ?: 10.0,
                            minFee = (m["minFee"] as? Number)?.toDouble() ?: 39.0,
                            maxFee = (m["maxFee"] as? Number)?.toDouble() ?: 250.0,
                            serviceFee = (m["serviceFee"] as? Number)?.toDouble() ?: 10.0,
                            surcharge = (m["surcharge"] as? Number)?.toDouble() ?: 0.0
                        )
                    }

                    val rates = DeliveryRates(
                        id = snapshot.id,
                        cityId = cityId,
                        food = parseRate(foodMap),
                        convenience = parseRate(convMap),
                        parcel = parseRate(parcelMap),
                        pabili = parseRate(pabiliMap)
                    )
                    trySend(rates)
                } catch (e: Exception) {
                    trySend(defaultDeliveryRates)
                }
            }

        awaitClose { listener.remove() }
    }

    // Listen to Banners
    fun getBannersFlow(): Flow<List<Banner>> = callbackFlow {
        val fs = firestore
        if (fs == null) {
            trySend(defaultBanners)
            awaitClose {}
            return@callbackFlow
        }

        val listener = fs.collection("banners")
            .whereEqualTo("status", "active")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || snapshot.isEmpty) {
                    trySend(defaultBanners)
                    return@addSnapshotListener
                }
                val banners = snapshot.documents.mapNotNull { doc ->
                    val linkMap = doc.get("link") as? Map<*, *>
                    Banner(
                        id = doc.id,
                        title = doc.getString("title") ?: "",
                        imageUrl = doc.getString("imageUrl") ?: "",
                        status = doc.getString("status") ?: "active",
                        priority = doc.getLong("priority")?.toInt() ?: 1,
                        startAt = doc.getString("startAt") ?: "",
                        endAt = doc.getString("endAt") ?: "",
                        link = BannerLink(
                            merchantId = linkMap?.get("merchantId") as? String,
                            category = linkMap?.get("category") as? String
                        )
                    )
                }.sortedBy { it.priority }
                trySend(if (banners.isNotEmpty()) banners else defaultBanners)
            }

        awaitClose { listener.remove() }
    }

    // Listen to Merchants for a City
    fun getMerchantsFlow(cityId: String): Flow<List<Merchant>> = callbackFlow {
        val fs = firestore
        if (fs == null) {
            trySend(defaultMerchants.filter { it.cityId == cityId || cityId.isEmpty() })
            awaitClose {}
            return@callbackFlow
        }

        val query = if (cityId.isNotEmpty()) {
            fs.collection("merchants")
                .whereEqualTo("cityId", cityId)
                .whereEqualTo("status", "active")
        } else {
            fs.collection("merchants").whereEqualTo("status", "active")
        }

        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null || snapshot.isEmpty) {
                // If query returns empty or error, check fallback
                val filtered = defaultMerchants.filter { it.cityId == cityId || cityId.isEmpty() }
                trySend(filtered)
                return@addSnapshotListener
            }

            val merchantList = mutableListOf<Merchant>()
            for (doc in snapshot.documents) {
                val tags = (doc.get("cuisineTags") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                val m = Merchant(
                    id = doc.id,
                    name = doc.getString("name") ?: "",
                    type = doc.getString("type") ?: "food",
                    status = doc.getString("status") ?: "active",
                    cityId = doc.getString("cityId") ?: "",
                    isOpen = doc.getBoolean("isOpen") ?: true,
                    rating = doc.getDouble("rating") ?: 4.8,
                    deliveryTime = doc.getString("deliveryTime") ?: "20-30 min",
                    address = doc.getString("address") ?: "",
                    lat = doc.getDouble("lat") ?: 9.7420,
                    lng = doc.getDouble("lng") ?: 118.7400,
                    cuisineTags = tags,
                    imageUrl = doc.getString("imageUrl") ?: ""
                )
                merchantList.add(m)
            }
            trySend(merchantList)
        }

        awaitClose { listener.remove() }
    }

    // Listen to Categories and Items for a Merchant
    fun getMerchantCategoriesFlow(merchantId: String): Flow<List<MerchantCategory>> = callbackFlow {
        val fs = firestore
        if (fs == null) {
            val fallback = defaultMerchants.find { it.id == merchantId }?.categories ?: emptyList()
            trySend(fallback)
            awaitClose {}
            return@callbackFlow
        }

        val listener = fs.collection("merchants").document(merchantId).collection("categories")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || snapshot.isEmpty) {
                    val fallback = defaultMerchants.find { it.id == merchantId }?.categories ?: emptyList()
                    trySend(fallback)
                    return@addSnapshotListener
                }
                val cats = snapshot.documents.mapNotNull { doc ->
                    MerchantCategory(
                        id = doc.id,
                        name = doc.getString("name") ?: "",
                        order = doc.getLong("order")?.toInt() ?: 1
                    )
                }.sortedBy { it.order }
                trySend(cats)
            }

        awaitClose { listener.remove() }
    }

    fun getMerchantItemsFlow(merchantId: String): Flow<List<MerchantItem>> = callbackFlow {
        val fs = firestore
        if (fs == null) {
            val fallback = defaultMerchants.find { it.id == merchantId }?.items ?: emptyList()
            trySend(fallback)
            awaitClose {}
            return@callbackFlow
        }

        val listener = fs.collection("merchants").document(merchantId).collection("items")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || snapshot.isEmpty) {
                    val fallback = defaultMerchants.find { it.id == merchantId }?.items ?: emptyList()
                    trySend(fallback)
                    return@addSnapshotListener
                }
                val items = snapshot.documents.mapNotNull { doc ->
                    MerchantItem(
                        id = doc.id,
                        categoryId = doc.getString("categoryId") ?: "",
                        name = doc.getString("name") ?: "",
                        price = doc.getDouble("price") ?: 0.0,
                        description = doc.getString("description") ?: "",
                        imageUrl = doc.getString("imageUrl") ?: "",
                        isAvailable = doc.getBoolean("isAvailable") ?: true
                    )
                }
                trySend(items)
            }

        awaitClose { listener.remove() }
    }

    // Listen to Customer Orders (Real-time snapshot listener)
    fun getCustomerOrdersFlow(customerId: String): Flow<List<OrderDocument>> = callbackFlow {
        val fs = firestore
        if (fs == null || customerId.isEmpty()) {
            trySend(emptyList())
            awaitClose {}
            return@callbackFlow
        }

        val listener = fs.collection("orders")
            .whereEqualTo("customerId", customerId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(tag, "Error listening to customer orders", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        try {
                            doc.toObject(OrderDocument::class.java)?.copy(id = doc.id)
                        } catch (e: Exception) {
                            Log.w(tag, "Failed to parse order doc ${doc.id}", e)
                            null
                        }
                    }.sortedByDescending { it.createdAt }
                    trySend(list)
                }
            }

        awaitClose { listener.remove() }
    }

    // Listen to Customer Addresses (users/{uid}/addresses)
    fun getAddressesFlow(uid: String): Flow<List<DelivioAddress>> = callbackFlow {
        val fs = firestore
        if (fs == null || uid.isEmpty()) {
            trySend(listOf(
                DelivioAddress(id = "addr-default", label = "Home", address = "Rizal Avenue, Puerto Princesa, Palawan", details = "Near Capitol Park", lat = 9.7392, lng = 118.7353, isDefault = true)
            ))
            awaitClose {}
            return@callbackFlow
        }

        val listener = fs.collection("users").document(uid).collection("addresses")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || snapshot.isEmpty) {
                    trySend(listOf(
                        DelivioAddress(id = "addr-default", label = "Home", address = "Rizal Avenue, Puerto Princesa, Palawan", details = "Near Capitol Park", lat = 9.7392, lng = 118.7353, isDefault = true)
                    ))
                    return@addSnapshotListener
                }
                val addresses = snapshot.documents.mapNotNull { doc ->
                    DelivioAddress(
                        id = doc.id,
                        label = doc.getString("label") ?: "Home",
                        address = doc.getString("address") ?: "",
                        details = doc.getString("details") ?: "",
                        lat = doc.getDouble("lat") ?: 9.7392,
                        lng = doc.getDouble("lng") ?: 118.7353,
                        isDefault = doc.getBoolean("isDefault") ?: false
                    )
                }
                trySend(addresses)
            }

        awaitClose { listener.remove() }
    }

    // Address Operations
    suspend fun saveAddress(uid: String, address: DelivioAddress): Result<Unit> {
        val fs = firestore ?: return Result.success(Unit)
        return try {
            val coll = fs.collection("users").document(uid).collection("addresses")
            val docRef = if (address.id.isEmpty() || address.id == "addr-default") coll.document() else coll.document(address.id)
            docRef.set(address.copy(id = docRef.id)).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Failed to save address", e)
            Result.failure(e)
        }
    }

    suspend fun deleteAddress(uid: String, addressId: String): Result<Unit> {
        val fs = firestore ?: return Result.success(Unit)
        return try {
            fs.collection("users").document(uid).collection("addresses").document(addressId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setDefaultAddress(uid: String, addressId: String): Result<Unit> {
        val fs = firestore ?: return Result.success(Unit)
        return try {
            val coll = fs.collection("users").document(uid).collection("addresses")
            val all = coll.get().await()
            for (d in all.documents) {
                d.reference.update("isDefault", d.id == addressId).await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Order Creation
    suspend fun createOrder(order: OrderDocument): Result<String> {
        val fs = firestore ?: return Result.success("ORD-${System.currentTimeMillis() % 100000}")
        return try {
            val docRef = fs.collection("orders").document()
            val orderWithId = order.copy(
                id = docRef.id,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                statusHistory = listOf(StatusHistoryEntry("Placed Order", System.currentTimeMillis()))
            )
            docRef.set(orderWithId).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Log.e(tag, "Error creating order in Firestore", e)
            Result.failure(e)
        }
    }

    // Cancel Order (Allowed only when status == "Placed Order")
    suspend fun cancelOrder(orderId: String, reason: String): Result<Unit> {
        val fs = firestore ?: return Result.success(Unit)
        return try {
            val docRef = fs.collection("orders").document(orderId)
            val current = docRef.get().await()
            val status = current.getString("status")
            if (status != "Placed Order") {
                return Result.failure(IllegalStateException("Order can only be cancelled while status is Placed Order"))
            }

            val existingHistory = (current.get("statusHistory") as? List<*>) ?: emptyList<Any>()
            val newEntry = mapOf("status" to "Cancelled", "at" to System.currentTimeMillis())
            val updatedHistory = existingHistory + newEntry

            docRef.update(
                mapOf(
                    "status" to "Cancelled",
                    "statusHistory" to updatedHistory,
                    "cancelReason" to reason,
                    "updatedAt" to System.currentTimeMillis()
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Error cancelling order", e)
            Result.failure(e)
        }
    }

    // Customer Profile
    suspend fun createUserProfile(user: DelivioUser): Result<Unit> {
        val fs = firestore ?: return Result.success(Unit)
        return try {
            fs.collection("users").document(user.uid).set(user).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateUserProfile(uid: String, name: String, phone: String): Result<Unit> {
        val fs = firestore ?: return Result.success(Unit)
        return try {
            fs.collection("users").document(uid).update(
                mapOf(
                    "name" to name.trim(),
                    "phone" to phone.trim()
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUserProfile(uid: String): DelivioUser? {
        val fs = firestore ?: return null
        return try {
            val doc = fs.collection("users").document(uid).get().await()
            if (doc.exists()) {
                doc.toObject(DelivioUser::class.java)?.copy(uid = doc.id)
            } else null
        } catch (e: Exception) {
            null
        }
    }
}
