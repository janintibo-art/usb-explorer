package art.janintibo.usbexplorer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EcranFichiers(etat: FichiersViewModel, onChoisirVolume: () -> Unit) {

    var menuTri by remember { mutableStateOf(false) }
    var fiche by remember { mutableStateOf<Entree?>(null) }
    var renommage by remember { mutableStateOf<Entree?>(null) }
    var suppression by remember { mutableStateOf<Entree?>(null) }

    val racine = etat.arbre
    if (racine == null) {
        Invitation(onChoisirVolume)
        return
    }

    Column(Modifier.fillMaxSize()) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                etat.chemin.forEachIndexed { position, etape ->
                    if (position > 0) {
                        Text(
                            text = "›",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Cendre,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                    }
                    Text(
                        text = etape.nom,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (position == etat.chemin.size - 1) Clair else Doux,
                        maxLines = 1,
                        modifier = Modifier.clickable { etat.remonterA(position) }
                    )
                }
            }

            Box {
                IconButton(onClick = { menuTri = true }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_sort),
                        contentDescription = "Trier",
                        tint = Doux,
                        modifier = Modifier.size(20.dp)
                    )
                }
                DropdownMenu(expanded = menuTri, onDismissRequest = { menuTri = false }) {
                    DropdownMenuItem(
                        text = { Text(libelleTri("Nom", etat, Tri.NOM)) },
                        onClick = {
                            menuTri = false
                            etat.changerTri(Tri.NOM)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(libelleTri("Taille", etat, Tri.TAILLE)) },
                        onClick = {
                            menuTri = false
                            etat.changerTri(Tri.TAILLE)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(libelleTri("Date", etat, Tri.DATE)) },
                        onClick = {
                            menuTri = false
                            etat.changerTri(Tri.DATE)
                        }
                    )
                }
            }

            IconButton(onClick = { etat.basculerAffichage() }) {
                Icon(
                    painter = painterResource(
                        id = if (etat.grille) R.drawable.ic_list else R.drawable.ic_grid
                    ),
                    contentDescription = "Changer d'affichage",
                    tint = Doux,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Box(Modifier.weight(1f)) {
            when {
                etat.chargement && etat.entrees.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Menthe)
                }

                etat.entrees.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Dossier vide",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Doux
                    )
                }

                etat.grille -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 106.dp),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(etat.entrees.size) { index ->
                        val entree = etat.entrees[index]
                        Tuile(
                            entree = entree,
                            arbre = racine,
                            onOuvrir = { if (entree.dossier) etat.entrer(entree) else etat.ouvrir(entree) },
                            onFiche = { fiche = entree }
                        )
                    }
                }

                else -> LazyColumn(
                    contentPadding = PaddingValues(
                        start = 12.dp, end = 4.dp, top = 4.dp, bottom = 24.dp
                    )
                ) {
                    items(etat.entrees.size) { index ->
                        val entree = etat.entrees[index]
                        Ligne(
                            entree = entree,
                            arbre = racine,
                            onOuvrir = { if (entree.dossier) etat.entrer(entree) else etat.ouvrir(entree) },
                            onFiche = { fiche = entree }
                        )
                    }
                }
            }
        }

        val note = etat.message
        if (note != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Pupitre)
                    .padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = Clair,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { etat.fermerMessage() }) {
                    Text("Fermer", color = Menthe)
                }
            }
        }
    }

    val choisie = fiche
    if (choisie != null) {
        AlertDialog(
            onDismissRequest = { fiche = null },
            shape = RoundedCornerShape(18.dp),
            containerColor = Pupitre,
            title = { Text(choisie.nom, color = Clair, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    if (!choisie.dossier) {
                        Text(
                            text = poids(choisie.octets) + " · " + quand(choisie.date),
                            style = MaterialTheme.typography.bodySmall,
                            color = Doux
                        )
                        Spacer(Modifier.height(14.dp))
                    }
                    Action("Ouvrir") {
                        fiche = null
                        if (choisie.dossier) etat.entrer(choisie) else etat.ouvrir(choisie)
                    }
                    Action("Renommer") {
                        fiche = null
                        renommage = choisie
                    }
                    if (!choisie.dossier) {
                        Action("Copier vers le téléphone") {
                            fiche = null
                            etat.copier(choisie)
                        }
                    }
                    Action("Supprimer") {
                        fiche = null
                        suppression = choisie
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { fiche = null }) {
                    Text("Fermer", color = Doux)
                }
            }
        )
    }

    val aRenommer = renommage
    if (aRenommer != null) {
        var texte by remember(aRenommer.identifiant) { mutableStateOf(aRenommer.nom) }
        AlertDialog(
            onDismissRequest = { renommage = null },
            shape = RoundedCornerShape(18.dp),
            containerColor = Pupitre,
            title = { Text("Renommer", color = Clair) },
            text = {
                OutlinedTextField(
                    value = texte,
                    onValueChange = { texte = it },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    etat.renommer(aRenommer, texte)
                    renommage = null
                }) {
                    Text("Renommer", color = Menthe)
                }
            },
            dismissButton = {
                TextButton(onClick = { renommage = null }) {
                    Text("Annuler", color = Doux)
                }
            }
        )
    }

    val aSupprimer = suppression
    if (aSupprimer != null) {
        AlertDialog(
            onDismissRequest = { suppression = null },
            shape = RoundedCornerShape(18.dp),
            containerColor = Pupitre,
            title = { Text("Supprimer ?", color = Clair) },
            text = {
                Text(
                    text = aSupprimer.nom + " sera effacé du disque. C'est définitif.",
                    color = Doux
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    etat.supprimer(aSupprimer)
                    suppression = null
                }) {
                    Text("Supprimer", color = Rouille)
                }
            },
            dismissButton = {
                TextButton(onClick = { suppression = null }) {
                    Text("Annuler", color = Doux)
                }
            }
        )
    }
}

@Composable
private fun Action(texte: String, onClick: () -> Unit) {
    Text(
        text = texte,
        style = MaterialTheme.typography.bodyLarge,
        color = Clair,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Ligne(
    entree: Entree,
    arbre: android.net.Uri,
    onOuvrir: () -> Unit,
    onFiche: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onOuvrir, onLongClick = onFiche)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Apercu(entree = entree, arbre = arbre, cote = 44)
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = entree.nom,
                style = MaterialTheme.typography.bodyLarge,
                color = Clair,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (entree.dossier) quand(entree.date)
                else poids(entree.octets) + " · " + quand(entree.date),
                style = MaterialTheme.typography.labelSmall,
                color = Doux
            )
        }
        IconButton(onClick = onFiche) {
            Icon(
                painter = painterResource(id = R.drawable.ic_more),
                contentDescription = "Actions",
                tint = Cendre,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Tuile(
    entree: Entree,
    arbre: android.net.Uri,
    onOuvrir: () -> Unit,
    onFiche: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onOuvrir, onLongClick = onFiche),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(Pupitre),
            contentAlignment = Alignment.Center
        ) {
            Apercu(entree = entree, arbre = arbre, cote = 0)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = entree.nom,
            style = MaterialTheme.typography.labelSmall,
            color = Clair,
            maxLines = 2,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}

/** Vignette si le fournisseur en produit une, icône de type sinon. */
@Composable
private fun Apercu(entree: Entree, arbre: android.net.Uri, cote: Int) {
    val contexte = LocalContext.current
    val vignette by produceState<ImageBitmap?>(initialValue = null, key1 = entree.identifiant) {
        value = if (Vignettes.possible(entree)) Vignettes.charger(contexte, arbre, entree) else null
    }

    val forme = if (cote > 0) {
        Modifier
            .size(cote.dp)
            .clip(RoundedCornerShape(9.dp))
    } else {
        Modifier.fillMaxSize()
    }

    val image = vignette
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = forme
        )
        return
    }

    Box(
        modifier = forme.background(if (cote > 0) Pupitre else Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = icone(entree)),
            contentDescription = null,
            colorFilter = ColorFilter.tint(if (entree.dossier) Menthe else Cendre),
            modifier = Modifier.size(if (cote > 0) 21.dp else 30.dp)
        )
    }
}

@Composable
private fun Invitation(onChoisirVolume: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Pupitre),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_folder),
                contentDescription = null,
                colorFilter = ColorFilter.tint(Menthe),
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = "Choisissez le volume",
            style = MaterialTheme.typography.titleMedium,
            color = Clair
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Montez d'abord le disque dans Paragon. Appuyez ensuite ici, ouvrez le " +
                "menu du sélecteur et prenez « Paragon File System Link », puis votre volume.",
            style = MaterialTheme.typography.bodyMedium,
            color = Doux,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onChoisirVolume,
            shape = RoundedCornerShape(11.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Menthe, contentColor = Nuit)
        ) {
            Text("Choisir le volume")
        }
    }
}

private fun libelleTri(nom: String, etat: FichiersViewModel, cible: Tri): String {
    if (etat.tri != cible) return nom
    return nom + if (etat.croissant) "  ↑" else "  ↓"
}

private fun icone(entree: Entree): Int = when {
    entree.dossier -> R.drawable.ic_folder
    entree.type.startsWith("image/") -> R.drawable.ic_image
    entree.type.startsWith("video/") -> R.drawable.ic_video
    entree.type.startsWith("audio/") -> R.drawable.ic_audio
    else -> R.drawable.ic_document
}

private fun quand(millisecondes: Long): String {
    if (millisecondes <= 0L) return "date inconnue"
    val format = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
    return format.format(Date(millisecondes))
}
