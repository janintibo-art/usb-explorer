package art.janintibo.usbexplorer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Le disque ne peut pas apparaître dans Mes fichiers : cette liste est réservée
 * aux volumes montés par Android. La notification joue le même rôle, sans
 * dépendre de Samsung.
 */
class EcouteurUsb : BroadcastReceiver() {

    override fun onReceive(contexte: Context?, intention: Intent?) {
        if (contexte == null || intention == null) return
        when (intention.action) {
            UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                val appareil = intention.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                if (appareil == null || !Avis.stockage(appareil)) return
                Avis.signaler(contexte, Avis.nommer(appareil))
            }

            UsbManager.ACTION_USB_DEVICE_DETACHED -> Avis.effacer(contexte)
        }
    }
}

object Avis {

    private const val CANAL = "branchement"
    private const val IDENTIFIANT = 1001

    fun stockage(appareil: UsbDevice): Boolean {
        for (i in 0 until appareil.interfaceCount) {
            if (appareil.getInterface(i).interfaceClass == UsbConstants.USB_CLASS_MASS_STORAGE) {
                return true
            }
        }
        return false
    }

    fun nommer(appareil: UsbDevice): String {
        val produit = appareil.productName
        if (produit != null && produit.isNotEmpty()) return produit
        val marque = appareil.manufacturerName
        if (marque != null && marque.isNotEmpty()) return marque
        return "Disque USB"
    }

    fun signaler(contexte: Context, nom: String) {
        preparerCanal(contexte)

        val ouverture = Intent(contexte, MainActivity::class.java)
        ouverture.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val differee = PendingIntent.getActivity(
            contexte,
            0,
            ouverture,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val avis = NotificationCompat.Builder(contexte, CANAL)
            .setSmallIcon(R.drawable.ic_drive)
            .setContentTitle(nom)
            .setContentText("Montez-le dans Paragon, puis ouvrez-le ici.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(differee)
            .setAutoCancel(false)
            .setOngoing(false)
            .build()

        try {
            val gestionnaire = NotificationManagerCompat.from(contexte)
            if (!gestionnaire.areNotificationsEnabled()) return
            gestionnaire.notify(IDENTIFIANT, avis)
        } catch (e: SecurityException) {
        } catch (e: Exception) {
        }
    }

    fun effacer(contexte: Context) {
        try {
            NotificationManagerCompat.from(contexte).cancel(IDENTIFIANT)
        } catch (e: Exception) {
        }
    }

    private fun preparerCanal(contexte: Context) {
        val canal = NotificationChannel(
            CANAL,
            "Branchement d'un disque",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        canal.description = "Prévient quand une clé ou un disque est connecté en USB."
        canal.setShowBadge(false)
        try {
            val gestionnaire =
                contexte.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            gestionnaire.createNotificationChannel(canal)
        } catch (e: Exception) {
        }
    }
}
