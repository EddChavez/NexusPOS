package org.ozafry.nexuspos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

import androidx.compose.runtime.remember
import org.ozafry.nexuspos.db.AndroidDatabaseDriverFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val driverFactory = remember { AndroidDatabaseDriverFactory(applicationContext) }
            App(
                driverFactory = driverFactory,
                registerId = "MBL-01",
                initialMode = POSViewMode.SUPERVISOR
            )
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}