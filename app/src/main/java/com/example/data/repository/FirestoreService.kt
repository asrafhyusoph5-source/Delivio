package com.example.data.repository

import android.util.Log
import com.example.data.model.Banner
import com.example.data.model.BannerLink
import com.example.data.model.City
import com.example.data.model.DeliveryRates
import com.example.data.model.DelivioAddress
import com.example.data.model.DelivioUser
import com.example.data.model.LocationPoint
import com.example.data.model.Merchant
import com.example.data.model.MerchantCategory
import com.example.data.model.MerchantItem
import com.example.data.model.OrderDocument
import com.example.data.model.OrderFees
import com.example.data.model.OrderItem
import com.example.data.model.PabiliInfo
import com.example.data.model.PabiliItemInfo
import com.example.data.model.ParcelInfo
import com.example.data.model.RateConfig
import com.example.data.model.RiderLocation
import com.example.data.model.StatusHistoryEntry
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import com.example.util.DelivioStatusConstants
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import com.example.data.model.PlatformSettings
import com.example.data.model.RideRateConfig
import com.example.data.model.SurchargesConfig
import com.example.util.FirestoreErrorBus
import kotlinx.coroutines.withContext

class OrderCannotBeCancelledException(val reasonMessage: String) : Exception(reasonMessage)

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

    val inMemoryOrders = java.util.concurrent.ConcurrentHashMap<String, OrderDocument>()
    val inMemoryOrdersUpdates = MutableSharedFlow<Unit>(extraBufferCapacity = 10)

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

    internal val defaultBanners = emptyList<Banner>()

    internal val defaultMerchants = emptyList<Merchant>()

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
        val currentUser = try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser } catch (_: Exception) { null }
        if (fs == null || currentUser == null) {
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
                    val servicesMap = snapshot.get("services") as? Map<*, *>
                    val foodMap = (servicesMap?.get("food") ?: snapshot.get("food")) as? Map<*, *>
                    val convMap = (servicesMap?.get("convenience") ?: snapshot.get("convenience")) as? Map<*, *>
                    val parcelMap = (servicesMap?.get("parcel") ?: snapshot.get("parcel")) as? Map<*, *>
                    val pabiliMap = (servicesMap?.get("pabili") ?: snapshot.get("pabili")) as? Map<*, *>

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

                    // Parse rideRates
                    val rideRatesRaw = snapshot.get("rideRates") as? Map<*, *>
                    val defaultRide = defaultDeliveryRates.rideRates
                    val parsedRideRates = mutableMapOf<String, RideRateConfig>()
                    for (typeKey in listOf("motorcycle", "tricycle", "car4", "car8")) {
                        val m = rideRatesRaw?.get(typeKey) as? Map<*, *>
                        val def = defaultRide[typeKey] ?: RideRateConfig()
                        if (m != null) {
                            parsedRideRates[typeKey] = RideRateConfig(
                                enabled = (m["enabled"] as? Boolean) ?: def.enabled,
                                baseFare = (m["baseFare"] as? Number)?.toDouble() ?: def.baseFare,
                                includedKm = (m["includedKm"] as? Number)?.toDouble() ?: def.includedKm,
                                perKm = (m["perKm"] as? Number)?.toDouble() ?: (m["perKmRate"] as? Number)?.toDouble() ?: def.perKm,
                                minFare = (m["minFare"] as? Number)?.toDouble() ?: (m["minFee"] as? Number)?.toDouble() ?: def.minFare,
                                perStopFee = (m["perStopFee"] as? Number)?.toDouble() ?: def.perStopFee,
                                serviceFee = (m["serviceFee"] as? Number)?.toDouble() ?: def.serviceFee
                            )
                        } else {
                            parsedRideRates[typeKey] = def
                        }
                    }

                    val surchargesMap = snapshot.get("surcharges") as? Map<*, *>
                    val surcharges = SurchargesConfig(
                        peak = (surchargesMap?.get("peak") as? Number)?.toDouble() ?: 0.0,
                        night = (surchargesMap?.get("night") as? Number)?.toDouble() ?: 0.0
                    )

                    val rates = DeliveryRates(
                        id = snapshot.id,
                        cityId = cityId,
                        food = parseRate(foodMap),
                        convenience = parseRate(convMap),
                        parcel = parseRate(parcelMap),
                        pabili = parseRate(pabiliMap),
                        rideRates = parsedRideRates,
                        surcharges = surcharges
                    )
                    trySend(rates)
                } catch (e: Exception) {
                    trySend(defaultDeliveryRates)
                }
            }

        awaitClose { listener.remove() }
    }

    // Listen to Platform Settings (settings/platform)
    fun getPlatformSettingsFlow(): Flow<PlatformSettings> = callbackFlow {
        val fs = firestore
        val currentUser = try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser } catch (_: Exception) { null }
        if (fs == null || currentUser == null) {
            trySend(PlatformSettings())
            awaitClose {}
            return@callbackFlow
        }

        val listener = fs.collection("settings").document("platform")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) {
                    trySend(PlatformSettings())
                    return@addSnapshotListener
                }
                try {
                    val servicesMap = (snapshot.get("services") as? Map<*, *>)?.mapNotNull { (k, v) ->
                        val key = k?.toString() ?: return@mapNotNull null
                        val value = v as? Boolean ?: true
                        key to value
                    }?.toMap() ?: mapOf("food" to true, "convenience" to true, "parcel" to true, "pabili" to true, "taxi" to true)

                    val vehiclesMap = (snapshot.get("taxiVehicles") as? Map<*, *>)?.mapNotNull { (k, v) ->
                        val key = k?.toString() ?: return@mapNotNull null
                        val value = v as? Boolean ?: true
                        key to value
                    }?.toMap() ?: mapOf("motorcycle" to true, "tricycle" to true, "car4" to true, "car8" to true)

                    trySend(PlatformSettings(services = servicesMap, taxiVehicles = vehiclesMap))
                } catch (e: Exception) {
                    trySend(PlatformSettings())
                }
            }

        awaitClose { listener.remove() }
    }

    // Create Taxi Order (Cash or Atomic Wallet Batch)
    suspend fun createTaxiOrder(order: OrderDocument): Result<String> = withContext(Dispatchers.IO) {
        val fs = firestore ?: return@withContext Result.failure(Exception("Firestore not initialized"))
        val orderId = if (order.id.isNotBlank()) order.id else "order_${System.currentTimeMillis()}"
        val finalOrder = order.copy(id = orderId, serviceType = "taxi")

        try {
            if (finalOrder.paymentMethod.equals("wallet", ignoreCase = true)) {
                val batch = fs.batch()
                val orderRef = fs.collection("orders").document(orderId)
                val userRef = fs.collection("users").document(finalOrder.customerId)

                batch.set(orderRef, finalOrder)
                batch.update(userRef, "walletBalance", FieldValue.increment(-finalOrder.fees.total))
                batch.commit().await()
            } else {
                fs.collection("orders").document(orderId).set(finalOrder).await()
            }
            inMemoryOrders[orderId] = finalOrder
            inMemoryOrdersUpdates.tryEmit(Unit)
            Result.success(orderId)
        } catch (e: Exception) {
            Log.e(tag, "Failed to create taxi order", e)
            Result.failure(e)
        }
    }

    // Listen to Banners
    fun getBannersFlow(): Flow<List<Banner>> = callbackFlow {
        val fs = firestore
        val currentUser = try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser } catch (_: Exception) { null }
        if (fs == null || currentUser == null) {
            trySend(emptyList())
            awaitClose {}
            return@callbackFlow
        }

        val listener = fs.collection("banners")
            .whereEqualTo("status", "active")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || snapshot.isEmpty) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val banners = snapshot.documents.mapNotNull { doc ->
                    val linkMap = doc.get("link") as? Map<*, *>
                    Banner(
                        id = doc.id,
                        title = doc.getString("title") ?: "",
                        imageUrl = doc.getString("imageUrl")?.trim() ?: "",
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
                trySend(banners)
            }

        awaitClose { listener.remove() }
    }

    // Listen to Merchants with one query where status == "active" (trimmed, lowercase in code)
    // No composite queries and no orderBy
    fun getMerchantsFlow(cityId: String = ""): Flow<List<Merchant>> = callbackFlow {
        val fs = firestore
        val currentUser = try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser } catch (_: Exception) { null }
        if (fs == null || currentUser == null) {
            trySend(emptyList())
            awaitClose {}
            return@callbackFlow
        }

        // Single query where status == "active"
        val query = fs.collection("merchants").whereEqualTo("status", "active")

        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(tag, "Error loading merchants", error)
                FirestoreErrorBus.emitError("query", "merchants", error)
                trySend(emptyList())
                return@addSnapshotListener
            }
            if (snapshot == null || snapshot.isEmpty) {
                trySend(emptyList())
                return@addSnapshotListener
            }

            val merchantList = mutableListOf<Merchant>()
            for (doc in snapshot.documents) {
                val rawStatus = doc.getString("status")?.trim()?.lowercase(java.util.Locale.ROOT) ?: "active"
                val tags = (doc.get("cuisineTags") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                val m = Merchant(
                    id = doc.id,
                    name = doc.getString("name") ?: "",
                    type = doc.getString("type") ?: "food",
                    status = rawStatus,
                    cityId = doc.getString("cityId") ?: "",
                    isOpen = doc.getBoolean("isOpen") ?: true,
                    rating = doc.getDouble("rating") ?: 4.8,
                    deliveryTime = doc.getString("deliveryTime") ?: "20-30 min",
                    address = doc.getString("address") ?: "",
                    lat = doc.getDouble("lat") ?: 9.7420,
                    lng = doc.getDouble("lng") ?: 118.7400,
                    cuisineTags = tags,
                    logoUrl = doc.getString("logoUrl")?.trim() ?: "",
                    bannerUrl = doc.getString("bannerUrl")?.trim() ?: "",
                    imageUrl = doc.getString("imageUrl")?.trim() ?: "",
                    hours = doc.get("hours") as? Map<String, Any>
                )
                merchantList.add(m)
            }
            trySend(merchantList)
        }

        awaitClose { listener.remove() }
    }

    // Update Merchant Shop Profile
    suspend fun updateMerchantProfile(
        merchantId: String,
        name: String? = null,
        address: String? = null,
        logoUrl: String? = null,
        bannerUrl: String? = null,
        imageUrl: String? = null
    ): Result<Unit> {
        val fs = firestore ?: return Result.success(Unit)
        return try {
            val updates = mutableMapOf<String, Any>()
            name?.let { updates["name"] = it.trim() }
            address?.let { updates["address"] = it.trim() }
            logoUrl?.let { updates["logoUrl"] = it.trim() }
            bannerUrl?.let { updates["bannerUrl"] = it.trim() }
            imageUrl?.let { updates["imageUrl"] = it.trim() }
            if (updates.isNotEmpty()) {
                fs.collection("merchants").document(merchantId).update(updates).await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Save or Update Merchant Menu Item
    suspend fun saveMerchantItem(
        merchantId: String,
        item: MerchantItem
    ): Result<Unit> {
        val fs = firestore ?: return Result.success(Unit)
        return try {
            val itemId = item.id.ifEmpty { "item-${System.currentTimeMillis()}" }
            val itemData = mapOf(
                "categoryId" to item.categoryId.trim(),
                "name" to item.name.trim(),
                "price" to item.price,
                "description" to item.description.trim(),
                "imageUrl" to item.imageUrl.trim(),
                "isAvailable" to item.isAvailable
            )
            fs.collection("merchants").document(merchantId)
                .collection("items").document(itemId)
                .set(itemData).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
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
        if (fs == null || merchantId.isBlank()) {
            val fallback = defaultMerchants.find { it.id == merchantId }?.items ?: emptyList()
            trySend(fallback)
            awaitClose {}
            return@callbackFlow
        }

        val listener = fs.collection("merchants").document(merchantId).collection("items")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(tag, "Error loading items for merchant $merchantId", error)
                    FirestoreErrorBus.emitError("query", "merchants/$merchantId/items", error)
                    val fallback = defaultMerchants.find { it.id == merchantId }?.items ?: emptyList()
                    trySend(fallback)
                    return@addSnapshotListener
                }
                if (snapshot == null || snapshot.isEmpty) {
                    val fallback = defaultMerchants.find { it.id == merchantId }?.items ?: emptyList()
                    trySend(fallback)
                    return@addSnapshotListener
                }
                val items = snapshot.documents.mapNotNull { doc ->
                    val isAvail = doc.getBoolean("available") ?: doc.getBoolean("isAvailable") ?: true
                    if (!isAvail) return@mapNotNull null
                    val catName = doc.getString("categoryName")?.trim()
                        ?: doc.getString("category")?.trim()
                        ?: ""
                    MerchantItem(
                        id = doc.id,
                        categoryId = doc.getString("categoryId") ?: "",
                        categoryName = catName,
                        name = doc.getString("name") ?: "",
                        price = doc.getDouble("price") ?: 0.0,
                        description = doc.getString("description") ?: "",
                        imageUrl = doc.getString("imageUrl")?.trim() ?: "",
                        isAvailable = true
                    )
                }
                trySend(items)
            }

        awaitClose { listener.remove() }
    }

    // Real-time snapshot listener for a single order (orders/{orderId})
    fun getOrderFlow(orderId: String): Flow<OrderDocument?> = callbackFlow {
        val fs = firestore
        if (fs == null || orderId.isEmpty()) {
            val job = CoroutineScope(Dispatchers.Default).launch {
                trySend(inMemoryOrders[orderId])
                inMemoryOrdersUpdates.collect {
                    trySend(inMemoryOrders[orderId])
                }
            }
            awaitClose { job.cancel() }
            return@callbackFlow
        }

        val listener = fs.collection("orders").document(orderId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(tag, "Error listening to order $orderId", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val orderDoc = parseOrderDocument(snapshot)
                    trySend(orderDoc)
                }
            }

        awaitClose { listener.remove() }
    }

    // Listen to Customer Orders (query customerId == uid, ordered by createdAt descending)
    fun getCustomerOrdersFlow(customerId: String): Flow<List<OrderDocument>> = callbackFlow {
        val fs = firestore
        val currentUser = try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser } catch (_: Exception) { null }
        if (fs == null || customerId.isEmpty() || currentUser == null) {
            val job = CoroutineScope(Dispatchers.Default).launch {
                val sendCurrent = {
                    val list = inMemoryOrders.values.filter {
                        customerId.isEmpty() || it.customerId == customerId || it.customerId == "guest-customer-uid"
                    }.sortedByDescending { it.createdAt }
                    trySend(list)
                }
                sendCurrent()
                inMemoryOrdersUpdates.collect {
                    sendCurrent()
                }
            }
            awaitClose { job.cancel() }
            return@callbackFlow
        }

        var fallbackRegistration: ListenerRegistration? = null
        val primaryListener = fs.collection("orders")
            .whereEqualTo("customerId", customerId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(tag, "Index error or query failed for customer orders ordered by createdAt, falling back to equality query", error)
                    if (fallbackRegistration == null) {
                        fallbackRegistration = fs.collection("orders")
                            .whereEqualTo("customerId", customerId)
                            .addSnapshotListener { snap, err ->
                                if (err != null) {
                                    Log.e(tag, "Fallback equality listener failed", err)
                                    return@addSnapshotListener
                                }
                                if (snap != null) {
                                    val list = snap.documents.mapNotNull { parseOrderDocument(it) }
                                        .sortedByDescending { it.createdAt }
                                    trySend(list)
                                }
                            }
                    }
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { parseOrderDocument(it) }
                        .sortedByDescending { it.createdAt }
                    trySend(list)
                }
            }

        awaitClose {
            primaryListener.remove()
            fallbackRegistration?.remove()
        }
    }

    private fun parseOrderDocument(doc: DocumentSnapshot): OrderDocument? {
        if (!doc.exists()) return null
        return try {
            val data = doc.data ?: return null

            val createdLong = when (val c = data["createdAt"]) {
                is Timestamp -> c.toDate().time
                is Number -> c.toLong()
                else -> System.currentTimeMillis()
            }
            val updatedLong = when (val u = data["updatedAt"]) {
                is Timestamp -> u.toDate().time
                is Number -> u.toLong()
                else -> System.currentTimeMillis()
            }

            val itemsRaw = data["items"] as? List<*>
            val itemsList = itemsRaw?.mapNotNull { itemObj ->
                if (itemObj is Map<*, *>) {
                    OrderItem(
                        name = itemObj["name"] as? String ?: "",
                        qty = (itemObj["qty"] as? Number)?.toInt()
                            ?: (itemObj["quantity"] as? Number)?.toInt() ?: 1,
                        price = (itemObj["price"] as? Number)?.toDouble() ?: 0.0,
                        notes = (itemObj["notes"] as? String)
                            ?: (itemObj["specialInstructions"] as? String) ?: ""
                    )
                } else null
            } ?: emptyList()

            val statusHistoryRaw = data["statusHistory"] as? List<*>
            val statusHistoryList = statusHistoryRaw?.mapNotNull { entry ->
                if (entry is Map<*, *>) {
                    val st = entry["status"] as? String ?: "Placed Order"
                    val at = when (val atVal = entry["at"]) {
                        is Timestamp -> atVal.toDate().time
                        is Number -> atVal.toLong()
                        else -> System.currentTimeMillis()
                    }
                    StatusHistoryEntry(status = st, at = at)
                } else null
            } ?: listOf(StatusHistoryEntry("Placed Order", createdLong))

            val pickupMap = data["pickup"] as? Map<*, *>
            val pickup = LocationPoint(
                label = pickupMap?.get("label") as? String ?: "",
                address = pickupMap?.get("address") as? String ?: "",
                lat = (pickupMap?.get("lat") as? Number)?.toDouble() ?: 0.0,
                lng = (pickupMap?.get("lng") as? Number)?.toDouble() ?: 0.0,
                contactName = pickupMap?.get("contactName") as? String ?: "",
                contactPhone = pickupMap?.get("contactPhone") as? String ?: ""
            )

            val dropoffMap = data["dropoff"] as? Map<*, *>
            val dropoff = if (dropoffMap != null) {
                LocationPoint(
                    label = dropoffMap["label"] as? String ?: "",
                    address = dropoffMap["address"] as? String ?: "",
                    lat = (dropoffMap["lat"] as? Number)?.toDouble() ?: 0.0,
                    lng = (dropoffMap["lng"] as? Number)?.toDouble() ?: 0.0,
                    contactName = dropoffMap["contactName"] as? String ?: "",
                    contactPhone = dropoffMap["contactPhone"] as? String ?: ""
                )
            } else null

            val feesMap = data["fees"] as? Map<*, *>
            val fees = OrderFees(
                subtotal = (feesMap?.get("subtotal") as? Number)?.toDouble() ?: 0.0,
                deliveryFee = (feesMap?.get("deliveryFee") as? Number)?.toDouble() ?: 0.0,
                serviceFee = (feesMap?.get("serviceFee") as? Number)?.toDouble() ?: 0.0,
                discount = (feesMap?.get("discount") as? Number)?.toDouble() ?: 0.0,
                tip = (feesMap?.get("tip") as? Number)?.toDouble() ?: 0.0,
                total = (feesMap?.get("total") as? Number)?.toDouble() ?: 0.0,
                estimatedItemsTotal = (feesMap?.get("estimatedItemsTotal") as? Number)?.toDouble() ?: 0.0,
                distanceKm = (feesMap?.get("distanceKm") as? Number)?.toDouble() ?: 0.0,
                distanceMethod = feesMap?.get("distanceMethod") as? String ?: "osrm"
            )

            val riderLocMap = data["riderLocation"] as? Map<*, *>
            val riderLoc = if (riderLocMap != null) {
                RiderLocation(
                    lat = (riderLocMap["lat"] as? Number)?.toDouble() ?: 0.0,
                    lng = (riderLocMap["lng"] as? Number)?.toDouble() ?: 0.0,
                    updatedAt = when (val u = riderLocMap["updatedAt"]) {
                        is Timestamp -> u.toDate().time
                        is Number -> u.toLong()
                        else -> System.currentTimeMillis()
                    }
                )
            } else null

            val pabiliMap = data["pabili"] as? Map<*, *>
            val pabili = if (pabiliMap != null) {
                val pItemsRaw = pabiliMap["items"] as? List<*>
                val pItems = pItemsRaw?.mapNotNull { pi ->
                    if (pi is Map<*, *>) {
                        PabiliItemInfo(
                            name = pi["name"] as? String ?: "",
                            quantity = (pi["quantity"] as? Number)?.toInt() ?: 1,
                            estimatedPrice = (pi["estimatedPrice"] as? Number)?.toDouble() ?: 0.0
                        )
                    } else null
                } ?: emptyList()

                val pPickupMap = pabiliMap["pickup"] as? Map<*, *>
                val pPickup = if (pPickupMap != null) {
                    LocationPoint(
                        label = pPickupMap["label"] as? String ?: "",
                        address = pPickupMap["address"] as? String ?: "",
                        lat = (pPickupMap["lat"] as? Number)?.toDouble() ?: 0.0,
                        lng = (pPickupMap["lng"] as? Number)?.toDouble() ?: 0.0,
                        contactName = pPickupMap["contactName"] as? String ?: "",
                        contactPhone = pPickupMap["contactPhone"] as? String ?: ""
                    )
                } else null

                PabiliInfo(
                    vendorName = pabiliMap["vendorName"] as? String ?: "",
                    items = pItems,
                    budget = (pabiliMap["budget"] as? Number)?.toDouble() ?: 1000.0,
                    finalReceiptTotal = (pabiliMap["finalReceiptTotal"] as? Number)?.toDouble(),
                    pickup = pPickup
                )
            } else null

            val parcelMap = data["parcel"] as? Map<*, *>
            val parcel = if (parcelMap != null) {
                ParcelInfo(
                    size = parcelMap["size"] as? String ?: "Small",
                    weightKg = (parcelMap["weightKg"] as? Number)?.toDouble() ?: 1.0,
                    description = parcelMap["description"] as? String ?: "",
                    fragile = parcelMap["fragile"] as? Boolean ?: false,
                    feePayer = parcelMap["feePayer"] as? String ?: "Sender",
                    photoUrl = parcelMap["photoUrl"] as? String
                )
            } else null

            OrderDocument(
                id = doc.id,
                serviceType = data["serviceType"] as? String ?: "food",
                customerId = data["customerId"] as? String ?: "",
                customerName = data["customerName"] as? String ?: "",
                customerPhone = data["customerPhone"] as? String ?: "",
                merchantId = data["merchantId"] as? String,
                merchantName = data["merchantName"] as? String,
                riderId = data["riderId"] as? String,
                riderName = data["riderName"] as? String,
                riderPhone = data["riderPhone"] as? String,
                riderLocation = riderLoc,
                status = data["status"] as? String ?: "Placed Order",
                statusHistory = statusHistoryList,
                items = itemsList,
                pickup = pickup,
                dropoff = dropoff,
                parcel = parcel,
                pabili = pabili,
                fees = fees,
                paymentMethod = data["paymentMethod"] as? String ?: "cod",
                paymentProvider = data["paymentProvider"] as? String,
                paymentStatus = data["paymentStatus"] as? String ?: if ((data["paymentMethod"] as? String) == "wallet") "paid" else "unpaid",
                cashToCollect = (data["cashToCollect"] as? Number)?.toDouble() ?: if ((data["paymentMethod"] as? String) == "wallet") 0.0 else fees.total,
                cashCollectAt = data["cashCollectAt"] as? String,
                walletRefunded = data["walletRefunded"] as? Boolean ?: false,
                cityId = data["cityId"] as? String ?: "",
                cancelReason = data["cancelReason"] as? String,
                rejectReason = data["rejectReason"] as? String,
                fulfillment = data["fulfillment"] as? String ?: "delivery",
                scheduledFor = when (val sf = data["scheduledFor"]) {
                    is Timestamp -> sf.toDate().time
                    is Number -> sf.toLong()
                    else -> null
                },
                createdAt = createdLong,
                updatedAt = updatedLong
            )
        } catch (e: Exception) {
            Log.e(tag, "Error parsing order document ${doc.id}", e)
            null
        }
    }

    // Listen to Customer Addresses (users/{uid}/addresses)
    fun getAddressesFlow(uid: String): Flow<List<DelivioAddress>> = callbackFlow {
        val fs = firestore
        val currentUser = try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser } catch (_: Exception) { null }
        if (fs == null || uid.isEmpty() || currentUser == null) {
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

    // Order Creation: Writes a real document to Firestore orders/{newId}
    suspend fun createOrder(order: OrderDocument): Result<String> {
        val fs = firestore
        if (fs == null) {
            val generatedId = "order-" + java.util.UUID.randomUUID().toString().take(8)
            val now = System.currentTimeMillis()
            val savedDoc = order.copy(
                id = generatedId,
                status = DelivioStatusConstants.STATUS_PLACED_ORDER,
                statusHistory = listOf(StatusHistoryEntry(DelivioStatusConstants.STATUS_PLACED_ORDER, now)),
                createdAt = now,
                updatedAt = now
            )
            inMemoryOrders[generatedId] = savedDoc
            inMemoryOrdersUpdates.tryEmit(Unit)
            return Result.success(generatedId)
        }
        return try {
            val docRef = fs.collection("orders").document()
            val now = System.currentTimeMillis()

            val orderMap = hashMapOf<String, Any?>(
                "customerId" to order.customerId,
                "customerName" to order.customerName,
                "customerPhone" to order.customerPhone,
                "serviceType" to order.serviceType,
                "merchantId" to order.merchantId, // Document ID or null for parcel and pabili
                "merchantName" to order.merchantName,
                "riderId" to null, // Always write this field explicitly as null
                "riderName" to null,
                "riderPhone" to null,
                "riderLocation" to null,
                "status" to "Placed Order",
                "statusHistory" to listOf(
                    mapOf("status" to "Placed Order", "at" to now)
                ),
                "items" to order.items.map { item ->
                    mapOf(
                        "name" to item.name,
                        "qty" to item.qty,
                        "quantity" to item.qty,
                        "price" to item.price,
                        "notes" to item.notes,
                        "specialInstructions" to item.notes
                    )
                },
                "pickup" to mapOf(
                    "label" to order.pickup.label,
                    "address" to order.pickup.address,
                    "lat" to order.pickup.lat,
                    "lng" to order.pickup.lng,
                    "contactName" to order.pickup.contactName,
                    "contactPhone" to order.pickup.contactPhone
                ),
                "dropoff" to if (order.fulfillment == DelivioStatusConstants.FULFILLMENT_PICKUP || order.dropoff == null) null else mapOf(
                    "label" to order.dropoff.label,
                    "address" to order.dropoff.address,
                    "lat" to order.dropoff.lat,
                    "lng" to order.dropoff.lng,
                    "contactName" to order.dropoff.contactName,
                    "contactPhone" to order.dropoff.contactPhone
                ),
                "fees" to mapOf(
                    "subtotal" to order.fees.subtotal,
                    "deliveryFee" to order.fees.deliveryFee,
                    "serviceFee" to order.fees.serviceFee,
                    "discount" to order.fees.discount,
                    "tip" to order.fees.tip,
                    "total" to order.fees.total,
                    "estimatedItemsTotal" to order.fees.estimatedItemsTotal,
                    "distanceKm" to order.fees.distanceKm,
                    "distanceMethod" to order.fees.distanceMethod
                ),
                "paymentMethod" to order.paymentMethod,
                "paymentProvider" to order.paymentProvider,
                "paymentStatus" to order.paymentStatus,
                "cashToCollect" to order.cashToCollect,
                "cashCollectAt" to order.cashCollectAt,
                "walletRefunded" to order.walletRefunded,
                "cityId" to order.cityId,
                "fulfillment" to order.fulfillment,
                "scheduledFor" to (order.scheduledFor?.let { Timestamp(java.util.Date(it)) }),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            if (order.parcel != null) {
                orderMap["parcel"] = mapOf(
                    "size" to order.parcel.size,
                    "weightKg" to order.parcel.weightKg,
                    "description" to order.parcel.description,
                    "fragile" to order.parcel.fragile,
                    "feePayer" to order.parcel.feePayer,
                    "photoUrl" to order.parcel.photoUrl
                )
            }

            if (order.pabili != null) {
                val pabiliPickup = order.pabili.pickup
                val pickupMap = if (pabiliPickup != null) {
                    mapOf(
                        "label" to pabiliPickup.label,
                        "address" to pabiliPickup.address,
                        "lat" to pabiliPickup.lat,
                        "lng" to pabiliPickup.lng,
                        "contactName" to pabiliPickup.contactName,
                        "contactPhone" to pabiliPickup.contactPhone
                    )
                } else null

                orderMap["pabili"] = mapOf(
                    "vendorName" to order.pabili.vendorName,
                    "items" to order.pabili.items.map {
                        mapOf(
                            "name" to it.name,
                            "quantity" to it.quantity,
                            "estimatedPrice" to it.estimatedPrice
                        )
                    },
                    "budget" to order.pabili.budget,
                    "finalReceiptTotal" to order.pabili.finalReceiptTotal,
                    "pickup" to pickupMap
                )
            }

            if (order.paymentMethod == "wallet") {
                val userRef = fs.collection("users").document(order.customerId)
                val batch = fs.batch()
                batch.set(docRef, orderMap)
                batch.update(userRef, mapOf("walletBalance" to FieldValue.increment(-order.fees.total)))
                batch.commit().await()
            } else {
                docRef.set(orderMap).await()
            }
            Result.success(docRef.id)
        } catch (e: Exception) {
            Log.e(tag, "Error creating order in Firestore", e)
            Result.failure(e)
        }
    }

    // Re-read latest order document
    suspend fun getLatestOrder(orderId: String): OrderDocument? {
        val fs = firestore ?: return inMemoryOrders[orderId]
        return try {
            val doc = fs.collection("orders").document(orderId).get().await()
            parseOrderDocument(doc)
        } catch (e: Exception) {
            null
        }
    }

    // Cancel Order (Allowed only when status == "Placed Order")
    // Updates only: status "Cancelled", cancelReason, statusHistory (arrayUnion of {status, at: Timestamp.now()}), updatedAt
    suspend fun cancelOrder(orderId: String, reason: String): Result<Unit> {
        val fs = firestore
        if (fs == null) {
            val current = inMemoryOrders[orderId] ?: return Result.failure(IllegalStateException("Order not found"))
            val now = System.currentTimeMillis()
            val updatedHistory = current.statusHistory + StatusHistoryEntry("Cancelled", now)
            inMemoryOrders[orderId] = current.copy(
                status = "Cancelled",
                cancelReason = reason,
                statusHistory = updatedHistory,
                updatedAt = now
            )
            inMemoryOrdersUpdates.tryEmit(Unit)
            return Result.success(Unit)
        }
        return try {
            val docRef = fs.collection("orders").document(orderId)
            val current = docRef.get().await()
            if (!current.exists()) {
                return Result.failure(IllegalStateException("Order not found"))
            }

            val status = current.getString("status") ?: ""
            val isPlaced = status.equals("Placed Order", ignoreCase = true) ||
                    status.equals("placed", ignoreCase = true) ||
                    status.equals("pending", ignoreCase = true)

            val serviceType = (current.getString("serviceType") ?: "food").lowercase()
            val riderId = current.getString("riderId")
            val isTaxi = serviceType == "taxi"
            val isParcelOrPabili = serviceType in listOf("parcel", "pabili") || current.getString("merchantId").isNullOrEmpty()

            if (isTaxi) {
                val canCancelTaxi = status.equals("Placed Order", ignoreCase = true) ||
                        status.equals("placed", ignoreCase = true) ||
                        status.equals("pending", ignoreCase = true) ||
                        status.equals("Looking for a driver...", ignoreCase = true) ||
                        status.equals("Driver is on the way", ignoreCase = true)
                if (!canCancelTaxi) {
                    val message = if (status.equals("Driver arrived", ignoreCase = true)) {
                        "You can't cancel the ride because the driver has already arrived."
                    } else if (status.equals("On trip", ignoreCase = true)) {
                        "You can't cancel the ride while on trip."
                    } else {
                        "This ride cannot be cancelled."
                    }
                    return Result.failure(OrderCannotBeCancelledException(message))
                }
            } else if (!isPlaced) {
                val message = if (isParcelOrPabili && (riderId != null || !isPlaced)) {
                    "You can't cancel the order because the rider already accepted it."
                } else {
                    "You can't cancel the order because the merchant already prepared the order."
                }
                return Result.failure(OrderCannotBeCancelledException(message))
            }

            val cancelEntry = mapOf(
                "status" to "Cancelled",
                "at" to Timestamp.now()
            )

            val updates = mapOf(
                "status" to "Cancelled",
                "cancelReason" to reason.trim().ifEmpty { "Cancelled by customer" },
                "statusHistory" to FieldValue.arrayUnion(cancelEntry),
                "updatedAt" to Timestamp.now()
            )

            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Error cancelling order", e)
            val isPermissionDenied = (e is com.google.firebase.firestore.FirebaseFirestoreException &&
                    e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) ||
                    (e.message?.contains("PERMISSION_DENIED", ignoreCase = true) == true)
            if (isPermissionDenied) {
                // The merchant accepted at the same moment
                Result.failure(OrderCannotBeCancelledException("PERMISSION_DENIED"))
            } else {
                Result.failure(e)
            }
        }
    }

    // Customer Profile
    suspend fun createUserProfile(user: DelivioUser): Result<Unit> {
        val fs = firestore ?: return Result.success(Unit)
        return try {
            fs.collection("users").document(user.uid).set(user).await()
            Result.success(Unit)
        } catch (e: Exception) {
            FirestoreErrorBus.emitError("set", "users/${user.uid}", e)
            Result.failure(e)
        }
    }

    suspend fun updateUserProfileFields(uid: String, changedFields: Map<String, Any>): Result<Unit> {
        val fs = firestore ?: return Result.success(Unit)
        return try {
            // Strictly exclude protected fields to prevent rule violations
            val forbidden = setOf("role", "email", "status", "createdAt", "merchantId", "staffRole", "walletBalance")
            val safeUpdates = changedFields.filterKeys { !forbidden.contains(it) }.toMutableMap()
            if (safeUpdates.isEmpty()) {
                return Result.success(Unit)
            }
            if (!safeUpdates.containsKey("updatedAt")) {
                safeUpdates["updatedAt"] = FieldValue.serverTimestamp()
            }
            fs.collection("users").document(uid).update(safeUpdates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            FirestoreErrorBus.emitError("update", "users/$uid", e)
            Result.failure(e)
        }
    }

    suspend fun updateUserProfile(uid: String, name: String, phone: String): Result<Unit> {
        val updates = mutableMapOf<String, Any>()
        if (name.isNotBlank()) updates["name"] = name.trim()
        if (phone.isNotBlank()) updates["phone"] = phone.trim()
        updates["updatedAt"] = FieldValue.serverTimestamp()
        return updateUserProfileFields(uid, updates)
    }

    suspend fun getUserProfile(uid: String): DelivioUser? {
        val fs = firestore ?: return null
        return try {
            val doc = fs.collection("users").document(uid).get().await()
            if (doc.exists()) {
                doc.toObject(DelivioUser::class.java)?.copy(uid = doc.id)
            } else null
        } catch (e: Exception) {
            FirestoreErrorBus.emitError("get", "users/$uid", e)
            null
        }
    }

    // Live listener for user profile from users/{uid}
    fun observeUserProfile(uid: String): Flow<DelivioUser?> = callbackFlow {
        val fs = firestore
        val currentUser = try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser } catch (_: Exception) { null }
        if (fs == null || uid.isEmpty() || currentUser == null) {
            trySend(null)
            awaitClose {}
            return@callbackFlow
        }
        val listener = fs.collection("users").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(tag, "Error listening to user profile: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val user = snapshot.toObject(DelivioUser::class.java)?.copy(uid = snapshot.id)
                    trySend(user)
                } else {
                    trySend(null)
                }
            }
        awaitClose { listener.remove() }
    }

    private val refundGuard = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    // Wallet Refund: atomic batch updating orders/{id} {walletRefunded: true, updatedAt} and users/{uid} {walletBalance: FieldValue.increment(+fees.total), walletRefundOrderId: id}
    suspend fun processWalletRefund(orderId: String, customerId: String, totalAmount: Double): Result<String> {
        if (!refundGuard.add(orderId)) {
            return Result.success("Already processed")
        }
        val fs = firestore
        if (fs == null) {
            val order = inMemoryOrders[orderId]
            if (order != null && !order.walletRefunded) {
                inMemoryOrders[orderId] = order.copy(walletRefunded = true)
                inMemoryOrdersUpdates.tryEmit(Unit)
            }
            return Result.success("₱${String.format(java.util.Locale.US, "%.2f", totalAmount)} refunded to your wallet")
        }
        return try {
            val orderRef = fs.collection("orders").document(orderId)
            val userRef = fs.collection("users").document(customerId)
            val batch = fs.batch()
            batch.update(orderRef, mapOf(
                "walletRefunded" to true,
                "updatedAt" to FieldValue.serverTimestamp()
            ))
            batch.update(userRef, mapOf(
                "walletBalance" to FieldValue.increment(totalAmount),
                "walletRefundOrderId" to orderId
            ))
            batch.commit().await()
            Result.success("₱${String.format(java.util.Locale.US, "%.2f", totalAmount)} refunded to your wallet")
        } catch (e: Exception) {
            refundGuard.remove(orderId)
            Log.e(tag, "Wallet refund failed", e)
            val isPermissionDenied = (e is com.google.firebase.firestore.FirebaseFirestoreException &&
                    e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) ||
                    e.message?.contains("PERMISSION_DENIED", ignoreCase = true) == true
            if (isPermissionDenied) {
                Result.failure(Exception("Refund pending - contact support"))
            } else {
                Result.failure(e)
            }
        }
    }

    // Fetch wallet transactions for user
    suspend fun getWalletTransactions(uid: String): List<com.example.data.model.WalletTransaction> {
        val fs = firestore ?: return emptyList()
        return try {
            val snapshot = fs.collection("walletTransactions")
                .whereEqualTo("customerId", uid)
                .get()
                .await()
            snapshot.documents.mapNotNull { doc ->
                val amount = (doc.get("amount") as? Number)?.toDouble() ?: 0.0
                val type = doc.getString("type") ?: "cash_in"
                val description = doc.getString("description") ?: ""
                val createdAt = doc.getTimestamp("createdAt")?.toDate()?.time ?: (doc.get("createdAt") as? Number)?.toLong() ?: System.currentTimeMillis()
                com.example.data.model.WalletTransaction(
                    id = doc.id,
                    customerId = uid,
                    type = type,
                    amount = amount,
                    description = description,
                    createdAt = createdAt
                )
            }
        } catch (e: Exception) {
            Log.w(tag, "Error fetching wallet transactions: ${e.message}")
            emptyList()
        }
    }

    suspend fun submitReview(
        orderId: String,
        customerId: String,
        riderId: String?,
        rating: Int,
        comment: String
    ): Result<Unit> {
        val fs = firestore ?: return Result.failure(Exception("Firestore not initialized"))
        return try {
            val reviewData = hashMapOf(
                "orderId" to orderId,
                "customerId" to customerId,
                "riderId" to riderId,
                "rating" to rating,
                "comment" to comment,
                "createdAt" to System.currentTimeMillis()
            )
            fs.collection("reviews").document(orderId).set(reviewData).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Error submitting review for $orderId: ${e.message}", e)
            Result.failure(e)
        }
    }
}
