package com.grafito.finanzas.model

import com.grafito.finanzas.data.CategoryEntity
import com.grafito.finanzas.data.EntryType
import com.grafito.finanzas.data.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategoryHistoryTest {
    @Test fun usesTheMostRecentCategoryForTheSameTypeAndAmount() {
        val food = CategoryEntity(1, "Comidas", "")
        val shopping = CategoryEntity(2, "Compras", "")
        val salary = CategoryEntity(3, "Sueldo", "", EntryType.INCOME)
        val entries = listOf(
            TransactionEntity(type = EntryType.INCOME, amountCents = 350, accountId = 1, categoryId = salary.id),
            TransactionEntity(type = EntryType.EXPENSE, amountCents = 300, accountId = 1, categoryId = shopping.id),
            TransactionEntity(type = EntryType.EXPENSE, amountCents = 350, accountId = 1),
            TransactionEntity(type = EntryType.EXPENSE, amountCents = 350, accountId = 1, categoryId = food.id),
            TransactionEntity(type = EntryType.EXPENSE, amountCents = 350, accountId = 1, categoryId = shopping.id)
        )

        assertEquals(food, suggestCategory(entries, listOf(food, shopping), EntryType.EXPENSE, 350))
        assertEquals(salary, suggestCategory(entries, listOf(salary), EntryType.INCOME, 350))
        assertNull(suggestCategory(entries, listOf(food, shopping), EntryType.EXPENSE, null))
    }
}
