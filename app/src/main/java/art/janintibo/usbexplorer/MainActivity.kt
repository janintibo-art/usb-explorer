package art.janintibo.usbexplorer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UsbExplorerTheme {
                Application()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Application(etat: UsbViewModel = viewModel()) {
    val contexte = LocalContext.current

    DisposableEffect(contexte) {
        val autorisation = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                etat.permissionRepondue()
            }
        }
        val branchement = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                etat.appareilChange()
            }
        }

        ContextCompat.registerReceiver(
            contexte,
            autorisation,
            IntentFilter(ACTION_PERMISSION_USB),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        val filtre = IntentFilter()
        filtre.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        filtre.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        ContextCompat.registerReceiver(
            contexte,
            branchement,
            filtre,
            ContextCompat.RECEIVER_EXPORTED
        )

        if (etat.premierPassage) etat.scanner()

        onDispose {
            try { contexte.unregisterReceiver(autorisation) } catch (e: Exception) { }
            try { contexte.unregisterReceiver(branchement) } catch (e: Exception) { }
        }
    }

    Scaffold(
        containerColor = Nuit,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "USB Explorer",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                actions = {
                    if (etat.occupe) {
                        Box(
                            modifier = Modifier.size(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = Menthe,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        IconButton(onClick = { etat.scanner() }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_refresh),
                                contentDescription = "Relire",
                                tint = Clair,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Nuit,
                    titleContentColor = Clair
                )
            )
        }
    ) { marges ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(marges)
        ) {
            EcranDisques(
                disques = etat.disques,
                etat = etat.etat,
                attente = etat.occupe
            )
        }
    }
}
