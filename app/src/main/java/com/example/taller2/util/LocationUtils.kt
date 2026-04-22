package com.example.taller2.util

import android.location.Location
import com.google.android.gms.maps.model.LatLng

fun shouldAddPoint(last: LatLng, current: LatLng): Boolean {
    val result = FloatArray(1)

    Location.distanceBetween(
        last.latitude,
        last.longitude,
        current.latitude,
        current.longitude,
        result
    )

    return result[0] >= 5f
}