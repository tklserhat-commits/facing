package app.facing.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.facing.FacingApp
import app.facing.R
import app.facing.data.db.GroupEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Zeigt die aktive Gruppe; ein Tipp öffnet die Auswahl zum Wechseln, Anlegen, Umbenennen und Löschen. */
@Composable
fun GroupChip(app: FacingApp, modifier: Modifier = Modifier, light: Boolean = false) {
    val current by app.photos.currentGroup.collectAsStateWithLifecycle(null)
    var open by remember { mutableStateOf(false) }
    // Der Scope gehört zum Chip (nicht zum Dialog), damit ein Wechsel auch nach dem Schließen fertig läuft
    val scope = rememberCoroutineScope()

    Button(
        onClick = { open = true },
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (light) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.55f),
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(
            stringResource(R.string.group_chip, current?.name ?: ""),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 140.dp),
        )
    }
    if (open) GroupDialog(app, scope) { open = false }
}

@Composable
private fun GroupDialog(app: FacingApp, scope: CoroutineScope, onClose: () -> Unit) {
    val groups by app.photos.groups.collectAsStateWithLifecycle(emptyList())
    val currentId by app.photos.currentGroupId.collectAsStateWithLifecycle(1L)
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<GroupEntity?>(null) }
    var deleting by remember { mutableStateOf<GroupEntity?>(null) }

    fun select(g: GroupEntity) {
        scope.launch { app.photos.setCurrentGroup(g.id) }
        onClose()
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.groups_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                groups.forEach { g ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = g.id == currentId, onClick = { select(g) })
                        Text(g.name, modifier = Modifier.weight(1f).clickable { select(g) })
                        TextButton(onClick = { renaming = g }) { Text("✎") }
                        if (groups.size > 1) TextButton(onClick = { deleting = g }) { Text("🗑") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { creating = true }) { Text(stringResource(R.string.group_new)) } },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.close)) } },
    )

    if (creating) {
        NameDialog(stringResource(R.string.group_new), "", onDismiss = { creating = false }) { name ->
            creating = false
            scope.launch { app.photos.createGroup(name) } // wechselt direkt zur neuen Gruppe
            onClose()
        }
    }
    renaming?.let { g ->
        NameDialog(stringResource(R.string.group_rename), g.name, onDismiss = { renaming = null }) { name ->
            renaming = null
            scope.launch { app.photos.renameGroup(g.id, name) }
        }
    }
    deleting?.let { g ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.group_delete_title, g.name)) },
            text = { Text(stringResource(R.string.group_delete_text)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    scope.launch { app.photos.deleteGroup(g.id) }
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(30) },
                singleLine = true,
                label = { Text(stringResource(R.string.group_name_label)) },
            )
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onConfirm(text) }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
