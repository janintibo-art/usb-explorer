# USB Explorer

Lecture des clés USB et disques durs externes branchés en OTG, y compris
ceux qu'Android refuse de monter.

## Pourquoi une application

Android ne monte nativement que FAT32 et exFAT. Pour tout le reste, il
propose de formater, ce qui efface le disque. Aucune application ne peut
monter un système de fichiers sans être root : l'appel système est réservé.

La voie sans root consiste à parler directement au disque. L'application
réclame l'interface de stockage de masse via l'API USB Host, envoie des
commandes SCSI encapsulées dans le protocole Bulk-Only Transport, lit les
secteurs bruts, et interprète elle-même ce qu'elle y trouve.

## État actuel : étape 1, lecture seule

- Détection des périphériques de stockage de masse et demande d'autorisation.
- INQUIRY et READ CAPACITY : fabricant, modèle, révision, taille de secteur,
  nombre de secteurs.
- Tables de partitions : MBR avec chaîne de partitions étendues, GPT avec les
  noms de volumes, et le cas sans table où le système de fichiers commence au
  premier secteur.
- Identification des formats : FAT12, FAT16, FAT32, exFAT, NTFS, ext2, ext3,
  ext4, HFS+, HFSX, APFS, Btrfs.
- Étiquettes de volume lisibles pour FAT et ext.

Rien n'est écrit sur le disque. Le code ne connaît que la commande de lecture.

## Étapes suivantes

2. FAT32 et exFAT : parcours des fichiers, puis copie vers le téléphone.
3. NTFS en lecture : table MFT, attributs, listes de fragments, index en arbre.
4. ext4 en lecture.
5. L'écriture, en dernier et avec précaution.

## Compilation

Kotlin, Jetpack Compose, Material 3. minSdk 30, compileSdk 34.
GitHub Actions compile l'artefact `usb-explorer-debug`.
