package com.oryctes.micontabilidad.data.parser

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

class CsvStatementParser : StatementParser {

    override suspend fun parse(
        inputStream: InputStream,
        preferredDelimiter: String?,
        preferredCharset: String?
    ): ParsedDocument = withContext(Dispatchers.IO) {
        val rawBytes = inputStream.readBytes()

        // 1. Detección de codificación
        val charset = if (!preferredCharset.isNullOrBlank()) {
            runCatching { Charset.forName(preferredCharset) }.getOrDefault(StandardCharsets.UTF_8)
        } else {
            detectCharset(rawBytes)
        }

        // 2. Detección de delimitador si no se especificó
        val sampleText = String(rawBytes.take(4096).toByteArray(), charset)
        val delimiterChar = if (!preferredDelimiter.isNullOrBlank() && preferredDelimiter.isNotEmpty()) {
            preferredDelimiter[0]
        } else {
            detectDelimiter(sampleText)
        }

        // 3. Parsear con Apache Commons CSV
        val format = CSVFormat.Builder.create()
            .setDelimiter(delimiterChar)
            .setQuote('"')
            .setIgnoreEmptyLines(true)
            .setTrim(true)
            .setAllowMissingColumnNames(true)
            .build()

        val reader = InputStreamReader(ByteArrayInputStream(rawBytes), charset)
        val csvParser = CSVParser(reader, format)

        val allRecords = csvParser.records
        if (allRecords.isEmpty()) {
            return@withContext ParsedDocument(
                headers = emptyList(),
                rows = emptyList(),
                detectedDelimiter = delimiterChar.toString(),
                detectedCharset = charset.name()
            )
        }

        // Detectar si la primera fila son cabeceras (contiene texto sin números puros)
        val firstRow = allRecords.first().toList()
        val hasHeaders = isHeaderRow(firstRow)

        val headers = if (hasHeaders) {
            firstRow.mapIndexed { index, name ->
                val clean = name.trim().replace("\uFEFF", "")
                if (clean.isBlank()) "Columna ${index + 1}" else clean
            }
        } else {
            firstRow.indices.map { "Columna ${it + 1}" }
        }

        val dataRecords = if (hasHeaders) allRecords.drop(1) else allRecords
        val rows = dataRecords.mapIndexed { index, record ->
            TabularRow(
                rowIndex = index + (if (hasHeaders) 1 else 0),
                cells = record.toList()
            )
        }

        ParsedDocument(
            headers = headers,
            rows = rows,
            detectedDelimiter = delimiterChar.toString(),
            detectedCharset = charset.name()
        )
    }

    private fun detectCharset(bytes: ByteArray): Charset {
        // Comprobar UTF-8 BOM
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            return StandardCharsets.UTF_8
        }

        // Validar si es UTF-8 válido
        return try {
            val decoder = StandardCharsets.UTF_8.newDecoder()
            decoder.decode(java.nio.ByteBuffer.wrap(bytes))
            StandardCharsets.UTF_8
        } catch (_: Exception) {
            // Frecuente en exportaciones de bancos españoles (ISO-8859-1 / Windows-1252)
            Charset.forName("ISO-8859-1")
        }
    }

    private fun detectDelimiter(sampleText: String): Char {
        val lines = sampleText.lines().filter { it.isNotBlank() }.take(5)
        if (lines.isEmpty()) return ';'

        val candidates = listOf(';', ',', '\t', '|')
        val scores = candidates.associateWith { delimiter ->
            val counts = lines.map { line -> line.count { it == delimiter } }
            val firstCount = counts.firstOrNull() ?: 0
            if (firstCount > 0 && counts.all { it == firstCount }) {
                firstCount * 10 // Consistente en todas las líneas
            } else {
                counts.sum()
            }
        }

        return scores.maxByOrNull { it.value }?.key ?: ';'
    }

    private fun isHeaderRow(row: List<String>): Boolean {
        if (row.isEmpty()) return false
        val headerKeywords = listOf(
            "fecha", "date", "concepto", "concept", "descripcion", "description",
            "importe", "amount", "cuantia", "saldo", "balance", "cargo", "abono",
            "debe", "haber", "operacion", "movimiento"
        )
        val textCount = row.count { cell ->
            val lower = cell.lowercase().trim()
            headerKeywords.any { lower.contains(it) }
        }
        return textCount >= 1
    }
}
