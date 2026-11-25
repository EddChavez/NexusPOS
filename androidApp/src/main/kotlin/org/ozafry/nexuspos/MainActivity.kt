package org.ozafry.nexuspos

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider

class MainActivity : ComponentActivity() {
    private lateinit var model: MobileModel
    private val connectivity by lazy { getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { runOnUiThread { model.connectionRestored() } }
        override fun onLost(network: Network) {
            runOnUiThread { if (connectivity.activeNetwork == null) model.connectionLost() }
        }
    }
    private var watching = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        model = ViewModelProvider(this)[MobileModel::class.java]
        setContent { MobileApp(model, this) }
    }

    override fun onStart() {
        super.onStart()
        if (!watching) {
            watching = runCatching { connectivity.registerDefaultNetworkCallback(networkCallback) }.isSuccess
        }
        if (!watching || connectivity.activeNetwork == null) model.refresh()
    }

    override fun onStop() {
        if (watching) {
            connectivity.unregisterNetworkCallback(networkCallback)
            watching = false
        }
        super.onStop()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        if (::model.isInitialized) model.interaction()
    }
}
