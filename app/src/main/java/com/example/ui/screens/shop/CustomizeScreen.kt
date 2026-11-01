package com.example.ui.screens.shop

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ItemChoice
import com.example.data.model.ItemSet
import com.example.data.model.SelectedItemChoice
import com.example.ui.components.DelivioAsyncImage
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextOnYellow
import com.example.ui.theme.YellowPrimary
import com.example.util.Formatters
import com.example.viewmodel.CustomizeSheetState

@Composable
fun CustomizeScreen(
    state: CustomizeSheetState,
    onClose: () -> Unit,
    onAddToCart: (List<SelectedItemChoice>, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onClose() }

    val item = state.item
    var quantity by remember { mutableIntStateOf(state.initialQuantity.coerceAtLeast(1)) }
    val selectedChoices = remember {
        mutableStateListOf<SelectedItemChoice>().apply {
            addAll(state.initialSelections)
        }
    }

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
            // DELIVIO YELLOW TOP BAR
            Surface(
                color = YellowPrimary,
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("customize_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextOnYellow,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isEditing) "Edit Item" else "Customize",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextOnYellow
                        )
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = TextOnYellow.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // SINGLE SCROLL CONTAINER
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("customize_scroll_container"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // PRODUCT HEADER CARD
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            val trimmedImageUrl = item.imageUrl.trim()
                            if (trimmedImageUrl.isNotEmpty()) {
                                DelivioAsyncImage(
                                    imageUrl = trimmedImageUrl,
                                    contentDescription = item.name,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(130.dp)
                                        .background(YellowPrimary.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(item.emoji.ifBlank { "🍽️" }, fontSize = 48.sp)
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )

                                Text(
                                    text = "Base price: ${Formatters.formatPrice(item.price)}",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = YellowPrimary
                                )

                                if (item.description.isNotBlank()) {
                                    Text(
                                        text = item.description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // SET SECTIONS
                items(item.sets) { itemSet ->
                    ItemSetSection(
                        set = itemSet,
                        selectedChoices = selectedChoices,
                        onChoiceToggled = { choice ->
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

        // STICKY BOTTOM BAR (Above system navigation bar)
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
                // Quantity Stepper & Price Summary
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
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        IconButton(
                            onClick = { if (quantity > 1) quantity-- },
                            enabled = quantity > 1,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = "Decrease quantity",
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Text(
                            text = "$quantity",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )

                        IconButton(
                            onClick = { quantity++ },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Increase quantity",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Add to Cart Button (56dp tall, full width, live total)
                Button(
                    onClick = {
                        if (isReadyToAdd) {
                            onAddToCart(selectedChoices.toList(), quantity)
                        }
                    },
                    enabled = isReadyToAdd,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = YellowPrimary,
                        contentColor = TextOnYellow,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor = MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("customize_add_to_cart_button")
                ) {
                    val actionLabel = if (isEditing) "Update cart" else "Add to cart"
                    Text(
                        text = if (isReadyToAdd) {
                            "$actionLabel • ${Formatters.formatPrice(liveTotal)}"
                        } else {
                            "Select required options"
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

@Composable
private fun ItemSetSection(
    set: ItemSet,
    selectedChoices: List<SelectedItemChoice>,
    onChoiceToggled: (ItemChoice) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedInSet = selectedChoices.filter { it.setId == set.id }
    val selectedCount = selectedInSet.size
    val isMaxReached = selectedCount >= set.maxSelect
    val isSingleChoice = set.maxSelect <= 1

    val ruleText = when {
        set.required && set.maxSelect <= 1 -> "Select one"
        set.required && set.minSelect <= 1 -> "Select 1 to ${set.maxSelect}"
        set.required && set.minSelect > 1 -> "Select at least ${set.minSelect}, up to ${set.maxSelect}"
        set.maxSelect <= 1 -> "Optional"
        else -> "Optional - up to ${set.maxSelect}"
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Set Header: Name & Required/Optional Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = set.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f, fill = false)
                )

                Spacer(modifier = Modifier.width(8.dp))

                if (set.required) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF212121) // Dark pill
                    ) {
                        Text(
                            text = "Required",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = YellowPrimary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
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

            // Rule Text & Live Counter
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = ruleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$selectedCount of ${set.maxSelect} selected",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (set.required && selectedCount < set.minSelect) YellowPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                thickness = 1.dp
            )

            // Choices list
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                set.choices.forEach { choice ->
                    val isSoldOut = !choice.available
                    val isSelected = selectedInSet.any { it.choiceId == choice.id }
                    // When maximum is reached, disable remaining unchecked choices
                    val isReachedMaxUnchecked = !isSingleChoice && isMaxReached && !isSelected
                    val isDisabled = isSoldOut || isReachedMaxUnchecked

                    ChoiceRow(
                        choice = choice,
                        isSingleChoice = isSingleChoice,
                        isSelected = isSelected,
                        isDisabled = isDisabled,
                        isSoldOut = isSoldOut,
                        onSelect = {
                            if (!isDisabled) {
                                onChoiceToggled(choice)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChoiceRow(
    choice: ItemChoice,
    isSingleChoice: Boolean,
    isSelected: Boolean,
    isDisabled: Boolean,
    isSoldOut: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (!isDisabled) {
                    Modifier.clickable { onSelect() }
                } else {
                    Modifier.alpha(0.45f)
                }
            )
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSingleChoice) {
            RadioButton(
                selected = isSelected,
                onClick = onSelect,
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
                onCheckedChange = { onSelect() },
                enabled = !isDisabled,
                colors = CheckboxDefaults.colors(
                    checkedColor = YellowPrimary,
                    checkmarkColor = TextOnYellow,
                    uncheckedColor = MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Choice Name
        Text(
            text = choice.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isDisabled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Price or Sold Out badge
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
    }
}

private fun handleChoiceToggle(
    set: ItemSet,
    choice: ItemChoice,
    selectedChoices: MutableList<SelectedItemChoice>
) {
    if (!choice.available) return

    if (set.maxSelect <= 1) {
        // Single choice / radio
        selectedChoices.removeAll { it.setId == set.id }
        selectedChoices.add(
            SelectedItemChoice(
                setId = set.id,
                setName = set.name,
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
                        setName = set.name,
                        choiceId = choice.id,
                        choiceName = choice.name,
                        price = choice.price
                    )
                )
            }
        }
    }
}
