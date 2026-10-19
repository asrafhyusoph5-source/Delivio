package com.example.data.datasource

import com.example.data.model.Address
import com.example.data.model.MenuItem
import com.example.data.model.OrderStatus
import com.example.data.model.Restaurant
import com.example.data.model.Rider
import com.example.util.AuthConstants

object MockData {

    val mockAddresses = listOf(
        Address(
            id = "addr_1",
            title = "Home",
            fullAddress = "742 Evergreen Terrace, Springfield",
            details = "Apt 4B, Ring bell #12",
            isDefault = true
        ),
        Address(
            id = "addr_2",
            title = "Office",
            fullAddress = "500 Howard Street, Suite 300, Financial District",
            details = "Front desk, leave with receptionist",
            isDefault = false
        ),
        Address(
            id = "addr_3",
            title = "Gym",
            fullAddress = "1200 Market Street, Downtown",
            details = "Meet at main entrance gate",
            isDefault = false
        )
    )

    val mockRider = Rider(
        id = "rider_101",
        name = "Alex Vance",
        phone = AuthConstants.MOCK_RIDER_PHONE,
        vehicle = "Honda PCX 160",
        licensePlate = "CD-9824",
        rating = 4.9,
        totalDeliveries = 1420,
        avatarEmoji = "🛵"
    )

    val categories = listOf(
        "All" to "🍽️",
        "Burgers" to "🍔",
        "Pizza" to "🍕",
        "Chicken" to "🍗",
        "Asian" to "🍜",
        "Drinks" to "🥤",
        "Dessert" to "🍰"
    )

    val restaurants = listOf(
        Restaurant(
            id = "rest_1",
            name = "Golden Smash Burger",
            category = "Burgers",
            rating = 4.9,
            reviewCount = 840,
            etaMinutes = 18,
            deliveryFee = 0.0,
            minOrder = 12.0,
            priceTier = "$$",
            address = "142 Elm St, Downtown",
            phone = AuthConstants.MOCK_MERCHANT_PHONE,
            bannerEmoji = "🍔",
            badge = "Free Delivery",
            isFastDelivery = true,
            isDeal = true,
            dealDescription = "20% off over $25"
        ),
        Restaurant(
            id = "rest_2",
            name = "Napoli Woodfire Pizza",
            category = "Pizza",
            rating = 4.8,
            reviewCount = 612,
            etaMinutes = 25,
            deliveryFee = 1.99,
            minOrder = 15.0,
            priceTier = "$$$",
            address = "88 Little Italy Ave",
            phone = AuthConstants.MOCK_MERCHANT_PHONE,
            bannerEmoji = "🍕",
            badge = "Chef's Choice",
            isFastDelivery = false,
            isDeal = false
        ),
        Restaurant(
            id = "rest_3",
            name = "Crispy Seoul Fried Chicken",
            category = "Chicken",
            rating = 4.9,
            reviewCount = 1120,
            etaMinutes = 20,
            deliveryFee = 0.99,
            minOrder = 14.0,
            priceTier = "$$",
            address = "302 K-Town Boulevard",
            phone = AuthConstants.MOCK_MERCHANT_PHONE,
            bannerEmoji = "🍗",
            badge = "Top Rated",
            isFastDelivery = true,
            isDeal = true,
            dealDescription = "Buy 1 Get 1 Drinks"
        ),
        Restaurant(
            id = "rest_4",
            name = "Tokyo Ramen & Dumpling Bar",
            category = "Asian",
            rating = 4.7,
            reviewCount = 530,
            etaMinutes = 22,
            deliveryFee = 1.49,
            minOrder = 12.0,
            priceTier = "$$",
            address = "77 Cherry Blossom Way",
            phone = AuthConstants.MOCK_MERCHANT_PHONE,
            bannerEmoji = "🍜",
            badge = "Popular",
            isFastDelivery = false,
            isDeal = false
        ),
        Restaurant(
            id = "rest_5",
            name = "Boba Dream Tea & Cafe",
            category = "Drinks",
            rating = 4.9,
            reviewCount = 980,
            etaMinutes = 15,
            deliveryFee = 0.0,
            minOrder = 8.0,
            priceTier = "$",
            address = "210 University Ave",
            phone = AuthConstants.MOCK_MERCHANT_PHONE,
            bannerEmoji = "🧋",
            badge = "Super Fast",
            isFastDelivery = true,
            isDeal = true,
            dealDescription = "Free delivery"
        ),
        Restaurant(
            id = "rest_6",
            name = "Dolce Gelato & Patisserie",
            category = "Dessert",
            rating = 4.8,
            reviewCount = 415,
            etaMinutes = 17,
            deliveryFee = 1.99,
            minOrder = 10.0,
            priceTier = "$$",
            address = "54 Sweet Tooth Lane",
            phone = AuthConstants.MOCK_MERCHANT_PHONE,
            bannerEmoji = "🍰",
            badge = "Deals Available",
            isFastDelivery = true,
            isDeal = true,
            dealDescription = "15% off entire menu",
            isOpen = false,
            nextOpenTime = "Opens 11:00 AM"
        ),
        Restaurant(
            id = "rest_7",
            name = "Fire Grill BBQ Smokehouse",
            category = "Burgers",
            rating = 4.6,
            reviewCount = 380,
            etaMinutes = 28,
            deliveryFee = 2.49,
            minOrder = 18.0,
            priceTier = "$$$",
            address = "909 Hickory Road",
            phone = AuthConstants.MOCK_MERCHANT_PHONE,
            bannerEmoji = "🥩",
            badge = "Hearty Portions",
            isFastDelivery = false,
            isDeal = false,
            isOpen = false,
            nextOpenTime = "Opens 4:00 PM"
        ),
        Restaurant(
            id = "rest_8",
            name = "Bangkok Street Wok",
            category = "Asian",
            rating = 4.8,
            reviewCount = 760,
            etaMinutes = 19,
            deliveryFee = 0.0,
            minOrder = 12.0,
            priceTier = "$$",
            address = "12 Spice District",
            phone = AuthConstants.MOCK_MERCHANT_PHONE,
            bannerEmoji = "🥢",
            badge = "Fast Delivery",
            isFastDelivery = true,
            isDeal = true,
            dealDescription = "Free Spring Rolls",
            isOpen = true
        ),
        Restaurant(
            id = "conv_1",
            name = "7-Quick 24/7 Mart",
            category = "Groceries",
            rating = 4.9,
            reviewCount = 1350,
            etaMinutes = 15,
            deliveryFee = 0.99,
            minOrder = 8.0,
            priceTier = "$",
            address = "101 Grand Avenue, Downtown",
            phone = AuthConstants.MOCK_MERCHANT_PHONE,
            bannerEmoji = "🏪",
            badge = "Open 24/7",
            isFastDelivery = true,
            isDeal = true,
            dealDescription = "Snacks & Drinks Deals",
            isConvenience = true,
            isOpen = true
        ),
        Restaurant(
            id = "conv_2",
            name = "Green Harvest Supermarket",
            category = "Produce",
            rating = 4.8,
            reviewCount = 920,
            etaMinutes = 22,
            deliveryFee = 0.0,
            minOrder = 15.0,
            priceTier = "$$",
            address = "450 Market Street",
            phone = AuthConstants.MOCK_MERCHANT_PHONE,
            bannerEmoji = "🥦",
            badge = "Free Delivery",
            isFastDelivery = false,
            isDeal = true,
            dealDescription = "Fresh Daily Farm Picks",
            isConvenience = true,
            isOpen = true
        ),
        Restaurant(
            id = "conv_3",
            name = "Daily Corner Bodega & Mart",
            category = "Essentials",
            rating = 4.7,
            reviewCount = 610,
            etaMinutes = 17,
            deliveryFee = 1.49,
            minOrder = 10.0,
            priceTier = "$",
            address = "220 Central Boulevard",
            phone = AuthConstants.MOCK_MERCHANT_PHONE,
            bannerEmoji = "🥫",
            badge = "Essentials",
            isFastDelivery = true,
            isDeal = false,
            isConvenience = true,
            isOpen = false,
            nextOpenTime = "Opens 8:00 AM"
        )
    )

    val menuItemsByRestaurant: Map<String, List<MenuItem>> = mapOf(
        "rest_1" to listOf(
            MenuItem("m1_1", "rest_1", "Double Truffle Smash", "Two Angus patties, black truffle aioli, Swiss cheese, brioche bun", 13.99, "Burgers", "🍔", isPopular = true),
            MenuItem("m1_2", "rest_1", "Classic Cheddar Burger", "Charbroiled beef, aged cheddar, lettuce, heirloom tomato, secret sauce", 10.99, "Burgers", "🍔", isPopular = true),
            MenuItem("m1_3", "rest_1", "Crispy Bacon BBQ Burger", "Smoked bacon, cheddar, crispy onion strings, house bourbon BBQ", 12.49, "Burgers", "🥓"),
            MenuItem("m1_4", "rest_1", "Garlic Parmesan Fries", "Hand-cut Idaho potatoes, garlic butter, fresh rosemary, aged parm", 4.99, "Sides", "🍟", isPopular = true),
            MenuItem("m1_5", "rest_1", "Loaded Cheese Tots", "Golden crispy tots drenched in melted cheddar, jalapeños, bacon bits", 5.99, "Sides", "🥔"),
            MenuItem("m1_6", "rest_1", "Salted Caramel Shake", "Hand-spun Madagascar vanilla ice cream, sea salt, rich dulce sauce", 5.49, "Drinks", "🥤"),
            MenuItem("m1_7", "rest_1", "Craft Root Beer", "Locally brewed, spiced sassafras, cane sugar", 2.99, "Drinks", "🍺")
        ),
        "rest_2" to listOf(
            MenuItem("m2_1", "rest_2", "Margherita D.O.P.", "San Marzano sauce, fresh buffalo mozzarella, fresh basil, extra virgin olive oil", 14.99, "Pizza", "🍕", isPopular = true),
            MenuItem("m2_2", "rest_2", "Spicy Diavola", "Spicy Calabrian salami, smoked mozzarella, chili honey drizzle", 16.49, "Pizza", "🌶️", isPopular = true),
            MenuItem("m2_3", "rest_2", "Quattro Formaggi", "Gorgonzola, fontina, parmigiano reggiano, mozzarella, garlic herbs", 15.99, "Pizza", "🧀"),
            MenuItem("m2_4", "rest_2", "Prosciutto & Arugula", "Prosciutto di Parma, wild baby arugula, shaved parmesan, balsamic glaze", 17.50, "Pizza", "🥓"),
            MenuItem("m2_5", "rest_2", "Garlic Herb Focaccia", "Woodfired flatbread with rosemary, sea salt, roasted garlic dip", 6.25, "Sides", "🥖"),
            MenuItem("m2_6", "rest_2", "San Pellegrino Limonata", "Sparkling citrus beverage, perfectly refreshing", 3.25, "Drinks", "🍋")
        ),
        "rest_3" to listOf(
            MenuItem("m3_1", "rest_3", "Soy Garlic Fried Chicken", "Ultra-crispy double-fried bone-in chicken glazed with sweet soy glaze", 14.99, "Chicken", "🍗", isPopular = true),
            MenuItem("m3_2", "rest_3", "Yangnyeom Spicy Crispy Chicken", "Classic Korean sweet and spicy chili sauce, crushed roasted peanuts", 15.49, "Chicken", "🌶️", isPopular = true),
            MenuItem("m3_3", "rest_3", "Honey Butter Boneless Tenders", "Tender breast bites tossed in rich creamy honey butter seasoning", 13.99, "Chicken", "🍯"),
            MenuItem("m3_4", "rest_3", "Kimchi Fried Rice", "Wok-seared aged kimchi, jasmine rice, sunny-side egg, sesame seeds", 8.99, "Sides", "🍳", isPopular = true),
            MenuItem("m3_5", "rest_3", "Pickled Daikon Radish", "Sweet vinegar crunch cubes, the essential palate cleanser", 2.49, "Sides", "🥗"),
            MenuItem("m3_6", "rest_3", "Korean Plum Soda", "Sparkling iced beverage infused with green plum syrup", 3.49, "Drinks", "🥤")
        ),
        "rest_4" to listOf(
            MenuItem("m4_1", "rest_4", "Tonkotsu Black Garlic Ramen", "24-hour pork bone broth, tender chashu pork, seasoned soft egg, black garlic oil", 15.99, "Ramen", "🍜", isPopular = true),
            MenuItem("m4_2", "rest_4", "Spicy Miso Ramen", "Rich fermented soybean broth, wok ground pork, corn, scallions, bamboo shoots", 14.99, "Ramen", "🌶️", isPopular = true),
            MenuItem("m4_3", "rest_4", "Pan-Fried Pork Gyoza (6pcs)", "Handmade dumplings with ginger pork filling, crispy lace bottom, dipping sauce", 7.49, "Appetizers", "🥟", isPopular = true),
            MenuItem("m4_4", "rest_4", "Takoyaki Octopus Balls", "Golden batter balls, tender octopus, bonito flakes, Japanese mayo, bulldog sauce", 8.25, "Appetizers", "🐙"),
            MenuItem("m4_5", "rest_4", "Iced Matcha Green Tea Latte", "Ceremonial grade Uji matcha, organic whole milk, slight sweet cane", 4.99, "Drinks", "🍵")
        ),
        "rest_5" to listOf(
            MenuItem("m5_1", "rest_5", "Brown Sugar Tiger Pearl Milk", "Slow-cooked brown sugar boba pearls, creamy fresh milk, caramelized cream", 6.25, "Boba", "🧋", isPopular = true),
            MenuItem("m5_2", "rest_5", "Jasmine Dragon Green Milk Tea", "Fragrant floral green tea base, silky milk, honey golden pearls", 5.75, "Boba", "🧋", isPopular = true),
            MenuItem("m5_3", "rest_5", "Mango Passionfruit Fruit Tea", "Fresh diced mango, passion fruit seeds, four seasons oolong, aloe jelly", 6.50, "Fruit Tea", "🥭"),
            MenuItem("m5_4", "rest_5", "Taro Coconut Cloud Swirl", "Real purple taro paste, rich coconut cream, crushed sago pearls", 6.75, "Boba", "🍠"),
            MenuItem("m5_5", "rest_5", "Egg Waffle Hong Kong Style", "Crispy puffy bubble waffle with condensed milk drizzle", 5.25, "Snacks", "🧇", isPopular = true)
        ),
        "rest_6" to listOf(
            MenuItem("m6_1", "rest_6", "Pistachio Sicilian Gelato Pint", "Authentic Bronte pistachios, rich silky milk base, roasted nut bits", 9.99, "Gelato", "🍨", isPopular = true),
            MenuItem("m6_2", "rest_6", "Tiramisu Tradizionale", "Espresso-soaked savoiardi ladyfingers, creamy mascarpone zabaione, cocoa", 7.99, "Dessert", "🍰", isPopular = true),
            MenuItem("m6_3", "rest_6", "Warm Molten Chocolate Lava Cake", "Gooey Belgian chocolate center, served with vanilla crème chantilly", 8.50, "Dessert", "🍫"),
            MenuItem("m6_4", "rest_6", "Strawberry Choux Puff (3pcs)", "Crisp choux pastry filled with diplomat cream and macerated strawberries", 6.99, "Pastry", "🍓"),
            MenuItem("m6_5", "rest_6", "Espresso Macchiato", "Single origin double shot espresso with velvety microfoam", 3.75, "Coffee", "☕")
        ),
        "rest_7" to listOf(
            MenuItem("m7_1", "rest_7", "Smoked Texas Brisket Platter", "14-hour hickory smoked beef brisket, house pickles, Texas toast, slaw", 18.99, "BBQ", "🥩", isPopular = true),
            MenuItem("m7_2", "rest_7", "St. Louis Smoked Ribs (Half Rack)", "Dry-rubbed baby back ribs glazed with sweet honey smoke BBQ", 16.99, "BBQ", "🍖", isPopular = true),
            MenuItem("m7_3", "rest_7", "Pulled Pork Brioche Sandwich", "Tender pulled shoulder, tangy cider slaw, toasted sesame bun", 12.99, "Burgers", "🍔"),
            MenuItem("m7_4", "rest_7", "Creamy 3-Cheese Mac & Cheese", "Cavatappi pasta, sharp cheddar, gouda, Monterey jack, crumb crust", 5.99, "Sides", "🧀"),
            MenuItem("m7_5", "rest_7", "Sweet Cornbread Muffin", "Baked with sweet corn kernels and honey butter", 3.49, "Sides", "🌽"),
            MenuItem("m7_6", "rest_7", "Southern Sweet Iced Tea", "Freshly brewed black tea sweetened with lemon slice", 2.99, "Drinks", "🥤")
        ),
        "rest_8" to listOf(
            MenuItem("m8_1", "rest_8", "Pad Thai with Jumbo Prawns", "Wok-fried rice noodles, tiger shrimp, tamarind pulp, peanuts, bean sprouts", 14.50, "Noodles", "🍤", isPopular = true),
            MenuItem("m8_2", "rest_8", "Bangkok Basil Minced Chicken (Kra Pao)", "Spicy holy basil stir fry, fried sunny egg, steamed jasmine rice", 13.25, "Mains", "🍲", isPopular = true),
            MenuItem("m8_3", "rest_8", "Creamy Green Curry Chicken", "Bamboo shoots, Thai eggplant, kaffir lime leaves, sweet basil coconut broth", 13.99, "Curry", "🥥"),
            MenuItem("m8_4", "rest_8", "Crispy Vegetable Spring Rolls (4pcs)", "Glass noodles, shredded cabbage, carrots, sweet plum dipping sauce", 6.50, "Appetizers", "🥢", isPopular = true),
            MenuItem("m8_5", "rest_8", "Thai Iced Tea with Condensed Milk", "Authentic amber Ceylon tea, crushed ice, creamy milk cap", 3.99, "Drinks", "🧋")
        ),
        "conv_1" to listOf(
            MenuItem("c1_1", "conv_1", "Whole Milk (1 Gallon)", "Grade A pasteurized whole milk, rich and creamy", 4.49, "Dairy", "🥛", isPopular = true),
            MenuItem("c1_2", "conv_1", "Artisan White Toast Bread", "Sliced sandwich loaf bread, freshly delivered daily", 2.99, "Bakery", "🍞", isPopular = true),
            MenuItem("c1_3", "conv_1", "Sparkling Spring Water (Pack of 6)", "Pure mountain spring water 500ml bottles", 5.99, "Drinks", "💧"),
            MenuItem("c1_4", "conv_1", "Spicy Cheese Tortilla Chips", "Crunchy loaded tortilla nachos bag 220g", 3.49, "Snacks", "🧀", isPopular = true),
            MenuItem("c1_5", "conv_1", "Instant Ramen Noodles (5-Pack)", "Savory chicken broth quick ramen", 4.25, "Pantry", "🍜"),
            MenuItem("c1_6", "conv_1", "Dark Chocolate Hazelnut Bar", "Swiss recipe 70% cocoa with whole nuts", 2.79, "Sweets", "🍫")
        ),
        "conv_2" to listOf(
            MenuItem("c2_1", "conv_2", "Organic Honeycrisp Apples (1kg)", "Crisp, sweet orchard-picked organic apples", 5.99, "Produce", "🍎", isPopular = true),
            MenuItem("c2_2", "conv_2", "Free-Range Large Brown Eggs (Dozen)", "Pasture-raised grade AA organic farm eggs", 4.99, "Dairy", "🥚", isPopular = true),
            MenuItem("c2_3", "conv_2", "Ripe Hass Avocados (3-Pack)", "Creamy Hass avocados, perfect for salads and toast", 4.50, "Produce", "🥑", isPopular = true),
            MenuItem("c2_4", "conv_2", "Greek Whole Milk Yogurt 32oz", "Plain unsweetened probiotic rich yogurt", 5.49, "Dairy", "🥣"),
            MenuItem("c2_5", "conv_2", "Extra Virgin Olive Oil 500ml", "Cold-pressed Mediterranean olive oil", 8.99, "Pantry", "🫒")
        ),
        "conv_3" to listOf(
            MenuItem("c3_1", "conv_3", "Canned Chunk Light Tuna (3-Pack)", "Wild-caught tuna in spring water 140g each", 4.89, "Pantry", "🐟", isPopular = true),
            MenuItem("c3_2", "conv_3", "Creamy Peanut Butter 16oz", "Roasted peanuts, smooth spread, gluten-free", 3.99, "Pantry", "🥜", isPopular = true),
            MenuItem("c3_3", "conv_3", "Honey Nut Toasted Oats Cereal", "Whole grain breakfast cereal 350g box", 4.79, "Breakfast", "🥣"),
            MenuItem("c3_4", "conv_3", "Ultra Citrus Dish Soap 750ml", "Cuts through grease with fresh lemon scent", 3.29, "Household", "🧼"),
            MenuItem("c3_5", "conv_3", "AA Alkaline Batteries (4-Pack)", "Long-lasting power for home electronics", 5.49, "Hardware", "🔋")
        )
    )

    fun getMenuItems(restaurantId: String): List<MenuItem> {
        return menuItemsByRestaurant[restaurantId] ?: emptyList()
    }

    val sampleOrderForTest = run {
        val rest = restaurants.first()
        val items = getMenuItems(rest.id).take(2).map {
            com.example.data.model.CartItem(
                menuItem = it,
                restaurantId = rest.id,
                restaurantName = rest.name,
                quantity = 1
            )
        }
        val sub = items.sumOf { it.totalPrice }
        com.example.data.model.Order(
            id = "ORD-7829",
            restaurant = rest,
            items = items,
            status = OrderStatus.PLACED,
            createdAt = System.currentTimeMillis(),
            statusTimestamps = mapOf(OrderStatus.PLACED to System.currentTimeMillis()),
            subtotal = sub,
            deliveryFee = 1.99,
            serviceFee = 1.99,
            discount = 0.0,
            total = sub + 1.99 + 1.99,
            deliveryAddress = mockAddresses.first(),
            rider = mockRider,
            deliveryNotes = "Leave at front door please"
        )
    }
}
