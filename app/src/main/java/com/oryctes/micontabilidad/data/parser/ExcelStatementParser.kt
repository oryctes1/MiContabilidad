package com.oryctes.micontabilidad.data.parser

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DateUtil
import org.dhatim.fastexcel.reader.ReadableWorkbook
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.time.format.DateTimeFormatter

class ExcelStatementParser : StatementParser {

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
        val bytes = inputStream.readBytes()

        // Determinar formato por número mágico / cabecera de archivo:
        // 1. OLE2 BIFF8 (.xls binario de Excel 97-2003): D0 CF 11 E0 A1 B1 1A E1
        // 2. ZIP OOXML (.xlsx): 50 4B 03 04 ("PK\x03\x04")
        val isOoxmlZip = bytes.size >= 4 &&
                bytes[0] == 0x50.toByte() &&
                bytes[1] == 0x4B.toByte() &&
                bytes[2] == 0x03.toByte() &&
                bytes[3] == 0x04.toByte()

        val isBiff8Xls = bytes.size >= 8 &&
                bytes[0] == 0xD0.toByte() &&
                bytes[1] == 0xCF.toByte() &&
                bytes[2] == 0x11.toByte() &&
                bytes[3] == 0xE0.toByte() &&
                bytes[4] == 0xA1.toByte() &&
                bytes[5] == 0xB1.toByte() &&
                bytes[6] == 0x1A.toByte() &&
                bytes[7] == 0xE1.toByte()

        when {
            isOoxmlZip -> parseOoxmlXlsx(bytes)
            isBiff8Xls -> parseBiff8Xls(bytes)
            else -> {
                // Si tiene extensión .xls/.xlsx pero en realidad es texto plano delimitado (CSV, TSV, etc.)
                try {
                    val csvParser = CsvStatementParser()
                    csvParser.parse(ByteArrayInputStream(bytes), preferredDelimiter, preferredCharset)
                } catch (_: Throwable) {
                    // Fallback a intentar con POI
                    parseBiff8Xls(bytes)
                }
            }
        }
    }

    private fun parseOoxmlXlsx(bytes: ByteArray): ParsedDocument {
        val rowsList = mutableListOf<TabularRow>()
        var headers = emptyList<String>()

        try {
            ReadableWorkbook(ByteArrayInputStream(bytes)).use { workbook ->
                val firstSheet = workbook.firstSheet ?: return ParsedDocument(emptyList(), emptyList())
                val rows = firstSheet.read()

                var headerFound = false
                for ((idx, row) in rows.withIndex()) {
                    val cells = (0 until row.cellCount).map { colIdx ->
                        val cell = row.getCell(colIdx)
                        cell?.text?.trim() ?: ""
                    }

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
            android.util.Log.e("ExcelParser", "Error parsing XLSX workbook with FastExcel", t)
            throw IllegalStateException("No se pudo procesar el archivo Excel (.xlsx): ${t.message ?: t.javaClass.simpleName}", t)
        }

        return ParsedDocument(
            headers = headers,
            rows = rowsList,
            detectedDelimiter = "EXCEL_XLSX",
            detectedCharset = "UTF-8"
        )
    }

    private fun parseBiff8Xls(bytes: ByteArray): ParsedDocument {
        val rowsList = mutableListOf<TabularRow>()
        var headers = emptyList<String>()
        val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

        try {
            HSSFWorkbook(ByteArrayInputStream(bytes)).use { workbook ->
                if (workbook.numberOfSheets == 0) return ParsedDocument(emptyList(), emptyList())
                val sheet = workbook.getSheetAt(0) ?: return ParsedDocument(emptyList(), emptyList())

                var headerFound = false
                val numRows = sheet.lastRowNum + 1

                for (r in 0 until numRows) {
                    val row = sheet.getRow(r) ?: continue
                    val lastCol = row.lastCellNum.toInt().coerceAtLeast(0)
                    if (lastCol == 0) continue

                    val cells = (0 until lastCol).map { c ->
                        val cell = row.getCell(c)
                        formatPoiCell(cell, dateFormatter)
                    }

                    if (cells.all { it.isBlank() }) continue

                    if (!headerFound) {
                        headers = cells.mapIndexed { index, name ->
                            if (name.isBlank()) "Columna ${index + 1}" else name
                        }
                        headerFound = true
                    } else {
                        rowsList.add(TabularRow(rowIndex = r, cells = cells))
                    }
                }
            }
        } catch (t: Throwable) {
            android.util.Log.e("ExcelParser", "Error parsing XLS workbook with Apache POI", t)
            throw IllegalStateException("No se pudo procesar el archivo Excel (.xls): ${t.message ?: t.javaClass.simpleName}", t)
        }

        return ParsedDocument(
            headers = headers,
            rows = rowsList,
            detectedDelimiter = "EXCEL_XLS",
            detectedCharset = "N/A"
        )
    }

    private fun formatPoiCell(cell: Cell?, dateFormatter: DateTimeFormatter): String {
        if (cell == null) return ""
        return try {
            when (cell.cellType) {
                CellType.STRING -> cell.stringCellValue.trim()
                CellType.NUMERIC -> {
                    if (DateUtil.isCellDateFormatted(cell)) {
                        cell.localDateTimeCellValue?.toLocalDate()?.format(dateFormatter)
                            ?: cell.dateCellValue?.toString()
                            ?: cell.numericCellValue.toString()
                    } else {
                        val num = cell.numericCellValue
                        if (num == num.toLong().toDouble()) {
                            num.toLong().toString()
                        } else {
                            num.toString()
                        }
                    }
                }
                CellType.BOOLEAN -> cell.booleanCellValue.toString()
                CellType.FORMULA -> {
                    when (cell.cachedFormulaResultType) {
                        CellType.NUMERIC -> cell.numericCellValue.toString()
                        CellType.STRING -> cell.stringCellValue.trim()
                        CellType.BOOLEAN -> cell.booleanCellValue.toString()
                        else -> ""
                    }
                }
                CellType.BLANK -> ""
                else -> ""
            }
        } catch (_: Exception) {
            ""
        }
    }
}
