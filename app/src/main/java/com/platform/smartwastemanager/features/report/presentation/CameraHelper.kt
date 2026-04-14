package com.platform.smartwastemanager.features.report.presentation

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner

/**
 * Binds CameraX use cases to the given [lifecycleOwner].
 *
 * Isolating ProcessCameraProvider here (away from Compose files) avoids
 * the "Cannot access ListenableFuture" compiler conflict that occurs when
 * CameraX and Compose code share the same file.
 *
 * @param context          Application or Activity context.
 * @param lifecycleOwner   The lifecycle owner to bind the camera to.
 * @param previewView      The PreviewView that displays the camera stream.
 * @param onCaptureBound   Called with the ready [ImageCapture] use case.
 */
fun bindCameraToLifecycle(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView,
    onCaptureBound: (ImageCapture) -> Unit
) {
    val mainExecutor = ContextCompat.getMainExecutor(context)
    val future = ProcessCameraProvider.getInstance(context)

    future.addListener(
        {
            try {
                val cameraProvider: ProcessCameraProvider = future.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
                )

                onCaptureBound(imageCapture)

            } catch (e: Exception) {
                Log.e("CameraHelper", "Camera bind failed", e)
            }
        },
        mainExecutor
    )
}