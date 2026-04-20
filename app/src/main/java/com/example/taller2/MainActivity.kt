package com.example.taller2

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.NoPhotography
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.taller2.ui.theme.Taller2Theme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executors

// ═════════════════════════════════════════════════════════════════════════════
// ACTIVIDAD PRINCIPAL
// ═════════════════════════════════════════════════════════════════════════════

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Taller2Theme {
                MainScreen()
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// PANTALLA PRINCIPAL
// Divide la pantalla en dos mitades iguales: cámara (arriba) y mapa (abajo).
// Gestiona el estado de permisos con Accompanist y la ubicación compartida.
// ═════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainScreen() {
    // Lista de coordenadas donde el usuario tomó fotos → se pasa al mapa
    val photoLocations = remember { mutableStateListOf<LatLng>() }

    // Permisos individuales con Accompanist
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
    val locationPermissions = rememberMultiplePermissionsState(
        listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    // Solicitar todos los permisos al iniciar la app
    LaunchedEffect(Unit) {
        if (!cameraPermission.status.isGranted) {
            cameraPermission.launchPermissionRequest()
        }
        if (!locationPermissions.allPermissionsGranted) {
            locationPermissions.launchMultiplePermissionRequest()
        }
    }

    // Última ubicación conocida, compartida entre CameraRegion y MapRegion
    var currentLatLng by remember { mutableStateOf<LatLng?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {

        // ── REGIÓN SUPERIOR: CÁMARA (50 % de la pantalla) ─────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (cameraPermission.status.isGranted) {
                CameraRegion(
                    currentLatLng = currentLatLng,
                    onPhotoTaken  = { latLng -> latLng?.let { photoLocations.add(it) } }
                )
            } else {
                PermissionDeniedRegion(
                    icon    = Icons.Default.NoPhotography,
                    message = stringResource(R.string.camera_permission_denied),
                    onRetry = { cameraPermission.launchPermissionRequest() }
                )
            }
        }

        // ── DIVISOR ENTRE REGIONES ─────────────────────────────────────────
        HorizontalDivider(
            thickness = 2.dp,
            color     = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )

        // ── REGIÓN INFERIOR: MAPA (50 % de la pantalla) ───────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (locationPermissions.allPermissionsGranted) {
                MapRegion(
                    photoLocations   = photoLocations,
                    onLocationUpdate = { latLng -> currentLatLng = latLng }
                )
            } else {
                PermissionDeniedRegion(
                    icon    = Icons.Default.LocationOff,
                    message = stringResource(R.string.location_permission_denied),
                    onRetry = { locationPermissions.launchMultiplePermissionRequest() }
                )
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// REGIÓN DE CÁMARA
// Muestra el preview de CameraX con:
//   • Botón flip (cámara trasera ↔ frontal)
//   • Botón de captura animado
//   • Etiqueta de sección
// Al tomar una foto, guarda el archivo y registra la LatLng actual.
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun CameraRegion(
    currentLatLng: LatLng?,
    onPhotoTaken:  (LatLng?) -> Unit
) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var lensFacing   by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var isCapturing  by remember { mutableStateOf(false) }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { cameraExecutor.shutdown() } }

    val outputDir = remember { getOutputDirectory(context) }

    // Escala animada del botón de captura (efecto "presionado")
    val captureScale by animateFloatAsState(
        targetValue   = if (isCapturing) 0.82f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label         = "captureScale"
    )

    Box(modifier = Modifier.fillMaxSize()) {

        // ── Preview de la cámara ───────────────────────────────────────────
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory  = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            },
            update = { previewView ->
                val future = ProcessCameraProvider.getInstance(context)
                future.addListener({
                    val provider = future.get()

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val capture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()
                    imageCapture = capture

                    val selector = CameraSelector.Builder()
                        .requireLensFacing(lensFacing)
                        .build()

                    try {
                        provider.unbindAll()
                        provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                    } catch (e: Exception) {
                        Log.e("CameraRegion", "Error al enlazar cámara: ${e.message}", e)
                    }
                }, ContextCompat.getMainExecutor(context))
            }
        )

        // ── Botón flip (esquina superior izquierda) ────────────────────────
        IconButton(
            onClick  = {
                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK)
                    CameraSelector.LENS_FACING_FRONT
                else
                    CameraSelector.LENS_FACING_BACK
            },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.82f))
        ) {
            Icon(
                imageVector        = Icons.Default.Cameraswitch,
                contentDescription = stringResource(R.string.flip_camera),
                tint               = Color.White,
                modifier           = Modifier.size(24.dp)
            )
        }

        // ── Etiqueta de sección (esquina superior derecha) ─────────────────
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.82f)
        ) {
            Text(
                text     = stringResource(R.string.camera_section),
                color    = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }

        // ── Botón de captura (borde inferior, centrado) ────────────────────
        IconButton(
            onClick  = {
                val capture = imageCapture ?: return@IconButton
                isCapturing = true

                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                    .format(System.currentTimeMillis())
                val photoFile = File(outputDir, "RouteShot_$timestamp.jpg")

                val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

                capture.takePicture(
                    outputOptions,
                    cameraExecutor,
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            isCapturing = false
                            onPhotoTaken(currentLatLng)
                            ContextCompat.getMainExecutor(context).execute {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.photo_saved),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }

                        override fun onError(exc: ImageCaptureException) {
                            isCapturing = false
                            Log.e("CameraRegion", "Error captura: ${exc.message}", exc)
                            ContextCompat.getMainExecutor(context).execute {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.photo_error),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                )
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp)
                .size(70.dp)
                .scale(captureScale)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .border(3.dp, Color.White, CircleShape)
        ) {
            Icon(
                imageVector        = Icons.Default.PhotoCamera,
                contentDescription = stringResource(R.string.take_photo),
                tint               = Color.White,
                modifier           = Modifier.size(34.dp)
            )
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// REGIÓN DE MAPA
// Muestra Google Maps con:
//   • Punto azul de ubicación actual (isMyLocationEnabled = true)
//   • Marcadores verdes en cada ubicación donde se tomó una foto
//   • Contador de fotos en la esquina inferior izquierda
//   • Seguimiento de la cámara del mapa a la posición del usuario
// ═════════════════════════════════════════════════════════════════════════════

@SuppressLint("MissingPermission")
@Composable
fun MapRegion(
    photoLocations:   List<LatLng>,
    onLocationUpdate: (LatLng) -> Unit
) {
    val context = LocalContext.current
    var userLocation by remember { mutableStateOf<LatLng?>(null) }
    val cameraPositionState = rememberCameraPositionState()

    // Actualizaciones de ubicación con FusedLocationProvider
    DisposableEffect(Unit) {
        val fusedClient = LocationServices.getFusedLocationProviderClient(context)

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3_000L)
            .setMinUpdateIntervalMillis(1_500L)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    val latLng = LatLng(loc.latitude, loc.longitude)
                    userLocation = latLng
                    onLocationUpdate(latLng)
                    // Seguir al usuario con la cámara del mapa
                    cameraPositionState.move(
                        CameraUpdateFactory.newCameraPosition(
                            CameraPosition.fromLatLngZoom(latLng, 16f)
                        )
                    )
                }
            }
        }

        fusedClient.requestLocationUpdates(request, callback, null)
        onDispose { fusedClient.removeLocationUpdates(callback) }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        if (userLocation == null) {
            // Pantalla de carga mientras obtenemos la primera ubicación
            Box(
                modifier         = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp
                    )
                    Text(
                        text     = stringResource(R.string.waiting_location),
                        color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            // Mapa principal
            GoogleMap(
                modifier            = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties          = MapProperties(
                    isMyLocationEnabled = true   // punto azul nativo de Maps
                ),
                uiSettings = MapUiSettings(
                    myLocationButtonEnabled = false, // usamos la cámara automática
                    zoomControlsEnabled     = false,
                    compassEnabled          = true
                )
            ) {
                // Marcador verde por cada foto tomada
                photoLocations.forEachIndexed { index, latLng ->
                    Marker(
                        state   = MarkerState(position = latLng),
                        title   = stringResource(R.string.photo_marker),
                        snippet = "Foto #${index + 1}",
                        icon    = BitmapDescriptorFactory.defaultMarker(
                            BitmapDescriptorFactory.HUE_GREEN
                        )
                    )
                }
            }
        }

        // ── Etiqueta de sección (esquina superior derecha) ─────────────────
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
        ) {
            Text(
                text       = stringResource(R.string.map_section),
                color      = Color.White,
                fontSize   = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier   = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }

        // ── Contador de fotos (esquina inferior izquierda) ──────────────────
        if (photoLocations.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp),
                shape           = RoundedCornerShape(20.dp),
                color           = MaterialTheme.colorScheme.secondary.copy(alpha = 0.92f),
                shadowElevation = 4.dp
            ) {
                Text(
                    text       = stringResource(R.string.photos_count, photoLocations.size),
                    color      = Color.White,
                    fontSize   = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier   = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// REGIÓN DE PERMISO DENEGADO
// Composable reutilizable que se muestra en lugar de la cámara o del mapa
// cuando el usuario no concedió el permiso correspondiente.
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun PermissionDeniedRegion(
    icon:    ImageVector,
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier         = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier            = Modifier.padding(36.dp)
        ) {
            // Ícono dentro de un círculo con fondo rojo suave
            Box(
                modifier         = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = icon,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.error,
                    modifier           = Modifier.size(34.dp)
                )
            }

            // Mensaje descriptivo
            Text(
                text       = message,
                color      = MaterialTheme.colorScheme.error,
                fontSize   = 14.sp,
                textAlign  = TextAlign.Center,
                lineHeight = 21.sp
            )

            // Botón para reintentar solicitar el permiso
            Button(
                onClick = onRetry,
                shape   = RoundedCornerShape(24.dp),
                colors  = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text  = stringResource(R.string.grant_permission),
                    color = Color.White
                )
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// UTILIDADES
// ═════════════════════════════════════════════════════════════════════════════

/**
 * Devuelve el directorio donde se guardarán las fotos.
 * Preferencia: almacenamiento externo privado de la app.
 * Fallback: directorio interno de caché.
 */
private fun getOutputDirectory(context: Context): File {
    val mediaDir = context.getExternalFilesDirs(null)
        .firstOrNull()
        ?.let { File(it, "RouteShot").also { dir -> dir.mkdirs() } }
    return if (mediaDir != null && mediaDir.exists()) mediaDir else context.filesDir
}