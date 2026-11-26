package com.example.ui.screens.more

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppDesignerServiceConfig
import com.example.data.model.City
import com.example.data.model.LoanOffer
import com.example.data.model.PlatformSettings
import com.example.data.repository.RestaurantRepository
import com.example.ui.components.DelivioIcon
import com.example.ui.theme.BadgeOrange
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextOnYellow
import com.example.ui.theme.YellowPrimary
import com.example.util.DelivioIconConstants
import com.example.util.ServiceAvailability
import java.text.NumberFormat
import java.util.Locale

data class MoreServiceTile(
    val key: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val iconBg: Color,
    val iconTint: Color,
    val iconUrl: String = "",
    val badgeText: String? = null
)

@Composable
fun MoreServicesScreen(
    restaurantRepository: RestaurantRepository,
    cityId: String,
    currentUid: String,
    onBack: () -> Unit,
    onOpenFlight: (String) -> Unit,
    onOpenLoans: () -> Unit,
    onOpenJobFair: (String) -> Unit,
    onOpenFood: () -> Unit = {},
    onOpenConvenience: () -> Unit = {},
    onOpenParcel: (String) -> Unit = {},
    onOpenPabili: (String) -> Unit = {},
    onOpenTaxi: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val cities by restaurantRepository.service.getCitiesFlow().collectAsState(initial = emptyList())
    val platformSettings by restaurantRepository.service.getPlatformSettingsFlow().collectAsState(initial = PlatformSettings())
    val appDesignerSettings by restaurantRepository.appDesignerSettings.collectAsState()
    val resolvedCity = remember(cityId, cities) {
        ServiceAvailability.resolveCity(cityId, cities) ?: City(id = cityId, name = cityId)
    }
    val liveCity by restaurantRepository.service.getCityDocumentFlow(resolvedCity.id).collectAsState(initial = null)
    val effectiveCity = liveCity ?: resolvedCity
    val loanOffer by restaurantRepository.service.getLoanOfferFlow(currentUid).collectAsState(initial = null)

    val tiles = remember(appDesignerSettings, platformSettings, effectiveCity, loanOffer) {
        appDesignerSettings.orderedServicesForPlacement("more")
            .filter { cfg ->
                val platformOn = platformSettings.services[cfg.id] != false
                val cityOn = effectiveCity.services[cfg.id] != false
                val loanOk = if (cfg.id == "loans") (loanOffer?.enabled == true) else true
                platformOn && cityOn && loanOk
            }
            .map { cfg -> buildMoreServiceTile(cfg, loanOffer) }
    }

    MoreServicesContent(
        tiles = tiles,
        onBack = onBack,
        onTileClick = { key ->
            when (key) {
                "food" -> onOpenFood()
                "convenience" -> onOpenConvenience()
                "parcel" -> onOpenParcel(effectiveCity.id)
                "pabili" -> onOpenPabili(effectiveCity.id)
                "taxi" -> onOpenTaxi(effectiveCity.id)
                "flight" -> onOpenFlight(effectiveCity.id)
                "loans" -> onOpenLoans()
                "jobfair" -> onOpenJobFair(effectiveCity.id)
            }
        },
        modifier = modifier
    )
}

fun buildMoreServiceTile(
    cfg: AppDesignerServiceConfig,
    loanOffer: LoanOffer? = null
): MoreServiceTile {
    val fallbackIcon = DelivioIconConstants.getServiceFallback(cfg.id)
    val title = cfg.label.ifBlank { com.example.util.AppDesignerParser.defaultServiceConfig(cfg.id).label }
    val iconUrl = cfg.iconUrl.trim()
    return when (cfg.id) {
        "flight" -> MoreServiceTile(
            key = "flight",
            title = title,
            subtitle = "Search domestic & international routes",
            icon = fallbackIcon,
            iconBg = YellowPrimary.copy(alpha = 0.22f),
            iconTint = TextOnYellow,
            iconUrl = iconUrl,
            badgeText = "Preview"
        )
        "loans" -> {
            val maxAmt = loanOffer?.maxAmount ?: 0.0
            val offerBadge = if (maxAmt > 0.0) {
                "Up to ₱${NumberFormat.getNumberInstance(Locale.US).apply { maximumFractionDigits = 0 }.format(maxAmt.toLong())}"
            } else {
                "Offer ready"
            }
            MoreServiceTile(
                key = "loans",
                title = title,
                subtitle = "View your personalized Delivio loan offer",
                icon = fallbackIcon,
                iconBg = SuccessGreen.copy(alpha = 0.14f),
                iconTint = SuccessGreen,
                iconUrl = iconUrl,
                badgeText = offerBadge
            )
        }
        "jobfair" -> MoreServiceTile(
            key = "jobfair",
            title = title,
            subtitle = "Browse local & global job openings",
            icon = fallbackIcon,
            iconBg = BadgeOrange.copy(alpha = 0.14f),
            iconTint = BadgeOrange,
            iconUrl = iconUrl
        )
        "food" -> MoreServiceTile(
            key = "food",
            title = title,
            subtitle = "Order meals from nearby restaurants",
            icon = fallbackIcon,
            iconBg = YellowPrimary.copy(alpha = 0.22f),
            iconTint = TextOnYellow,
            iconUrl = iconUrl
        )
        "convenience" -> MoreServiceTile(
            key = "convenience",
            title = title,
            subtitle = "24/7 groceries, essentials & snacks",
            icon = fallbackIcon,
            iconBg = SuccessGreen.copy(alpha = 0.14f),
            iconTint = SuccessGreen,
            iconUrl = iconUrl
        )
        "parcel" -> MoreServiceTile(
            key = "parcel",
            title = title,
            subtitle = "Send packages & documents across town",
            icon = fallbackIcon,
            iconBg = BadgeOrange.copy(alpha = 0.14f),
            iconTint = BadgeOrange,
            iconUrl = iconUrl
        )
        "pabili" -> MoreServiceTile(
            key = "pabili",
            title = title,
            subtitle = "Buy-for-me errands from local stores",
            icon = fallbackIcon,
            iconBg = YellowPrimary.copy(alpha = 0.22f),
            iconTint = TextOnYellow,
            iconUrl = iconUrl
        )
        "taxi" -> MoreServiceTile(
            key = "taxi",
            title = title,
            subtitle = "Book a quick ride around the city",
            icon = fallbackIcon,
            iconBg = SuccessGreen.copy(alpha = 0.14f),
            iconTint = SuccessGreen,
            iconUrl = iconUrl
        )
        else -> MoreServiceTile(
            key = cfg.id,
            title = title,
            subtitle = "Explore $title",
            icon = fallbackIcon,
            iconBg = YellowPrimary.copy(alpha = 0.22f),
            iconTint = TextOnYellow,
            iconUrl = iconUrl
        )
    }
}

@Composable
fun MoreServicesContent(
    showFlight: Boolean,
    showLoans: Boolean,
    showJobFair: Boolean,
    loanOffer: LoanOffer? = null,
    onBack: () -> Unit,
    onTileClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val tiles = remember(showFlight, showLoans, showJobFair, loanOffer) {
        buildList {
            if (showFlight) {
                add(buildMoreServiceTile(com.example.util.AppDesignerParser.defaultServiceConfig("flight"), loanOffer))
            }
            if (showLoans) {
                add(buildMoreServiceTile(com.example.util.AppDesignerParser.defaultServiceConfig("loans"), loanOffer))
            }
            if (showJobFair) {
                add(buildMoreServiceTile(com.example.util.AppDesignerParser.defaultServiceConfig("jobfair"), loanOffer))
            }
        }
    }

    MoreServicesContent(
        tiles = tiles,
        onBack = onBack,
        onTileClick = onTileClick,
        modifier = modifier
    )
}

@Composable
fun MoreServicesContent(
    tiles: List<MoreServiceTile>,
    onBack: () -> Unit,
    onTileClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
        topBar = {
            Surface(
                color = YellowPrimary,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("more_services_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextOnYellow
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "More services",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextOnYellow
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .navigationBarsPadding()
        ) {
            if (tiles.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp)
                        .testTag("more_services_empty_state"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(84.dp)
                            .clip(CircleShape)
                            .background(YellowPrimary.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.GridView,
                            contentDescription = null,
                            tint = YellowPrimary,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Nothing here yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(tiles, key = { it.key }) { tile ->
                        LargeServiceTileCard(
                            tile = tile,
                            onClick = { onTileClick(tile.key) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LargeServiceTileCard(
    tile: MoreServiceTile,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .testTag("more_tile_${tile.key}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DelivioIcon(
                iconFileName = tile.iconUrl.trim().ifEmpty { null },
                contentDescription = tile.title,
                size = 64.dp,
                fallbackIcon = tile.icon,
                fallbackTint = tile.iconTint,
                backgroundColor = tile.iconBg,
                shape = RoundedCornerShape(18.dp),
                fallbackIconSize = 32.dp,
                iconPadding = 8.dp
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = tile.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (!tile.badgeText.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = tile.iconBg
                        ) {
                            Text(
                                text = tile.badgeText,
                                color = tile.iconTint,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
                Text(
                    text = tile.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Open ${tile.title}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
