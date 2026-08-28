package com.example.healthheatv2.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PdfUtil {
    private var isInitialized = false

    fun init(context: Context) {
        if (!isInitialized) {
            PDFBoxResourceLoader.init(context.applicationContext)
            isInitialized = true
        }
    }

    suspend fun extractTextFromPdf(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        var document: PDDocument? = null
        try {
            init(context)
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                document = PDDocument.load(inputStream)
                val pdfStripper = PDFTextStripper()
                return@withContext pdfStripper.getText(document)
            }
        } catch (e: Exception) {
            Log.e("PdfUtil", "Error extracting text from PDF", e)
            return@withContext ""
        } finally {
            document?.close()
        }
        return@withContext ""
    }
}
