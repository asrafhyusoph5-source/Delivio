package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.YellowPrimary

/**
 * DelivioUnifiedMapPicker backed by OpenStreetMap (osmdroid) and Nominatim.
 */
@Composable
fun DelivioUnifiedMapPicker(
    title: String,
    initialAddress: String,
    initialLat: Double = 9.7392,
    initialLng: Double = 118.7353,
    pinColor: Color = YellowPrimary,
    onLocationConfirmed: (addressText: String, lat: Double, lng: Double) -> Unit,
    modifier: Modifier = Modifier
) {
    OsmLocationPickerMap(
        title = title,
        initialAddress = initialAddress,
        initialLat = initialLat,
        initialLng = initialLng,
        pinColor = pinColor,
        onLocationConfirmed = onLocationConfirmed,
        modifier = modifier
    )
}
