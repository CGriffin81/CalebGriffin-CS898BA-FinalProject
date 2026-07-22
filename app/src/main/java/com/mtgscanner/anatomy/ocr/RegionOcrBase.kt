package com.mtgscanner.anatomy.ocr

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Shared ML Kit text recognition utility for all region OCR readers.
 *
 * Each reader delegates the raw OCR call here, then applies its own
 * domain-specific parsing to the recognized [Text] object.
 *
 * The recognizer instance is shared (singleton) across all readers for efficiency.
 */
object MlKitRecognizer {

    private const val TAG = "MlKitRecognizer"

    private val textRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Run ML Kit text recognition on a bitmap region.
     *
     * @param regionBitmap Cropped bitmap for a single card region.
     * @return Recognized [Text] object, or null if recognition produced no text.
     */
    suspend fun recognize(regionBitmap: Bitmap): Text? = withContext(Dispatchers.Default) {
        return@withContext try {
            if (regionBitmap.isRecycled || regionBitmap.width < 2 || regionBitmap.height < 2) {
                Log.w(TAG, "Invalid bitmap: recycled=${regionBitmap.isRecycled} " +
                    "size=${regionBitmap.width}x${regionBitmap.height}")
                return@withContext null
            }
            val image = InputImage.fromBitmap(regionBitmap, 0)
            val result = textRecognizer.process(image).await()
            if (result.text.isEmpty()) null else result
        } catch (e: Exception) {
            Log.e(TAG, "ML Kit recognition failed: ${e.message}", e)
            null
        }
    }

    /** Extract all text as a single trimmed string. */
    fun textOf(result: Text?): String = result?.text?.trim() ?: ""

    /** Extract all lines as trimmed non-empty strings. */
    fun linesOf(result: Text?): List<String> =
        result?.text?.split('\n')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
}
