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
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import androidx.compose.runtime.snapshotFlow

@OptIn(FlowPreview::class)
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

    // ── Escucha el centro del mapa con debounce ───────────────────────────────
    // Sin debounce: mientras el usuario arrastra, se emiten decenas de posiciones
    // intermedias por segundo → todas pasan por shouldAddPoint → puntos falsos
    // en el recorrido → línea quebrada.
    //
    // Con debounce(600ms): el flujo solo emite cuando el mapa lleva 600ms quieto.
    // Eso significa que mientras el usuario arrastra no se registra nada, y solo
    // cuando suelta el dedo se toma la posición final como "ubicación actual".
    //
    // distinctUntilChanged: evita emitir si el usuario tocó el mapa pero lo dejó
    // exactamente en el mismo lugar.
    //
    // filter(!isMoving): descarta emisiones mientras la animación de inercia
    // del mapa todavía está en curso.
    LaunchedEffect(cameraPositionState) {
        snapshotFlow { cameraPositionState.isMoving to cameraPositionState.position.target }
            .filter  { (isMoving, _) -> !isMoving }
            .debounce(600L)
            .distinctUntilChanged { old, new -> old.second == new.second }
            .collectLatest { (_, target) ->
                onLocationChange(target)
            }
    }

    Box(modifier = modifier) {
        GoogleMap(
            modifier            = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            uiSettings          = MapUiSettings(
                zoomControlsEnabled     = true,
                compassEnabled          = true,
                myLocationButtonEnabled = false
            )
        ) {
            photos.forEach { photo -> PhotoMarker(photo = photo) }

            if (routePoints.size >= 2) {
                Polyline(
                    points = routePoints,
                    color  = routeColor,
                    width  = 8f
                )
            }
        }

        // ── Etiqueta sección ──────────────────────────────────────────────────
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

        // ── Botones Iniciar / Borrar ───────────────────────────────────────────
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
                Icon(Icons.Default.PlayArrow, contentDescription = "Iniciar recorrido")
                Text(if (isTracking) " En curso" else " Iniciar")
            }

            FilledTonalButton(onClick = onClear) {
                Icon(Icons.Default.Delete, contentDescription = "Borrar recorrido")
                Text(" Borrar")
            }
        }

        // ── Hint inferior ──────────────────────────────────────────────────────
        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.92f)
        ) {
            Text(
                text     = if (isTracking) "Recorrido en curso…" else "Mueve el mapa para cambiar la ubicación de la foto",
                color    = Color.White,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MARCADOR CON MINIATURA
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PhotoMarker(photo: RoutePhoto) {
    val context = LocalContext.current

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
        anchor  = androidx.compose.ui.geometry.Offset(0.5f, 1f)
    ) {
        Box(contentAlignment = Alignment.TopCenter) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(2.5.dp, Color.White, RoundedCornerShape(10.dp))
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
                    Box(
                        modifier         = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "📷", fontSize = 22.sp)
                    }
                }
            }

            Box(
                modifier = Modifier
                    .padding(top = 60.dp)
                    .size(width = 14.dp, height = 10.dp)
                    .background(color = Color.White, shape = TriangleShape)
            )
        }
    }
}

private val TriangleShape = object : androidx.compose.ui.graphics.Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density
    ): androidx.compose.ui.graphics.Outline {
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(size.width / 2f, size.height)
            lineTo(0f, 0f)
            lineTo(size.width, 0f)
            close()
        }
        return androidx.compose.ui.graphics.Outline.Generic(path)
    }
}