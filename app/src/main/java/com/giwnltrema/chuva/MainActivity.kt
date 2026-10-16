package com.giwnltrema.chuva

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.giwnltrema.chuva.data.LocationProvider
import com.giwnltrema.chuva.notify.Notifier
import com.giwnltrema.chuva.notify.RainCheckWorker
import com.giwnltrema.chuva.ui.ChuvaApp
import com.giwnltrema.chuva.ui.theme.ChuvaTheme
import org.maplibre.android.MapLibre

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    private val askLocation = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        vm.refresh()
        // Os alertas vêm ligados por padrão; no Android 13+ precisam da permissão de notificação.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notifier.canNotify(this)) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        MapLibre.getInstance(this)
        Notifier.createChannels(this)
        RainCheckWorker.sync(this)

        setContent {
            ChuvaTheme {
                ChuvaApp(vm)
            }
        }

        if (savedInstanceState == null) {
            if (LocationProvider(this).hasPermission()) {
                vm.refresh()
            } else {
                askLocation.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                )
            }
        }
    }
}
