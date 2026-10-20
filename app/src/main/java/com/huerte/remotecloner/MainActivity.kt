package com.huerte.remotecloner

import android.os.Bundle
import android.content.Intent
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.huerte.remotecloner.ui.App
import com.huerte.remotecloner.ui.ConnectionViewModel
import com.huerte.remotecloner.ui.RemoteViewModel
import com.huerte.remotecloner.ui.theme.RemoteClonerAndroidTheme

class MainActivity : ComponentActivity() {
    private val connectionViewModel: ConnectionViewModel by viewModels()

    // Both view models share one Bridge, so the boards connected on the Connection tab are the ones
    // the Clone, Library and Control tabs talk to.
    private val remoteViewModel: RemoteViewModel by viewModels {
        viewModelFactory { initializer { RemoteViewModel(application, connectionViewModel.bridge) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            RemoteClonerAndroidTheme {
                App(connectionViewModel, remoteViewModel)
            }
        }
    }

    // A board plugged in while the app is open (see the USB_DEVICE_ATTACHED filter in the manifest).
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        connectionViewModel.refresh()
        connectionViewModel.autoConnect()
    }

    override fun onResume() {
        super.onResume()
        connectionViewModel.refresh()       // pick up boards plugged in while the app was in the background
        connectionViewModel.autoConnect()   // reopen the boards used last time
    }
}
