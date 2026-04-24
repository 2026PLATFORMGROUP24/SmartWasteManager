package com.platform.smartwastemanager.features.askai.data

import android.content.Context
import android.util.Log
import com.google.firebase.storage.FirebaseStorage
import com.platform.smartwastemanager.core.util.Constants
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.File

/**
 * Sealed state emitted by [ModelDownloadManager.downloadModel].
 */
sealed class ModelDownloadState {
    /** Initial state — not yet started. */
    object Idle : ModelDownloadState()

    /** Download is in progress. [progress] is 0–100. */
    data class Downloading(val progress: Int) : ModelDownloadState()

    /** Model file is ready at [path]. */
    data class Ready(val path: String) : ModelDownloadState()

    /** An error occurred. */
    data class Error(val message: String) : ModelDownloadState()
}

/**
 * Manages the one-time download of the SmoLLM2-360M GGUF model from Firebase Storage
 * to the app's internal files directory.
 *
 * Storage path : [Constants.AI_MODEL_STORAGE_PATH]
 * Local path   : {filesDir}/models/[Constants.AI_MODEL_FILE_NAME]
 */
class ModelDownloadManager(private val context: Context) {

    private val TAG = "ModelDownloadManager"

    /** Absolute path to the local model file (may or may not exist yet). */
    val modelFilePath: String
        get() = File(context.filesDir, "models/${Constants.AI_MODEL_FILE_NAME}").absolutePath

    /**
     * Returns true if the model has already been fully downloaded and appears valid
     * (file exists and is at least [Constants.AI_MODEL_MIN_SIZE_BYTES]).
     * Also deletes any legacy/incompatible model files from previous versions.
     */
    fun isModelDownloaded(): Boolean {
        // Clean up any legacy model files (e.g. old Q8_0 download which caused SIGSEGV)
        val modelsDir = File(context.filesDir, "models")
        Constants.AI_MODEL_LEGACY_FILE_NAMES.forEach { legacyName ->
            val legacyFile = File(modelsDir, legacyName)
            if (legacyFile.exists()) {
                Log.i(TAG, "Deleting legacy/incompatible model file: $legacyName")
                legacyFile.delete()
            }
        }
        val file = File(modelFilePath)
        return file.exists() && file.length() >= Constants.AI_MODEL_MIN_SIZE_BYTES
    }

    /**
     * Downloads the model from Firebase Storage if it is not already present.
     *
     * Emits:
     *  - [ModelDownloadState.Ready]        immediately if already downloaded
     *  - [ModelDownloadState.Downloading]  with 0–100 progress during download
     *  - [ModelDownloadState.Ready]        when download completes successfully
     *  - [ModelDownloadState.Error]        if download fails
     */
    fun downloadModel(): Flow<ModelDownloadState> = callbackFlow {
        if (isModelDownloaded()) {
            Log.d(TAG, "Model already present at $modelFilePath")
            trySend(ModelDownloadState.Ready(modelFilePath))
            close()
            return@callbackFlow
        }

        trySend(ModelDownloadState.Downloading(0))

        // Ensure the models directory exists
        val modelsDir = File(context.filesDir, "models")
        if (!modelsDir.exists()) modelsDir.mkdirs()

        val modelFile = File(modelsDir, Constants.AI_MODEL_FILE_NAME)

        val storageRef = FirebaseStorage.getInstance()
            .reference
            .child(Constants.AI_MODEL_STORAGE_PATH)

        Log.d(TAG, "Starting download from ${Constants.AI_MODEL_STORAGE_PATH}")

        val task = storageRef.getFile(modelFile)

        task.addOnProgressListener { snapshot ->
            val total = snapshot.totalByteCount
            val transferred = snapshot.bytesTransferred
            val progress = if (total > 0) ((transferred * 100.0) / total).toInt() else 0
            Log.d(TAG, "Download progress: $progress% ($transferred / $total bytes)")
            trySend(ModelDownloadState.Downloading(progress))
        }

        task.addOnSuccessListener {
            Log.d(TAG, "Download complete: ${modelFile.length()} bytes")
            trySend(ModelDownloadState.Ready(modelFilePath))
            close()
        }

        task.addOnFailureListener { e ->
            Log.e(TAG, "Download failed: ${e.message}", e)
            // Remove partial file so the next attempt starts clean
            if (modelFile.exists()) modelFile.delete()
            trySend(ModelDownloadState.Error(e.message ?: "Download failed. Please try again."))
            close()
        }

        awaitClose {
            // Cancel the Firebase Storage task if the flow is cancelled
            task.cancel()
        }
    }
}

