package app.lekto.prototype

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lekto.prototype.icons.lucide.ALargeSmall
import app.lekto.prototype.icons.lucide.ArrowLeft
import app.lekto.prototype.icons.lucide.BookOpen
import app.lekto.prototype.icons.lucide.Bookmark
import app.lekto.prototype.icons.lucide.ChevronLeft
import app.lekto.prototype.icons.lucide.ChevronRight
import app.lekto.prototype.icons.lucide.Library
import app.lekto.prototype.icons.lucide.LucideDemo
import app.lekto.prototype.icons.lucide.Search
import app.lekto.prototype.icons.lucide.Settings
import app.lekto.prototype.icons.lucide.Trash
import app.lekto.prototype.icons.lucide.Volume2
import app.lekto.prototype.icons.lucide.X
import app.lekto.prototype.icons.symbols.ArrowBack
import app.lekto.prototype.icons.symbols.Book2
import app.lekto.prototype.icons.symbols.Close
import app.lekto.prototype.icons.symbols.Search as SearchSymbols
import app.lekto.prototype.icons.symbols.Settings as SettingsSymbols
import app.lekto.prototype.icons.symbols.SymbolsDemo
import app.lekto.prototype.icons.symbols.VolumeUp
import io.github.takahirom.roborazzi.captureRoboImage
import kotlin.test.Test

/*
 * PROTOTYPE — identité visuelle de Lekto (issue #168, volet visuel).
 *
 * Ce fichier est jetable. Il répond à une seule question : « à quoi ressemble
 * un mot coloré par niveau de maîtrise sur du papier, de l'encre et du sépia,
 * et quelles couleurs tiennent le seuil WCAG AA ? »
 *
 * Il ne dépend d'aucun code de production, parce que le thème n'existe pas
 * encore : les jetons ci-dessous sont la proposition à valider, pas la
 * décision. Rien ici ne part en production ; les valeurs retenues iront dans
 * `app/src/commonMain/kotlin/app/lekto/theme/`.
 *
 * Le rendre : ./gradlew :app:recordRoborazziDesktop --tests "*VisualIdentityPrototype*"
 * Les images sortent dans app/src/desktopTest/goldens/desktop/prototype/.
 */

/** Un jeu de jetons candidat pour un thème. */
private data class Theme(
    val name: String,
    val paper: Color,
    val surface: Color,
    val outline: Color,
    val ink: Color,
    val muted: Color,
    val unknown: Color,
    val recognized: Color,
    val familiar: Color,
    val learned: Color,
    val ratios: String,
    /**
     * De combien la teinte d'une langue s'éclaircit sur ce fond : sur du papier
     * sombre, une teinte mate disparaît. 0 = telle quelle.
     */
    val eclat: Float = 0f,
)

/**
 * « Encre sur papier » : le fond est du papier chaud, le texte de l'encre, et
 * le `primary` est l'encre elle-même — un bouton plein est un rectangle
 * d'encre. Aucune couleur saturée n'est introduite par la coquille : les seules
 * couleurs de l'écran sont celles qui portent du sens, les niveaux de maîtrise.
 */
private val Clair = Theme(
    name = "Clair — papier",
    paper = Color(0xFFFAF7F2),
    surface = Color(0xFFF3EEE6),
    outline = Color(0xFFD6CCBC),
    ink = Color(0xFF1C1A17),
    muted = Color(0xFF6B6357),
    unknown = Color(0xFF1D4ED8),
    recognized = Color(0xFFA16207),
    familiar = Color(0xFFC2410C),
    learned = Color(0xFF15803D),
    ratios = "16,2:1 le texte · 6,3 · 4,6 · 4,8 · 4,7",
)

private val Sombre = Theme(
    name = "Sombre — nuit chaude",
    paper = Color(0xFF15130F),
    surface = Color(0xFF1F1C17),
    outline = Color(0xFF3A342B),
    ink = Color(0xFFE8E2D8),
    muted = Color(0xFF9A9083),
    unknown = Color(0xFF8AB4F8),
    recognized = Color(0xFFF2C14E),
    familiar = Color(0xFFEE9A5F),
    learned = Color(0xFF7BD88F),
    ratios = "14,4:1 le texte · 8,8 · 11,1 · 8,3 · 10,7",
    eclat = 0.35f,
)

private val Sepia = Theme(
    name = "Sépia — papier chiffon",
    paper = Color(0xFFF6ECD8),
    surface = Color(0xFFEFE3C9),
    outline = Color(0xFFD8C7A6),
    ink = Color(0xFF3B2F1E),
    muted = Color(0xFF6B5B41),
    unknown = Color(0xFF1D4ED8),
    recognized = Color(0xFF92400E),
    familiar = Color(0xFF9A3412),
    learned = Color(0xFF166534),
    ratios = "11,1:1 le texte · 5,7 · 6,1 · 6,2 · 6,1",
)

/** La palette livrée aujourd'hui, pour mesurer l'écart. */
private val Aujourdhui = Theme(
    name = "Aujourd'hui",
    paper = Color.White,
    surface = Color(0xFFF4F4F5),
    outline = Color(0xFFD4D4D8),
    ink = Color(0xFF1A1A1A),
    muted = Color(0xFF71717A),
    unknown = Color(0xFF2563EB),
    recognized = Color(0xFFFACC15),
    familiar = Color(0xFFFB923C),
    learned = Color(0xFF22C55E),
    ratios = "5,2:1 le texte · ÉCHEC 1,5 · ÉCHEC 2,3 · ÉCHEC 2,3",
)

private const val PHARE = "Phare"

/** Les quatre niveaux d'une rampe, quelle qu'elle soit. */
private data class Rampe(
    val unknown: Color,
    val recognized: Color,
    val familiar: Color,
    val learned: Color,
)

private enum class Niveau { UNKNOWN, RECOGNIZED, FAMILIAR, LEARNED }

private data class Mot(val texte: String, val niveau: Niveau?)

/** La phrase du livre, étiquetée par niveau. C'est elle qu'on colore. */
private val PHRASE = listOf(
    Mot("The", null),
    Mot("fat", Niveau.LEARNED),
    Mot("man", null),
    Mot("told", Niveau.UNKNOWN),
    Mot("the", null),
    Mot("children", null),
    Mot("that", null),
    Mot("tomorrow", Niveau.RECOGNIZED),
    Mot("would", null),
    Mot("be", null),
    Mot("another", Niveau.FAMILIAR),
    Mot("day,", null),
    Mot("and", null),
    Mot("the", null),
    Mot("fir", null),
    Mot("tree", null),
    Mot("stood", Niveau.UNKNOWN),
    Mot("silent.", null),
)

/** La rampe de [theme] telle qu'elle colore les lettres. */
private fun Theme.rampe(): Rampe = Rampe(unknown, recognized, familiar, learned)

/** La rampe livrée aujourd'hui, telles que les lettres sont peintes. */
private val RampAujourdhui: Rampe = Rampe(
    unknown = Color(0xFF2563EB),
    recognized = Color(0xFFFACC15),
    familiar = Color(0xFFFB923C),
    learned = Color(0xFF22C55E),
)

/**
 * Les teintes vives, pour la variante « lavis de fond » : elles redeviennent
 * utilisables parce qu'elles passent derrière le mot au lieu de le peindre, et
 * c'est l'encre qui porte le contraste. 35 % d'opacité sur le papier.
 */
private val VifClair = Rampe(
    unknown = Color(0x592563EB),
    recognized = Color(0x8CFACC15),
    familiar = Color(0x73FB923C),
    learned = Color(0x6622C55E),
)

private fun Rampe.couleur(niveau: Niveau): Color = when (niveau) {
    Niveau.UNKNOWN -> unknown
    Niveau.RECOGNIZED -> recognized
    Niveau.FAMILIAR -> familiar
    Niveau.LEARNED -> learned
}

/**
 * La phrase du livre, rendue selon un traitement : la couleur des lettres, un
 * lavis derrière elles, ou rien — avec ou sans soulignement. Un seul endroit,
 * parce que c'est la seule chose qui compte : une page en cours de lecture.
 *
 * [lavis] non nul passe derrière le mot, et c'est alors [theme.ink] qui colore
 * les lettres. [souligne] ajoute le repère non-couleur. [selection] nomme les
 * mots que le lecteur vient de toucher — un seul, ou toute une phrase.
 */
@Composable
private fun Phrase(
    theme: Theme,
    couleur: Rampe,
    lavis: Rampe? = null,
    souligne: Boolean = false,
    selection: List<String> = emptyList(),
    mots: List<Mot> = PHRASE,
) {
    val decoration = if (souligne) TextDecoration.Underline else TextDecoration.None
    Text(
        text = buildAnnotatedString {
            mots.forEachIndexed { index, mot ->
                val style = mot.niveau?.let { niveau ->
                    SpanStyle(
                        color = if (lavis == null) couleur.couleur(niveau) else theme.ink,
                        background = lavis?.couleur(niveau) ?: Color.Unspecified,
                        textDecoration = decoration,
                    )
                }
                val fond = if (mot.texte in selection) {
                    SpanStyle(background = theme.ink.copy(alpha = 0.14f))
                } else {
                    null
                }
                when {
                    fond != null && style != null -> withStyle(style.merge(fond)) { append(mot.texte) }
                    fond != null -> withStyle(fond) { append(mot.texte) }
                    style != null -> withStyle(style) { append(mot.texte) }
                    else -> append(mot.texte)
                }
                if (index != mots.lastIndex) append(" ")
            }
        },
        style = TextStyle(
            fontFamily = FontFamily.Serif,
            fontSize = 18.sp,
            lineHeight = 30.6.sp,
            color = theme.ink,
        ),
    )
}

/** La page telle qu'elle est livrée : la couleur des lettres, pas de lavis. */
@Composable
private fun Page(theme: Theme) {
    Phrase(theme = theme, couleur = theme.rampe())
}

/** Le papier : le fond de tout l'écran, et rien d'autre. */
@Composable
private fun Colonne(theme: Theme, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .width(432.dp)
            .fillMaxHeight()
            .background(theme.paper)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) { content() }
}

/** Les jetons d'un thème, sur son propre papier. */
@Composable
private fun Feuille(theme: Theme) {
    Colonne(theme) {
        Text(
            text = theme.name,
            style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = theme.ink,
            ),
        )
        Text(
            text = "Le papier, l'encre, et une seule couleur saturée : le sens.",
            style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = theme.muted,
            ),
        )

        // Les deux actions : pleine (l'encre) et discrète (l'encre aussi, sans fond).
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .background(theme.ink, RoundedCornerShape(8.dp))
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Text(
                    text = "Importer",
                    style = TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = theme.paper,
                    ),
                )
            }
            Text(
                text = "Réglages",
                style = TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = theme.ink,
                ),
            )
        }

        // Une surface : pas d'ombre, une bordure et un fond légèrement plus sombre.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(theme.surface, RoundedCornerShape(12.dp))
                .border(1.dp, theme.outline, RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Children's Literature",
                style = TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = theme.ink,
                ),
            )
            Text(
                text = "EPUB · 357 pages · page 99",
                style = TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 12.sp,
                    color = theme.muted,
                ),
            )
        }

        Page(theme)

        Text(
            text = "Panneau de recherche",
            style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = theme.ink,
            ),
        )
        PageInPanel(theme)
    }
}

/** La page telle qu'elle apparaît dans le panneau de recherche, avec ses pastilles. */
@Composable
private fun PageInPanel(theme: Theme) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.surface, RoundedCornerShape(12.dp))
            .border(1.dp, theme.outline, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Page(theme)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pastille("Inconnu", theme.unknown)
            Pastille("Reconnu", theme.recognized)
            Pastille("Familier", theme.familiar)
            Pastille("Appris", theme.learned)
        }
        Text(
            text = "Contraste mesuré — ${theme.ratios}",
            style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 10.sp,
                color = theme.muted,
            ),
        )
    }
}

/** Une pastille de niveau : le fond coloré, et l'encre adaptée. */
@Composable
private fun Pastille(label: String, fond: Color) {
    Box(
        modifier = Modifier
            .background(fond, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = if (fond.luminance() > 0.4f) Color(0xFF1A1A1A) else Color.White,
            ),
        )
    }
}

/** Le titre de la planche. */
@Composable
private fun TitrePlanche(titre: String, sousTitre: String, papier: Color, encre: Color) {
    Column(modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 8.dp)) {
        Text(
            text = titre,
            style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = encre,
            ),
        )
        Text(
            text = sousTitre,
            style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 13.sp,
                color = encre.copy(alpha = 0.65f),
            ),
        )
    }
}

/**
 * Les quelques icônes que l'identité demande, dessinées en vectoriel : un trait
 * de 2 unités sur une grille de 24, extrémités arrondies. Ici, le jeu de
 * navigation — c'est lui qui décidera si la coquille tient debout.
 */
@Composable
private fun Icone(nom: String, couleur: Color) {
    Text(
        text = nom,
        style = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontSize = 10.sp,
            color = couleur,
        ),
    )
}

private const val LARGEUR = 1440
private const val HAUTEUR = 1010

@OptIn(ExperimentalTestApi::class)
class VisualIdentityPrototype {

    /**
     * Planche 1 — les trois fonds côte à côte, chacun avec ses propres jetons.
     * Répond à « c'est quoi, l'encre sur papier ? »
     */
    @Test
    fun identite() = runDesktopComposeUiTest(width = LARGEUR, height = HAUTEUR) {
        setContent {
            Column(
                modifier = Modifier
                    .size(LARGEUR.dp, HAUTEUR.dp)
                    .background(Color(0xFF101010)),
            ) {
                TitrePlanche(
                    titre = "Lekto — trois fonds, une identité",
                    sousTitre = "Le même écran sur du papier chaud, une nuit chaude et du sépia. " +
                        "Les seules couleurs saturées sont celles qui portent du sens.",
                    papier = Color(0xFF101010),
                    encre = Color(0xFFF5F5F5),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Feuille(Clair)
                    Feuille(Sombre)
                    Feuille(Sepia)
                }
            }
        }
        onRoot().captureRoboImage("prototype/identite.png")
    }

    /**
     * Planche 2 — la faute d'accessibilité, et le rattrapage. La même phrase,
     * à gauche avec la palette livrée, à droite avec la rampe proposée.
     */
    @Test
    fun contraste() = runDesktopComposeUiTest(width = LARGEUR, height = 660) {
        setContent {
            Column(
                modifier = Modifier
                    .size(LARGEUR.dp, 660.dp)
                    .background(Color(0xFF101010)),
            ) {
                TitrePlanche(
                    titre = "La palette livrée échoue, et le rattrapage est mesurable",
                    sousTitre = "Un mot coloré est du texte de 18 sp : le seuil WCAG AA est 4,5:1. " +
                        "Trois niveaux sur quatre sont sous le seuil — le jaune à 1,5:1 est presque invisible.",
                    papier = Color(0xFF101010),
                    encre = Color(0xFFF5F5F5),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Colonne(Aujourdhui) {
                        Text(
                            text = "Livré aujourd'hui",
                            style = TextStyle(
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Aujourdhui.ink,
                            ),
                        )
                        Page(Aujourdhui)
                        PageInPanel(Aujourdhui)
                    }
                    Colonne(Clair) {
                        Text(
                            text = "Proposé — la même phrase, lisible",
                            style = TextStyle(
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Clair.ink,
                            ),
                        )
                        Page(Clair)
                        PageInPanel(Clair)
                    }
                    Colonne(Sombre) {
                        Text(
                            text = "Le sombre est un autre jeu, pas un éclaircissement",
                            style = TextStyle(
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Sombre.ink,
                            ),
                        )
                        Page(Sombre)
                        PageInPanel(Sombre)
                    }
                }
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icone("bibliothèque", Color(0xFF9A9083))
                    Icone("vocabulaire", Color(0xFF9A9083))
                    Icone("langues", Color(0xFF9A9083))
                    Icone("réglages", Color(0xFF9A9083))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Icônes : géométrie Lucide, 24×24, trait 2, extrémités rondes.",
                        style = TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 12.sp,
                            color = Color(0xFF9A9083),
                        ),
                    )
                }
            }
        }
        onRoot().captureRoboImage("prototype/contraste.png")
    }

    /**
     * Planche 3 — le même mot marqué de quatre façons, sur le même papier :
     * les lettres colorées (livré, puis proposé), avec et sans soulignement, et
     * la variante du lavis de fond que Damien voulait voir.
     */
    @Test
    fun traitements() = runDesktopComposeUiTest(width = LARGEUR, height = 660) {
        setContent {
            Column(Modifier.size(LARGEUR.dp, 660.dp).background(Clair.paper)) {
                TitrePlanche(
                    titre = "Quatre façons de marquer un mot, sur le même papier",
                    sousTitre = "Une seule question : qu'est-ce qui distingue « à travailler » de " +
                        "« su » sans rendre la phrase illisible ?",
                    papier = Clair.paper,
                    encre = Clair.ink,
                )
                Traitement(
                    titre = "Livré aujourd'hui — les lettres, soulignées",
                    note = "1,4 · 2,3 · 2,3:1 — trois niveaux sous le seuil AA",
                    fond = Color.White,
                ) {
                    Phrase(Clair, RampAujourdhui, souligne = true)
                }
                Traitement(
                    titre = "Proposé — les lettres, sans soulignement",
                    note = "4,7 à 6,3:1 — mais rien ne distingue le mot sans la couleur",
                ) {
                    Phrase(Clair, Clair.rampe())
                }
                Traitement(
                    titre = "Proposé — les lettres, soulignées",
                    note = "4,7 à 6,3:1, et le soulignement porte le sens pour qui ne lit pas la couleur",
                ) {
                    Phrase(Clair, Clair.rampe(), souligne = true)
                }
                Traitement(
                    titre = "Variante — un lavis derrière le mot",
                    note = "les teintes vives redeviennent utilisables : c'est l'encre qui porte le contraste",
                ) {
                    Phrase(Clair, VifClair, lavis = VifClair)
                }
                Traitement(
                    titre = "Variante — le lavis, souligné",
                    note = "le repère non-couleur s'ajoute au lavis",
                ) {
                    Phrase(Clair, VifClair, lavis = VifClair, souligne = true)
                }
            }
        }
        onRoot().captureRoboImage("prototype/traitements.png")
    }

    /**
     * Planche 4 — le mot que le lecteur vient de toucher, dans les trois fonds.
     * Le lavis est de l'encre translucide, donc aucune teinte nouvelle n'entre
     * dans l'écran, et la couleur du niveau continue de lire au travers.
     */
    @Test
    fun selection() = runDesktopComposeUiTest(width = LARGEUR, height = 660) {
        setContent {
            Column(Modifier.size(LARGEUR.dp, 660.dp).background(Color(0xFF101010))) {
                TitrePlanche(
                    titre = "Le mot touché : de l'encre translucide, pas une couleur de plus",
                    sousTitre = "Le lavis du mot sélectionné est de l'encre à 14 % ou 24 %, jamais " +
                        "une teinte : l'accent de l'app reste l'encre.",
                    papier = Color(0xFF101010),
                    encre = Color(0xFFF5F5F5),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Fond(Clair, "told", 0.14f)
                    Fond(Sombre, "told", 0.14f)
                    Fond(Sepia, "another", 0.24f)
                }
            }
        }
        onRoot().captureRoboImage("prototype/selection.png")
    }

    /**
     * Planche 5 — « la géométrie, c'est quoi ». Le même concept dessiné par un
     * jeu au trait et par un jeu plein, à la taille réelle d'une barre de
     * navigation, puis grossi, puis mélangé dans la même rangée.
     */
    @Test
    fun geometrie() = runDesktopComposeUiTest(width = LARGEUR, height = 700) {
        setContent {
            Column(Modifier.size(LARGEUR.dp, 700.dp).background(Clair.paper)) {
                TitrePlanche(
                    titre = "La géométrie, c'est les règles de dessin que tout un jeu partage",
                    sousTitre = "Grille de tracé, épaisseur du trait, forme des extrémités. " +
                        "Quand tout un jeu les partage, il lit comme une famille — et un seul icône " +
                        "étranger saute aux yeux.",
                    papier = Clair.paper,
                    encre = Clair.ink,
                )
                GeometrieLigne(
                    titre = "Lucide — le jeu choisi",
                    note = "grille de 24, trait de 2, extrémités et jonctions arrondies. " +
                        "Le dessin est un contour : l'intérieur reste du papier.",
                    icones = listOf(
                        LucideDemo.Library,
                        LucideDemo.Search,
                        LucideDemo.Volume2,
                        LucideDemo.Settings,
                        LucideDemo.Trash,
                        LucideDemo.X,
                    ),
                )
                GeometrieLigne(
                    titre = "Material Symbols — l'autre école",
                    note = "grille de 960, formes pleines. Plus gras, plus massif, " +
                        "et aucun trait à partager.",
                    icones = listOf(
                        SymbolsDemo.Book2,
                        SymbolsDemo.SearchSymbols,
                        SymbolsDemo.VolumeUp,
                        SymbolsDemo.SettingsSymbols,
                        SymbolsDemo.Close,
                        SymbolsDemo.ArrowBack,
                    ),
                )
                GeometrieLigne(
                    titre = "Le mélange, dans une même rangée",
                    note = "première, troisième et cinquième de Lucide ; les autres de Material " +
                        "Symbols. Rien n'est cassé, mais plus rien ne se ressemble.",
                    icones = listOf(
                        LucideDemo.Library,
                        SymbolsDemo.SearchSymbols,
                        LucideDemo.Volume2,
                        SymbolsDemo.SettingsSymbols,
                        LucideDemo.X,
                        SymbolsDemo.Close,
                    ),
                )
                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
                    Text(
                        text = "Le même concept, agrandi : le trait contre la pleine forme",
                        style = TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Clair.ink,
                        ),
                    )
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(32.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = LucideDemo.Settings,
                            contentDescription = null,
                            tint = Clair.ink,
                            modifier = Modifier.size(96.dp),
                        )
                        Icon(
                            imageVector = SymbolsDemo.SettingsSymbols,
                            contentDescription = null,
                            tint = Clair.ink,
                            modifier = Modifier.size(96.dp),
                        )
                        Text(
                            text = "Même taille de cadre, même concept, deux dessins qui " +
                                "n'appartiennent pas à la même famille.",
                            style = TextStyle(
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 12.sp,
                                color = Clair.muted,
                            ),
                        )
                    }
                }
            }
        }
        onRoot().captureRoboImage("prototype/geometrie.png")
    }

    /**
     * Planche 6 — la Bibliothèque, l'écran d'arrivée, dans les trois fonds.
     * Le champ de recherche est là (issue #173), la progression aussi (#172).
     */
    @Test
    fun bibliotheque() = runDesktopComposeUiTest(width = LARGEUR, height = 980) {
        setContent {
            Column(Modifier.size(LARGEUR.dp, 980.dp).background(Color(0xFF101010))) {
                TitrePlanche(
                    titre = "La Bibliothèque — l'écran d'arrivée",
                    sousTitre = "Deux signaux, pas plus : la langue, par la couleur de la " +
                        "vignette, et la progression, par la barre sous le titre.",
                    papier = Color(0xFF101010),
                    encre = Color(0xFFF5F5F5),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    EcranBibliotheque(Clair, EtatEcran.PLEIN)
                    EcranBibliotheque(Sombre, EtatEcran.PLEIN)
                    EcranBibliotheque(Sepia, EtatEcran.PLEIN)
                }
            }
        }
        onRoot().captureRoboImage("prototype/bibliotheque.png")
    }

    /**
     * Planche 7 — les trois états que la Bibliothèque n'a pas aujourd'hui : vide,
     * en cours de lecture (celui qui manque, et qui fait clignoter « aucun
     * livre »), et en échec.
     */
    @Test
    fun etats() = runDesktopComposeUiTest(width = LARGEUR, height = 980) {
        setContent {
            Column(Modifier.size(LARGEUR.dp, 980.dp).background(Color(0xFF101010))) {
                TitrePlanche(
                    titre = "La Bibliothèque — les trois états",
                    sousTitre = "Aujourd'hui un coffre vide et un coffre en cours de lecture " +
                        "s'affichent tous les deux « aucun livre ».",
                    papier = Color(0xFF101010),
                    encre = Color(0xFFF5F5F5),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    EcranBibliotheque(Clair, EtatEcran.VIDE)
                    EcranBibliotheque(Clair, EtatEcran.CHARGEMENT)
                    EcranBibliotheque(Clair, EtatEcran.ERREUR)
                }
            }
        }
        onRoot().captureRoboImage("prototype/bibliotheque-etats.png")
    }

    /**
     * Planche 8 — le lecteur, dans les trois fonds. Un mot est touché, donc lavé
     * à l'encre : c'est l'état où le panneau de recherche va s'ouvrir.
     */
    @Test
    fun lecteur() = runDesktopComposeUiTest(width = LARGEUR, height = 980) {
        setContent {
            Column(Modifier.size(LARGEUR.dp, 980.dp).background(Color(0xFF101010))) {
                TitrePlanche(
                    titre = "Le lecteur — la page, et rien de plus",
                    sousTitre = "Le chrome tient en deux bandes de 48 et 64 dp. Entre les deux, " +
                        "seulement le texte — jusqu'à ce qu'un doigt le touche.",
                    papier = Color(0xFF101010),
                    encre = Color(0xFFF5F5F5),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    EcranLecteur(Clair, chrome = true, selection = listOf("stood"))
                    EcranLecteur(Sombre, chrome = true, selection = listOf("told"))
                    EcranLecteur(Sepia, chrome = true, selection = listOf("another"))
                }
            }
        }
        onRoot().captureRoboImage("prototype/lecteur.png")
    }

    /**
     * Planche 9 — les trois moments du lecteur : la page seule, les zones de
     * toucher qui la commandent, et le panneau Aa qui change le texte.
     */
    @Test
    fun lecteurMoments() = runDesktopComposeUiTest(width = LARGEUR, height = 980) {
        setContent {
            Column(Modifier.size(LARGEUR.dp, 980.dp).background(Color(0xFF101010))) {
                TitrePlanche(
                    titre = "Le lecteur — la page, les zones, le panneau du texte",
                    sousTitre = "Toucher le tiers central fait disparaître le chrome et revenir " +
                        "la page nue ; le panneau Aa change la face, la taille et les marges, " +
                        "et la page se remet en page sous les yeux du lecteur.",
                    papier = Color(0xFF101010),
                    encre = Color(0xFFF5F5F5),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    EcranLecteur(Clair, chrome = false)
                    EcranLecteur(Clair, chrome = false, zones = true)
                    EcranLecteur(Clair, chrome = true, panneau = true, selection = listOf("stood"))
                }
            }
        }
        onRoot().captureRoboImage("prototype/lecteur-moments.png")
    }

    /**
     * Planche 10 — le panneau, dans les trois fonds. Un mot touché, sa définition,
     * son niveau : c'est l'interaction qui définit le produit.
     */
    @Test
    fun recherche() = runDesktopComposeUiTest(width = LARGEUR, height = 980) {
        setContent {
            Column(Modifier.size(LARGEUR.dp, 980.dp).background(Color(0xFF101010))) {
                TitrePlanche(
                    titre = "Le panneau du mot — l'interaction signature",
                    sousTitre = "La page reste visible au-dessus, le mot touché reste lavé, et " +
                        "le panneau ne prend jamais plus de la moitié de l'écran.",
                    papier = Color(0xFF101010),
                    encre = Color(0xFFF5F5F5),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    EcranRecherche(Clair, EtatRecherche.MOT)
                    EcranRecherche(Sombre, EtatRecherche.MOT)
                    EcranRecherche(Sepia, EtatRecherche.MOT)
                }
            }
        }
        onRoot().captureRoboImage("prototype/recherche.png")
    }

    /**
     * Planche 11 — les trois états que le panneau doit savoir montrer :
     * le dictionnaire absent, une phrase traduite par l'IA, et une phrase sans
     * aucun fournisseur configuré.
     */
    @Test
    fun rechercheEtats() = runDesktopComposeUiTest(width = LARGEUR, height = 980) {
        setContent {
            Column(Modifier.size(LARGEUR.dp, 980.dp).background(Color(0xFF101010))) {
                TitrePlanche(
                    titre = "Le panneau — quand il n'a pas ce qu'on lui demande",
                    sousTitre = "Un dictionnaire absent, une phrase traduite, une phrase sans " +
                        "fournisseur : trois messages dans le panneau, jamais une alerte.",
                    papier = Color(0xFF101010),
                    encre = Color(0xFFF5F5F5),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    EcranRecherche(Clair, EtatRecherche.HORS_LIGNE)
                    EcranRecherche(Clair, EtatRecherche.PHRASE_IA)
                    EcranRecherche(Clair, EtatRecherche.PHRASE_SANS_IA)
                }
            }
        }
        onRoot().captureRoboImage("prototype/recherche-etats.png")
    }

    /**
     * Planche 12 — le lecteur desktop, et ce que fait la colonne de droite. Elle
     * est réservée (rien ne bouge quand elle s'ouvre), et elle montre les
     * chapitres quand rien n'est sélectionné : pas de bande vide, et les
     * chapitres y trouvent leur place.
     */
    @Test
    fun desktop() = runDesktopComposeUiTest(width = 1280, height = 1680) {
        setContent {
            Column(Modifier.size(1280.dp, 1680.dp).background(Color(0xFF101010))) {
                TitrePlanche(
                    titre = "Le lecteur desktop — la colonne de contexte",
                    sousTitre = "Elle est toujours là, donc la page ne bouge jamais quand un " +
                        "mot est touché. Fermée, elle montre les chapitres ; ouverte, le mot.",
                    papier = Color(0xFF101010),
                    encre = Color(0xFFF5F5F5),
                )
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    FenetreDesktop(Clair, EtatDesktop.CHAPITRES)
                    FenetreDesktop(Clair, EtatDesktop.MOT)
                }
            }
        }
        onRoot().captureRoboImage("prototype/desktop.png")
    }

    /** Planche 13 — le Vocabulaire, dans les trois fonds. */
    @Test
    fun vocabulaire() = runDesktopComposeUiTest(width = LARGEUR, height = 980) {
        setContent {
            Column(Modifier.size(LARGEUR.dp, 980.dp).background(Color(0xFF101010))) {
                TitrePlanche(
                    titre = "Le Vocabulaire — ce qu'on a ramassé en lisant",
                    sousTitre = "Le mot garde la sérif du livre ; sa traduction et sa phrase de " +
                        "contexte restent en sans-sérif. La corbeille est une icône discrète, et " +
                        "elle demande confirmation.",
                    papier = Color(0xFF101010),
                    encre = Color(0xFFF5F5F5),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    EcranVocabulaire(Clair, EtatVocabulaire.PLEIN)
                    EcranVocabulaire(Sombre, EtatVocabulaire.PLEIN)
                    EcranVocabulaire(Sepia, EtatVocabulaire.PLEIN)
                }
            }
        }
        onRoot().captureRoboImage("prototype/vocabulaire.png")
    }

    /** Planche 14 — les états du Vocabulaire : vide, sans résultat, et la suppression. */
    @Test
    fun vocabulaireEtats() = runDesktopComposeUiTest(width = LARGEUR, height = 980) {
        setContent {
            Column(Modifier.size(LARGEUR.dp, 980.dp).background(Color(0xFF101010))) {
                TitrePlanche(
                    titre = "Le Vocabulaire — trois états, dont un qui protège",
                    sousTitre = "Un vocabulaire vide ne dit pas la même chose qu'une recherche " +
                        "sans résultat ; et supprimer demande confirmation, parce que ça ne " +
                        "se devine pas.",
                    papier = Color(0xFF101010),
                    encre = Color(0xFFF5F5F5),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    EcranVocabulaire(Clair, EtatVocabulaire.VIDE)
                    EcranVocabulaire(Clair, EtatVocabulaire.SANS_RESULTAT)
                    EcranVocabulaire(Clair, EtatVocabulaire.SUPPRESSION)
                }
            }
        }
        onRoot().captureRoboImage("prototype/vocabulaire-etats.png")
    }
}

/** Les états du Vocabulaire. */
private enum class EtatVocabulaire { PLEIN, VIDE, SANS_RESULTAT, SUPPRESSION }

/** Une entrée du vocabulaire, telle que la liste la donne. */
private data class MotVocabulaire(
    val mot: String,
    val traduction: String,
    val contexte: String,
    val niveau: Niveau,
)

private val Vocabulaire = listOf(
    MotVocabulaire(
        "stood",
        "se tenir debout",
        "The fir tree stood in the wood and the children clapped their hands…",
        Niveau.UNKNOWN,
    ),
    MotVocabulaire(
        "adorned",
        "orné, décoré",
        "looked forward with pleasure to being adorned again",
        Niveau.UNKNOWN,
    ),
    MotVocabulaire(
        "Princess",
        "princesse",
        "who fell downstairs, and yet was raised to honour and married the Princess.",
        Niveau.RECOGNIZED,
    ),
    MotVocabulaire(
        "another",
        "un autre",
        "Tell another! tell another! for they wanted to hear about Ivede-Avede",
        Niveau.FAMILIAR,
    ),
    MotVocabulaire(
        "story",
        "histoire",
        "but they only got the story of Klumpey-Dumpey",
        Niveau.LEARNED,
    ),
)

/** Le nom français d'un niveau, tel que l'écran et le panneau le disent. */
private fun Niveau.nom(): String = when (this) {
    Niveau.UNKNOWN -> "Inconnu"
    Niveau.RECOGNIZED -> "Reconnu"
    Niveau.FAMILIAR -> "Familier"
    Niveau.LEARNED -> "Appris"
}

/** L'écran du vocabulaire, tel qu'un téléphone le montre. */
@Composable
private fun EcranVocabulaire(theme: Theme, etat: EtatVocabulaire) {
    Box(modifier = Modifier.width(390.dp).height(844.dp)) {
        Column(modifier = Modifier.fillMaxSize().background(theme.paper)) {
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Vocabulaire",
                        style = TextStyle(
                            fontFamily = FontFamily.Serif,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.ink,
                        ),
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "128 mots",
                        style = TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 12.sp,
                            color = theme.muted,
                        ),
                    )
                }
                if (etat == EtatVocabulaire.PLEIN || etat == EtatVocabulaire.SUPPRESSION) {
                    ChampRecherche(theme, placeholder = "Rechercher un mot")
                    Filtres(theme)
                    Column {
                        Vocabulaire.forEach { entree ->
                            LigneMot(theme, entree)
                        }
                    }
                }
                when (etat) {
                    EtatVocabulaire.VIDE -> {
                        ChampRecherche(theme, placeholder = "Rechercher un mot")
                        MessageCentre(
                            theme = theme,
                            titre = "Aucun mot",
                            corps = "Touche un mot pendant la lecture, et il atterrit ici.",
                        )
                    }

                    EtatVocabulaire.SANS_RESULTAT -> {
                        ChampRecherche(theme, placeholder = "Rechercher un mot", query = "princesse")
                        Filtres(theme)
                        MessageCentre(
                            theme = theme,
                            titre = "Aucun mot pour « princesse »",
                            corps = "La recherche porte sur le mot et sur sa traduction.",
                            lien = "Effacer la recherche",
                        )
                    }

                    else -> Unit
                }
            }
            BarreNavigation(theme, selection = 1)
        }
        if (etat == EtatVocabulaire.SUPPRESSION) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f)),
            )
            DialogueSuppression(theme, modifier = Modifier.align(Alignment.Center))
        }
    }
}

/** Les filtres de niveau : le choix courant en encre pleine, la pastille garde sa couleur. */
@Composable
private fun Filtres(theme: Theme) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Filtre(theme, "Tous", null, choisi = true)
        val rampe = theme.rampe()
        Filtre(theme, "Inconnu", rampe.unknown, choisi = false)
        Filtre(theme, "Reconnu", rampe.recognized, choisi = false)
        Filtre(theme, "Familier", rampe.familiar, choisi = false)
        Filtre(theme, "Appris", rampe.learned, choisi = false)
        Filtre(theme, "Connu", theme.outline, choisi = false)
    }
}

@Composable
private fun Filtre(theme: Theme, nom: String, pastille: Color?, choisi: Boolean) {
    Row(
        modifier = Modifier
            .background(
                if (choisi) theme.ink else theme.paper,
                RoundedCornerShape(8.dp),
            )
            .border(
                width = 1.dp,
                color = if (choisi) theme.ink else theme.outline,
                shape = RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (pastille != null) {
            Box(modifier = Modifier.size(8.dp).background(pastille, RoundedCornerShape(4.dp)))
        }
        Text(
            text = nom,
            style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 12.sp,
                color = if (choisi) theme.paper else theme.ink,
            ),
        )
    }
}

/** Une entrée : le mot en sérif, la traduction, la phrase qui l'a fait naître. */
@Composable
private fun LigneMot(theme: Theme, entree: MotVocabulaire) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = entree.mot,
                        style = TextStyle(
                            fontFamily = FontFamily.Serif,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.ink,
                        ),
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(theme.rampe().couleur(entree.niveau), RoundedCornerShape(4.dp)),
                    )
                    Text(
                        text = entree.niveau.nom(),
                        style = TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 11.sp,
                            color = theme.muted,
                        ),
                    )
                }
                Text(
                    text = entree.traduction,
                    style = TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 13.sp,
                        color = theme.ink,
                    ),
                )
                Text(
                    text = entree.contexte,
                    style = TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = theme.muted,
                    ),
                )
            }
            Icon(
                imageVector = LucideDemo.Trash,
                contentDescription = null,
                tint = theme.muted,
                modifier = Modifier.size(16.dp),
            )
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(theme.outline.copy(alpha = 0.6f)))
    }
}

/** Un message centré, avec un lien quand il y a une action à proposer. */
@Composable
private fun MessageCentre(theme: Theme, titre: String, corps: String, lien: String? = null) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = titre,
            style = TextStyle(
                fontFamily = FontFamily.Serif,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = theme.ink,
            ),
        )
        Text(
            text = corps,
            style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = theme.muted,
            ),
        )
        if (lien != null) {
            Text(
                text = lien,
                style = TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = theme.ink,
                ),
            )
        }
    }
}

/**
 * La suppression demande confirmation. Le rouge est la seule couleur saturée que
 * l'identité autorise hors des niveaux : il signifie « irréversible », jamais
 * « décoration ».
 */
@Composable
private fun DialogueSuppression(theme: Theme, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .width(310.dp)
            .background(theme.paper, RoundedCornerShape(16.dp))
            .border(1.dp, theme.outline, RoundedCornerShape(16.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Supprimer « stood » ?",
            style = TextStyle(
                fontFamily = FontFamily.Serif,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = theme.ink,
            ),
        )
        Text(
            text = "L'entrée quitte le vocabulaire. Le mot peut être enregistré de nouveau " +
                "en le touchant dans le livre.",
            style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = theme.muted,
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            Text(
                text = "Annuler",
                modifier = Modifier.padding(end = 20.dp),
                style = TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = theme.ink,
                ),
            )
            Text(
                text = "Supprimer",
                style = TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFB3261E),
                ),
            )
        }
    }
}

/** Ce que la colonne de contexte montre. */
private enum class EtatDesktop { CHAPITRES, MOT }

/** La fenêtre desktop : le chrome, la page, et sa colonne de contexte. */
@Composable
private fun FenetreDesktop(theme: Theme, etat: EtatDesktop) {
    Column(modifier = Modifier.width(1280.dp).height(800.dp).background(theme.paper)) {
        BarreHaut(theme)
        Row(modifier = Modifier.weight(1f)) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Column(
                    modifier = Modifier
                        .width(620.dp)
                        .align(Alignment.TopCenter)
                        .padding(vertical = 24.dp),
                ) {
                    Phrase(
                        theme = theme,
                        couleur = theme.rampe(),
                        souligne = true,
                        selection = if (etat == EtatDesktop.MOT) listOf("stood") else emptyList(),
                        mots = PASSAGE,
                    )
                }
            }
            Column(
                modifier = Modifier
                    .width(420.dp)
                    .fillMaxHeight()
                    .background(theme.surface),
            ) {
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(theme.outline))
                when (etat) {
                    EtatDesktop.CHAPITRES -> ColonneChapitres(theme)
                    EtatDesktop.MOT -> ColonneMot(theme)
                }
            }
        }
        BarreBas(theme)
    }
}

/** Fermée, la colonne est le sommaire : ce que le lecteur peut vouloir rejoindre. */
@Composable
private fun ColonneChapitres(theme: Theme) {
    Column(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "Chapitres",
            style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 12.sp,
                color = theme.muted,
            ),
        )
        listOf(
            "1. The Fir Tree" to true,
            "2. The Snow Queen" to false,
            "3. The Little Mermaid" to false,
            "4. The Nightingale" to false,
            "5. The Ugly Duckling" to false,
        ).forEach { (titre, courant) ->
            Text(
                text = titre,
                style = TextStyle(
                    fontFamily = FontFamily.Serif,
                    fontSize = 15.sp,
                    fontWeight = if (courant) FontWeight.Bold else FontWeight.Normal,
                    color = if (courant) theme.ink else theme.muted,
                ),
            )
        }
        Box(modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(1.dp).background(theme.outline))
        Text(
            text = "Page 99 sur 357",
            style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 11.sp,
                color = theme.muted,
            ),
        )
    }
}

/** Ouverte, la colonne est le panneau du mot — plus haut, plus aéré, jamais superposé. */
@Composable
private fun ColonneMot(theme: Theme) {
    Column(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "stood",
                    style = TextStyle(
                        fontFamily = FontFamily.Serif,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.ink,
                    ),
                )
                Text(
                    text = "verbe · /stʊd/",
                    style = TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 12.sp,
                        color = theme.muted,
                    ),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(
                    imageVector = LucideDemo.Volume2,
                    contentDescription = null,
                    tint = theme.ink,
                    modifier = Modifier.size(20.dp),
                )
                Icon(
                    imageVector = LucideDemo.X,
                    contentDescription = null,
                    tint = theme.ink,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Separateur(theme)
        Sens("1", "the past tense of stand.", null, theme)
        Sens("2", "to be in an upright position, supported by the feet.", "se tenir debout", theme)
        Sens("3", "to remain in a place without moving.", "rester", theme)
        Sens("4", "to rise to one's feet after sitting.", "se lever", theme)
        Separateur(theme)
        Text(
            text = "Niveau — Reconnu",
            style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 12.sp,
                color = theme.muted,
            ),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val rampe = theme.rampe()
            ChipNiveau(theme, "0", rampe.unknown, choisi = false)
            ChipNiveau(theme, "1", rampe.recognized, choisi = true)
            ChipNiveau(theme, "2", rampe.familiar, choisi = false)
            ChipNiveau(theme, "3", rampe.learned, choisi = false)
            ChipNiveau(theme, "4", theme.outline, choisi = false)
        }
        Separateur(theme)
        Text(
            text = "Chercher ailleurs",
            style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 12.sp,
                color = theme.muted,
            ),
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("WordReference", "Reverso", "Linguee", "Google Traduction").forEach { lien ->
                Text(
                    text = lien,
                    style = TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 12.sp,
                        color = theme.ink,
                    ),
                )
            }
        }
    }
}

/** Les états du panneau de recherche. */
private enum class EtatRecherche { MOT, HORS_LIGNE, PHRASE_IA, PHRASE_SANS_IA }

/** Le lecteur, avec le panneau posé dessus. */
@Composable
private fun EcranRecherche(theme: Theme, etat: EtatRecherche) {
    Box(modifier = Modifier.width(390.dp).height(844.dp)) {
        val selection = when (etat) {
            EtatRecherche.PHRASE_IA, EtatRecherche.PHRASE_SANS_IA -> listOf("fir", "tree", "stood")
            else -> listOf("stood")
        }
        EcranLecteur(theme, chrome = true, selection = selection)
        val bas = Modifier.align(Alignment.BottomCenter)
        when (etat) {
            EtatRecherche.MOT -> PanneauMot(theme, modifier = bas)
            EtatRecherche.HORS_LIGNE -> PanneauMot(theme, horsLigne = true, modifier = bas)
            EtatRecherche.PHRASE_IA -> PanneauPhrase(theme, ia = true, modifier = bas)
            EtatRecherche.PHRASE_SANS_IA -> PanneauPhrase(theme, ia = false, modifier = bas)
        }
    }
}

/**
 * Le panneau d'un mot. Il n'y a pas de bouton « Enregistrer » : **le niveau est
 * l'enregistrement**. Un geste, et le mot est au niveau choisi.
 */
@Composable
private fun PanneauMot(theme: Theme, horsLigne: Boolean = false, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(theme.surface, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(theme.outline))
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(36.dp, 4.dp)
                    .background(theme.outline, RoundedCornerShape(2.dp)),
            )
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "stood",
                        style = TextStyle(
                            fontFamily = FontFamily.Serif,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.ink,
                        ),
                    )
                    Text(
                        text = "verbe · /stʊd/",
                        style = TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 12.sp,
                            color = theme.muted,
                        ),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(
                        imageVector = LucideDemo.Volume2,
                        contentDescription = null,
                        tint = theme.ink,
                        modifier = Modifier.size(20.dp),
                    )
                    Icon(
                        imageVector = LucideDemo.X,
                        contentDescription = null,
                        tint = theme.ink,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Separateur(theme)
            if (horsLigne) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Le dictionnaire n'est pas installé sur cet appareil.",
                        style = TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = theme.ink,
                        ),
                    )
                    Text(
                        text = "Le mot peut quand même être enregistré : il sera traduit dès " +
                            "qu'un dictionnaire sera là.",
                        style = TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = theme.muted,
                        ),
                    )
                    Text(
                        text = "Voir les langues",
                        style = TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = theme.ink,
                        ),
                    )
                }
            } else {
                Sens("1", "the past tense of stand.", null, theme)
                Sens("2", "to be in an upright position, supported by the feet.", "se tenir debout", theme)
                Sens("3", "to remain in a place without moving.", "rester", theme)
                Text(
                    text = "…",
                    style = TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 12.sp,
                        color = theme.muted,
                    ),
                )
            }
            Separateur(theme)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Niveau",
                        style = TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 11.sp,
                            color = theme.muted,
                        ),
                    )
                    Text(
                        text = "Reconnu",
                        style = TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = theme.ink,
                        ),
                    )
                }
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val rampe = theme.rampe()
                    ChipNiveau(theme, "0", rampe.unknown, choisi = false)
                    ChipNiveau(theme, "1", rampe.recognized, choisi = true)
                    ChipNiveau(theme, "2", rampe.familiar, choisi = false)
                    ChipNiveau(theme, "3", rampe.learned, choisi = false)
                    ChipNiveau(theme, "4", theme.outline, choisi = false)
                }
            }
            Separateur(theme)
            Text(
                text = "WordReference · Reverso · Linguee · Google Traduction",
                style = TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 11.sp,
                    color = theme.muted,
                ),
            )
        }
    }
}

/** Un sens : le numéro, la définition, et la traduction quand elle existe. */
@Composable
private fun Sens(numero: String, definition: String, traduction: String?, theme: Theme) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = numero,
            style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 12.sp,
                color = theme.muted,
            ),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = definition,
                style = TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = theme.ink,
                ),
            )
            if (traduction != null) {
                Text(
                    text = "→ $traduction",
                    style = TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = theme.muted,
                    ),
                )
            }
        }
    }
}

/** Un niveau du panneau : sa couleur, son numéro, et un anneau s'il est le niveau courant. */
@Composable
private fun ChipNiveau(theme: Theme, numero: String, fond: Color, choisi: Boolean) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .background(fond, RoundedCornerShape(8.dp))
            .then(if (choisi) Modifier.border(2.dp, theme.ink, RoundedCornerShape(8.dp)) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = numero,
            style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (fond.luminance() > 0.4f) Color(0xFF1A1A1A) else Color.White,
            ),
        )
    }
}

/** Le trait qui sépare, dans le panneau : une couleur, pas une ombre. */
@Composable
private fun Separateur(theme: Theme) {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(theme.outline.copy(alpha = 0.6f)))
}

/**
 * Le panneau d'une phrase : la traduction vient du fournisseur du lecteur, ou
 * d'une invite qui ne bloque rien. Aucun dictionnaire hors ligne ne traduit une
 * phrase, donc l'invite est le seul chemin quand rien n'est configuré.
 */
@Composable
private fun PanneauPhrase(theme: Theme, ia: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(theme.surface, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(theme.outline))
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(36.dp, 4.dp)
                    .background(theme.outline, RoundedCornerShape(2.dp)),
            )
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = "the fir tree stood",
                    modifier = Modifier.weight(1f),
                    style = TextStyle(
                        fontFamily = FontFamily.Serif,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.ink,
                    ),
                )
                Icon(
                    imageVector = LucideDemo.X,
                    contentDescription = null,
                    tint = theme.ink,
                    modifier = Modifier.size(20.dp),
                )
            }
            Separateur(theme)
            if (ia) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Le sapin se tenait debout",
                        style = TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            color = theme.ink,
                        ),
                    )
                    Text(
                        text = "Traduit par ton fournisseur — 12 mots envoyés",
                        style = TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 11.sp,
                            color = theme.muted,
                        ),
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Une phrase a besoin d'une IA pour être traduite.",
                        style = TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = theme.ink,
                        ),
                    )
                    Text(
                        text = "Un mot a son dictionnaire hors ligne ; une phrase n'en a pas.",
                        style = TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = theme.muted,
                        ),
                    )
                    Text(
                        text = "Configurer un fournisseur",
                        style = TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = theme.ink,
                        ),
                    )
                }
            }
            Separateur(theme)
            Text(
                text = "Google Traduction",
                style = TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 11.sp,
                    color = theme.muted,
                ),
            )
        }
    }
}

/** La page telle qu'un livre la donne : un passage, pas deux lignes. */
private val PASSAGE = listOf(
    Mot("The", null),
    Mot("fir", Niveau.LEARNED),
    Mot("tree", null),
    Mot("stood", Niveau.UNKNOWN),
    Mot("in", null),
    Mot("the", null),
    Mot("wood", Niveau.FAMILIAR),
    Mot("and", null),
    Mot("the", null),
    Mot("children", null),
    Mot("clapped", Niveau.UNKNOWN),
    Mot("their", null),
    Mot("hands", null),
    Mot("and", null),
    Mot("cried", Niveau.RECOGNIZED),
    Mot("Tell", null),
    Mot("another!", Niveau.FAMILIAR),
    Mot("for", null),
    Mot("they", null),
    Mot("wanted", null),
    Mot("to", null),
    Mot("hear", null),
    Mot("about", null),
    Mot("Ivede-Avede;", Niveau.UNKNOWN),
    Mot("but", null),
    Mot("they", null),
    Mot("only", null),
    Mot("got", null),
    Mot("the", null),
    Mot("story", Niveau.LEARNED),
    Mot("of", null),
    Mot("Klumpey-Dumpey,", Niveau.UNKNOWN),
    Mot("who", null),
    Mot("fell", null),
    Mot("downstairs,", null),
    Mot("and", null),
    Mot("yet", null),
    Mot("was", null),
    Mot("raised", Niveau.RECOGNIZED),
    Mot("to", null),
    Mot("honour", Niveau.UNKNOWN),
    Mot("and", null),
    Mot("married", null),
    Mot("the", null),
    Mot("Princess.", null),
    Mot("And", null),
    Mot("the", null),
    Mot("children", null),
    Mot("looked", null),
    Mot("forward", null),
    Mot("with", null),
    Mot("pleasure", Niveau.FAMILIAR),
    Mot("to", null),
    Mot("being", null),
    Mot("adorned", Niveau.UNKNOWN),
    Mot("again,", null),
    Mot("the", null),
    Mot("next", null),
    Mot("evening,", null),
    Mot("with", null),
    Mot("candles", null),
    Mot("and", null),
    Mot("toys,", null),
    Mot("gold", null),
    Mot("and", null),
    Mot("fruit.", null),
    Mot("Then", null),
    Mot("the", null),
    Mot("fir", Niveau.LEARNED),
    Mot("tree", null),
    Mot("thought", Niveau.UNKNOWN),
    Mot("of", null),
    Mot("the", null),
    Mot("next", null),
    Mot("evening,", null),
    Mot("and", null),
    Mot("looked", null),
    Mot("forward", null),
    Mot("with", null),
    Mot("pleasure", Niveau.FAMILIAR),
    Mot("to", null),
    Mot("being", null),
    Mot("adorned", Niveau.UNKNOWN),
    Mot("again.", null),
    Mot("Yes,", null),
    Mot("so", null),
    Mot("it", null),
    Mot("happens", Niveau.UNKNOWN),
    Mot("in", null),
    Mot("the", null),
    Mot("world!", Niveau.RECOGNIZED),
    Mot("thought", Niveau.UNKNOWN),
    Mot("the", null),
    Mot("fir", Niveau.LEARNED),
    Mot("tree,", null),
    Mot("and", null),
    Mot("believed", Niveau.UNKNOWN),
    Mot("it", null),
    Mot("must", null),
    Mot("be", null),
    Mot("true,", null),
    Mot("because", null),
    Mot("it", null),
    Mot("was", null),
    Mot("such", null),
    Mot("a", null),
    Mot("nice", Niveau.RECOGNIZED),
    Mot("man", null),
    Mot("who", null),
    Mot("told", Niveau.UNKNOWN),
    Mot("it.", null),
    Mot("Well,", null),
    Mot("who", null),
    Mot("can", null),
    Mot("know?", Niveau.UNKNOWN),
    Mot("Perhaps", null),
    Mot("I", null),
    Mot("shall", null),
    Mot("fall", Niveau.RECOGNIZED),
    Mot("downstairs,", null),
    Mot("too,", null),
    Mot("and", null),
    Mot("marry", Niveau.UNKNOWN),
    Mot("a", null),
    Mot("Princess!", Niveau.FAMILIAR),
)

/** L'écran tel qu'un téléphone le montre : deux bandes de chrome, la page au milieu. */
@Composable
private fun EcranLecteur(
    theme: Theme,
    chrome: Boolean,
    zones: Boolean = false,
    panneau: Boolean = false,
    selection: List<String> = emptyList(),
) {
    Column(
        modifier = Modifier
            .width(390.dp)
            .height(844.dp)
            .background(theme.paper),
    ) {
        if (chrome) BarreHaut(theme)
        Box(modifier = Modifier.weight(1f)) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                Phrase(
                    theme = theme,
                    couleur = theme.rampe(),
                    souligne = true,
                    selection = selection,
                    mots = PASSAGE,
                )
            }
            if (zones) ZonesDeToucher(theme)
        }
        if (chrome) BarreBas(theme)
        if (panneau) PanneauTexte(theme)
    }
}

/**
 * La bande du haut : la sortie, le titre du livre, et le panneau du texte. Trois
 * choses, pas une de plus — la couleur du reste appartient à la page.
 */
@Composable
private fun BarreHaut(theme: Theme) {
    Row(
        modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = LucideDemo.ArrowLeft,
            contentDescription = null,
            tint = theme.ink,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = "Children's Literature",
            style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 14.sp,
                color = theme.muted,
            ),
        )
        Spacer(Modifier.weight(1f))
        Icon(
            imageVector = LucideDemo.ALargeSmall,
            contentDescription = null,
            tint = theme.ink,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * La bande du bas : où l'on est. Les chevrons ne sont pas des boutons — ce sont
 * les zones du tiers gauche et du tiers droit, montrées. Toucher le texte les
 * fait disparaître.
 */
@Composable
private fun BarreBas(theme: Theme) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = LucideDemo.ChevronLeft,
            contentDescription = null,
            tint = theme.muted.copy(alpha = 0.6f),
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "Page 99 sur 357",
            style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 12.sp,
                color = theme.muted,
            ),
        )
        Spacer(Modifier.weight(1f))
        Icon(
            imageVector = LucideDemo.ChevronRight,
            contentDescription = null,
            tint = theme.muted.copy(alpha = 0.6f),
            modifier = Modifier.size(18.dp),
        )
    }
}

/** Les trois tiers, montrés : la page précédente, le chrome, la page suivante. */
@Composable
private fun BoxScope.ZonesDeToucher(theme: Theme) {
    Row(modifier = Modifier.fillMaxSize()) {
        ZoneTactile(theme, "Page précédente", Modifier.weight(1f))
        ZoneTactile(theme, "Chrome", Modifier.weight(1f))
        ZoneTactile(theme, "Page suivante", Modifier.weight(1f))
    }
}

/** Un tiers de la page, montré à 6 % d'encre pour que le lecteur se repère. */
@Composable
private fun ZoneTactile(theme: Theme, label: String, modifier: Modifier) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(2.dp)
            .background(theme.ink.copy(alpha = 0.06f), RoundedCornerShape(8.dp)),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(bottom = 12.dp),
            style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 11.sp,
                color = theme.muted,
            ),
        )
    }
}

/**
 * Le panneau du texte : la face, la taille, les marges. Il vit dans le lecteur
 * (ADR-0035) et il n'y a pas de bouton de validation — la page au-dessus est
 * l'aperçu.
 */
@Composable
private fun PanneauTexte(theme: Theme) {
    Column {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(theme.outline))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(theme.surface)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Le texte",
                    style = TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = theme.ink,
                    ),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "Fermer",
                    style = TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 13.sp,
                        color = theme.ink,
                    ),
                )
            }
            RowTexte(
                theme = theme,
                label = "Police",
                options = listOf("Sérif" to true, "Sans" to false),
            )
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Taille",
                    style = TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 13.sp,
                        color = theme.ink,
                    ),
                )
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .border(1.dp, theme.outline, RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "−",
                        style = TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 14.sp,
                            color = theme.ink,
                        ),
                    )
                }
                Text(
                    text = "18",
                    modifier = Modifier.padding(horizontal = 14.dp),
                    style = TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = theme.ink,
                    ),
                )
                Box(
                    modifier = Modifier
                        .border(1.dp, theme.outline, RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "+",
                        style = TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 14.sp,
                            color = theme.ink,
                        ),
                    )
                }
            }
            RowTexte(
                theme = theme,
                label = "Marges",
                options = listOf("Étroit" to false, "Confort" to true, "Large" to false),
            )
        }
    }
}

/** Une rangée de choix du panneau : un seul est plein, les autres sont des contours. */
@Composable
private fun RowTexte(theme: Theme, label: String, options: List<Pair<String, Boolean>>) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 13.sp,
                color = theme.ink,
            ),
        )
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (nom, choisi) ->
                Box(
                    modifier = Modifier
                        .background(
                            if (choisi) theme.ink else theme.paper,
                            RoundedCornerShape(8.dp),
                        )
                        .border(
                            width = 1.dp,
                            color = if (choisi) theme.ink else theme.outline,
                            shape = RoundedCornerShape(8.dp),
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = nom,
                        style = TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 12.sp,
                            color = if (choisi) theme.paper else theme.ink,
                        ),
                    )
                }
            }
        }
    }
}


/** Les états que l'écran doit savoir montrer. */
private enum class EtatEcran { PLEIN, VIDE, CHARGEMENT, ERREUR }

/** Un livre tel que la Bibliothèque le montre — sans couverture, il n'y en a pas. */
private data class LivreListe(
    val titre: String,
    val code: String,
    val teinte: Color,
    val format: String,
    /** La part lue, ou `null` pour un livre jamais ouvert. */
    val part: Float?,
)

/** Les teintes de langue : mates, terreuses, hors du vocabulaire de la maîtrise. */
private val Livres = listOf(
    LivreListe("Children's Literature", "EN", Color(0xFF4C6B8A), "EPUB", 0.32f),
    LivreListe("The Fir Tree", "EN", Color(0xFF4C6B8A), "EPUB", 0.78f),
    LivreListe("Le Petit Prince", "FR", Color(0xFF8A5A44), "EPUB", 0.04f),
    LivreListe("Cuentos de la selva", "ES", Color(0xFF9A7B33), "TXT", null),
    LivreListe("Der Struwwelpeter", "DE", Color(0xFF5F7A5A), "EPUB", 0.61f),
)

/** L'écran tel qu'un téléphone le montre : 390 sur 844. */
@Composable
private fun EcranBibliotheque(theme: Theme, etat: EtatEcran) {
    Column(
        modifier = Modifier
            .width(390.dp)
            .height(844.dp)
            .background(theme.paper),
    ) {
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Bibliothèque",
                    style = TextStyle(
                        fontFamily = FontFamily.Serif,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.ink,
                    ),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "Importer",
                    style = TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = theme.ink,
                    ),
                )
            }
            ChampRecherche(theme)
            when (etat) {
                EtatEcran.PLEIN -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Livres.forEach { livre -> LigneLivre(theme, livre) }
                }

                EtatEcran.VIDE -> EtatVide(theme)
                EtatEcran.CHARGEMENT -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    repeat(4) { LigneSquelette(theme) }
                }

                EtatEcran.ERREUR -> EtatErreur(theme)
            }
        }
        BarreNavigation(theme)
    }
}

/** Le champ de recherche (issue #173) : une surface, la loupe, et rien de plus. */
@Composable
private fun ChampRecherche(
    theme: Theme,
    placeholder: String = "Rechercher un titre",
    query: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.surface, RoundedCornerShape(12.dp))
            .border(1.dp, theme.outline, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = LucideDemo.Search,
            contentDescription = null,
            tint = theme.muted,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = query ?: placeholder,
            style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 14.sp,
                color = if (query != null) theme.ink else theme.muted,
            ),
        )
    }
}

/** Une ligne de la liste : la vignette, le titre, et la progression. */
@Composable
private fun LigneLivre(theme: Theme, livre: LivreListe) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Vignette(theme, livre)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = livre.titre,
                style = TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = theme.ink,
                ),
            )
            Text(
                text = livre.part?.let { part -> "${livre.format} · ${(part * 100).toInt()} %" }
                    ?: "${livre.format} · jamais ouvert",
                style = TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 12.sp,
                    color = theme.muted,
                ),
            )
            if (livre.part != null) BarreProgression(theme, livre.part)
        }
    }
}

/** La progression : un trait, pas un ornement (issue #172). */
@Composable
private fun BarreProgression(theme: Theme, part: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(3.dp)
            .background(theme.outline, RoundedCornerShape(2.dp)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(part)
                .height(3.dp)
                .background(theme.ink, RoundedCornerShape(2.dp)),
        )
    }
}

/**
 * La vignette : il n'y a aucune couverture dans les données (un `Book` porte un
 * titre, une langue, un format et un nom de fichier), donc la vignette porte la
 * seule information qui manque au titre — la langue.
 */
@Composable
private fun Vignette(theme: Theme, livre: LivreListe) {
    val teinte = lerp(livre.teinte, Color.White, theme.eclat)
    Box(
        modifier = Modifier
            .size(44.dp, 60.dp)
            .background(teinte, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = livre.code,
            style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (teinte.luminance() > 0.4f) Color(0xFF1A1A1A) else Color.White,
            ),
        )
    }
}

/** Une ligne de squelette : la vignette et deux traits, à la place d'un livre. */
@Composable
private fun LigneSquelette(theme: Theme) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp, 60.dp)
                .background(theme.outline, RoundedCornerShape(8.dp)),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(14.dp)
                    .background(theme.outline, RoundedCornerShape(4.dp)),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .height(10.dp)
                    .background(theme.outline, RoundedCornerShape(4.dp)),
            )
        }
    }
}

/** L'état vide : il dit quoi faire, et il porte l'action. */
@Composable
private fun EtatVide(theme: Theme) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Aucun livre",
            style = TextStyle(
                fontFamily = FontFamily.Serif,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = theme.ink,
            ),
        )
        Text(
            text = "Un EPUB ou un texte, et le premier mot est à une minute.",
            style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = theme.muted,
            ),
        )
        Box(
            modifier = Modifier
                .padding(top = 16.dp)
                .background(theme.ink, RoundedCornerShape(8.dp))
                .padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            Text(
                text = "Importer un livre",
                style = TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = theme.paper,
                ),
            )
        }
    }
}

/** L'échec : là où l'action a eu lieu, jamais en plein écran. */
@Composable
private fun EtatErreur(theme: Theme) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.surface, RoundedCornerShape(12.dp))
            .border(1.dp, theme.outline, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Ce livre n'a pas pu être importé",
            style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = theme.ink,
            ),
        )
        Text(
            text = "Le fichier est peut-être protégé. La bibliothèque n'a pas changé.",
            style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = theme.muted,
            ),
        )
        Text(
            text = "Fermer",
            style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = theme.ink,
            ),
        )
    }
}

/** La coquille : trois lieux, et l'onglet courant en encre pleine. */
@Composable
private fun BarreNavigation(theme: Theme, selection: Int = 0) {
    Column {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(theme.outline))
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Onglet(theme, LucideDemo.BookOpen, "Bibliothèque", selection == 0)
            Onglet(theme, LucideDemo.Bookmark, "Vocabulaire", selection == 1)
            Onglet(theme, LucideDemo.Settings, "Réglages", selection == 2)
        }
    }
}

/** Un onglet : l'icône, le mot, et rien d'autre. */
@Composable
private fun Onglet(theme: Theme, icone: ImageVector, label: String, choisi: Boolean) {
    val teinte = if (choisi) theme.ink else theme.muted
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icone,
            contentDescription = null,
            tint = teinte,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = label,
            style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 11.sp,
                fontWeight = if (choisi) FontWeight.Medium else FontWeight.Normal,
                color = teinte,
            ),
        )
    }
}

/** Une ligne de la planche 5 : le libellé, la note, et les icônes à taille réelle. */
@Composable
private fun GeometrieLigne(titre: String, note: String, icones: List<ImageVector>) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.width(300.dp).padding(end = 16.dp)) {
                Text(
                    text = titre,
                    style = TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Clair.ink,
                    ),
                )
                Text(
                    text = note,
                    style = TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = Clair.muted,
                    ),
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                icones.forEach { icone ->
                    Icon(
                        imageVector = icone,
                        contentDescription = null,
                        tint = Clair.ink,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .height(1.dp)
                .background(Clair.outline),
        )
    }
}

/** Un fond, une phrase, et le mot touché lavé à l'encre. */
@Composable
private fun Fond(theme: Theme, mot: String, alpha: Float) {
    Colonne(theme) {
        Text(
            text = theme.name,
            style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = theme.ink,
            ),
        )
        Text(
            text = "Encre à ${(alpha * 100).toInt()} % sur le mot touché — ici « $mot »",
            style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 12.sp,
                color = theme.muted,
            ),
        )
        PageLave(theme, mot, alpha)
        Text(
            text = "Sans sélection, pour comparer",
            style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 12.sp,
                color = theme.muted,
            ),
        )
        Page(theme)
    }
}

/** La phrase avec un mot sélectionné, lavé à [alpha] d'encre. */
@Composable
private fun PageLave(theme: Theme, mot: String, alpha: Float, mots: List<Mot> = PHRASE) {
    val decoration = TextDecoration.None
    Text(
        text = buildAnnotatedString {
            mots.forEachIndexed { index, token ->
                val niveau = token.niveau
                val style = if (niveau != null) {
                    SpanStyle(
                        color = theme.rampe().couleur(niveau),
                        background = if (token.texte == mot) theme.ink.copy(alpha = alpha) else Color.Unspecified,
                        textDecoration = decoration,
                    )
                } else {
                    null
                }
                if (style != null) withStyle(style) { append(token.texte) } else append(token.texte)
                if (index != mots.lastIndex) append(" ")
            }
        },
        style = TextStyle(
            fontFamily = FontFamily.Serif,
            fontSize = 18.sp,
            lineHeight = 30.6.sp,
            color = theme.ink,
        ),
    )
}

/** Une ligne de la planche 3 : le libellé, la mesure, et la phrase traitée. */
@Composable
private fun Traitement(
    titre: String,
    note: String,
    fond: Color? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.width(300.dp).padding(end = 16.dp)) {
                Text(
                    text = titre,
                    style = TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Clair.ink,
                    ),
                )
                Text(
                    text = note,
                    style = TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = Clair.muted,
                    ),
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(fond ?: Color.Unspecified, RoundedCornerShape(8.dp))
                    .padding(if (fond != null) 12.dp else 0.dp),
            ) { content() }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .height(1.dp)
                .background(Clair.outline),
        )
    }
}
