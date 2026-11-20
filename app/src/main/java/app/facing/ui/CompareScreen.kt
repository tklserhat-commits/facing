package app.facing.ui

import app.facing.R
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import app.facing.FacingApp

/** Zwei Tage übereinanderlegen; der Regler blendet von Tag A (links) zu Tag B (rechts) über. */
@Composable
fun CompareScreen(app: FacingApp, onClose: () -> Unit) {
    val days by app.photos.dayCovers.collectAsStateWithLifecycle(emptyList()) // neueste zuerst
    var dayA by rememberSaveable { mutableStateOf<String?>(null) }
    var dayB by rememberSaveable { mutableStateOf<String?>(null) }
    var blend by rememberSaveable { mutableFloatStateOf(0.5f) }
    var picking by remember { mutableIntStateOf(0) } // 0 = nichts, 1 = A, 2 = B

    BackHandler(onBack = onClose)

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text(stringResource(R.string.back)) }
            Text(stringResource(R.string.compare_title))
        }

        if (days.size < 2) {
            Hint(stringResource(R.string.compare_need_two))
            return@Column
        }

        // Standard: ältester Tag gegen neuesten Tag
        val a = days.firstOrNull { it.day == dayA } ?: days.last()
        val b = days.firstOrNull { it.day == dayB } ?: days.first()

        Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f)) {
            AsyncImage(
                model = app.photos.storage.photoFile(a.fileName),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            AsyncImage(
                model = app.photos.storage.photoFile(b.fileName),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = blend,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Slider(
            value = blend,
            onValueChange = { blend = it },
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = { picking = 1 }) { Text(stringResource(R.string.day_a, formatDay(a.day))) }
            TextButton(onClick = { picking = 2 }) { Text(stringResource(R.string.day_b, formatDay(b.day))) }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = { blend = 0f }) { Text(stringResource(R.string.only_a)) }
            TextButton(onClick = { dayA = b.day; dayB = a.day }) { Text(stringResource(R.string.swap)) }
            TextButton(onClick = { blend = 1f }) { Text(stringResource(R.string.only_b)) }
        }

        if (picking != 0) {
            AlertDialog(
                onDismissRequest = { picking = 0 },
                title = { Text(stringResource(if (picking == 1) R.string.pick_day_a else R.string.pick_day_b)) },
                text = {
                    LazyColumn {
                        items(days, key = { it.id }) { p ->
                            TextButton(onClick = {
                                if (picking == 1) dayA = p.day else dayB = p.day
                                picking = 0
                            }) { Text(formatDay(p.day) + if (p.isFavorite) " ★" else "") }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { picking = 0 }) { Text(stringResource(R.string.cancel)) } },
            )
        }
    }
}
