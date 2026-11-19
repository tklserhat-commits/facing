package app.facing.ui

import app.facing.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import app.facing.FacingApp
import app.facing.data.db.PhotoEntity
import java.io.File
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** Monatsraster: Tage mit Foto zeigen ihr Titelbild und sind antippbar, Lücken bleiben dunkel. */
@Composable
fun CalendarView(app: FacingApp, covers: List<PhotoEntity>, onDay: (String) -> Unit) {
    val byDay = remember(covers) { covers.associateBy { it.day } }
    var month by rememberSaveable {
        mutableStateOf((covers.firstOrNull()?.day?.take(7) ?: YearMonth.now().toString()))
    }
    val ym = YearMonth.parse(month)
    val lead = ym.atDay(1).dayOfWeek.value - 1 // Montag = 0

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { month = ym.minusMonths(1).toString() }) { Text("‹") }
            Text(
                ym.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + ym.year,
                color = Color.White,
            )
            TextButton(onClick = { month = ym.plusMonths(1).toString() }) { Text("›") }
        }
        Row(Modifier.fillMaxWidth()) {
            for (d in java.time.DayOfWeek.values().map { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }) {
                Text(
                    d, color = Color.Gray, fontSize = 12.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.padding(top = 4.dp),
        ) {
            items(lead) { Box(Modifier.aspectRatio(3f / 4f)) }
            items(ym.lengthOfMonth()) { i ->
                val date = ym.atDay(i + 1)
                val photo = byDay[date.toString()]
                CalendarCell(app, date.dayOfMonth, photo) { onDay(date.toString()) }
            }
        }
        val count = (1..ym.lengthOfMonth()).count { byDay.containsKey(ym.atDay(it).toString()) }
        Text(
            stringResource(R.string.calendar_days_of, count, ym.lengthOfMonth()),
            color = Color.White,
            modifier = Modifier.padding(8.dp),
        )
    }
}

@Composable
private fun CalendarCell(app: FacingApp, dayOfMonth: Int, photo: PhotoEntity?, onClick: () -> Unit) {
    val thumb by produceState<File?>(null, photo?.fileName) {
        value = photo?.let { runCatching { app.photos.storage.thumbnail(it.fileName) }.getOrNull() }
    }
    Box(
        Modifier
            .aspectRatio(3f / 4f)
            .background(Color(0xFF1C1C1C))
            .then(if (photo != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        if (photo != null) {
            AsyncImage(
                model = thumb,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            dayOfMonth.toString() + if (photo?.isFavorite == true) " ★" else "",
            color = if (photo != null) Color.White else Color.Gray,
            fontSize = 11.sp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .background(if (photo != null) Color.Black.copy(alpha = 0.55f) else Color.Transparent)
                .padding(horizontal = 3.dp),
        )
    }
}
