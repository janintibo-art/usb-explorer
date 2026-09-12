package art.janintibo.usbexplorer

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

data class Entree(
    val identifiant: String,
    val nom: String,
    val type: String,
    val octets: Long,
    val date: Long,
    val dossier: Boolean
)

enum class Tri { NOM, TAILLE, DATE }

/**
 * Paragon publie ses volumes montés à travers le Storage Access Framework.
 * On ne touche donc ni à l'USB ni au système de fichiers : on interroge un
 * fournisseur de documents, exactement comme on le ferait pour Drive.
 */
object Saf {

    private val colonnes = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED
    )

    fun racine(arbre: Uri): String = DocumentsContract.getTreeDocumentId(arbre)

    fun documentUri(arbre: Uri, identifiant: String): Uri =
        DocumentsContract.buildDocumentUriUsingTree(arbre, identifiant)

    suspend fun lister(
        contexte: Context,
        arbre: Uri,
        identifiant: String,
        tri: Tri,
        croissant: Boolean
    ): List<Entree> = withContext(Dispatchers.IO) {

        val enfants = DocumentsContract.buildChildDocumentsUriUsingTree(arbre, identifiant)
        val trouves = ArrayList<Entree>()

        try {
            contexte.contentResolver.query(enfants, colonnes, null, null, null)?.use { curseur ->
                while (curseur.moveToNext()) {
                    val identite = curseur.getString(0)
                    if (identite == null || identite.isEmpty()) continue
                    val type = curseur.getString(2) ?: ""
                    trouves.add(
                        Entree(
                            identifiant = identite,
                            nom = curseur.getString(1) ?: identite,
                            type = type,
                            octets = curseur.getLong(3),
                            date = curseur.getLong(4),
                            dossier = type == DocumentsContract.Document.MIME_TYPE_DIR
                        )
                    )
                }
            }
        } catch (e: Exception) {
            return@withContext emptyList()
        }

        ordonner(trouves, tri, croissant)
    }

    private fun ordonner(liste: List<Entree>, tri: Tri, croissant: Boolean): List<Entree> {
        val comparateur = when (tri) {
            Tri.NOM -> compareBy<Entree> { it.nom.lowercase() }
            Tri.TAILLE -> compareBy<Entree> { it.octets }
            Tri.DATE -> compareBy<Entree> { it.date }
        }
        val trie = if (croissant) liste.sortedWith(comparateur)
        else liste.sortedWith(comparateur.reversed())
        // Les dossiers d'abord : on cherche un chemin avant de chercher un fichier.
        return trie.sortedByDescending { it.dossier }
    }

    suspend fun renommer(contexte: Context, arbre: Uri, entree: Entree, nom: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                DocumentsContract.renameDocument(
                    contexte.contentResolver,
                    documentUri(arbre, entree.identifiant),
                    nom
                ) != null
            } catch (e: Exception) {
                false
            }
        }

    suspend fun supprimer(contexte: Context, arbre: Uri, entree: Entree): Boolean =
        withContext(Dispatchers.IO) {
            try {
                DocumentsContract.deleteDocument(
                    contexte.contentResolver,
                    documentUri(arbre, entree.identifiant)
                )
            } catch (e: Exception) {
                false
            }
        }

    /** Copie un fichier du disque vers Téléchargements/USB Explorer. */
    suspend fun versTelephone(contexte: Context, arbre: Uri, entree: Entree): String =
        withContext(Dispatchers.IO) {
            val resolveur: ContentResolver = contexte.contentResolver
            val valeurs = ContentValues()
            valeurs.put(MediaStore.Downloads.DISPLAY_NAME, entree.nom)
            if (entree.type.isNotEmpty()) {
                valeurs.put(MediaStore.Downloads.MIME_TYPE, entree.type)
            }
            valeurs.put(
                MediaStore.Downloads.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/USB Explorer"
            )

            val cible = try {
                resolveur.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, valeurs)
            } catch (e: Exception) {
                null
            } ?: return@withContext "Impossible de créer le fichier sur le téléphone."

            val erreur = try {
                copier(resolveur, Saf.documentUri(arbre, entree.identifiant), cible)
            } catch (e: Exception) {
                e.message ?: "erreur inconnue"
            }

            if (erreur == null) {
                "Copié dans Téléchargements/USB Explorer"
            } else {
                try { resolveur.delete(cible, null, null) } catch (e: Exception) { }
                "Copie interrompue : " + erreur
            }
        }

    /** Renvoie null en cas de succès, le motif de l'échec sinon. */
    private fun copier(resolveur: ContentResolver, source: Uri, cible: Uri): String? {
        val entrant = resolveur.openInputStream(source) ?: return "fichier illisible sur le disque"
        entrant.use { lecture ->
            val sortant = resolveur.openOutputStream(cible)
                ?: return "écriture refusée sur le téléphone"
            sortant.use { ecriture ->
                lecture.copyTo(ecriture, 1 shl 16)
                ecriture.flush()
            }
        }
        return null
    }

    fun ouvrirAvec(contexte: Context, arbre: Uri, entree: Entree): Boolean {
        val intention = Intent(Intent.ACTION_VIEW)
        val type = if (entree.type.isEmpty()) "*/*" else entree.type
        intention.setDataAndType(documentUri(arbre, entree.identifiant), type)
        intention.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intention.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            contexte.startActivity(intention)
            true
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * Les vignettes dépendent du fournisseur : Paragon peut très bien ne pas en
 * produire. On essaie, et on retombe sur l'icône de type sans bruit.
 */
object Vignettes {

    private val cache = ConcurrentHashMap<String, ImageBitmap>()
    private val absentes = ConcurrentHashMap<String, Boolean>()

    fun possible(entree: Entree): Boolean =
        entree.type.startsWith("image/") || entree.type.startsWith("video/")

    suspend fun charger(contexte: Context, arbre: Uri, entree: Entree): ImageBitmap? =
        withContext(Dispatchers.IO) {
            val cle = entree.identifiant
            val connue = cache[cle]
            if (connue != null) return@withContext connue
            if (absentes.containsKey(cle)) return@withContext null
            try {
                val image = contexte.contentResolver.loadThumbnail(
                    Saf.documentUri(arbre, entree.identifiant),
                    Size(256, 256),
                    null
                ).asImageBitmap()
                cache[cle] = image
                image
            } catch (e: Exception) {
                absentes[cle] = true
                null
            }
        }

    fun oublier() {
        cache.clear()
        absentes.clear()
    }
}
