package com.giwnltrema.chuva.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.giwnltrema.chuva.MainViewModel
import com.giwnltrema.chuva.ui.home.AlertsSheet
import com.giwnltrema.chuva.ui.home.HomeScreen
import com.giwnltrema.chuva.ui.map.MapScreen

private data class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    Tab("Previsão", Icons.Outlined.WaterDrop, Icons.Rounded.WaterDrop),
    Tab("Mapa", Icons.Outlined.Map, Icons.Rounded.Map),
)

@Composable
fun ChuvaApp(vm: MainViewModel) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val home by vm.home.collectAsStateWithLifecycle()
    val map by vm.map.collectAsStateWithLifecycle()
    val alerts by vm.alerts.collectAsStateWithLifecycle()
    var showAlerts by rememberSaveable { mutableStateOf(false) }

    if (showAlerts) {
        AlertsSheet(settings = alerts, onChange = vm::updateAlerts, onDismiss = { showAlerts = false })
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, tab ->
                    NavigationBarItem(
                        selected = selected == i,
                        onClick = { selected = i },
                        icon = { Icon(if (selected == i) tab.selectedIcon else tab.icon, contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        when (selected) {
            0 -> HomeScreen(
                state = home,
                onRefresh = vm::refresh,
                onOpenMap = { selected = 1 },
                onOpenAlerts = { showAlerts = true },
                alertsOn = alerts.anyEnabled,
                modifier = Modifier.padding(padding),
            )
            else -> Box(Modifier.padding(bottom = padding.calculateBottomPadding())) {
                MapScreen(
                    home = home,
                    state = map,
                    onLoadRadar = vm::loadRadar,
                    onLoadGrid = vm::loadGrid,
                )
            }
        }
    }
}
