# Prototype — identité visuelle de Lekto

Ces images sont les planches du prototype de l'identité visuelle. **Elles sont jetables** :
le prototype répond à des questions, il ne part pas en production. Ce qui en sort est la
décision, et elle ira dans `docs/design.md` et dans `app/src/commonMain/kotlin/app/lekto/theme/`.

Elles vivent sur une branche jetable, `prototype/visual-identity`, hors de `main`. Le pointeur
et le verdict sont sur l'issue #168.

## Les planches

| Image | La question à laquelle elle répond |
| ----- | ---------------------------------- |
| `identite.png` | À quoi ressemblent le papier, l'encre et les trois fonds, et quelles couleurs de maîtrise tiennent sur chacun ? |
| `contraste.png` | La palette livrée échoue-t-elle, et de combien ? |
| `traitements.png` | Colorer les lettres, les souligner, ou poser un lavis derrière le mot ? |
| `selection.png` | Comment marquer le mot que le lecteur vient de toucher, dans les trois fonds ? |
| `geometrie.png` | En quoi un jeu d'icônes au trait et un jeu plein ne se ressemblent pas ? |
| `bibliotheque.png` | À quoi ressemble l'écran d'arrivée, dans les trois fonds ? |
| `bibliotheque-etats.png` | Vide, en cours de lecture, en échec — et pourquoi les deux premiers ne doivent pas se confondre ? |
| `lecteur.png` | À quoi ressemble une page en cours de lecture, chrome compris ? |
| `lecteur-moments.png` | La page nue, les zones de toucher, et le panneau du texte. |
| `recherche.png` | Le panneau du mot posé sur la page, dans les trois fonds. |
| `recherche-etats.png` | Le dictionnaire absent, la phrase traduite, la phrase sans fournisseur. |
| `desktop.png` | Le lecteur desktop et sa colonne de contexte : les chapitres quand rien n'est touché, le mot quand on touche. |

## Regénérer

Depuis la racine du dépôt :

```sh
docs/prototype/visual-identity/record.sh
```

Le script lance Roborazzi sur la cible desktop et déplace les images ici. Il faut un JVM ;
Gradle, le JDK et les dépendances sont provisionnés par le wrapper.

## Ce que le prototype contient

- `app/src/desktopTest/kotlin/app/lekto/prototype/VisualIdentityPrototype.kt` — les planches,
  avec les jetons candidats écrits en dur. Ils ne dépendent d'aucun code de production, parce
  que le thème n'existe pas encore.
- `app/src/desktopTest/kotlin/app/lekto/prototype/icons/` — les icônes générées pour la
  démonstration : le jeu Lucide (`lucide/`) et six Material Symbols (`symbols/`), converties par
  Valkyrie depuis des SVG téléchargés. Rien de tout cela n'est le jeu définitif, qui sera
  généré dans `commonMain` par son propre ticket.

## Pourquoi les images sont commitées

Une image dans `app/build/` disparaît au premier `clean`. Ces planches sont ce que l'issue cite
et ce que la revue regarde : elles sont donc committées, sur cette branche seulement.
