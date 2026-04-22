package com.example.taller2.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.NoPhotography
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.example.taller2.camera.CameraSection
import com.example.taller2.map.MapSection
import com.example.taller2.model.RoutePhoto
import com.example.taller2.util.shouldAddPoint
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState
import com.google.android.gms.maps.model.LatLng

@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainScreen() {
    val isLandscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // ── Permisos ─────────────────────────────────────────────────────────────
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
    val locationPermissions = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    // ── Estado compartido ─────────────────────────────────────────────────────
    val photos       = remember { mutableStateListOf<RoutePhoto>() }
    val routePoints  = remember { mutableStateListOf<LatLng>() }

    var currentLocation by remember { mutableStateOf(LatLng(4.60971, -74.08175)) }
    var isTracking      by remember { mutableStateOf(false) }

    // ── Solicitar permisos al inicio ──────────────────────────────────────────
    LaunchedEffect(Unit) {
        if (!cameraPermission.status.isGranted) {
            cameraPermission.launchPermissionRequest()
        }
        if (!locationPermissions.allPermissionsGranted) {
            locationPermissions.launchMultiplePermissionRequest()
        }
    }

    // ── Acciones del recorrido ────────────────────────────────────────────────
    val startTracking = {
        isTracking = true
        if (routePoints.isEmpty()) {
            routePoints.add(currentLocation)
        }
    }

    val clearAll = {
        isTracking = false
        photos.clear()
        routePoints.clear()
    }

    // Recibe actualizaciones de ubicación desde MapSection (GPS o cámara del mapa)
    val updateLocation: (LatLng) -> Unit = { newLocation ->
        currentLocation = newLocation
        if (isTracking) {
            val lastPoint = routePoints.lastOrNull()
            if (lastPoint == null || shouldAddPoint(lastPoint, newLocation)) {
                routePoints.add(newLocation)
            }
        }
    }

    // ── Layout adaptativo: portrait = Column, landscape = Row ─────────────────
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (isLandscape) {
            // LANDSCAPE: mapa a la izquierda, cámara a la derecha
            val halfWidth = maxWidth / 2

            Row(modifier = Modifier.fillMaxSize()) {

                // Mapa (mitad izquierda)
                MapSection(
                    modifier        = Modifier
                        .width(halfWidth)
                        .fillMaxHeight(),
                    photos          = photos,
                    routePoints     = routePoints,
                    isTracking      = isTracking,
                    onStartTracking = startTracking,
                    onClear         = clearAll,
                    onLocationChange = updateLocation
                )

                // Divisor vertical
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(2.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                )

                // Cámara (mitad derecha) — o mensaje de permiso denegado
                if (cameraPermission.status.isGranted) {
                    CameraSection(
                        modifier        = Modifier
                            .width(halfWidth)
                            .fillMaxHeight(),
                        currentLocation = currentLocation,
                        photos          = photos,
                        onPhotoSaved    = { photo -> photos.add(photo) }
                    )
                } else {
                    PermissionDeniedSection(
                        modifier = Modifier
                            .width(halfWidth)
                            .fillMaxHeight(),
                        icon     = Icons.Default.NoPhotography,
                        message  = "Debes conceder permiso de cámara para usar esta sección.",
                        onRetry  = { cameraPermission.launchPermissionRequest() }
                    )
                }
            }

        } else {
            // PORTRAIT: cámara arriba, mapa abajo
            val halfHeight = maxHeight / 2

            Column(modifier = Modifier.fillMaxSize()) {

                // Cámara (mitad superior) — o mensaje de permiso denegado
                if (cameraPermission.status.isGranted) {
                    CameraSection(
                        modifier        = Modifier
                            .fillMaxWidth()
                            .height(halfHeight),
                        currentLocation = currentLocation,
                        photos          = photos,
                        onPhotoSaved    = { photo -> photos.add(photo) }
                    )
                } else {
                    PermissionDeniedSection(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(halfHeight),
                        icon     = Icons.Default.NoPhotography,
                        message  = "Debes conceder permiso de cámara para usar esta sección.",
                        onRetry  = { cameraPermission.launchPermissionRequest() }
                    )
                }

                HorizontalDivider(
                    thickness = 2.dp,
                    color     = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                )

                // Mapa (mitad inferior)
                MapSection(
                    modifier        = Modifier
                        .fillMaxWidth()
                        .height(halfHeight),
                    photos          = photos,
                    routePoints     = routePoints,
                    isTracking      = isTracking,
                    onStartTracking = startTracking,
                    onClear         = clearAll,
                    onLocationChange = updateLocation
                )
            }
        }
    }
}