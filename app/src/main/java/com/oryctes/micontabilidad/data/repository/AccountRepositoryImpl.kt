package com.oryctes.micontabilidad.data.repository

import com.oryctes.micontabilidad.data.local.dao.AccountDao
import com.oryctes.micontabilidad.data.local.entity.AccountEntity
import com.oryctes.micontabilidad.domain.model.Account
import com.oryctes.micontabilidad.domain.repository.AccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AccountRepositoryImpl(
    private val accountDao: AccountDao,
    private val investmentValuationDao: com.oryctes.micontabilidad.data.local.dao.InvestmentValuationDao
) : AccountRepository {

    override fun getAllAccounts(): Flow<List<Account>> {
        return accountDao.getAllAccountsFlow().map { list -> list.map { it.toDomain() } }
    }

    override fun getActiveAccounts(): Flow<List<Account>> {
        return accountDao.getActiveAccountsFlow().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getAccountById(id: Long): Account? {
        return accountDao.getAccountById(id)?.toDomain()
    }

    override suspend fun createAccount(account: Account): Long {
        return accountDao.insertAccount(AccountEntity.fromDomain(account))
    }

    override suspend fun updateAccount(account: Account) {
        accountDao.updateAccount(AccountEntity.fromDomain(account))
    }

    override suspend fun setArchived(id: Long, archived: Boolean) {
        accountDao.setArchived(id, archived)
    }

    override suspend fun deleteAccount(account: Account) {
        accountDao.deleteAccount(AccountEntity.fromDomain(account))
    }

    override fun getAllInvestmentValuations(): Flow<List<com.oryctes.micontabilidad.domain.model.InvestmentValuation>> {
        return investmentValuationDao.getAllValuationsFlow().map { list -> list.map { it.toDomain() } }
    }

    override fun getValuationsByAccount(accountId: Long): Flow<List<com.oryctes.micontabilidad.domain.model.InvestmentValuation>> {
        return investmentValuationDao.getValuationsByAccountFlow(accountId).map { list -> list.map { it.toDomain() } }
    }

    override suspend fun saveValuation(valuation: com.oryctes.micontabilidad.domain.model.InvestmentValuation): Long {
        val entity = com.oryctes.micontabilidad.data.local.entity.InvestmentValuationEntity.fromDomain(valuation)
        return if (valuation.id > 0) {
            investmentValuationDao.updateValuation(entity)
            valuation.id
        } else {
            investmentValuationDao.insertValuation(entity)
        }
    }

    override suspend fun deleteValuation(valuationId: Long) {
        investmentValuationDao.deleteValuationById(valuationId)
    }
}
