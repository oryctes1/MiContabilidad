package com.oryctes.micontabilidad.domain.normalizer

import java.math.BigDecimal
import java.math.RoundingMode

object AmountNormalizer {

    // Patrón para detectar números monetarios con decimales obligatorios (ej. 117.04, -65,00, 2,347.41)
    private val AMOUNT_PATTERN = Regex("""(?<![\w/])[-+]?\s*(?:\d{1,3}(?:[.,]\d{3})+|\d+)[.,]\d{1,2}(?![\w/])""")

    /**
     * Normaliza una cadena de texto a un [BigDecimal] con 2 decimales.
     */
    fun parseAmount(raw: String?, preferredDecimalSeparator: Char? = null): BigDecimal? {
        if (raw.isNullOrBlank()) return null
        return parseDirect(raw, preferredDecimalSeparator)
    }

    /**
     * Extrae todos los importes numéricos monetarios válidos encontrados en una cadena.
     */
    fun extractAllAmounts(raw: String?, preferredDecimalSeparator: Char? = null): List<BigDecimal> {
        if (raw.isNullOrBlank()) return emptyList()
        return AMOUNT_PATTERN.findAll(raw).mapNotNull { m ->
            parseDirect(m.value.trim(), preferredDecimalSeparator)
        }.toList()
    }

    private fun parseDirect(raw: String, preferredDecimalSeparator: Char? = null): BigDecimal? {
        var text = raw.trim()
            .replace("\uFEFF", "")
            .replace("\u00A0", " ") // espacios no rompibles
            .replace("euros", "", ignoreCase = true)
            .replace("euro", "", ignoreCase = true)
            .replace("EUR", "", ignoreCase = true)
            .replace("USD", "", ignoreCase = true)
            .replace("GBP", "", ignoreCase = true)
            .replace("CHF", "", ignoreCase = true)
            .replace("€", "")
            .replace("$", "")
            .replace("£", "")
            .replace("\u2212", "-") // MINUS SIGN
            .replace("\u2013", "-") // EN DASH
            .replace("\u2014", "-") // EM DASH
            .replace("\u2010", "-") // HYPHEN
            .replace("\uFE63", "-") // SMALL HYPHEN-MINUS
            .replace("\uFF0D", "-") // FULLWIDTH HYPHEN-MINUS
            .trim()

        if (text.isBlank()) return null

        // Comprobación de formato con paréntesis contables ej: "(150.00)" o "( 150,00 )"
        var isNegative = false
        if (text.startsWith("(") && text.endsWith(")")) {
            isNegative = true
            text = text.substring(1, text.length - 1).trim()
        }

        // Comprobación de sufijo CR (Credit = positivo) / DB (Debit = negativo)
        if (text.endsWith("DB", ignoreCase = true) || text.endsWith("DR", ignoreCase = true)) {
            isNegative = true
            text = text.substring(0, text.length - 2).trim()
        } else if (text.endsWith("CR", ignoreCase = true)) {
            text = text.substring(0, text.length - 2).trim()
        }

        // Signos + o -
        if (text.startsWith("-")) {
            isNegative = true
            text = text.removePrefix("-").trim()
        } else if (text.startsWith("+")) {
            text = text.removePrefix("+").trim()
        }

        if (text.endsWith("-")) {
            isNegative = true
            text = text.removeSuffix("-").trim()
        }

        // Eliminar espacios intermedios
        text = text.replace(" ", "")

        if (text.isBlank()) return null

        // Detección de separadores decimales y de miles
        val lastComma = text.lastIndexOf(',')
        val lastDot = text.lastIndexOf('.')

        val normalizedNumberString = when {
            preferredDecimalSeparator == ',' -> {
                // Coma decimal forzada: puntos son miles
                text.replace(".", "").replace(',', '.')
            }
            preferredDecimalSeparator == '.' -> {
                // Punto decimal forzado: comas son miles
                text.replace(",", "")
            }
            lastComma != -1 && lastDot != -1 -> {
                // Ambos presentes: el último determina la parte decimal
                if (lastComma > lastDot) {
                    // Formato europeo: 1.234,56
                    text.replace(".", "").replace(',', '.')
                } else {
                    // Formato anglosajón: 1,234.56
                    text.replace(",", "")
                }
            }
            lastComma != -1 -> {
                // Solo comas presentes: ej. "1234,56" o "1,234"
                val decimalDigitsCount = text.length - 1 - lastComma
                if (decimalDigitsCount == 3 && text.count { it == ',' } > 1) {
                    // Múltiples comas como separador de miles: 1,000,000
                    text.replace(",", "")
                } else {
                    // Coma decimal europea
                    text.replace(',', '.')
                }
            }
            lastDot != -1 -> {
                // Solo puntos presentes: ej. "1234.56" o "1.234"
                val decimalDigitsCount = text.length - 1 - lastDot
                if (decimalDigitsCount == 3 && text.count { it == '.' } > 1) {
                    // Múltiples puntos como separador de miles: 1.000.000
                    text.replace(".", "")
                } else {
                    // Punto decimal
                    text
                }
            }
            else -> text
        }

        return try {
            val bd = BigDecimal(normalizedNumberString)
            val signed = if (isNegative) bd.negate() else bd
            signed.setScale(2, RoundingMode.HALF_UP)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Resuelve importe desde dos columnas separadas: Debe/Cargo y Haber/Abono
     */
    fun resolveDebitCredit(
        debitRaw: String?,
        creditRaw: String?,
        decimalSeparator: Char? = null
    ): BigDecimal? {
        val debit = parseAmount(debitRaw, decimalSeparator)
        val credit = parseAmount(creditRaw, decimalSeparator)

        return when {
            debit != null && debit.compareTo(BigDecimal.ZERO) != 0 -> {
                // El débito/cargo es un gasto (negativo)
                if (debit > BigDecimal.ZERO) debit.negate() else debit
            }
            credit != null && credit.compareTo(BigDecimal.ZERO) != 0 -> {
                // El crédito/abono es un ingreso (positivo)
                if (credit < BigDecimal.ZERO) credit.abs() else credit
            }
            else -> null
        }
    }
}
