package com.oryctes.micontabilidad.domain.model

data class ColumnMapping(
    val operationDateIndex: Int = -1,
    val valueDateIndex: Int = -1,
    val conceptIndex: Int = -1,
    val conceptDetailsIndex: Int = -1,  // Concepto ampliado / Más datos / Subcategoría
    val amountIndex: Int = -1,
    val debitIndex: Int = -1,   // Cargo / Debe
    val creditIndex: Int = -1,  // Abono / Haber
    val balanceIndex: Int = -1,
    val dateFormat: String? = null,        // dd/MM/yyyy, yyyy-MM-dd, etc.
    val decimalSeparator: Char? = null,    // ',' o '.'
    val skipHeaderRows: Int = 1
) {
    fun isValid(): Boolean {
        val hasConcept = conceptIndex >= 0
        val hasDate = operationDateIndex >= 0
        val hasAmount = amountIndex >= 0 || (debitIndex >= 0 && creditIndex >= 0)
        return hasConcept && hasDate && hasAmount
    }
}

data class ImportProfile(
    val id: Long = 0,
    val name: String,
    val entity: String,
    val delimiter: String = ";",
    val charset: String = "UTF-8",
    val mapping: ColumnMapping = ColumnMapping()
)
