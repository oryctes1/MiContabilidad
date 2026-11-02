package com.oryctes.micontabilidad.data.parser

import android.content.Context

class ParserFactory(private val context: Context) {

    fun getParser(fileName: String, mimeType: String? = null): StatementParser {
        val lowerName = fileName.lowercase()
        val lowerMime = mimeType?.lowercase() ?: ""

        return when {
            lowerName.endsWith(".xlsx") || lowerName.endsWith(".xls") ||
                    lowerMime.contains("spreadsheet") || lowerMime.contains("excel") -> {
                XlsxStatementParser()
            }
            lowerName.endsWith(".pdf") || lowerMime.contains("pdf") -> {
                PdfStatementParser(context)
            }
            else -> {
                // Por defecto CSV / texto delimitado
                CsvStatementParser()
            }
        }
    }
}
