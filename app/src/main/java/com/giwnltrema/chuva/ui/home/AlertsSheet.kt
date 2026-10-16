package com.giwnltrema.chuva.ui.home

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.giwnltrema.chuva.data.Alert
import com.giwnltrema.chuva.data.AlertSettings
import com.giwnltrema.chuva.notify.Notifier
import com.giwnltrema.chuva.notify.RainCheckWorker

private val leadOptions = listOf(15, 30, 60)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsSheet(
    settings: AlertSettings,
    onChange: (AlertSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    // Recalcula as permissões quando o usuário volta das configurações do sistema.
    var tick by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    val canNotify = remember(tick) { Notifier.canNotify(context) }
    val background = remember(tick) { RainCheckWorker.hasBackgroundLocation(context) }

    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val askBackground = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }

    fun ensureNotifications() {
        if (canNotify) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Text("Notificações", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))

            if (settings.anyEnabled && !canNotify) {
                Warning(
                    icon = { Icon(Icons.Rounded.NotificationsOff, null) },
                    text = "As notificações estão bloqueadas para o app.",
                    action = "Permitir",
                    onAction = ::ensureNotifications,
                )
                Spacer(Modifier.height(16.dp))
            }

            ToggleRow(
                title = "Avisar antes de chover",
                subtitle = "Checa a previsão de 15 em 15 min e avisa uma vez por chuva",
                checked = settings.rainAlerts,
            ) {
                onChange(settings.copy(rainAlerts = it))
                if (it) ensureNotifications()
            }
            if (settings.rainAlerts) {
                Text(
                    "Antecedência",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    leadOptions.forEachIndexed { i, min ->
                        SegmentedButton(
                            selected = settings.leadMinutes == min,
                            onClick = { onChange(settings.copy(leadMinutes = min)) },
                            shape = SegmentedButtonDefaults.itemShape(i, leadOptions.size),
                            label = { Text(if (min < 60) "$min min" else "1 h") },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                ToggleRow(
                    title = "Silenciar à noite",
                    subtitle = "Sem alertas entre 22h e 7h",
                    checked = settings.quietNight,
                ) { onChange(settings.copy(quietNight = it)) }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            ToggleRow(
                title = "Resumo da manhã",
                subtitle = "Por volta das 6h30: vai chover hoje? Quanto e a partir de que horas",
                checked = settings.morningSummary,
            ) {
                onChange(settings.copy(morningSummary = it))
                if (it) ensureNotifications()
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            if (background) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Os alertas acompanham sua localização atual, mesmo com o app fechado.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                Warning(
                    icon = { Icon(Icons.Rounded.LocationOn, null) },
                    text = "Hoje os alertas usam o último lugar em que você abriu o app. " +
                        "Para seguirem você, permita a localização \"o tempo todo\".",
                    action = "Permitir",
                    onAction = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            askBackground.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        } else {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
                            )
                        }
                    },
                )
            }

            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = {
                    ensureNotifications()
                    Notifier.rain(context, Alert("Chuva em ~25 min (teste)", "Moderada · 2,3 mm na primeira hora"))
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Enviar notificação de teste") }
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun Warning(icon: @Composable () -> Unit, text: String, action: String, onAction: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                icon()
                Spacer(Modifier.width(12.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
            FilledTonalButton(onClick = onAction, modifier = Modifier.align(Alignment.End)) { Text(action) }
        }
    }
}
