package com.oryctes.micontabilidad.domain.normalizer

import com.oryctes.micontabilidad.domain.model.ColumnMapping
import java.math.BigDecimal

data class InferredTableStructure(
    val mapping: ColumnMapping,
    val headerRowIndex: Int,
    val detectedHeaders: List<String>,
    val columnSamples: Map<Int, String> // Columna -> Valor de ejemplo representativo
)

object ColumnTypeInferer {

    private val HEADER_KEYWORDS = listOf(
        "fecha", "date", "concepto", "concept", "descripcion", "description",
        "detalle", "movimiento", "datos", "importe", "amount", "cuantia", "saldo", "balance",
        "cargo", "abono", "debe", "haber", "operacion", "comentario"
    )

    /**
     * Inspecciona todas las filas del documento para:
     * 1. Localizar la fila real de cabecera de la tabla de movimientos.
     * 2. Analizar el tipo de dato predominante en cada columna (fechas, importes, conceptos).
     * 3. Proponer el mapeo de columnas y los valores de ejemplo para la UI.
     */
    fun inferFromRows(
        rawHeaders: List<String>,
        allRows: List<List<String>>
    ): InferredTableStructure {
        if (allRows.isEmpty()) {
            return InferredTableStructure(
                mapping = ColumnMapping(skipHeaderRows = 1),
                headerRowIndex = 0,
                detectedHeaders = rawHeaders,
                columnSamples = emptyMap()
            )
        }

        // 1. Buscar la fila que más probablemente sea la cabecera de la tabla de transacciones
        var bestHeaderRowIdx = -1
        var maxKeywordMatches = 0

        // Comprobar primero las cabeceras originales provistas
        val rawHeaderMatches = countKeywordMatches(rawHeaders)
        if (rawHeaderMatches >= 2) {
            bestHeaderRowIdx = -1 // Las cabeceras originales ya eran buenas
            maxKeywordMatches = rawHeaderMatches
        }

        // Si no, o para PDFs/XLSX con membretes iniciales, buscar en las primeras 25 filas
        val searchLimit = minOf(allRows.size, 25)
        for (i in 0 until searchLimit) {
            val row = allRows[i]
            val matches = countKeywordMatches(row)
            if (matches > maxKeywordMatches) {
                maxKeywordMatches = matches
                bestHeaderRowIdx = i
            }
        }

        val effectiveHeaders = when {
            bestHeaderRowIdx >= 0 -> {
                allRows[bestHeaderRowIdx].mapIndexed { idx, name ->
                    val clean = name.trim()
                    if (clean.isBlank()) "Columna ${idx + 1}" else clean
                }
            }
            rawHeaders.isNotEmpty() && rawHeaders.any { !it.startsWith("Columna ") } -> rawHeaders
            else -> {
                val maxCols = allRows.maxOfOrNull { it.size } ?: 1
                (1..maxCols).map { "Columna $it" }
            }
        }

        val dataStartIndex = if (bestHeaderRowIdx >= 0) bestHeaderRowIdx + 1 else 0
        val dataRows = allRows.drop(dataStartIndex).filter { row ->
            row.any { it.isNotBlank() }
        }

        val maxCols = maxOf(
            effectiveHeaders.size,
            dataRows.maxOfOrNull { it.size } ?: 0
        )

        // 2. Extraer muestras representativas de cada columna
        val samples = mutableMapOf<Int, String>()
        for (col in 0 until maxCols) {
            val sampleVal = dataRows.mapNotNull { it.getOrNull(col)?.trim() }
                .firstOrNull { it.isNotBlank() } ?: ""
            samples[col] = sampleVal
        }

        // 3. Analizar tipos de datos por columna
        val dateCols = mutableListOf<Int>()
        val numberCols = mutableListOf<Int>()
        val textCols = mutableListOf<Int>()

        val sampleSize = minOf(dataRows.size, 20)
        val testRows = dataRows.take(sampleSize)

        for (col in 0 until maxCols) {
            val nonBlankCells = testRows.mapNotNull { it.getOrNull(col)?.trim() }
                .filter { it.isNotBlank() }

            if (nonBlankCells.isEmpty()) continue

            var dateCount = 0
            var numberCount = 0
            var textCount = 0

            for (cell in nonBlankCells) {
                val isDate = DateNormalizer.parseDate(cell) != null
                val isNumber = AmountNormalizer.parseAmount(cell) != null

                when {
                    isDate -> dateCount++
                    isNumber -> numberCount++
                    else -> textCount++
                }
            }

            val total = nonBlankCells.size.toFloat()
            if (dateCount / total >= 0.5f) {
                dateCols.add(col)
            } else if (numberCount / total >= 0.5f) {
                numberCols.add(col)
            } else {
                textCols.add(col)
            }
        }

        // 4. Asignar columnas según tipos y cabeceras
        var opDateCol = -1
        var valDateCol = -1
        var conceptCol = -1
        var conceptDetailsCol = -1
        var amountCol = -1
        var debitCol = -1
        var creditCol = -1
        var balanceCol = -1

        // Asignar fechas distinguiendo Fecha de Operación y Fecha Valor
        val opDateHeaderIdx = effectiveHeaders.indexOfFirst {
            val h = it.lowercase().trim()
            (h.contains("operaci") || h == "fecha" || h.startsWith("fecha ")) && !h.contains("valor")
        }
        val valDateHeaderIdx = effectiveHeaders.indexOfFirst {
            val h = it.lowercase().trim()
            h.contains("valor")
        }

        if (opDateHeaderIdx != -1) {
            opDateCol = opDateHeaderIdx
            if (valDateHeaderIdx != -1) {
                valDateCol = valDateHeaderIdx
            } else if (dateCols.size > 1) {
                valDateCol = dateCols.firstOrNull { it != opDateCol } ?: -1
            }
        } else if (dateCols.isNotEmpty()) {
            // Si solo hay una columna de fecha (ej. F. VALOR en ING), esa es la fecha de operación principal
            opDateCol = dateCols[0]
            if (dateCols.size > 1) {
                valDateCol = dateCols[1]
            }
        }

        // Asignar concepto principal y concepto ampliado / más datos
        if (textCols.isNotEmpty()) {
            val detailsKeywords = listOf("más datos", "mas datos", "datos", "ampliado", "comentario", "subcategor", "observac")
            val mainConceptKeywords = listOf("concepto", "movimiento", "descrip", "detalle")

            // 1. Detectar si existe una columna específica de más datos / concepto ampliado
            val detailsColCandidate = textCols.find { col ->
                val h = effectiveHeaders.getOrNull(col)?.lowercase().orEmpty()
                detailsKeywords.any { h.contains(it) }
            }
            if (detailsColCandidate != null) {
                conceptDetailsCol = detailsColCandidate
            }

            // 2. Para el concepto principal, buscar entre las columnas de texto restantes
            val availableTextCols = if (conceptDetailsCol != -1) textCols.filter { it != conceptDetailsCol } else textCols

            val keywordConcept = availableTextCols.find { col ->
                val h = effectiveHeaders.getOrNull(col)?.lowercase().orEmpty()
                mainConceptKeywords.any { h.contains(it) }
            }

            conceptCol = keywordConcept ?: availableTextCols.maxByOrNull { col ->
                testRows.mapNotNull { it.getOrNull(col) }.sumOf { it.length }
            } ?: (if (availableTextCols.isNotEmpty()) availableTextCols[0] else -1)
        }

        // Asignar importes y saldo
        // Comprobar si hay columnas nombradas explícitamente Cargo/Debe y Abono/Haber
        val debitHeaderCol = numberCols.find { col ->
            val h = effectiveHeaders.getOrNull(col)?.lowercase().orEmpty()
            h.contains("cargo") || h.contains("debe") || h.contains("gasto")
        }
        val creditHeaderCol = numberCols.find { col ->
            val h = effectiveHeaders.getOrNull(col)?.lowercase().orEmpty()
            h.contains("abono") || h.contains("haber") || h.contains("ingreso")
        }

        if (debitHeaderCol != null && creditHeaderCol != null) {
            debitCol = debitHeaderCol
            creditCol = creditHeaderCol
        }

        // Comprobar Saldo prioritariamente por cabecera
        val balanceHeaderCol = effectiveHeaders.indexOfFirst {
            val h = it.lowercase()
            h.contains("saldo") || h.contains("balance")
        }

        if (balanceHeaderCol != -1) {
            balanceCol = balanceHeaderCol
        } else if (numberCols.size >= 2 && debitCol == -1 && amountCol == -1) {
            balanceCol = numberCols.last()
        }

        // Comprobar Importe prioritariamente por cabecera
        val amountHeaderCol = effectiveHeaders.indexOfFirst {
            val h = it.lowercase()
            (h.contains("importe") || h.contains("cuantia") || h.contains("monto") || h.contains("amount")) &&
                    !h.contains("saldo") && !h.contains("balance")
        }

        if (amountHeaderCol != -1) {
            amountCol = amountHeaderCol
        } else if (debitCol == -1) {
            val remainingNumberCols = numberCols.filter { it != balanceCol }
            if (remainingNumberCols.isNotEmpty()) {
                amountCol = remainingNumberCols.first()
            }
        }

        // Fallbacks de seguridad si no se detectó fecha o concepto
        if (opDateCol == -1) {
            opDateCol = effectiveHeaders.indexOfFirst {
                val h = it.lowercase()
                h.contains("fecha") || h.contains("f. valor") || h.contains("f. oper") || h.contains("f.valor") || h.contains("date")
            }
        }
        if (conceptCol == -1) {
            conceptCol = effectiveHeaders.indexOfFirst {
                val h = it.lowercase()
                h.contains("descrip") || h.contains("concepto") || h.contains("detalle") || h.contains("movimiento")
            }
        }

        val mapping = ColumnMapping(
            operationDateIndex = opDateCol,
            valueDateIndex = valDateCol,
            conceptIndex = conceptCol,
            conceptDetailsIndex = conceptDetailsCol,
            amountIndex = amountCol,
            debitIndex = debitCol,
            creditIndex = creditCol,
            balanceIndex = balanceCol,
            skipHeaderRows = dataStartIndex
        )

        return InferredTableStructure(
            mapping = mapping,
            headerRowIndex = bestHeaderRowIdx,
            detectedHeaders = effectiveHeaders,
            columnSamples = samples
        )
    }

    private fun countKeywordMatches(row: List<String>): Int {
        return row.count { cell ->
            val lower = cell.lowercase().trim()
            HEADER_KEYWORDS.any { lower.contains(it) }
        }
    }
}
