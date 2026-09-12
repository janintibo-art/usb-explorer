package art.janintibo.usbexplorer

import java.nio.ByteBuffer
import java.nio.ByteOrder

enum class Prise { NATIVE, A_VENIR, HORS_PORTEE }

data class PartitionInfo(
    val rang: Int,
    val nom: String,
    val premierBloc: Long,
    val nombreBlocs: Long,
    val octets: Long,
    val format: String,
    val volume: String,
    val prise: Prise
)

data class DisqueInfo(
    val fabricant: String,
    val modele: String,
    val revision: String,
    val capacite: Long,
    val tailleBloc: Int,
    val schema: String,
    val partitions: List<PartitionInfo>
)

object Tables {

    fun analyser(bloc: BlocUsb): DisqueInfo {
        val partitions = ArrayList<PartitionInfo>()
        var schema = "Inconnu"

        val secteurZero = bloc.lire(0L, 1)

        // Beaucoup de clés n'ont aucune table : le système de fichiers commence
        // au tout premier secteur. On teste ce cas avant de chercher une table,
        // sinon un secteur d'amorçage NTFS se fait passer pour un MBR.
        val brut = if (secteurZero != null) identifier(bloc, 0L) else null
        if (brut != null && brut.first != "Inconnu") {
            schema = "Sans table"
            partitions.add(
                composer(1, "Volume unique", 0L, bloc.nombreBlocs, bloc.tailleBloc, brut)
            )
            return assembler(bloc, schema, partitions)
        }

        if (secteurZero == null || secteurZero.size < 512) {
            return assembler(bloc, "Illisible", partitions)
        }
        if ((secteurZero[510].toInt() and 0xFF) != 0x55 ||
            (secteurZero[511].toInt() and 0xFF) != 0xAA
        ) {
            return assembler(bloc, "Aucune table reconnue", partitions)
        }

        var gpt = false
        for (i in 0 until 4) {
            if ((secteurZero[446 + 16 * i + 4].toInt() and 0xFF) == 0xEE) gpt = true
        }

        if (gpt) {
            schema = "GPT"
            lireGpt(bloc, partitions)
            if (partitions.isEmpty()) schema = "GPT illisible"
        } else {
            schema = "MBR"
            lireMbr(bloc, secteurZero, partitions)
        }

        return assembler(bloc, schema, partitions)
    }

    private fun assembler(
        bloc: BlocUsb,
        schema: String,
        partitions: List<PartitionInfo>
    ) = DisqueInfo(
        fabricant = bloc.fabricant,
        modele = bloc.modele,
        revision = bloc.revision,
        capacite = bloc.capacite,
        tailleBloc = bloc.tailleBloc,
        schema = schema,
        partitions = partitions
    )

    private fun lireMbr(bloc: BlocUsb, secteur: ByteArray, sortie: ArrayList<PartitionInfo>) {
        var rang = 0
        for (i in 0 until 4) {
            val base = 446 + 16 * i
            val type = secteur[base + 4].toInt() and 0xFF
            if (type == 0) continue
            val premier = entierPetit(secteur, base + 8)
            val nombre = entierPetit(secteur, base + 12)
            if (nombre <= 0L) continue

            if (type == 0x05 || type == 0x0F || type == 0x85) {
                lireEtendue(bloc, premier, sortie)
                continue
            }
            rang = sortie.size + 1
            sortie.add(
                composer(
                    rang,
                    "Partition " + rang,
                    premier,
                    nombre,
                    bloc.tailleBloc,
                    identifier(bloc, premier) ?: Pair(nomType(type), "")
                )
            )
        }
    }

    private fun lireEtendue(bloc: BlocUsb, base: Long, sortie: ArrayList<PartitionInfo>) {
        var courant = base
        var garde = 0
        while (garde < 32) {
            garde++
            val secteur = bloc.lire(courant, 1) ?: return
            if (secteur.size < 512) return
            val typeLogique = secteur[446 + 4].toInt() and 0xFF
            val decalage = entierPetit(secteur, 446 + 8)
            val nombre = entierPetit(secteur, 446 + 12)
            if (typeLogique != 0 && nombre > 0L) {
                val debut = courant + decalage
                val rang = sortie.size + 1
                sortie.add(
                    composer(
                        rang,
                        "Partition logique " + rang,
                        debut,
                        nombre,
                        bloc.tailleBloc,
                        identifier(bloc, debut) ?: Pair(nomType(typeLogique), "")
                    )
                )
            }
            val suivantType = secteur[462 + 4].toInt() and 0xFF
            val suivant = entierPetit(secteur, 462 + 8)
            if (suivant <= 0L || (suivantType != 0x05 && suivantType != 0x0F)) return
            courant = base + suivant
        }
    }

    private fun lireGpt(bloc: BlocUsb, sortie: ArrayList<PartitionInfo>) {
        val entete = bloc.lire(1L, 1) ?: return
        if (String(entete, 0, 8, Charsets.US_ASCII) != "EFI PART") return

        val tampon = ByteBuffer.wrap(entete).order(ByteOrder.LITTLE_ENDIAN)
        val departEntrees = tampon.getLong(72)
        val nombreEntrees = tampon.getInt(80)
        val tailleEntree = tampon.getInt(84)
        if (nombreEntrees <= 0 || nombreEntrees > 512) return
        if (tailleEntree < 128 || tailleEntree > 1024) return

        val octets = nombreEntrees * tailleEntree
        val blocs = (octets + bloc.tailleBloc - 1) / bloc.tailleBloc
        val table = bloc.lire(departEntrees, blocs) ?: return

        for (i in 0 until nombreEntrees) {
            val base = i * tailleEntree
            if (base + tailleEntree > table.size) break
            var vide = true
            for (j in 0 until 16) {
                if (table[base + j].toInt() != 0) {
                    vide = false
                    break
                }
            }
            if (vide) continue

            val lecture = ByteBuffer.wrap(table).order(ByteOrder.LITTLE_ENDIAN)
            val premier = lecture.getLong(base + 32)
            val dernier = lecture.getLong(base + 40)
            if (dernier < premier) continue
            val nombre = dernier - premier + 1L

            val nom = nomGpt(table, base + 56)
            val rang = sortie.size + 1
            sortie.add(
                composer(
                    rang,
                    if (nom.isEmpty()) "Partition " + rang else nom,
                    premier,
                    nombre,
                    bloc.tailleBloc,
                    identifier(bloc, premier) ?: Pair("Inconnu", "")
                )
            )
        }
    }

    private fun composer(
        rang: Int,
        nom: String,
        premier: Long,
        nombre: Long,
        tailleBloc: Int,
        trouve: Pair<String, String>
    ): PartitionInfo {
        val format = trouve.first
        return PartitionInfo(
            rang = rang,
            nom = nom,
            premierBloc = premier,
            nombreBlocs = nombre,
            octets = nombre * tailleBloc.toLong(),
            format = format,
            volume = trouve.second,
            prise = classer(format)
        )
    }

    private fun classer(format: String): Prise = when (format) {
        "FAT12", "FAT16", "FAT32", "exFAT" -> Prise.NATIVE
        "NTFS", "ext2", "ext3", "ext4" -> Prise.A_VENIR
        else -> Prise.HORS_PORTEE
    }

    /** Renvoie le format détecté et l'étiquette de volume quand elle est lisible. */
    private fun identifier(bloc: BlocUsb, debut: Long): Pair<String, String>? {
        val secteur = octets(bloc, debut, 0L, 512) ?: return null

        val marque = String(secteur, 3, 8, Charsets.US_ASCII)
        if (marque == "NTFS    ") return Pair("NTFS", "")
        if (marque == "EXFAT   ") return Pair("exFAT", "")

        if (String(secteur, 82, 8, Charsets.US_ASCII) == "FAT32   ") {
            return Pair("FAT32", String(secteur, 71, 11, Charsets.US_ASCII).trim())
        }
        val ancien = String(secteur, 54, 8, Charsets.US_ASCII)
        if (ancien.startsWith("FAT")) {
            val nom = when {
                ancien.startsWith("FAT12") -> "FAT12"
                ancien.startsWith("FAT16") -> "FAT16"
                else -> "FAT16"
            }
            return Pair(nom, String(secteur, 43, 11, Charsets.US_ASCII).trim())
        }
        if (String(secteur, 32, 4, Charsets.US_ASCII) == "NXSB") return Pair("APFS", "")

        val superbloc = octets(bloc, debut, 1024L, 512)
        if (superbloc != null) {
            val lecture = ByteBuffer.wrap(superbloc).order(ByteOrder.LITTLE_ENDIAN)
            val magie = lecture.getShort(56).toInt() and 0xFFFF
            if (magie == 0xEF53) {
                val incompatibles = lecture.getInt(96)
                val compatibles = lecture.getInt(92)
                val nom = when {
                    (incompatibles and 0x40) != 0 -> "ext4"
                    (compatibles and 0x04) != 0 -> "ext3"
                    else -> "ext2"
                }
                val brut = String(superbloc, 120, 16, Charsets.UTF_8)
                return Pair(nom, brut.trim().replace("\u0000", ""))
            }
            val signature = String(superbloc, 0, 2, Charsets.US_ASCII)
            if (signature == "H+") return Pair("HFS+", "")
            if (signature == "HX") return Pair("HFSX", "")
        }

        val btrfs = octets(bloc, debut, 65600L, 16)
        if (btrfs != null && String(btrfs, 0, 8, Charsets.US_ASCII) == "_BHRfS_M") {
            return Pair("Btrfs", "")
        }

        return Pair("Inconnu", "")
    }

    /** Lit [taille] octets à [decalage] octets du début de la partition. */
    private fun octets(bloc: BlocUsb, debut: Long, decalage: Long, taille: Int): ByteArray? {
        val parBloc = bloc.tailleBloc.toLong()
        val premier = debut + decalage / parBloc
        val dedans = (decalage % parBloc).toInt()
        val nombre = ((dedans + taille).toLong() + parBloc - 1L) / parBloc
        val donnees = bloc.lire(premier, nombre.toInt()) ?: return null
        if (donnees.size < dedans + taille) return null
        return donnees.copyOfRange(dedans, dedans + taille)
    }

    private fun nomGpt(table: ByteArray, position: Int): String {
        val construction = StringBuilder()
        var i = 0
        while (i < 72) {
            val base = position + i
            if (base + 1 >= table.size) break
            val code = (table[base].toInt() and 0xFF) or ((table[base + 1].toInt() and 0xFF) shl 8)
            if (code == 0) break
            construction.append(code.toChar())
            i += 2
        }
        return construction.toString().trim()
    }

    private fun nomType(type: Int): String = when (type) {
        0x07 -> "NTFS ou exFAT"
        0x0B, 0x0C -> "FAT32"
        0x06, 0x0E -> "FAT16"
        0x01, 0x04 -> "FAT12"
        0x83 -> "Linux"
        0x82 -> "Swap Linux"
        0xAF -> "HFS+"
        else -> "Type " + Integer.toHexString(type).uppercase()
    }

    private fun entierPetit(source: ByteArray, position: Int): Long {
        return (source[position].toLong() and 0xFF) or
            ((source[position + 1].toLong() and 0xFF) shl 8) or
            ((source[position + 2].toLong() and 0xFF) shl 16) or
            ((source[position + 3].toLong() and 0xFF) shl 24)
    }
}

fun poids(octets: Long): String {
    if (octets < 1024L) return "$octets o"
    val unites = arrayOf("Ko", "Mo", "Go", "To")
    var valeur = octets.toDouble() / 1024.0
    var i = 0
    while (valeur >= 1024.0 && i < unites.size - 1) {
        valeur /= 1024.0
        i++
    }
    val format = if (valeur >= 100.0) java.text.DecimalFormat("#") else java.text.DecimalFormat("#.#")
    return format.format(valeur) + " " + unites[i]
}
