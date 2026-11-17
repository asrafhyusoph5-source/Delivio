package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import com.example.data.datasource.MockData
import com.example.data.model.ItemAvailability
import com.example.data.model.ItemVariant
import com.example.data.model.ItemVariation
import com.example.data.model.ItemVariationOption
import com.example.data.model.MainCategoriesConfig
import com.example.data.model.MerchantItem
import com.example.data.model.MerchantSubcategory
import com.example.data.model.PlatformSettings
import com.example.data.model.Restaurant
import com.example.ui.screens.shop.ConvenienceShopContent
import com.example.ui.screens.shop.resolveConvenienceColumns
import com.example.ui.theme.DelivioTheme
import com.example.util.AppDesignerParser
import com.example.util.DelivioStatusConstants
import com.example.util.Formatters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class ConvenienceShopRedesignTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun sampleConvenienceShop() = MockData.restaurants.first().copy(
        id = "conv_1",
        name = "Uncle John's Mart",
        type = "convenience",
        isConvenience = true,
        cityId = "puerto-princesa",
        ratingSum = 24.0,
        ratingCount = 5,
        prepTimeMinutes = 10
    )

    private fun sampleSubcategories() = listOf(
        MerchantSubcategory(id = "sub_snacks", name = "Snacks", sortOrder = 1),
        MerchantSubcategory(id = "sub_drinks", name = "Drinks", sortOrder = 2)
    )

    private fun sampleItems(): List<MerchantItem> = listOf(
        MerchantItem(
            id = "item_a",
            name = "Zebra Chips",
            price = 100.0,
            salePrice = 40.0,
            saleActive = true,
            itemSize = "225g",
            subcategoryId = "sub_snacks",
            subcategoryName = "Snacks",
            isAvailable = true,
            inStock = true
        ),
        MerchantItem(
            id = "item_b",
            name = "Apple Biscuit",
            price = 99690.0,
            subcategoryId = "sub_snacks",
            subcategoryName = "Snacks",
            isAvailable = true,
            inStock = true
        ),
        MerchantItem(
            id = "item_c",
            name = "Cheese Crackers",
            price = 92.92,
            subcategoryId = "sub_snacks",
            subcategoryName = "Snacks",
            isAvailable = true,
            inStock = true
        ),
        MerchantItem(
            id = "item_d",
            name = "Berry Juice",
            price = 50.0,
            subcategoryId = "sub_drinks",
            subcategoryName = "Drinks",
            isAvailable = true,
            inStock = false // Sold out
        ),
        MerchantItem(
            id = "item_e",
            name = "Coffee Bottle",
            price = 75.0,
            subcategoryId = "sub_drinks",
            subcategoryName = "Drinks",
            isAvailable = true,
            inStock = true,
            availability = ItemAvailability(
                days = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun"),
                start = "00:00",
                end = "00:01",
                dateFrom = "2099-01-01",
                dateTo = "2099-01-02"
            )
        ),
        MerchantItem(
            id = "item_f",
            name = "Orange Soda",
            price = 45.0,
            subcategoryId = "sub_drinks",
            subcategoryName = "Drinks",
            isAvailable = true,
            inStock = true
        ),
        MerchantItem(
            id = "item_g",
            name = "Unassigned Battery Pack",
            price = 120.0,
            subcategoryId = "",
            subcategoryName = "",
            variationsEnabled = true,
            variations = listOf(
                ItemVariation(
                    id = "v_size",
                    name = "Pack",
                    options = listOf(
                        ItemVariationOption(id = "o_2", name = "2-Pack"),
                        ItemVariationOption(id = "o_4", name = "4-Pack")
                    )
                )
            ),
            variants = listOf(
                ItemVariant(key = "o_2", optionIds = listOf("o_2"), label = "2-Pack", price = 120.0, stock = 5),
                ItemVariant(key = "o_4", optionIds = listOf("o_4"), label = "4-Pack", price = 220.0, stock = 5)
            ),
            isAvailable = true,
            inStock = true
        )
    )

    @Test
    fun responsiveColumnResolution_appliesWidthBreakpointsCorrectly() {
        // Phone width (360dp): keeps admin columns 1..3
        assertEquals(1, resolveConvenienceColumns(1, 360.dp))
        assertEquals(2, resolveConvenienceColumns(2, 360.dp))
        assertEquals(3, resolveConvenienceColumns(3, 360.dp))

        // Tablet / wide width (>= 600dp): admin + 2 (max 6)
        assertEquals(3, resolveConvenienceColumns(1, 600.dp))
        assertEquals(4, resolveConvenienceColumns(2, 600.dp))
        assertEquals(5, resolveConvenienceColumns(3, 840.dp))

        // Extra narrow (< 320dp): at most 2 columns
        assertEquals(1, resolveConvenienceColumns(1, 300.dp))
        assertEquals(2, resolveConvenienceColumns(2, 300.dp))
        assertEquals(2, resolveConvenienceColumns(3, 300.dp))
    }

    @Test
    fun compactPriceFormatter_hidesWholeZeroDecimalsOnlyWhenRequested() {
        assertEquals("₱99,690", Formatters.formatCompactPrice(99690.0, hideWholeZeroDecimals = true))
        assertEquals("₱50", Formatters.formatCompactPrice(50.0, hideWholeZeroDecimals = true))
        assertEquals("₱92.92", Formatters.formatCompactPrice(92.92, hideWholeZeroDecimals = true))
        assertEquals("₱99,690.00", Formatters.formatCompactPrice(99690.0, hideWholeZeroDecimals = false))
    }

    @Test
    fun appDesignerParser_clampsAndParsesNumbersAndStrings() {
        assertEquals(3, AppDesignerParser.parseConvenienceColumns(3L))
        assertEquals(3, AppDesignerParser.parseConvenienceColumns("3"))
        assertEquals(1, AppDesignerParser.parseConvenienceColumns("1"))
        assertEquals(1, AppDesignerParser.parseConvenienceColumns(0))
        assertEquals(3, AppDesignerParser.parseConvenienceColumns(8))
        assertEquals(2, AppDesignerParser.parseConvenienceColumns("not-a-number"))
        assertEquals(2, AppDesignerParser.parseConvenienceColumns(null))
    }

    @Test
    fun showLayoutDebugFlagIsFalse() {
        assertFalse(DelivioStatusConstants.SHOW_LAYOUT_DEBUG)
    }

    @Test
    fun convenience3Columns_showsContinuousGridWithCompactHeaderAndNoSectionHeaders() {
        var directAddedItemId: String? = null
        var openedSelectorItemId: String? = null
        var openedProductPageItemId: String? = null
        var adminCols by mutableIntStateOf(3)
        var searchQuery by mutableStateOf("")

        composeRule.setContent {
            DelivioTheme {
                ConvenienceShopContent(
                    restaurant = sampleConvenienceShop(),
                    firestoreSubcategories = sampleSubcategories(),
                    firestoreItems = sampleItems(),
                    firestoreMenus = emptyList(),
                    cartItems = emptyList(),
                    platformSettings = PlatformSettings(),
                    soldCountMap = emptyMap(),
                    adminColumns = adminCols,
                    isStoreOpen = true,
                    canOrderFromStore = true,
                    hasOtherBranches = false,
                    etaRangeText = "15–25 min",
                    shopVouchers = emptyList(),
                    appliedPromoCode = null,
                    searchQuery = searchQuery,
                    mainCategories = MainCategoriesConfig(),
                    onChangeBranch = {},
                    onUseVoucher = {},
                    onDirectAdd = { item, _ -> directAddedItemId = item.id },
                    onDecrease = {},
                    onOpenSelectorSheet = { item, _ -> openedSelectorItemId = item.id },
                    onOpenProductPage = { item, _ -> openedProductPageItemId = item.id }
                )
            }
        }

        // Compact header is shown with shop name, OPEN chip, rating, ETA, logo (no big hero banner)
        composeRule.onNodeWithTag("convenience_compact_header").assertIsDisplayed()
        composeRule.onNodeWithTag("convenience_header_logo").assertIsDisplayed()
        composeRule.onNodeWithTag("convenience_header_name").assertIsDisplayed()
        composeRule.onNodeWithText("OPEN").assertIsDisplayed()
        composeRule.onNodeWithText("15–25 min").assertIsDisplayed()
        composeRule.onAllNodesWithTag("shop_header_banner_cover").assertCountEquals(0)
        composeRule.onAllNodesWithTag("layout_debug_footer").assertCountEquals(0)

        // Category chips: All (selected), Snacks, Drinks, Other last
        composeRule.onNodeWithTag("convenience_chip_all").assertIsDisplayed()
        composeRule.onNodeWithTag("convenience_chip_sub_snacks").assertIsDisplayed()
        composeRule.onNodeWithTag("convenience_chip_sub_drinks").assertIsDisplayed()
        composeRule.onNodeWithTag("convenience_chip_other_section").assertIsDisplayed()

        // No section headers in 3-col continuous grid
        composeRule.onAllNodesWithTag("section_header_sub_snacks").assertCountEquals(0)
        composeRule.onAllNodesWithTag("section_header_sub_drinks").assertCountEquals(0)

        // 7 items arranged in 3-col rows: row_0 (3 items), row_1 (3 items), row_2 (1 item)
        composeRule.onNodeWithTag("convenience_grid_row_0").assertIsDisplayed()
        composeRule.onNodeWithTag("convenience_grid_row_1").assertIsDisplayed()

        // Verify class-order then alphabetical ordering:
        // Row 0 = Snacks alphabetical: Apple Biscuit, Cheese Crackers, Zebra Chips
        composeRule.onNodeWithText("Apple Biscuit").assertIsDisplayed()
        composeRule.onNodeWithText("₱99,690").assertIsDisplayed() // hidden .00 in 3-col
        composeRule.onNodeWithText("₱92.92").assertIsDisplayed() // keeps decimals when non-zero
        composeRule.onNodeWithTag("convenience_discount_badge_item_a", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("-60%").assertIsDisplayed()
        composeRule.onNodeWithText("225g").assertIsDisplayed()

        // Verify "+" button sits completely inside image box bounds in 3-col mode
        val imageBounds = composeRule.onNodeWithTag("convenience_card_image_box_item_b", useUnmergedTree = true).getBoundsInRoot()
        val addBtnBounds = composeRule.onNodeWithTag("convenience_add_btn_item_b").getBoundsInRoot()
        assertTrue("Add btn left must be inside image", addBtnBounds.left >= imageBounds.left)
        assertTrue("Add btn right must be inside image", addBtnBounds.right <= imageBounds.right)
        assertTrue("Add btn top must be inside image", addBtnBounds.top >= imageBounds.top)
        assertTrue("Add btn bottom must be inside image", addBtnBounds.bottom <= imageBounds.bottom)

        // Tapping "+" on plain item adds directly; tapping card opens product page
        composeRule.onNodeWithTag("convenience_add_btn_item_b").performClick()
        assertEquals("item_b", directAddedItemId)
        composeRule.onNodeWithTag("convenience_grid_card_item_b").performClick()
        assertEquals("item_b", openedProductPageItemId)

        // Scroll to row_1 and verify sold out & unavailable states
        composeRule.onNodeWithTag("convenience_products_grid")
            .performScrollToNode(hasTestTag("convenience_grid_row_1"))
        composeRule.onNodeWithTag("convenience_sold_out_label_item_d", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("convenience_add_btn_disabled_item_d").assertIsDisplayed()
        composeRule.onNodeWithTag("convenience_unavailable_label_item_e", useUnmergedTree = true).assertIsDisplayed()

        // Scroll to row_2 (7th item) and verify variation "+" opens selector
        composeRule.onNodeWithTag("convenience_products_grid")
            .performScrollToNode(hasTestTag("convenience_grid_row_2"))
        composeRule.onNodeWithTag("convenience_grid_row_2").assertIsDisplayed()
        composeRule.onNodeWithTag("convenience_add_btn_item_g").performClick()
        assertEquals("item_g", openedSelectorItemId)

        // Tap Drinks chip -> only Drinks items appear, Snacks item hidden
        composeRule.onNodeWithTag("convenience_chip_sub_drinks").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Orange Soda").assertIsDisplayed()
        composeRule.onAllNodesWithText("Apple Biscuit").assertCountEquals(0)

        // Tap All again and test search -> empty shows "No products found"
        composeRule.onNodeWithTag("convenience_chip_all").performClick()
        searchQuery = "nonexistentproductxyz"
        composeRule.waitForIdle()
        composeRule.onNodeWithText("No products found").assertIsDisplayed()
        searchQuery = ""
        composeRule.waitForIdle()

        // Live admin change: 3 -> 2 columns -> still grid (with decimals shown on whole prices)
        adminCols = 2
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("convenience_products_grid").assertIsDisplayed()
        composeRule.onNodeWithText("₱99,690.00").assertIsDisplayed()

        // Live admin change: 2 -> 1 column -> switches to 1-column wide row list with section headers
        adminCols = 1
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("convenience_single_column_list").assertIsDisplayed()
        composeRule.onNodeWithTag("section_header_sub_snacks").assertIsDisplayed()
        composeRule.onNodeWithTag("convenience_wide_card_item_b").assertIsDisplayed()
    }
}
