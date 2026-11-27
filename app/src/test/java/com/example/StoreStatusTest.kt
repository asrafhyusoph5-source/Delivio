package com.example

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.Address
import com.example.data.model.HolidayEntry
import com.example.data.model.MenuItem
import com.example.data.model.Merchant
import com.example.data.model.Restaurant
import com.example.data.model.StoreManualOverride
import com.example.data.repository.CartRepository
import com.example.data.repository.FirestoreService
import com.example.data.repository.OrderRepository
import com.example.data.repository.PreferenceRepository
import com.example.data.repository.RestaurantRepository
import com.example.ui.components.ChooseBranchBottomSheet
import com.example.ui.components.RestaurantCard
import com.example.ui.screens.cart.CartScreen
import com.example.ui.theme.DelivioTheme
import com.example.util.BranchWithDistance
import com.example.util.BusinessHoursUtil
import com.example.viewmodel.CartViewModel
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class StoreStatusTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val manilaZone: ZoneId = ZoneId.of("Asia/Manila")

    private fun manilaMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, manilaZone)
            .toInstant()
            .toEpochMilli()
    }

    private fun sampleMerchant(
        id: String = "m1",
        status: String = "active",
        isOpen: Boolean = true,
        adminLock: Boolean = false,
        manualOverride: StoreManualOverride? = null,
        allowScheduledOrders: Boolean = true,
        autoSchedule: Boolean = false,
        hours: Map<String, Any>? = null,
        holidays: List<HolidayEntry> = emptyList()
    ): Merchant = Merchant(
        id = id,
        name = "Palawan Kitchen",
        type = "food",
        status = status,
        isOpen = isOpen,
        adminLock = adminLock,
        manualOverride = manualOverride,
        allowScheduledOrders = allowScheduledOrders,
        autoSchedule = autoSchedule,
        hours = hours,
        holidays = holidays
    )

    private fun sampleRestaurant(
        id: String = "m1",
        status: String = "active",
        isOpen: Boolean = true,
        adminLock: Boolean = false,
        manualOverride: StoreManualOverride? = null,
        allowScheduledOrders: Boolean = true,
        autoSchedule: Boolean = false,
        hours: Map<String, Any>? = null,
        holidays: List<HolidayEntry> = emptyList()
    ): Restaurant = Restaurant(
        id = id,
        name = "Palawan Kitchen",
        category = "Food",
        type = "food",
        rating = 4.7,
        reviewCount = 24,
        etaMinutes = 25,
        deliveryFee = 39.0,
        address = "Rizal Ave",
        phone = "09170000000",
        status = status,
        isOpen = isOpen,
        adminLock = adminLock,
        manualOverride = manualOverride,
        allowScheduledOrders = allowScheduledOrders,
        autoSchedule = autoSchedule,
        hours = hours,
        holidays = holidays
    )

    @Test
    fun `storeStatus enforces strict priority rules 1 through 5`() {
        val monday10am = manilaMillis(2026, 11, 23, 10, 0) // Monday 10:00 AM Asia/Manila
        val monday10pm = manilaMillis(2026, 11, 23, 22, 0) // Monday 10:00 PM Asia/Manila
        val futureUntil = monday10am + 2 * 60 * 60 * 1000L

        val hoursMap = mapOf(
            "mon" to mapOf("closed" to false, "open" to "08:00", "close" to "21:00"),
            "tue" to mapOf("closed" to false, "open" to "08:00", "close" to "21:00")
        )

        // 1. Merchant suspended -> closed, beats adminLock/manualOverride/isOpen
        val suspended = sampleMerchant(
            status = "suspended",
            isOpen = true,
            adminLock = false,
            manualOverride = StoreManualOverride(state = "open", until = futureUntil)
        )
        val suspendedStatus = BusinessHoursUtil.storeStatus(suspended, monday10am)
        assertFalse(suspendedStatus.open)
        assertEquals("Store is suspended", suspendedStatus.reason)

        // 2. adminLock true -> closed ("Closed by Delivio"), beats manualOverride open & hours & isOpen
        val locked = sampleMerchant(
            status = "active",
            isOpen = true,
            adminLock = true,
            manualOverride = StoreManualOverride(state = "open", until = futureUntil),
            autoSchedule = true,
            hours = hoursMap
        )
        val lockedStatus = BusinessHoursUtil.storeStatus(locked, monday10am)
        assertFalse(lockedStatus.open)
        assertEquals("Closed by Delivio", lockedStatus.reason)

        // 3. manualOverride open while now < until overrides autoSchedule outside hours & isOpen=false
        val forcedOpen = sampleMerchant(
            status = "active",
            isOpen = false,
            autoSchedule = true,
            hours = hoursMap,
            manualOverride = StoreManualOverride(state = "open", until = monday10pm + 3600_000L)
        )
        val forcedOpenStatus = BusinessHoursUtil.storeStatus(forcedOpen, monday10pm)
        assertTrue(forcedOpenStatus.open)
        assertEquals(monday10pm + 3600_000L, forcedOpenStatus.nextChange)

        // 3b. manualOverride closed while now < until overrides autoSchedule inside hours & isOpen=true
        val forcedClosed = sampleMerchant(
            status = "active",
            isOpen = true,
            autoSchedule = true,
            hours = hoursMap,
            manualOverride = StoreManualOverride(state = "closed", until = futureUntil)
        )
        val forcedClosedStatus = BusinessHoursUtil.storeStatus(forcedClosed, monday10am)
        assertFalse(forcedClosedStatus.open)
        assertEquals(futureUntil, forcedClosedStatus.nextChange)

        // 3c. Expired manualOverride falls through to autoSchedule / manual switch
        val expiredOverride = sampleMerchant(
            status = "active",
            isOpen = true,
            autoSchedule = true,
            hours = hoursMap,
            manualOverride = StoreManualOverride(state = "closed", until = monday10am - 1000L)
        )
        assertTrue(BusinessHoursUtil.storeStatus(expiredOverride, monday10am).open)

        // 4. Auto-schedule on: open inside today's hours, closed outside with next opening time
        val scheduled = sampleMerchant(autoSchedule = true, isOpen = false, hours = hoursMap)
        val openInside = BusinessHoursUtil.storeStatus(scheduled, monday10am)
        assertTrue(openInside.open)
        assertEquals(manilaMillis(2026, 11, 23, 21, 0), openInside.nextChange)

        val closedAfter = BusinessHoursUtil.storeStatus(scheduled, monday10pm)
        assertFalse(closedAfter.open)
        assertEquals(manilaMillis(2026, 11, 24, 8, 0), closedAfter.nextChange)

        // 5. Auto-schedule off: manual switch decides
        val manualOpen = sampleMerchant(autoSchedule = false, isOpen = true, hours = hoursMap)
        val manualClosed = sampleMerchant(autoSchedule = false, isOpen = false, hours = hoursMap)
        assertTrue(BusinessHoursUtil.storeStatus(manualOpen, monday10pm).open)
        assertFalse(BusinessHoursUtil.storeStatus(manualClosed, monday10am).open)
    }

    @Test
    fun `storeStatus supports overnight windows in Asia Manila`() {
        val hoursMap = mapOf(
            "fri" to mapOf("closed" to false, "open" to "22:00", "close" to "03:00"),
            "sat" to mapOf("closed" to false, "open" to "22:00", "close" to "03:00")
        )
        val overnightShop = sampleMerchant(autoSchedule = true, isOpen = false, hours = hoursMap)

        // Friday 23:30 Asia/Manila -> OPEN until Saturday 03:00
        val friNight = manilaMillis(2026, 11, 27, 23, 30)
        val statusFri = BusinessHoursUtil.storeStatus(overnightShop, friNight)
        assertTrue(statusFri.open)
        assertEquals(manilaMillis(2026, 11, 28, 3, 0), statusFri.nextChange)

        // Saturday 01:30 Asia/Manila -> still OPEN from Friday overnight window until 03:00
        val satEarlyAm = manilaMillis(2026, 11, 28, 1, 30)
        val statusSatEarly = BusinessHoursUtil.storeStatus(overnightShop, satEarlyAm)
        assertTrue(statusSatEarly.open)
        assertEquals(manilaMillis(2026, 11, 28, 3, 0), statusSatEarly.nextChange)

        // Saturday 03:15 Asia/Manila -> CLOSED, next opening is Saturday 22:00
        val satClosed = manilaMillis(2026, 11, 28, 3, 15)
        val statusSatClosed = BusinessHoursUtil.storeStatus(overnightShop, satClosed)
        assertFalse(statusSatClosed.open)
        assertEquals(manilaMillis(2026, 11, 28, 22, 0), statusSatClosed.nextChange)
    }

    @Test
    fun `formatClosedOrderForLaterLabel respects allowScheduledOrders and adminLock`() {
        val monMorningBeforeOpen = manilaMillis(2026, 11, 23, 6, 30)
        val hoursMap = mapOf(
            "mon" to mapOf("closed" to false, "open" to "08:00", "close" to "21:00"),
            "tue" to mapOf("closed" to false, "open" to "08:00", "close" to "21:00")
        )

        val closedAllowLater = sampleRestaurant(autoSchedule = true, hours = hoursMap, allowScheduledOrders = true)
        val status = BusinessHoursUtil.storeStatus(closedAllowLater, monMorningBeforeOpen)
        assertFalse(status.open)
        assertEquals(
            "Order for later - Opens 8:00 AM",
            BusinessHoursUtil.formatClosedOrderForLaterLabel(closedAllowLater, status, monMorningBeforeOpen)
        )

        val closedAdminLock = closedAllowLater.copy(adminLock = true)
        val lockedStatus = BusinessHoursUtil.storeStatus(closedAdminLock, monMorningBeforeOpen)
        assertFalse(BusinessHoursUtil.allowsOrderForLater(closedAdminLock))
        assertNull(BusinessHoursUtil.formatClosedOrderForLaterLabel(closedAdminLock, lockedStatus, monMorningBeforeOpen))

        val closedDisallowLater = closedAllowLater.copy(allowScheduledOrders = false)
        assertFalse(BusinessHoursUtil.allowsOrderForLater(closedDisallowLater))
        assertNull(BusinessHoursUtil.formatClosedOrderForLaterLabel(closedDisallowLater, status, monMorningBeforeOpen))
    }

    @Test
    fun `closed shop rejects instant checkout and allows scheduled checkout when permitted`() = runTest {
        val fs = FirestoreService()
        val cartRepo = CartRepository()
        val orderRepo = OrderRepository(fs)
        val cartVm = CartViewModel(cartRepo, orderRepo)

        val openShop = sampleRestaurant(id = "shop_live", isOpen = true, autoSchedule = false)
        val item = MenuItem(
            id = "item1",
            restaurantId = "shop_live",
            name = "Chicken Inasal",
            description = "Grilled chicken",
            price = 150.0,
            category = "Mains",
            emoji = "🍗"
        )

        // Open shop allows adding normally
        assertTrue(cartRepo.addItem(item, openShop, quantity = 1))
        assertEquals(1, cartRepo.cartItems.value.size)

        // Admin/Merchant closes shop live -> CartRepository & CartViewModel immediately reflect Closed
        val closedShop = openShop.copy(isOpen = false)
        cartVm.syncLiveRestaurant(closedShop)
        assertEquals(false, cartVm.currentRestaurant.value?.isOpen)

        val addr = Address(id = "addr_1", title = "Home", fullAddress = "Rizal Ave", lat = 9.74, lng = 118.73)
        // Instant order on closed shop MUST fail
        val instantResult = cartVm.placeOrder(
            address = addr,
            customerId = "cust_1",
            fulfillmentOverride = "delivery"
        )
        assertTrue(instantResult.isFailure)
        assertTrue(instantResult.exceptionOrNull()?.message?.contains("closed", ignoreCase = true) == true)

        // Scheduled order (>45 mins ahead) on closed shop that allows scheduling MUST succeed
        val futureSched = System.currentTimeMillis() + 2 * 60 * 60 * 1000L
        cartVm.setScheduledFor(futureSched)
        val scheduledResult = cartVm.placeOrder(
            address = addr,
            customerId = "cust_1",
            fulfillmentOverride = "delivery"
        )
        assertTrue("Expected scheduled order to succeed on closed shop", scheduledResult.isSuccess)

        // If adminLock = true, even addItem with bypassHoursCheck = true & placeOrder fail
        val adminLockedShop = openShop.copy(adminLock = true)
        assertFalse(cartRepo.addItem(item, adminLockedShop, quantity = 1, bypassHoursCheck = true))
        cartRepo.addItem(item, openShop, quantity = 1)
        cartVm.syncLiveRestaurant(adminLockedShop)
        val lockedResult = cartVm.placeOrder(address = addr, customerId = "cust_1")
        assertTrue(lockedResult.isFailure)
        assertEquals("Closed by Delivio", lockedResult.exceptionOrNull()?.message)
    }

    @Test
    fun `restaurantCard and branchSheet show Closed, Order for later, and Closed by Delivio labels`() {
        val closedShop = sampleRestaurant(
            id = "closed_card_shop",
            isOpen = false,
            autoSchedule = false,
            allowScheduledOrders = true
        )
        val lockedShop = sampleRestaurant(
            id = "locked_card_shop",
            isOpen = true,
            adminLock = true
        )

        composeRule.setContent {
            DelivioTheme {
                ChooseBranchBottomSheet(
                    branches = listOf(
                        BranchWithDistance(closedShop, 1.2),
                        BranchWithDistance(lockedShop, 2.0)
                    ),
                    deliveryRates = com.example.data.model.DeliveryRates(),
                    onSelectBranch = {},
                    onDismiss = {}
                )
            }
        }
        composeRule.waitForIdle()
        assertTrue(composeRule.onAllNodesWithText("Closed").fetchSemanticsNodes().isNotEmpty())
        composeRule.onNodeWithText("Order for later").assertIsDisplayed()
        composeRule.onNodeWithText("Closed by Delivio").assertIsDisplayed()
    }

    @Test
    fun `cartScreen updates live when restaurant closes and reopens without restarting`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefRepo = PreferenceRepository(context)
        val fs = FirestoreService()
        val mainVm = MainViewModel(prefRepo, fs)
        val cartRepo = CartRepository()
        val orderRepo = OrderRepository(fs)
        val cartVm = CartViewModel(cartRepo, orderRepo)

        val shopOpen = sampleRestaurant(id = "live_cart_shop", isOpen = true, autoSchedule = false)
        val item = MenuItem(
            id = "m_item_1",
            restaurantId = "live_cart_shop",
            name = "Pork Sisig",
            description = "Sizzling",
            price = 180.0,
            category = "Food",
            emoji = "🍽️"
        )
        assertTrue(cartRepo.addItem(item, shopOpen, 1))

        composeRule.setContent {
            DelivioTheme {
                CartScreen(
                    cartViewModel = cartVm,
                    mainViewModel = mainVm,
                    onExploreShop = {}
                )
            }
        }
        composeRule.waitForIdle()
        assertEquals(0, composeRule.onAllNodes(androidx.compose.ui.test.hasTestTag("cart_store_closed_banner")).fetchSemanticsNodes().size)

        // Simulate live snapshot update closing the shop
        cartVm.syncLiveRestaurant(shopOpen.copy(isOpen = false))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("cart_store_closed_banner").assertIsDisplayed()
        assertNotNull(composeRule.onNodeWithTag("place_order_button"))

        // Simulate live snapshot update reopening the shop
        cartVm.syncLiveRestaurant(shopOpen.copy(isOpen = true))
        composeRule.waitForIdle()
        assertEquals(0, composeRule.onAllNodes(androidx.compose.ui.test.hasTestTag("cart_store_closed_banner")).fetchSemanticsNodes().size)
    }

    @Test
    fun `restaurantCard renders Closed badge and Closed by Delivio when adminLock is true`() {
        val lockedState = MutableStateFlow(
            sampleRestaurant(id = "card_locked", isOpen = true, adminLock = true)
        )
        composeRule.setContent {
            val shop by lockedState.collectAsState()
            DelivioTheme {
                RestaurantCard(restaurant = shop, onClick = {})
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Closed").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("Closed by Delivio").fetchSemanticsNodes().isNotEmpty())

        // Reopen live -> shows OPEN
        lockedState.value = sampleRestaurant(id = "card_locked", isOpen = true, adminLock = false)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("OPEN").assertIsDisplayed()
    }
}
