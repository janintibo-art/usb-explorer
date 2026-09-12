package art.janintibo.usbexplorer

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

data class Etape(val identifiant: String, val nom: String)

class FichiersViewModel(application: Application) : AndroidViewModel(application) {

    var arbre by mutableStateOf<Uri?>(null)
        private set
    var chemin by mutableStateOf<List<Etape>>(emptyList())
        private set
    var entrees by mutableStateOf<List<Entree>>(emptyList())
        private set
    var chargement by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    var tri by mutableStateOf(Tri.NOM)
        private set
    var croissant by mutableStateOf(true)
        private set
    var grille by mutableStateOf(false)
        private set

    init {
        val memorise = Depot.lireArbre(application)
        if (memorise != null && autorise(application, memorise)) {
            arbre = memorise
            chemin = listOf(Etape(Saf.racine(memorise), "Volume"))
        }
    }

    private fun autorise(contexte: Context, uri: Uri): Boolean {
        for (droit in contexte.contentResolver.persistedUriPermissions) {
            if (droit.uri == uri && droit.isReadPermission) return true
        }
        return false
    }

    fun definirArbre(uri: Uri) {
        val contexte = getApplication<Application>()
        try {
            contexte.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: Exception) {
        }
        Depot.ecrireArbre(contexte, uri)
        Vignettes.oublier()
        arbre = uri
        chemin = listOf(Etape(Saf.racine(uri), "Volume"))
        entrees = emptyList()
        recharger()
    }

    fun oublierArbre() {
        val contexte = getApplication<Application>()
        Depot.effacerArbre(contexte)
        Vignettes.oublier()
        arbre = null
        chemin = emptyList()
        entrees = emptyList()
    }

    fun recharger() {
        val racine = arbre ?: return
        val courant = chemin.lastOrNull() ?: return
        if (chargement) return
        chargement = true
        viewModelScope.launch {
            entrees = Saf.lister(
                getApplication<Application>(),
                racine,
                courant.identifiant,
                tri,
                croissant
            )
            chargement = false
        }
    }

    fun entrer(entree: Entree) {
        if (!entree.dossier) return
        chemin = chemin + Etape(entree.identifiant, entree.nom)
        entrees = emptyList()
        recharger()
    }

    fun remonterA(position: Int) {
        if (position < 0 || position >= chemin.size) return
        chemin = chemin.subList(0, position + 1).toList()
        entrees = emptyList()
        recharger()
    }

    /** Renvoie vrai si la navigation a consommé le geste de retour. */
    fun remonter(): Boolean {
        if (chemin.size <= 1) return false
        chemin = chemin.subList(0, chemin.size - 1).toList()
        entrees = emptyList()
        recharger()
        return true
    }

    fun changerTri(nouveau: Tri) {
        if (tri == nouveau) {
            croissant = !croissant
        } else {
            tri = nouveau
            croissant = true
        }
        recharger()
    }

    fun basculerAffichage() {
        grille = !grille
    }

    fun ouvrir(entree: Entree) {
        val racine = arbre ?: return
        if (!Saf.ouvrirAvec(getApplication<Application>(), racine, entree)) {
            message = "Aucune application ne sait ouvrir ce fichier."
        }
    }

    fun renommer(entree: Entree, nom: String) {
        val racine = arbre ?: return
        val propre = nom.trim()
        if (propre.isEmpty() || propre == entree.nom) return
        viewModelScope.launch {
            val fait = Saf.renommer(getApplication<Application>(), racine, entree, propre)
            message = if (fait) "Renommé" else "Renommage refusé par le volume."
            Vignettes.oublier()
            recharger()
        }
    }

    fun supprimer(entree: Entree) {
        val racine = arbre ?: return
        viewModelScope.launch {
            val fait = Saf.supprimer(getApplication<Application>(), racine, entree)
            message = if (fait) "Supprimé" else "Suppression refusée par le volume."
            recharger()
        }
    }

    fun copier(entree: Entree) {
        val racine = arbre ?: return
        if (entree.dossier) {
            message = "La copie de dossiers arrivera dans une prochaine version."
            return
        }
        viewModelScope.launch {
            message = "Copie en cours…"
            message = Saf.versTelephone(getApplication<Application>(), racine, entree)
        }
    }

    fun fermerMessage() {
        message = null
    }
}

object Depot {

    private const val FICHIER = "usb_explorer"
    private const val CLE = "arbre"

    fun lireArbre(contexte: Context): Uri? {
        val reglages = contexte.getSharedPreferences(FICHIER, Context.MODE_PRIVATE)
        val texte = reglages.getString(CLE, null) ?: return null
        return try {
            Uri.parse(texte)
        } catch (e: Exception) {
            null
        }
    }

    fun ecrireArbre(contexte: Context, uri: Uri) {
        contexte.getSharedPreferences(FICHIER, Context.MODE_PRIVATE)
            .edit()
            .putString(CLE, uri.toString())
            .apply()
    }

    fun effacerArbre(contexte: Context) {
        contexte.getSharedPreferences(FICHIER, Context.MODE_PRIVATE)
            .edit()
            .remove(CLE)
            .apply()
    }
}
