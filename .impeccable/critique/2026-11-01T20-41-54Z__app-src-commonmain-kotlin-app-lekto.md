---
target: the app in its entirety
total_score: 26
max_score: 40
na_heuristics: 
p0_count: 0
p1_count: 5
target_identity: "file:/home/damien/orca/workspaces/lekto/ui-impeccable/app/src/commonMain/kotlin/app/lekto"
timestamp: 2026-11-01T20-41-54Z
slug: app-src-commonmain-kotlin-app-lekto
---
# Lekto — critique de l'application

Method: design review only (detector not applicable, native platform)

## Design Health Score

| # | Heuristique | Note | Problème clé |
|---|-------------|------|--------------|
| 1 | Visibility of System Status | 3 | Progression, sync, squelettes : tout est dit. Mais rien ne dit qu'un mot vient d'être enregistré — le niveau *est* le save, et son effet est à 300 px au-dessus de la feuille. |
| 2 | Match System / Real World | 3 | « Mastery », « Unknown / Recognized / Familiar / Learned / Known » : une taxonomie de linguiste au moment où l'on lit. « Connection » est un nom, pas une action. |
| 3 | User Control and Freedom | 3 | Back stack réel, panneaux refermables, filtres réversibles. Pas d'annulation sur un niveau changé par erreur, pas de sortie douce d'une suppression. |
| 4 | Consistency and Standards | 3 | Trois langages de chip coexistent (filtre encre, filtre coloré, pastilles numérotées). Le soulignement de chaque mot coloré imite un lien. |
| 5 | Error Prevention | 3 | Confirmations, contrôles désactivés, dernier chip non désélectionnable. Mais un tap sur un chip écrit immédiatement, sans état « enregistré » ni retour. |
| 6 | Recognition Rather Than Recall | 2 | Aucune légende des couleurs dans le lecteur. Le lecteur doit mémoriser bleu = inconnu. La correspondance couleur→mot n'existe que dans l'écran Vocabulaire. |
| 7 | Flexibility and Efficiency | 2 | Flèches au clavier, colonne de contexte. Pas de tri, pas de filtre par livre, pas d'action groupée, pas de raccourci pour sauver un mot. |
| 8 | Aesthetic and Minimalist Design | 3 | Identité superbe et disciplinée. Mais une page constellée de soulignements et cinq pavés saturés dans la feuille de recherche. |
| 9 | Error Recovery | 3 | Messages simples, inline, non bloquants. Deux impasses : le dictionnaire absent et l'import échoué ne nomment pas de remède. |
| 10 | Help and Documentation | 1 | Aucune aide dans l'app, aucun onboarding, aucune légende, aucun lien vers le guide. Le premier écran n'explique pas ce qu'est Lekto. |
| **Total** | | **26/40** | **Acceptable** (65 %) |

## Design Specificity Verdict

**Verdict : très spécifique, et pourtant incomplet.** Ce n'est pas un reader interchangeable. « De l'encre sur du papier, et les seules couleurs saturées sont celles qui veulent dire quelque chose » tient à l'écran : le papier chaud, la rampe de maîtrise mesurée à 4,5:1, l'absence de couleur pour « known » comme récompense, la vignette qui remplace une couverture impossible, le titre d'écran emprunté au serif de lecture. Un produit voisin ne peut pas copier ça sans copier Lekto.

La faiblesse est ailleurs : la spécificité est dans la **peau**, pas encore dans la **boucle d'apprentissage**. Les trois écrans qui portent la promesse produit — la première page, la feuille de recherche, la fin d'un livre — sont les moins dessinés. Retiré le papier, il reste un lecteur avec une liste de mots, ce que n'importe quel clone peut être.

**Deterministic scan : not applicable (native platform).**

## Overall Impression

Un très beau cadre autour d'un produit qui n'a pas encore expliqué pourquoi il existe.

Ce qui marche : l'identité, la bibliothèque, les réglages. Ce qui ne marche pas : les dix premières minutes, l'instant où l'on sauve un mot, et la fin d'un livre. Le lecteur arrive sur une étagère vide sans phrase d'explication, importe, tombe sur une page où **tous** les mots sont bleus et soulignés, tape un mot, et se voit demander de classer ce mot sur une échelle en cinq points avant même de l'avoir enregistré.

**Non, il ne faut pas refaire l'interface de zéro.** Le monde visuel est le meilleur actif du projet ; le jeter ne corrigerait aucun des cinq problèmes ci-dessous. Il faut reconstruire trois surfaces (premier lancement, feuille de recherche, fin de livre) et une règle typographique, en gardant l'encre et le papier.

La plus grande opportunité : faire que la première page enseigne son propre code couleur, et que la fin d'un livre soit la preuve que les deux résultats ont eu lieu.

## What's Working

1. **L'identité tient, et elle est mesurée.** Les quatre niveaux passent 4,5:1 sur chaque papier — vérifiable dans le code, pas une intention. L'idée que « connu » n'a pas de couleur, donc que l'absence est la récompense, est meilleure que tout ce que fait la concurrence.
2. **La Bibliothèque est lisible d'un coup d'œil.** Titre, import, recherche, chips, deux groupes nommés, règle de progression. La décision de remplacer les couvertures par une vignette de langue règle un problème réel (une étagère à moitié illustrée lit comme cassée) au lieu de le décorer.
3. **Les réglages sont une carte, pas un formulaire.** Un index avec la valeur visible sur la ligne, et des écrans en forme imprimée derrière. Rare, et bien exécuté.
4. **La pagination est sérieuse.** Ancre en offset de caractère, mesure par chapitre, reprise sans flash : la réouverture retombe où on s'était arrêté, y compris après un changement de police.

## Priority Issues

### [P1] Les dix premières minutes n'expliquent rien

- **What** : la Bibliothèque vide dit « An EPUB or a text file, and the first word is a minute away. » et rien d'autre. Après import, on reste sur l'étagère. On ouvre le livre : tous les mots non enregistrés sont `UNKNOWN`, donc **toute la page est bleue et soulignée**, sans légende, sans phrase, sans indication qu'un tap enregistre. Le mécanisme qui distingue le produit — couleur = maîtrise — est invisible au moment exact où il faudrait le montrer.
- **Why it matters** : le persona débutant (A1–A2) ne peut pas interpréter la page. Il voit un document annoté, pas un outil. Le premier résultat du produit (finir le livre) n'est pas aidé ; le second (retenir les mots) n'est jamais annoncé. C'est aussi ce qui rend la promesse « lecture colorée par votre niveau » fausse au premier lancement : sans **Reader level** ni **Difficulty table**, chaque mot est bleu, donc la couleur ne dit rien d'utile.
- **Fix** : (a) une phrase dans l'état vide qui dit ce que fait Lekto, pas seulement ce qu'il accepte ; (b) sur la première page d'un livre, une légende discrète, refermable, qui nomme les quatre couleurs ; (c) après le premier import, une étape inline qui propose de déclarer le niveau de lecture de la langue et de télécharger la table de difficulté — sans passer par Réglages ▸ Langues. La couleur doit vouloir dire quelque chose dès la première page, ou ne pas être montrée.
- **Suggested command** : `$impeccable onboard`

### [P1] « Sauver » et « classer » sont le même geste, et l'état n'existe pas

- **What** : il n'y a pas de bouton Save — un chip de niveau *est* l'enregistrement (choix assumé, `docs/design.md` §7.3). Mais la feuille pré-sélectionne **Recognized** pour un mot qui n'est pas enregistré (`defaultLevel`), avec un anneau d'encre et le mot « Recognized » affiché à côté. Un lecteur ne peut donc pas distinguer « enregistré au niveau 1 » de « pas encore enregistré, suggestion de niveau 1 ». Taper le chip déjà cerclé ne produit aucun changement visible : ça enregistre quand même.
- **Why it matters** : premier contact avec la fonction centrale = une décision à cinq branches, posée comme un réglage, sur un mot dont on ne sait pas s'il est sauvé. C'est de la charge cognitive au pire moment (on est en train de lire), et ça produit des sauvegardes involontaires comme des mots perdus. Le principe produit « un geste, une intention » est énoncé mais pas tenu.
- **Fix** : séparer l'état de l'action. Un mot non enregistré n'a **aucun** chip cerclé ; le premier tap enregistre et le niveau devient un fait affiché (« Saved · Recognized »). Ensuite seulement, les chips servent à ajuster, avec la possibilité de revenir en arrière. Le défaut dérivé de la difficulté reste, mais il est *proposé* (« likely Recognized »), pas *affiché comme acquis*.
- **Suggested command** : `$impeccable shape` (modèle d'interaction) puis `$impeccable clarify`

### [P1] La page se bat contre la lecture : soulignements, titres colorés, mesure sans limite

- **What** : trois défauts sur la surface où l'utilisateur passe le plus de temps.
  - Chaque mot des niveaux 0–3 est souligné **dans la couleur du niveau**, à 1,5 px, pleine intensité. Sur une première page, un tiers des mots sont soulignés : la page lit comme une forêt de liens.
  - Le titre de chapitre est un bloc de texte comme un autre, donc il reçoit la couleur et le soulignement de maîtrise. Dans la capture, « The Lantern Keeper » est bleu et souligné : la structure du livre ressemble à du vocabulaire.
  - À partir de 1024 dp de large, le texte occupe ~976 dp à 18 sp, soit **~105 caractères par ligne**. Le plafond de 1100 dp ne protège rien : même avec la colonne de contexte, il reste 732 dp (~78 caractères).
- **Why it matters** : le premier résultat du produit est « finir le livre sans s'arrêter ». Un texte qui ressemble à un lien et des lignes de 105 caractères fatiguent exactement le lecteur qu'on veut retenir. Et un titre coloré brouille la hiérarchie : on ne sait plus ce qui est structure et ce qui est mot à apprendre.
- **Fix** : (a) les titres restent à l'encre, en graisse de titre, jamais colorés ni soulignés — la maîtrise ne s'applique qu'au texte courant ; (b) garder un indicateur non-coloré (obligation d'accessibilité) mais le rendre discret : pointillé 1 px à 60 % de la couleur, ou trait plus fin, pour qu'il ne se lise pas comme un lien ; (c) plafonner la mesure à ~34 em (62–70 caractères), centrer la colonne de texte, et laisser les marges absorber la largeur au lieu des lignes.
- **Suggested command** : `$impeccable typeset`

### [P1] La feuille de recherche est trop petite pour son travail, et le dictionnaire absent est une impasse

- **What** : la feuille est plafonnée à 45 % de la hauteur (288 dp sur 640). Déduire l'en-tête, le sélecteur et les références laisse ~110 dp de corps : environ une définition et demie. Pour un mot à plusieurs sens — le cas normal — le lecteur doit faire défiler dans une fenêtre de quatre lignes, alors que c'est *le* geste du produit. Quand le pack n'est pas installé, le panneau affiche « The offline dictionary isn't installed yet. » sans aucun moyen de l'installer : le téléchargement vit dans Réglages ▸ Langues.
- **Why it matters** : le cœur du produit se joue ici. Une fenêtre trop courte transforme la consultation en fouille ; une impasse nommée sans porte transforme un manque en bug. Un débutant conclut que le dictionnaire est cassé, pas qu'il est à installer.
- **Fix** : dimensionner la feuille sur son contenu jusqu'à ~60 % (le plafond existait pour laisser la page visible, pas pour couper le sens), et offrir dans le message une action discrète `Install the offline dictionary` qui mène au bon endroit. Même chose pour l'échec d'import : nommer le fichier et la cause.
- **Suggested command** : `$impeccable layout`

### [P1] Aucun lien entre un livre et les mots qu'il a donnés

- **What** : une ligne de Bibliothèque dit `EPUB · 46 %`, jamais ce que le livre a produit. Le Vocabulaire est une liste plate : pas de livre d'origine, pas de tri, pas de filtre par livre, aucune relecture. `VocabularyEntry` ne porte même pas d'identifiant de livre, donc le lien n'est pas seulement absent de l'écran, il n'existe pas dans les données. Et la fin d'un livre — le pic émotionnel, l'endroit où le produit devrait prouver qu'il a marché — est un écran de texte avec deux boutons.
- **Why it matters** : le second résultat du produit est « retenir les mots rencontrés ». Aujourd'hui, Lekto les stocke. Rien ne dit au lecteur qu'il progresse, et rien ne l'aide à revoir. Deux résultats co-égaux sont annoncés ; un seul est instrumenté.
- **Fix** : au moment où le livre se termine, montrer ce que la session a produit (« 18 words from this book · 6 learned ») et offrir le chemin vers ces mots. Dans le Vocabulaire, afficher le livre d'origine et permettre de filtrer par livre. Cela demande un champ sur l'enregistrement (et donc une décision de format, ADR) — c'est le seul point de cette liste qui touche le domaine.
- **Suggested command** : `$impeccable shape`

## Persona Red Flags

**Camille — débutante A1–A2, lit dans le bus, sessions de dix minutes.**
Étagère vide sans explication → importe un EPUB → reste sur l'étagère → ouvre le livre → **toute la page est bleue et soulignée**, sans légende : elle croit à des liens ou à un correcteur orthographique. Elle tape « harbour » : cinq pavés numérotés, « Recognized » déjà cerclé. Elle tape le 1 : rien ne semble se passer (l'effet est 300 px plus haut, caché par la feuille). Elle ferme. Plus tard, le Vocabulaire dit « 1 word » sans dire d'où il vient.

**Jordan — première fois avec ce type d'app.**
Aucune aide dans l'application, aucun lien vers le guide, aucune légende. « Mastery », « Unknown / Recognized / Familiar / Learned / Known » : cinq termes à inférer, en anglais, dans une feuille de 288 dp. Le dictionnaire absent est annoncé sans remède. L'échec d'import dit « The import failed. » sans nommer le fichier. Il abandonnera avant d'avoir compris ce que fait l'app.

**Sam — lecteur d'écran, navigation clavier.**
Les fondations sont bonnes : 4,5:1 mesuré, soulignement non-coloré, cibles 48 dp, actions d'accessibilité pour tourner les pages, progression sémantique. Deux trous : (a) l'état « enregistré / non enregistré » n'existe pas, donc un lecteur d'écran annonce `selected` sur un mot qui n'est pas sauvé ; (b) le titre de chapitre coloré est annoncé comme du texte ordinaire, donc la structure du livre disparaît de l'arbre. Sur desktop Linux, aucune synthèse vocale — c'est documenté, mais l'app ne redirige pas vers Android.

## Minor Observations

- La colonne de contexte réserve 320 dp (29 % d'une fenêtre 1100) pour afficher « This book has no chapter list. » sur tout livre à chapitre unique. Ne la montrer que s'il y a une liste ou un mot ouvert.
- La barre de progression d'une ligne de Bibliothèque court jusqu'au bord du contenu alors que le texte s'arrête bien avant : le trait paraît orphelin sous le titre.
- Les filtres du Vocabulaire s'enroulent sur trois rangées au pointeur et repoussent la première ligne sous la ligne de flottaison (visible en capture). Un tri, ou un menu, serait plus calme.
- Les lignes de Réglages n'ont pas la même hauteur quand une valeur manque (Attribution) : les filets tombent sur un rythme irrégulier.
- `Delete « {0} »?` : guillemets français avec espaces dans un texte anglais. Utiliser “ ”.
- « Connection » est un nom ; l'action est « Connect » ou « Test connection ».
- L'état vide promet « the first word is a minute away », mais le dictionnaire et la table de difficulté sont deux téléchargements séparés : la promesse est optimiste.
- Le guide annonce l'import PDF ; `BookFormat` ne connaît qu'EPUB et TXT, et un `.pdf` échoue. Corriger le guide ou le format — c'est une promesse non tenue, pas un détail.
- La chrome du lecteur ne se cache jamais : 112 dp sur 640 (17,5 %) en permanence. Le choix est assumé, mais c'est un sixième de la page.
- « 0 % » sur la première page se lit comme une mauvaise note. « 1 % » ou une position de chapitre serait plus juste.
- Une ligne de Vocabulaire fait quatre lignes de texte : trois lignes visibles à l'écran. Réduire la phrase de contexte à une ligne avec ellipse.

## Questions to Consider

- Et si la toute première page portait une phrase qui explique ses couleurs, plutôt que de laisser le lecteur deviner ?
- Et si sauver un mot était un geste unique sans taxonomie, le niveau étant quelque chose qu'on ajuste plus tard, dans une liste ?
- Le lecteur a-t-il besoin de 112 dp de chrome permanents sur un téléphone ?
- À quoi ressemblerait la fin d'un livre si elle était la récompense des **deux** résultats, plutôt que deux boutons ?
- Si la fenêtre fait 1600 dp de large, pourquoi la page fait-elle 1550 dp de texte ?
