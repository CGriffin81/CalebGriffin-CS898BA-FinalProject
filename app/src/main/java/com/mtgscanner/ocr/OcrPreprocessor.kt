package com.mtgscanner.ocr

import android.graphics.Bitmap
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc

/**
 * Image preprocessing for OCR to improve text recognition accuracy.
 * Handles contrast enhancement, perspective correction, and region extraction.
 */
class OcrPreprocessor {

    /**
     * Preprocess card image to improve OCR accuracy.
     * Applies contrast enhancement, grayscale conversion, and sharpening.
     * @param cardBitmap Input card image.
     * @return Preprocessed bitmap optimized for OCR.
     */
    fun preprocessForOcr(cardBitmap: Bitmap): Bitmap {
        // Convert bitmap to OpenCV Mat
        val mat = Mat()
        org.opencv.android.Utils.bitmapToMat(cardBitmap, mat)
        
        // Convert to grayscale
        val gray = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY)
        
        // Enhance contrast using CLAHE (Contrast Limited Adaptive Histogram Equalization)
        val clahe = org.opencv.imgproc.CLAHE()
        clahe.clipLimit = 2.0
        clahe.tileGridSize = org.opencv.core.Size(8.0, 8.0)
        val enhanced = Mat()
        clahe.apply(gray, enhanced)
        
        // Apply Gaussian blur to reduce noise
        val blurred = Mat()
        Imgproc.GaussianBlur(enhanced, blurred, org.opencv.core.Size(3.0, 3.0), 0.0)
        
        // Sharpen to enhance text edges
        val sharpened = Mat()
        val kernel = org.opencv.core.Mat(3, 3, org.opencv.core.CvType.CV_32F)
        kernel.put(0, 0, 0.0, -1.0, 0.0)
        kernel.put(1, 0, -1.0, 5.0, -1.0)
        kernel.put(2, 0, 0.0, -1.0, 0.0)
        Imgproc.filter2D(blurred, sharpened, -1, kernel)
        
        // Convert back to bitmap
        val result = Bitmap.createBitmap(cardBitmap.width, cardBitmap.height, Bitmap.Config.ARGB_8888)
        org.opencv.android.Utils.matToBitmap(sharpened, result)
        
        // Clean up
        mat.release()
        gray.release()
        enhanced.release()
        blurred.release()
        sharpened.release()
        kernel.release()
        
        return result
    }

    /**
     * Extract specific regions of the card for focused OCR.
     * Magic cards have distinct regions: name area (top), type/text (middle), collector info (bottom).
     */
    fun extractCardRegions(cardBitmap: Bitmap): CardRegions {
        val height = cardBitmap.height
        val width = cardBitmap.width
        
        // Rough region estimates (adjust based on actual card proportions)
        val nameRegion = Bitmap.createBitmap(cardBitmap, 0, 0, width, (height * 0.15).toInt())
        val typeRegion = Bitmap.createBitmap(cardBitmap, 0, (height * 0.4).toInt(), width, (height * 0.25).toInt())
        val collectorRegion = Bitmap.createBitmap(cardBitmap, 0, (height * 0.85).toInt(), width, (height * 0.15).toInt())
        
        return CardRegions(
            nameRegion = nameRegion,
            typeRegion = typeRegion,
            collectorRegion = collectorRegion
        )
    }

    /**
     * Card image split into regions for targeted OCR.
     */
    data class CardRegions(
        val nameRegion: Bitmap,
        val typeRegion: Bitmap,
        val collectorRegion: Bitmap
    )
}
