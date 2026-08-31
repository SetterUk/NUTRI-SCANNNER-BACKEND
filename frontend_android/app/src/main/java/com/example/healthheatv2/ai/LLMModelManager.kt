package com.example.healthheatv2.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Detects whether a Gemma 4 E2B model file has been sideloaded on the device.
 * Used as a pre-check before GemmaInferenceManager attempts ML Kit GenAI init.
 *
 * Supported sideload paths (for ADB testing):
 *   adb push gemma4-e2b-it.task /data/local/tmp/gemma4-e2b-it.task
 *   adb push gemma4-e2b-it.task /sdcard/Download/gemma4-e2b-it.task
 */
class LLMModelManager(private val context: Context) {

    // Ordered list of Gemma candidate file names (most preferred first)
    private val gemma4Candidates = listOf(
        "gemma-2b-it-gpu-int4.bin",
        "gemma-2b-it-cpu-int4.bin",
        "gemma4-e2b-it.task",
        "gemma4-e2b-it-int4.bin",
        "gemma4-e2b.task",
        "gemma4-e2b.bin",
        "gemma-2b-it.task",
        "gemma-2b-it.bin",
        "gemma2-2b-it.bin",
        "gemma2-2b-it-gpu-int4.bin",
        "gemma-1b-it.task"
    )

    // Ordered list of directories to scan for sideloaded model files
    private val searchDirectories: List<File> by lazy {
        listOf(
            context.filesDir,                               // /data/data/com.example.healthheatv2/files
            File(context.filesDir, "llm_models"),           // app private storage subdir
            context.cacheDir,                               // app cache
            File(context.cacheDir, "llm_models"),           // app cache subdir
            File("/data/local/tmp"),                        // ADB sideload
            context.getExternalFilesDir(null),              // external app storage
            File(context.getExternalFilesDir(null), "llm_models")
        ).filterNotNull()
    }

    data class ModelInfo(
        val file: File,
        val name: String,
        val sizeMb: Float
    )

    /**
     * Scans all known directories for a valid Gemma 4 E2B (or compatible) model file.
     * @return [ModelInfo] if found, null otherwise.
     */
    suspend fun findSideloadedModel(): ModelInfo? = withContext(Dispatchers.IO) {
        for (dir in searchDirectories) {
            if (!dir.exists()) continue
            for (candidate in gemma4Candidates) {
                val file = File(dir, candidate)
                if (file.exists() && file.length() > 1_000_000L) { // must be > 1MB (not a dummy)
                    val sizeMb = file.length() / (1024f * 1024f)
                    Log.d(TAG, "✅ Found sideloaded model: ${file.absolutePath} (${sizeMb.toInt()} MB)")
                    return@withContext ModelInfo(file, candidate, sizeMb)
                }
            }
        }
        Log.d(TAG, "No sideloaded model file found.")
        return@withContext null
    }

    /**
     * Returns the recommended ADB command the developer can use to sideload the model.
     */
    fun getAdbSideloadCommand(): String =
        "adb push gemma4-e2b-it.task /data/local/tmp/gemma4-e2b-it.task"

    companion object {
        private const val TAG = "LLMModelManager"
    }
}
