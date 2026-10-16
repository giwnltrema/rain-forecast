package com.giwnltrema.chuva

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.giwnltrema.chuva.data.LocationProvider
import com.giwnltrema.chuva.ui.ChuvaApp
import com.giwnltrema.chuva.ui.theme.ChuvaTheme
import org.maplibre.android.MapLibre

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    private val askLocation = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { vm.refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        MapLibre.getInstance(this)

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
