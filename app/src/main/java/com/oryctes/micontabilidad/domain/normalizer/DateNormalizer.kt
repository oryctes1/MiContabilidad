package com.oryctes.micontabilidad.domain.normalizer

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

object DateNormalizer {

    private val COMMON_PATTERNS = listOf(
        "dd/MM/yyyy",
        "d/M/yyyy",
        "dd-MM-yyyy",
        "d-M-yyyy",
        "yyyy-MM-dd",
        "yyyy/MM/dd",
        "dd.MM.yyyy",
        "d.M.yyyy",
        "dd/MM/yy",
        "dd-MM-yy",
        "yyyyMMdd"
    )

    fun parseDate(raw: String, customPattern: String? = null): LocalDate? {
        val cleaned = raw.trim().replace("\uFEFF", "")
        if (cleaned.isBlank()) return null

        if (!customPattern.isNullOrBlank()) {
            try {
                val formatter = DateTimeFormatter.ofPattern(customPattern, Locale.getDefault())
                return LocalDate.parse(cleaned, formatter)
            } catch (_: Exception) {
                // Si falla el patrón personalizado, intenta con los habituales
            }
        }

        for (pattern in COMMON_PATTERNS) {
            try {
                val formatter = DateTimeFormatter.ofPattern(pattern, Locale.getDefault())
                return LocalDate.parse(cleaned, formatter)
            } catch (_: DateTimeParseException) {
                // Continúa probando
            }
        }

        // Intento con formato ISO por defecto
        val directIso = try {
            LocalDate.parse(cleaned)
        } catch (_: Exception) {
            null
        }
        if (directIso != null) return directIso

        // Fallback: Si la celda contiene una fecha y texto adicional (ej. "09/10/2026 Hogar")
        val dateRegex = Regex("""\b(\d{1,2}[/-]\d{1,2}[/-]\d{2,4}|\d{4}[/-]\d{1,2}[/-]\d{1,2})\b""")
        val match = dateRegex.find(cleaned)
        if (match != null) {
            val dateToken = match.value
            for (pattern in COMMON_PATTERNS) {
                try {
                    val formatter = DateTimeFormatter.ofPattern(pattern, Locale.getDefault())
                    return LocalDate.parse(dateToken, formatter)
                } catch (_: DateTimeParseException) {}
            }
        }

        return null
    }
}
