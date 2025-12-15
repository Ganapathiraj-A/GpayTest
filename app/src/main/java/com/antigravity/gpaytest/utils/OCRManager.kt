package com.antigravity.gpaytest.utils

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import java.util.regex.Pattern

data class ParsedTransaction(
    val transactionId: String? = null,
    val amount: String? = null,
    val rawText: String = ""
)

object OCRManager {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    // Regex Patterns
    // 12-digit UTR/Ref No. Matches standalone 12 digits or labeled ones.
    private val UTR_PATTERN = Pattern.compile("\\b\\d{12}\\b")
    
    // Amount Pattern: Matches ₹ 50, ₹50.00, 50.00 with optional currency symbol
    private val AMOUNT_PATTERN = Pattern.compile("[₹Rs.]?\\s?([\\d,]+\\.?\\d*)")

    suspend fun processImage(context: Context, imageUri: Uri): ParsedTransaction {
        val bitmap = try {
            MediaStore.Images.Media.getBitmap(context.contentResolver, imageUri)
        } catch (e: Exception) {
            return ParsedTransaction(rawText = "Error loading image: ${e.message}")
        }
        return processBitmap(bitmap)
    }

    suspend fun processBitmap(bitmap: Bitmap): ParsedTransaction {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val result = recognizer.process(image).await()
            val text = result.text
            
            parseText(text)
        } catch (e: Exception) {
            ParsedTransaction(rawText = "OCR Failed: ${e.message}")
        }
    }

    private fun parseText(text: String): ParsedTransaction {
        val lines = text.split("\n")
        var foundId: String? = null
        var foundAmount: String? = null

        // Strategy: Line by Line Search
        for (line in lines) {
            // Find Transaction ID
            if (foundId == null) {
                val matcher = UTR_PATTERN.matcher(line)
                if (matcher.find()) {
                    foundId = matcher.group()
                }
            }

            // Find Amount
            // Heuristic: Usually implies a successful payment with "Paid" or just larger numbers
            // We look for currency symbols or clean number formats
            if (foundAmount == null) {
                 if (line.contains("₹") || line.contains("Rs")) {
                     val matcher = AMOUNT_PATTERN.matcher(line)
                     if (matcher.find()) {
                         // Clean up commas
                         val rawAmount = matcher.group(1)?.replace(",", "")
                         // Validate it looks like a number
                         if (!rawAmount.isNullOrEmpty() && rawAmount.toDoubleOrNull() != null) {
                             foundAmount = rawAmount
                         }
                     }
                 }
            }
        }
        
        // Fallback: If no currency symbol found, look for just numbers in likely positions (advanced logic omitted for V1)
        
        return ParsedTransaction(
            transactionId = foundId,
            amount = foundAmount,
            rawText = text
        )
    }
}
