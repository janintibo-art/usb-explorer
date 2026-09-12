package art.janintibo.usbexplorer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun EcranDisques(
    disques: List<DisqueInfo>,
    etat: String,
    attente: Boolean,
    onAnalyser: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Pupitre)
                    .border(1.dp, Rainure, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = "Analyse bas niveau",
                    style = MaterialTheme.typography.titleMedium,
                    color = Clair
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Lire les secteurs suppose de réclamer le disque de force. " +
                        "Cela coupe le montage de Paragon, et l'onglet Fichiers ne verra " +
                        "plus rien tant que vous ne l'aurez pas remonté. C'est pour cette " +
                        "raison que rien ne se lance tout seul ici.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Doux
                )
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = onAnalyser,
                    enabled = !attente,
                    shape = RoundedCornerShape(11.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Ambre,
                        contentColor = Nuit
                    )
                ) {
                    Text(if (attente) "Analyse en cours…" else "Analyser les disques")
                }
                if (disques.isEmpty() && etat.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = etat,
                        style = MaterialTheme.typography.bodySmall,
                        color = Doux
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
        }

        items(disques.size) { index ->
            BlocDisque(disques[index])
            Spacer(Modifier.height(18.dp))
        }

        item {
            Text(
                text = "Rien n'est jamais écrit sur ces disques : cette partie ne sait que " +
                    "lire des secteurs. Si Android propose de formater, refusez : cela " +
                    "effacerait tout le contenu.",
                style = MaterialTheme.typography.bodySmall,
                color = Doux,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun BlocDisque(disque: DisqueInfo) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Pupitre)
            .border(1.dp, Rainure, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Menthe.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_drive),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(Menthe),
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (disque.modele.isEmpty()) "Disque USB" else disque.modele,
                    style = MaterialTheme.typography.titleMedium,
                    color = Clair,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOf(disque.fabricant, disque.revision)
                        .filter { it.isNotEmpty() }
                        .joinToString(" "),
                    style = MaterialTheme.typography.bodySmall,
                    color = Doux,
                    maxLines = 1
                )
            }
            Text(
                text = poids(disque.capacite),
                style = MaterialTheme.typography.titleMedium,
                color = Clair
            )
        }

        Spacer(Modifier.height(16.dp))
        CarteDisque(disque)
        Spacer(Modifier.height(10.dp))

        Text(
            text = disque.schema + " · " + disque.tailleBloc + " o par secteur · " +
                disque.partitions.size + " partition" +
                (if (disque.partitions.size > 1) "s" else ""),
            style = MaterialTheme.typography.bodySmall,
            color = Doux,
            fontFamily = FontFamily.Monospace
        )

        if (disque.partitions.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            for (partition in disque.partitions) {
                LignePartition(partition)
            }
        }
    }
}

/** Le disque vu à l'échelle : chaque partition occupe sa vraie proportion. */
@Composable
private fun CarteDisque(disque: DisqueInfo) {
    val total = disque.capacite.coerceAtLeast(1L)
    val segments = ArrayList<Pair<Float, Color?>>()
    var curseur = 0L

    for (partition in disque.partitions.sortedBy { it.premierBloc }) {
        val debut = partition.premierBloc * disque.tailleBloc.toLong()
        if (debut > curseur) {
            segments.add(Pair((debut - curseur).toFloat() / total.toFloat(), null))
        }
        segments.add(Pair(partition.octets.toFloat() / total.toFloat(), teinte(partition.prise)))
        curseur = debut + partition.octets
    }
    if (curseur < disque.capacite) {
        segments.add(Pair((disque.capacite - curseur).toFloat() / total.toFloat(), null))
    }
    if (segments.isEmpty()) {
        segments.add(Pair(1f, null))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .clip(RoundedCornerShape(7.dp)),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        for (segment in segments) {
            val part = segment.first.coerceAtLeast(0.008f)
            Box(
                modifier = Modifier
                    .weight(part)
                    .fillMaxHeight()
                    .background(segment.second ?: Rainure)
            )
        }
    }
}

@Composable
private fun LignePartition(partition: PartitionInfo) {
    val couleur = teinte(partition.prise)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(40.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(couleur)
        )
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (partition.volume.isEmpty()) partition.nom
                    else partition.volume,
                    style = MaterialTheme.typography.titleSmall,
                    color = Clair,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(couleur.copy(alpha = 0.16f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = partition.format,
                        style = MaterialTheme.typography.labelSmall,
                        color = couleur
                    )
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = "secteur " + partition.premierBloc + " · " + partition.nombreBlocs +
                    " secteurs",
                style = MaterialTheme.typography.labelSmall,
                color = Doux,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = mention(partition.prise),
                style = MaterialTheme.typography.labelSmall,
                color = Doux
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = poids(partition.octets),
            style = MaterialTheme.typography.titleSmall,
            color = Clair
        )
    }
}
