package com.example.awaaz.speech

import android.content.Context
import org.vosk.Model
import org.vosk.android.StorageService

/**
 * Loads the Vosk speech recognition model from `assets/model/`.
 *
 * **Asset layout:** Place the unpacked Vosk model so that:
 * - `src/main/assets/model/am/` (e.g. `final.mdl`)
 * - `src/main/assets/model/conf/`
 * - `src/main/assets/model/graph/`
 * - etc.
 *
 * Download a model from https://alphacephei.com/vosk/models, unzip it, and put its
 * contents inside a folder named `model` under `src/main/assets/`.
 *
 * **Usage:**
 * ```
 * VoskModelLoader.load(context, "model",
 *     onReady = { model -> /* use model with Recognizer */ },
 *     onError = { e -> /* handle error */ }
 * )
 * ```
 *
 * Or use [VoskSpeechRecognizer.loadModelFromAssets] which uses this path internally.
 */
object VoskModelLoader {

    /** Default path for the model in assets: `assets/model/` */
    const val ASSETS_MODEL_PATH = "model"

    /**
     * Loads the Vosk model from assets. Unpacks [assetFolderName] from assets to internal
     * storage and creates a [Model] instance. Callbacks run on the main thread.
     *
     * @param context Application or activity context.
     * @param assetFolderName Folder name under `assets/` containing the model (e.g. "model" for `assets/model/`).
     * @param onReady Called with the loaded [Model] when ready.
     * @param onError Called if unpacking or loading fails.
     */
    @JvmStatic
    fun load(
        context: Context,
        assetFolderName: String = ASSETS_MODEL_PATH,
        onReady: (Model) -> Unit,
        onError: (Exception) -> Unit
    ) {
        StorageService.unpack(
            context,
            assetFolderName,
            "model",
            { model ->
                onReady(model)
            },
            { e ->
                val ex = e ?: Exception("Failed to load Vosk model from assets/$assetFolderName")
                onError(ex)
            }
        )
    }

    /**
     * Loads the Vosk model from `assets/model/`. Convenience for the default path.
     */
    @JvmStatic
    fun loadFromAssetsModel(
        context: Context,
        onReady: (Model) -> Unit,
        onError: (Exception) -> Unit
    ) {
        load(context, ASSETS_MODEL_PATH, onReady, onError)
    }
}
