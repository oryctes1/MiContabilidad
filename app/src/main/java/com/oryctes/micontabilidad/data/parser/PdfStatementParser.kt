package com.oryctes.micontabilidad.data.parser

import android.content.Context
import android.util.Log
import com.oryctes.micontabilidad.domain.normalizer.DateNormalizer
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

class PdfStatementParser(private val context: Context? = null) : StatementParser {

    init {
        try {
            if (context != null) {
                PDFBoxResourceLoader.init(context)
            }
        } catch (_: Exception) {}
    }

    override suspend fun parse(
        inputStream: InputStream,
        preferredDelimiter: String?,
        preferredCharset: String?
    ): ParsedDocument = withContext(Dispatchers.IO) {
        PDDocument.load(inputStream).use { document ->
            val stripper = TableAwarePdfStripper()
            stripper.sortByPosition = true
            stripper.getText(document)
            stripper.buildParsedDocument()
        }
    }

    private class TableAwarePdfStripper : PDFTextStripper() {

        data class Fragment(
            val text: String,
            val x: Float,
            val y: Float,
            val width: Float,
            val pageNum: Int
        ) {
            val endX: Float get() = x + width
        }

        private var currentPage = 0
        private val currentLineFragments = mutableListOf<Fragment>()
        private val allPhysicalLines = mutableListOf<List<Fragment>>()

        override fun startPage(page: PDPage?) {
            currentPage++
            super.startPage(page)
        }

        override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
            if (textPositions.isNotEmpty() && text.isNotBlank()) {
                val first = textPositions.first()
                val last = textPositions.last()
                val width = (last.xDirAdj + last.widthDirAdj) - first.xDirAdj
                currentLineFragments.add(
                    Fragment(
                        text = text.trim(),
                        x = first.xDirAdj,
                        y = first.yDirAdj,
                        width = if (width > 0) width else last.widthDirAdj,
                        pageNum = currentPage
                    )
                )
            }
            super.writeString(text, textPositions)
        }

        override fun writeLineSeparator() {
            flushCurrentLine()
            super.writeLineSeparator()
        }

        override fun endPage(page: PDPage?) {
            flushCurrentLine()
            super.endPage(page)
        }

        private fun flushCurrentLine() {
            if (currentLineFragments.isNotEmpty()) {
                val sorted = currentLineFragments.sortedBy { it.x }.toList()
                allPhysicalLines.add(sorted)
                currentLineFragments.clear()
            }
        }

        fun buildParsedDocument(): ParsedDocument {
            flushCurrentLine()

            if (allPhysicalLines.isEmpty()) {
                Log.w("PdfParser", "allPhysicalLines is empty")
                return ParsedDocument(emptyList(), emptyList())
            }

            Log.d("PdfParser", "Total physical lines captured: ${allPhysicalLines.size}")

            val keywords = listOf(
                "f. valor", "f.valor", "fecha", "valor", "categor", "subcat",
                "descrip", "comentario", "importe", "cuantia", "cuantía", "saldo", "cargo", "abono", "debe", "haber",
                "movimiento", "datos", "concepto"
            )

            // 1. Localizar la línea de cabecera de la tabla de movimientos
            var headerLineIdx = -1
            var bestScore = 0

            for ((idx, line) in allPhysicalLines.withIndex()) {
                val lineText = line.joinToString(" ") { it.text.lowercase() }
                val score = keywords.count { lineText.contains(it) }
                if (score > bestScore) {
                    bestScore = score
                    headerLineIdx = idx
                }
            }

            Log.d("PdfParser", "Best header line idx: $headerLineIdx with score $bestScore")

            if (headerLineIdx == -1 || bestScore < 2) {
                Log.w("PdfParser", "No clear table header found. Using fallback.")
                return fallbackSimpleLines(allPhysicalLines)
            }

            // 2. Extraer títulos de columnas
            val headerFragments = allPhysicalLines[headerLineIdx]
            val mergedHeaderColumns = mutableListOf<Fragment>()

            for (frag in headerFragments) {
                if (mergedHeaderColumns.isEmpty()) {
                    mergedHeaderColumns.add(frag)
                } else {
                    val last = mergedHeaderColumns.last()
                    val gap = frag.x - last.endX
                    val fragTextLower = frag.text.lowercase().trim()
                    val lastTextLower = last.text.lowercase().trim()

                    // Un fragmento NUNCA se fusiona si es una palabra clave reconocible de cabecera bancaria
                    val isFragKnownHeader = keywords.any { fragTextLower.contains(it) }
                    val isLastKnownHeader = keywords.any { lastTextLower.contains(it) }

                    // Protección explícita para evitar que columnas de importe/cuantía y saldo se fusionen
                    val isFragAmount = fragTextLower.contains("cuant") || fragTextLower.contains("import")
                    val isLastAmount = lastTextLower.contains("cuant") || lastTextLower.contains("import")
                    val isFragSaldo = fragTextLower.contains("saldo")
                    val isLastSaldo = lastTextLower.contains("saldo")
                    val isAmountSaldoBoundary = (isLastAmount && isFragSaldo) || (isLastSaldo && isFragAmount)

                    // Solo fusionar si la distancia es mínima (< 6pt) y no es una cabecera bancaria separada (ej. "Fecha" y "Fecha valor"),
                    // o si frag es un sufijo como "(€)" o "(EUR)" o last es una abreviatura ("f." o "f" o "nº")
                    val shouldMerge = when {
                        isAmountSaldoBoundary -> false
                        // Símbolo de moneda o paréntesis
                        fragTextLower.startsWith("(") || fragTextLower.startsWith("€") || fragTextLower == "eur" -> gap < 16f
                        // Abreviatura como "F." en "F. VALOR"
                        lastTextLower in listOf("f.", "f", "nº", "num", "cta", "cta.", "n.") -> gap < 16f
                        // Si ambos son cabeceras independientes (ej. "Fecha" y "Fecha valor"), JAMÁS fusionar
                        isFragKnownHeader && isLastKnownHeader -> false
                        // Para palabras compuestas dentro del mismo título sin keywords separadas
                        !isFragKnownHeader && gap < 6f -> true
                        else -> false
                    }

                    if (shouldMerge) {
                        mergedHeaderColumns[mergedHeaderColumns.lastIndex] = Fragment(
                            text = "${last.text} ${frag.text}",
                            x = last.x,
                            y = last.y,
                            width = (frag.x + frag.width) - last.x,
                            pageNum = last.pageNum
                        )
                    } else {
                        mergedHeaderColumns.add(frag)
                    }
                }
            }

            val columnHeaders = mergedHeaderColumns.map { it.text }
            val numCols = mergedHeaderColumns.size
            Log.d("PdfParser", "Detected $numCols columns: $columnHeaders")

            if (numCols < 2) {
                return fallbackSimpleLines(allPhysicalLines)
            }

            // 3. Determinar los límites reales de cada columna.
            val dataLines = allPhysicalLines.drop(headerLineIdx + 1)
            val sampleDataFrags = dataLines.take(50).flatMap { it }

            // Identificar qué columnas corresponden a importe y saldo para calibrar sus fronteras
            val amountHeaderIdx = columnHeaders.indexOfFirst {
                val h = it.lowercase()
                (h.contains("importe") || h.contains("cuanti") || h.contains("cuantí") || h.contains("amount")) && !h.contains("saldo")
            }
            val balanceHeaderIdx = columnHeaders.indexOfFirst {
                val h = it.lowercase()
                h.contains("saldo") || h.contains("balance")
            }

            val boundaries = mutableListOf<Float>()
            for (i in 0 until numCols - 1) {
                val currH = mergedHeaderColumns[i]
                val nextH = mergedHeaderColumns[i + 1]

                val boundary = when {
                    // Frontera justo antes de la columna de Importe:
                    // El texto previo (ej. Más datos o Descripción) puede ser largo y llegar hasta el importe.
                    i + 1 == amountHeaderIdx -> {
                        val minAmountDataX = sampleDataFrags
                            .map { it.x }
                            .filter { it > nextH.x - 30f && it <= nextH.x + 25f }
                            .minOrNull() ?: (nextH.x - 5f)
                        minAmountDataX - 3f
                    }
                    // Frontera entre Importe y Saldo:
                    i == amountHeaderIdx && i + 1 == balanceHeaderIdx -> {
                        val minSaldoDataX = sampleDataFrags
                            .map { it.x }
                            .filter { it > nextH.x - 25f && it <= nextH.x + 25f }
                            .minOrNull() ?: (nextH.x - 5f)
                        val minAmountDataX = sampleDataFrags
                            .map { it.x }
                            .filter { it > currH.x - 30f && it <= currH.x + 25f }
                            .minOrNull() ?: (currH.x - 5f)
                        val maxAmountDataX = sampleDataFrags
                            .map { it.x }
                            .filter { it >= minAmountDataX - 5f && it < minSaldoDataX - 5f }
                            .maxOrNull() ?: (minAmountDataX + 20f)
                        (maxAmountDataX + minSaldoDataX) / 2f
                    }
                    else -> {
                        (currH.x + nextH.x) / 2f
                    }
                }
                boundaries.add(boundary)
            }
            boundaries.add(Float.MAX_VALUE)
            Log.d("PdfParser", "Header positions: ${mergedHeaderColumns.map { it.x }}, boundaries: $boundaries")

            // Identificar qué columna corresponde a la fecha de operación o valor
            val dateColIdx = columnHeaders.indexOfFirst {
                val h = it.lowercase()
                h.contains("fecha") || h.contains("f. valor") || h.contains("f.valor") || h.contains("date")
            }.let { if (it >= 0) it else 0 }

            // 4. Procesar líneas de datos
            val rows = mutableListOf<TabularRow>()
            var lastCompletedRowCells: MutableList<String>? = null

            for (line in dataLines) {
                val lineTextLower = line.joinToString(" ") { it.text.lowercase() }

                // Omitir cabeceras repetidas en páginas siguientes o metadatos de pie de página
                if (keywords.count { lineTextLower.contains(it) } >= 3 ||
                    lineTextLower.contains("página ") || lineTextLower.contains("pagina ") ||
                    lineTextLower.contains("ing bank n.v.") || lineTextLower.contains("total movimientos")
                ) {
                    continue
                }

                val cells = MutableList(numCols) { "" }

                for (frag in line) {
                    val colIdx = boundaries.indexOfFirst { frag.x < it }
                    val safeCol = if (colIdx in 0 until numCols) colIdx else numCols - 1

                    if (cells[safeCol].isBlank()) {
                        cells[safeCol] = frag.text
                    } else {
                        cells[safeCol] = "${cells[safeCol]} ${frag.text}"
                    }
                }

                // Si la línea está completamente vacía, ignorarla
                if (cells.all { it.isBlank() }) continue

                // 5. Determinar si es una nueva transacción o continuación de descripción
                val dateCandidate1 = cells.getOrNull(dateColIdx)?.trim().orEmpty()
                val dateCandidate0 = cells.getOrNull(0)?.trim().orEmpty()
                val isDateValid = DateNormalizer.parseDate(dateCandidate1) != null ||
                                  DateNormalizer.parseDate(dateCandidate0) != null

                if (isDateValid) {
                    rows.add(TabularRow(rowIndex = rows.size, cells = cells))
                    lastCompletedRowCells = cells
                } else if (lastCompletedRowCells != null) {
                    // Es continuación multilínea (descripción larga)
                    for (c in 0 until numCols) {
                        val extra = cells[c].trim()
                        if (extra.isNotEmpty()) {
                            val currentVal = lastCompletedRowCells[c]
                            lastCompletedRowCells[c] = if (currentVal.isBlank()) extra else "$currentVal $extra"
                        }
                    }
                } else {
                    // Si no teníamos fila previa pero contiene datos, crearla
                    rows.add(TabularRow(rowIndex = rows.size, cells = cells))
                    lastCompletedRowCells = cells
                }
            }

            Log.d("PdfParser", "Extracted ${rows.size} transaction rows.")
            if (rows.isNotEmpty()) {
                Log.d("PdfParser", "First row cells: ${rows.first().cells}")
            }

            val preHeaderLines = if (headerLineIdx > 0) {
                allPhysicalLines.take(headerLineIdx).map { line ->
                    line.joinToString(" ") { it.text }
                }
            } else emptyList()

            return ParsedDocument(
                headers = columnHeaders,
                rows = rows,
                detectedDelimiter = "PDF_SPATIAL_COORDINATES",
                detectedCharset = "PDF",
                documentHeaderLines = preHeaderLines
            )
        }

        private fun fallbackSimpleLines(lines: List<List<Fragment>>): ParsedDocument {
            var maxCols = 1
            val parsedLines = lines.mapIndexed { idx, lineFrags ->
                val cells = lineFrags.map { it.text.trim() }.filter { it.isNotEmpty() }
                if (cells.size > maxCols) maxCols = cells.size
                TabularRow(rowIndex = idx, cells = cells)
            }
            val headers = (1..maxCols).map { "Columna $it" }
            val preLines = lines.take(5).map { line -> line.joinToString(" ") { it.text } }
            return ParsedDocument(
                headers = headers,
                rows = parsedLines,
                detectedDelimiter = "PDF_FALLBACK",
                detectedCharset = "PDF",
                documentHeaderLines = preLines
            )
        }
    }
}
