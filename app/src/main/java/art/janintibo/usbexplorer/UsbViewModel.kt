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

class UsbViewModel(application: Application) : AndroidViewModel(application) {

    var disques by mutableStateOf<List<DisqueInfo>>(emptyList())
        private set
    var occupe by mutableStateOf(false)
        private set
    var etat by mutableStateOf("")
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
            val trouves = ArrayList<DisqueInfo>()
            var attente = false

            val appareils = gestionnaire.deviceList.values.filter { stockage(it) }

            for (appareil in appareils) {
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
                appareils.isEmpty() -> "Aucun disque branché"
                attente && trouves.isEmpty() -> "En attente de votre autorisation"
                trouves.isEmpty() -> "Disque détecté, mais illisible en mode bloc"
                else -> ""
            }
            premierPassage = false
            occupe = false
        }
    }

    /** Appelé quand la boîte de dialogue d'autorisation s'est refermée. */
    fun permissionRepondue() {
        demandeEnCours = false
        scanner()
    }

    fun appareilChange() {
        demandeEnCours = false
        disques = emptyList()
        scanner()
    }

    private fun stockage(appareil: UsbDevice): Boolean {
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
