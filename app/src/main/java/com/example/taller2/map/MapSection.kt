package com.example.taller2.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.taller2.model.RoutePhoto
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.flow.collectLatest

@Composable
fun MapSection(
    modifier: Modifier,
    photos: List<RoutePhoto>,
    routePoints: List<LatLng>,
    isTracking: Boolean,
    onStartTracking: () -> Unit,
    onClear: () -> Unit,
    onLocationChange: (LatLng) -> Unit
) {
    val bogota = remember { LatLng(4.60971, -74.08175) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(bogota, 16f)
    }

    val routeColor = MaterialTheme.colorScheme.primary

    LaunchedEffect(cameraPositionState) {
        snapshotFlow { cameraPositionState.position.target }
            .collectLatest { target ->
                onLocationChange(target)
            }
    }

    Box(modifier = modifier) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            uiSettings = MapUiSettings(
                zoomControlsEnabled = true,
                compassEnabled = true,
                myLocationButtonEnabled = false
            )
        ) {
            photos.forEach { photo ->
                Marker(
                    state = MarkerState(position = photo.location),
                    title = photo.name,
                    snippet = "Foto tomada durante el recorrido",
                    icon = BitmapDescriptorFactory.defaultMarker(
                        BitmapDescriptorFactory.HUE_GREEN
                    )
                )
            }

            if (routePoints.size >= 2) {
                Polyline(
                    points = routePoints,
                    color = routeColor,
                    width = 8f
                )
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
        ) {
            Text(
                text = "Mapa",
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = onStartTracking,
                enabled = !isTracking
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Iniciar recorrido"
                )
                Text(if (isTracking) " En curso" else " Iniciar")
            }

            FilledTonalButton(
                onClick = onClear
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Borrar recorrido"
                )
                Text(" Borrar")
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.92f)
        ) {
            Text(
                text = "Mueve el mapa para cambiar la ubicación de la foto",
                color = Color.White,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}