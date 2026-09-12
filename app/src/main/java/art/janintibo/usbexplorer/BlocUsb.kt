package art.janintibo.usbexplorer

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Accès bloc à un périphérique de stockage de masse USB.
 *
 * Android ne monte que FAT32 et exFAT, et aucune application ne peut monter
 * autre chose sans être root. On contourne en parlant directement au disque :
 * on réclame son interface, on lui envoie des commandes SCSI encapsulées dans
 * le protocole Bulk-Only Transport, et on lit les secteurs bruts. Rien n'est
 * jamais écrit ici : cette classe ne connaît que la lecture.
 */
class BlocUsb private constructor(
    private val connexion: UsbDeviceConnection,
    private val interfaceUsb: UsbInterface,
    private val entree: UsbEndpoint,
    private val sortie: UsbEndpoint
) {

    var fabricant: String = ""
        private set
    var modele: String = ""
        private set
    var revision: String = ""
        private set
    var tailleBloc: Int = 512
        private set
    var nombreBlocs: Long = 0L
        private set

    val capacite: Long get() = nombreBlocs * tailleBloc.toLong()

    private var etiquette = 0

    companion object {

        private const val DELAI = 5000
        private const val SIGNATURE_CBW = 0x43425355
        private const val SIGNATURE_CSW = 0x53425355

        /** Ouvre le premier périphérique de stockage de masse trouvé. */
        fun ouvrir(gestionnaire: UsbManager, appareil: UsbDevice): BlocUsb? {
            var cible: UsbInterface? = null
            for (i in 0 until appareil.interfaceCount) {
                val candidat = appareil.getInterface(i)
                if (candidat.interfaceClass == UsbConstants.USB_CLASS_MASS_STORAGE &&
                    candidat.interfaceSubclass == 6 &&
                    candidat.interfaceProtocol == 80
                ) {
                    cible = candidat
                    break
                }
            }
            if (cible == null) return null

            var lecture: UsbEndpoint? = null
            var ecriture: UsbEndpoint? = null
            for (i in 0 until cible.endpointCount) {
                val point = cible.getEndpoint(i)
                if (point.type != UsbConstants.USB_ENDPOINT_XFER_BULK) continue
                if (point.direction == UsbConstants.USB_DIR_IN) lecture = point else ecriture = point
            }
            if (lecture == null || ecriture == null) return null

            val connexion = gestionnaire.openDevice(appareil) ?: return null
            if (!connexion.claimInterface(cible, true)) {
                connexion.close()
                return null
            }

            val bloc = BlocUsb(connexion, cible, lecture, ecriture)
            if (!bloc.interroger() || !bloc.mesurer()) {
                bloc.fermer()
                return null
            }
            return bloc
        }
    }

    fun fermer() {
        try {
            connexion.releaseInterface(interfaceUsb)
        } catch (e: Exception) {
        }
        try {
            connexion.close()
        } catch (e: Exception) {
        }
    }

    /** Lit [blocs] secteurs à partir du secteur logique [lba]. */
    fun lire(lba: Long, blocs: Int): ByteArray? {
        val longueur = blocs * tailleBloc
        val tampon = ByteArray(longueur)
        val cdb = ByteArray(10)
        cdb[0] = 0x28
        cdb[2] = ((lba shr 24) and 0xFF).toByte()
        cdb[3] = ((lba shr 16) and 0xFF).toByte()
        cdb[4] = ((lba shr 8) and 0xFF).toByte()
        cdb[5] = (lba and 0xFF).toByte()
        cdb[7] = ((blocs shr 8) and 0xFF).toByte()
        cdb[8] = (blocs and 0xFF).toByte()
        return if (transaction(cdb, tampon, longueur, true)) tampon else null
    }

    private fun interroger(): Boolean {
        val cdb = ByteArray(6)
        cdb[0] = 0x12
        cdb[4] = 36
        val reponse = ByteArray(36)
        if (!transaction(cdb, reponse, 36, true)) return false
        fabricant = texte(reponse, 8, 8)
        modele = texte(reponse, 16, 16)
        revision = texte(reponse, 32, 4)
        return true
    }

    private fun mesurer(): Boolean {
        val cdb = ByteArray(10)
        cdb[0] = 0x25
        val reponse = ByteArray(8)
        if (!transaction(cdb, reponse, 8, true)) return false
        val dernier = entierGrand(reponse, 0)
        val taille = entierGrand(reponse, 4)
        if (taille <= 0L || taille > 8192L) return false
        tailleBloc = taille.toInt()
        nombreBlocs = dernier + 1L
        return nombreBlocs > 0L
    }

    /** Une commande SCSI complète : enveloppe, données, accusé de réception. */
    private fun transaction(
        cdb: ByteArray,
        donnees: ByteArray?,
        longueur: Int,
        versHote: Boolean
    ): Boolean {
        etiquette++

        val enveloppe = ByteBuffer.allocate(31).order(ByteOrder.LITTLE_ENDIAN)
        enveloppe.putInt(SIGNATURE_CBW)
        enveloppe.putInt(etiquette)
        enveloppe.putInt(longueur)
        enveloppe.put(if (versHote) 0x80.toByte() else 0x00)
        enveloppe.put(0)
        enveloppe.put(cdb.size.toByte())
        enveloppe.put(cdb)

        val octetsEnveloppe = enveloppe.array()
        if (connexion.bulkTransfer(sortie, octetsEnveloppe, octetsEnveloppe.size, DELAI) != 31) {
            return false
        }

        if (donnees != null && longueur > 0) {
            var transferes = 0
            while (transferes < longueur) {
                val morceau = ByteArray(longueur - transferes)
                val point = if (versHote) entree else sortie
                val lus = connexion.bulkTransfer(point, morceau, morceau.size, DELAI)
                if (lus <= 0) return false
                System.arraycopy(morceau, 0, donnees, transferes, lus)
                transferes += lus
            }
        }

        val accuse = ByteArray(13)
        if (connexion.bulkTransfer(entree, accuse, 13, DELAI) != 13) return false
        val lecture = ByteBuffer.wrap(accuse).order(ByteOrder.LITTLE_ENDIAN)
        if (lecture.int != SIGNATURE_CSW) return false
        return accuse[12].toInt() == 0
    }

    private fun texte(source: ByteArray, debut: Int, taille: Int): String {
        val brut = String(source, debut, taille, Charsets.US_ASCII)
        return brut.trim().replace("\u0000", "")
    }

    private fun entierGrand(source: ByteArray, position: Int): Long {
        return ((source[position].toLong() and 0xFF) shl 24) or
            ((source[position + 1].toLong() and 0xFF) shl 16) or
            ((source[position + 2].toLong() and 0xFF) shl 8) or
            (source[position + 3].toLong() and 0xFF)
    }
}
