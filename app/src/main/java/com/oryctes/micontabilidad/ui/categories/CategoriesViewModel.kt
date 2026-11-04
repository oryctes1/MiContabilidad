package com.oryctes.micontabilidad.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oryctes.micontabilidad.domain.model.Category
import com.oryctes.micontabilidad.domain.model.CategoryType
import com.oryctes.micontabilidad.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CategoriesUiState(
    val categories: List<Category> = emptyList(),
    val incomeCategories: List<Category> = emptyList(),
    val expenseCategories: List<Category> = emptyList(),
    val savingCategories: List<Category> = emptyList(),
    val selectedCategory: Category? = null,
    val showEditDialog: Boolean = false,
    val isNewCategory: Boolean = false,
    val infoMessage: String? = null
)

class CategoriesViewModel(
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    private val _selectedCategory = MutableStateFlow<Category?>(null)
    private val _showEditDialog = MutableStateFlow(false)
    private val _isNewCategory = MutableStateFlow(false)
    private val _infoMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<CategoriesUiState> = combine(
        transactionRepository.getCategories(),
        _selectedCategory,
        _showEditDialog,
        _isNewCategory,
        _infoMessage
    ) { categories, selected, showDialog, isNew, message ->
        val income = categories.filter { it.type == CategoryType.INCOME }
        val expense = categories.filter { it.type == CategoryType.EXPENSE }
        val saving = categories.filter { it.type == CategoryType.SAVING }

        CategoriesUiState(
            categories = categories,
            incomeCategories = income,
            expenseCategories = expense,
            savingCategories = saving,
            selectedCategory = selected,
            showEditDialog = showDialog,
            isNewCategory = isNew,
            infoMessage = message
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CategoriesUiState()
    )

    init {
        // Asegurar que las categorías por defecto se carguen y apliquen
        viewModelScope.launch {
            transactionRepository.categorizeUncategorizedTransactions()
        }
    }

    fun openNewCategoryDialog(type: CategoryType = CategoryType.EXPENSE) {
        _selectedCategory.value = Category(
            name = "",
            colorHex = when (type) {
                CategoryType.INCOME -> "#2E7D32"
                CategoryType.EXPENSE -> "#C62828"
                CategoryType.SAVING -> "#26A69A"
            },
            type = type,
            keywords = emptyList()
        )
        _isNewCategory.value = true
        _showEditDialog.value = true
    }

    fun openEditCategoryDialog(category: Category) {
        _selectedCategory.value = category
        _isNewCategory.value = false
        _showEditDialog.value = true
    }

    fun dismissEditDialog() {
        _showEditDialog.value = false
        _selectedCategory.value = null
    }

    fun saveCategory(name: String, colorHex: String, type: CategoryType, keywords: List<String>) {
        val current = _selectedCategory.value ?: return
        if (name.isBlank()) return

        viewModelScope.launch {
            val updated = current.copy(
                name = name.trim(),
                colorHex = colorHex,
                type = type,
                keywords = keywords.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            )

            if (_isNewCategory.value) {
                transactionRepository.createCategory(updated)
                _infoMessage.value = "Categoría creada: ${updated.name}"
            } else {
                transactionRepository.updateCategory(updated)
                _infoMessage.value = "Categoría actualizada: ${updated.name}"
            }

            // Reclasificar apuntes para aplicar de inmediato las nuevas palabras clave
            transactionRepository.recategorizeAllTransactions()
            dismissEditDialog()
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            transactionRepository.deleteCategory(category)
            transactionRepository.recategorizeAllTransactions()
            _infoMessage.value = "Categoría eliminada: ${category.name}"
        }
    }

    fun recategorizeAll() {
        viewModelScope.launch {
            transactionRepository.recategorizeAllTransactions()
            _infoMessage.value = "Reorganización de categorías completada"
        }
    }

    fun clearInfoMessage() {
        _infoMessage.value = null
    }
}
