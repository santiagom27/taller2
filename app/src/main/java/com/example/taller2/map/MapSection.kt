package com.example.taller2.map

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.taller2.model.RoutePhoto
import com.example.taller2.util.loadBitmap
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
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
            // ── Marcadores con miniatura de la foto ──────────────────────────
            photos.forEach { photo ->
                PhotoMarker(photo = photo)
            }

            // ── Polyline del recorrido ───────────────────────────────────────
            if (routePoints.size >= 2) {
                Polyline(
                    points = routePoints,
                    color  = routeColor,
                    width  = 8f
                )
            }
        }

        // ── Etiqueta sección ─────────────────────────────────────────────────
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
        ) {
            Text(
                text     = "Mapa",
                color    = Color.White,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        // ── Botones Iniciar / Borrar ──────────────────────────────────────────
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
                    imageVector        = Icons.Default.PlayArrow,
                    contentDescription = "Iniciar recorrido"
                )
                Text(if (isTracking) " En curso" else " Iniciar")
            }

            FilledTonalButton(onClick = onClear) {
                Icon(
                    imageVector        = Icons.Default.Delete,
                    contentDescription = "Borrar recorrido"
                )
                Text(" Borrar")
            }
        }

        // ── Hint inferior ─────────────────────────────────────────────────────
        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.92f)
        ) {
            Text(
                text     = "Mueve el mapa para cambiar la ubicación de la foto",
                color    = Color.White,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MARCADOR CON MINIATURA
// Usa MarkerComposable para mostrar la foto dentro del pin del mapa.
// La imagen se carga de forma asíncrona con produceState + loadBitmap.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PhotoMarker(photo: RoutePhoto) {
    val context = LocalContext.current

    // Carga el bitmap de forma asíncrona (suspending fun en IO)
    val bitmap by produceState<Bitmap?>(
        initialValue = null,
        key1         = photo.uri
    ) {
        value = loadBitmap(context, photo.uri)
    }

    MarkerComposable(
        state   = MarkerState(position = photo.location),
        title   = photo.name,
        snippet = "Foto tomada durante el recorrido",
        // anchor centra el marcador horizontalmente y lo ancla por la punta inferior
        anchor  = androidx.compose.ui.geometry.Offset(0.5f, 1f)
    ) {
        // Contenedor del marcador: miniatura + triángulo inferior
        Box(contentAlignment = Alignment.TopCenter) {
            // ── Thumbnail ────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(
                        width = 2.5.dp,
                        color = Color.White,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap             = bitmap!!.asImageBitmap(),
                        contentDescription = photo.name,
                        modifier           = Modifier.fillMaxSize(),
                        contentScale       = ContentScale.Crop
                    )
                } else {
                    // Placeholder mientras carga
                    Box(
                        modifier         = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text     = "📷",
                            fontSize = 22.sp
                        )
                    }
                }
            }

            // ── Triángulo / punta inferior del marcador ───────────────────────
            // Se simula con un Box pequeño rotado
            Box(
                modifier = Modifier
                    .padding(top = 60.dp)
                    .size(width = 14.dp, height = 10.dp)
                    .background(
                        color = Color.White,
                        shape = TriangleShape
                    )
            )
        }
    }
}

// Forma triangular para la punta del marcador
private val TriangleShape = object : androidx.compose.ui.graphics.Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density
    ): androidx.compose.ui.graphics.Outline {
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(size.width / 2f, size.height) // punta inferior
            lineTo(0f, 0f)                        // esquina superior izquierda
            lineTo(size.width, 0f)                // esquina superior derecha
            close()
        }
        return androidx.compose.ui.graphics.Outline.Generic(path)
    }
}