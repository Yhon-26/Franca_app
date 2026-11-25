package com.grafito.finanzas.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import com.grafito.finanzas.data.CategoryEntity
import com.grafito.finanzas.data.categoryNameKey

@Composable
internal fun rememberCategoryMatches(categories: List<CategoryEntity>, search: String): List<CategoryEntity> {
    val indexed = remember(categories) { categories.map { it to categoryNameKey(it.name) } }
    return remember(indexed, search) { indexed.filter { search in it.second }.map { it.first } }
}

internal fun categoryIcon(category: CategoryEntity): ImageVector = when (categoryNameKey(category.name)) {
    "alimentacion" -> Icons.Outlined.RestaurantMenu
    "transporte" -> Icons.Outlined.DirectionsBus
    "compras" -> Icons.Outlined.ShoppingBag
    "servicios" -> Icons.Outlined.Bolt
    "salud" -> Icons.Outlined.MedicalServices
    "entretenimiento" -> Icons.Outlined.Movie
    "sueldo" -> Icons.Outlined.WorkOutline
    "ventas" -> Icons.Outlined.Storefront
    "otros ingresos" -> Icons.Outlined.Savings
    "supermercado" -> Icons.Outlined.ShoppingCart
    "vivienda y alquiler" -> Icons.Outlined.Home
    "internet y celular" -> Icons.Outlined.PhoneAndroid
    "educacion" -> Icons.Outlined.School
    "mascotas" -> Icons.Outlined.Pets
    "ropa y calzado" -> Icons.Outlined.Checkroom
    "cuidado personal" -> Icons.Outlined.Spa
    "suscripciones" -> Icons.Outlined.Subscriptions
    "deudas y cuotas" -> Icons.AutoMirrored.Outlined.ReceiptLong
    "combustible y estacionamiento" -> Icons.Outlined.LocalGasStation
    "regalos" -> Icons.Outlined.CardGiftcard
    "viajes" -> Icons.Outlined.Luggage
    "mantenimiento del hogar" -> Icons.Outlined.HomeRepairService
    "seguros" -> Icons.Outlined.Shield
    "impuestos y tramites" -> Icons.Outlined.Description
    else -> Icons.Outlined.Category
}
