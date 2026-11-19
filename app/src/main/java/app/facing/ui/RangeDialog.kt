package app.facing.ui

import android.content.Context
import app.facing.R
import androidx.compose.ui.res.stringResource
import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.time.LocalDate

/** Beschriftung für den gewählten Zeitraum (Tage als "yyyy-MM-dd", null = offen). */
internal fun rangeLabel(context: Context, from: String?, to: String?): String = when {
    from == null && to == null -> context.getString(R.string.range_all)
    to == null -> context.getString(R.string.range_since, formatDay(from!!))
    from == null -> context.getString(R.string.range_until, formatDay(to))
    else -> "${formatDay(from)} – ${formatDay(to)}"
}

/** Auswahl des Film-Zeitraums: Voreinstellungen oder eigener Zeitraum per Datumsauswahl. */
@Composable
fun RangeDialog(onSelect: (from: String?, to: String?) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current

    fun pickCustom() {
        val t = LocalDate.now()
        DatePickerDialog(context, { _, y, m, d ->
            val from = LocalDate.of(y, m + 1, d)
            DatePickerDialog(context, { _, y2, m2, d2 ->
                val end = LocalDate.of(y2, m2 + 1, d2)
                val first = if (from.isAfter(end)) end else from
                val last = if (from.isAfter(end)) from else end
                onSelect(first.toString(), last.toString())
            }, y, m, d).apply { setTitle(context.getString(R.string.date_to)) }.show()
        }, t.year, t.monthValue - 1, t.dayOfMonth).apply { setTitle(context.getString(R.string.date_from)) }.show()
    }

    fun lastDays(n: Long) = onSelect(LocalDate.now().minusDays(n).toString(), null)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.range_title)) },
        text = {
            Column {
                TextButton(onClick = { onSelect(null, null) }) { Text(stringResource(R.string.range_all)) }
                TextButton(onClick = { lastDays(30) }) { Text(stringResource(R.string.range_30)) }
                TextButton(onClick = { lastDays(90) }) { Text(stringResource(R.string.range_90)) }
                TextButton(onClick = { lastDays(365) }) { Text(stringResource(R.string.range_year)) }
                TextButton(onClick = { pickCustom() }) { Text(stringResource(R.string.range_custom)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
