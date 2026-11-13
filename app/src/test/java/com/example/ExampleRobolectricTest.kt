package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.datasource.MockData
import com.example.data.model.Address
import com.example.data.model.CartItem
import com.example.data.model.ItemChoice
import com.example.data.model.ItemSet
import com.example.data.model.MenuItem
import com.example.data.model.OrderItem
import com.example.data.model.OrderStatus
import com.example.data.model.ParcelDetails
import com.example.data.model.PabiliDetails
import com.example.data.model.PabiliItem
import com.example.data.model.SelectedItemChoice
import com.example.data.model.ServiceType
import com.example.data.repository.AuthRepository
import com.example.data.repository.CartRepository
import com.example.data.repository.OrderRepository
import com.example.data.repository.PreferenceRepository
import com.example.data.repository.RestaurantRepository
import com.example.util.AuthConstants
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context verifies Delivio app name`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Delivio", appName)
    }

    @Test
    fun `auth repository accepts valid demo credentials`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefRepo = PreferenceRepository(context)
        val firestoreService = com.example.data.repository.FirestoreService()
        val authRepo = AuthRepository(prefRepo, firestoreService)

        val result = authRepo.login(AuthConstants.DEMO_EMAIL, AuthConstants.DEMO_PASSWORD)
        assertTrue(result.isSuccess)
        assertEquals(AuthConstants.DEMO_EMAIL, result.getOrNull()?.email)
    }

    @Test
    fun `auth repository rejects invalid credentials`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefRepo = PreferenceRepository(context)
        val firestoreService = com.example.data.repository.FirestoreService()
        val authRepo = AuthRepository(prefRepo, firestoreService)

        val result = authRepo.login("invalid-email-format", "wrongpass")
        assertTrue(result.isFailure)
        assertEquals("Please enter a valid email address", result.exceptionOrNull()?.message)
    }

    @Test
    fun `cart calculations and promo code discounts work correctly`() {
        val cartRepo = CartRepository()
        val rest = MockData.restaurants.first()
        val item1 = MockData.getMenuItems(rest.id).first()
        val item2 = MockData.getMenuItems(rest.id)[1]

        cartRepo.addItem(item1, rest, 2) // 2x item1
        cartRepo.addItem(item2, rest, 1) // 1x item2

        val expectedSubtotal = (item1.price * 2) + item2.price
        assertEquals(expectedSubtotal, cartRepo.getSubtotal(), 0.01)

        // Apply promo code SAVE20
        val promoResult = cartRepo.applyPromoCode("SAVE20")
        assertTrue(promoResult.isSuccess)
        assertEquals("SAVE20", cartRepo.appliedPromoCode.value)
        assertEquals(expectedSubtotal * 0.20, cartRepo.promoDiscount.value, 0.01)
    }

    @Test
    fun `order status transitions and properties match requirements`() {
        // Test status properties
        assertTrue(OrderStatus.PLACED.isCallEnabled())
        assertFalse(OrderStatus.PLACED.hasRider())
        assertFalse(OrderStatus.PLACED.showMap())

        assertTrue(OrderStatus.READY.hasRider())
        assertFalse(OrderStatus.READY.showMap())

        assertTrue(OrderStatus.ON_ANOTHER_DELIVERY.hasRider())
        assertTrue(OrderStatus.ON_ANOTHER_DELIVERY.showMap())

        assertTrue(OrderStatus.ON_THE_WAY.hasRider())
        assertTrue(OrderStatus.ON_THE_WAY.showMap())

        assertTrue(OrderStatus.DELIVERED.hasRider())
        assertFalse(OrderStatus.DELIVERED.showMap())

        // Test status wording
        assertEquals("Placed Order", OrderStatus.PLACED.title)
        assertEquals("Preparing", OrderStatus.PREPARING.title)
        assertEquals("Ready", OrderStatus.READY.title)
        assertEquals("On another delivery", OrderStatus.ON_ANOTHER_DELIVERY.title)
        assertEquals("Your rider is delivering another order first. You're next after that.", OrderStatus.ON_ANOTHER_DELIVERY.subtitle)
        assertEquals("Rider is on the way", OrderStatus.ON_THE_WAY.title)
        assertEquals("Delivered", OrderStatus.DELIVERED.title)
    }

    @Test
    fun `on another delivery status shows dynamically between ready and rider on the way, and moves on`() {
        val normalFlow = com.example.util.DelivioStatusConstants.getFlowForFulfillment(
            fulfillment = "delivery",
            serviceType = "food",
            currentStatus = "Ready"
        )
        assertFalse(normalFlow.contains(com.example.util.DelivioStatusConstants.STATUS_ON_ANOTHER_DELIVERY))
        assertEquals(listOf("Placed Order", "Preparing", "Ready", "Rider is on the way", "Delivered"), normalFlow)

        // When order has status "On another delivery"
        val activeFlow = com.example.util.DelivioStatusConstants.getFlowForFulfillment(
            fulfillment = "delivery",
            serviceType = "food",
            currentStatus = "On another delivery"
        )
        assertTrue(activeFlow.contains("On another delivery"))
        val readyIndex = activeFlow.indexOf("Ready")
        val onAnotherIndex = activeFlow.indexOf("On another delivery")
        val onTheWayIndex = activeFlow.indexOf("Rider is on the way")
        assertEquals(readyIndex + 1, onAnotherIndex)
        assertEquals(onAnotherIndex + 1, onTheWayIndex)

        // When status transitions to "Rider is on the way", tracker moves on
        val nextFlow = com.example.util.DelivioStatusConstants.getFlowForFulfillment(
            fulfillment = "delivery",
            serviceType = "food",
            currentStatus = "Rider is on the way"
        )
        assertFalse(nextFlow.contains("On another delivery"))
        assertEquals(3, nextFlow.indexOf("Rider is on the way"))

        // Active status check
        assertFalse(com.example.util.DelivioStatusConstants.isTerminal("On another delivery"))
        assertTrue(OrderStatus.ON_ANOTHER_DELIVERY.showMap())
    }

    @Test
    fun `service merchant filtering works for All, Food, and Convenience`() {
        val firestoreService = com.example.data.repository.FirestoreService()
        val repo = RestaurantRepository(firestoreService)

        val allMerchants = repo.getRestaurantsByServiceType(ServiceType.ALL)
        val foodMerchants = repo.getRestaurantsByServiceType(ServiceType.FOOD)
        val convMerchants = repo.getRestaurantsByServiceType(ServiceType.CONVENIENCE)

        assertTrue(allMerchants.isNotEmpty())
        assertTrue(convMerchants.isNotEmpty())
        assertTrue(foodMerchants.all { !it.isConvenience })
        assertTrue(convMerchants.all { it.isConvenience })
    }

    @Test
    fun `booking parcel order creates active order with parcel details`() = runTest {
        val firestoreService = com.example.data.repository.FirestoreService()
        val orderRepo = OrderRepository(firestoreService)
        val parcel = ParcelDetails(
            senderName = "Alice",
            senderPhone = "12345",
            senderAddress = "123 Elm St",
            recipientName = "Bob",
            recipientPhone = "67890",
            recipientAddress = "456 Oak St",
            packageSize = "Medium / Box",
            weightKg = 4.0,
            itemDescription = "Box of clothes",
            isFragile = true,
            vehicleType = "Motorcycle"
        )

        val result = orderRepo.bookParcelOrder(
            customerId = "cust-1",
            customerName = "Alice",
            customerPhone = "12345",
            parcelDetails = parcel,
            deliveryFee = 5.50
        )
        assertTrue(result.isSuccess)
        assertNotNull(orderRepo.activeOrder.value)
        assertEquals(ServiceType.PARCEL, orderRepo.activeOrder.value?.serviceType)
    }

    @Test
    fun `booking pabili order creates active order with pabili details`() = runTest {
        val firestoreService = com.example.data.repository.FirestoreService()
        val orderRepo = OrderRepository(firestoreService)
        val pabili = PabiliDetails(
            storeName = "Quick Mart",
            storeAddress = "Downtown",
            items = listOf(
                PabiliItem(name = "Bandages", quantity = 2, estimatedPrice = 3.0),
                PabiliItem(name = "Antiseptic", quantity = 1, estimatedPrice = 5.0)
            ),
            recipientName = "Charlie",
            recipientPhone = "555-0101",
            deliveryAddress = "789 Pine Rd",
            riderNotes = "Call upon arrival"
        )

        val result = orderRepo.bookPabiliOrder(
            customerId = "cust-1",
            customerName = "Charlie",
            customerPhone = "555-0101",
            pabiliDetails = pabili,
            itemTotal = 11.0,
            deliveryFee = 4.50
        )
        assertTrue(result.isSuccess)
        assertNotNull(orderRepo.activeOrder.value)
        assertEquals(ServiceType.PABILI, orderRepo.activeOrder.value?.serviceType)
    }

    @Test
    fun `checkout two orders from same shop tracks two orders simultaneously and allows switching`() = runTest {
        val firestoreService = com.example.data.repository.FirestoreService()
        val orderRepo = OrderRepository(firestoreService)

        val address = Address(
            id = "addr-1",
            title = "Home",
            fullAddress = "123 Rizal Ave, Puerto Princesa",
            details = "Near Capitol",
            lat = 9.7420,
            lng = 118.7400,
            isDefault = true
        )
        val rest = MockData.restaurants.first()
        val item1 = MockData.getMenuItems(rest.id).first()
        val item2 = MockData.getMenuItems(rest.id).getOrNull(1) ?: item1

        // Order 1 from same shop
        val res1 = orderRepo.placeFoodOrConvenienceOrder(
            customerId = "cust-multi-1",
            customerName = "Customer Multi",
            customerPhone = "09171234567",
            restaurant = rest,
            items = listOf(CartItem(menuItem = item1, restaurantId = rest.id, restaurantName = rest.name, quantity = 2)),
            deliveryAddress = address,
            deliveryLat = address.lat,
            deliveryLng = address.lng,
            deliveryFee = 2.50,
            serviceFee = 1.00,
            discount = 0.0,
            subtotal = item1.price * 2,
            total = (item1.price * 2) + 3.50,
            cityId = "puerto-princesa",
            deliveryNotes = "Order 1"
        )
        assertTrue(res1.isSuccess)
        val id1 = res1.getOrThrow()

        // Order 2 from same shop
        val res2 = orderRepo.placeFoodOrConvenienceOrder(
            customerId = "cust-multi-1",
            customerName = "Customer Multi",
            customerPhone = "09171234567",
            restaurant = rest,
            items = listOf(CartItem(menuItem = item2, restaurantId = rest.id, restaurantName = rest.name, quantity = 1)),
            deliveryAddress = address,
            deliveryLat = address.lat,
            deliveryLng = address.lng,
            deliveryFee = 2.50,
            serviceFee = 1.00,
            discount = 0.0,
            subtotal = item2.price,
            total = item2.price + 3.50,
            cityId = "puerto-princesa",
            deliveryNotes = "Order 2"
        )
        assertTrue(res2.isSuccess)
        val id2 = res2.getOrThrow()

        // Verify both orders are tracked simultaneously
        assertEquals(2, orderRepo.activeOrders.value.size)
        assertTrue(orderRepo.activeOrders.value.any { it.id == id1 })
        assertTrue(orderRepo.activeOrders.value.any { it.id == id2 })

        // Verify active order can switch between them
        orderRepo.selectActiveOrder(id1)
        assertEquals(id1, orderRepo.activeOrder.value?.id)

        orderRepo.selectActiveOrder(id2)
        assertEquals(id2, orderRepo.activeOrder.value?.id)

        // Cancel order 2: order 1 remains actively tracked
        val cancelRes = orderRepo.cancelOrder(id2, "Customer changed mind")
        assertTrue(cancelRes.isSuccess)
        assertEquals(1, orderRepo.activeOrders.value.size)
        assertEquals(id1, orderRepo.activeOrder.value?.id)
    }

    @Test
    fun `checkout two orders from different shops tracks two orders simultaneously and allows switching`() = runTest {
        val firestoreService = com.example.data.repository.FirestoreService()
        val orderRepo = OrderRepository(firestoreService)

        val address = Address(
            id = "addr-1",
            title = "Home",
            fullAddress = "123 Rizal Ave, Puerto Princesa",
            details = "Near Capitol",
            lat = 9.7420,
            lng = 118.7400,
            isDefault = true
        )
        val restA = MockData.restaurants[0]
        val restB = MockData.restaurants[1]
        val itemA = MockData.getMenuItems(restA.id).first()
        val itemB = MockData.getMenuItems(restB.id).first()

        // Order A from Shop A
        val resA = orderRepo.placeFoodOrConvenienceOrder(
            customerId = "cust-diff-1",
            customerName = "Customer Test",
            customerPhone = "09171234567",
            restaurant = restA,
            items = listOf(CartItem(menuItem = itemA, restaurantId = restA.id, restaurantName = restA.name, quantity = 1)),
            deliveryAddress = address,
            deliveryLat = address.lat,
            deliveryLng = address.lng,
            deliveryFee = 2.50,
            serviceFee = 1.00,
            discount = 0.0,
            subtotal = itemA.price,
            total = itemA.price + 3.50,
            cityId = "puerto-princesa",
            deliveryNotes = "Order A"
        )
        assertTrue(resA.isSuccess)
        val idA = resA.getOrThrow()

        // Order B from Shop B
        val resB = orderRepo.placeFoodOrConvenienceOrder(
            customerId = "cust-diff-1",
            customerName = "Customer Test",
            customerPhone = "09171234567",
            restaurant = restB,
            items = listOf(CartItem(menuItem = itemB, restaurantId = restB.id, restaurantName = restB.name, quantity = 1)),
            deliveryAddress = address,
            deliveryLat = address.lat,
            deliveryLng = address.lng,
            deliveryFee = 2.50,
            serviceFee = 1.00,
            discount = 0.0,
            subtotal = itemB.price,
            total = itemB.price + 3.50,
            cityId = "puerto-princesa",
            deliveryNotes = "Order B"
        )
        assertTrue(resB.isSuccess)
        val idB = resB.getOrThrow()

        // Verify both orders are tracked simultaneously
        assertEquals(2, orderRepo.activeOrders.value.size)
        val trackedMerchantNames = orderRepo.activeOrders.value.map { it.restaurant.name }
        assertTrue(trackedMerchantNames.contains(restA.name))
        assertTrue(trackedMerchantNames.contains(restB.name))

        // Switch tracking to Order A
        orderRepo.selectActiveOrder(idA)
        assertEquals(idA, orderRepo.activeOrder.value?.id)
        assertEquals(restA.name, orderRepo.activeOrder.value?.restaurant?.name)

        // Switch tracking to Order B
        orderRepo.selectActiveOrder(idB)
        assertEquals(idB, orderRepo.activeOrder.value?.id)
        assertEquals(restB.name, orderRepo.activeOrder.value?.restaurant?.name)
    }

    @Test
    fun `Nominatim reverse geocode and search functions behave predictably`() = runTest {
        val searchRes = com.example.data.api.NominatimService.searchPlaces("Puerto Princesa")
        assertNotNull(searchRes)
        val revRes = com.example.data.api.NominatimService.reverseGeocode(9.7392, 118.7353)
        assertNotNull(revRes)
    }

    @Test
    fun `saved addresses is initially empty none and maxed at 3 and editable`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefRepo = PreferenceRepository(context)
        val mainViewModel = com.example.viewmodel.MainViewModel(prefRepo)

        // 1. Initially empty (None)
        assertTrue(mainViewModel.savedAddresses.value.isEmpty())
        assertEquals("None", mainViewModel.currentAddress.value.title)

        // 2. Add Address 1
        val a1 = mainViewModel.addManualAddress("Home", "123 Main St", "Apt 1", setAsActive = true)
        assertNotNull(a1)
        assertEquals(1, mainViewModel.savedAddresses.value.size)
        assertEquals("Home", mainViewModel.currentAddress.value.title)

        // 3. Add Address 2 and 3
        val a2 = mainViewModel.addManualAddress("Work", "456 Office Blvd", "Floor 2", setAsActive = false)
        val a3 = mainViewModel.addManualAddress("Gym", "789 Fitness Way", "", setAsActive = false)
        assertNotNull(a2)
        assertNotNull(a3)
        assertEquals(3, mainViewModel.savedAddresses.value.size)

        // 4. Try adding 4th address - should be blocked by max 3 limit
        val a4 = mainViewModel.addManualAddress("Extra", "101 Extra St", "", setAsActive = false)
        assertEquals(null, a4)
        assertEquals(3, mainViewModel.savedAddresses.value.size)

        // 5. Edit an existing address
        mainViewModel.updateAddress(a1!!.id, "Sweet Home", "123 Main St Renovated", "Apt 1B", setAsActive = true)
        assertEquals(3, mainViewModel.savedAddresses.value.size)
        val updatedA1 = mainViewModel.savedAddresses.value.find { it.id == a1.id }
        assertNotNull(updatedA1)
        assertEquals("Sweet Home", updatedA1?.title)
        assertEquals("123 Main St Renovated", updatedA1?.fullAddress)
        assertEquals("Sweet Home", mainViewModel.currentAddress.value.title)

        // 6. Delete address
        mainViewModel.deleteAddress(a2!!.id)
        assertEquals(2, mainViewModel.savedAddresses.value.size)
    }

    @Test
    fun `profile picture local storage path persists and can be cleared`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefRepo = PreferenceRepository(context)

        // 1. Initial path is null
        val initialPath = prefRepo.profilePicturePathFlow.first()
        assertEquals(null, initialPath)

        // 2. Set path to a local internal file
        val testFile = java.io.File(context.filesDir, "test_avatar.jpg")
        testFile.writeText("test avatar content")
        prefRepo.setProfilePicturePath(testFile.absolutePath)

        val updatedPath = prefRepo.profilePicturePathFlow.first()
        assertEquals(testFile.absolutePath, updatedPath)

        // 3. Remove path
        prefRepo.setProfilePicturePath(null)
        val clearedPath = prefRepo.profilePicturePathFlow.first()
        assertEquals(null, clearedPath)
    }

    @Test
    fun `menu items with sets compute fromPrice using basePrice plus cheapest choice of required sets`() {
        val itemWithSets = MenuItem(
            id = "test_burger",
            restaurantId = "rest_1",
            name = "Test Burger",
            description = "Custom burger",
            price = 100.0,
            category = "Burgers",
            emoji = "🍔",
            hasSets = true,
            sets = listOf(
                ItemSet(
                    id = "set_bun",
                    name = "Bun",
                    required = true,
                    choices = listOf(
                        ItemChoice("bun_1", "Regular", price = 0.0),
                        ItemChoice("bun_2", "Brioche", price = 15.0)
                    )
                ),
                ItemSet(
                    id = "set_cheese",
                    name = "Cheese",
                    required = true,
                    choices = listOf(
                        ItemChoice("ch_1", "Cheddar", price = 10.0),
                        ItemChoice("ch_2", "Gouda", price = 20.0)
                    )
                ),
                ItemSet(
                    id = "set_sauce",
                    name = "Sauce",
                    required = false,
                    choices = listOf(
                        ItemChoice("sc_1", "Mayo", price = 5.0)
                    )
                )
            )
        )

        // Cheapest bun = 0.0, cheapest cheese = 10.0. Optional set not added to fromPrice.
        // fromPrice = 100.0 + 0.0 + 10.0 = 110.0
        assertEquals(110.0, itemWithSets.fromPrice, 0.01)

        val itemWithoutSets = MenuItem(
            id = "test_fries",
            restaurantId = "rest_1",
            name = "Fries",
            description = "French fries",
            price = 50.0,
            category = "Sides",
            emoji = "🍟"
        )
        assertEquals(50.0, itemWithoutSets.fromPrice, 0.01)
    }

    @Test
    fun `cart items with different set choices are stored as distinct lines`() {
        val cartRepo = CartRepository()
        val rest = MockData.restaurants.first()
        val burger = MenuItem(
            id = "custom_burger",
            restaurantId = rest.id,
            name = "Custom Burger",
            description = "Burger",
            price = 100.0,
            category = "Burgers",
            emoji = "🍔",
            hasSets = true
        )

        val selectionA = listOf(
            SelectedItemChoice(setId = "bun", setName = "Bun", choiceId = "brioche", choiceName = "Brioche Bun", price = 15.0)
        )
        val selectionB = listOf(
            SelectedItemChoice(setId = "bun", setName = "Bun", choiceId = "sesame", choiceName = "Sesame Bun", price = 0.0)
        )

        // Add 1 with Brioche
        cartRepo.addItem(burger, rest, 1, selections = selectionA)
        // Add 1 with Sesame
        cartRepo.addItem(burger, rest, 1, selections = selectionB)

        val items = cartRepo.cartItems.value
        assertEquals(2, items.size)

        val lineA = items.find { it.selections.any { s -> s.choiceId == "brioche" } }
        assertNotNull(lineA)
        assertEquals(115.0, lineA!!.unitPrice, 0.01)
        assertEquals(115.0, lineA.totalPrice, 0.01)

        val lineB = items.find { it.selections.any { s -> s.choiceId == "sesame" } }
        assertNotNull(lineB)
        assertEquals(100.0, lineB!!.unitPrice, 0.01)
        assertEquals(100.0, lineB.totalPrice, 0.01)

        // Adding another with Brioche increments lineA
        cartRepo.addItem(burger, rest, 1, selections = selectionA)
        val updatedItems = cartRepo.cartItems.value
        assertEquals(2, updatedItems.size)
        val updatedLineA = updatedItems.find { it.selections.any { s -> s.choiceId == "brioche" } }
        assertEquals(2, updatedLineA?.quantity)
        assertEquals(230.0, updatedLineA!!.totalPrice, 0.01)
    }

    @Test
    fun `unavailable choice is detected and flagged`() {
        val cartRepo = CartRepository()
        val rest = MockData.restaurants.first()
        val item = MenuItem(
            id = "item_1",
            restaurantId = rest.id,
            name = "Item 1",
            description = "Desc",
            price = 50.0,
            category = "Food",
            emoji = "🍽️"
        )
        val choice = SelectedItemChoice("set_1", "Set 1", "choice_1", "Choice 1", 10.0)
        val cartItem = CartItem(
            id = "cart_item_1",
            menuItem = item,
            restaurantId = rest.id,
            restaurantName = rest.name,
            quantity = 1,
            selections = listOf(choice)
        )

        // Initially available
        assertTrue(cartRepo.isChoiceAvailable(cartItem, choice))

        // Mark choice as unavailable
        cartRepo.markChoiceAvailability(item.id, "set_1", "choice_1", false)
        assertFalse(cartRepo.isChoiceAvailable(cartItem, choice))
    }

    @Test
    fun `crash report preferences save and clear correctly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("delivio_crash_prefs", Context.MODE_PRIVATE)

        // Simulate crash save
        prefs.edit()
            .putBoolean("has_crash", true)
            .putString("stack_trace", "java.lang.NullPointerException: test crash")
            .putLong("crash_timestamp", 1700000000000L)
            .putString("crash_screen", "Home")
            .commit()

        assertTrue(prefs.getBoolean("has_crash", false))
        assertEquals("java.lang.NullPointerException: test crash", prefs.getString("stack_trace", null))
        assertEquals("Home", prefs.getString("crash_screen", null))

        // Clear crash
        prefs.edit().clear().commit()
        assertFalse(prefs.getBoolean("has_crash", false))
    }

    @Test
    fun `firestore error bus emits non fatal banner correctly`() = runTest {
        val bannerFlow = com.example.util.FirestoreErrorBus.nonFatalBanner
        com.example.util.FirestoreErrorBus.showNonFatalBanner("banners", "PERMISSION_DENIED")
        val received = bannerFlow.first()
        assertEquals("Could not load banners: PERMISSION_DENIED", received)
    }

    @Test
    fun `city services default to all true when missing`() {
        val defaultCity = com.example.data.model.City()
        assertEquals(5, defaultCity.services.size)
        assertTrue(defaultCity.services["food"] == true)
        assertTrue(defaultCity.services["convenience"] == true)
        assertTrue(defaultCity.services["parcel"] == true)
        assertTrue(defaultCity.services["pabili"] == true)
        assertTrue(defaultCity.services["taxi"] == true)
    }

    @Test
    fun `banner discount model defaults and fields`() {
        val discount = com.example.data.model.BannerDiscount(
            type = "fixed",
            value = 50.0,
            minOrder = 200.0,
            maxDiscount = 50.0,
            code = "DELIVIO50"
        )
        val banner = com.example.data.model.Banner(
            id = "b1",
            title = "Promo",
            imageUrl = "https://example.com/banner.png",
            discount = discount
        )
        assertNotNull(banner.discount)
        assertEquals(50.0, banner.discount!!.value, 0.01)
        assertEquals("DELIVIO50", banner.discount!!.code)
    }

    @Test
    fun `serviceOn returns true when neither platform nor city is false`() {
        val platform = com.example.data.model.PlatformSettings(
            services = mapOf("food" to true, "taxi" to false)
        )
        val city = com.example.data.model.City(
            services = mapOf("food" to true, "convenience" to false)
        )

        fun serviceOn(key: String): Boolean {
            val pOn = platform.services[key] != false
            val cOn = city.services[key] != false
            return pOn && cOn
        }

        assertTrue(serviceOn("food"))
        assertFalse(serviceOn("convenience")) // false in city
        assertFalse(serviceOn("taxi")) // false in platform
        assertTrue(serviceOn("parcel")) // missing in both -> true
        assertTrue(serviceOn("pabili")) // missing in both -> true
    }

    @Test
    fun `coordinate matching finds closest city within radius`() {
        val ppc = com.example.data.model.City(
            id = "puerto-princesa",
            name = "Puerto Princesa",
            lat = 9.7392,
            lng = 118.7353,
            radiusKm = 25.0
        )
        val coron = com.example.data.model.City(
            id = "coron",
            name = "Coron",
            lat = 12.0000,
            lng = 120.2000,
            radiusKm = 20.0
        )
        val cities = listOf(ppc, coron)

        fun matchCity(lat: Double, lng: Double): com.example.data.model.City? {
            val matches = cities.mapNotNull { city ->
                val dist = com.example.data.api.OsrmRoutingService.calculateHaversineDistanceKm(lat, lng, city.lat, city.lng)
                val radius = if (city.radiusKm > 0.0) city.radiusKm else 25.0
                if (dist <= radius) Pair(city, dist) else null
            }.sortedBy { it.second }
            return matches.firstOrNull()?.first
        }

        // Close to Puerto Princesa
        val matchedPpc = matchCity(9.7400, 118.7360)
        assertNotNull(matchedPpc)
        assertEquals("puerto-princesa", matchedPpc!!.id)

        // Close to Coron
        val matchedCoron = matchCity(12.0100, 120.2050)
        assertNotNull(matchedCoron)
        assertEquals("coron", matchedCoron!!.id)

        // In Manila (far from both)
        val matchedManila = matchCity(14.5995, 120.9842)
        assertNull(matchedManila)
    }

    @Test
    fun `formatDisplayName formats first name plus last initial`() {
        assertEquals("Juan C.", com.example.util.Formatters.formatDisplayName("Juan Cruz"))
        assertEquals("John S.", com.example.util.Formatters.formatDisplayName("John Smith"))
        assertEquals("Maria", com.example.util.Formatters.formatDisplayName("Maria"))
        assertEquals("Delivio Customer", com.example.util.Formatters.formatDisplayName(""))
        assertEquals("Delivio Customer", com.example.util.Formatters.formatDisplayName(null))
    }

    @Test
    fun `ItemSet ruleText and selection limits match requirements`() {
        // required and max 1: "Select one"
        val setReqSingle = ItemSet(name = "Bun", required = true, minSelect = 1, maxSelect = 1)
        assertEquals("Select one", setReqSingle.ruleText)

        // required and max N: "Select 1 to N"
        val setReqMulti = ItemSet(name = "Toppings", required = true, minSelect = 1, maxSelect = 3)
        assertEquals("Select 1 to 3", setReqMulti.ruleText)

        // required at least M up to N (M > 1)
        val setReqMinMulti = ItemSet(name = "Sauces", required = true, minSelect = 2, maxSelect = 4)
        assertEquals("Select at least 2, up to 4", setReqMinMulti.ruleText)

        // optional and max 1: "Optional"
        val setOptSingle = ItemSet(name = "Drink", required = false, minSelect = 0, maxSelect = 1)
        assertEquals("Optional", setOptSingle.ruleText)

        // optional and max N: "Optional - up to N"
        val setOptMulti = ItemSet(name = "Extras", required = false, minSelect = 0, maxSelect = 5)
        assertEquals("Optional - up to 5", setOptMulti.ruleText)
    }

    @Test
    fun `MerchantSubcategory sortOrder ordering works correctly`() {
        val list = listOf(
            com.example.data.model.MerchantSubcategory("sub_3", "Dessert", 3),
            com.example.data.model.MerchantSubcategory("sub_1", "Burgers", 1),
            com.example.data.model.MerchantSubcategory("sub_2", "Sides", 2)
        ).sortedBy { it.sortOrder }

        assertEquals("Burgers", list[0].name)
        assertEquals("Sides", list[1].name)
        assertEquals("Dessert", list[2].name)
    }

    @Test
    fun `cuisine tag filtering matches case-insensitively`() {
        val rest = com.example.data.model.Restaurant(
            id = "rest_test",
            name = "Test Burger Joint",
            category = "Food",
            cuisineTags = listOf("Burgers", "Fast Food", "American"),
            rating = 4.8,
            reviewCount = 100,
            etaMinutes = 20,
            deliveryFee = 39.0,
            address = "Rizal Ave",
            phone = "123"
        )

        assertTrue(rest.cuisineTags.any { it.equals("burgers", ignoreCase = true) })
        assertTrue(rest.cuisineTags.any { it.equals("FAST FOOD", ignoreCase = true) })
        assertFalse(rest.cuisineTags.any { it.equals("pizza", ignoreCase = true) })
    }

    @Test
    fun `delivery option adjustments and special radius calculate fee correctly`() {
        val baseDeliveryFee = 39.0
        val specialRadius = com.example.data.model.MerchantSpecialRadius(
            enabled = true,
            radiusKm = 8.0,
            customerExtraFee = 15.0,
            riderExtraFee = 20.0
        )
        val rest = com.example.data.model.Restaurant(
            id = "rest_sr",
            name = "Outskirts Cafe",
            category = "Food",
            rating = 4.7,
            reviewCount = 50,
            etaMinutes = 25,
            deliveryFee = baseDeliveryFee,
            address = "North Road",
            phone = "123",
            specialRadius = specialRadius
        )

        val dispatchSettings = com.example.data.model.DispatchSettings(
            defaultOption = "priority",
            searchRadiusKm = 5.0,
            options = mapOf(
                "priority" to com.example.data.model.DispatchOptionConfig(enabled = true, feeAdjustType = "flat", feeAdjustValue = 20.0, holdMinutes = 0, maxBatch = 1),
                "standard" to com.example.data.model.DispatchOptionConfig(enabled = true, feeAdjustType = "flat", feeAdjustValue = 0.0, holdMinutes = 5, maxBatch = 2),
                "saver" to com.example.data.model.DispatchOptionConfig(enabled = true, feeAdjustType = "flat", feeAdjustValue = -10.0, holdMinutes = 12, maxBatch = 3)
            )
        )

        fun calcOptionFee(optKey: String): Double {
            val config = dispatchSettings.options[optKey]!!
            val adjust = if (config.feeAdjustType == "percent") baseDeliveryFee * (config.feeAdjustValue / 100.0) else config.feeAdjustValue
            val adjustedBase = (baseDeliveryFee + adjust).coerceAtLeast(0.0)
            val custExtra = if (rest.specialRadius?.enabled == true) rest.specialRadius.customerExtraFee else 0.0
            return (adjustedBase + custExtra).coerceAtLeast(0.0)
        }

        assertEquals(74.0, calcOptionFee("priority"), 0.01) // 39 + 20 + 15
        assertEquals(54.0, calcOptionFee("standard"), 0.01) // 39 + 0 + 15
        assertEquals(44.0, calcOptionFee("saver"), 0.01)    // 39 - 10 + 15
    }

    @Test
    fun `isSplitChild orders are excluded from customer order history`() {
        val parentOrder = com.example.data.model.OrderDocument(
            id = "order_parent",
            customerId = "user_1",
            isSplitChild = false,
            splitOut = mapOf("childId" to "order_child")
        )
        val childOrder = com.example.data.model.OrderDocument(
            id = "order_child",
            customerId = "user_1",
            isSplitChild = true
        )
        val normalOrder = com.example.data.model.OrderDocument(
            id = "order_normal",
            customerId = "user_1",
            isSplitChild = false
        )

        val documents = listOf(parentOrder, childOrder, normalOrder)
        val filtered = documents.filter { !it.isSplitChild }

        assertEquals(2, filtered.size)
        assertTrue(filtered.any { it.id == "order_parent" })
        assertTrue(filtered.any { it.id == "order_normal" })
        assertFalse(filtered.any { it.id == "order_child" })
    }

    @Test
    fun `delivery options ETA formulas match priority standard and saver specifications`() {
        val baseEta = 25 // prep time + travel time
        val holdPriority = 0
        val holdStandard = 5
        val holdSaver = 12

        val etaPriority = baseEta
        val etaStandard = baseEta + kotlin.math.round(holdStandard / 2.0).toInt()
        val etaSaver = baseEta + holdSaver

        assertEquals(25, etaPriority) // baseEta
        assertEquals(27, etaStandard) // 25 + round(2.5) = 25 + 2 = 27 (about half of holdMinutes)
        assertEquals(37, etaSaver)    // 25 + 12 = 37

        // Priority card eta text
        val priorityMin = (etaPriority - 4).coerceAtLeast(10)
        val priorityMax = etaPriority + 4
        assertEquals("21-29 mins", "$priorityMin-$priorityMax mins")

        // Standard card eta text
        val standardMin = (etaStandard - 4).coerceAtLeast(10)
        val standardMax = etaStandard + 4
        assertEquals("23-31 mins", "$standardMin-$standardMax mins")

        // Saver card eta text
        val saverMin = (etaSaver - 4).coerceAtLeast(10)
        val saverMax = etaSaver + 4
        assertEquals("33-41 mins", "$saverMin-$saverMax mins")
    }

    @Test
    fun `dispatch object saves endsWhenReady with true for priority and standard and false for saver`() {
        val defaultSettings = com.example.data.model.DispatchSettings()
        val priorityConfig = defaultSettings.options["priority"]!!
        val standardConfig = defaultSettings.options["standard"]!!
        val saverConfig = defaultSettings.options["saver"]!!

        assertTrue(priorityConfig.endsWhenReady)
        assertTrue(standardConfig.endsWhenReady)
        assertFalse(saverConfig.endsWhenReady)

        fun createDispatchMap(optKey: String, config: com.example.data.model.DispatchOptionConfig): Map<String, Any?> {
            return mapOf(
                "option" to optKey,
                "holdMinutes" to config.holdMinutes,
                "maxBatch" to config.maxBatch,
                "poolable" to (config.maxBatch > 1),
                "searchRadiusKm" to 5.0,
                "specialRadius" to null,
                "endsWhenReady" to config.endsWhenReady
            )
        }

        val priorityMap = createDispatchMap("priority", priorityConfig)
        val standardMap = createDispatchMap("standard", standardConfig)
        val saverMap = createDispatchMap("saver", saverConfig)

        assertEquals(true, priorityMap["endsWhenReady"])
        assertEquals(true, standardMap["endsWhenReady"])
        assertEquals(false, saverMap["endsWhenReady"])
        assertEquals(false, priorityMap["poolable"])
        assertEquals(true, standardMap["poolable"])
        assertEquals(true, saverMap["poolable"])
    }

    @Test
    fun `order number generation complies with 6 alphanumeric chars without O 0 I 1`() {
        val forbidden = setOf('O', '0', 'I', '1')
        val validChars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toSet()

        for (i in 1..100) {
            val orderNum = com.example.util.OrderNumberUtil.generateOrderNumber()
            assertEquals(6, orderNum.length)
            for (char in orderNum) {
                assertFalse("Char '$char' must not be in forbidden set", forbidden.contains(char))
                assertTrue("Char '$char' must be in allowed charset", validChars.contains(char))
            }
        }
    }

    @Test
    fun `order number formatting displays hash prefix with fallback to last 6 doc id uppercase`() {
        // With explicit orderNumber
        assertEquals("#K7PM2X", com.example.util.OrderNumberUtil.formatOrderNumber("K7PM2X", "doc123456789"))
        assertEquals("#A9B8C7", com.example.util.OrderNumberUtil.formatOrderNumber("A9B8C7", null))

        // Fallback for older orders without orderNumber
        assertEquals("#456789", com.example.util.OrderNumberUtil.formatOrderNumber("", "doc123456789"))
        assertEquals("#ABCDEF", com.example.util.OrderNumberUtil.formatOrderNumber(null, "ord_abcdef"))

        // Display property on OrderDocument and Order
        val doc = com.example.data.model.OrderDocument(
            id = "doc_998877",
            orderNumber = "X5Y6Z7"
        )
        assertEquals("#X5Y6Z7", doc.displayOrderNumber)

        val oldDoc = com.example.data.model.OrderDocument(
            id = "order_old_abc123",
            orderNumber = ""
        )
        assertEquals("#ABC123", oldDoc.displayOrderNumber)
    }

    @Test
    fun `must try add-ons filter sets and in-cart items and order by bestSellers then tags then remaining`() {
        val checkoutSettings = com.example.data.model.CheckoutSettings(
            mustTry = com.example.data.model.MustTryConfig(
                enabled = true,
                title = "Must try!",
                description = "Add a favorite from this shop to go with your order",
                maxItems = 8
            )
        )

        val item1 = com.example.data.model.MerchantItem(
            id = "item_1",
            name = "Classic Burger",
            price = 150.0,
            hasSets = false,
            isAvailable = true,
            inStock = true,
            tags = listOf("Burgers")
        )
        val item2 = com.example.data.model.MerchantItem(
            id = "item_2",
            name = "Special Combo",
            price = 250.0,
            hasSets = true, // Has sets -> must be excluded!
            isAvailable = true,
            inStock = true
        )
        val item3 = com.example.data.model.MerchantItem(
            id = "item_3",
            name = "Soldout Drink",
            price = 50.0,
            hasSets = false,
            isAvailable = false, // Not available -> must be excluded!
            inStock = true
        )
        val item4 = taggedBestSeller(
            id = "item_4",
            name = "Garlic Rice"
        )
        val item5 = com.example.data.model.MerchantItem(
            id = "item_5",
            name = "Top Seller Fries",
            price = 80.0,
            hasSets = false,
            isAvailable = true,
            inStock = true
        )
        val item6 = com.example.data.model.MerchantItem(
            id = "item_6",
            name = "Soda Can",
            price = 45.0,
            hasSets = false,
            isAvailable = true,
            inStock = true
        )

        val allItems = listOf(item1, item2, item3, item4, item5, item6)
        val cartItemIds = setOf("item_6") // item_6 is in the cart -> must be excluded!

        // Best sellers stats: item_5 has 100 sales
        val bestSellers = listOf(
            com.example.data.model.BestSellerStatItem(itemId = "item_5", qty = 100)
        )

        // Filter eligible
        val eligible = allItems.filter { item ->
            !item.hasSets &&
            item.sets.isEmpty() &&
            item.isAvailable &&
            item.inStock &&
            !cartItemIds.contains(item.id)
        }

        // Eligible should only contain item_1, item_4, item_5 (item_2 has sets, item_3 not available, item_6 in cart)
        assertEquals(3, eligible.size)
        assertTrue(eligible.any { it.id == "item_1" })
        assertTrue(eligible.any { it.id == "item_4" })
        assertTrue(eligible.any { it.id == "item_5" })

        // Ordering logic:
        val eligibleMap = eligible.associateBy { it.id }.toMutableMap()
        val group1 = bestSellers.mapNotNull { stat -> eligibleMap.remove(stat.itemId) }
        val group2 = eligibleMap.values.filter { it.tags.any { t -> t.equals("Best Seller", ignoreCase = true) } }
        group2.forEach { eligibleMap.remove(it.id) }
        val group3 = eligibleMap.values.toList()

        val ordered = (group1 + group2 + group3).take(checkoutSettings.mustTry.maxItems)

        assertEquals("item_5", ordered[0].id) // Group 1: stats/bestSellers
        assertEquals("item_4", ordered[1].id) // Group 2: tagged "Best Seller"
        assertEquals("item_1", ordered[2].id) // Group 3: remaining eligible
    }

    @Test
    fun `menu availableNow evaluates active status, no schedule, daily schedule, and overnight schedule correctly`() {
        val timezone = "Asia/Manila"
        val zoneId = java.time.ZoneId.of(timezone)

        // 1. Inactive menu -> false
        val inactiveMenu = com.example.data.model.MerchantMenu(
            id = "m1",
            active = false,
            schedule = com.example.data.model.MenuSchedule(start = "06:00", end = "22:00")
        )
        assertFalse(com.example.util.MenuAvailabilityUtil.isMenuAvailable(inactiveMenu, System.currentTimeMillis(), timezone))

        // 2. Active with no schedule -> true
        val noScheduleMenu = com.example.data.model.MerchantMenu(
            id = "m2",
            active = true,
            schedule = null
        )
        assertTrue(com.example.util.MenuAvailabilityUtil.isMenuAvailable(noScheduleMenu, System.currentTimeMillis(), timezone))

        // 3. Breakfast menu 06:00 to 10:00 on all days
        val breakfastMenu = com.example.data.model.MerchantMenu(
            id = "breakfast",
            active = true,
            schedule = com.example.data.model.MenuSchedule(
                days = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun"),
                start = "06:00",
                end = "10:00"
            )
        )

        // Helper to get millis for a specific day and time in Manila
        fun getMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
            val zdt = java.time.ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zoneId)
            return zdt.toInstant().toEpochMilli()
        }

        // Friday 2026-11-06 08:30 AM -> inside breakfast window
        val friMorning = getMillis(2026, 11, 6, 8, 30)
        assertTrue(com.example.util.MenuAvailabilityUtil.isMenuAvailable(breakfastMenu, friMorning, timezone))

        // Friday 2026-11-06 11:00 AM -> outside breakfast window
        val friNoon = getMillis(2026, 11, 6, 11, 0)
        assertFalse(com.example.util.MenuAvailabilityUtil.isMenuAvailable(breakfastMenu, friNoon, timezone))

        // 4. Weekday only breakfast (Mon-Fri)
        val weekdayBreakfast = com.example.data.model.MerchantMenu(
            id = "weekday_breakfast",
            active = true,
            schedule = com.example.data.model.MenuSchedule(
                days = listOf("mon", "tue", "wed", "thu", "fri"),
                start = "06:00",
                end = "10:00"
            )
        )
        // Friday 08:30 AM -> true
        assertTrue(com.example.util.MenuAvailabilityUtil.isMenuAvailable(weekdayBreakfast, friMorning, timezone))

        // Saturday 2026-11-07 08:30 AM -> false (Saturday not in days)
        val satMorning = getMillis(2026, 11, 7, 8, 30)
        assertFalse(com.example.util.MenuAvailabilityUtil.isMenuAvailable(weekdayBreakfast, satMorning, timezone))

        // 5. Overnight menu 22:00 to 04:00 (Friday & Saturday nights)
        val overnightMenu = com.example.data.model.MerchantMenu(
            id = "late_night",
            active = true,
            schedule = com.example.data.model.MenuSchedule(
                days = listOf("fri", "sat"),
                start = "22:00",
                end = "04:00"
            )
        )
        // Friday 23:00 -> true (today is Friday in days, curMinutes >= 22:00)
        val friLateNight = getMillis(2026, 11, 6, 23, 0)
        assertTrue(com.example.util.MenuAvailabilityUtil.isMenuAvailable(overnightMenu, friLateNight, timezone))

        // Saturday 02:00 AM -> true (yesterday was Friday in days, curMinutes <= 04:00)
        val satEarlyMorning = getMillis(2026, 11, 7, 2, 0)
        assertTrue(com.example.util.MenuAvailabilityUtil.isMenuAvailable(overnightMenu, satEarlyMorning, timezone))

        // Saturday 05:00 AM -> false (past 04:00 window)
        val satDawn = getMillis(2026, 11, 7, 5, 0)
        assertFalse(com.example.util.MenuAvailabilityUtil.isMenuAvailable(overnightMenu, satDawn, timezone))

        // Sunday 02:00 AM -> true (yesterday was Saturday in days, curMinutes <= 04:00)
        val sunEarlyMorning = getMillis(2026, 11, 8, 2, 0)
        assertTrue(com.example.util.MenuAvailabilityUtil.isMenuAvailable(overnightMenu, sunEarlyMorning, timezone))

        // Monday 02:00 AM -> false (yesterday was Sunday, not in days)
        val monEarlyMorning = getMillis(2026, 11, 9, 2, 0)
        assertFalse(com.example.util.MenuAvailabilityUtil.isMenuAvailable(overnightMenu, monEarlyMorning, timezone))
    }

    @Test
    fun `item orderability checks inStock, empty menus fallback, default main menuId, and menu availability`() {
        val timezone = "Asia/Manila"
        val zoneId = java.time.ZoneId.of(timezone)

        fun getMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
            val zdt = java.time.ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zoneId)
            return zdt.toInstant().toEpochMilli()
        }

        val testTimeMorning = getMillis(2026, 11, 6, 8, 0) // 8:00 AM
        val testTimeAfternoon = getMillis(2026, 11, 6, 14, 0) // 2:00 PM

        val breakfastMenu = com.example.data.model.MerchantMenu(
            id = "breakfast",
            name = "Breakfast",
            active = true,
            schedule = com.example.data.model.MenuSchedule(start = "06:00", end = "10:00")
        )
        val mainMenu = com.example.data.model.MerchantMenu(
            id = "main",
            name = "Main",
            active = true,
            schedule = com.example.data.model.MenuSchedule(start = "10:00", end = "22:00")
        )
        val menus = listOf(breakfastMenu, mainMenu)

        val pancakeItem = com.example.data.model.MerchantItem(
            id = "pancakes",
            name = "Pancakes",
            price = 120.0,
            inStock = true,
            isAvailable = true,
            menuIds = listOf("breakfast")
        )

        val defaultItem = com.example.data.model.MerchantItem(
            id = "burger",
            name = "Burger",
            price = 150.0,
            inStock = true,
            isAvailable = true,
            menuIds = emptyList() // Missing or empty means ["main"]
        )

        val soldOutItem = com.example.data.model.MerchantItem(
            id = "waffles",
            name = "Waffles",
            price = 130.0,
            inStock = false, // Out of stock!
            isAvailable = true,
            menuIds = listOf("breakfast")
        )

        // 1. In the morning (8:00 AM):
        // Pancakes (breakfast) is orderable
        assertTrue(com.example.util.MenuAvailabilityUtil.isItemOrderable(pancakeItem, menus, testTimeMorning, timezone))
        // Burger (default main: 10:00-22:00) is NOT orderable at 8:00 AM
        assertFalse(com.example.util.MenuAvailabilityUtil.isItemOrderable(defaultItem, menus, testTimeMorning, timezone))
        // Sold out item is NOT orderable
        assertFalse(com.example.util.MenuAvailabilityUtil.isItemOrderable(soldOutItem, menus, testTimeMorning, timezone))

        // 2. In the afternoon (2:00 PM):
        // Pancakes (breakfast) is NOT orderable
        assertFalse(com.example.util.MenuAvailabilityUtil.isItemOrderable(pancakeItem, menus, testTimeAfternoon, timezone))
        // Burger (default main) IS orderable at 2:00 PM
        assertTrue(com.example.util.MenuAvailabilityUtil.isItemOrderable(defaultItem, menus, testTimeAfternoon, timezone))

        // 3. Shop with no menus at all -> everything in stock is available
        val emptyMenus = emptyList<com.example.data.model.MerchantMenu>()
        assertTrue(com.example.util.MenuAvailabilityUtil.isItemOrderable(pancakeItem, emptyMenus, testTimeAfternoon, timezone))
        assertTrue(com.example.util.MenuAvailabilityUtil.isItemOrderable(defaultItem, emptyMenus, testTimeMorning, timezone))
        assertFalse(com.example.util.MenuAvailabilityUtil.isItemOrderable(soldOutItem, emptyMenus, testTimeMorning, timezone))
    }

    @Test
    fun `availability label formats daily hours and day-specific hours matching specifications`() {
        val dailyBreakfast = com.example.data.model.MerchantMenu(
            id = "breakfast",
            schedule = com.example.data.model.MenuSchedule(
                days = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun"),
                start = "06:00",
                end = "10:00"
            )
        )
        val item1 = com.example.data.model.MerchantItem(id = "item1", menuIds = listOf("breakfast"))
        val label1 = com.example.util.MenuAvailabilityUtil.getAvailabilityLabel(item1, listOf(dailyBreakfast))
        assertEquals("Available 6:00 AM - 10:00 AM", label1)

        val weekdayMenu = com.example.data.model.MerchantMenu(
            id = "lunch",
            schedule = com.example.data.model.MenuSchedule(
                days = listOf("mon", "tue", "wed", "thu", "fri"),
                start = "11:00",
                end = "14:00"
            )
        )
        val item2 = com.example.data.model.MerchantItem(id = "item2", menuIds = listOf("lunch"))
        val label2 = com.example.util.MenuAvailabilityUtil.getAvailabilityLabel(item2, listOf(weekdayMenu))
        assertEquals("Available Mon-Fri 11:00 AM - 2:00 PM", label2)
    }

    @Test
    fun `formatItemSize formats non-blank sizes with leading slash`() {
        assertEquals("/225g", com.example.util.Formatters.formatItemSize("225g"))
        assertEquals("/190ml", com.example.util.Formatters.formatItemSize(" 190ml "))
        assertEquals("/12pcs", com.example.util.Formatters.formatItemSize("/12pcs"))
        assertEquals("", com.example.util.Formatters.formatItemSize(""))
        assertEquals("", com.example.util.Formatters.formatItemSize("   "))
        assertEquals("", com.example.util.Formatters.formatItemSize(null))
    }

    @Test
    fun `item availability checks days hours date range and overnight windows in Manila timezone`() {
        val zone = java.time.ZoneId.of("Asia/Manila")

        // Weekday breakfast 06:00 - 10:00
        val breakfastAvail = com.example.data.model.ItemAvailability(
            days = listOf("mon", "tue", "wed", "thu", "fri"),
            start = "06:00",
            end = "10:00"
        )
        val fri8am = java.time.ZonedDateTime.of(2026, 11, 13, 8, 0, 0, 0, zone).toInstant().toEpochMilli() // Friday
        val fri11am = java.time.ZonedDateTime.of(2026, 11, 13, 11, 0, 0, 0, zone).toInstant().toEpochMilli()
        val sat8am = java.time.ZonedDateTime.of(2026, 11, 14, 8, 0, 0, 0, zone).toInstant().toEpochMilli() // Saturday

        assertTrue(com.example.util.MenuAvailabilityUtil.isItemAvailabilitySatisfied(breakfastAvail, fri8am, "Asia/Manila"))
        assertFalse(com.example.util.MenuAvailabilityUtil.isItemAvailabilitySatisfied(breakfastAvail, fri11am, "Asia/Manila"))
        assertFalse(com.example.util.MenuAvailabilityUtil.isItemAvailabilitySatisfied(breakfastAvail, sat8am, "Asia/Manila"))

        // Overnight window: Fri-Sat 22:00 - 02:00 checks yesterday for post-midnight hours
        val lateNightAvail = com.example.data.model.ItemAvailability(
            days = listOf("fri"),
            start = "22:00",
            end = "02:00"
        )
        val fri11pm = java.time.ZonedDateTime.of(2026, 11, 13, 23, 0, 0, 0, zone).toInstant().toEpochMilli() // Fri 23:00
        val sat1am = java.time.ZonedDateTime.of(2026, 11, 14, 1, 0, 0, 0, zone).toInstant().toEpochMilli()  // Sat 01:00 (yesterday was Fri)
        val fri1am = java.time.ZonedDateTime.of(2026, 11, 13, 1, 0, 0, 0, zone).toInstant().toEpochMilli()  // Fri 01:00 (yesterday was Thu)

        assertTrue(com.example.util.MenuAvailabilityUtil.isItemAvailabilitySatisfied(lateNightAvail, fri11pm, "Asia/Manila"))
        assertTrue(com.example.util.MenuAvailabilityUtil.isItemAvailabilitySatisfied(lateNightAvail, sat1am, "Asia/Manila"))
        assertFalse(com.example.util.MenuAvailabilityUtil.isItemAvailabilitySatisfied(lateNightAvail, fri1am, "Asia/Manila"))

        // Seasonal date range Nov 20 - Dec 31
        val holidayAvail = com.example.data.model.ItemAvailability(
            dateFrom = "2026-11-20",
            dateTo = "2026-12-31"
        )
        val nov19 = java.time.ZonedDateTime.of(2026, 11, 19, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
        val nov25 = java.time.ZonedDateTime.of(2026, 11, 25, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertFalse(com.example.util.MenuAvailabilityUtil.isItemAvailabilitySatisfied(holidayAvail, nov19, "Asia/Manila"))
        assertTrue(com.example.util.MenuAvailabilityUtil.isItemAvailabilitySatisfied(holidayAvail, nov25, "Asia/Manila"))

        // Availability labels
        val itemBreakfast = com.example.data.model.MerchantItem(id = "b1", availability = breakfastAvail)
        assertEquals(
            "Available Mon-Fri, 6:00 AM - 10:00 AM",
            com.example.util.MenuAvailabilityUtil.getAvailabilityLabel(itemBreakfast, emptyList(), "Asia/Manila")
        )
        val itemHoliday = com.example.data.model.MerchantItem(id = "h1", availability = holidayAvail)
        assertEquals(
            "Available Nov 20 - Dec 31",
            com.example.util.MenuAvailabilityUtil.getAvailabilityLabel(itemHoliday, emptyList(), "Asia/Manila")
        )

        // Day keys via java.time (mon..sun), never numeric day index
        assertEquals("mon", com.example.util.MenuAvailabilityUtil.toDayKey(java.time.DayOfWeek.MONDAY))
        assertEquals("tue", com.example.util.MenuAvailabilityUtil.toDayKey(java.time.DayOfWeek.TUESDAY))
        assertEquals("wed", com.example.util.MenuAvailabilityUtil.toDayKey(java.time.DayOfWeek.WEDNESDAY))
        assertEquals("thu", com.example.util.MenuAvailabilityUtil.toDayKey(java.time.DayOfWeek.THURSDAY))
        assertEquals("fri", com.example.util.MenuAvailabilityUtil.toDayKey(java.time.DayOfWeek.FRIDAY))
        assertEquals("sat", com.example.util.MenuAvailabilityUtil.toDayKey(java.time.DayOfWeek.SATURDAY))
        assertEquals("sun", com.example.util.MenuAvailabilityUtil.toDayKey(java.time.DayOfWeek.SUNDAY))
        assertFalse(com.example.util.MenuAvailabilityUtil.isDayInSchedule(java.time.DayOfWeek.MONDAY, listOf("1")))
        assertFalse(com.example.util.MenuAvailabilityUtil.isDayInSchedule(java.time.DayOfWeek.SUNDAY, listOf("0", "7")))

        // Day-only menu schedule without hours still checks day key
        val sundayMenu = com.example.data.model.MerchantMenu(
            id = "sun_special",
            active = true,
            schedule = com.example.data.model.MenuSchedule(days = listOf("sun"))
        )
        val sunNoon = java.time.ZonedDateTime.of(2026, 11, 15, 12, 0, 0, 0, zone).toInstant().toEpochMilli() // Sunday
        assertTrue(com.example.util.MenuAvailabilityUtil.isMenuAvailable(sundayMenu, sunNoon, "Asia/Manila"))
        assertFalse(com.example.util.MenuAvailabilityUtil.isMenuAvailable(sundayMenu, fri8am, "Asia/Manila"))
    }

    @Test
    fun `formatShopDisplayName formats name and branchName per outlets contract`() {
        assertEquals(
            "Jollibee - Palawan Rizal",
            com.example.util.BranchGroupingUtil.formatShopDisplayName("Jollibee", "Palawan Rizal")
        )
        assertEquals(
            "Jollibee",
            com.example.util.BranchGroupingUtil.formatShopDisplayName("Jollibee", "")
        )
        assertEquals(
            "Jollibee",
            com.example.util.BranchGroupingUtil.formatShopDisplayName("Jollibee", null)
        )
        // Avoid double suffix if already formatted
        assertEquals(
            "Jollibee - Palawan Rizal",
            com.example.util.BranchGroupingUtil.formatShopDisplayName("Jollibee - Palawan Rizal", "Palawan Rizal")
        )
    }

    @Test
    fun `branch grouping selects nearest deliverable branch respects radius and specialRadius and breaks ties by rating`() {
        val customerLat = 9.7392
        val customerLng = 118.7353

        fun makeBranch(
            id: String,
            name: String,
            chainId: String = "",
            branchName: String = "",
            branchCode: String = "",
            lat: Double,
            lng: Double,
            rating: Double = 4.5,
            status: String = "active",
            type: String = "food",
            specialRadius: com.example.data.model.MerchantSpecialRadius? = null
        ) = com.example.data.model.Restaurant(
            id = id,
            name = name,
            category = "Fast Food",
            type = type,
            rating = rating,
            reviewCount = 100,
            etaMinutes = 20,
            deliveryFee = 39.0,
            address = branchName,
            phone = "09170000000",
            status = status,
            isConvenience = type == "convenience",
            lat = lat,
            lng = lng,
            chainId = chainId,
            branchName = branchName,
            branchCode = branchCode,
            specialRadius = specialRadius
        )

        val jolliRizal = makeBranch(
            id = "j1", name = "Jollibee", chainId = "chain_j",
            branchName = "Palawan Rizal", branchCode = "PR01",
            lat = 9.7400, lng = 118.7360, rating = 4.6 // ~0.12 km
        )
        val jolliSanJose = makeBranch(
            id = "j2", name = "Jollibee", chainId = "chain_j",
            branchName = "San Jose", branchCode = "SJ02",
            lat = 9.7700, lng = 118.7490, rating = 4.9 // ~3.7 km
        )
        val jolliInactive = makeBranch(
            id = "j3", name = "Jollibee", chainId = "chain_j",
            branchName = "Closed Branch", branchCode = "CB03",
            lat = 9.7393, lng = 118.7354, rating = 5.0, status = "suspended"
        )
        val singleShop = makeBranch(
            id = "s1", name = "Local Kitchen", chainId = "",
            lat = 9.7410, lng = 118.7370, rating = 4.7 // ~0.27 km
        )
        // Out of 5km default radius unless specialRadius covers it
        val farWithSpecial = makeBranch(
            id = "j4", name = "Far Grill", chainId = "chain_far",
            branchName = "Honda Bay", branchCode = "HB01",
            lat = 9.8100, lng = 118.7400, rating = 4.5, // ~7.9 km
            specialRadius = com.example.data.model.MerchantSpecialRadius(enabled = true, radiusKm = 10.0)
        )
        val tooFarConv = makeBranch(
            id = "c1", name = "Conv Store", chainId = "",
            type = "convenience",
            lat = 9.8500, lng = 118.7400, rating = 4.5 // ~12.3 km (out of 5km)
        )

        val allShops = listOf(jolliSanJose, jolliRizal, jolliInactive, singleShop, farWithSpecial, tooFarConv)

        val grouped = com.example.util.BranchGroupingUtil.groupByChainNearestBranch(
            shops = allShops,
            customerLat = customerLat,
            customerLng = customerLng,
            defaultRadiusKm = 5.0,
            isServiceOnForType = { true }
        )

        // Expected: jolliRizal (nearest active of chain_j), singleShop, farWithSpecial (within specialRadius)
        assertEquals(3, grouped.size)
        assertTrue(grouped.any { it.id == "j1" })
        assertFalse(grouped.any { it.id == "j2" })
        assertFalse(grouped.any { it.id == "j3" })
        assertTrue(grouped.any { it.id == "s1" })
        assertTrue(grouped.any { it.id == "j4" })
        assertFalse(grouped.any { it.id == "c1" })

        // Recompute when delivery address moves close to San Jose -> San Jose wins for chain_j
        val sanJoseAddressLat = 9.7710
        val sanJoseAddressLng = 118.7495
        val regrouped = com.example.util.BranchGroupingUtil.groupByChainNearestBranch(
            shops = allShops,
            customerLat = sanJoseAddressLat,
            customerLng = sanJoseAddressLng,
            defaultRadiusKm = 5.0,
            isServiceOnForType = { true }
        )
        assertEquals("j2", regrouped.firstOrNull { it.chainId == "chain_j" }?.id)

        // City service off excludes food shops
        val groupedFoodOff = com.example.util.BranchGroupingUtil.groupByChainNearestBranch(
            shops = allShops,
            customerLat = customerLat,
            customerLng = customerLng,
            defaultRadiusKm = 5.0,
            isServiceOnForType = { svc -> svc != "food" }
        )
        assertTrue(groupedFoodOff.isEmpty())

        // Equal distance tie broken by higher rating
        val tieA = makeBranch("t1", "Cafe", chainId = "chain_tie", branchName = "A", lat = 9.7400, lng = 118.7360, rating = 4.3)
        val tieB = makeBranch("t2", "Cafe", chainId = "chain_tie", branchName = "B", lat = 9.7400, lng = 118.7360, rating = 4.9)
        val tieWinner = com.example.util.BranchGroupingUtil.groupByChainNearestBranch(
            shops = listOf(tieA, tieB),
            customerLat = customerLat,
            customerLng = customerLng,
            defaultRadiusKm = 5.0,
            isServiceOnForType = { true }
        )
        assertEquals(1, tieWinner.size)
        assertEquals("t2", tieWinner.first().id)

        // Deliverable chain branches sorted nearest first for Choose a branch sheet
        val chainBranches = com.example.util.BranchGroupingUtil.getDeliverableBranchesForChain(
            allShops = allShops,
            chainId = "chain_j",
            customerLat = customerLat,
            customerLng = customerLng,
            defaultRadiusKm = 5.0,
            isServiceOnForType = { true }
        )
        assertEquals(listOf("j1", "j2"), chainBranches.map { it.restaurant.id })
    }

    @Test
    fun `placing order stores branch snapshots and branch switch clears cart`() = runTest {
        val firestoreService = com.example.data.repository.FirestoreService()
        val orderRepo = OrderRepository(firestoreService)
        val cartRepo = CartRepository()

        val branch = com.example.data.model.Restaurant(
            id = "branch_rizal",
            name = "Jollibee",
            category = "Fast Food",
            type = "food",
            rating = 4.8,
            reviewCount = 200,
            etaMinutes = 25,
            deliveryFee = 39.0,
            address = "Rizal Ave, Puerto Princesa",
            phone = "+639171234567",
            lat = 9.7415,
            lng = 118.7380,
            chainId = "chain_jollibee",
            chainName = "Jollibee",
            isBranch = true,
            branchName = "Palawan Rizal",
            branchCode = "PR01",
            branchSetupDone = true
        )
        val burger = MenuItem(
            id = "item_yum",
            restaurantId = branch.id,
            name = "Yumburger",
            description = "Classic",
            price = 55.0,
            category = "Burgers",
            emoji = "🍔"
        )
        cartRepo.addItem(burger, branch, 2)
        assertEquals(1, cartRepo.cartItems.value.size)
        assertEquals("Jollibee - Palawan Rizal", cartRepo.cartItems.value.first().restaurantName)

        val deliveryAddress = Address(
            id = "addr_1",
            title = "Home",
            fullAddress = "123 Main St",
            lat = 9.7392,
            lng = 118.7353
        )
        val res = orderRepo.placeFoodOrConvenienceOrder(
            customerId = "cust_1",
            customerName = "Juan",
            customerPhone = "09170000001",
            restaurant = branch,
            items = cartRepo.cartItems.value,
            deliveryAddress = deliveryAddress,
            deliveryLat = deliveryAddress.lat,
            deliveryLng = deliveryAddress.lng,
            deliveryFee = 39.0,
            serviceFee = 10.0,
            discount = 0.0,
            subtotal = 110.0,
            total = 159.0,
            cityId = "ppc",
            deliveryNotes = ""
        )
        assertTrue(res.isSuccess)
        val createdOrder = orderRepo.activeOrder.value
        assertNotNull(createdOrder)
        assertEquals("Jollibee", createdOrder?.merchantName)
        assertEquals("Palawan Rizal", createdOrder?.branchName)
        assertEquals("PR01", createdOrder?.branchCode)
        assertEquals("chain_jollibee", createdOrder?.chainId)
        assertEquals("Jollibee - Palawan Rizal", createdOrder?.displayMerchantName)

        // Switching branch clears cart
        cartRepo.clearCart()
        assertTrue(cartRepo.cartItems.value.isEmpty())
        assertNull(cartRepo.currentRestaurant.value)
    }

    private fun taggedBestSeller(id: String, name: String): com.example.data.model.MerchantItem {
        return com.example.data.model.MerchantItem(
            id = id,
            name = name,
            price = 35.0,
            hasSets = false,
            isAvailable = true,
            inStock = true,
            tags = listOf("Best Seller")
        )
    }

    @Test
    fun `mainCategories filters active sorts by sortOrder keeps Others last and matches aliases normalized`() {
        val config = com.example.data.model.MainCategoriesConfig(
            food = listOf(
                com.example.data.model.MainCategory(
                    id = "others",
                    name = "Others",
                    type = "food",
                    sortOrder = 0, // even with sortOrder 0, Others MUST be last
                    active = true,
                    aliases = listOf("other")
                ),
                com.example.data.model.MainCategory(
                    id = "inactive-cat",
                    name = "Inactive Category",
                    type = "food",
                    sortOrder = 1,
                    active = false
                ),
                com.example.data.model.MainCategory(
                    id = "pizza",
                    name = "Pizza",
                    type = "food",
                    sortOrder = 5,
                    active = true,
                    aliases = listOf("woodfire", "pizzas")
                ),
                com.example.data.model.MainCategory(
                    id = "milk-tea",
                    name = "Milk Tea",
                    type = "food",
                    sortOrder = 2,
                    active = true,
                    imageUrl = "https://example.com/milktea.png",
                    aliases = listOf("boba", "tea house")
                )
            ),
            convenience = listOf(
                com.example.data.model.MainCategory(
                    id = "pharmacy",
                    name = "Pharmacies",
                    type = "convenience",
                    sortOrder = 1,
                    active = true,
                    aliases = listOf("drugstore")
                ),
                com.example.data.model.MainCategory(
                    id = "others",
                    name = "Others",
                    type = "convenience",
                    sortOrder = 2,
                    active = true
                )
            )
        )

        val activeFood = com.example.util.MainCategoriesUtil.activeForType(config, "food")
        assertEquals(listOf("Milk Tea", "Pizza", "Others"), activeFood.map { it.name })

        // Normalized matching against id, name, aliases
        val bobaShop = com.example.data.model.Restaurant(
            id = "s_boba",
            name = "Happy Cup",
            category = "Food",
            type = "food",
            rating = 4.9,
            reviewCount = 10,
            etaMinutes = 15,
            deliveryFee = 29.0,
            address = "Rizal",
            phone = "123",
            cuisineTags = listOf("  TEA   HOUSE  ")
        )
        assertTrue(com.example.util.MainCategoriesUtil.shopMatchesCategory(bobaShop, activeFood[0], config))
        assertFalse(com.example.util.MainCategoriesUtil.shopMatchesCategory(bobaShop, activeFood[1], config))

        // Unmatched tag resolves to Others
        val mysteryShop = bobaShop.copy(id = "s_mystery", cuisineTags = listOf("Exotic Fusion"))
        assertTrue(com.example.util.MainCategoriesUtil.shopMatchesCategory(mysteryShop, activeFood.last(), config))
        assertFalse(com.example.util.MainCategoriesUtil.shopMatchesCategory(mysteryShop, activeFood[0], config))

        // Convenience vs Food isolation
        val convPharmacy = bobaShop.copy(id = "c_pharm", type = "convenience", isConvenience = true, cuisineTags = listOf("Drugstore"))
        val activeConv = com.example.util.MainCategoriesUtil.activeForType(config, "convenience")
        assertTrue(com.example.util.MainCategoriesUtil.shopMatchesCategory(convPharmacy, activeConv[0], config))
        assertFalse(com.example.util.MainCategoriesUtil.shopMatchesCategory(convPharmacy, activeFood[2], config))

        // Item category matching
        assertTrue(com.example.util.MainCategoriesUtil.itemMatchesCategory("  WOODFIRE ", activeFood[1], activeFood))
        assertTrue(com.example.util.MainCategoriesUtil.itemMatchesCategory("Unknown Dish", activeFood[2], activeFood))

        // JSON cache serialization roundtrip
        val json = com.example.util.MainCategoriesUtil.configToJson(config)
        val restored = com.example.util.MainCategoriesUtil.configFromJson(json)
        assertNotNull(restored)
        assertEquals(config.food.size, restored?.food?.size)
        assertEquals(config.convenience.size, restored?.convenience?.size)
    }

    @Test
    fun `getEligibleGroupedShops lists both food and convenience shops even with minimal fields or empty address`() {
        val city = com.example.data.model.City(
            id = "puerto-princesa",
            name = "Puerto Princesa",
            status = "active",
            lat = 9.7392,
            lng = 118.7353,
            radiusKm = 25.0,
            services = mapOf("food" to true, "convenience" to true)
        )
        val platform = com.example.data.model.PlatformSettings(
            services = mapOf("food" to true, "convenience" to true)
        )

        val foodShop = com.example.data.model.Restaurant(
            id = "shop_food_1",
            name = "Kainan Express",
            category = "Food",
            type = "food",
            rating = 4.8,
            reviewCount = 0,
            etaMinutes = 20,
            deliveryFee = 39.0,
            address = "Rizal",
            phone = "",
            cityId = "puerto-princesa",
            status = "active",
            isConvenience = false,
            cuisineTags = emptyList(), // no tags required
            logoUrl = "",              // no logo required
            lat = 9.7400,
            lng = 118.7360,
            chainId = "shared_owner_chain"
        )
        val convShop = com.example.data.model.Restaurant(
            id = "shop_conv_1",
            name = "24h Minimart",
            category = "Convenience",
            type = "convenience",
            rating = 4.8,
            reviewCount = 0,
            etaMinutes = 20,
            deliveryFee = 39.0,
            address = "Rizal",
            phone = "",
            cityId = "puerto-princesa",
            status = "active",
            isConvenience = true,
            cuisineTags = emptyList(),
            logoUrl = "",
            lat = 9.7405,
            lng = 118.7365,
            chainId = "shared_owner_chain" // same chainId must NOT collapse food & convenience together!
        )
        val inactiveShop = foodShop.copy(id = "shop_inactive", status = "suspended")
        val outOfRadiusShop = foodShop.copy(id = "shop_far", lat = 10.5000, lng = 119.5000)

        // Empty location (0.0, 0.0) falls back to city center and lists both food and convenience
        val result = com.example.util.BranchGroupingUtil.getEligibleGroupedShops(
            allShops = listOf(foodShop, convShop, inactiveShop, outOfRadiusShop),
            cities = listOf(city),
            platformSettings = platform,
            defaultRadiusKm = 5.0,
            customerLat = 0.0,
            customerLng = 0.0,
            fallbackCity = city,
            logExcluded = false
        )

        val ids = result.map { it.restaurant.id }.toSet()
        assertEquals(2, result.size)
        assertTrue(ids.contains("shop_food_1"))
        assertTrue(ids.contains("shop_conv_1"))
        assertFalse(ids.contains("shop_inactive"))
        assertFalse(ids.contains("shop_far"))
    }

    @Test
    fun `businessHours honors closedUntil and holidays while keeping scheduled checks accurate`() {
        val zone = java.time.ZoneId.of("Asia/Manila")
        // Fixed date: 2026-12-25 10:00 AM Manila (Friday)
        val holidayMorning = java.time.ZonedDateTime.of(2026, 12, 25, 10, 0, 0, 0, zone).toInstant().toEpochMilli()
        val regularMorning = java.time.ZonedDateTime.of(2026, 12, 24, 10, 0, 0, 0, zone).toInstant().toEpochMilli()

        val hoursMap = mapOf(
            "thu" to mapOf("closed" to false, "open" to "08:00", "close" to "21:00"),
            "fri" to mapOf("closed" to false, "open" to "08:00", "close" to "21:00")
        )
        val shopWithHoliday = com.example.data.model.Restaurant(
            id = "h1",
            name = "Holiday Cafe",
            category = "Food",
            type = "food",
            rating = 4.8,
            reviewCount = 10,
            etaMinutes = 20,
            deliveryFee = 39.0,
            address = "Rizal",
            phone = "123",
            isOpen = true,
            autoSchedule = true,
            hours = hoursMap,
            holidays = listOf(com.example.data.model.HolidayEntry(date = "2026-12-25", name = "Christmas Day", closed = true))
        )

        // Open on Dec 24 at 10:00 AM
        assertTrue(com.example.util.BusinessHoursUtil.effectiveOpen(shopWithHoliday, "Asia/Manila", regularMorning))
        // Closed on Dec 25 holiday even though Fri 10 AM is inside regular hours
        assertFalse(com.example.util.BusinessHoursUtil.effectiveOpen(shopWithHoliday, "Asia/Manila", holidayMorning))
        assertFalse(com.example.util.BusinessHoursUtil.isScheduleTimeValid(shopWithHoliday, holidayMorning, "Asia/Manila").first)

        // Platform-wide holiday range closes shop too
        val platformHolidays = listOf(
            com.example.data.model.HolidayEntry(dateFrom = "2026-12-24", dateTo = "2026-12-26", name = "Holiday Break", closed = true)
        )
        assertFalse(com.example.util.BusinessHoursUtil.effectiveOpen(shopWithHoliday, "Asia/Manila", regularMorning, platformHolidays))

        // Manual closedUntil in future overrides isOpen == true
        val closedUntilShop = shopWithHoliday.copy(
            holidays = emptyList(),
            closedUntil = regularMorning + 2 * 60 * 60 * 1000L
        )
        assertFalse(com.example.util.BusinessHoursUtil.effectiveOpen(closedUntilShop, "Asia/Manila", regularMorning))
    }
}
