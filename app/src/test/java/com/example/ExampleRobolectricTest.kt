package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.datasource.MockData
import com.example.data.model.Address
import com.example.data.model.CartItem
import com.example.data.model.OrderStatus
import com.example.data.model.ParcelDetails
import com.example.data.model.PabiliDetails
import com.example.data.model.PabiliItem
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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
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

        assertTrue(OrderStatus.ON_THE_WAY.hasRider())
        assertTrue(OrderStatus.ON_THE_WAY.showMap())

        assertTrue(OrderStatus.DELIVERED.hasRider())
        assertFalse(OrderStatus.DELIVERED.showMap())

        // Test status wording
        assertEquals("Placed Order", OrderStatus.PLACED.title)
        assertEquals("Preparing", OrderStatus.PREPARING.title)
        assertEquals("Ready", OrderStatus.READY.title)
        assertEquals("Rider is on the way", OrderStatus.ON_THE_WAY.title)
        assertEquals("Delivered", OrderStatus.DELIVERED.title)
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
}
