package com.oryctes.micontabilidad.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.oryctes.micontabilidad.domain.model.Account
import com.oryctes.micontabilidad.domain.model.AccountType
import com.oryctes.micontabilidad.domain.model.Category
import com.oryctes.micontabilidad.domain.model.ImportBatch
import com.oryctes.micontabilidad.domain.model.ImportProfile
import com.oryctes.micontabilidad.domain.model.ReviewStatus
import com.oryctes.micontabilidad.domain.model.SourceFile
import com.oryctes.micontabilidad.domain.model.Transaction
import java.math.BigDecimal
import java.time.LocalDate

@Entity(
    tableName = "accounts",
    indices = [Index(value = ["alias"])]
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val alias: String,
    val entity: String,
    val type: AccountType,
    val currency: String = "EUR",
    val last4: String? = null,
    val notes: String? = null,
    val isArchived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): Account = Account(
        id = id,
        alias = alias,
        entity = entity,
        type = type,
        currency = currency,
        last4 = last4,
        notes = notes,
        isArchived = isArchived,
        createdAt = createdAt
    )

    companion object {
        fun fromDomain(account: Account): AccountEntity = AccountEntity(
            id = account.id,
            alias = account.alias,
            entity = account.entity,
            type = account.type,
            currency = account.currency,
            last4 = account.last4,
            notes = account.notes,
            isArchived = account.isArchived,
            createdAt = account.createdAt
        )
    }
}

@Entity(
    tableName = "source_files",
    indices = [Index(value = ["fileHash"])]
)
data class SourceFileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val uri: String,
    val displayName: String,
    val mimeType: String,
    val fileHash: String,
    val importedAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): SourceFile = SourceFile(
        id = id,
        uri = uri,
        displayName = displayName,
        mimeType = mimeType,
        fileHash = fileHash,
        importedAt = importedAt
    )

    companion object {
        fun fromDomain(model: SourceFile): SourceFileEntity = SourceFileEntity(
            id = model.id,
            uri = model.uri,
            displayName = model.displayName,
            mimeType = model.mimeType,
            fileHash = model.fileHash,
            importedAt = model.importedAt
        )
    }
}

@Entity(
    tableName = "import_batches",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SourceFileEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceFileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["accountId"]),
        Index(value = ["sourceFileId"])
    ]
)
data class ImportBatchEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val accountId: Long,
    val sourceFileId: Long,
    val importedAt: Long = System.currentTimeMillis(),
    val status: String = "COMPLETED",
    val totalRows: Int = 0,
    val insertedCount: Int = 0,
    val duplicateCount: Int = 0,
    val possibleDuplicateCount: Int = 0,
    val errorCount: Int = 0
) {
    fun toDomain(): ImportBatch = ImportBatch(
        id = id,
        accountId = accountId,
        sourceFileId = sourceFileId,
        importedAt = importedAt,
        status = status,
        totalRows = totalRows,
        insertedCount = insertedCount,
        duplicateCount = duplicateCount,
        possibleDuplicateCount = possibleDuplicateCount,
        errorCount = errorCount
    )

    companion object {
        fun fromDomain(model: ImportBatch): ImportBatchEntity = ImportBatchEntity(
            id = model.id,
            accountId = model.accountId,
            sourceFileId = model.sourceFileId,
            importedAt = model.importedAt,
            status = model.status,
            totalRows = model.totalRows,
            insertedCount = model.insertedCount,
            duplicateCount = model.duplicateCount,
            possibleDuplicateCount = model.possibleDuplicateCount,
            errorCount = model.errorCount
        )
    }
}

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["accountId", "normalizedHash"], unique = true),
        Index(value = ["operationDate"]),
        Index(value = ["reviewStatus"])
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val accountId: Long,
    val operationDate: LocalDate,
    val valueDate: LocalDate? = null,
    val concept: String,
    val amount: BigDecimal,
    val balance: BigDecimal? = null,
    val currency: String = "EUR",
    val normalizedHash: String,
    val sourceFileId: Long? = null,
    val importBatchId: Long? = null,
    val isPossibleDuplicate: Boolean = false,
    val reviewStatus: ReviewStatus = ReviewStatus.CONFIRMED,
    val categoryId: Long? = null,
    val isCategoryManuallyAssigned: Boolean = false,
    val isExcludedFromTotals: Boolean = false,
    val isCreditCard: Boolean = false,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): Transaction = Transaction(
        id = id,
        accountId = accountId,
        operationDate = operationDate,
        valueDate = valueDate,
        concept = concept,
        amount = amount,
        balance = balance,
        currency = currency,
        normalizedHash = normalizedHash,
        sourceFileId = sourceFileId,
        importBatchId = importBatchId,
        isPossibleDuplicate = isPossibleDuplicate,
        reviewStatus = reviewStatus,
        categoryId = categoryId,
        isCategoryManuallyAssigned = isCategoryManuallyAssigned,
        isExcludedFromTotals = isExcludedFromTotals,
        isCreditCard = isCreditCard,
        notes = notes,
        createdAt = createdAt
    )

    companion object {
        fun fromDomain(model: Transaction): TransactionEntity = TransactionEntity(
            id = model.id,
            accountId = model.accountId,
            operationDate = model.operationDate,
            valueDate = model.valueDate,
            concept = model.concept,
            amount = model.amount,
            balance = model.balance,
            currency = model.currency,
            normalizedHash = model.normalizedHash,
            sourceFileId = model.sourceFileId,
            importBatchId = model.importBatchId,
            isPossibleDuplicate = model.isPossibleDuplicate,
            reviewStatus = model.reviewStatus,
            categoryId = model.categoryId,
            isCategoryManuallyAssigned = model.isCategoryManuallyAssigned,
            isExcludedFromTotals = model.isExcludedFromTotals,
            isCreditCard = model.isCreditCard,
            notes = model.notes,
            createdAt = model.createdAt
        )
    }
}

@Entity(
    tableName = "import_profiles",
    indices = [Index(value = ["name"])]
)
data class ImportProfileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val entity: String,
    val delimiter: String = ";",
    val charset: String = "UTF-8",
    val operationDateIndex: Int = -1,
    val valueDateIndex: Int = -1,
    val conceptIndex: Int = -1,
    val conceptDetailsIndex: Int = -1,
    val amountIndex: Int = -1,
    val debitIndex: Int = -1,
    val creditIndex: Int = -1,
    val balanceIndex: Int = -1,
    val dateFormat: String? = null,
    val decimalSeparator: Char? = null,
    val skipHeaderRows: Int = 1
)

@Entity(
    tableName = "categories",
    indices = [Index(value = ["name"], unique = true)]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val colorHex: String = "#3F51B5",
    val iconName: String = "category",
    val keywordsCsv: String = "",
    val type: String = "EXPENSE"
) {
    fun toDomain(): Category = Category(
        id = id,
        name = name,
        colorHex = colorHex,
        iconName = iconName,
        keywords = if (keywordsCsv.isBlank()) emptyList() else keywordsCsv.split(",").map { it.trim() },
        type = try {
            com.oryctes.micontabilidad.domain.model.CategoryType.valueOf(type)
        } catch (_: Exception) {
            when {
                name.equals("Ahorro", ignoreCase = true) -> com.oryctes.micontabilidad.domain.model.CategoryType.SAVING
                name.equals("Nóminas", ignoreCase = true) ||
                name.equals("Bizum", ignoreCase = true) ||
                name.equals("Intereses", ignoreCase = true) ||
                (name.equals("Extras", ignoreCase = true) && type == "INCOME") -> com.oryctes.micontabilidad.domain.model.CategoryType.INCOME
                else -> com.oryctes.micontabilidad.domain.model.CategoryType.EXPENSE
            }
        }
    )

    companion object {
        fun fromDomain(c: Category): CategoryEntity = CategoryEntity(
            id = c.id,
            name = c.name,
            colorHex = c.colorHex,
            iconName = c.iconName,
            keywordsCsv = c.keywords.joinToString(","),
            type = c.type.name
        )
    }
}
