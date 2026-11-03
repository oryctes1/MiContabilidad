package com.oryctes.micontabilidad.data.parser

import java.io.InputStream

/**
 * XlsxStatementParser delega en ExcelStatementParser para soportar tanto .xlsx (OOXML)
 * como .xls clásico (BIFF8 OLE2 de Apache POI).
 */
class XlsxStatementParser : StatementParser {
    private val delegate = ExcelStatementParser()

    override suspend fun parse(
        inputStream: InputStream,
        preferredDelimiter: String?,
        preferredCharset: String?
    ): ParsedDocument = delegate.parse(inputStream, preferredDelimiter, preferredCharset)
}
