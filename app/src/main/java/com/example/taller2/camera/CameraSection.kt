package com.example.taller2.camera

import android.annotation.SuppressLint
import android.util.Log
import android.widget.Toast
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.taller2.model.RoutePhoto
import com.example.taller2.util.createOutputOptions
import com.example.taller2.util.createPhotoName
import com.example.taller2.util.loadBitmap
import com.google.android.gms.maps.model.LatLng
import java.util.concurrent.Executors

@SuppressLint("UnusedBoxWithConstraintsScope")
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
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    val buttonScale by animateFloatAsState(
        targetValue = if (isCapturing) 0.85f else 1f,
        animationSpec = spring(),
        label = "captureButton"
    )

    BoxWithConstraints(modifier = modifier.background(Color.Black)) {
        val stripHeight = 95.dp
        val previewHeight =
            if (maxHeight > stripHeight) maxHeight - stripHeight else maxHeight

        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(previewHeight)
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { previewContext ->
                        PreviewView(previewContext).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }
                    },
                    update = { previewView ->
                        val providerFuture = ProcessCameraProvider.getInstance(context)

                        providerFuture.addListener({
                            val cameraProvider = providerFuture.get()

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
                                Log.e("CameraSection", "Error al iniciar la cámara", e)
                            }
                        }, ContextCompat.getMainExecutor(context))
                    }
                )

                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                ) {
                    IconButton(
                        onClick = {
                            lensFacing =
                                if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                    CameraSelector.LENS_FACING_FRONT
                                } else {
                                    CameraSelector.LENS_FACING_BACK
                                }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cameraswitch,
                            contentDescription = "Cambiar cámara",
                            tint = Color.White
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
                        text = "Cámara",
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
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

                                    Log.e("CameraSection", "Error al tomar la foto", exception)

                                    ContextCompat.getMainExecutor(context).execute {
                                        Toast.makeText(
                                            context,
                                            "No fue posible guardar la foto.",
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
                        .scale(buttonScale)
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

            PhotoRow(
                photos = photos,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(stripHeight)
            )
        }
    }
}

@Composable
private fun PhotoRow(
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
                items(
                    items = photos,
                    key = { photo -> photo.uri.toString() }
                ) { photo ->
                    PhotoItem(photo = photo)
                }
            }
        }
    }
}

@Composable
private fun PhotoItem(photo: RoutePhoto) {
    val context = LocalContext.current

    val bitmap by produceState<android.graphics.Bitmap?>(
        initialValue = null,
        key1 = photo.uri
    ) {
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
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
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