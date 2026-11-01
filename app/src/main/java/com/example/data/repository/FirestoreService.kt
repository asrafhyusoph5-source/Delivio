package com.example.data.repository

import android.util.Log
import com.example.data.model.*
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
        food = RateConfig(baseFare = 39.0, includedKm = 2.0, perKmRate = 10.0, minFee = 39.0, maxFee = 250.0, serviceFee = 10.0, surcharge = 0.0),
        convenience = RateConfig(baseFare = 45.0, includedKm = 2.0, perKmRate = 12.0, minFee = 45.0, maxFee = 280.0, serviceFee = 12.0, surcharge = 0.0),
        parcel = RateConfig(baseFare = 50.0, includedKm = 3.0, perKmRate = 14.0, minFee = 50.0, maxFee = 350.0, serviceFee = 15.0, surcharge = 0.0),
        pabili = RateConfig(baseFare = 55.0, includedKm = 2.0, perKmRate = 15.0, minFee = 55.0, maxFee = 400.0, serviceFee = 20.0, surcharge = 0.0)
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
                    if (error is com.google.firebase.firestore.FirebaseFirestoreException) {
                        FirestoreErrorBus.showNonFatalBanner("cities", error.code.name)
                    }
                    trySend(listOf(defaultCity))
                    return@addSnapshotListener
                }
                if (snapshot != null && !snapshot.isEmpty) {
                    val cities = snapshot.documents.mapNotNull { doc ->
                        try {
                            val center = doc.get("center") as? Map<*, *>
                            val lat = (center?.get("lat") as? Number)?.toDouble() ?: (doc.get("lat") as? Number)?.toDouble() ?: 9.7392
                            val lng = (center?.get("lng") as? Number)?.toDouble() ?: (doc.get("lng") as? Number)?.toDouble() ?: 118.7353
                            val radiusKm = (doc.get("radiusKm") as? Number)?.toDouble() ?: 25.0
                            val servicesMap = doc.get("services") as? Map<*, *>
                            val services = if (servicesMap != null) {
                                mapOf(
                                    "food" to (servicesMap["food"] as? Boolean ?: true),
                                    "convenience" to (servicesMap["convenience"] as? Boolean ?: true),
                                    "parcel" to (servicesMap["parcel"] as? Boolean ?: true),
                                    "pabili" to (servicesMap["pabili"] as? Boolean ?: true),
                                    "taxi" to (servicesMap["taxi"] as? Boolean ?: true)
                                )
                            } else {
                                mapOf(
                                    "food" to true,
                                    "convenience" to true,
                                    "parcel" to true,
                                    "pabili" to true,
                                    "taxi" to true
                                )
                            }
                            City(
                                id = doc.id,
                                name = doc.getString("name") ?: "",
                                province = doc.getString("province") ?: "",
                                status = doc.getString("status") ?: "active",
                                lat = lat,
                                lng = lng,
                                radiusKm = radiusKm,
                                services = services
                            )
                        } catch (e: Exception) {
                            Log.w(tag, "Skipping bad city doc ${doc.id}: ${e.message}")
                            null
                        }
                    }
                    trySend(if (cities.isNotEmpty()) cities else listOf(defaultCity))
                } else {
                    trySend(listOf(defaultCity))
                }
            }

        awaitClose { listener.remove() }
    }

    // Listen live to a single city document: cities/{cityId}
    fun getCityDocumentFlow(cityId: String): Flow<City?> = callbackFlow {
        val fs = firestore
        if (fs == null || cityId.isBlank()) {
            trySend(null)
            awaitClose {}
            return@callbackFlow
        }

        val listener = fs.collection("cities").document(cityId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    if (error is com.google.firebase.firestore.FirebaseFirestoreException) {
                        FirestoreErrorBus.showNonFatalBanner("city", error.code.name)
                    }
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    try {
                        val center = snapshot.get("center") as? Map<*, *>
                        val lat = (center?.get("lat") as? Number)?.toDouble() ?: (snapshot.get("lat") as? Number)?.toDouble() ?: 9.7392
                        val lng = (center?.get("lng") as? Number)?.toDouble() ?: (snapshot.get("lng") as? Number)?.toDouble() ?: 118.7353
                        val radiusKm = (snapshot.get("radiusKm") as? Number)?.toDouble() ?: 25.0
                        val servicesMap = snapshot.get("services") as? Map<*, *>
                        val services = if (servicesMap != null) {
                            mapOf(
                                "food" to (servicesMap["food"] as? Boolean ?: true),
                                "convenience" to (servicesMap["convenience"] as? Boolean ?: true),
                                "parcel" to (servicesMap["parcel"] as? Boolean ?: true),
                                "pabili" to (servicesMap["pabili"] as? Boolean ?: true),
                                "taxi" to (servicesMap["taxi"] as? Boolean ?: true)
                            )
                        } else {
                            mapOf(
                                "food" to true,
                                "convenience" to true,
                                "parcel" to true,
                                "pabili" to true,
                                "taxi" to true
                            )
                        }
                        val city = City(
                            id = snapshot.id,
                            name = snapshot.getString("name") ?: "",
                            province = snapshot.getString("province") ?: "",
                            status = snapshot.getString("status") ?: "active",
                            lat = lat,
                            lng = lng,
                            radiusKm = radiusKm,
                            services = services
                        )
                        trySend(city)
                    } catch (e: Exception) {
                        Log.w(tag, "Error parsing city $cityId: ${e.message}")
                    }
                }
            }

        awaitClose { listener.remove() }
    }

    // Re-check whether a service is active for a city (read live document snapshots from server)
    suspend fun checkServiceOn(service: String, cityId: String): Boolean = withContext(Dispatchers.IO) {
        val fs = firestore ?: return@withContext true
        val svc = service.lowercase().trim()
        try {
            val platformDoc = fs.collection("settings").document("platform").get().await()
            val platformServices = platformDoc.get("services") as? Map<*, *>
            val platformOn = platformServices?.get(svc) as? Boolean ?: true
            if (!platformOn) return@withContext false

            if (cityId.isNotBlank()) {
                val cityDoc = fs.collection("cities").document(cityId).get().await()
                if (cityDoc.exists()) {
                    val cityServices = cityDoc.get("services") as? Map<*, *>
                    val cityOn = cityServices?.get(svc) as? Boolean ?: true
                    if (!cityOn) return@withContext false
                }
            }
            true
        } catch (e: Exception) {
            Log.w(tag, "Error checking serviceOn for $service in $cityId: ${e.message}")
            true
        }
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
                if (error != null) {
                    if (error is com.google.firebase.firestore.FirebaseFirestoreException) {
                        FirestoreErrorBus.showNonFatalBanner("delivery rates", error.code.name)
                    }
                    trySend(defaultDeliveryRates)
                    return@addSnapshotListener
                }
                if (snapshot == null || !snapshot.exists()) {
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
                        val sFeeType = (m["serviceFeeType"] as? String)?.lowercase() ?: "flat"
                        val sFee = (m["serviceFee"] as? Number)?.toDouble() ?: 10.0
                        val sFeePercent = (m["serviceFeePercent"] as? Number)?.toDouble() ?: (if (sFeeType == "percent") sFee else 0.0)
                        val sFeeMin = (m["serviceFeeMin"] as? Number)?.toDouble()
                        val sFeeMax = (m["serviceFeeMax"] as? Number)?.toDouble()
                        return RateConfig(
                            baseFare = (m["baseFare"] as? Number)?.toDouble() ?: 39.0,
                            includedKm = (m["includedKm"] as? Number)?.toDouble() ?: 2.0,
                            perKmRate = (m["perKmRate"] as? Number)?.toDouble() ?: 10.0,
                            minFee = (m["minFee"] as? Number)?.toDouble() ?: 39.0,
                            maxFee = (m["maxFee"] as? Number)?.toDouble() ?: 250.0,
                            serviceFee = sFee,
                            serviceFeeType = sFeeType,
                            serviceFeePercent = sFeePercent,
                            serviceFeeMin = sFeeMin,
                            serviceFeeMax = sFeeMax,
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
                            val sFeeType = (m["serviceFeeType"] as? String)?.lowercase() ?: "flat"
                            val sFee = (m["serviceFee"] as? Number)?.toDouble() ?: def.serviceFee
                            val sFeePercent = (m["serviceFeePercent"] as? Number)?.toDouble() ?: (if (sFeeType == "percent") sFee else 0.0)
                            val sFeeMin = (m["serviceFeeMin"] as? Number)?.toDouble()
                            val sFeeMax = (m["serviceFeeMax"] as? Number)?.toDouble()
                            parsedRideRates[typeKey] = RideRateConfig(
                                enabled = (m["enabled"] as? Boolean) ?: def.enabled,
                                baseFare = (m["baseFare"] as? Number)?.toDouble() ?: def.baseFare,
                                includedKm = (m["includedKm"] as? Number)?.toDouble() ?: def.includedKm,
                                perKm = (m["perKm"] as? Number)?.toDouble() ?: (m["perKmRate"] as? Number)?.toDouble() ?: def.perKm,
                                minFare = (m["minFare"] as? Number)?.toDouble() ?: (m["minFee"] as? Number)?.toDouble() ?: def.minFare,
                                perStopFee = (m["perStopFee"] as? Number)?.toDouble() ?: def.perStopFee,
                                serviceFee = sFee,
                                serviceFeeType = sFeeType,
                                serviceFeePercent = sFeePercent,
                                serviceFeeMin = sFeeMin,
                                serviceFeeMax = sFeeMax
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
                if (error != null) {
                    if (error is com.google.firebase.firestore.FirebaseFirestoreException) {
                        FirestoreErrorBus.showNonFatalBanner("platform settings", error.code.name)
                    }
                    trySend(PlatformSettings())
                    return@addSnapshotListener
                }
                if (snapshot == null || !snapshot.exists()) {
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

                    val vatMap = snapshot.get("vat") as? Map<*, *>
                    val vatConfig = if (vatMap != null) {
                        com.example.data.model.VatConfig(
                            enabled = (vatMap["enabled"] as? Boolean) ?: false,
                            percent = (vatMap["percent"] as? Number)?.toDouble() ?: 0.0,
                            mode = (vatMap["mode"] as? String)?.lowercase() ?: "added",
                            base = (vatMap["base"] as? String)?.lowercase() ?: "total"
                        )
                    } else com.example.data.model.VatConfig()

                    val timezone = snapshot.getString("timezone")?.ifBlank { "Asia/Manila" } ?: "Asia/Manila"

                    trySend(
                        PlatformSettings(
                            services = servicesMap,
                            taxiVehicles = vehiclesMap,
                            vat = vatConfig,
                            timezone = timezone
                        )
                    )
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
                if (error != null) {
                    if (error is com.google.firebase.firestore.FirebaseFirestoreException) {
                        FirestoreErrorBus.showNonFatalBanner("banners", error.code.name)
                    }
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot == null || snapshot.isEmpty) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val banners = snapshot.documents.mapNotNull { doc ->
                    try {
                        val imageUrl = (doc.get("imageUrl") as? String)?.trim() ?: ""
                        if (!imageUrl.startsWith("https://", ignoreCase = true)) return@mapNotNull null

                        val title = (doc.get("title") as? String)?.trim() ?: ""
                        if (title.isEmpty()) return@mapNotNull null

                        val status = (doc.get("status") as? String)?.trim() ?: "active"
                        if (!status.equals("active", ignoreCase = true)) return@mapNotNull null

                        val priority = (doc.get("priority") as? Number)?.toInt() ?: 0

                        val linkMap = doc.get("link") as? Map<*, *>
                        val link = BannerLink(
                            merchantId = linkMap?.get("merchantId") as? String,
                            category = linkMap?.get("category") as? String
                        )

                        val discountMap = doc.get("discount") as? Map<*, *>
                        val discount = if (discountMap != null) {
                            com.example.data.model.BannerDiscount(
                                type = (discountMap["type"] as? String) ?: "percent",
                                value = (discountMap["value"] as? Number)?.toDouble() ?: 0.0,
                                minOrder = (discountMap["minOrder"] as? Number)?.toDouble() ?: 0.0,
                                maxDiscount = (discountMap["maxDiscount"] as? Number)?.toDouble() ?: 0.0,
                                code = (discountMap["code"] as? String) ?: ""
                            )
                        } else null

                        fun parseTimestampOrString(v: Any?): String {
                            return when (v) {
                                is Timestamp -> v.toDate().toString()
                                is String -> v
                                else -> ""
                            }
                        }

                        Banner(
                            id = doc.id,
                            title = title,
                            imageUrl = imageUrl,
                            status = status,
                            priority = priority,
                            startAt = parseTimestampOrString(doc.get("startAt")),
                            endAt = parseTimestampOrString(doc.get("endAt")),
                            link = link,
                            discount = discount
                        )
                    } catch (e: Exception) {
                        Log.w(tag, "Skipping invalid banner ${doc.id}: ${e.message}")
                        null
                    }
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
                if (error is com.google.firebase.firestore.FirebaseFirestoreException) {
                    FirestoreErrorBus.showNonFatalBanner("merchants", error.code.name)
                } else {
                    FirestoreErrorBus.emitError("query", "merchants", error)
                }
                trySend(emptyList())
                return@addSnapshotListener
            }
            if (snapshot == null || snapshot.isEmpty) {
                trySend(emptyList())
                return@addSnapshotListener
            }

            val merchantList = mutableListOf<Merchant>()
            for (doc in snapshot.documents) {
                try {
                    val rawStatus = (doc.get("status") as? String)?.trim()?.lowercase(java.util.Locale.ROOT) ?: "active"
                    val tags = (doc.get("cuisineTags") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                    val isPaused = (doc.get("isPaused") as? Boolean) ?: false
                    val pauseUntilTime = when (val p = doc.get("pauseUntilTime")) {
                        is Timestamp -> p.toDate().time
                        is Number -> p.toLong()
                        else -> 0L
                    }
                    val closedUntil = when (val c = doc.get("closedUntil")) {
                        is Timestamp -> c.toDate().time
                        is Number -> c.toLong()
                        else -> null
                    }
                    val openUntil = when (val o = doc.get("openUntil")) {
                        is Timestamp -> o.toDate().time
                        is Number -> o.toLong()
                        else -> null
                    }
                    val autoSchedule = (doc.get("autoSchedule") as? Boolean) ?: false

                    val m = Merchant(
                        id = doc.id,
                        name = (doc.get("name") as? String) ?: "",
                        type = (doc.get("type") as? String) ?: "food",
                        status = rawStatus,
                        cityId = (doc.get("cityId") as? String) ?: "",
                        isOpen = (doc.get("isOpen") as? Boolean) ?: true,
                        rating = (doc.get("rating") as? Number)?.toDouble() ?: 4.8,
                        deliveryTime = (doc.get("deliveryTime") as? String) ?: "20-30 min",
                        address = (doc.get("address") as? String) ?: "",
                        lat = (doc.get("lat") as? Number)?.toDouble() ?: 9.7420,
                        lng = (doc.get("lng") as? Number)?.toDouble() ?: 118.7400,
                        cuisineTags = tags,
                        logoUrl = (doc.get("logoUrl") as? String)?.trim() ?: "",
                        bannerUrl = (doc.get("bannerUrl") as? String)?.trim() ?: "",
                        imageUrl = (doc.get("imageUrl") as? String)?.trim() ?: "",
                        hours = doc.get("hours") as? Map<String, Any>,
                        isPaused = isPaused,
                        pauseUntilTime = pauseUntilTime,
                        closedUntil = closedUntil,
                        openUntil = openUntil,
                        autoSchedule = autoSchedule
                    )
                    merchantList.add(m)
                } catch (e: Exception) {
                    Log.w(tag, "Skipping bad merchant doc ${doc.id}: ${e.message}")
                }
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
                if (error != null) {
                    if (error is com.google.firebase.firestore.FirebaseFirestoreException) {
                        FirestoreErrorBus.showNonFatalBanner("categories", error.code.name)
                    }
                    val fallback = defaultMerchants.find { it.id == merchantId }?.categories ?: emptyList()
                    trySend(fallback)
                    return@addSnapshotListener
                }
                if (snapshot == null || snapshot.isEmpty) {
                    val fallback = defaultMerchants.find { it.id == merchantId }?.categories ?: emptyList()
                    trySend(fallback)
                    return@addSnapshotListener
                }
                val cats = snapshot.documents.mapNotNull { doc ->
                    try {
                        MerchantCategory(
                            id = doc.id,
                            name = doc.getString("name") ?: "",
                            order = (doc.get("order") as? Number)?.toInt() ?: 1
                        )
                    } catch (e: Exception) {
                        null
                    }
                }.sortedBy { it.order }
                trySend(cats)
            }

        awaitClose { listener.remove() }
    }

    // Listen to Subcategories for a Merchant (merchants/{id}/subcategories sorted by sortOrder)
    fun getMerchantSubcategoriesFlow(merchantId: String): Flow<List<MerchantSubcategory>> = callbackFlow {
        val fs = firestore
        if (fs == null || merchantId.isBlank()) {
            trySend(emptyList())
            awaitClose {}
            return@callbackFlow
        }

        val listener = fs.collection("merchants").document(merchantId).collection("subcategories")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    if (error is com.google.firebase.firestore.FirebaseFirestoreException) {
                        FirestoreErrorBus.showNonFatalBanner("subcategories", error.code.name)
                    }
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot == null || snapshot.isEmpty) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val subcats = snapshot.documents.mapNotNull { doc ->
                    try {
                        val name = (doc.get("name") as? String)?.trim() ?: ""
                        if (name.isBlank()) return@mapNotNull null
                        val sortOrder = (doc.get("sortOrder") as? Number)?.toInt()
                            ?: (doc.get("order") as? Number)?.toInt()
                            ?: 0
                        MerchantSubcategory(
                            id = doc.id,
                            name = name,
                            sortOrder = sortOrder
                        )
                    } catch (e: Exception) {
                        null
                    }
                }.sortedBy { it.sortOrder }
                trySend(subcats)
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
                    if (error is com.google.firebase.firestore.FirebaseFirestoreException) {
                        FirestoreErrorBus.showNonFatalBanner("items", error.code.name)
                    } else {
                        FirestoreErrorBus.emitError("query", "merchants/$merchantId/items", error)
                    }
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
                    try {
                        val isAvail = (doc.get("available") as? Boolean) ?: (doc.get("isAvailable") as? Boolean) ?: true
                        if (!isAvail) return@mapNotNull null
                        val catName = (doc.get("categoryName") as? String)?.trim()
                            ?: (doc.get("category") as? String)?.trim()
                            ?: ""
                        val subcatId = (doc.get("subcategoryId") as? String)?.trim()
                            ?: (doc.get("subcategory") as? String)?.trim()
                            ?: (doc.get("categoryId") as? String)?.trim()
                            ?: ""
                        val subcatName = (doc.get("subcategoryName") as? String)?.trim()
                            ?: (doc.get("subcategory") as? String)?.trim()
                            ?: catName
                        val (hasSets, sets) = parseItemSets(doc)
                        MerchantItem(
                            id = doc.id,
                            categoryId = (doc.get("categoryId") as? String) ?: "",
                            categoryName = catName,
                            subcategoryId = subcatId,
                            subcategoryName = subcatName,
                            name = (doc.get("name") as? String) ?: "",
                            price = (doc.get("price") as? Number)?.toDouble() ?: 0.0,
                            description = (doc.get("description") as? String) ?: "",
                            imageUrl = (doc.get("imageUrl") as? String)?.trim() ?: "",
                            isAvailable = true,
                            hasSets = hasSets,
                            sets = sets
                        )
                    } catch (e: Exception) {
                        Log.w(tag, "Skipping bad item doc ${doc.id}: ${e.message}")
                        null
                    }
                }
                trySend(items)
            }

        awaitClose { listener.remove() }
    }

    private fun parseItemSets(doc: DocumentSnapshot): Pair<Boolean, List<ItemSet>> {
        val hasSetsField = doc.getBoolean("hasSets")
        val rawSets = doc.get("sets") as? List<*>
        val setsList = rawSets?.mapNotNull { setObj ->
            if (setObj is Map<*, *>) {
                val rawChoices = (setObj["choices"] ?: setObj["options"]) as? List<*>
                val choicesList = rawChoices?.mapNotNull { chObj ->
                    if (chObj is Map<*, *>) {
                        ItemChoice(
                            id = chObj["id"] as? String ?: chObj["choiceId"] as? String ?: "",
                            name = chObj["name"] as? String ?: chObj["choiceName"] as? String ?: "",
                            price = (chObj["price"] as? Number)?.toDouble()
                                ?: (chObj["extraPrice"] as? Number)?.toDouble() ?: 0.0,
                            available = chObj["available"] as? Boolean
                                ?: chObj["isAvailable"] as? Boolean ?: true
                        )
                    } else null
                } ?: emptyList()

                val req = setObj["required"] as? Boolean
                    ?: setObj["isRequired"] as? Boolean ?: false
                val minS = (setObj["minSelect"] as? Number)?.toInt()
                    ?: (setObj["minChoices"] as? Number)?.toInt()
                    ?: (setObj["min"] as? Number)?.toInt()
                    ?: if (req) 1 else 0
                val maxS = (setObj["maxSelect"] as? Number)?.toInt()
                    ?: (setObj["maxChoices"] as? Number)?.toInt()
                    ?: (setObj["max"] as? Number)?.toInt() ?: 1

                ItemSet(
                    id = setObj["id"] as? String ?: setObj["setId"] as? String ?: "",
                    name = setObj["name"] as? String ?: setObj["setName"] as? String ?: "",
                    required = req,
                    minSelect = minS,
                    maxSelect = maxS,
                    choices = choicesList
                )
            } else null
        } ?: emptyList()

        val effectiveHasSets = hasSetsField ?: setsList.isNotEmpty()
        return Pair(effectiveHasSets, setsList)
    }

    suspend fun getMerchantAvailableItems(merchantId: String): List<MerchantItem> = withContext(Dispatchers.IO) {
        val fs = firestore
        if (fs == null) {
            val fallback = defaultMerchants.find { it.id == merchantId }?.items ?: emptyList()
            return@withContext fallback.filter { it.isAvailable }
        }
        try {
            val snapshot = fs.collection("merchants").document(merchantId)
                .collection("items")
                .get()
                .await()
            snapshot.documents.mapNotNull { doc ->
                val isAvail = doc.getBoolean("available") ?: doc.getBoolean("isAvailable") ?: true
                if (!isAvail) return@mapNotNull null
                val catName = doc.getString("categoryName")?.trim()
                    ?: doc.getString("category")?.trim()
                    ?: ""
                val subcatId = doc.getString("subcategoryId")?.trim()
                    ?: doc.getString("subcategory")?.trim()
                    ?: doc.getString("categoryId")?.trim()
                    ?: ""
                val subcatName = doc.getString("subcategoryName")?.trim()
                    ?: doc.getString("subcategory")?.trim()
                    ?: catName
                val (hasSets, sets) = parseItemSets(doc)
                MerchantItem(
                    id = doc.id,
                    categoryId = doc.getString("categoryId") ?: "",
                    categoryName = catName,
                    subcategoryId = subcatId,
                    subcategoryName = subcatName,
                    name = doc.getString("name") ?: "",
                    price = (doc.getDouble("price") ?: (doc.get("price") as? Number)?.toDouble()) ?: 0.0,
                    description = doc.getString("description") ?: "",
                    imageUrl = doc.getString("imageUrl")?.trim() ?: "",
                    isAvailable = true,
                    hasSets = hasSets,
                    sets = sets
                )
            }
        } catch (e: Exception) {
            Log.w(tag, "Error loading available items for merchant $merchantId: ${e.message}")
            val fallback = defaultMerchants.find { it.id == merchantId }?.items ?: emptyList()
            fallback.filter { it.isAvailable }
        }
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
                    if (error is com.google.firebase.firestore.FirebaseFirestoreException) {
                        FirestoreErrorBus.showNonFatalBanner("orders", error.code.name)
                    }
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
                    Log.w(tag, "Index error or query failed for customer orders, falling back to equality query", error)
                    if (error is com.google.firebase.firestore.FirebaseFirestoreException && error.code != com.google.firebase.firestore.FirebaseFirestoreException.Code.FAILED_PRECONDITION) {
                        FirestoreErrorBus.showNonFatalBanner("orders", error.code.name)
                    }
                    if (fallbackRegistration == null) {
                        fallbackRegistration = fs.collection("orders")
                            .whereEqualTo("customerId", customerId)
                            .addSnapshotListener { snap, err ->
                                if (err != null) {
                                    Log.e(tag, "Fallback equality listener failed", err)
                                    if (err is com.google.firebase.firestore.FirebaseFirestoreException) {
                                        FirestoreErrorBus.showNonFatalBanner("orders", err.code.name)
                                    }
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

    private fun parseOrderItem(itemObj: Map<*, *>): OrderItem {
        val price = (itemObj["price"] as? Number)?.toDouble() ?: 0.0
        val basePrice = (itemObj["basePrice"] as? Number)?.toDouble() ?: price
        val itemId = itemObj["itemId"] as? String ?: ""
        val selectionsRaw = itemObj["selections"] as? List<*>
        val selectionsList = selectionsRaw?.mapNotNull { selObj ->
            if (selObj is Map<*, *>) {
                SelectedItemChoice(
                    setId = selObj["setId"] as? String ?: "",
                    setName = selObj["setName"] as? String ?: "",
                    choiceId = selObj["choiceId"] as? String ?: "",
                    choiceName = selObj["choiceName"] as? String ?: "",
                    price = (selObj["price"] as? Number)?.toDouble() ?: 0.0
                )
            } else null
        } ?: emptyList()

        return OrderItem(
            itemId = itemId,
            name = itemObj["name"] as? String ?: "",
            qty = (itemObj["qty"] as? Number)?.toInt()
                ?: (itemObj["quantity"] as? Number)?.toInt() ?: 1,
            basePrice = basePrice,
            price = price,
            selections = selectionsList,
            notes = (itemObj["notes"] as? String)
                ?: (itemObj["specialInstructions"] as? String) ?: ""
        )
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
                if (itemObj is Map<*, *>) parseOrderItem(itemObj) else null
            } ?: emptyList()

            val origItemsRaw = data["originalItems"] as? List<*>
            val originalItemsList = origItemsRaw?.mapNotNull { itemObj ->
                if (itemObj is Map<*, *>) parseOrderItem(itemObj) else null
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
                serviceFeeType = (feesMap?.get("serviceFeeType") as? String) ?: "flat",
                serviceFeePercent = (feesMap?.get("serviceFeePercent") as? Number)?.toDouble() ?: 0.0,
                discount = (feesMap?.get("discount") as? Number)?.toDouble() ?: 0.0,
                tip = (feesMap?.get("tip") as? Number)?.toDouble() ?: 0.0,
                vat = (feesMap?.get("vat") as? Number)?.toDouble() ?: 0.0,
                vatPercent = (feesMap?.get("vatPercent") as? Number)?.toDouble() ?: 0.0,
                vatMode = (feesMap?.get("vatMode") as? String) ?: "added",
                vatBase = (feesMap?.get("vatBase") as? Number)?.toDouble() ?: 0.0,
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
                originalItems = originalItemsList,
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
                note = data["note"] as? String ?: "",
                editedAt = when (val ea = data["editedAt"]) {
                    is Timestamp -> ea.toDate().time
                    is Number -> ea.toLong()
                    else -> null
                },
                editNote = data["editNote"] as? String,
                walletAdjustDue = (data["walletAdjustDue"] as? Number)?.toDouble() ?: 0.0,
                walletAdjustApplied = (data["walletAdjustApplied"] as? Number)?.toDouble() ?: 0.0,
                lateTip = (data["lateTip"] as? Map<*, *>)?.let { lt ->
                    val amt = (lt["amount"] as? Number)?.toDouble() ?: 0.0
                    val at = when (val atVal = lt["at"]) {
                        is Timestamp -> atVal.toDate().time
                        is Number -> atVal.toLong()
                        else -> System.currentTimeMillis()
                    }
                    val paidWith = (lt["paidWith"] as? String) ?: "wallet"
                    LateTip(amount = amt, at = at, paidWith = paidWith)
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
        val now = System.currentTimeMillis()
        val cleanFulfillment = when (order.fulfillment.lowercase().trim()) {
            "pickup" -> "pickup"
            "scheduled" -> "scheduled"
            else -> "delivery"
        }
        val isPickup = cleanFulfillment == "pickup"

        if (fs == null) {
            val generatedId = "order-" + java.util.UUID.randomUUID().toString().take(8)
            val savedDoc = order.copy(
                id = generatedId,
                fulfillment = cleanFulfillment,
                dropoff = if (isPickup) null else order.dropoff,
                fees = order.fees.copy(
                    deliveryFee = if (isPickup) 0.0 else order.fees.deliveryFee,
                    tip = if (isPickup) 0.0 else order.fees.tip
                ),
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
            // Re-read merchants/{id} document from server for shop orders
            if (!order.merchantId.isNullOrBlank()) {
                val merchantSnap = try {
                    fs.collection("merchants").document(order.merchantId)
                        .get(com.google.firebase.firestore.Source.SERVER).await()
                } catch (e: Exception) {
                    Log.w(tag, "Could not reach server to verify merchant status, falling back to cache", e)
                    fs.collection("merchants").document(order.merchantId).get().await()
                }
                if (merchantSnap.exists()) {
                    val status = merchantSnap.getString("status") ?: "active"
                    val isOpen = merchantSnap.getBoolean("isOpen") ?: false
                    val isPaused = merchantSnap.getBoolean("isPaused") ?: false
                    val pauseUntil = merchantSnap.getLong("pauseUntilTime") ?: 0L
                    val closedUntil = when (val cu = merchantSnap.get("closedUntil")) {
                        is com.google.firebase.Timestamp -> cu.toDate().time
                        is Number -> cu.toLong()
                        else -> null
                    }
                    val openUntil = when (val ou = merchantSnap.get("openUntil")) {
                        is com.google.firebase.Timestamp -> ou.toDate().time
                        is Number -> ou.toLong()
                        else -> null
                    }
                    val autoSchedule = merchantSnap.getBoolean("autoSchedule") ?: false
                    @Suppress("UNCHECKED_CAST")
                    val hours = merchantSnap.get("hours") as? Map<String, Any>

                    val isEffectiveOpen = com.example.util.BusinessHoursUtil.effectiveOpen(
                        status = status,
                        isOpen = isOpen,
                        isPaused = isPaused,
                        pauseUntilTime = pauseUntil,
                        closedUntil = closedUntil,
                        openUntil = openUntil,
                        autoSchedule = autoSchedule,
                        hours = hours,
                        timezone = "Asia/Manila",
                        nowMillis = now
                    )

                    if (!isEffectiveOpen) {
                        return Result.failure(IllegalStateException("This store just closed and is not accepting orders."))
                    }
                }
            }

            val docRef = fs.collection("orders").document()

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
                        "itemId" to item.itemId,
                        "name" to item.name,
                        "qty" to item.qty,
                        "quantity" to item.qty,
                        "basePrice" to item.basePrice,
                        "price" to item.price,
                        "selections" to item.selections.map { sel ->
                            mapOf(
                                "setId" to sel.setId,
                                "setName" to sel.setName,
                                "choiceId" to sel.choiceId,
                                "choiceName" to sel.choiceName,
                                "price" to sel.price
                            )
                        },
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
                "dropoff" to if (isPickup || order.dropoff == null) null else mapOf(
                    "label" to order.dropoff.label,
                    "address" to order.dropoff.address,
                    "lat" to order.dropoff.lat,
                    "lng" to order.dropoff.lng,
                    "contactName" to order.dropoff.contactName,
                    "contactPhone" to order.dropoff.contactPhone
                ),
                "fees" to mapOf(
                    "subtotal" to order.fees.subtotal,
                    "deliveryFee" to (if (isPickup) 0.0 else order.fees.deliveryFee),
                    "serviceFee" to order.fees.serviceFee,
                    "serviceFeeType" to order.fees.serviceFeeType,
                    "serviceFeePercent" to order.fees.serviceFeePercent,
                    "discount" to order.fees.discount,
                    "tip" to (if (isPickup) 0.0 else order.fees.tip),
                    "vat" to order.fees.vat,
                    "vatPercent" to order.fees.vatPercent,
                    "vatMode" to order.fees.vatMode,
                    "vatBase" to order.fees.vatBase,
                    "total" to order.fees.total,
                    "estimatedItemsTotal" to order.fees.estimatedItemsTotal,
                    "distanceKm" to order.fees.distanceKm,
                    "distanceMethod" to order.fees.distanceMethod
                ),
                "paymentMethod" to order.paymentMethod,
                "paymentProvider" to order.paymentProvider,
                "paymentStatus" to order.paymentStatus,
                "cashToCollect" to (if (order.paymentMethod == "wallet") 0.0 else order.fees.total),
                "cashCollectAt" to order.cashCollectAt,
                "walletRefunded" to order.walletRefunded,
                "cityId" to order.cityId,
                "fulfillment" to cleanFulfillment,
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
            val isShopOrder = !order.merchantId.isNullOrBlank()
            val isPermissionDenied = e is com.google.firebase.firestore.FirebaseFirestoreException &&
                    e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED
            val errorMessage = if (isShopOrder && isPermissionDenied) {
                "This store just closed and is not accepting orders."
            } else if (isShopOrder && e.message?.contains("PERMISSION_DENIED", ignoreCase = true) == true) {
                "This store just closed and is not accepting orders."
            } else if (e.message?.contains("This store just closed") == true) {
                "This store just closed and is not accepting orders."
            } else {
                e.localizedMessage ?: e.message ?: "Failed to place order"
            }
            Result.failure(Exception(errorMessage, e))
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

    fun parseUserDocument(doc: DocumentSnapshot): DelivioUser? {
        if (!doc.exists()) return null
        return try {
            val createdLong = when (val c = doc.get("createdAt")) {
                is Timestamp -> c.toDate().time
                is Number -> c.toLong()
                else -> System.currentTimeMillis()
            }
            val wallet = (doc.get("walletBalance") as? Number)?.toDouble() ?: 0.0
            DelivioUser(
                uid = doc.id,
                email = (doc.get("email") as? String) ?: "",
                name = (doc.get("name") as? String) ?: "",
                phone = (doc.get("phone") as? String) ?: "",
                role = (doc.get("role") as? String) ?: "customer",
                status = (doc.get("status") as? String) ?: "active",
                walletBalance = wallet,
                createdAt = createdLong
            )
        } catch (e: Exception) {
            Log.w(tag, "Error parsing user doc ${doc.id}", e)
            null
        }
    }

    suspend fun getUserProfile(uid: String): DelivioUser? {
        val fs = firestore ?: return null
        return try {
            val doc = fs.collection("users").document(uid).get().await()
            parseUserDocument(doc)
        } catch (e: Exception) {
            if (e is com.google.firebase.firestore.FirebaseFirestoreException) {
                FirestoreErrorBus.showNonFatalBanner("user profile", e.code.name)
            } else {
                FirestoreErrorBus.emitError("get", "users/$uid", e)
            }
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
                    if (error is com.google.firebase.firestore.FirebaseFirestoreException) {
                        FirestoreErrorBus.showNonFatalBanner("user profile", error.code.name)
                    }
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val user = parseUserDocument(snapshot)
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

    private val editRefundGuard = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    suspend fun processOrderEditWalletRefund(orderId: String, customerId: String, adjustDue: Double): Result<String> {
        if (!editRefundGuard.add(orderId)) {
            return Result.success("Already processed")
        }
        val fs = firestore
        if (fs == null) {
            val order = inMemoryOrders[orderId]
            if (order != null && order.walletAdjustApplied == 0.0) {
                inMemoryOrders[orderId] = order.copy(walletAdjustApplied = adjustDue)
                inMemoryOrdersUpdates.tryEmit(Unit)
            }
            return Result.success("₱${String.format(java.util.Locale.US, "%.2f", adjustDue)} refunded to your wallet for the updated order")
        }
        return try {
            val orderRef = fs.collection("orders").document(orderId)
            val userRef = fs.collection("users").document(customerId)
            val batch = fs.batch()
            batch.update(orderRef, mapOf(
                "walletAdjustApplied" to adjustDue,
                "updatedAt" to FieldValue.serverTimestamp()
            ))
            batch.update(userRef, mapOf(
                "walletBalance" to FieldValue.increment(adjustDue),
                "walletRefundOrderId" to orderId
            ))
            batch.commit().await()
            Result.success("₱${String.format(java.util.Locale.US, "%.2f", adjustDue)} refunded to your wallet for the updated order")
        } catch (e: Exception) {
            editRefundGuard.remove(orderId)
            Log.e(tag, "Wallet adjustment refund failed for order $orderId", e)
            Result.failure(e)
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

    suspend fun reviewExists(orderId: String): Boolean = withContext(Dispatchers.IO) {
        val fs = firestore ?: return@withContext false
        try {
            val doc = fs.collection("reviews").document(orderId).get().await()
            doc.exists()
        } catch (e: Exception) {
            Log.w(tag, "Error checking if review exists for $orderId: ${e.message}")
            false
        }
    }

    suspend fun checkUserProfile(uid: String): UserDocCheckResult {
        val fs = firestore ?: return UserDocCheckResult.Missing
        return try {
            val doc = fs.collection("users").document(uid).get().await()
            if (!doc.exists()) {
                UserDocCheckResult.Missing
            } else {
                val user = parseUserDocument(doc)
                if (user != null) {
                    UserDocCheckResult.Exists(user)
                } else {
                    UserDocCheckResult.Missing
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error reading user doc $uid", e)
            UserDocCheckResult.Error(e)
        }
    }

    suspend fun getReview(orderId: String): ReviewDocument? {
        val fs = firestore ?: return null
        return try {
            val doc = fs.collection("reviews").document(orderId).get().await()
            if (!doc.exists()) return null
            val data = doc.data ?: return null
            val createdLong = when (val c = data["createdAt"]) {
                is Timestamp -> c.toDate().time
                is Number -> c.toLong()
                else -> System.currentTimeMillis()
            }
            ReviewDocument(
                orderId = (data["orderId"] as? String) ?: doc.id,
                orderNumber = (data["orderNumber"] as? String) ?: "",
                serviceType = (data["serviceType"] as? String) ?: "",
                customerId = (data["customerId"] as? String) ?: "",
                merchantId = data["merchantId"] as? String,
                riderId = data["riderId"] as? String,
                ratingMerchant = (data["ratingMerchant"] as? Number)?.toInt(),
                ratingRider = (data["ratingRider"] as? Number)?.toInt(),
                comment = (data["comment"] as? String) ?: "",
                createdAt = createdLong
            )
        } catch (e: Exception) {
            Log.w(tag, "Error fetching review for $orderId: ${e.message}")
            null
        }
    }

    suspend fun saveReview(
        orderId: String,
        orderNumber: String,
        serviceType: String,
        customerId: String,
        customerName: String,
        merchantId: String?,
        merchantName: String?,
        riderId: String?,
        riderName: String?,
        ratingMerchant: Int?,
        ratingRider: Int?,
        comment: String,
        itemsSummary: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        // Delegate to batch submission without names
        submitReviewAndTipBatch(
            orderId = orderId,
            orderNumber = orderNumber,
            serviceType = serviceType,
            customerId = customerId,
            merchantId = merchantId,
            riderId = riderId,
            ratingMerchant = ratingMerchant,
            ratingRider = ratingRider,
            comment = comment,
            tipAmount = 0.0
        )
    }

    // Submit Review and Tip in ONE batch write
    // Rule: reviews/{orderId} stores NO names: {orderId, orderNumber, serviceType, customerId, merchantId, riderId, ratingMerchant (or null), ratingRider (or null), comment, createdAt: serverTimestamp()}
    // if tip > 0: update orders/{orderId} with {lateTip: {amount, at: Timestamp.now(), paidWith: "wallet"}, updatedAt: serverTimestamp()} and users/{uid} with {walletBalance: FieldValue.increment(-amount)}
    suspend fun submitReviewAndTipBatch(
        orderId: String,
        orderNumber: String,
        serviceType: String,
        customerId: String,
        merchantId: String?,
        riderId: String?,
        ratingMerchant: Int?,
        ratingRider: Int?,
        comment: String,
        tipAmount: Double
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val fs = firestore ?: return@withContext Result.failure(Exception("Firestore not initialized"))
        try {
            val batch = fs.batch()
            val reviewRef = fs.collection("reviews").document(orderId)

            val reviewData = hashMapOf<String, Any?>(
                "orderId" to orderId,
                "orderNumber" to orderNumber,
                "serviceType" to serviceType.lowercase(),
                "customerId" to customerId,
                "merchantId" to merchantId,
                "riderId" to riderId,
                "ratingMerchant" to ratingMerchant,
                "ratingRider" to ratingRider,
                "comment" to comment.trim().take(300),
                "createdAt" to FieldValue.serverTimestamp()
            )
            batch.set(reviewRef, reviewData)

            if (tipAmount > 0.0) {
                val orderRef = fs.collection("orders").document(orderId)
                val userRef = fs.collection("users").document(customerId)

                batch.update(orderRef, mapOf(
                    "lateTip" to mapOf(
                        "amount" to tipAmount,
                        "at" to Timestamp.now(),
                        "paidWith" to "wallet"
                    ),
                    "updatedAt" to FieldValue.serverTimestamp()
                ))

                batch.update(userRef, mapOf(
                    "walletBalance" to FieldValue.increment(-tipAmount),
                    "updatedAt" to FieldValue.serverTimestamp()
                ))
            }

            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Error saving review and tip batch for $orderId: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun submitReview(
        orderId: String,
        customerId: String,
        riderId: String?,
        rating: Int,
        comment: String
    ): Result<Unit> {
        return submitReviewAndTipBatch(
            orderId = orderId,
            orderNumber = orderId.takeLast(6).uppercase(),
            serviceType = "food",
            customerId = customerId,
            merchantId = null,
            riderId = riderId,
            ratingMerchant = null,
            ratingRider = rating,
            comment = comment,
            tipAmount = 0.0
        )
    }

    // Check count of unresolved tickets for customer (limit <= 3)
    suspend fun getUnresolvedTicketCountForCustomer(customerId: String): Int {
        val fs = firestore ?: return 0
        return try {
            val snap = fs.collection("supportTickets")
                .whereEqualTo("customerId", customerId)
                .get().await()
            snap.documents.count { doc ->
                val status = doc.getString("status") ?: "open"
                status != "resolved"
            }
        } catch (e: Exception) {
            0
        }
    }

    // Check if order already has an unresolved ticket (limit <= 1)
    suspend fun getUnresolvedTicketForOrder(orderId: String): SupportTicket? {
        val fs = firestore ?: return null
        return try {
            val snap = fs.collection("supportTickets")
                .whereEqualTo("orderId", orderId)
                .get().await()
            val doc = snap.documents.firstOrNull { d ->
                val status = d.getString("status") ?: "open"
                status != "resolved"
            } ?: return null
            val data = doc.data ?: return null
            val createdLong = when (val c = data["createdAt"]) {
                is Timestamp -> c.toDate().time
                is Number -> c.toLong()
                else -> System.currentTimeMillis()
            }
            SupportTicket(
                id = doc.id,
                customerId = (data["customerId"] as? String) ?: "",
                customerName = (data["customerName"] as? String) ?: "",
                orderId = (data["orderId"] as? String) ?: "",
                orderNumber = (data["orderNumber"] as? String) ?: "",
                serviceType = (data["serviceType"] as? String) ?: "",
                reason = (data["reason"] as? String) ?: "",
                reasonLabel = (data["reasonLabel"] as? String) ?: "",
                title = (data["title"] as? String) ?: "",
                status = (data["status"] as? String) ?: "open",
                createdAt = createdLong,
                lastMessageAt = createdLong,
                lastMessagePreview = (data["lastMessagePreview"] as? String) ?: "",
                unreadByAdmin = (data["unreadByAdmin"] as? Boolean) ?: false,
                unreadByCustomer = (data["unreadByCustomer"] as? Boolean) ?: false,
                resolutionNote = data["resolutionNote"] as? String
            )
        } catch (e: Exception) {
            null
        }
    }

    // Create Support Ticket in ONE batch write:
    // set supportTickets/{ticketId} = {customerId, customerName, orderId, orderNumber, serviceType, reason, reasonLabel, title, status: "open", createdAt, lastMessageAt, lastMessagePreview, unreadByAdmin: true}
    // plus messages in subcollection supportTickets/{ticketId}/messages/{id}
    suspend fun createSupportTicketBatch(
        ticket: SupportTicket,
        initialDetailText: String,
        photosBase64: List<String>
    ): Result<String> = withContext(Dispatchers.IO) {
        val fs = firestore ?: return@withContext Result.failure(Exception("Firestore not initialized"))
        try {
            val batch = fs.batch()
            val ticketRef = if (ticket.id.isNotEmpty()) fs.collection("supportTickets").document(ticket.id) else fs.collection("supportTickets").document()
            val ticketId = ticketRef.id

            val preview = if (initialDetailText.isNotBlank()) initialDetailText.take(60) else ticket.title.take(60)

            val ticketData = hashMapOf<String, Any?>(
                "customerId" to ticket.customerId,
                "customerName" to ticket.customerName,
                "orderId" to ticket.orderId,
                "orderNumber" to ticket.orderNumber,
                "serviceType" to ticket.serviceType,
                "reason" to ticket.reason,
                "reasonLabel" to ticket.reasonLabel,
                "title" to ticket.title,
                "status" to "open",
                "createdAt" to FieldValue.serverTimestamp(),
                "lastMessageAt" to FieldValue.serverTimestamp(),
                "lastMessagePreview" to preview,
                "unreadByAdmin" to true
            )
            batch.set(ticketRef, ticketData)

            if (initialDetailText.isNotBlank()) {
                val msgRef = ticketRef.collection("messages").document()
                batch.set(msgRef, mapOf(
                    "senderType" to "customer",
                    "senderId" to ticket.customerId,
                    "text" to initialDetailText.trim().take(500),
                    "imageData" to null,
                    "isInitial" to true,
                    "createdAt" to FieldValue.serverTimestamp()
                ))
            }

            for (photo in photosBase64) {
                val photoRef = ticketRef.collection("messages").document()
                batch.set(photoRef, mapOf(
                    "senderType" to "customer",
                    "senderId" to ticket.customerId,
                    "text" to "",
                    "imageData" to photo,
                    "isInitial" to true,
                    "createdAt" to FieldValue.serverTimestamp()
                ))
            }

            batch.commit().await()
            Result.success(ticketId)
        } catch (e: Exception) {
            Log.e(tag, "Error creating support ticket batch", e)
            Result.failure(e)
        }
    }

    // Observe a single ticket
    fun observeTicket(ticketId: String): Flow<SupportTicket?> = callbackFlow {
        val fs = firestore
        if (fs == null || ticketId.isEmpty()) {
            trySend(null)
            awaitClose {}
            return@callbackFlow
        }
        val listener = fs.collection("supportTickets").document(ticketId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(tag, "Error listening to ticket $ticketId: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val data = snapshot.data ?: emptyMap<String, Any?>()
                    val createdLong = when (val c = data["createdAt"]) {
                        is Timestamp -> c.toDate().time
                        is Number -> c.toLong()
                        else -> System.currentTimeMillis()
                    }
                    val lastMsgAt = when (val l = data["lastMessageAt"]) {
                        is Timestamp -> l.toDate().time
                        is Number -> l.toLong()
                        else -> createdLong
                    }
                    val ticket = SupportTicket(
                        id = snapshot.id,
                        customerId = (data["customerId"] as? String) ?: "",
                        customerName = (data["customerName"] as? String) ?: "",
                        orderId = (data["orderId"] as? String) ?: "",
                        orderNumber = (data["orderNumber"] as? String) ?: "",
                        serviceType = (data["serviceType"] as? String) ?: "",
                        reason = (data["reason"] as? String) ?: "",
                        reasonLabel = (data["reasonLabel"] as? String) ?: "",
                        title = (data["title"] as? String) ?: "",
                        status = (data["status"] as? String) ?: "open",
                        createdAt = createdLong,
                        lastMessageAt = lastMsgAt,
                        lastMessagePreview = (data["lastMessagePreview"] as? String) ?: "",
                        unreadByAdmin = (data["unreadByAdmin"] as? Boolean) ?: true,
                        unreadByCustomer = (data["unreadByCustomer"] as? Boolean) ?: false,
                        resolutionNote = data["resolutionNote"] as? String
                    )
                    trySend(ticket)
                } else {
                    trySend(null)
                }
            }
        awaitClose { listener.remove() }
    }

    // Observe messages of a ticket sorted by createdAt
    fun observeTicketMessages(ticketId: String): Flow<List<TicketMessage>> = callbackFlow {
        val fs = firestore
        if (fs == null || ticketId.isEmpty()) {
            trySend(emptyList())
            awaitClose {}
            return@callbackFlow
        }
        val listener = fs.collection("supportTickets").document(ticketId)
            .collection("messages")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(tag, "Error listening to messages of $ticketId: ${error.message}")
                    return@addSnapshotListener
                }
                val messages = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        val data = doc.data ?: return@mapNotNull null
                        val createdLong = when (val c = data["createdAt"]) {
                            is Timestamp -> c.toDate().time
                            is Number -> c.toLong()
                            else -> System.currentTimeMillis()
                        }
                        TicketMessage(
                            id = doc.id,
                            senderType = (data["senderType"] as? String) ?: "customer",
                            senderId = (data["senderId"] as? String) ?: "",
                            text = (data["text"] as? String) ?: "",
                            imageData = data["imageData"] as? String,
                            isInitial = (data["isInitial"] as? Boolean) ?: false,
                            createdAt = createdLong
                        )
                    } catch (e: Exception) {
                        null
                    }
                }?.sortedBy { it.createdAt } ?: emptyList()
                trySend(messages)
            }
        awaitClose { listener.remove() }
    }

    // Observe customer tickets
    fun observeCustomerTickets(customerId: String): Flow<List<SupportTicket>> = callbackFlow {
        val fs = firestore
        if (fs == null || customerId.isEmpty()) {
            trySend(emptyList())
            awaitClose {}
            return@callbackFlow
        }
        val listener = fs.collection("supportTickets")
            .whereEqualTo("customerId", customerId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(tag, "Error listening to customer tickets: ${error.message}")
                    return@addSnapshotListener
                }
                val tickets = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        val data = doc.data ?: return@mapNotNull null
                        val createdLong = when (val c = data["createdAt"]) {
                            is Timestamp -> c.toDate().time
                            is Number -> c.toLong()
                            else -> System.currentTimeMillis()
                        }
                        val lastMsgAt = when (val l = data["lastMessageAt"]) {
                            is Timestamp -> l.toDate().time
                            is Number -> l.toLong()
                            else -> createdLong
                        }
                        SupportTicket(
                            id = doc.id,
                            customerId = (data["customerId"] as? String) ?: "",
                            customerName = (data["customerName"] as? String) ?: "",
                            orderId = (data["orderId"] as? String) ?: "",
                            orderNumber = (data["orderNumber"] as? String) ?: "",
                            serviceType = (data["serviceType"] as? String) ?: "",
                            reason = (data["reason"] as? String) ?: "",
                            reasonLabel = (data["reasonLabel"] as? String) ?: "",
                            title = (data["title"] as? String) ?: "",
                            status = (data["status"] as? String) ?: "open",
                            createdAt = createdLong,
                            lastMessageAt = lastMsgAt,
                            lastMessagePreview = (data["lastMessagePreview"] as? String) ?: "",
                            unreadByAdmin = (data["unreadByAdmin"] as? Boolean) ?: false,
                            unreadByCustomer = (data["unreadByCustomer"] as? Boolean) ?: false,
                            resolutionNote = data["resolutionNote"] as? String
                        )
                    } catch (e: Exception) {
                        null
                    }
                }?.sortedByDescending { it.createdAt } ?: emptyList()
                trySend(tickets)
            }
        awaitClose { listener.remove() }
    }

    // Send a message in a ticket
    suspend fun sendTicketMessage(
        ticketId: String,
        customerId: String,
        text: String,
        imageData: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val fs = firestore ?: return@withContext Result.failure(Exception("Firestore not initialized"))
        try {
            val ticketRef = fs.collection("supportTickets").document(ticketId)
            val msgRef = ticketRef.collection("messages").document()

            val batch = fs.batch()
            batch.set(msgRef, mapOf(
                "senderType" to "customer",
                "senderId" to customerId,
                "text" to text.trim(),
                "imageData" to imageData,
                "isInitial" to false,
                "createdAt" to FieldValue.serverTimestamp()
            ))

            val preview = if (text.isNotBlank()) text.take(60) else "Photo"
            batch.update(ticketRef, mapOf(
                "lastMessageAt" to FieldValue.serverTimestamp(),
                "lastMessagePreview" to preview,
                "unreadByAdmin" to true
            ))

            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Error sending ticket message", e)
            Result.failure(e)
        }
    }
}

sealed class UserDocCheckResult {
    data class Exists(val user: DelivioUser) : UserDocCheckResult()
    object Missing : UserDocCheckResult()
    data class Error(val exception: Exception) : UserDocCheckResult()
}

