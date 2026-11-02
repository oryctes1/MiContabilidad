package com.oryctes.micontabilidad.domain.normalizer

import com.oryctes.micontabilidad.domain.model.Transaction
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max

object DeduplicationEngine {

    /**
     * Normaliza un concepto eliminando espacios extras, signos de puntuación repetidos
     * y convirtiendo a minúsculas para un hash reproducible.
     */
    fun normalizeConcept(concept: String): String {
        return concept.lowercase()
            .replace(Regex("[^a-z0-9áéíóúñü]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Calcula el hash SHA-256 normalizado para deduplicación idempotente.
     */
    fun computeNormalizedHash(
        accountId: Long,
        operationDate: LocalDate,
        concept: String,
        amount: BigDecimal,
        balance: BigDecimal?,
        currency: String = "EUR"
    ): String {
        val normConcept = normalizeConcept(concept)
        val normAmount = amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
        val normBalance = balance?.setScale(2, java.math.RoundingMode.HALF_UP)?.toPlainString() ?: "NO_BALANCE"
        val normCurrency = currency.trim().uppercase()

        val rawSignature = "$accountId|$operationDate|$normConcept|$normAmount|$normBalance|$normCurrency"

        val bytes = MessageDigest.getInstance("SHA-256").digest(rawSignature.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Detección difusa de posibles duplicados (solapamientos con pequeñas diferencias):
     * - Misma cuenta
     * - Misma cuantía exacta (amount)
     * - Fechas de operación muy próximas (<= 3 días naturales de diferencia)
     * - Similitud de concepto >= 0.70 (usando similitud por tokens y Levenshtein)
     */
    fun isFuzzyDuplicate(
        candidate: Transaction,
        existingList: List<Transaction>
    ): Boolean {
        for (existing in existingList) {
            if (candidate.normalizedHash == existing.normalizedHash) {
                // Es un duplicado exacto, no difuso
                continue
            }
            if (candidate.amount.compareTo(existing.amount) != 0) {
                continue
            }

            val daysDiff = ChronoUnit.DAYS.between(
                minOf(candidate.operationDate, existing.operationDate),
                maxOf(candidate.operationDate, existing.operationDate)
            )
            if (daysDiff > 3) {
                continue
            }

            val c1 = normalizeConcept(candidate.concept)
            val c2 = normalizeConcept(existing.concept)

            if (c1 == c2) {
                return true
            }

            val similarity = calculateSimilarity(c1, c2)
            if (similarity >= 0.55) {
                return true
            }
        }
        return false
    }

    /**
     * Calcula la similitud de texto combinando coincidencia de tokens (Jaccard)
     * y distancia Levenshtein.
     */
    fun calculateSimilarity(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        if (s1.isEmpty() || s2.isEmpty()) return 0.0

        var score = 0.0
        val tokens1 = s1.split(" ").filter { it.isNotBlank() }.toSet()
        val tokens2 = s2.split(" ").filter { it.isNotBlank() }.toSet()

        if (tokens1.isNotEmpty() && tokens2.isNotEmpty()) {
            val intersection = tokens1.intersect(tokens2).size
            val union = tokens1.union(tokens2).size
            score = maxOf(score, intersection.toDouble() / union.toDouble())
        }

        // Levenshtein como alternativa para strings cortos
        val maxLen = max(s1.length, s2.length)
        val distance = levenshteinDistance(s1, s2)
        val levScore = (maxLen - distance).toDouble() / maxLen.toDouble()
        score = maxOf(score, levScore)

        return score
    }

    private fun levenshteinDistance(lhs: CharSequence, rhs: CharSequence): Int {
        var prev = IntArray(rhs.length + 1) { it }
        var current = IntArray(rhs.length + 1)

        for (i in lhs.indices) {
            current[0] = i + 1
            for (j in rhs.indices) {
                val cost = if (lhs[i] == rhs[j]) 0 else 1
                current[j + 1] = minOf(
                    current[j] + 1,
                    prev[j + 1] + 1,
                    prev[j] + cost
                )
            }
            val temp = prev
            prev = current
            current = temp
        }
        return prev[rhs.length]
    }
}
