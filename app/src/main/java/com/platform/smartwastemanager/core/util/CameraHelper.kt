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
import java.util.concurrent.Executor

/**
 * Binds CameraX use cases to the given [lifecycleOwner].
 *
 * Keeping all ProcessCameraProvider / ListenableFuture calls here —
 * in a plain Kotlin file with no Compose imports — resolves the classpath
 * conflict that causes "Cannot access ListenableFuture" in ScanScreen.kt.
 *
 * ProcessCameraProvider is part of camera-lifecycle which IS on the classpath.
 * The ListenableFuture type only appears at the call site of getInstance(), so
 * isolating that call here prevents the Compose compiler from encountering it.
 *
 * @param context          Application/activity context.
 * @param lifecycleOwner   The lifecycle to bind the camera to.
 * @param previewView      The view that renders the camera stream.
 * @param onCaptureBound   Called with the [ImageCapture] use case once the camera is ready.
 */
fun bindCameraToLifecycle(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView,
    onCaptureBound: (ImageCapture) -> Unit
) {
    val mainExecutor: Executor = ContextCompat.getMainExecutor(context)

    // getInstance() returns ListenableFuture<ProcessCameraProvider>.
    // This is the ONLY place in the project where ListenableFuture is referenced.
    // Because this file has no Compose imports, the conflict does not occur.
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

                // Notify ScanScreen that the ImageCapture use case is ready
                onCaptureBound(imageCapture)

            } catch (e: Exception) {
                Log.e("CameraHelper", "Camera bind failed", e)
            }
        },
        mainExecutor
    )
}