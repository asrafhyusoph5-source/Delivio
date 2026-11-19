package com.example

import com.example.data.model.Address
import com.example.data.model.CartItem
import com.example.data.model.LocationPoint
import com.example.data.model.MenuItem
import com.example.data.model.PabiliDetails
import com.example.data.model.PabiliItem
import com.example.data.model.ParcelDetails
import com.example.data.model.Restaurant
import com.example.data.repository.FirestoreService
import com.example.data.repository.OrderRepository
import com.example.util.FirestoreErrorBus
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
@Config(sdk = [36])
class OrderContactAndQueryTest {

    @Test
    fun `firestore error bus shows customer friendly orders refresh message without raw error code`() = runTest {
        FirestoreErrorBus.showOrdersRefreshBanner()
        val msg1 = FirestoreErrorBus.nonFatalBanner.first()
        assertEquals("We could not refresh your orders. Pull down to retry.", msg1)
        assertFalse(msg1.contains("PERMISSION_DENIED"))

        FirestoreErrorBus.showNonFatalBanner("orders", "PERMISSION_DENIED")
        val msg2 = FirestoreErrorBus.nonFatalBanner.first()
        assertEquals("We could not refresh your orders. Pull down to retry.", msg2)
        assertFalse(msg2.contains("PERMISSION_DENIED"))

        // Non-orders sections remain unchanged
        FirestoreErrorBus.showNonFatalBanner("banners", "PERMISSION_DENIED")
        val msg3 = FirestoreErrorBus.nonFatalBanner.first()
        assertEquals("Could not load banners: PERMISSION_DENIED", msg3)
    }

    @Test
    fun `parcel order populates sender pickup and recipient dropoff contact fields while preserving existing fields`() = runTest {
        val fs = FirestoreService()
        val repo = OrderRepository(fs)
        val details = ParcelDetails(
            senderName = "Alice Sender",
            senderPhone = "+639171112222",
            senderAddress = "Rizal Ave, Puerto Princesa",
            pickupLat = 9.7392f,
            pickupLng = 118.7353f,
            recipientName = "Bob Recipient",
            recipientPhone = "+639183334444",
            recipientAddress = "Malvar St, Puerto Princesa",
            dropoffLat = 9.7420f,
            dropoffLng = 118.7400f,
            packageSize = "Small / Pouch",
            weightKg = 1.5,
            itemDescription = "Documents"
        )

        val result = repo.bookParcelOrder(
            customerId = "cust_1",
            customerName = "Alice Sender",
            customerPhone = "+639171112222",
            parcelDetails = details,
            deliveryFee = 65.0,
            serviceFee = 15.0
        )

        assertTrue(result.isSuccess)
        val orderId = result.getOrThrow()
        val savedDoc = fs.inMemoryOrders[orderId]
        assertNotNull(savedDoc)

        val pickupMap = fs.buildLocationPointMap(savedDoc!!.pickup, includePhone = true)
        assertEquals("Alice Sender", pickupMap["name"])
        assertEquals("+639171112222", pickupMap["phone"])
        assertEquals("Rizal Ave, Puerto Princesa", pickupMap["address"])
        assertEquals(9.7392f.toDouble(), pickupMap["lat"] as Double, 0.0001)
        assertEquals(118.7353f.toDouble(), pickupMap["lng"] as Double, 0.0001)
        assertEquals("", pickupMap["note"])
        // Existing fields kept
        assertEquals("Sender: Alice Sender", pickupMap["label"])
        assertEquals("Alice Sender", pickupMap["contactName"])
        assertEquals("+639171112222", pickupMap["contactPhone"])

        val dropoffMap = fs.buildLocationPointMap(savedDoc.dropoff!!, includePhone = true)
        assertEquals("Bob Recipient", dropoffMap["name"])
        assertEquals("+639183334444", dropoffMap["phone"])
        assertEquals("Malvar St, Puerto Princesa", dropoffMap["address"])
        assertEquals(9.7420f.toDouble(), dropoffMap["lat"] as Double, 0.0001)
        assertEquals(118.7400f.toDouble(), dropoffMap["lng"] as Double, 0.0001)
        assertEquals("", dropoffMap["note"])
        // Existing fields kept
        assertEquals("Recipient: Bob Recipient", dropoffMap["label"])
        assertEquals("Bob Recipient", dropoffMap["contactName"])
        assertEquals("+639183334444", dropoffMap["contactPhone"])
    }

    @Test
    fun `pabili order populates vendor pickup without phone and recipient dropoff with phone`() = runTest {
        val fs = FirestoreService()
        val repo = OrderRepository(fs)
        val details = PabiliDetails(
            storeName = "Mercury Drug Rizal",
            storeAddress = "Rizal Ave, Puerto Princesa",
            storeLat = 9.7400f,
            storeLng = 118.7360f,
            items = listOf(PabiliItem("Paracetamol", 2, 50.0)),
            recipientName = "Cara Buyer",
            recipientPhone = "+639195556666",
            deliveryAddress = "San Pedro, Puerto Princesa",
            deliveryLat = 9.7500f,
            deliveryLng = 118.7450f,
            riderNotes = "Ask for receipt"
        )

        val result = repo.bookPabiliOrder(
            customerId = "cust_2",
            customerName = "Cara Buyer",
            customerPhone = "+639195556666",
            pabiliDetails = details,
            itemTotal = 100.0,
            deliveryFee = 60.0,
            serviceFee = 20.0
        )

        assertTrue(result.isSuccess)
        val savedDoc = fs.inMemoryOrders[result.getOrThrow()]!!

        val pickupMap = fs.buildLocationPointMap(savedDoc.pickup, includePhone = false)
        assertEquals("Mercury Drug Rizal", pickupMap["name"])
        assertFalse(pickupMap.containsKey("phone"))
        assertEquals("Rizal Ave, Puerto Princesa", pickupMap["address"])
        assertEquals(9.7400f.toDouble(), pickupMap["lat"] as Double, 0.0001)
        assertEquals(118.7360f.toDouble(), pickupMap["lng"] as Double, 0.0001)
        assertEquals("Ask for receipt", pickupMap["note"])
        // Existing fields kept
        assertEquals("Vendor", pickupMap["label"])

        val dropoffMap = fs.buildLocationPointMap(savedDoc.dropoff!!, includePhone = true)
        assertEquals("Cara Buyer", dropoffMap["name"])
        assertEquals("+639195556666", dropoffMap["phone"])
        assertEquals("San Pedro, Puerto Princesa", dropoffMap["address"])
        assertEquals(9.7500f.toDouble(), dropoffMap["lat"] as Double, 0.0001)
        assertEquals(118.7450f.toDouble(), dropoffMap["lng"] as Double, 0.0001)
        assertEquals("Ask for receipt", dropoffMap["note"])
        assertEquals("Recipient: Cara Buyer", dropoffMap["label"])
        assertEquals("Cara Buyer", dropoffMap["contactName"])
        assertEquals("+639195556666", dropoffMap["contactPhone"])
    }

    @Test
    fun `food and convenience orders populate dropoff with customer contact fields and note`() = runTest {
        val fs = FirestoreService()
        val repo = OrderRepository(fs)
        val restaurant = Restaurant(
            id = "mart_1",
            name = "QuickMart",
            category = "Convenience",
            type = "convenience",
            isConvenience = true,
            etaMinutes = 20,
            deliveryFee = 45.0,
            address = "101 Store St",
            phone = "+639170001111",
            lat = 9.741,
            lng = 118.739
        )
        val item = MenuItem(
            id = "item_1",
            restaurantId = "mart_1",
            name = "Bottled Water",
            description = "500ml",
            price = 25.0,
            category = "Drinks",
            emoji = "💧"
        )
        val address = Address(
            id = "addr_1",
            title = "Home",
            fullAddress = "22 Palm St, Puerto Princesa",
            details = "Blue gate",
            lat = 9.748,
            lng = 118.744
        )

        val result = repo.placeFoodOrConvenienceOrder(
            customerId = "cust_3",
            customerName = "Dan Customer",
            customerPhone = "+639178889999",
            restaurant = restaurant,
            items = listOf(CartItem(menuItem = item, restaurantId = "mart_1", restaurantName = "QuickMart", quantity = 1)),
            deliveryAddress = address,
            deliveryLat = address.lat,
            deliveryLng = address.lng,
            deliveryFee = 45.0,
            serviceFee = 12.0,
            discount = 0.0,
            subtotal = 25.0,
            total = 82.0,
            cityId = "puerto-princesa",
            deliveryNotes = "Call on arrival"
        )

        assertTrue(result.isSuccess)
        val savedDoc = fs.inMemoryOrders[result.getOrThrow()]!!
        val dropoffMap = fs.buildLocationPointMap(savedDoc.dropoff!!, includePhone = true)

        assertEquals("Dan Customer", dropoffMap["name"])
        assertEquals("+639178889999", dropoffMap["phone"])
        assertEquals("22 Palm St, Puerto Princesa", dropoffMap["address"])
        assertEquals(9.748, dropoffMap["lat"] as Double, 0.0001)
        assertEquals(118.744, dropoffMap["lng"] as Double, 0.0001)
        assertEquals("Blue gate • Call on arrival", dropoffMap["note"])
        // Existing fields kept
        assertEquals("Home", dropoffMap["label"])
        assertEquals("Dan Customer", dropoffMap["contactName"])
        assertEquals("+639178889999", dropoffMap["contactPhone"])
        assertEquals("Blue gate", dropoffMap["details"])
    }

    @Test
    fun `taxi order populates passenger name and phone on both pickup and dropoff`() {
        val fs = FirestoreService()
        val repo = OrderRepository(fs)
        val pickup = LocationPoint(
            label = "Pick Up",
            address = "SM City Puerto Princesa",
            lat = 9.7440,
            lng = 118.7420
        )
        val dropoff = LocationPoint(
            label = "Drop off",
            address = "Robinsons Place Palawan",
            lat = 9.7660,
            lng = 118.7460
        )

        val orderDoc = repo.buildTaxiOrderDocument(
            customerId = "cust_taxi",
            customerName = "Elena Passenger",
            customerPhone = "+639172223333",
            vehicleType = "tricycle",
            cityId = "puerto-princesa",
            pickup = pickup,
            dropoff = dropoff,
            stops = emptyList(),
            distanceKm = 3.2,
            rideFare = 60.0,
            serviceFee = 8.0,
            total = 68.0,
            paymentMethod = "cod",
            note = "Waiting near main entrance"
        )

        assertEquals("Elena Passenger", orderDoc.pickup.name)
        assertEquals("+639172223333", orderDoc.pickup.phone)
        assertEquals("SM City Puerto Princesa", orderDoc.pickup.address)
        assertEquals(9.7440, orderDoc.pickup.lat, 0.0001)
        assertEquals(118.7420, orderDoc.pickup.lng, 0.0001)
        assertEquals("Waiting near main entrance", orderDoc.pickup.note)
        assertEquals("Elena Passenger", orderDoc.pickup.contactName)
        assertEquals("+639172223333", orderDoc.pickup.contactPhone)

        assertEquals("Elena Passenger", orderDoc.dropoff!!.name)
        assertEquals("+639172223333", orderDoc.dropoff!!.phone)
        assertEquals("Robinsons Place Palawan", orderDoc.dropoff!!.address)
        assertEquals(9.7660, orderDoc.dropoff!!.lat, 0.0001)
        assertEquals(118.7460, orderDoc.dropoff!!.lng, 0.0001)
        assertEquals("Waiting near main entrance", orderDoc.dropoff!!.note)
        assertEquals("Elena Passenger", orderDoc.dropoff!!.contactName)
        assertEquals("+639172223333", orderDoc.dropoff!!.contactPhone)
    }
}
