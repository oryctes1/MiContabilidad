package com.oryctes.micontabilidad.di

import com.oryctes.micontabilidad.data.local.AppDatabase
import com.oryctes.micontabilidad.data.parser.ParserFactory
import com.oryctes.micontabilidad.data.repository.AccountRepositoryImpl
import com.oryctes.micontabilidad.data.repository.ImportRepositoryImpl
import com.oryctes.micontabilidad.data.repository.TransactionRepositoryImpl
import com.oryctes.micontabilidad.domain.repository.AccountRepository
import com.oryctes.micontabilidad.domain.repository.ImportRepository
import com.oryctes.micontabilidad.domain.repository.TransactionRepository
import com.oryctes.micontabilidad.ui.accounts.AccountsViewModel
import com.oryctes.micontabilidad.ui.categories.CategoriesViewModel
import com.oryctes.micontabilidad.ui.dashboard.DashboardViewModel
import com.oryctes.micontabilidad.ui.import.ImportViewModel
import com.oryctes.micontabilidad.ui.settings.SettingsViewModel
import com.oryctes.micontabilidad.ui.summaries.SummariesViewModel
import com.oryctes.micontabilidad.ui.transactions.TransactionsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    // Base de datos Room
    single { AppDatabase.buildDatabase(get()) }
    single { get<AppDatabase>().accountDao() }
    single { get<AppDatabase>().transactionDao() }
    single { get<AppDatabase>().sourceFileDao() }
    single { get<AppDatabase>().importBatchDao() }
    single { get<AppDatabase>().importProfileDao() }
    single { get<AppDatabase>().categoryDao() }

    // Factoría de parsers
    single { ParserFactory(get()) }

    // Repositorios
    single<AccountRepository> { AccountRepositoryImpl(get()) }
    single<TransactionRepository> { TransactionRepositoryImpl(get(), get(), get(), get()) }
    single<ImportRepository> {
        ImportRepositoryImpl(
            context = get(),
            accountDao = get(),
            transactionDao = get(),
            sourceFileDao = get(),
            importBatchDao = get(),
            importProfileDao = get(),
            categoryDao = get(),
            parserFactory = get()
        )
    }

    // ViewModels
    viewModel { DashboardViewModel(get(), get()) }
    viewModel { AccountsViewModel(get(), get()) }
    viewModel { TransactionsViewModel(get(), get()) }
    viewModel { ImportViewModel(get(), get(), get()) }
    viewModel { SummariesViewModel(get(), get()) }
    viewModel { CategoriesViewModel(get()) }
    viewModel { SettingsViewModel(get(), get()) }
}
