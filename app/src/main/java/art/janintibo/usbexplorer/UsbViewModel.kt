package art.janintibo.usbexplorer

import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val ACTION_PERMISSION_USB = "art.janintibo.usbexplorer.PERMISSION"

data class InterfaceBrute(val classe: Int, val sousClasse: Int, val protocole: Int) {
    val stockage: Boolean get() = classe == UsbConstants.USB_CLASS_MASS_STORAGE
    val bulkOnly: Boolean get() = stockage && sousClasse == 6 && protocole == 80
}

/** Ce qu'Android voit sur le port, stockage ou non. Sert au diagnostic. */
data class AppareilBrut(
    val nom: String,
    val vendeur: Int,
    val produit: Int,
    val autorise: Boolean,
    val interfaces: List<InterfaceBrute>
) {
    val stockage: Boolean get() = interfaces.any { it.stockage }
}

class UsbViewModel(application: Application) : AndroidViewModel(application) {

    var disques by mutableStateOf<List<DisqueInfo>>(emptyList())
        private set
    var inventaire by mutableStateOf<List<AppareilBrut>>(emptyList())
        private set
    var occupe by mutableStateOf(false)
        private set
    var etat by mutableStateOf("Analyse non lancée")
        private set
    var premierPassage by mutableStateOf(true)
        private set

    private var demandeEnCours = false

    fun scanner() {
        if (occupe) return
        occupe = true

        val contexte = getApplication<Application>()
        val gestionnaire = contexte.getSystemService(Context.USB_SERVICE) as UsbManager

        viewModelScope.launch {
            val appareils = try {
                gestionnaire.deviceList.values.toList()
            } catch (e: Exception) {
                emptyList<UsbDevice>()
            }

            inventaire = appareils.map { decrire(gestionnaire, it) }

            val trouves = ArrayList<DisqueInfo>()
            var attente = false

            for (appareil in appareils) {
                if (!porteStockage(appareil)) continue
                if (!gestionnaire.hasPermission(appareil)) {
                    attente = true
                    if (!demandeEnCours) {
                        demandeEnCours = true
                        demander(contexte, gestionnaire, appareil)
                    }
                    continue
                }
                val info = withContext(Dispatchers.IO) {
                    val bloc = BlocUsb.ouvrir(gestionnaire, appareil)
                    if (bloc == null) {
                        null
                    } else {
                        try {
                            Tables.analyser(bloc)
                        } catch (e: Exception) {
                            null
                        } finally {
                            bloc.fermer()
                        }
                    }
                }
                if (info != null) trouves.add(info)
            }

            disques = trouves
            etat = when {
                appareils.isEmpty() ->
                    "Android ne voit aucun périphérique sur le port USB. " +
                        "Le téléphone n'est pas en mode hôte, ou le disque n'est pas alimenté."
                inventaire.none { it.stockage } ->
                    "Un périphérique est branché, mais aucun ne se présente comme du stockage."
                attente && trouves.isEmpty() -> "En attente de votre autorisation"
                trouves.isEmpty() -> "Disque détecté, mais illisible en mode bloc"
                else -> ""
            }
            premierPassage = false
            occupe = false
        }
    }

    private fun decrire(gestionnaire: UsbManager, appareil: UsbDevice): AppareilBrut {
        val interfaces = ArrayList<InterfaceBrute>()
        for (i in 0 until appareil.interfaceCount) {
            val brute = appareil.getInterface(i)
            interfaces.add(
                InterfaceBrute(
                    brute.interfaceClass,
                    brute.interfaceSubclass,
                    brute.interfaceProtocol
                )
            )
        }
        val nom = appareil.productName
            ?: appareil.manufacturerName
            ?: appareil.deviceName
        return AppareilBrut(
            nom = nom,
            vendeur = appareil.vendorId,
            produit = appareil.productId,
            autorise = try { gestionnaire.hasPermission(appareil) } catch (e: Exception) { false },
            interfaces = interfaces
        )
    }

    /** Appelé quand la boîte de dialogue d'autorisation s'est refermée. */
    fun permissionRepondue() {
        demandeEnCours = false
        scanner()
    }

    fun appareilChange() {
        demandeEnCours = false
        disques = emptyList()
        inventaire = emptyList()
        etat = "Branchement détecté. Lancez l'analyse quand vous le souhaitez."
    }

    private fun porteStockage(appareil: UsbDevice): Boolean {
        for (i in 0 until appareil.interfaceCount) {
            if (appareil.getInterface(i).interfaceClass == UsbConstants.USB_CLASS_MASS_STORAGE) {
                return true
            }
        }
        return false
    }

    private fun demander(contexte: Context, gestionnaire: UsbManager, appareil: UsbDevice) {
        val intention = Intent(ACTION_PERMISSION_USB).setPackage(contexte.packageName)
        val differee = PendingIntent.getBroadcast(
            contexte,
            0,
            intention,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        try {
            gestionnaire.requestPermission(appareil, differee)
        } catch (e: Exception) {
            demandeEnCours = false
        }
    }
}

fun nomClasse(classe: Int): String = when (classe) {
    1 -> "Audio"
    2 -> "Communication"
    3 -> "Clavier ou souris"
    6 -> "Image"
    7 -> "Imprimante"
    8 -> "Stockage de masse"
    9 -> "Concentrateur"
    10 -> "Données CDC"
    11 -> "Carte à puce"
    14 -> "Vidéo"
    224 -> "Sans fil"
    255 -> "Propriétaire"
    else -> "Classe " + classe
}

fun nomProtocole(sousClasse: Int, protocole: Int): String = when {
    protocole == 80 -> "Bulk-Only Transport"
    protocole == 98 -> "UAS"
    protocole == 0 -> "CBI"
    else -> "sous-classe " + sousClasse + ", protocole " + protocole
}
