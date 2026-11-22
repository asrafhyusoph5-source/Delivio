package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.printToString
import com.example.crash.CrashReportManager
import com.example.data.model.ItemChoice
import com.example.data.model.ItemSet
import com.example.data.model.ItemVariant
import com.example.data.model.ItemVariation
import com.example.data.model.ItemVariationOption
import com.example.data.model.MenuItem
import com.example.data.model.MenuSchedule
import com.example.data.model.MerchantItem
import com.example.data.model.MerchantMenu
import com.example.data.model.Restaurant
import com.example.data.repository.CartRepository
import com.example.data.repository.FirestoreService
import com.example.data.repository.OrderRepository
import com.example.ui.components.VariationSelectorFullScreen
import com.example.ui.screens.shop.CustomizeScreen
import com.example.ui.theme.DelivioTheme
import com.example.util.RelatedProductsUtil
import com.example.viewmodel.CartViewModel
import com.example.viewmodel.CustomizeSheetState
import com.example.viewmodel.OrderViewModel
import com.example.viewmodel.VariationSelectorState
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class YouMayAlsoLikeAndReviewsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val testRestaurant = Restaurant(
        id = "shop_1",
        name = "Test Shop",
        category = "Food",
        type = "food",
        etaMinutes = 25,
        deliveryFee = 39.0,
        address = "Rizal Ave, Puerto Princesa",
        phone = "+639170000000",
        cityId = "puerto-princesa"
    )

    private fun sampleMenuItem(
        id: String = "cur_item",
        name: String = "Spicy Chickenjoy with Rice 2pcs Regular",
        price: Double = 100.0,
        categoryId: String = "cat_meals",
        categoryName: String = "Meals",
        subcategoryId: String = "sub_chicken",
        subcategoryName: String = "Chicken",
        tags: List<String> = listOf("crispy"),
        sets: List<ItemSet> = emptyList(),
        variationsEnabled: Boolean = false,
        variations: List<ItemVariation> = emptyList(),
        variants: List<ItemVariant> = emptyList()
    ) = MenuItem(
        id = id,
        restaurantId = testRestaurant.id,
        name = name,
        description = "Test description",
        price = price,
        category = categoryName,
        emoji = "🍔",
        sets = sets,
        hasSets = sets.isNotEmpty(),
        variationsEnabled = variationsEnabled,
        variations = variations,
        variants = variants,
        categoryId = categoryId,
        subcategoryId = subcategoryId,
        subcategoryName = subcategoryName,
        tags = tags
    )

    private fun sampleMerchantItem(
        id: String,
        name: String,
        price: Double = 100.0,
        salePrice: Double? = null,
        saleActive: Boolean = false,
        itemSize: String = "",
        categoryId: String = "cat_other",
        categoryName: String = "Other",
        subcategoryId: String = "sub_other",
        subcategoryName: String = "Other",
        tags: List<String> = emptyList(),
        soldCount: Int = 0,
        isPopular: Boolean = false,
        isAvailable: Boolean = true,
        inStock: Boolean = true,
        menuIds: List<String> = emptyList(),
        sets: List<ItemSet> = emptyList(),
        variationsEnabled: Boolean = false,
        variations: List<ItemVariation> = emptyList(),
        variants: List<ItemVariant> = emptyList()
    ) = MerchantItem(
        id = id,
        name = name,
        price = price,
        salePrice = salePrice,
        saleActive = saleActive,
        itemSize = itemSize,
        categoryId = categoryId,
        categoryName = categoryName,
        subcategoryId = subcategoryId,
        subcategoryName = subcategoryName,
        tags = tags,
        soldCount = soldCount,
        isPopular = isPopular,
        isAvailable = isAvailable,
        inStock = inStock,
        menuIds = menuIds,
        hasSets = sets.isNotEmpty(),
        sets = sets,
        variationsEnabled = variationsEnabled,
        variations = variations,
        variants = variants
    )

    @Test
    fun `extractKeywords keeps 3+ letter words and tags while ignoring stop words, sizes, and numbers`() {
        val keywords = RelatedProductsUtil.extractKeywords(
            name = "2pcs Spicy Chicken with Fries and the Large Pack 500g",
            tags = listOf("Crispy", "pack", "Best Seller")
        )
        assertTrue(keywords.contains("spicy"))
        assertTrue(keywords.contains("chicken"))
        assertTrue(keywords.contains("fries"))
        assertTrue(keywords.contains("crispy"))
        assertTrue(keywords.contains("best seller"))

        assertFalse(keywords.contains("with"))
        assertFalse(keywords.contains("and"))
        assertFalse(keywords.contains("the"))
        assertFalse(keywords.contains("large"))
        assertFalse(keywords.contains("pack"))
        assertFalse(keywords.contains("2pcs"))
        assertFalse(keywords.contains("500g"))
    }

    @Test
    fun `buildRecommendations ranks all 4 tiers, excludes current item, caps at 12, and puts unavailable last`() {
        val current = sampleMenuItem(
            id = "cur",
            name = "Spicy Chicken Burger Regular",
            price = 100.0,
            categoryId = "cat_burgers",
            categoryName = "Burgers",
            subcategoryId = "sub_chicken_burger",
            subcategoryName = "Chicken Burgers",
            tags = listOf("crispy")
        )

        val candidates = listOf(
            // Self - must be excluded
            sampleMerchantItem(
                id = "cur",
                name = "Spicy Chicken Burger Regular",
                price = 100.0,
                categoryId = "cat_burgers",
                categoryName = "Burgers",
                subcategoryId = "sub_chicken_burger",
                subcategoryName = "Chicken Burgers",
                tags = listOf("crispy")
            ),
            // Tier 1 (2 keyword matches: spicy + chicken)
            sampleMerchantItem(id = "t1_2matches", name = "Spicy Chicken Tenders", price = 250.0, soldCount = 5),
            // Tier 1 (1 match, higher soldCount)
            sampleMerchantItem(id = "t1_1match_pop", name = "Chicken Fillet", price = 260.0, soldCount = 40),
            // Tier 1 (1 match, lower soldCount, via tag)
            sampleMerchantItem(id = "t1_1match_tag", name = "Crunchy Fries", price = 270.0, tags = listOf("crispy"), soldCount = 10),
            // Tier 2 (same category class/main category, no keyword overlap)
            sampleMerchantItem(id = "t2_cat", name = "Beef Patty Melt", price = 300.0, categoryId = "cat_burgers", categoryName = "Burgers", soldCount = 20),
            // Tier 3 (similar price within 30%: 105 vs 125; 105 should come before 125)
            sampleMerchantItem(id = "t3_far", name = "Mango Shake", price = 125.0),
            sampleMerchantItem(id = "t3_near", name = "Halo Sundae", price = 105.0),
            // Tier 4 (outside 30% price, different category, no keyword overlap)
            sampleMerchantItem(id = "t4_other_pop", name = "Family Pizza", price = 500.0, soldCount = 30),
            sampleMerchantItem(id = "t4_other_a", name = "Apple Pie", price = 40.0, soldCount = 0),
            // Extra filler items to test max 12 cap
            sampleMerchantItem(id = "t4_1", name = "T4 Item B", price = 40.0),
            sampleMerchantItem(id = "t4_2", name = "T4 Item C", price = 40.0),
            sampleMerchantItem(id = "t4_3", name = "T4 Item D", price = 40.0),
            sampleMerchantItem(id = "t4_4", name = "T4 Item E", price = 40.0),
            // Sold out item even with 2 keyword matches must be placed last (or truncated if 12 available precede it)
            sampleMerchantItem(id = "sold_out_match", name = "Spicy Chicken Wrap", price = 99.0, isAvailable = false, inStock = false)
        )

        val recs = RelatedProductsUtil.buildRecommendations(
            currentItem = current,
            shopItems = candidates
        )

        assertEquals(12, recs.size)
        val ids = recs.map { it.item.id }
        assertFalse(ids.contains("cur"))
        assertEquals(
            listOf("t1_2matches", "t1_1match_pop", "t1_1match_tag", "t2_cat", "t3_near", "t3_far", "t4_other_pop"),
            ids.take(7)
        )

        // When total <= 12, sold-out or schedule-unavailable items appear after available ones
        val closedMenu = MerchantMenu(
            id = "breakfast",
            name = "Breakfast",
            active = true,
            schedule = MenuSchedule(
                days = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun"),
                start = "00:00",
                end = "00:01"
            )
        )
        val mainMenu = MerchantMenu(id = "main", name = "Main Menu", active = true)
        val withUnavailable = RelatedProductsUtil.buildRecommendations(
            currentItem = current,
            shopItems = listOf(
                sampleMerchantItem(id = "sold_out", name = "Spicy Chicken Wrap", price = 100.0, inStock = false),
                sampleMerchantItem(id = "sched_unavail", name = "Spicy Pancakes", price = 100.0, menuIds = listOf("breakfast")),
                sampleMerchantItem(id = "avail_t4", name = "Bottled Water", price = 25.0)
            ),
            shopMenus = listOf(mainMenu, closedMenu),
            nowMillis = 12 * 3600_000L // noon UTC (20:00 Asia/Manila)
        )
        assertEquals("avail_t4", withUnavailable.first().item.id)
        assertTrue(withUnavailable.first().isOrderable)
        assertFalse(withUnavailable[1].isOrderable)
        assertFalse(withUnavailable[2].isOrderable)
    }

    @Test
    fun `product details screen renders You May Also Like and invokes callbacks`() {
        val current = sampleMenuItem(id = "p1", name = "Classic Burger", price = 120.0, sets = emptyList())
        val recSimple = sampleMerchantItem(
            id = "p2",
            name = "Spicy Burger",
            price = 150.0,
            salePrice = 110.0,
            saleActive = true,
            itemSize = "150g"
        )

        var openedItem: MerchantItem? = null
        var quickAddedItem: MerchantItem? = null

        composeRule.setContent {
            DelivioTheme {
                CustomizeScreen(
                    state = CustomizeSheetState(
                        item = current,
                        restaurant = testRestaurant,
                        shopItems = listOf(recSimple)
                    ),
                    onClose = {},
                    onAddToCart = { _, _ -> },
                    onOpenProduct = { openedItem = it },
                    onQuickAddProduct = { quickAddedItem = it }
                )
            }
        }

        composeRule.onNodeWithTag("customize_scroll_container")
            .performScrollToNode(hasTestTag("you_may_also_like_section"))
        composeRule.onNodeWithText("You May Also Like").assertIsDisplayed()
        composeRule.onNodeWithTag("yml_card_p2").assertIsDisplayed()
        composeRule.onNodeWithTag("yml_sale_badge_p2", useUnmergedTree = true).assertIsDisplayed()

        composeRule.onNodeWithTag("yml_add_btn_p2").performClick()
        assertEquals("p2", quickAddedItem?.id)

        composeRule.onNodeWithTag("yml_card_p2").performClick()
        assertEquals("p2", openedItem?.id)
    }

    @Test
    fun `customize screen hides You May Also Like when there are no other products`() {
        val set = ItemSet(
            id = "s1",
            name = "Drinks",
            required = false,
            minSelect = 0,
            maxSelect = 1,
            choices = listOf(ItemChoice(id = "c1", name = "Iced Tea", price = 25.0))
        )
        val current = sampleMenuItem(id = "p1", name = "Combo Meal", price = 180.0, sets = listOf(set))

        composeRule.setContent {
            DelivioTheme {
                CustomizeScreen(
                    state = CustomizeSheetState(
                        item = current,
                        restaurant = testRestaurant,
                        shopItems = listOf(sampleMerchantItem(id = "p1", name = "Combo Meal", price = 180.0))
                    ),
                    onClose = {},
                    onAddToCart = { _, _ -> }
                )
            }
        }

        assertTrue(composeRule.onAllNodesWithTag("you_may_also_like_section").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `choose option variation screen displays You May Also Like and invokes callbacks`() {
        val variation = ItemVariation(
            id = "v_size",
            name = "Size",
            options = listOf(ItemVariationOption(id = "opt_m", name = "Medium"))
        )
        val variant = ItemVariant(key = "opt_m", optionIds = listOf("opt_m"), price = 95.0, stock = 5)
        val current = sampleMenuItem(
            id = "var_1",
            name = "Milk Tea Classic",
            price = 95.0,
            variationsEnabled = true,
            variations = listOf(variation),
            variants = listOf(variant)
        )
        val recItem = sampleMerchantItem(id = "var_2", name = "Taro Milk Tea", price = 105.0, itemSize = "16oz")
        var opened: MerchantItem? = null
        var quickAdded: MerchantItem? = null

        composeRule.setContent {
            DelivioTheme {
                VariationSelectorFullScreen(
                    state = VariationSelectorState(
                        item = current,
                        restaurant = testRestaurant,
                        asBottomSheet = false,
                        shopItems = listOf(recItem)
                    ),
                    onClose = {},
                    onConfirm = { _, _ -> },
                    onOpenProduct = { opened = it },
                    onQuickAddProduct = { quickAdded = it }
                )
            }
        }

        composeRule.onNodeWithTag("variation_scroll_container")
            .performScrollToNode(hasTestTag("you_may_also_like_section"))
        composeRule.onNodeWithText("You May Also Like").assertIsDisplayed()
        composeRule.onNodeWithTag("yml_add_btn_var_2").performClick()
        assertEquals("var_2", quickAdded?.id)

        composeRule.onNodeWithTag("yml_card_var_2").performClick()
        assertEquals("var_2", opened?.id)
    }

    @Test
    fun `quickAddRecommendedProduct adds simple item directly and opens selector for options or variations`() {
        val cartRepo = CartRepository()
        val orderRepo = OrderRepository(FirestoreService())
        val cartVm = CartViewModel(cartRepo, orderRepo)

        val simpleItem = sampleMerchantItem(id = "simple_1", name = "Garlic Rice", price = 35.0)
        val setItem = sampleMerchantItem(
            id = "set_1",
            name = "Sisig Bowl",
            price = 120.0,
            sets = listOf(
                ItemSet(
                    id = "s1",
                    name = "Egg",
                    required = true,
                    minSelect = 1,
                    maxSelect = 1,
                    choices = listOf(ItemChoice(id = "c1", name = "Fried Egg", price = 15.0))
                )
            )
        )
        val varItem = sampleMerchantItem(
            id = "var_1",
            name = "T-Shirt",
            price = 250.0,
            variationsEnabled = true,
            variations = listOf(ItemVariation("v1", "Color", options = listOf(ItemVariationOption("o1", "Black")))),
            variants = listOf(ItemVariant("o1", listOf("o1"), price = 250.0, stock = 4))
        )

        cartVm.cacheShopCatalog(
            restaurantId = testRestaurant.id,
            items = listOf(simpleItem, setItem, varItem),
            isStoreOpen = true
        )

        // Simple item adds straight to cart
        cartVm.quickAddRecommendedProduct(simpleItem, testRestaurant)
        assertEquals(1, cartVm.cartItems.value.size)
        assertEquals("simple_1", cartVm.cartItems.value.first().menuItem.id)
        assertNull(cartVm.customizeState.value)
        assertNull(cartVm.variationSelectorState.value)

        // Item with sets opens customizer
        cartVm.quickAddRecommendedProduct(setItem, testRestaurant)
        assertEquals("set_1", cartVm.customizeState.value?.item?.id)

        // Item with variations opens variation selector
        cartVm.quickAddRecommendedProduct(varItem, testRestaurant)
        assertNull(cartVm.customizeState.value)
        assertEquals("var_1", cartVm.variationSelectorState.value?.item?.id)
    }

    @Test
    fun `missing or guessed review ids return null quietly without writing to CrashReportManager`() = runTest {
        val fs = FirestoreService()
        val orderRepo = OrderRepository(fs)
        val orderVm = OrderViewModel(orderRepo)
        val initialReports = CrashReportManager.reports.value.size

        // Guessed / non-existent order ID must return null ("not reviewed") and log zero non-fatal crashes
        val guessedReview = orderVm.getReview("order_1795113176971")
        assertNull(guessedReview)
        assertNull(fs.getReview("order_1795113176971"))
        assertFalse(fs.reviewExists("order_1795113176971"))

        assertTrue(
            fs.isPermissionDeniedError(
                FirebaseFirestoreException(
                    "Missing or insufficient permissions.",
                    FirebaseFirestoreException.Code.PERMISSION_DENIED
                )
            )
        )
        assertTrue(fs.isPermissionDeniedError(RuntimeException("PERMISSION_DENIED: reviews/order_1795113176971")))
        assertEquals(initialReports, CrashReportManager.reports.value.size)
    }
}
