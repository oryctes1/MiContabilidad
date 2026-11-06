package com.oryctes.micontabilidad.domain.util

import java.math.BigDecimal
import java.time.LocalDate

/**
 * Interfaz mínima o campos requeridos para ordenar cronológicamente
 * transacciones dentro de una misma fecha utilizando la correlación aritmética de saldos.
 */
interface BalanceAwareItem {
    val id: Long
    val operationDate: LocalDate
    val amount: BigDecimal
    val balance: BigDecimal?
}

object TransactionChronologyHelper {

    /**
     * Ordena una lista de elementos (de una misma o diferentes cuentas) de manera estrictamente cronológica:
     * - Fecha de operación ascendente
     * - Dentro de la misma fecha y cuenta:
     *   Reconstruye el orden cronológico basándose en `prev.balance + next.amount == next.balance`.
     *   Si alguna transacción no tiene saldo o no puede encadenarse, se desempatan de forma estable (menor id).
     */
    fun <T : BalanceAwareItem> sortChronologically(items: List<T>): List<T> {
        if (items.size <= 1) return items

        // Agrupar por fecha
        val byDate = items.groupBy { it.operationDate }
        val sortedDates = byDate.keys.sorted()

        val result = ArrayList<T>(items.size)
        for (date in sortedDates) {
            val itemsOnDate = byDate[date] ?: continue
            if (itemsOnDate.size == 1) {
                result.add(itemsOnDate[0])
            } else {
                result.addAll(sortIntraDay(itemsOnDate))
            }
        }
        return result
    }

    /**
     * Ordena una lista de transacciones en la UI (orden extracto bancario tradicional: más reciente primero):
     * - Fecha de operación descendente
     * - Dentro del mismo día: la última operación del día aparece primero (arriba del todo).
     */
    fun <T : BalanceAwareItem> sortForDisplay(items: List<T>): List<T> {
        if (items.size <= 1) return items

        val byDate = items.groupBy { it.operationDate }
        val sortedDates = byDate.keys.sortedDescending()

        val result = ArrayList<T>(items.size)
        for (date in sortedDates) {
            val itemsOnDate = byDate[date] ?: continue
            if (itemsOnDate.size == 1) {
                result.add(itemsOnDate[0])
            } else {
                // Orden cronológico del día invertido: el último movimiento del día va primero
                val chrono = sortIntraDay(itemsOnDate)
                result.addAll(chrono.reversed())
            }
        }
        return result
    }

    /**
     * Ordena transacciones de un mismo día de forma cronológica (del primer movimiento al último del día).
     */
    private fun <T : BalanceAwareItem> sortIntraDay(items: List<T>): List<T> {
        val withBalance = items.filter { it.balance != null }
        val withoutBalance = items.filter { it.balance == null }

        if (withBalance.size <= 1) {
            // Si solo hay uno o cero con saldo, ordenar por id
            return items.sortedBy { it.id }
        }

        // Construir grafo de precedencia inmediata entre los que tienen balance:
        // 'a' precede inmediatamente a 'b' si: a.balance + b.amount == b.balance
        val nextMap = mutableMapOf<Long, T>()
        val prevMap = mutableMapOf<Long, T>()

        for (a in withBalance) {
            for (b in withBalance) {
                if (a.id != b.id) {
                    val expectedBal = a.balance!!.add(b.amount)
                    if (expectedBal.compareTo(b.balance) == 0) {
                        nextMap[a.id] = b
                        prevMap[b.id] = a
                    }
                }
            }
        }

        // Encontrar cabezas de cadena (elementos que no tienen predecesor en este día)
        val heads = withBalance.filter { !prevMap.containsKey(it.id) }.toMutableList()
        // Si por alguna razón circular/ambigua heads estuviera vacía, usar el menor id
        if (heads.isEmpty()) {
            heads.addAll(withBalance.sortedBy { it.id })
        } else {
            heads.sortBy { it.id }
        }

        val visited = mutableSetOf<Long>()
        val chainedOrder = mutableListOf<T>()

        for (head in heads) {
            var curr: T? = head
            while (curr != null && curr.id !in visited) {
                visited.add(curr.id)
                chainedOrder.add(curr)
                curr = nextMap[curr.id]
            }
        }

        // Cualquier elemento con saldo que no haya entrado en las cadenas
        for (item in withBalance) {
            if (item.id !in visited) {
                visited.add(item.id)
                chainedOrder.add(item)
            }
        }

        // Si hay elementos sin saldo en este día, agregarlos al final manteniendo orden de id
        return if (withoutBalance.isEmpty()) {
            chainedOrder
        } else {
            chainedOrder + withoutBalance.sortedBy { it.id }
        }
    }

    /**
     * Devuelve el último elemento (el saldo final del día) de un conjunto de transacciones del mismo día o período.
     */
    fun <T : BalanceAwareItem> resolveLatestBalance(items: List<T>): BigDecimal? {
        val withBalance = items.filter { it.balance != null }
        if (withBalance.isEmpty()) return null
        if (withBalance.size == 1) return withBalance[0].balance

        val sorted = sortIntraDay(withBalance)
        return sorted.lastOrNull()?.balance
    }

    /**
     * Devuelve el saldo inicial o primer apunte del día/período.
     */
    fun <T : BalanceAwareItem> resolveEarliestBalance(items: List<T>): BigDecimal? {
        val withBalance = items.filter { it.balance != null }
        if (withBalance.isEmpty()) return null
        if (withBalance.size == 1) return withBalance[0].balance

        val sorted = sortIntraDay(withBalance)
        return sorted.firstOrNull()?.balance
    }
}
