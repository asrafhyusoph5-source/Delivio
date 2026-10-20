package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.YellowPrimary

/**
 * OpenStreetMap Interactive Location Picker backed by osmdroid and Nominatim.
 */
@Composable
fun InteractiveLocationPickerMap(
    title: String,
    initialAddress: String,
    initialLat: Double = 9.7392,
    initialLng: Double = 118.7353,
    pinColor: Color = YellowPrimary,
    onLocationSelected: (String, Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    OsmLocationPickerMap(
        title = title,
        initialAddress = initialAddress,
        initialLat = initialLat,
        initialLng = initialLng,
        pinColor = pinColor,
        onLocationConfirmed = { addr, lat, lng ->
            onLocationSelected(addr, lat.toFloat(), lng.toFloat())
        },
        modifier = modifier
    )
}
