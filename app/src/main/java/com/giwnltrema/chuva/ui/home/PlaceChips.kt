package com.giwnltrema.chuva.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.giwnltrema.chuva.data.Destination
import com.giwnltrema.chuva.data.PlaceKind
import com.giwnltrema.chuva.data.SavedPlace
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun PlaceKind.icon(): ImageVector = when (this) {
    PlaceKind.HOME -> Icons.Rounded.Home
    PlaceKind.WORK -> Icons.Rounded.Business
    PlaceKind.OTHER -> Icons.Rounded.Place
}

/**
 * "Aqui · Casa · Trabalho · ＋": troca o lugar da previsão.
 * Segurar um lugar salvo abre a edição (renomear, mudar endereço, apagar).
 */
@Composable
fun PlaceChips(
    places: List<SavedPlace>,
    selected: String?,
    onSelect: (String?) -> Unit,
    onEdit: (SavedPlace?) -> Unit,
) {
    Column {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { PlaceChip("Aqui", Icons.Rounded.MyLocation, selected == null, onClick = { onSelect(null) }) }
            items(places, key = { it.id }) { p ->
                PlaceChip(p.name, p.kind.icon(), selected == p.id, onClick = { onSelect(p.id) }, onLongClick = { onEdit(p) })
            }
            item { PlaceChip(if (places.isEmpty()) "Salvar lugar" else "", Icons.Rounded.Add, false, onClick = { onEdit(null) }) }
        }
        if (places.isEmpty()) {
            Text(
                "Salve sua casa, o trabalho… para ver a previsão de lá com um toque.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaceChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) scheme.secondaryContainer else Color.Transparent,
        contentColor = if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
        border = if (selected) null else BorderStroke(1.dp, scheme.outline),
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = if (label.isEmpty()) 10.dp else 14.dp, vertical = 8.dp),
        ) {
            Icon(icon, contentDescription = label.ifEmpty { "Adicionar lugar" }, modifier = Modifier.size(18.dp))
            if (label.isNotEmpty()) {
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false)
            }
        }
    }
}

/** Cadastro/edição de um lugar: nome, tipo e endereço (busca ou localização atual). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceSheet(
    editing: SavedPlace?,
    search: suspend (String) -> List<Destination>,
    currentAddress: suspend () -> Destination?,
    onSave: (SavedPlace) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var kind by remember { mutableStateOf(editing?.kind ?: PlaceKind.HOME) }
    var name by remember { mutableStateOf(editing?.name ?: "") }
    var nameTouched by remember { mutableStateOf(editing != null) }
    var location by remember { mutableStateOf(editing?.let { Destination(it.name, "", it.lat, it.lon) }) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<Destination>()) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Nome automático a partir do tipo, até a pessoa digitar o dela.
    LaunchedEffect(kind) { if (!nameTouched) name = if (kind == PlaceKind.OTHER) "" else kind.label }
    LaunchedEffect(query) {
        if (query.trim().length < 3) { results = emptyList(); return@LaunchedEffect }
        delay(400)
        busy = true
        results = search(query)
        busy = false
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (editing == null) "Salvar lugar" else "Editar lugar", style = MaterialTheme.typography.headlineSmall)

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                PlaceKind.entries.forEachIndexed { i, k ->
                    SegmentedButton(
                        selected = kind == k,
                        onClick = { kind = k },
                        shape = SegmentedButtonDefaults.itemShape(i, PlaceKind.entries.size),
                        icon = { Icon(k.icon(), null, Modifier.size(18.dp)) },
                        label = { Text(k.label, maxLines = 1, softWrap = false) },
                    )
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it; nameTouched = true },
                label = { Text("Nome") },
                placeholder = { Text("Ex.: Academia, Casa da mãe") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Endereço", style = MaterialTheme.typography.titleSmall)
            location?.let { loc ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
                        Icon(Icons.Rounded.Place, null)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(loc.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (loc.detail.isNotBlank()) {
                                Text(loc.detail, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(if (location == null) "Buscar endereço ou lugar" else "Trocar endereço") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = { if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            results.take(5).forEach { d ->
                ListItem(
                    headlineContent = { Text(d.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { if (d.detail.isNotBlank()) Text(d.detail, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingContent = { Icon(Icons.Rounded.Place, null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)).combinedClickableCompat {
                        location = d
                        query = ""
                        results = emptyList()
                    },
                )
            }
            OutlinedButton(
                onClick = {
                    scope.launch {
                        busy = true
                        currentAddress()?.let { location = it }
                        busy = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.MyLocation, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Usar onde estou agora")
            }

            Spacer(Modifier.height(4.dp))
            Button(
                enabled = location != null && name.isNotBlank(),
                onClick = {
                    val loc = location ?: return@Button
                    onSave(
                        SavedPlace(
                            id = editing?.id ?: java.util.UUID.randomUUID().toString(),
                            name = name.trim(),
                            kind = kind,
                            lat = loc.lat,
                            lon = loc.lon,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Salvar") }
            if (editing != null) {
                TextButton(
                    onClick = { onDelete(editing.id) },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) { Text("Apagar \"${editing.name}\"", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit) = this.combinedClickable(onClick = onClick)
