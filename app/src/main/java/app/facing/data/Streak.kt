package app.facing.data

import java.time.LocalDate

/**
 * Anzahl der Tage in Folge mit mindestens einem Foto.
 * Ist heute noch kein Foto da, zählt die Serie bis gestern weiter,
 * sie bricht also erst, wenn ein ganzer Tag ausgelassen wurde.
 */
fun currentStreak(days: Collection<String>, today: LocalDate = LocalDate.now()): Int {
    val dates = days.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet()
    var day = if (today in dates) today else today.minusDays(1)
    var count = 0
    while (day in dates) {
        count++
        day = day.minusDays(1)
    }
    return count
}
