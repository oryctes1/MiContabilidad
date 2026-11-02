package com.oryctes.micontabilidad.domain.repository

import android.net.Uri
import com.oryctes.micontabilidad.data.parser.ParsedDocument
import com.oryctes.micontabilidad.data.parser.TabularRow
import com.oryctes.micontabilidad.domain.model.Account
import com.oryctes.micontabilidad.domain.model.Category
import com.oryctes.micontabilidad.domain.model.CategorySummary
import com.oryctes.micontabilidad.domain.model.ColumnMapping
import com.oryctes.micontabilidad.domain.model.ImportProfile
import com.oryctes.micontabilidad.domain.model.ImportResult
import com.oryctes.micontabilidad.domain.model.MonthlySummary
import com.oryctes.micontabilidad.domain.model.Transaction
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.YearMonth

interface AccountRepository {
    fun getAllAccounts(): Flow<List<Account>>
    fun getActiveAccounts(): Flow<List<Account>>
    suspend fun getAccountById(id: Long): Account?
    suspend fun createAccount(account: Account): Long
    suspend fun updateAccount(account: Account)
    suspend fun setArchived(id: Long, archived: Boolean)
    suspend fun deleteAccount(account: Account)
}

interface TransactionRepository {
    fun getFilteredTransactions(
        accountId: Long?,
        startDate: LocalDate?,
        endDate: LocalDate?,
        searchQuery: String?
    ): Flow<List<Transaction>>

    suspend fun getTransactionById(id: Long): Transaction?
    suspend fun updateTransaction(transaction: Transaction)
    suspend fun deleteTransaction(transaction: Transaction)
    suspend fun deleteDiscardedTransactions()
    suspend fun deleteAllTransactions()
    suspend fun deleteTransactionsByAccount(accountId: Long)
    suspend fun deleteTransactionsByCategory(categoryId: Long)
    suspend fun deleteTransactionsByAccountAndCategory(accountId: Long, categoryId: Long)
    suspend fun categorizeUncategorizedTransactions()

    fun getMonthlySummaries(accountId: Long?, year: Int): Flow<List<MonthlySummary>>
    fun getCategorySummaries(accountId: Long?, yearMonth: YearMonth): Flow<List<CategorySummary>>
    fun getCategories(): Flow<List<Category>>
    suspend fun exportToCsv(accountId: Long?): String
}

interface ImportRepository {
    suspend fun parseFile(
        uri: Uri,
        fileName: String,
        mimeType: String?,
        profile: ImportProfile?
    ): ParsedDocument

    suspend fun executeImport(
        accountId: Long,
        uri: Uri,
        fileName: String,
        mimeType: String?,
        mapping: ColumnMapping,
        rows: List<TabularRow>
    ): ImportResult

    fun getProfiles(): Flow<List<ImportProfile>>
    suspend fun saveProfile(profile: ImportProfile): Long
    suspend fun deleteProfile(profile: ImportProfile)
}
