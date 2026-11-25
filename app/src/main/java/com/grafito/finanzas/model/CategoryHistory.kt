package com.grafito.finanzas.model

import com.grafito.finanzas.data.CategoryEntity
import com.grafito.finanzas.data.TransactionEntity

internal fun suggestCategory(
    recentEntries: List<TransactionEntity>,
    categories: List<CategoryEntity>,
    type: String,
    amountCents: Long?
): CategoryEntity? {
    if (amountCents == null) return null
    val categoryId = recentEntries.firstOrNull {
        it.type == type && it.amountCents == amountCents && it.categoryId != null
    }?.categoryId ?: return null
    return categories.find { it.id == categoryId }
}
