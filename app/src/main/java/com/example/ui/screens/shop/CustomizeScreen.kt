package com.example.ui.screens.shop

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.ItemChoice
import com.example.data.model.ItemSet
import com.example.data.model.SelectedItemChoice
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextOnYellow
import com.example.ui.theme.YellowPrimary
import com.example.util.Formatters
import com.example.viewmodel.CustomizeSheetState
import kotlinx.coroutines.launch

@Composable
fun CustomizeScreen(
    state: CustomizeSheetState,
    onClose: () -> Unit,
    onAddToCart: (List<SelectedItemChoice>, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onClose() }

    val item = state.item
    val scope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()

    var quantity by remember { mutableIntStateOf(state.initialQuantity.coerceAtLeast(1)) }
    val selectedChoices = remember {
        mutableStateListOf<SelectedItemChoice>().apply {
            addAll(state.initialSelections)
        }
    }

    // Track which set is currently incomplete when user taps disabled Add to cart
    var highlightedSetId by remember { mutableStateOf<String?>(null) }

    // Validation: Every required set must reach its minimum selection (minSelect)
    val isReadyToAdd by remember {
        derivedStateOf {
            item.sets.all { set ->
                val count = selectedChoices.count { it.setId == set.id }
                if (set.required) {
                    count >= set.minSelect
                } else {
                    true
                }
            }
        }
    }

    // Live Total = (basePrice + sum of chosen prices) * quantity
    val liveTotal by remember {
        derivedStateOf {
            val addOnsTotal = selectedChoices.sumOf { it.price }
            (item.price + addOnsTotal) * quantity
        }
    }

    val isEditing = state.editingCartItemId != null

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // TOP BAR: Round X button, title "Customize", and under it the item name and base price
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Round X button
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                            .testTag("customize_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isEditing) "Edit Item" else "Customize",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = Formatters.formatPrice(item.price),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                val sizeText = Formatters.formatItemSize(item.itemSize)
                                if (sizeText.isNotEmpty()) {
                                    Text(
                                        text = sizeText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // SINGLE SCROLL CONTAINER (product detail + sets / option groups)
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("customize_scroll_container"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 140.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp) // Generous 24 dp spacing between sets
            ) {
                // Product Detail Header (large bold price, then "/225g" in small grey)
                item(key = "product_header") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp)
                    ) {
                        if (item.imageUrl.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(16.dp))
                            ) {
                                com.example.ui.components.DelivioAsyncImage(
                                    imageUrl = item.imageUrl,
                                    contentDescription = item.name,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (item.description.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = item.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = Formatters.formatPrice(item.price),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val sizeText = Formatters.formatItemSize(item.itemSize)
                            if (sizeText.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = sizeText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                itemsIndexed(item.sets, key = { _, set -> set.id }) { index, itemSet ->
                    val isHighlighted = highlightedSetId == itemSet.id

                    ItemSetCard(
                        set = itemSet,
                        selectedChoices = selectedChoices,
                        isHighlighted = isHighlighted,
                        onChoiceToggled = { choice ->
                            highlightedSetId = null // clear highlight on selection
                            handleChoiceToggle(
                                set = itemSet,
                                choice = choice,
                                selectedChoices = selectedChoices
                            )
                        }
                    )
                }
            }
        }

        // STICKY BOTTOM BAR (stepper and large Add to cart button with live total)
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 16.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Stepper row (minus, quantity, plus)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Quantity",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        IconButton(
                            onClick = { if (quantity > 1) quantity-- },
                            enabled = quantity > 1,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(if (quantity > 1) MaterialTheme.colorScheme.surface else Color.Transparent)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = "Decrease quantity",
                                tint = if (quantity > 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Text(
                            text = "$quantity",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )

                        IconButton(
                            onClick = { quantity++ },
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(YellowPrimary)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Increase quantity",
                                tint = TextOnYellow,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Add to Cart Button (Large, showing live total: base plus options times quantity)
                // If tapped while disabled: scrolls to first incomplete set, highlights it in red, shows "Please choose one"
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Button(
                        onClick = {
                            if (isReadyToAdd) {
                                onAddToCart(selectedChoices.toList(), quantity)
                            } else {
                                // Find first incomplete required set
                                val firstIncomplete = item.sets.firstOrNull { set ->
                                    set.required && selectedChoices.count { it.setId == set.id } < set.minSelect
                                }
                                if (firstIncomplete != null) {
                                    highlightedSetId = firstIncomplete.id
                                    val targetIndex = item.sets.indexOf(firstIncomplete)
                                    if (targetIndex >= 0) {
                                        scope.launch {
                                            // +1 because item 0 is the product_header
                                            lazyListState.animateScrollToItem(targetIndex + 1)
                                        }
                                    }
                                }
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isReadyToAdd) YellowPrimary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isReadyToAdd) TextOnYellow else MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("customize_add_to_cart_button")
                    ) {
                        val actionLabel = if (isEditing) "Update cart" else "Add to cart"
                        Text(
                            text = "$actionLabel • ${Formatters.formatPrice(liveTotal)}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Each set shows its OWN name as a large bold title (use name, else customName, else subcategoryName).
 * Never show category classes here.
 * On the right a pill: "Required" (dark with white text) or "Optional" (light grey).
 * Under the title a grey hint: "Select one", "Select 2 to 3", or "Select up to 3".
 */
@Composable
private fun ItemSetCard(
    set: ItemSet,
    selectedChoices: List<SelectedItemChoice>,
    isHighlighted: Boolean,
    onChoiceToggled: (ItemChoice) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedInSet = selectedChoices.filter { it.setId == set.id }
    val selectedCount = selectedInSet.size
    val isMaxReached = selectedCount >= set.maxSelect
    val isSingleChoice = set.maxSelect <= 1

    // Own name as large bold title (use name, else customName, else subcategoryName for old data)
    val setTitle = set.displayName.ifBlank { "Option Group" }

    // Grey hint under title: "Select one", "Select 2 to 3", or "Select up to 3"
    val hintText = when {
        set.maxSelect == 1 -> "Select one"
        set.minSelect in 2..set.maxSelect && set.minSelect != set.maxSelect -> "Select ${set.minSelect} to ${set.maxSelect}"
        set.minSelect > 1 && set.minSelect == set.maxSelect -> "Select ${set.minSelect}"
        else -> "Select up to ${set.maxSelect}"
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isHighlighted) ErrorRed.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = if (isHighlighted) 1.5.dp else 1.dp,
            color = if (isHighlighted) ErrorRed else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row: Title & Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Title (Large Bold)
                Text(
                    text = setTitle,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f, fill = false)
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Pill: "Required" (dark with white text) or "Optional" (light grey)
                if (set.required) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF212121) // Dark pill with white text
                    ) {
                        Text(
                            text = "Required",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f) // Light grey pill
                    ) {
                        Text(
                            text = "Optional",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Grey hint under title: "Select one", "Select 2 to 3", or "Select up to 3"
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = hintText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )

                if (set.maxSelect > 1) {
                    Text(
                        text = "$selectedCount / ${set.maxSelect}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Highlight error notice if tapped Add to Cart while incomplete
            if (isHighlighted) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(ErrorRed.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = ErrorRed,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (set.maxSelect == 1) "Please choose one" else "Please select at least ${set.minSelect}",
                        color = ErrorRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Thin divider before options
            HorizontalDivider(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                thickness = 0.8.dp
            )

            // Option rows (at least 56 dp tall, whole row tappable)
            Column(modifier = Modifier.fillMaxWidth()) {
                set.choices.forEachIndexed { choiceIdx, choice ->
                    val isSoldOut = !choice.available
                    val isSelected = selectedInSet.any { it.choiceId == choice.id }
                    // When maximum is reached, remaining unchecked options become disabled
                    val isMaxLimitReachedForOption = !isSingleChoice && isMaxReached && !isSelected
                    val isDisabled = isSoldOut || isMaxLimitReachedForOption

                    OptionRow(
                        choice = choice,
                        isSingleChoice = isSingleChoice,
                        isSelected = isSelected,
                        isDisabled = isDisabled,
                        isSoldOut = isSoldOut,
                        isMaxLimitReached = isMaxLimitReachedForOption,
                        maxSelect = set.maxSelect,
                        onSelect = {
                            if (!isDisabled) {
                                onChoiceToggled(choice)
                            }
                        }
                    )

                    // Thin divider between options (except after last)
                    if (choiceIdx < set.choices.lastIndex) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            thickness = 0.6.dp,
                            modifier = Modifier.padding(start = 12.dp, end = 12.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Option rows:
 * - At least 56 dp tall, the whole row is tappable
 * - Name in bold
 * - Below it a small flame and "Popular" if popular
 * - On the right "Free" when the price is 0, otherwise "+ P7.00" (or "+ ₱7.00")
 * - Far right a round radio button when max is 1, or checkbox when higher
 * - Sold-out options are greyed with "Sold out" and disabled
 * - When maximum is reached, other options become disabled with hint "You can choose up to N"
 */
@Composable
private fun OptionRow(
    choice: ItemChoice,
    isSingleChoice: Boolean,
    isSelected: Boolean,
    isDisabled: Boolean,
    isSoldOut: Boolean,
    isMaxLimitReached: Boolean,
    maxSelect: Int,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp) // at least 56 dp tall
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (!isDisabled) {
                    Modifier.clickable { onSelect() }
                } else {
                    Modifier.alpha(0.5f)
                }
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Column: Name in bold; below it small flame and "Popular" if popular (or disabled max reached hint)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = choice.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (isSoldOut) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
            )

            if (choice.isPopular) {
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_flame),
                        contentDescription = "Popular",
                        tint = Color(0xFFFF5722),
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "Popular",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF5722)
                    )
                }
            } else if (isMaxLimitReached) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "You can choose up to $maxSelect",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Right side: Price / Sold out badge
        if (isSoldOut) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = "Sold out",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        } else if (choice.price == 0.0) {
            Text(
                text = "Free",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = SuccessGreen
            )
        } else {
            Text(
                text = "+ ${Formatters.formatPrice(choice.price)}",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Far right: Round radio button when maximum is 1, or checkbox when higher
        if (isSingleChoice) {
            RadioButton(
                selected = isSelected,
                onClick = if (!isDisabled) onSelect else null,
                enabled = !isDisabled,
                colors = RadioButtonDefaults.colors(
                    selectedColor = YellowPrimary,
                    unselectedColor = MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier.size(24.dp)
            )
        } else {
            Checkbox(
                checked = isSelected,
                onCheckedChange = if (!isDisabled) { _ -> onSelect() } else null,
                enabled = !isDisabled,
                colors = CheckboxDefaults.colors(
                    checkedColor = YellowPrimary,
                    checkmarkColor = TextOnYellow,
                    uncheckedColor = MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

private fun handleChoiceToggle(
    set: ItemSet,
    choice: ItemChoice,
    selectedChoices: MutableList<SelectedItemChoice>
) {
    if (!choice.available) return
    val setName = set.displayName.ifBlank { set.name }

    if (set.maxSelect <= 1) {
        // Single choice / radio
        selectedChoices.removeAll { it.setId == set.id }
        selectedChoices.add(
            SelectedItemChoice(
                setId = set.id,
                setName = setName,
                choiceId = choice.id,
                choiceName = choice.name,
                price = choice.price
            )
        )
    } else {
        // Multi-choice / checkbox
        val existingIndex = selectedChoices.indexOfFirst { it.setId == set.id && it.choiceId == choice.id }
        if (existingIndex >= 0) {
            selectedChoices.removeAt(existingIndex)
        } else {
            val currentCountForSet = selectedChoices.count { it.setId == set.id }
            if (currentCountForSet < set.maxSelect) {
                selectedChoices.add(
                    SelectedItemChoice(
                        setId = set.id,
                        setName = setName,
                        choiceId = choice.id,
                        choiceName = choice.name,
                        price = choice.price
                    )
                )
            }
        }
    }
}
