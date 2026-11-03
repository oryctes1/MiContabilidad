package com.oryctes.micontabilidad.domain.normalizer

import com.oryctes.micontabilidad.domain.model.Account
import com.oryctes.micontabilidad.domain.model.AccountType

data class DetectedAccountInfo(
    val suggestedAlias: String,
    val entity: String,
    val type: AccountType,
    val currency: String = "EUR",
    val last4: String? = null,
    val iban: String? = null
)

data class AccountMismatchInfo(
    val targetAccountId: Long,
    val targetAccountAlias: String,
    val detectedAccountName: String,
    val matchingExistingAccount: Account? = null,
    val detectedInfo: DetectedAccountInfo
)

object AccountDetector {

    private val KNOWN_ENTITIES = listOf(
        "BBVA" to "BBVA",
        "SANTANDER" to "Banco Santander",
        "CAIXABANK" to "CaixaBank",
        "LA CAIXA" to "CaixaBank",
        "CAIXA" to "CaixaBank",
        "ING" to "ING",
        "SABADELL" to "Banco Sabadell",
        "BANKINTER" to "Bankinter",
        "OPENBANK" to "Openbank",
        "ABANCA" to "Abanca",
        "KUTXABANK" to "Kutxabank",
        "IBERCAJA" to "Ibercaja",
        "UNICAJA" to "Unicaja Banco",
        "REVOLUT" to "Revolut",
        "N26" to "N26",
        "EVO" to "EVO Banco",
        "DEUTSCHE" to "Deutsche Bank",
        "PIBANK" to "Pibank",
        "IMAGIN" to "Imagin",
        "CAJAMAR" to "Cajamar",
        "RURAL" to "Caja Rural",
        "LABORAL" to "Laboral Kutxa",
        "TRIODOS" to "Triodos Bank"
    )

    private val BANK_CODES = mapOf(
        "0182" to "BBVA",
        "0049" to "Banco Santander",
        "2100" to "CaixaBank",
        "1465" to "ING",
        "0128" to "Bankinter",
        "0081" to "Banco Sabadell",
        "0073" to "Openbank",
        "2080" to "Abanca",
        "2095" to "Kutxabank",
        "2085" to "Ibercaja",
        "2103" to "Unicaja Banco",
        "0239" to "EVO Banco",
        "3058" to "Cajamar",
        "1491" to "Triodos Bank"
    )

    private val IBAN_REGEX = Regex("\\b([A-Z]{2}\\d{2}[0-9A-Z]{11,28})\\b")
    private val IBAN_SPACED_REGEX = Regex("\\b([A-Z]{2}\\d{2}(?:\\s+[0-9A-Z]{2,4}){3,7})\\b")
    private val CCC_REGEX = Regex("\\b(\\d{4})[\\s\\-](\\d{4})[\\s\\-](\\d{2})[\\s\\-](\\d{10})\\b")
    private val CCC_SPACED_REGEX = Regex("\\b(\\d{4})[\\s\\-](\\d{4})[\\s\\-](\\d{4})[\\s\\-](\\d{8})\\b")
    private val CCC_DIGITS_20_REGEX = Regex("\\b(\\d{4})\\s*(\\d{4})\\s*(\\d{2}|\\d{4})\\s*(\\d{8}|\\d{10})\\b")
    private val CARD_MASK_REGEX = Regex("(?:\\*{4,12}|[xX]{4,12}|•{4,12})\\s*([0-9]{4})")
    private val LAST4_REGEX = Regex("(?:terminad[ao] en|cuenta|tarjeta|acabada en|last 4|nº|num|contrato)?[:\\s]*\\b([0-9]{4})\\b", RegexOption.IGNORE_CASE)

    /**
     * Analiza el nombre del archivo, las cabeceras del documento y las primeras líneas del extracto
     * para inferir la entidad, el tipo de cuenta, el IBAN y los últimos 4 dígitos.
     */
    fun detectFromDocument(
        fileName: String,
        headers: List<String>,
        firstRowsSamples: List<List<String>>,
        documentHeaderLines: List<String> = emptyList()
    ): DetectedAccountInfo? {
        val allText = buildString {
            append(fileName)
            append(" ")
            documentHeaderLines.forEach { append(it).append(" ") }
            headers.forEach { append(it).append(" ") }
            firstRowsSamples.take(10).forEach { row ->
                row.forEach { append(it).append(" ") }
            }
        }

        val upperText = allText.uppercase()

        // 1. Detectar IBAN o CCC primero (para extraer código de entidad bancaria española)
        val rawIban = IBAN_REGEX.find(allText)?.value
            ?: IBAN_SPACED_REGEX.find(allText)?.value
        val iban = rawIban?.replace(" ", "")?.replace("-", "")

        val cccMatch = CCC_REGEX.find(allText)
            ?: CCC_SPACED_REGEX.find(allText)
            ?: CCC_DIGITS_20_REGEX.find(allText)

        // 2. Detectar Entidad
        var detectedEntity = "Banco"

        // Buscar primero por nombre o marca explícita en el nombre de archivo o cabecera
        for ((keyword, canonicalName) in KNOWN_ENTITIES) {
            if (upperText.contains(keyword)) {
                detectedEntity = canonicalName
                break
            }
        }

        // Si no se dedujo por nombre, deducir por código bancario en IBAN o CCC (ej. ESxx 0182 -> BBVA, ESxx 0049 -> Santander)
        if (detectedEntity == "Banco") {
            if (iban != null && iban.length >= 8 && iban.startsWith("ES", ignoreCase = true)) {
                val code = iban.substring(4, 8)
                val matchedName = BANK_CODES[code]
                if (matchedName != null) {
                    detectedEntity = matchedName
                }
            } else if (cccMatch != null) {
                val code = cccMatch.groupValues[1]
                val matchedName = BANK_CODES[code]
                if (matchedName != null) {
                    detectedEntity = matchedName
                }
            }
        }

        // 3. Detectar Tipo (Tarjeta de Crédito, Débito, Ahorro, o Cuenta Corriente)
        val detectedType = when {
            upperText.contains("TARJETA DE CREDITO") || upperText.contains("CREDIT CARD") -> AccountType.CREDIT_CARD
            upperText.contains("TARJETA DE DEBITO") || upperText.contains("DEBIT CARD") || upperText.contains("TARJETA") -> AccountType.CREDIT_CARD
            upperText.contains("AHORRO") || upperText.contains("SAVINGS") -> AccountType.SAVINGS
            upperText.contains("INVERSION") || upperText.contains("VALORES") -> AccountType.INVESTMENT
            else -> AccountType.CHECKING
        }

        // 4. Detectar últimos 4 dígitos (de tarjeta o de cuenta)
        var last4: String? = null
        if (iban != null && iban.length >= 4) {
            last4 = iban.takeLast(4)
        } else if (cccMatch != null) {
            last4 = cccMatch.groupValues[4].takeLast(4)
        } else {
            val cardMaskMatch = CARD_MASK_REGEX.find(allText)
            if (cardMaskMatch != null) {
                last4 = cardMaskMatch.groupValues[1]
            } else {
                val last4Match = LAST4_REGEX.find(fileName)
                if (last4Match != null) {
                    last4 = last4Match.groupValues[1]
                }
            }
        }

        // 5. Detectar moneda
        val currency = when {
            allText.contains("$") || upperText.contains("USD") -> "USD"
            allText.contains("£") || upperText.contains("GBP") -> "GBP"
            else -> "EUR"
        }

        // 6. Construir Alias sugerido
        val suggestedAlias = buildString {
            append(detectedEntity)
            append(" ")
            append(if (detectedType == AccountType.CREDIT_CARD) "Tarjeta" else "Cuenta")
            if (!last4.isNullOrBlank()) {
                append(" •••• $last4")
            }
        }

        // Solo retornamos si se detectó al menos la entidad o el IBAN/last4 o si es un archivo identificable
        val hasAnyClue = detectedEntity != "Banco" || last4 != null || iban != null || fileName.contains("extracto", ignoreCase = true)
        return if (hasAnyClue) {
            DetectedAccountInfo(
                suggestedAlias = suggestedAlias,
                entity = detectedEntity,
                type = detectedType,
                currency = currency,
                last4 = last4,
                iban = iban
            )
        } else {
            // Sugerencia genérica a partir del nombre del archivo
            val cleanName = fileName.substringBeforeLast(".").replace("_", " ").replace("-", " ")
            DetectedAccountInfo(
                suggestedAlias = cleanName.replaceFirstChar { it.uppercase() },
                entity = "Mi Entidad",
                type = AccountType.CHECKING,
                currency = "EUR"
            )
        }
    }

    /**
     * Comprueba si el extracto analizado apunta a una cuenta diferente de la seleccionada
     * para la importación.
     */
    fun checkAccountMismatch(
        targetAccount: Account?,
        detected: DetectedAccountInfo?,
        accounts: List<Account>
    ): AccountMismatchInfo? {
        if (targetAccount == null || detected == null) return null

        val hasConcreteClues = (detected.entity != "Mi Entidad" && detected.entity != "Banco") ||
                !detected.last4.isNullOrBlank() ||
                !detected.iban.isNullOrBlank()
        if (!hasConcreteClues) return null

        // 1. ¿Existe otra cuenta existente que coincida con lo detectado?
        val otherExistingMatch = accounts.find { acc ->
            acc.id != targetAccount.id && (
                (!detected.last4.isNullOrBlank() && acc.last4 == detected.last4) ||
                (detected.last4.isNullOrBlank() && acc.entity.equals(detected.entity, ignoreCase = true) && acc.type == detected.type)
            )
        }

        if (otherExistingMatch != null) {
            return AccountMismatchInfo(
                targetAccountId = targetAccount.id,
                targetAccountAlias = targetAccount.alias,
                detectedAccountName = otherExistingMatch.alias,
                matchingExistingAccount = otherExistingMatch,
                detectedInfo = detected
            )
        }

        // 2. Si no hay otra cuenta existente coincidente, ¿contradice lo detectado a la cuenta destino seleccionada?
        val last4Mismatch = !detected.last4.isNullOrBlank() &&
                !targetAccount.last4.isNullOrBlank() &&
                detected.last4 != targetAccount.last4

        val entityMismatch = detected.entity != "Banco" &&
                detected.entity != "Mi Entidad" &&
                targetAccount.entity.isNotBlank() &&
                !targetAccount.entity.equals(detected.entity, ignoreCase = true)

        val typeMismatch = detected.type != targetAccount.type &&
                (!detected.last4.isNullOrBlank() || entityMismatch)

        if (last4Mismatch || entityMismatch || typeMismatch) {
            return AccountMismatchInfo(
                targetAccountId = targetAccount.id,
                targetAccountAlias = targetAccount.alias,
                detectedAccountName = detected.suggestedAlias,
                matchingExistingAccount = null,
                detectedInfo = detected
            )
        }

        return null
    }
}
