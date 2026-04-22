package com.example.taller2

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.NoPhotography
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
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
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executors

data class RoutePhoto(
    val name: String,
    val uri: Uri,
    val location: LatLng
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Taller2Theme {

                var isLoggedIn by remember { mutableStateOf(false) }

                if (isLoggedIn) {
                    MainScreen()
                } else {
                    LoginScreen(
                        onLoginSuccess = { isLoggedIn = true }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
    val locationPermissions = rememberMultiplePermissionsState(
        listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    val photos = remember { mutableStateListOf<RoutePhoto>() }
    val routePoints = remember { mutableStateListOf<LatLng>() }

    var currentLocation by remember { mutableStateOf<LatLng?>(null) }
    var isTracking by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!cameraPermission.status.isGranted) cameraPermission.launchPermissionRequest()
        if (!locationPermissions.allPermissionsGranted) {
            locationPermissions.launchMultiplePermissionRequest()
        }
    }

    val clearAll = {
        isTracking = false
        photos.clear()
        routePoints.clear()
        Toast.makeText(context, "Recorrido borrado.", Toast.LENGTH_SHORT).show()
    }

    val cameraContent: @Composable (Modifier) -> Unit = { modifier ->
        if (cameraPermission.status.isGranted) {
            CameraSection(
                modifier = modifier,
                currentLocation = currentLocation,
                photos = photos,
                onPhotoSaved = { photos.add(it) }
            )
        } else {
            PermissionDeniedSection(
                icon = Icons.Default.NoPhotography,
                message = "Debes conceder permiso de cámara para usar esta parte de la app.",
                onRetry = { cameraPermission.launchPermissionRequest() }
            )
        }
    }

    val mapContent: @Composable (Modifier) -> Unit = { modifier ->
        if (locationPermissions.allPermissionsGranted) {
            MapSection(
                modifier = modifier,
                photos = photos,
                routePoints = routePoints,
                isTracking = isTracking,
                onStartTracking = {
                    isTracking = true
                    currentLocation?.let {
                        if (routePoints.isEmpty()) routePoints.add(it)
                    }
                },
                onClear = clearAll,
                onLocationChange = { newLocation ->
                    currentLocation = newLocation
                    if (isTracking) {
                        val last = routePoints.lastOrNull()
                        if (last == null || shouldAddPoint(last, newLocation)) {
                            routePoints.add(newLocation)
                        }
                    }
                }
            )
        } else {
            PermissionDeniedSection(
                icon = Icons.Default.LocationOff,
                message = "Debes conceder permiso de ubicación para ver el mapa y registrar el recorrido.",
                onRetry = { locationPermissions.launchMultiplePermissionRequest() }
            )
        }
    }

    if (isLandscape) {
        Row(modifier = Modifier.fillMaxSize()) {
            mapContent(
                Modifier
                    .weight(1f)
                    .fillMaxSize()
            )

            Divider(
                modifier = Modifier
                    .fillMaxSize()
                    .width(2.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            )

            cameraContent(
                Modifier
                    .weight(1f)
                    .fillMaxSize()
            )
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            cameraContent(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )

            Divider(
                thickness = 2.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            )

            mapContent(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        }
    }
}

@Composable
fun CameraSection(
    modifier: Modifier,
    currentLocation: LatLng?,
    photos: List<RoutePhoto>,
    onPhotoSaved: (RoutePhoto) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var isCapturing by remember { mutableStateOf(false) }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose { cameraExecutor.shutdown() }
    }

    val scale by animateFloatAsState(
        targetValue = if (isCapturing) 0.85f else 1f,
        animationSpec = spring(),
        label = "captureButton"
    )

    Column(modifier = modifier.background(Color.Black)) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { previewContext ->
                    PreviewView(previewContext).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                },
                update = { previewView ->
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()

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
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                selector,
                                preview,
                                capture
                            )
                        } catch (e: Exception) {
                            Log.e("CameraSection", "Error al iniciar cámara", e)
                        }
                    }, ContextCompat.getMainExecutor(context))
                }
            )

            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
            ) {
                Text(
                    text = "Cámara",
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            IconButton(
                onClick = {
                    lensFacing =
                        if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                            CameraSelector.LENS_FACING_FRONT
                        } else {
                            CameraSelector.LENS_FACING_BACK
                        }
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f))
            ) {
                Icon(
                    imageVector = Icons.Default.Cameraswitch,
                    contentDescription = "Cambiar cámara",
                    tint = Color.White
                )
            }

            IconButton(
                onClick = {
                    val capture = imageCapture ?: return@IconButton

                    if (currentLocation == null) {
                        Toast.makeText(
                            context,
                            "Espera a que se obtenga la ubicación.",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@IconButton
                    }

                    isCapturing = true
                    val fileName = createPhotoName()
                    val outputOptions = createOutputOptions(context, fileName)

                    capture.takePicture(
                        outputOptions,
                        cameraExecutor,
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                isCapturing = false
                                val uri = output.savedUri

                                if (uri != null) {
                                    ContextCompat.getMainExecutor(context).execute {
                                        onPhotoSaved(
                                            RoutePhoto(
                                                name = fileName,
                                                uri = uri,
                                                location = currentLocation
                                            )
                                        )
                                        Toast.makeText(
                                            context,
                                            "Foto guardada en galería.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                } else {
                                    ContextCompat.getMainExecutor(context).execute {
                                        Toast.makeText(
                                            context,
                                            "No se pudo obtener la URI de la foto.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }

                            override fun onError(exception: ImageCaptureException) {
                                isCapturing = false
                                Log.e("CameraSection", "Error al tomar foto", exception)
                                ContextCompat.getMainExecutor(context).execute {
                                    Toast.makeText(
                                        context,
                                        "Error al guardar la foto.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    )
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 18.dp)
                    .size(72.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .border(3.dp, Color.White, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoCamera,
                    contentDescription = "Tomar foto",
                    tint = Color.White,
                    modifier = Modifier.size(34.dp)
                )
            }
        }

        PhotoList(
            photos = photos,
            modifier = Modifier
                .fillMaxWidth()
                .height(95.dp)
        )
    }
}

@SuppressLint("MissingPermission")
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
    val context = LocalContext.current
    var userLocation by remember { mutableStateOf<LatLng?>(null) }
    val cameraPositionState = rememberCameraPositionState()
    val routeColor = MaterialTheme.colorScheme.primary

    DisposableEffect(Unit) {
        val fusedClient = LocationServices.getFusedLocationProviderClient(context)

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L)
            .setMinUpdateIntervalMillis(1500L)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    val latLng = LatLng(location.latitude, location.longitude)
                    userLocation = latLng
                    onLocationChange(latLng)

                    cameraPositionState.move(
                        CameraUpdateFactory.newCameraPosition(
                            CameraPosition.fromLatLngZoom(latLng, 16f)
                        )
                    )
                }
            }
        }

        fusedClient.requestLocationUpdates(request, callback, null)

        onDispose {
            fusedClient.removeLocationUpdates(callback)
        }
    }

    Box(modifier = modifier) {
        if (userLocation == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Obteniendo ubicación...")
                }
            }
        } else {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(isMyLocationEnabled = true),
                uiSettings = MapUiSettings(
                    myLocationButtonEnabled = false,
                    zoomControlsEnabled = false,
                    compassEnabled = true
                )
            ) {
                if (routePoints.size >= 2) {
                    Polyline(
                        points = routePoints,
                        color = routeColor,
                        width = 12f
                    )
                }

                photos.forEach { photo ->
                    Marker(
                        state = MarkerState(position = photo.location),
                        title = photo.name,
                        snippet = "Foto del recorrido",
                        icon = BitmapDescriptorFactory.defaultMarker(
                            BitmapDescriptorFactory.HUE_GREEN
                        )
                    )
                }
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
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (isTracking) "En curso" else "Iniciar")
            }

            FilledTonalButton(
                onClick = onClear,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Borrar recorrido"
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Borrar")
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
                text = "Fotos: ${photos.size}  |  Puntos: ${routePoints.size}",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
fun PhotoList(
    photos: List<RoutePhoto>,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier) {
        if (photos.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Aquí aparecerán las fotos tomadas.",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontSize = 13.sp
                )
            }
        } else {
            LazyRow(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(photos, key = { it.uri.toString() }) { photo ->
                    PhotoItem(photo)
                }
            }
        }
    }
}

@Composable
fun PhotoItem(photo: RoutePhoto) {
    val context = LocalContext.current

    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = photo.uri) {
        value = loadBitmap(context, photo.uri)
    }

    Column(
        modifier = Modifier
            .padding(start = 10.dp, top = 8.dp, bottom = 8.dp)
            .width(82.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = photo.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = photo.name,
            maxLines = 1,
            fontSize = 10.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun PermissionDeniedSection(
    icon: ImageVector,
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(34.dp)
                )
            }

            Text(
                text = message,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.error,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )

            Button(onClick = onRetry) {
                Text("Conceder permiso")
            }
        }
    }
}

fun createPhotoName(): String {
    val time = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        .format(System.currentTimeMillis())
    return "RouteShot_$time.jpg"
}

fun createOutputOptions(
    context: Context,
    fileName: String
): ImageCapture.OutputFileOptions {
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/RouteShot"
            )
        }
    }

    return ImageCapture.OutputFileOptions.Builder(
        context.contentResolver,
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        values
    ).build()
}

suspend fun loadBitmap(context: Context, uri: Uri): Bitmap? {
    return withContext(Dispatchers.IO) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source)
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
        }.getOrNull()
    }
}

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