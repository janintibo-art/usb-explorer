package art.janintibo.usbexplorer

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel

private enum class Onglet { FICHIERS, DISQUES }

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
private fun Application(
    usb: UsbViewModel = viewModel(),
    fichiers: FichiersViewModel = viewModel()
) {
    val contexte = LocalContext.current
    var onglet by remember { mutableStateOf(Onglet.FICHIERS) }
    var menu by remember { mutableStateOf(false) }

    val selecteur = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) fichiers.definirArbre(uri)
    }

    val avis = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val accorde = ContextCompat.checkSelfPermission(
                contexte,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!accorde) avis.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    DisposableEffect(contexte) {
        val autorisation = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                usb.permissionRepondue()
            }
        }
        val branchement = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                usb.appareilChange()
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

        onDispose {
            try { contexte.unregisterReceiver(autorisation) } catch (e: Exception) { }
            try { contexte.unregisterReceiver(branchement) } catch (e: Exception) { }
        }
    }

    LaunchedEffect(fichiers.arbre) {
        if (fichiers.arbre != null && fichiers.entrees.isEmpty()) fichiers.recharger()
    }

    BackHandler(enabled = onglet == Onglet.FICHIERS && fichiers.chemin.size > 1) {
        fichiers.remonter()
    }

    Scaffold(
        containerColor = Nuit,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (onglet == Onglet.FICHIERS) "Fichiers" else "Disques",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                actions = {
                    if (onglet == Onglet.FICHIERS && fichiers.arbre != null) {
                        IconButton(onClick = { fichiers.recharger() }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_refresh),
                                contentDescription = "Relire le dossier",
                                tint = Clair,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Box {
                            IconButton(onClick = { menu = true }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_more),
                                    contentDescription = "Menu",
                                    tint = Clair,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = menu,
                                onDismissRequest = { menu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Changer de volume") },
                                    onClick = {
                                        menu = false
                                        selecteur.launch(null)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Oublier ce volume") },
                                    onClick = {
                                        menu = false
                                        fichiers.oublierArbre()
                                    }
                                )
                            }
                        }
                    }
                    if (onglet == Onglet.DISQUES && usb.occupe) {
                        Box(
                            modifier = Modifier.size(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = Ambre,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Nuit,
                    titleContentColor = Clair
                )
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Pupitre, tonalElevation = 0.dp) {
                Onglets(
                    icone = R.drawable.ic_folder,
                    texte = "Fichiers",
                    choisi = onglet == Onglet.FICHIERS
                ) { onglet = Onglet.FICHIERS }
                Onglets(
                    icone = R.drawable.ic_drive,
                    texte = "Disques",
                    choisi = onglet == Onglet.DISQUES
                ) { onglet = Onglet.DISQUES }
            }
        }
    ) { marges ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(marges)
        ) {
            when (onglet) {
                Onglet.FICHIERS -> EcranFichiers(
                    etat = fichiers,
                    onChoisirVolume = { selecteur.launch(null) }
                )

                Onglet.DISQUES -> EcranDisques(
                    disques = usb.disques,
                    inventaire = usb.inventaire,
                    etat = usb.etat,
                    attente = usb.occupe,
                    onAnalyser = { usb.scanner() }
                )
            }
        }
    }
}

@Composable
private fun RowScope.Onglets(
    icone: Int,
    texte: String,
    choisi: Boolean,
    onClick: () -> Unit
) {
    NavigationBarItem(
        selected = choisi,
        onClick = onClick,
        icon = {
            Icon(
                painter = painterResource(id = icone),
                contentDescription = texte,
                modifier = Modifier.size(20.dp)
            )
        },
        label = { Text(texte, style = MaterialTheme.typography.labelSmall) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = Menthe,
            selectedTextColor = Menthe,
            unselectedIconColor = Doux,
            unselectedTextColor = Doux,
            indicatorColor = Menthe.copy(alpha = 0.14f)
        )
    )
}
