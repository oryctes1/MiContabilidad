package com.oryctes.micontabilidad.data.parser

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.dhatim.fastexcel.reader.ReadableWorkbook
import java.io.InputStream

class XlsxStatementParser : StatementParser {

    override suspend fun parse(
        inputStream: InputStream,
        preferredDelimiter: String?,
        preferredCharset: String?
    ): ParsedDocument = withContext(Dispatchers.IO) {
        val rowsList = mutableListOf<TabularRow>()
        var headers = emptyList<String>()

        ReadableWorkbook(inputStream).use { workbook ->
            val firstSheet = workbook.firstSheet ?: return@withContext ParsedDocument(emptyList(), emptyList())
            val rows = firstSheet.read()

            var headerFound = false
            for ((idx, row) in rows.withIndex()) {
                val cells = (0 until row.cellCount).map { colIdx ->
                    val cell = row.getCell(colIdx)
                    cell?.text?.trim() ?: ""
                }

                // Omitir filas completamente vacías al inicio
                if (cells.all { it.isBlank() }) continue

                if (!headerFound) {
                    headers = cells.mapIndexed { index, name ->
                        if (name.isBlank()) "Columna ${index + 1}" else name
                    }
                    headerFound = true
                } else {
                    rowsList.add(TabularRow(rowIndex = idx, cells = cells))
                }
            }
        }

        ParsedDocument(
            headers = headers,
            rows = rowsList,
            detectedDelimiter = "EXCEL_TABULAR",
            detectedCharset = "N/A"
        )
    }
}
