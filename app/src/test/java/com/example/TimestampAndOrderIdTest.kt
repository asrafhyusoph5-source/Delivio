package com.example

import com.example.data.model.LocationPoint
import com.example.data.model.OrderDocument
import com.example.data.model.OrderFees
import com.example.data.model.PabiliInfo
import com.example.data.model.ParcelInfo
import com.example.data.model.TaxiStop
import com.example.data.repository.FirestoreService
import com.example.data.repository.OrderRepository
import com.example.util.FirestoreTimeUtil
import com.example.util.nowTs
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimestampAndOrderIdTest {

    @Test
    fun nowTsAndServerTs_returnFirestoreTimestampTypes() {
        val ts = nowTs()
        assertTrue(ts is Timestamp)
        assertTrue(FirestoreTimeUtil.nowTs() is Timestamp)
        assertEquals(FieldValue.serverTimestamp(), FirestoreTimeUtil.serverTs())

        val entry = FirestoreTimeUtil.statusHistoryEntry("Cancelled")
        assertEquals("Cancelled", entry["status"])
        assertTrue(entry["at"] is Timestamp)
        assertFalse(entry["at"] is Number)
        assertFalse(entry["at"] is String)
    }

    @Test
    fun buildOrderFirestoreMap_writesOnlyFirestoreTimestampsAcrossAllServices() {
        val fs = FirestoreService()
        val services = listOf("food", "convenience", "parcel", "pabili", "taxi")

        for (service in services) {
            val doc = OrderDocument(
                serviceType = service,
                customerId = "cust_1",
                customerName = "Ana Reyes",
                customerPhone = "+639171112222",
                merchantId = if (service in listOf("food", "convenience")) "merch_1" else null,
                vehicleType = if (service == "taxi") "tricycle" else if (service == "parcel") "motorcycle" else null,
                pickup = LocationPoint(label = "Pickup", address = "Rizal Ave", lat = 9.74, lng = 118.73),
                dropoff = LocationPoint(label = "Dropoff", address = "Malvar St", lat = 9.75, lng = 118.74),
                stops = if (service == "taxi") listOf(TaxiStop(number = 3, address = "Stop 1", lat = 9.745, lng = 118.735)) else emptyList(),
                parcel = if (service == "parcel") ParcelInfo() else null,
                pabili = if (service == "pabili") PabiliInfo(vendorName = "Vendor") else null,
                fees = OrderFees(deliveryFee = 50.0, serviceFee = 10.0, total = 60.0),
                scheduledFor = if (service == "food") 1_795_000_000_000L else null
            )

            val map = fs.buildOrderFirestoreMap(doc)

            assertEquals("createdAt must be serverTimestamp for $service", FieldValue.serverTimestamp(), map["createdAt"])
            assertEquals("updatedAt must be serverTimestamp for $service", FieldValue.serverTimestamp(), map["updatedAt"])

            val history = map["statusHistory"] as? List<*>
            assertNotNull(history)
            assertEquals(1, history!!.size)
            val entry = history.first() as Map<*, *>
            val atVal = entry["at"]
            assertTrue("statusHistory[0].at must be Timestamp for $service", atVal is Timestamp)
            assertFalse(atVal is Number)
            assertFalse(atVal is String)

            if (service == "food") {
                assertTrue("scheduledFor must be Timestamp", map["scheduledFor"] is Timestamp)
            }
            if (service == "taxi") {
                val stops = map["stops"] as? List<*>
                assertNotNull(stops)
                assertEquals(1, stops!!.size)
            }
        }
    }

    @Test
    fun taxiOrderDocument_doesNotPreGenerateTimestampOrderId() {
        val repo = OrderRepository(FirestoreService())
        val doc = repo.buildTaxiOrderDocument(
            customerId = "cust_1",
            customerName = "Ana Reyes",
            customerPhone = "+639171112222",
            vehicleType = "tricycle",
            cityId = "puerto-princesa",
            pickup = LocationPoint(address = "A", lat = 9.74, lng = 118.73),
            dropoff = LocationPoint(address = "B", lat = 9.75, lng = 118.74),
            stops = emptyList(),
            distanceKm = 2.0,
            rideFare = 50.0,
            serviceFee = 8.0,
            total = 58.0,
            paymentMethod = "cod",
            note = ""
        )

        assertEquals("", doc.id)
        assertFalse(Regex("""^order_\d{10,}$""").matches(doc.id))
    }
}
