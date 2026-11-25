package com.grafito.finanzas.model

import com.grafito.finanzas.data.AccountEntity
import com.grafito.finanzas.data.CategoryEntity
import com.grafito.finanzas.data.EntryType
import com.grafito.finanzas.data.TransactionEntity
import com.grafito.finanzas.data.categoryNameKey

private val decimalAmount = Regex("(?:[0-9]{1,9}|[0-9]{1,3}(?:[.,][0-9]{3})+)[.,][0-9]{1,2}")

private data class MovementSearchTerm(val text: String, val cents: Long?, val isWholeNumber: Boolean)

internal fun filterMovements(
    transactions: List<TransactionEntity>,
    accounts: Map<Long, AccountEntity>,
    categories: Map<Long, CategoryEntity>,
    query: String,
    type: String?
): List<TransactionEntity> {
    val terms = categoryNameKey(query).replace("s/", " ").split(' ').filter(String::isNotEmpty).map { text ->
        val amount = text.trimStart('+', '-', '−')
        val cents = if (decimalAmount.matches(amount)) {
            val separator = maxOf(amount.lastIndexOf('.'), amount.lastIndexOf(','))
            Money.parse(amount.substring(0, separator).filter(Char::isDigit) + "." + amount.substring(separator + 1))
        } else null
        MovementSearchTerm(text, cents, text.all { it in '0'..'9' })
    }
    if (terms.isEmpty() && type == null) return transactions

    return transactions.filter { entry ->
        if (type != null && entry.type != type) return@filter false
        if (terms.isEmpty()) return@filter true
        val typeName = when (entry.type) {
            EntryType.INCOME -> "Ingreso"
            EntryType.TRANSFER -> "Transferencia"
            else -> "Gasto"
        }
        val fields = categoryNameKey(
            listOfNotNull(
                typeName,
                entry.note,
                accounts[entry.accountId]?.name,
                entry.toAccountId?.let { accounts[it]?.name },
                categories[entry.categoryId]?.name
            ).joinToString(" ")
        )
        val wholeAmount = (entry.amountCents / 100).toString()
        terms.all { term ->
            term.text in fields || term.cents == entry.amountCents ||
                (term.isWholeNumber && term.text in wholeAmount)
        }
    }
}
