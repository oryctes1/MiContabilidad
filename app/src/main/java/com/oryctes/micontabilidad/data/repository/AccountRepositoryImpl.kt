package com.oryctes.micontabilidad.data.repository

import com.oryctes.micontabilidad.data.local.dao.AccountDao
import com.oryctes.micontabilidad.data.local.entity.AccountEntity
import com.oryctes.micontabilidad.domain.model.Account
import com.oryctes.micontabilidad.domain.repository.AccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AccountRepositoryImpl(
    private val accountDao: AccountDao
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
}
