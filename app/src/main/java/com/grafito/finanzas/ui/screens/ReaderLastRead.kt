package com.grafito.finanzas.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

@Composable
internal fun ReaderLastRead(active: Boolean, time: Long?, waiting: String, modifier: Modifier = Modifier) {
    val text = if (time != null) {
        val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(time))
        val stale = active && System.currentTimeMillis() - time >= TimeUnit.DAYS.toMillis(7)
        if (stale) "Sin lecturas en los últimos 7 días.\nÚltima lectura válida · $date"
        else "Última lectura válida · $date"
    } else if (active) {
        "Aún no hay una lectura válida registrada. $waiting"
    } else return
    Text(text, modifier, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
