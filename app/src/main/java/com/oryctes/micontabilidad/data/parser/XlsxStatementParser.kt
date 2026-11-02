package com.oryctes.micontabilidad.data.parser

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.dhatim.fastexcel.reader.ReadableWorkbook
import java.io.InputStream

class XlsxStatementParser : StatementParser {

    init {
        try {
            System.setProperty("javax.xml.stream.XMLInputFactory", "com.fasterxml.aalto.stax.InputFactoryImpl")
            System.setProperty("javax.xml.stream.XMLOutputFactory", "com.fasterxml.aalto.stax.OutputFactoryImpl")
            System.setProperty("javax.xml.stream.XMLEventFactory", "com.fasterxml.aalto.stax.EventFactoryImpl")
        } catch (_: Throwable) {}
    }

    override suspend fun parse(
        inputStream: InputStream,
        preferredDelimiter: String?,
        preferredCharset: String?
    ): ParsedDocument = withContext(Dispatchers.IO) {
        val rowsList = mutableListOf<TabularRow>()
        var headers = emptyList<String>()

        try {
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
        } catch (t: Throwable) {
            android.util.Log.e("XlsxParser", "Error parsing XLSX workbook", t)
            throw IllegalStateException("No se pudo procesar el archivo Excel (.xlsx): ${t.message ?: t.javaClass.simpleName}", t)
        }

        ParsedDocument(
            headers = headers,
            rows = rowsList,
            detectedDelimiter = "EXCEL_TABULAR",
            detectedCharset = "N/A"
        )
    }
}
