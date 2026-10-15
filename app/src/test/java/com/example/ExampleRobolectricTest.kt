package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.datasource.MockData
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
}
