package com.oryctes.micontabilidad.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.oryctes.micontabilidad.data.local.entity.AccountEntity
import com.oryctes.micontabilidad.data.local.entity.CategoryEntity
import com.oryctes.micontabilidad.data.local.entity.ImportBatchEntity
import com.oryctes.micontabilidad.data.local.entity.ImportProfileEntity
import com.oryctes.micontabilidad.data.local.entity.SourceFileEntity
import com.oryctes.micontabilidad.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY isArchived ASC, alias ASC")
    fun getAllAccountsFlow(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE isArchived = 0 ORDER BY alias ASC")
    fun getActiveAccountsFlow(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getAccountById(id: Long): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: AccountEntity): Long

    @Update
    suspend fun updateAccount(account: AccountEntity)

    @Query("UPDATE accounts SET isArchived = :isArchived WHERE id = :id")
    suspend fun setArchived(id: Long, isArchived: Boolean)

    @Delete
    suspend fun deleteAccount(account: AccountEntity)
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE reviewStatus != 'DISCARDED' ORDER BY operationDate DESC, id DESC")
    fun getAllTransactionsFlow(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE accountId = :accountId AND reviewStatus != 'DISCARDED' ORDER BY operationDate DESC, id DESC")
    fun getTransactionsByAccountFlow(accountId: Long): Flow<List<TransactionEntity>>

    @Query("""
        SELECT * FROM transactions 
        WHERE (:accountId IS NULL OR accountId = :accountId)
          AND (:startDate IS NULL OR operationDate >= :startDate)
          AND (:endDate IS NULL OR operationDate <= :endDate)
          AND (
              :searchQuery IS NULL 
              OR concept LIKE '%' || :searchQuery || '%'
              OR amount LIKE '%' || :searchQuery || '%'
          )
          AND reviewStatus != 'DISCARDED'
        ORDER BY operationDate DESC, id DESC
    """)
    fun getFilteredTransactionsFlow(
        accountId: Long?,
        startDate: LocalDate?,
        endDate: LocalDate?,
        searchQuery: String?
    ): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE accountId = :accountId AND operationDate BETWEEN :start AND :end AND reviewStatus != 'DISCARDED'")
    suspend fun getTransactionsBetweenDates(accountId: Long, start: LocalDate, end: LocalDate): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE accountId = :accountId AND reviewStatus != 'DISCARDED'")
    suspend fun getTransactionsByAccount(accountId: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransactionById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE normalizedHash = :hash AND accountId = :accountId LIMIT 1")
    suspend fun findByNormalizedHash(accountId: Long, hash: String): TransactionEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTransactions(transactions: List<TransactionEntity>): List<Long>

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Delete
    suspend fun deleteTransaction(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE importBatchId = :batchId")
    suspend fun deleteByBatchId(batchId: Long)

    @Query("DELETE FROM transactions WHERE reviewStatus = 'DISCARDED'")
    suspend fun deleteDiscardedTransactions()

    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions()

    @Query("DELETE FROM transactions WHERE accountId = :accountId")
    suspend fun deleteTransactionsByAccount(accountId: Long)

    @Query("DELETE FROM transactions WHERE categoryId = :categoryId")
    suspend fun deleteTransactionsByCategory(categoryId: Long)

    @Query("DELETE FROM transactions WHERE accountId = :accountId AND categoryId = :categoryId")
    suspend fun deleteTransactionsByAccountAndCategory(accountId: Long, categoryId: Long)

    @Query("SELECT * FROM transactions WHERE categoryId IS NULL")
    suspend fun getUncategorizedTransactions(): List<TransactionEntity>
}

@Dao
interface SourceFileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSourceFile(sourceFile: SourceFileEntity): Long

    @Query("SELECT * FROM source_files WHERE fileHash = :hash LIMIT 1")
    suspend fun findByHash(hash: String): SourceFileEntity?

    @Query("SELECT * FROM source_files WHERE id = :id")
    suspend fun getById(id: Long): SourceFileEntity?

    @Query("DELETE FROM source_files")
    suspend fun deleteAllSourceFiles()
}

@Dao
interface ImportBatchDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatch(batch: ImportBatchEntity): Long

    @Update
    suspend fun updateBatch(batch: ImportBatchEntity)

    @Query("SELECT * FROM import_batches ORDER BY importedAt DESC")
    fun getAllBatchesFlow(): Flow<List<ImportBatchEntity>>

    @Query("SELECT * FROM import_batches ORDER BY importedAt DESC LIMIT 1")
    suspend fun getLatestBatch(): ImportBatchEntity?

    @Query("SELECT * FROM import_batches WHERE id = :id")
    suspend fun getById(id: Long): ImportBatchEntity?

    @Delete
    suspend fun deleteBatch(batch: ImportBatchEntity)

    @Query("DELETE FROM import_batches")
    suspend fun deleteAllBatches()

    @Query("DELETE FROM import_batches WHERE accountId = :accountId")
    suspend fun deleteBatchesByAccount(accountId: Long)
}

@Dao
interface ImportProfileDao {
    @Query("SELECT * FROM import_profiles ORDER BY name ASC")
    fun getAllProfilesFlow(): Flow<List<ImportProfileEntity>>

    @Query("SELECT * FROM import_profiles WHERE entity = :entity LIMIT 1")
    suspend fun getByEntity(entity: String): ImportProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: ImportProfileEntity): Long

    @Delete
    suspend fun deleteProfile(profile: ImportProfileEntity)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun getAllCategoriesFlow(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories")
    suspend fun getAllCategories(): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity): Long

    @Update
    suspend fun updateCategory(category: CategoryEntity)

    @Delete
    suspend fun deleteCategory(category: CategoryEntity)
}
