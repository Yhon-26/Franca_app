package com.grafito.finanzas.data

import java.text.Normalizer
import java.util.Locale

internal const val MAX_BACKUP_CATEGORIES = 1_000

private val accentMarks = Regex("\\p{M}+")
private val spaces = Regex("[\\s\\p{Z}]+")

internal fun categoryNameKey(name: String): String = spaces.replace(
    accentMarks.replace(Normalizer.normalize(name, Normalizer.Form.NFD), ""), " "
).trim().lowercase(Locale.ROOT)

private val starterCategories = listOf("Alimentación", "Transporte", "Compras", "Servicios", "Salud", "Entretenimiento", "Otros")
    .map { CategoryEntity(name = it, emoji = "") } +
    listOf("Sueldo", "Ventas", "Otros ingresos").map { CategoryEntity(name = it, emoji = "", type = EntryType.INCOME) }

private val additionalExpenses = listOf(
    "Supermercado", "Vivienda y alquiler", "Internet y celular", "Educación", "Mascotas",
    "Ropa y calzado", "Cuidado personal", "Suscripciones", "Deudas y cuotas",
    "Combustible y estacionamiento", "Regalos", "Viajes", "Mantenimiento del hogar", "Seguros", "Impuestos y trámites"
).map { CategoryEntity(name = it, emoji = "") }

/** Called within the same transaction as initialization or restoration. */
internal suspend fun FinanceDao.ensureCategoryCatalog() {
    val existing = allCategories()
    val existingKeys = existing.mapTo(HashSet()) { it.type to categoryNameKey(it.name) }
    val presets = if (existing.isEmpty()) starterCategories + additionalExpenses else additionalExpenses
    val availableSlots = (MAX_BACKUP_CATEGORIES - existing.size).coerceAtLeast(0)
    val missing = presets.filterNot { (it.type to categoryNameKey(it.name)) in existingKeys }.take(availableSlots)
    if (missing.isEmpty()) return

    val usedIds = existing.mapTo(HashSet()) { it.id }
    // Imported IDs can be close to Long.MAX_VALUE. Allocate unused positive IDs.
    var nextId = 1L
    for (category in missing) {
        while (nextId in usedIds) nextId++
        insertCategory(category.copy(id = nextId++))
    }
}
