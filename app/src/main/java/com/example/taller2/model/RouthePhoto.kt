package com.example.taller2.model

import android.net.Uri
import com.google.android.gms.maps.model.LatLng

data class RoutePhoto(
    val name: String,
    val uri: Uri,
    val location: LatLng
)