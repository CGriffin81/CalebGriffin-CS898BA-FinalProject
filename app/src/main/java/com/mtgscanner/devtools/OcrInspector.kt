package com.mtgscanner.devtools

import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * OCR Inspector Panel — displays detailed inspection data for a selected region.
 *
 * Shows:
 * - Cropped bitmap (exact pixels sent to ML Kit)
 * - ML Kit OCR result text
 * - Bounding boxes from ML Kit (per text block and per line)
 * - Confidence (per-block)
 * - Processing time
 * - Save crop as PNG button for offline inspection
 *
 * @param regionBitmap The exact bitmap crop that would be sent to OCR (null = no selection)
 * @param regionName Name of the selected region (e.g., "Name", "Collector")
 * @param onDismiss Close the inspector panel
 */
@Composable
fun OcrInspectorPanel(
    regionBitmap: Bitmap?,
    regionName: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (regionBitmap == null) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Inspection state
    var inspectionResult by remember { mutableStateOf<InspectionResult?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var saveMessage by remember { mutableStateOf<String?>(null) }

    // Run OCR inspection on the bitmap
    LaunchedEffect(regionBitmap) {
        isProcessing = true
        inspectionResult = inspectRegion(regionBitmap)
        isProcessing = false
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.6f)
            .background(Color(0xFF1A1A1A), RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("OCR INSPECTOR: $regionName", color = Color.Cyan, fontSize = 11.sp,
                fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Save crop button
                Text("💾 Save", color = Color.Green, fontSize = 10.sp,
                    modifier = Modifier.clickable {
                        scope.launch {
                            val path = saveCropAsPng(context, regionBitmap, regionName)
                            saveMessage = path
                        }
                    })
                // Close
                Text("✕", color = Color.Red, fontSize = 14.sp,
                    modifier = Modifier.clickable { onDismiss() })
            }
        }

        // Save confirmation
        saveMessage?.let {
            Text("Saved: $it", color = Color.Green.copy(alpha = 0.7f), fontSize = 8.sp)
        }

        // Bitmap preview
        Text("Crop: ${regionBitmap.width}×${regionBitmap.height} px",
            color = Color.Gray, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        Image(
            bitmap = regionBitmap.asImageBitmap(),
            contentDescription = "Region crop",
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .background(Color.DarkGray, RoundedCornerShape(4.dp)),
            contentScale = ContentScale.Fit
        )

        // OCR Results
        if (isProcessing) {
            Text("Processing...", color = Color.Yellow, fontSize = 10.sp)
        }

        inspectionResult?.let { result ->
            // Timing
            Text("⏱ Processing time: ${result.processingTimeMs}ms",
                color = Color.White, fontSize = 9.sp, fontFamily = FontFamily.Monospace)

            // Full recognized text
            Text("OCR Text:", color = Color.Cyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text(
                text = result.fullText.ifEmpty { "[empty — no text recognized]" },
                color = if (result.fullText.isNotEmpty()) Color.Green else Color.Red,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black, RoundedCornerShape(4.dp))
                    .padding(6.dp)
            )

            // ML Kit blocks with bounding boxes
            Text("ML Kit Blocks (${result.blocks.size}):",
                color = Color.Cyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)

            result.blocks.forEachIndexed { blockIdx, block ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF2A2A2A), RoundedCornerShape(4.dp))
                        .padding(4.dp)
                ) {
                    Text("Block $blockIdx: '${block.text.take(40)}'",
                        color = Color.White, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    Text("  bbox: ${block.boundingBox}",
                        color = Color.Gray, fontSize = 8.sp, fontFamily = FontFamily.Monospace)

                    block.lines.forEachIndexed { lineIdx, line ->
                        Text("  Line $lineIdx: '${line.text}' bbox=${line.boundingBox}",
                            color = Color.White.copy(alpha = 0.8f), fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace)
                    }
                }
            }

            // Summary
            Text("Total: ${result.blocks.size} blocks, ${result.lineCount} lines, " +
                "${result.fullText.length} chars",
                color = Color.Gray, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

/**
 * Run ML Kit OCR on a bitmap and return detailed inspection data.
 */
private suspend fun inspectRegion(bitmap: Bitmap): InspectionResult = withContext(Dispatchers.Default) {
    val startTime = System.currentTimeMillis()

    return@withContext try {
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val result = recognizer.process(image).await()
        val elapsed = System.currentTimeMillis() - startTime

        val blocks = result.textBlocks.map { block ->
            InspectionBlock(
                text = block.text,
                boundingBox = block.boundingBox?.let {
                    "L=${it.left} T=${it.top} R=${it.right} B=${it.bottom}"
                } ?: "null",
                lines = block.lines.map { line ->
                    InspectionLine(
                        text = line.text,
                        boundingBox = line.boundingBox?.let {
                            "L=${it.left} T=${it.top} R=${it.right} B=${it.bottom}"
                        } ?: "null"
                    )
                }
            )
        }

        InspectionResult(
            fullText = result.text,
            blocks = blocks,
            lineCount = result.textBlocks.sumOf { it.lines.size },
            processingTimeMs = elapsed
        )
    } catch (e: Exception) {
        Log.e("OcrInspector", "Inspection failed: ${e.message}", e)
        InspectionResult(
            fullText = "ERROR: ${e.message}",
            blocks = emptyList(),
            lineCount = 0,
            processingTimeMs = System.currentTimeMillis() - startTime
        )
    }
}

/**
 * Save a bitmap crop as PNG for offline inspection.
 * Files are saved to the app's external files directory.
 */
private suspend fun saveCropAsPng(context: Context, bitmap: Bitmap, regionName: String): String =
    withContext(Dispatchers.IO) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val filename = "ocr_crop_${regionName}_${timestamp}.png"
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            ?: context.filesDir
        val file = File(dir, filename)

        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        Log.d("OcrInspector", "Saved crop: ${file.absolutePath} (${bitmap.width}×${bitmap.height})")
        file.absolutePath
    }

private data class InspectionResult(
    val fullText: String,
    val blocks: List<InspectionBlock>,
    val lineCount: Int,
    val processingTimeMs: Long
)

private data class InspectionBlock(
    val text: String,
    val boundingBox: String,
    val lines: List<InspectionLine>
)

private data class InspectionLine(
    val text: String,
    val boundingBox: String
)
