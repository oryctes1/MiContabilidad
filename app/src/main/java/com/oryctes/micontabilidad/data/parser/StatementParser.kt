package com.oryctes.micontabilidad.data.parser

import java.io.InputStream

data class TabularRow(
    val rowIndex: Int,
    val cells: List<String>
)

data class ParsedDocument(
    val headers: List<String>,
    val rows: List<TabularRow>,
    val detectedDelimiter: String? = null,
    val detectedCharset: String? = null,
    val documentHeaderLines: List<String> = emptyList()
)

interface StatementParser {
    suspend fun parse(
        inputStream: InputStream,
        preferredDelimiter: String? = null,
        preferredCharset: String? = null
    ): ParsedDocument
}
