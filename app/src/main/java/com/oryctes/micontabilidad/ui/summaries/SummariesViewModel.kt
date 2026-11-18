package com.oryctes.micontabilidad.ui.summaries

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oryctes.micontabilidad.domain.model.Account
import com.oryctes.micontabilidad.domain.model.AccountType
import com.oryctes.micontabilidad.domain.model.AccountWithBalance
import com.oryctes.micontabilidad.domain.model.withBalance
import com.oryctes.micontabilidad.domain.model.sortedForDisplay
import com.oryctes.micontabilidad.domain.model.CategorySummary
import com.oryctes.micontabilidad.domain.model.InvestmentValuation
import com.oryctes.micontabilidad.domain.model.ValuationType
import com.oryctes.micontabilidad.domain.model.MonthlySummary
import com.oryctes.micontabilidad.domain.repository.AccountRepository
import com.oryctes.micontabilidad.domain.repository.TransactionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

private fun isHogarOrUtility(name: String): Boolean {
    val n = name.trim().lowercase()
    return n == "hogar" ||
           n == "agua" ||
           n == "luz" ||
           n == "gas" ||
           n == "comunicaciones" ||
           n == "comunicación" ||
           n == "comunicacion" ||
           n == "agua, luz y gas" ||
           n == "agua, luz, gas" ||
           n == "luz, gas y agua" ||
           n == "suministros"
}

data class CategoryOption(
    val id: String, // "CAT_id" o "HOGAR_GROUP" o "UNCATEGORIZED"
    val name: String,
    val colorHex: String
)

data class SummariesUiState(
    val selectedYear: Int = LocalDate.now().year,
    val selectedMonth: Int? = null, // null representa el año completo ("Todo el año")
    val selectedAccountId: Long? = null,
    val accounts: List<Account> = emptyList(),
    val monthlySummaries: List<MonthlySummary> = emptyList(),
    val categoryExpenses: List<CategorySummary> = emptyList(),
    val totalYearIncome: BigDecimal = BigDecimal.ZERO,
    val totalYearExpense: BigDecimal = BigDecimal.ZERO,
    val totalYearSavings: BigDecimal = BigDecimal.ZERO,
    val selectedInvestmentInfo: AccountWithBalance? = null,
    val investmentHistoryPoints: List<com.oryctes.micontabilidad.ui.components.BalanceDataPoint> = emptyList(),
    val availableCategoryOptions: List<CategoryOption> = emptyList(),
    val selectedCategoryOptionId: String? = null,
    val selectedCategoryMonthlyExpenses: List<BigDecimal> = List(12) { BigDecimal.ZERO },
    val selectedCategoryMonthlyAverage: BigDecimal? = null
)

private data class DataQuad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

private data class SelectionState(
    val year: Int,
    val month: Int?,
    val accountId: Long?,
    val categoryOptionId: String?
)

@OptIn(ExperimentalCoroutinesApi::class)
class SummariesViewModel(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _selectedYear = MutableStateFlow(LocalDate.now().year)
    private val _selectedMonth = MutableStateFlow<Int?>(null) // Por defecto: año completo
    private val _selectedAccountId = MutableStateFlow<Long?>(null)
    private val _selectedCategoryOptionId = MutableStateFlow<String?>(null)

    val selectedYear: StateFlow<Int> = _selectedYear.asStateFlow()
    val selectedMonth: StateFlow<Int?> = _selectedMonth.asStateFlow()
    val selectedAccountId: StateFlow<Long?> = _selectedAccountId.asStateFlow()
    val selectedCategoryOptionId: StateFlow<String?> = _selectedCategoryOptionId.asStateFlow()

    private val selectionState = combine(_selectedYear, _selectedMonth, _selectedAccountId, _selectedCategoryOptionId) { year, month, accId, catOptId ->
        SelectionState(year, month, accId, catOptId)
    }

    private val monthlySummariesFlow = selectionState.flatMapLatest { sel ->
        transactionRepository.getMonthlySummaries(sel.accountId, sel.year)
    }

    private val accountDataFlow = combine(
        accountRepository.getAllAccounts(),
        transactionRepository.getFilteredTransactions(null, null, null, null),
        accountRepository.getAllInvestmentValuations(),
        transactionRepository.getCategories()
    ) { accounts, allTxs, valuations, dbCategories ->
        DataQuad(accounts, allTxs, valuations, dbCategories)
    }

    val uiState: StateFlow<SummariesUiState> = combine(
        selectionState,
        accountDataFlow,
        monthlySummariesFlow
    ) { sel, dataQuad, monthly ->
        val (accounts, allTxs, valuations, dbCategories) = dataQuad
        val eligibleAccounts = accounts.filter {
            (!it.isArchived || it.type == AccountType.INVESTMENT) && it.type != AccountType.DEPOSIT
        }
        val sortedAccounts = eligibleAccounts.sortedForDisplay(allTxs)

        // Comprobar si la cuenta seleccionada es una inversión
        val selectedAccount = accounts.find { it.id == sel.accountId }
        val isInvestment = selectedAccount != null && selectedAccount.type == AccountType.INVESTMENT

        val selectedInvestmentInfo = if (isInvestment) {
            selectedAccount!!.withBalance(allTxs, valuations)
        } else null

        val investmentHistoryPoints = if (isInvestment) {
            val shortDateFmt = java.time.format.DateTimeFormatter.ofPattern("MM/yy")
            val fullDateFmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")
            val accVals = valuations.filter { it.accountId == selectedAccount.id }
                .sortedWith(compareBy<InvestmentValuation> { it.date }.thenBy { it.id })

            val points = mutableListOf<com.oryctes.micontabilidad.ui.components.BalanceDataPoint>()

            // Seguimiento acumulativo del valor de la inversión a lo largo del tiempo
            var runningValue = BigDecimal.ZERO

            // 1. Añadir el valor inicial de la inversión como punto de partida si existe initialAmount
            val initAmount = selectedAccount.initialAmount
            val initDate = selectedAccount.startDate
            if (initAmount != null && initAmount > BigDecimal.ZERO) {
                val pointDate = initDate ?: accVals.firstOrNull()?.date ?: LocalDate.now()
                val firstVal = accVals.firstOrNull()
                val shouldIncludeInitial = firstVal == null || pointDate.isBefore(firstVal.date) ||
                        (pointDate == firstVal.date && initAmount.compareTo(firstVal.value) != 0)

                if (shouldIncludeInitial) {
                    runningValue = initAmount
                    points.add(
                        com.oryctes.micontabilidad.ui.components.BalanceDataPoint(
                            label = pointDate.format(shortDateFmt),
                            fullDate = "${pointDate.format(fullDateFmt)} (Valor inicial)",
                            balance = runningValue
                        )
                    )
                }
            }

            // 2. Procesar cronológicamente todas las valoraciones y aportaciones:
            // - Si es APORTACIÓN (CONTRIBUTION): incrementa el valor acumulado de la inversión.
            // - Si es VALORACIÓN (VALUATION) o LIQUIDACIÓN (LIQUIDATION): actualiza el valor total de mercado en esa fecha.
            accVals.forEach { v ->
                when (v.type) {
                    ValuationType.CONTRIBUTION -> {
                        runningValue = runningValue.add(v.value)
                        points.add(
                            com.oryctes.micontabilidad.ui.components.BalanceDataPoint(
                                label = v.date.format(shortDateFmt),
                                fullDate = "${v.date.format(fullDateFmt)} (+${v.value.toPlainString()} € Aportación)",
                                balance = runningValue
                            )
                        )
                    }
                    ValuationType.VALUATION, ValuationType.LIQUIDATION -> {
                        runningValue = v.value
                        points.add(
                            com.oryctes.micontabilidad.ui.components.BalanceDataPoint(
                                label = v.date.format(shortDateFmt),
                                fullDate = "${v.date.format(fullDateFmt)} (${v.type.displayName})",
                                balance = runningValue
                            )
                        )
                    }
                }
            }

            // Si points sigue vacío (sin initialAmount ni valoraciones), usar último balance conocido
            if (points.isEmpty()) {
                val initVal = selectedInvestmentInfo?.lastBalance
                    ?: selectedAccount.initialAmount
                    ?: BigDecimal.ZERO
                val date = selectedAccount.startDate ?: LocalDate.now()
                listOf(
                    com.oryctes.micontabilidad.ui.components.BalanceDataPoint(
                        label = date.format(shortDateFmt),
                        fullDate = date.format(fullDateFmt),
                        balance = initVal
                    )
                )
            } else {
                points
            }
        } else {
            emptyList()
        }

        val yearIncome = monthly.fold(BigDecimal.ZERO) { acc, m -> acc.add(m.totalIncome) }
        val yearExpense = monthly.fold(BigDecimal.ZERO) { acc, m -> acc.add(m.totalExpense) }
        val yearSavings = yearIncome.subtract(yearExpense)

        // Transacciones del año de cuentas seleccionadas (o todas)
        val allYearTxs = allTxs.filter { tx ->
            !tx.isExcludedFromTotals &&
            tx.operationDate.year == sel.year &&
            (sel.accountId == null || tx.accountId == sel.accountId)
        }
        val yearExpenseTxs = allYearTxs.filter { it.amount < BigDecimal.ZERO }

        // Calcular desglose de categorías según el mes seleccionado o todo el año
        val targetExpenseTxs = if (sel.month == null) {
            yearExpenseTxs
        } else {
            yearExpenseTxs.filter { it.operationDate.monthValue == sel.month }
        }

        val totalPeriodExpense = targetExpenseTxs.fold(BigDecimal.ZERO) { acc, t -> acc.add(t.amount.abs()) }
        val categoryMap = dbCategories.associateBy { it.id }

        val calculatedCategories = if (totalPeriodExpense > BigDecimal.ZERO) {
            targetExpenseTxs.groupBy { it.categoryId }.map { (catId, txs) ->
                val catTotal = txs.fold(BigDecimal.ZERO) { acc, t -> acc.add(t.amount.abs()) }
                val cat = catId?.let { categoryMap[it] }
                val name = cat?.name ?: "Sin categoría / Otros"
                val color = cat?.colorHex ?: "#9E9E9E"
                val pct = catTotal.divide(totalPeriodExpense, 4, java.math.RoundingMode.HALF_UP).toFloat() * 100f
                CategorySummary(
                    categoryName = name,
                    colorHex = color,
                    totalAmount = catTotal,
                    percentage = pct
                )
            }.sortedByDescending { it.totalAmount }
        } else {
            emptyList()
        }

        // Agrupar en la categoría Hogar también agua, luz, gas y comunicaciones exclusivamente para informes
        val chartCategories = calculatedCategories.groupHogarAndUtilities()

        // Construir opciones de categorías para el carrusel de Desglose por Categoría:
        // Categorías de Gasto (y Bizum si tiene gastos): orden decreciente de gasto total anual.
        // Las tres categorías de ingresos específicas ("Nóminas", "Intereses", "Otros ingresos", o cualquier INCOME excepto Bizum):
        // estrictamente al final del carrusel, ordenadas de mayor a menor importe.
        val hasHogarOrUtility = dbCategories.any { isHogarOrUtility(it.name) }
        val hogarCat = dbCategories.find { it.name.trim().equals("hogar", ignoreCase = true) }
        val hogarColorHex = hogarCat?.colorHex ?: "#6D4C41"

        fun isDedicatedIncomeCategory(cat: com.oryctes.micontabilidad.domain.model.Category): Boolean {
            val n = cat.name.trim().lowercase()
            return n == "nóminas" || n == "nominas" || n == "intereses" || n == "otros ingresos" ||
                   (cat.type == com.oryctes.micontabilidad.domain.model.CategoryType.INCOME && n != "bizum")
        }

        // Calcular total anual de cada categoría de gasto
        val expenseOptionsWithAmount = mutableListOf<Pair<CategoryOption, BigDecimal>>()
        if (hasHogarOrUtility) {
            val hogarYearTotal = yearExpenseTxs.filter { tx ->
                val catName = tx.categoryId?.let { categoryMap[it]?.name } ?: ""
                isHogarOrUtility(catName)
            }.fold(BigDecimal.ZERO) { acc, tx -> acc.add(tx.amount.abs()) }
            expenseOptionsWithAmount.add(CategoryOption("HOGAR_GROUP", "Hogar (y suministros)", hogarColorHex) to hogarYearTotal)
        }

        // Categorías que NO son ingresos puros ni parte de hogar (incluye categorías de gasto habituales, ahorro y Bizum)
        dbCategories.filter { !isDedicatedIncomeCategory(it) && !isHogarOrUtility(it.name) }.forEach { cat ->
            val catYearTotal = yearExpenseTxs.filter { it.categoryId == cat.id }
                .fold(BigDecimal.ZERO) { acc, tx -> acc.add(tx.amount.abs()) }
            expenseOptionsWithAmount.add(CategoryOption("CAT_${cat.id}", cat.name, cat.colorHex) to catYearTotal)
        }

        val uncategorizedYearTotal = yearExpenseTxs.filter { it.categoryId == null }
            .fold(BigDecimal.ZERO) { acc, tx -> acc.add(tx.amount.abs()) }
        if (uncategorizedYearTotal > BigDecimal.ZERO) {
            expenseOptionsWithAmount.add(CategoryOption("UNCATEGORIZED", "Sin categoría / Otros", "#9E9E9E") to uncategorizedYearTotal)
        }

        // Ordenar categorías de gasto en orden decreciente de gasto anual
        val sortedExpenseOptions = expenseOptionsWithAmount.sortedByDescending { it.second }.map { it.first }

        // Categorías de ingreso (Nóminas, Intereses, Otros ingresos):
        // Se colocan al final del carrusel, ordenadas de mayor a menor cuantía total anual
        val yearIncomeTxs = allYearTxs.filter { it.amount > BigDecimal.ZERO }
        val incomeCategories = dbCategories.filter { isDedicatedIncomeCategory(it) }
        val incomeOptionsWithAmount = incomeCategories.map { cat ->
            val catIncomeTotal = yearIncomeTxs.filter { it.categoryId == cat.id }
                .fold(BigDecimal.ZERO) { acc, tx -> acc.add(tx.amount) }
            CategoryOption("CAT_${cat.id}", cat.name, cat.colorHex) to catIncomeTotal
        }
        val sortedIncomeOptions = incomeOptionsWithAmount.sortedByDescending { it.second }.map { it.first }

        val options = sortedExpenseOptions + sortedIncomeOptions

        // Seleccionar la categoría activa (por defecto la primera disponible o la seleccionada previamente)
        val activeCategoryOptionId = if (options.any { it.id == sel.categoryOptionId }) {
            sel.categoryOptionId
        } else {
            options.firstOrNull()?.id
        }

        // Calcular importes mensuales (1..12) para la categoría activa
        val isSelectedOptionIncome = activeCategoryOptionId?.let { optId ->
            if (optId.startsWith("CAT_")) {
                val catId = optId.removePrefix("CAT_").toLongOrNull()
                catId?.let { categoryMap[it] }?.let { isDedicatedIncomeCategory(it) } ?: false
            } else false
        } ?: false

        val targetYearTxsForCategory = if (isSelectedOptionIncome) allYearTxs.filter { it.amount > BigDecimal.ZERO } else yearExpenseTxs

        val monthlyExpensesForSelectedCategory = if (activeCategoryOptionId != null) {
            val matchingTxs = targetYearTxsForCategory.filter { tx ->
                when {
                    activeCategoryOptionId == "HOGAR_GROUP" -> {
                        val catName = tx.categoryId?.let { categoryMap[it]?.name } ?: ""
                        isHogarOrUtility(catName)
                    }
                    activeCategoryOptionId.startsWith("CAT_") -> {
                        val targetCatId = activeCategoryOptionId.removePrefix("CAT_").toLongOrNull()
                        tx.categoryId == targetCatId
                    }
                    activeCategoryOptionId == "UNCATEGORIZED" -> tx.categoryId == null
                    else -> false
                }
            }

            val byMonth = matchingTxs.groupBy { it.operationDate.monthValue }
            (1..12).map { m ->
                val txsInMonth = byMonth[m] ?: emptyList()
                txsInMonth.fold(BigDecimal.ZERO) { acc, tx -> acc.add(tx.amount.abs()) }
            }
        } else {
            List(12) { BigDecimal.ZERO }
        }

        // Calcular la media mensual del gasto/ingreso para los meses hasta los que hay datos de extractos
        val maxMonthWithDataInYear = allYearTxs.maxOfOrNull { it.operationDate.monthValue } ?: 0
        val selectedCategoryMonthlyAverage = if (maxMonthWithDataInYear > 0) {
            val totalCategoryInPeriod = monthlyExpensesForSelectedCategory.take(maxMonthWithDataInYear)
                .fold(BigDecimal.ZERO) { acc, b -> acc.add(b) }
            totalCategoryInPeriod.divide(BigDecimal(maxMonthWithDataInYear), 2, java.math.RoundingMode.HALF_UP)
        } else null

        SummariesUiState(
            selectedYear = sel.year,
            selectedMonth = sel.month,
            selectedAccountId = sel.accountId,
            accounts = sortedAccounts,
            monthlySummaries = monthly,
            categoryExpenses = chartCategories,
            totalYearIncome = yearIncome,
            totalYearExpense = yearExpense,
            totalYearSavings = yearSavings,
            selectedInvestmentInfo = selectedInvestmentInfo,
            investmentHistoryPoints = investmentHistoryPoints,
            availableCategoryOptions = options,
            selectedCategoryOptionId = activeCategoryOptionId,
            selectedCategoryMonthlyExpenses = monthlyExpensesForSelectedCategory,
            selectedCategoryMonthlyAverage = selectedCategoryMonthlyAverage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SummariesUiState()
    )

    fun setYear(year: Int) {
        _selectedYear.value = year
    }

    fun setMonth(month: Int?) {
        _selectedMonth.value = month
    }

    fun setAccountId(accountId: Long?) {
        _selectedAccountId.value = accountId
    }

    fun setCategoryOptionId(categoryOptionId: String) {
        _selectedCategoryOptionId.value = categoryOptionId
    }
}


/**
 * Agrupa en la categoría 'Hogar' los gastos de agua, luz, gas y comunicaciones
 * exclusivamente para los gráficos de gastos de la pestaña Informes.
 */
private fun List<CategorySummary>.groupHogarAndUtilities(): List<CategorySummary> {
    if (this.isEmpty()) return this

    val totalExpense = this.fold(BigDecimal.ZERO) { acc, c -> acc.add(c.totalAmount) }
    if (totalExpense.compareTo(BigDecimal.ZERO) == 0) return this

    fun isHogarOrUtility(name: String): Boolean {
        val n = name.trim().lowercase()
        return n == "hogar" ||
               n == "agua" ||
               n == "luz" ||
               n == "gas" ||
               n == "comunicaciones" ||
               n == "comunicación" ||
               n == "comunicacion" ||
               n == "agua, luz y gas" ||
               n == "agua, luz, gas" ||
               n == "luz, gas y agua" ||
               n == "suministros"
    }

    val (hogarAndUtilities, otherCategories) = this.partition { isHogarOrUtility(it.categoryName) }
    if (hogarAndUtilities.isEmpty()) {
        return this
    }

    val mergedTotal = hogarAndUtilities.fold(BigDecimal.ZERO) { acc, c -> acc.add(c.totalAmount) }
    val hogarOriginal = hogarAndUtilities.find { it.categoryName.trim().equals("hogar", ignoreCase = true) }
    val colorHex = hogarOriginal?.colorHex ?: "#6D4C41"

    val percentage = (mergedTotal.divide(totalExpense, 4, java.math.RoundingMode.HALF_UP).toFloat() * 100f)

    val mergedHogar = CategorySummary(
        categoryName = "Hogar",
        colorHex = colorHex,
        totalAmount = mergedTotal,
        percentage = percentage
    )

    return (otherCategories + mergedHogar).sortedByDescending { it.totalAmount }
}
