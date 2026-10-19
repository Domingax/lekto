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
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
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
import app.lekto.prototype.icons.symbols.SymbolsDemo
import app.lekto.prototype.icons.symbols.VolumeUp
import io.github.takahirom.roborazzi.captureRoboImage
import kotlin.test.Test
import app.lekto.prototype.icons.symbols.Search as SearchSymbols
import app.lekto.prototype.icons.symbols.Settings as SettingsSymbols

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
private val Light = Theme(
    name = "Light — paper",
    paper = Color(0xFFFAF7F2),
    surface = Color(0xFFF3EEE6),
    outline = Color(0xFFD6CCBC),
    ink = Color(0xFF1C1A17),
    muted = Color(0xFF6B6357),
    unknown = Color(0xFF1D4ED8),
    recognized = Color(0xFFA16207),
    familiar = Color(0xFFC2410C),
    learned = Color(0xFF15803D),
    ratios = "16.2:1 text · 6.3 · 4.6 · 4.8 · 4.7",
)

private val Dark = Theme(
    name = "Dark — warm night",
    paper = Color(0xFF15130F),
    surface = Color(0xFF1F1C17),
    outline = Color(0xFF3A342B),
    ink = Color(0xFFE8E2D8),
    muted = Color(0xFF9A9083),
    unknown = Color(0xFF8AB4F8),
    recognized = Color(0xFFF2C14E),
    familiar = Color(0xFFEE9A5F),
    learned = Color(0xFF7BD88F),
    ratios = "14.4:1 text · 8.8 · 11.1 · 8.3 · 10.7",
    eclat = 0.35f,
)

private val Sepia = Theme(
    name = "Sepia — rag paper",
    paper = Color(0xFFF6ECD8),
    surface = Color(0xFFEFE3C9),
    outline = Color(0xFFD8C7A6),
    ink = Color(0xFF3B2F1E),
    muted = Color(0xFF6B5B41),
    unknown = Color(0xFF1D4ED8),
    recognized = Color(0xFF92400E),
    familiar = Color(0xFF9A3412),
    learned = Color(0xFF166534),
    ratios = "11.1:1 text · 5.7 · 6.1 · 6.2 · 6.1",
)

/** La palette livrée aujourd'hui, pour mesurer l'écart. */
private val Aujourdhui = Theme(
    name = "As delivered",
    paper = Color.White,
    surface = Color(0xFFF4F4F5),
    outline = Color(0xFFD4D4D8),
    ink = Color(0xFF1A1A1A),
    muted = Color(0xFF71717A),
    unknown = Color(0xFF2563EB),
    recognized = Color(0xFFFACC15),
    familiar = Color(0xFFFB923C),
    learned = Color(0xFF22C55E),
    ratios = "5.2:1 text · FAIL 1.5 · FAIL 2.3 · FAIL 2.3",
)

/** Les quatre niveaux d'une rampe, quelle qu'elle soit. */
private data class Rampe(val unknown: Color, val recognized: Color, val familiar: Color, val learned: Color)

private enum class Mastery { UNKNOWN, RECOGNIZED, FAMILIAR, LEARNED }

private data class Mot(val texte: String, val niveau: Mastery?)

/** La phrase du livre, étiquetée par niveau. C'est elle qu'on colore. */
private val PHRASE = listOf(
    Mot("The", null),
    Mot("fat", Mastery.LEARNED),
    Mot("man", null),
    Mot("told", Mastery.UNKNOWN),
    Mot("the", null),
    Mot("children", null),
    Mot("that", null),
    Mot("tomorrow", Mastery.RECOGNIZED),
    Mot("would", null),
    Mot("be", null),
    Mot("another", Mastery.FAMILIAR),
    Mot("day,", null),
    Mot("and", null),
    Mot("the", null),
    Mot("fir", null),
    Mot("tree", null),
    Mot("stood", Mastery.UNKNOWN),
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
private val VifLight = Rampe(
    unknown = Color(0x592563EB),
    recognized = Color(0x8CFACC15),
    familiar = Color(0x73FB923C),
    learned = Color(0x6622C55E),
)

private fun Rampe.couleur(niveau: Mastery): Color = when (niveau) {
    Mastery.UNKNOWN -> unknown
    Mastery.RECOGNIZED -> recognized
    Mastery.FAMILIAR -> familiar
    Mastery.LEARNED -> learned
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
            text = "Paper, ink, and one saturated colour: meaning.",
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
                    text = "Import",
                    style = TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = theme.paper,
                    ),
                )
            }
            Text(
                text = "Settings",
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
            text = "Lookup panel",
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
            Pastille("Unknown", theme.unknown)
            Pastille("Recognized", theme.recognized)
            Pastille("Familiar", theme.familiar)
            Pastille("Learned", theme.learned)
        }
        Text(
            text = "Measured contrast — ${theme.ratios}",
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
                    titre = "Lekto — three papers, one identity",
                    sousTitre = "The same screen on warm paper, a warm night and rag paper. " +
                        "The only saturated colours are the ones that carry meaning.",
                    papier = Color(0xFF101010),
                    encre = Color(0xFFF5F5F5),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Feuille(Light)
                    Feuille(Dark)
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
                    titre = "The delivered palette fails, and the fix can be measured",
                    sousTitre = "A coloured word is 18 sp of body text: the WCAG AA bar is 4.5:1. " +
                        "Three levels out of four fall short — the yellow at 1.5:1 is almost invisible.",
                    papier = Color(0xFF101010),
                    encre = Color(0xFFF5F5F5),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Colonne(Aujourdhui) {
                        Text(
                            text = "As delivered",
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
                    Colonne(Light) {
                        Text(
                            text = "Proposed — the same sentence, legible",
                            style = TextStyle(
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Light.ink,
                            ),
                        )
                        Page(Light)
                        PageInPanel(Light)
                    }
                    Colonne(Dark) {
                        Text(
                            text = "Dark is another set, not a lightening",
                            style = TextStyle(
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Dark.ink,
                            ),
                        )
                        Page(Dark)
                        PageInPanel(Dark)
                    }
                }
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icone("library", Color(0xFF9A9083))
                    Icone("vocabulary", Color(0xFF9A9083))
                    Icone("languages", Color(0xFF9A9083))
                    Icone("settings", Color(0xFF9A9083))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Icons: Lucide geometry, 24×24, stroke 2, round caps.",
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
            Column(Modifier.size(LARGEUR.dp, 660.dp).background(Light.paper)) {
                TitrePlanche(
                    titre = "Four ways to mark a word, on the same paper",
                    sousTitre = "One question: what tells a word still to learn apart from " +
                        "one already known, without making the page unreadable?",
                    papier = Light.paper,
                    encre = Light.ink,
                )
                Traitement(
                    titre = "As delivered — coloured letters, underlined",
                    note = "1.4 · 2.3 · 2.3:1 — three levels below AA",
                    fond = Color.White,
                ) {
                    Phrase(Light, RampAujourdhui, souligne = true)
                }
                Traitement(
                    titre = "Proposed — coloured letters, no underline",
                    note = "4.7 to 6.3:1 — but nothing marks the word without the colour",
                ) {
                    Phrase(Light, Light.rampe())
                }
                Traitement(
                    titre = "Proposed — coloured letters, underlined",
                    note = "4.7 to 6.3:1, and the underline carries the meaning without the colour",
                ) {
                    Phrase(Light, Light.rampe(), souligne = true)
                }
                Traitement(
                    titre = "Variant — a wash behind the word",
                    note = "bright hues become usable again: the ink carries the contrast",
                ) {
                    Phrase(Light, VifLight, lavis = VifLight)
                }
                Traitement(
                    titre = "Variant — the wash, underlined",
                    note = "the non-colour marker joins the wash",
                ) {
                    Phrase(Light, VifLight, lavis = VifLight, souligne = true)
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
                    titre = "The touched word: translucent ink, not one more colour",
                    sousTitre = "The selected word's wash is ink at 14 % or 24 %, never " +
                        "a hue: the app's accent stays the ink.",
                    papier = Color(0xFF101010),
                    encre = Color(0xFFF5F5F5),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Fond(Light, "told", 0.14f)
                    Fond(Dark, "told", 0.14f)
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
            Column(Modifier.size(LARGEUR.dp, 700.dp).background(Light.paper)) {
                TitrePlanche(
                    titre = "Geometry is the drawing rules a whole icon set shares",
                    sousTitre = "The grid, the stroke width, the shape of the caps. " +
                        "When a whole set shares them it reads as a family — and one icon " +
                        "from another set jumps out.",
                    papier = Light.paper,
                    encre = Light.ink,
                )
                GeometrieLigne(
                    titre = "Lucide — the chosen set",
                    note = "24 grid, 2 stroke, round caps and joins. " +
                        "The drawing is an outline: the inside stays paper.",
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
                    titre = "Material Symbols — the other school",
                    note = "960 grid, solid shapes. Heavier, bulkier, " +
                        "and no stroke to share.",
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
                    titre = "The mix, in one row",
                    note = "first, third and fifth from Lucide; the rest from Material " +
                        "Symbols. Nothing is broken, but nothing matches.",
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
                        text = "The same concept, enlarged: the stroke against the solid shape",
                        style = TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Light.ink,
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
                            tint = Light.ink,
                            modifier = Modifier.size(96.dp),
                        )
                        Icon(
                            imageVector = SymbolsDemo.SettingsSymbols,
                            contentDescription = null,
                            tint = Light.ink,
                            modifier = Modifier.size(96.dp),
                        )
                        Text(
                            text = "Same frame, same concept, two drawings that " +
                                "do not belong to the same family.",
                            style = TextStyle(
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 12.sp,
                                color = Light.muted,
                            ),
                        )
                    }
                }
            }
        }
        onRoot().captureRoboImage("prototype/geometrie.png")
    }

    /**
     * Planche 6 — la Library, l'écran d'arrivée, dans les trois fonds.
     * Le champ de recherche est là (issue #173), la progression aussi (#172).
     */
    @Test
    fun bibliotheque() = runDesktopComposeUiTest(width = LARGEUR, height = 980) {
        setContent {
            Column(Modifier.size(LARGEUR.dp, 980.dp).background(Color(0xFF101010))) {
                TitrePlanche(
                    titre = "The Library — the landing screen",
                    sousTitre = "Two signals, no more: the language, by the vignette's " +
                        "colour, and the progress, by the rule under the title.",
                    papier = Color(0xFF101010),
                    encre = Color(0xFFF5F5F5),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    EcranBibliotheque(Light, EtatEcran.PLEIN)
                    EcranBibliotheque(Dark, EtatEcran.PLEIN)
                    EcranBibliotheque(Sepia, EtatEcran.PLEIN)
                }
            }
        }
        onRoot().captureRoboImage("prototype/library.png")
    }

    /**
     * Planche 7 — les trois états que la Library n'a pas aujourd'hui : vide,
     * en cours de lecture (celui qui manque, et qui fait clignoter « aucun
     * livre »), et en échec.
     */
    @Test
    fun etats() = runDesktopComposeUiTest(width = LARGEUR, height = 980) {
        setContent {
            Column(Modifier.size(LARGEUR.dp, 980.dp).background(Color(0xFF101010))) {
                TitrePlanche(
                    titre = "The Library — the three states",
                    sousTitre = "Today an empty vault and a vault still being read " +
                        "both show “no books yet”.",
                    papier = Color(0xFF101010),
                    encre = Color(0xFFF5F5F5),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    EcranBibliotheque(Light, EtatEcran.VIDE)
                    EcranBibliotheque(Light, EtatEcran.CHARGEMENT)
                    EcranBibliotheque(Light, EtatEcran.ERREUR)
                }
            }
        }
        onRoot().captureRoboImage("prototype/library-states.png")
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
                    titre = "The reader — the page, and nothing more",
                    sousTitre = "The chrome is two bands, 48 and 64 dp. Between them, " +
                        "only the text — until a finger touches it.",
                    papier = Color(0xFF101010),
                    encre = Color(0xFFF5F5F5),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    EcranLecteur(Light, chrome = true, selection = listOf("stood"))
                    EcranLecteur(Dark, chrome = true, selection = listOf("told"))
                    EcranLecteur(Sepia, chrome = true, selection = listOf("another"))
                }
            }
        }
        onRoot().captureRoboImage("prototype/reader.png")
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
                    titre = "The reader — the page, the zones, the text panel",
                    sousTitre = "Touching the middle third hides the chrome and brings back " +
                        "the bare page; the Aa panel sets the face, the size and the margins, " +
                        "and the page reflows in front of the reader.",
                    papier = Color(0xFF101010),
                    encre = Color(0xFFF5F5F5),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    EcranLecteur(Light, chrome = false)
                    EcranLecteur(Light, chrome = false, zones = true)
                    EcranLecteur(Light, chrome = true, panneau = true, selection = listOf("stood"))
                }
            }
        }
        onRoot().captureRoboImage("prototype/reader-states.png")
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
                    titre = "The word panel — the signature interaction",
                    sousTitre = "The page stays visible above, the touched word stays washed, and " +
                        "the panel never takes more than half the screen.",
                    papier = Color(0xFF101010),
                    encre = Color(0xFFF5F5F5),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    EcranRecherche(Light, EtatRecherche.MOT)
                    EcranRecherche(Dark, EtatRecherche.MOT)
                    EcranRecherche(Sepia, EtatRecherche.MOT)
                }
            }
        }
        onRoot().captureRoboImage("prototype/lookup.png")
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
                    titre = "The panel — when it cannot answer",
                    sousTitre = "A missing dictionary, a translated phrase, a phrase with no " +
                        "provider: three messages in the panel, never an alert.",
                    papier = Color(0xFF101010),
                    encre = Color(0xFFF5F5F5),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    EcranRecherche(Light, EtatRecherche.HORS_LIGNE)
                    EcranRecherche(Light, EtatRecherche.PHRASE_IA)
                    EcranRecherche(Light, EtatRecherche.PHRASE_SANS_IA)
                }
            }
        }
        onRoot().captureRoboImage("prototype/lookup-states.png")
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
                    titre = "The desktop reader — the context column",
                    sousTitre = "It is always there, so the page never moves when a " +
                        "word is touched. Closed, it shows the chapters; open, the word.",
                    papier = Color(0xFF101010),
                    encre = Color(0xFFF5F5F5),
                )
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    FenetreDesktop(Light, EtatDesktop.CHAPITRES)
                    FenetreDesktop(Light, EtatDesktop.MOT)
                }
            }
        }
        onRoot().captureRoboImage("prototype/desktop.png")
    }

    /** Planche 13 — le Vocabulary, dans les trois fonds. */
    @Test
    fun vocabulary() = runDesktopComposeUiTest(width = LARGEUR, height = 980) {
        setContent {
            Column(Modifier.size(LARGEUR.dp, 980.dp).background(Color(0xFF101010))) {
                TitrePlanche(
                    titre = "The Vocabulary — what you picked up while reading",
                    sousTitre = "The word keeps the book's serif; its translation and its " +
                        "context sentence stay in the sans. The bin is a quiet icon, and " +
                        "it asks for confirmation.",
                    papier = Color(0xFF101010),
                    encre = Color(0xFFF5F5F5),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    EcranVocabulary(Light, EtatVocabulary.PLEIN)
                    EcranVocabulary(Dark, EtatVocabulary.PLEIN)
                    EcranVocabulary(Sepia, EtatVocabulary.PLEIN)
                }
            }
        }
        onRoot().captureRoboImage("prototype/vocabulary.png")
    }

    /** Planche 14 — les états du Vocabulary : vide, sans résultat, et la suppression. */
    @Test
    fun vocabularyEtats() = runDesktopComposeUiTest(width = LARGEUR, height = 980) {
        setContent {
            Column(Modifier.size(LARGEUR.dp, 980.dp).background(Color(0xFF101010))) {
                TitrePlanche(
                    titre = "The Vocabulary — three states, one of which protects you",
                    sousTitre = "An empty vocabulary does not say the same thing as a search " +
                        "with no result; and deleting asks for confirmation, because that " +
                        "cannot be guessed.",
                    papier = Color(0xFF101010),
                    encre = Color(0xFFF5F5F5),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    EcranVocabulary(Light, EtatVocabulary.VIDE)
                    EcranVocabulary(Light, EtatVocabulary.SANS_RESULTAT)
                    EcranVocabulary(Light, EtatVocabulary.SUPPRESSION)
                }
            }
        }
        onRoot().captureRoboImage("prototype/vocabulary-states.png")
    }

    /**
     * Planche 15 — les Settings : un index, puis deux écrans qu'il ouvre.
     * L'apparence et les languages sont des entrées de Settings, pas des onglets.
     */
    @Test
    fun reglages() = runDesktopComposeUiTest(width = LARGEUR, height = 980) {
        setContent {
            Column(Modifier.size(LARGEUR.dp, 980.dp).background(Color(0xFF101010))) {
                TitrePlanche(
                    titre = "Settings — an index, not a form",
                    sousTitre = "Today the LLM provider and the WebDAV server are two " +
                        "forms in the middle of Settings. They become screens, and " +
                        "the index keeps only rows.",
                    papier = Color(0xFF101010),
                    encre = Color(0xFFF5F5F5),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    EcranReglages(Light)
                    EcranAppearance(Light)
                    EcranLanguages(Light)
                }
            }
        }
        onRoot().captureRoboImage("prototype/settings.png")
    }

    /**
     * Planche 16 — l'icône de l'application, et l'écran de lancement qu'Android
     * impose. Trois marques candidates, à leur taille réelle, puis le lancement
     * sur papier clair et sur nuit chaude (issue #177).
     */
    @Test
    fun icone() = runDesktopComposeUiTest(width = LARGEUR, height = 760) {
        setContent {
            Column(Modifier.size(LARGEUR.dp, 760.dp).background(Color(0xFF101010))) {
                TitrePlanche(
                    titre = "The icon — three marks, and Android's launch screen",
                    sousTitre = "Android 12 shows a launch screen: the icon on the window " +
                        "background. The only decision left is the mark — and the background.",
                    papier = Color(0xFF101010),
                    encre = Color(0xFFF5F5F5),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    Column(modifier = Modifier.width(600.dp)) {
                        Text(
                            text = "The three candidates, at 96 dp then at launcher size (48 dp)",
                            style = TextStyle(
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 12.sp,
                                color = Color(0xFFB9B2A6),
                            ),
                        )
                        Row(
                            modifier = Modifier.padding(top = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            MarqueAffichee(Marque.Lettre, 96)
                            MarqueAffichee(Marque.Livre, 96)
                            MarqueAffichee(Marque.Page, 96)
                        }
                        Row(
                            modifier = Modifier.padding(top = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            MarqueAffichee(Marque.Lettre, 48)
                            MarqueAffichee(Marque.Livre, 48)
                            MarqueAffichee(Marque.Page, 48)
                        }
                        Column(
                            modifier = Modifier.padding(top = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = "The letter — the reading serif, nothing else. " +
                                    "Legible at 48 dp, one monochrome layer.",
                                style = TextStyle(
                                    fontFamily = FontFamily.SansSerif,
                                    fontSize = 11.sp,
                                    color = Color(0xFFB9B2A6),
                                ),
                            )
                            Text(
                                text = "The book — the Lucide geometry of the rest of the app. Honest, but generic.",
                                style = TextStyle(
                                    fontFamily = FontFamily.SansSerif,
                                    fontSize = 11.sp,
                                    color = Color(0xFFB9B2A6),
                                ),
                            )
                            Text(
                                text = "The page — a sheet, with the mastery palette as its spine. " +
                                    "The most distinctive, the most fragile small.",
                                style = TextStyle(
                                    fontFamily = FontFamily.SansSerif,
                                    fontSize = 11.sp,
                                    color = Color(0xFFB9B2A6),
                                ),
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Launch, on light paper and on warm night",
                            style = TextStyle(
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 12.sp,
                                color = Color(0xFFB9B2A6),
                            ),
                        )
                        Row(
                            modifier = Modifier.padding(top = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Lancement(Light)
                            Lancement(Dark)
                        }
                    }
                }
            }
        }
        onRoot().captureRoboImage("prototype/icon.png")
    }

    /**
     * Planche 17 — les deux formulaires que Settings cachait : le fournisseur IA
     * et le coffre avec sa synchronisation.
     */
    @Test
    fun formulaires() = runDesktopComposeUiTest(width = LARGEUR, height = 980) {
        setContent {
            Column(Modifier.size(LARGEUR.dp, 980.dp).background(Color(0xFF101010))) {
                TitrePlanche(
                    titre = "The two forms — the key, and the server",
                    sousTitre = "Fields that read like a printed form: the label " +
                        "above, the value, a hairline. No wall of boxes.",
                    papier = Color(0xFF101010),
                    encre = Color(0xFFF5F5F5),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    EcranProvider(Light)
                    EcranCoffre(Light)
                }
            }
        }
        onRoot().captureRoboImage("prototype/settings-forms.png")
    }

    /**
     * Planche 18 — « le L dans le livre » n'est pas « le L dans la page ».
     * Les deux marques, à 96 dp et à 48 dp, puis le lancement.
     */
    @Test
    fun marque() = runDesktopComposeUiTest(width = LARGEUR, height = 640) {
        setContent {
            Column(Modifier.size(LARGEUR.dp, 640.dp).background(Color(0xFF101010))) {
                TitrePlanche(
                    titre = "The mark — the L in the book, or the L on the page",
                    sousTitre = "Two ways to combine the letter and the paper. The first is " +
                        "an open book; the second is a sheet with a coloured spine.",
                    papier = Color(0xFF101010),
                    encre = Color(0xFFF5F5F5),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(40.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column {
                        Text(
                            text = "The L in the book",
                            style = TextStyle(
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 12.sp,
                                color = Color(0xFFB9B2A6),
                            ),
                        )
                        Row(
                            modifier = Modifier.padding(top = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            MarqueV2Dessin(MarqueV2.Livre, 96)
                            MarqueV2Dessin(MarqueV2.Livre, 48)
                        }
                    }
                    Column {
                        Text(
                            text = "The L on the page",
                            style = TextStyle(
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 12.sp,
                                color = Color(0xFFB9B2A6),
                            ),
                        )
                        Row(
                            modifier = Modifier.padding(top = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            MarqueV2Dessin(MarqueV2.Page, 96)
                            MarqueV2Dessin(MarqueV2.Page, 48)
                        }
                    }
                    Column {
                        Text(
                            text = "Launch",
                            style = TextStyle(
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 12.sp,
                                color = Color(0xFFB9B2A6),
                            ),
                        )
                        Row(
                            modifier = Modifier.padding(top = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            LancementV2(Light, MarqueV2.Livre)
                            LancementV2(Dark, MarqueV2.Livre)
                        }
                    }
                }
            }
        }
        onRoot().captureRoboImage("prototype/icon-mark.png")
    }

    /**
     * Planche 19 — la décision : la page pour l'icône normale, le L seul pour la
     * couche monochrome et l'écran de lancement.
     */
    @Test
    fun iconeFinale() = runDesktopComposeUiTest(width = LARGEUR, height = 620) {
        setContent {
            Column(Modifier.size(LARGEUR.dp, 620.dp).background(Color(0xFF101010))) {
                TitrePlanche(
                    titre = "The icon — the page in colour, the L in monochrome",
                    sousTitre = "Android 13 tints the monochrome layer from the wallpaper's " +
                        "palette: one colour, so one letter. Book or page, a " +
                        "combined drawing would become a smudge.",
                    papier = Color(0xFF101010),
                    encre = Color(0xFFF5F5F5),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(40.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column {
                        Text(
                            text = "The icon — the page",
                            style = TextStyle(
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 12.sp,
                                color = Color(0xFFB9B2A6),
                            ),
                        )
                        Row(
                            modifier = Modifier.padding(top = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            MarqueV2Dessin(MarqueV2.Page, 96, avecLettre = false)
                            MarqueV2Dessin(MarqueV2.Page, 48, avecLettre = false)
                        }
                    }
                    Column {
                        Text(
                            text = "The monochrome layer — the L, in one colour",
                            style = TextStyle(
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 12.sp,
                                color = Color(0xFFB9B2A6),
                            ),
                        )
                        Row(
                            modifier = Modifier.padding(top = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            Monochrome(96)
                            Monochrome(48)
                        }
                    }
                    Column {
                        Text(
                            text = "Launch — the L on the theme's paper",
                            style = TextStyle(
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 12.sp,
                                color = Color(0xFFB9B2A6),
                            ),
                        )
                        Row(
                            modifier = Modifier.padding(top = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            LancementFinal(Light)
                            LancementFinal(Dark)
                        }
                    }
                }
            }
        }
        onRoot().captureRoboImage("prototype/icon-final.png")
    }
}

/** La couche monochrome telle que le système la teinte : une seule couleur, le L seul. */
@Composable
private fun Monochrome(taille: Int) {
    val d = taille.dp
    Box(
        modifier = Modifier
            .size(d)
            .background(Color(0xFF2B2B2B), RoundedCornerShape((taille * 0.22f).dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "L",
            style = TextStyle(
                fontFamily = FontFamily.Serif,
                fontSize = (taille * 0.7f).sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFB9B2A6),
            ),
        )
    }
}

/** Launch, avec la lettre seule. */
@Composable
private fun LancementFinal(theme: Theme) {
    Box(
        modifier = Modifier
            .width(230.dp)
            .height(300.dp)
            .background(theme.paper),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .background(theme.surface, RoundedCornerShape(19.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "L",
                style = TextStyle(
                    fontFamily = FontFamily.Serif,
                    fontSize = 54.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.ink,
                ),
            )
        }
    }
}

/** Les deux combinaisons de la lettre et du papier. */
private enum class MarqueV2 { Livre, Page }

/** Une marque à [taille], sur la tuile du lanceur. */
@Composable
private fun MarqueV2Dessin(marque: MarqueV2, taille: Int, avecLettre: Boolean = true) {
    val d = taille.dp
    Box(
        modifier = Modifier
            .size(d)
            .background(Light.paper, RoundedCornerShape((taille * 0.22f).dp)),
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = Modifier.size(d * 0.62f), contentAlignment = Alignment.Center) {
            when (marque) {
                MarqueV2.Livre -> {
                    Icon(
                        imageVector = LucideDemo.BookOpen,
                        contentDescription = null,
                        tint = Light.ink,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Text(
                        text = "L",
                        modifier = Modifier
                            .background(Light.paper, RoundedCornerShape(d * 0.05f))
                            .padding(horizontal = d * 0.03f),
                        style = TextStyle(
                            fontFamily = FontFamily.Serif,
                            fontSize = (taille * 0.3f).sp,
                            fontWeight = FontWeight.Bold,
                            color = Light.ink,
                        ),
                    )
                }

                MarqueV2.Page -> {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Column(modifier = Modifier.width(d * 0.11f).fillMaxHeight()) {
                            listOf(
                                Light.rampe().unknown,
                                Light.rampe().recognized,
                                Light.rampe().familiar,
                                Light.rampe().learned,
                            ).forEach { couleur ->
                                Box(modifier = Modifier.weight(1f).fillMaxWidth().background(couleur))
                            }
                        }
                        Box(
                            modifier = Modifier.weight(1f).fillMaxHeight().background(Color(0xFFF2ECE2)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (avecLettre) {
                                Text(
                                    text = "L",
                                    style = TextStyle(
                                        fontFamily = FontFamily.Serif,
                                        fontSize = (taille * 0.3f).sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Light.ink,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Launch, avec la marque retenue. */
@Composable
private fun LancementV2(theme: Theme, marque: MarqueV2) {
    Box(
        modifier = Modifier
            .width(280.dp)
            .height(360.dp)
            .background(theme.paper),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .background(theme.surface, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                when (marque) {
                    MarqueV2.Livre -> {
                        Icon(
                            imageVector = LucideDemo.BookOpen,
                            contentDescription = null,
                            tint = theme.ink,
                            modifier = Modifier.fillMaxSize(),
                        )
                        Text(
                            text = "L",
                            modifier = Modifier
                                .background(theme.surface, RoundedCornerShape(3.dp))
                                .padding(horizontal = 2.dp),
                            style = TextStyle(
                                fontFamily = FontFamily.Serif,
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Bold,
                                color = theme.ink,
                            ),
                        )
                    }

                    MarqueV2.Page -> Text(
                        text = "L",
                        style = TextStyle(
                            fontFamily = FontFamily.Serif,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.ink,
                        ),
                    )
                }
            }
        }
    }
}

/** L'écran du fournisseur IA : la clé, le modèle, et ce que l'app en fait. */
@Composable
private fun EcranProvider(theme: Theme) {
    Column(modifier = Modifier.width(390.dp).height(844.dp).background(theme.paper)) {
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            EnTeteEcran(theme, "LLM provider")
            Text(
                text = "A selected phrase can be translated by your provider. The key " +
                    "stays in this device's keychain; your books never leave the vault.",
                modifier = Modifier.padding(bottom = 10.dp),
                style = TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = theme.muted,
                ),
            )
            Champ(theme, "Provider", "Anthropic", action = null, chevron = true)
            Champ(theme, "API key", "••••••••••••••••", action = "Show")
            Champ(theme, "Model", "claude-sonnet-4", action = null, chevron = true)
            Text(
                text = "A key is stored · tested on 12 October",
                modifier = Modifier.padding(top = 10.dp),
                style = TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 11.sp,
                    color = theme.muted,
                ),
            )
            Column(modifier = Modifier.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BoutonPlein(theme, "Test connection")
                Text(
                    text = "Remove key",
                    style = TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFB3261E),
                    ),
                )
            }
        }
        BarreNavigation(theme, selection = 2)
    }
}

/** L'écran du coffre et de la synchronisation. */
@Composable
private fun EcranCoffre(theme: Theme) {
    Column(modifier = Modifier.width(390.dp).height(844.dp).background(theme.paper)) {
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            EnTeteEcran(theme, "Vault and sync")
            Text(
                text = "The vault lives in the app's private storage. It holds your " +
                    "books, your vocabulary and your positions.",
                modifier = Modifier.padding(bottom = 8.dp),
                style = TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = theme.muted,
                ),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BoutonContour(theme, "Export vault", Modifier.weight(1f))
                BoutonContour(theme, "Import", Modifier.weight(1f))
            }
            Text(
                text = "Sync",
                modifier = Modifier.padding(top = 20.dp, bottom = 2.dp),
                style = TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 11.sp,
                    color = theme.muted,
                ),
            )
            Text(
                text = "Synced 2 minutes ago · 3 devices",
                modifier = Modifier.padding(bottom = 6.dp),
                style = TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 13.sp,
                    color = theme.ink,
                ),
            )
            Champ(theme, "Server address", "cloud.example.com/dav", action = null)
            Champ(theme, "Username", "damien", action = null)
            Champ(theme, "Application password", "••••••••••", action = "Show")
            Column(modifier = Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BoutonPlein(theme, "Sync now")
                BoutonContour(theme, "Test connection", Modifier.fillMaxWidth())
                Text(
                    text = "Disconnect",
                    style = TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFB3261E),
                    ),
                )
            }
        }
        BarreNavigation(theme, selection = 2)
    }
}

/** Un champ de formulaire imprimé : le libellé, la valeur, un filet. */
@Composable
private fun Champ(theme: Theme, label: String, valeur: String, action: String?, chevron: Boolean = false) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 11.sp,
                    color = theme.muted,
                ),
            )
            Spacer(Modifier.weight(1f))
            if (action != null) {
                Text(
                    text = action,
                    style = TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = theme.ink,
                    ),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = valeur,
                modifier = Modifier.weight(1f),
                style = TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 15.sp,
                    color = theme.ink,
                ),
            )
            if (chevron) {
                Icon(
                    imageVector = LucideDemo.ChevronRight,
                    contentDescription = null,
                    tint = theme.muted,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(theme.outline))
    }
}

/** Le bouton d'action d'un écran : l'encre, pleine largeur. */
@Composable
private fun BoutonPlein(theme: Theme, label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.ink, RoundedCornerShape(8.dp))
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = theme.paper,
            ),
        )
    }
}

/** L'action secondaire : un contour, jamais une deuxième encre pleine. */
@Composable
private fun BoutonContour(theme: Theme, label: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .border(1.dp, theme.outline, RoundedCornerShape(8.dp))
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = theme.ink,
            ),
        )
    }
}

/** Les trois marques candidates (issue #177). */
private enum class Marque { Lettre, Livre, Page }

/** Une marque dessinée à [taille], dans une tuile de lanceur. */
@Composable
private fun MarqueAffichee(marque: Marque, taille: Int) {
    val d = taille.dp
    Box(
        modifier = Modifier
            .size(d)
            .background(Light.paper, RoundedCornerShape((taille * 0.22f).dp)),
        contentAlignment = Alignment.Center,
    ) {
        MarqueDessin(marque, (taille * 0.62f).dp)
    }
}

/** Le dessin de la marque, quelle que soit sa taille. */
@Composable
private fun MarqueDessin(marque: Marque, d: androidx.compose.ui.unit.Dp) {
    when (marque) {
        Marque.Lettre -> Text(
            text = "L",
            style = TextStyle(
                fontFamily = FontFamily.Serif,
                fontSize = (d.value * 1.1f).sp,
                fontWeight = FontWeight.Bold,
                color = Light.ink,
            ),
        )

        Marque.Livre -> Icon(
            imageVector = LucideDemo.BookOpen,
            contentDescription = null,
            tint = Light.ink,
            modifier = Modifier.size(d),
        )

        Marque.Page -> Row(modifier = Modifier.size(d * 0.8f, d)) {
            Column(modifier = Modifier.width(d * 0.18f).fillMaxHeight()) {
                listOf(
                    Light.rampe().unknown,
                    Light.rampe().recognized,
                    Light.rampe().familiar,
                    Light.rampe().learned,
                ).forEach { couleur ->
                    Box(modifier = Modifier.weight(1f).fillMaxWidth().background(couleur))
                }
            }
            Box(modifier = Modifier.weight(1f).fillMaxHeight().background(Color(0xFFF2ECE2)))
        }
    }
}

/** L'écran de lancement tel qu'Android le peint : le fond, puis l'icône au centre. */
@Composable
private fun Lancement(theme: Theme) {
    Column {
        Box(
            modifier = Modifier
                .width(300.dp)
                .height(380.dp)
                .background(theme.paper),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(theme.surface, RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "L",
                    style = TextStyle(
                        fontFamily = FontFamily.Serif,
                        fontSize = 62.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.ink,
                    ),
                )
            }
        }
        Text(
            text = theme.name,
            modifier = Modifier.padding(top = 6.dp),
            style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 11.sp,
                color = Color(0xFFB9B2A6),
            ),
        )
    }
}

/** L'écran des settings : un index, groupé par ce que chaque groupe sert. */
@Composable
private fun EcranReglages(theme: Theme) {
    Column(modifier = Modifier.width(390.dp).height(844.dp).background(theme.paper)) {
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Settings",
                modifier = Modifier.padding(bottom = 10.dp),
                style = TextStyle(
                    fontFamily = FontFamily.Serif,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.ink,
                ),
            )
            GroupeReglages(theme, "Reading")
            LigneReglage(theme, "Appearance", "Sépia")
            LigneReglagesSeparation(theme, derniere = false)
            LigneReglage(theme, "App language", "French")
            LigneReglagesSeparation(theme, derniere = false)
            LigneReglage(theme, "Reading languages", "English, Spanish")
            GroupeReglages(theme, "Data")
            LigneReglage(theme, "LLM provider", "Anthropic · configured")
            LigneReglagesSeparation(theme, derniere = false)
            LigneReglage(theme, "Vault and sync", "Synced 2 minutes ago")
            GroupeReglages(theme, "Application")
            LigneReglage(theme, "Attribution", null)
            LigneReglagesSeparation(theme, derniere = false)
            LigneReglage(theme, "About", "version 0.9")
        }
        BarreNavigation(theme, selection = 2)
    }
}

/** Un intitulé de groupe, discret, en bas de casse. */
@Composable
private fun GroupeReglages(theme: Theme, titre: String) {
    Text(
        text = titre,
        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
        style = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontSize = 11.sp,
            color = theme.muted,
        ),
    )
}

/** Une ligne d'index : le nom, la valeur courante, et rien de plus. */
@Composable
private fun LigneReglage(theme: Theme, titre: String, valeur: String?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = titre,
                style = TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 15.sp,
                    color = theme.ink,
                ),
            )
            if (valeur != null) {
                Text(
                    text = valeur,
                    style = TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 12.sp,
                        color = theme.muted,
                    ),
                )
            }
        }
        Icon(
            imageVector = LucideDemo.ChevronRight,
            contentDescription = null,
            tint = theme.muted,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** Le trait entre deux lignes d'index. */
@Composable
private fun LigneReglagesSeparation(theme: Theme, derniere: Boolean) {
    if (!derniere) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(theme.outline.copy(alpha = 0.6f)))
    }
}

/** L'écran Appearance : quatre choix, chacun montré sur son propre papier. */
@Composable
private fun EcranAppearance(theme: Theme) {
    Column(modifier = Modifier.width(390.dp).height(844.dp).background(theme.paper)) {
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            EnTeteEcran(theme, "Appearance")
            Text(
                text = "The theme recolours the whole app — never the book's text, " +
                    "which keeps its face, size and margins (ADR-0035).",
                modifier = Modifier.padding(bottom = 10.dp),
                style = TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = theme.muted,
                ),
            )
            ChoixTheme(theme, "System", "follows the device setting", ThemePreview.Systeme, choisi = false)
            ChoixTheme(theme, "Light", "warm paper, black ink", ThemePreview.Light, choisi = false)
            ChoixTheme(theme, "Dark", "warm night, light ink", ThemePreview.Dark, choisi = false)
            ChoixTheme(theme, "Sépia", "rag paper", ThemePreview.Sepia, choisi = true)
        }
        BarreNavigation(theme, selection = 2)
    }
}

/** Les quatre aperçus d'un thème, dessinés à leur vraie couleur. */
private enum class ThemePreview { Systeme, Light, Dark, Sepia }

/** Une ligne de l'écran Appearance : l'aperçu, le nom, et la coche. */
@Composable
private fun ChoixTheme(theme: Theme, nom: String, sousTitre: String, apercu: ThemePreview, choisi: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ApercuTheme(apercu)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = nom,
                style = TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 15.sp,
                    fontWeight = if (choisi) FontWeight.Medium else FontWeight.Normal,
                    color = theme.ink,
                ),
            )
            Text(
                text = sousTitre,
                style = TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 11.sp,
                    color = theme.muted,
                ),
            )
        }
        if (choisi) {
            Text(
                text = "✓",
                style = TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 16.sp,
                    color = theme.ink,
                ),
            )
        }
    }
}

/** L'aperçu : un rectangle du papier du thème, avec deux lignes d'encre. */
@Composable
private fun ApercuTheme(apercu: ThemePreview) {
    val clair = Light
    val sombre = Dark
    val sepia = Sepia
    Row(
        modifier = Modifier
            .size(48.dp, 34.dp)
            .background(clair.outline.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
            .padding(1.dp),
    ) {
        when (apercu) {
            ThemePreview.Systeme -> {
                ApercuMoitie(clair.paper, clair.ink, Modifier.weight(1f))
                ApercuMoitie(sombre.paper, sombre.ink, Modifier.weight(1f))
            }

            ThemePreview.Light -> ApercuMoitie(clair.paper, clair.ink, Modifier.weight(1f))

            ThemePreview.Dark -> ApercuMoitie(sombre.paper, sombre.ink, Modifier.weight(1f))

            ThemePreview.Sepia -> ApercuMoitie(sepia.paper, sepia.ink, Modifier.weight(1f))
        }
    }
}

/** Une moitié d'aperçu : le papier, et deux traits d'encre. */
@Composable
private fun ApercuMoitie(papier: Color, encre: Color, modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxHeight().background(papier, RoundedCornerShape(5.dp)),
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 6.dp)
                .fillMaxWidth()
                .height(2.dp)
                .background(encre.copy(alpha = 0.8f), RoundedCornerShape(1.dp)),
        )
        Box(
            modifier = Modifier
                .padding(start = 6.dp, end = 14.dp, top = 4.dp)
                .fillMaxWidth()
                .height(2.dp)
                .background(encre.copy(alpha = 0.35f), RoundedCornerShape(1.dp)),
        )
    }
}

/** L'écran Languages : une langue par ligne, son état, et un chevron. */
@Composable
private fun EcranLanguages(theme: Theme) {
    Column(modifier = Modifier.width(390.dp).height(844.dp).background(theme.paper)) {
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            EnTeteEcran(theme, "Languages")
            Text(
                text = "What the app can do for each language: place its words, and " +
                    "translate them into French.",
                modifier = Modifier.padding(bottom = 6.dp),
                style = TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = theme.muted,
                ),
            )
            LangueLigne(theme, "English", "Word difficulty · EN→FR dictionary", null)
            LangueLigne(theme, "Spanish", "Nothing installed yet", "Download 12 MB")
            LangueLigne(theme, "German", "Difficulty installed · no dictionary", "Download 9 MB")
            Text(
                text = "Attribution",
                modifier = Modifier.padding(top = 18.dp),
                style = TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = theme.ink,
                ),
            )
        }
        BarreNavigation(theme, selection = 2)
    }
}

/** Une langue : son nom, son état, et l'action quand il en manque une. */
@Composable
private fun LangueLigne(theme: Theme, nom: String, etat: String, action: String?) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = nom,
                    style = TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 15.sp,
                        color = theme.ink,
                    ),
                )
                Text(
                    text = etat,
                    style = TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 11.sp,
                        color = theme.muted,
                    ),
                )
            }
            if (action != null) {
                Text(
                    text = action,
                    style = TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = theme.ink,
                    ),
                )
            } else {
                Icon(
                    imageVector = LucideDemo.ChevronRight,
                    contentDescription = null,
                    tint = theme.muted,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(theme.outline.copy(alpha = 0.6f)))
    }
}

/** L'en-tête d'un écran qui se quitte : la flèche, puis le titre en sérif. */
@Composable
private fun EnTeteEcran(theme: Theme, titre: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = LucideDemo.ArrowLeft,
            contentDescription = null,
            tint = theme.ink,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = titre,
            style = TextStyle(
                fontFamily = FontFamily.Serif,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = theme.ink,
            ),
        )
    }
}

/** Les états du Vocabulary. */
private enum class EtatVocabulary { PLEIN, VIDE, SANS_RESULTAT, SUPPRESSION }

/** Une entrée du vocabulary, telle que la liste la donne. */
private data class MotVocabulary(val mot: String, val traduction: String, val contexte: String, val niveau: Mastery)

private val Vocabulary = listOf(
    MotVocabulary(
        "stood",
        "se tenir debout",
        "The fir tree stood in the wood and the children clapped their hands…",
        Mastery.UNKNOWN,
    ),
    MotVocabulary(
        "adorned",
        "orné, décoré",
        "looked forward with pleasure to being adorned again",
        Mastery.UNKNOWN,
    ),
    MotVocabulary(
        "Princess",
        "princesse",
        "who fell downstairs, and yet was raised to honour and married the Princess.",
        Mastery.RECOGNIZED,
    ),
    MotVocabulary(
        "another",
        "un autre",
        "Tell another! tell another! for they wanted to hear about Ivede-Avede",
        Mastery.FAMILIAR,
    ),
    MotVocabulary(
        "story",
        "histoire",
        "but they only got the story of Klumpey-Dumpey",
        Mastery.LEARNED,
    ),
)

/** Le nom français d'un niveau, tel que l'écran et le panneau le disent. */
private fun Mastery.nom(): String = when (this) {
    Mastery.UNKNOWN -> "Unknown"
    Mastery.RECOGNIZED -> "Recognized"
    Mastery.FAMILIAR -> "Familiar"
    Mastery.LEARNED -> "Learned"
}

/** L'écran du vocabulary, tel qu'un téléphone le montre. */
@Composable
private fun EcranVocabulary(theme: Theme, etat: EtatVocabulary) {
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
                        text = "Vocabulary",
                        style = TextStyle(
                            fontFamily = FontFamily.Serif,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.ink,
                        ),
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "128 words",
                        style = TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 12.sp,
                            color = theme.muted,
                        ),
                    )
                }
                if (etat == EtatVocabulary.PLEIN || etat == EtatVocabulary.SUPPRESSION) {
                    ChampRecherche(theme, placeholder = "Search")
                    Filtres(theme)
                    Column {
                        Vocabulary.forEach { entree ->
                            LigneMot(theme, entree)
                        }
                    }
                }
                when (etat) {
                    EtatVocabulary.VIDE -> {
                        ChampRecherche(theme, placeholder = "Search")
                        MessageCentre(
                            theme = theme,
                            titre = "No words saved yet",
                            corps = "Tap a word while reading, and it lands here.",
                        )
                    }

                    EtatVocabulary.SANS_RESULTAT -> {
                        ChampRecherche(theme, placeholder = "Search", query = "princesse")
                        Filtres(theme)
                        MessageCentre(
                            theme = theme,
                            titre = "No words match « princesse ».",
                            corps = "The search covers the word and its translation.",
                            lien = "Clear search",
                        )
                    }

                    else -> Unit
                }
            }
            BarreNavigation(theme, selection = 1)
        }
        if (etat == EtatVocabulary.SUPPRESSION) {
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
        Filtre(theme, "All levels", null, choisi = true)
        val rampe = theme.rampe()
        Filtre(theme, "Unknown", rampe.unknown, choisi = false)
        Filtre(theme, "Recognized", rampe.recognized, choisi = false)
        Filtre(theme, "Familiar", rampe.familiar, choisi = false)
        Filtre(theme, "Learned", rampe.learned, choisi = false)
        Filtre(theme, "Known", theme.outline, choisi = false)
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
private fun LigneMot(theme: Theme, entree: MotVocabulary) {
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
            text = "Delete « stood »?",
            style = TextStyle(
                fontFamily = FontFamily.Serif,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = theme.ink,
            ),
        )
        Text(
            text = "This removes the entry from your vocabulary. The word can be saved again " +
                "by tapping it in the book.",
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
                text = "Cancel",
                modifier = Modifier.padding(end = 20.dp),
                style = TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = theme.ink,
                ),
            )
            Text(
                text = "Delete",
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
                    EtatDesktop.CHAPITRES -> ColonneChapters(theme)
                    EtatDesktop.MOT -> ColonneMot(theme)
                }
            }
        }
        BarreBas(theme)
    }
}

/** Fermée, la colonne est le sommaire : ce que le lecteur peut vouloir rejoindre. */
@Composable
private fun ColonneChapters(theme: Theme) {
    Column(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "Chapters",
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
            text = "Page 99 of 357",
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
                    text = "verb · /stʊd/",
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
            text = "Mastery — Recognized",
            style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 12.sp,
                color = theme.muted,
            ),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val rampe = theme.rampe()
            ChipMastery(theme, "0", rampe.unknown, choisi = false)
            ChipMastery(theme, "1", rampe.recognized, choisi = true)
            ChipMastery(theme, "2", rampe.familiar, choisi = false)
            ChipMastery(theme, "3", rampe.learned, choisi = false)
            ChipMastery(theme, "4", theme.outline, choisi = false)
        }
        Separateur(theme)
        Text(
            text = "Look it up online",
            style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 12.sp,
                color = theme.muted,
            ),
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("WordReference", "Reverso", "Linguee", "Google Translate").forEach { lien ->
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
                        text = "verb · /stʊd/",
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
                        text = "The offline dictionary isn't installed on this device.",
                        style = TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = theme.ink,
                        ),
                    )
                    Text(
                        text = "The word can still be saved: it will be translated " +
                            "as soon as a dictionary is here.",
                        style = TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = theme.muted,
                        ),
                    )
                    Text(
                        text = "See Languages",
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
                        text = "Mastery",
                        style = TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 11.sp,
                            color = theme.muted,
                        ),
                    )
                    Text(
                        text = "Recognized",
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
                    ChipMastery(theme, "0", rampe.unknown, choisi = false)
                    ChipMastery(theme, "1", rampe.recognized, choisi = true)
                    ChipMastery(theme, "2", rampe.familiar, choisi = false)
                    ChipMastery(theme, "3", rampe.learned, choisi = false)
                    ChipMastery(theme, "4", theme.outline, choisi = false)
                }
            }
            Separateur(theme)
            Text(
                text = "WordReference · Reverso · Linguee · Google Translate",
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
private fun ChipMastery(theme: Theme, numero: String, fond: Color, choisi: Boolean) {
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
                        text = "Translated by your provider — 12 words sent",
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
                        text = "A phrase needs an AI to be translated.",
                        style = TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = theme.ink,
                        ),
                    )
                    Text(
                        text = "A word has its offline dictionary; a phrase has none.",
                        style = TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = theme.muted,
                        ),
                    )
                    Text(
                        text = "Set up a provider",
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
                text = "Google Translate",
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
    Mot("fir", Mastery.LEARNED),
    Mot("tree", null),
    Mot("stood", Mastery.UNKNOWN),
    Mot("in", null),
    Mot("the", null),
    Mot("wood", Mastery.FAMILIAR),
    Mot("and", null),
    Mot("the", null),
    Mot("children", null),
    Mot("clapped", Mastery.UNKNOWN),
    Mot("their", null),
    Mot("hands", null),
    Mot("and", null),
    Mot("cried", Mastery.RECOGNIZED),
    Mot("Tell", null),
    Mot("another!", Mastery.FAMILIAR),
    Mot("for", null),
    Mot("they", null),
    Mot("wanted", null),
    Mot("to", null),
    Mot("hear", null),
    Mot("about", null),
    Mot("Ivede-Avede;", Mastery.UNKNOWN),
    Mot("but", null),
    Mot("they", null),
    Mot("only", null),
    Mot("got", null),
    Mot("the", null),
    Mot("story", Mastery.LEARNED),
    Mot("of", null),
    Mot("Klumpey-Dumpey,", Mastery.UNKNOWN),
    Mot("who", null),
    Mot("fell", null),
    Mot("downstairs,", null),
    Mot("and", null),
    Mot("yet", null),
    Mot("was", null),
    Mot("raised", Mastery.RECOGNIZED),
    Mot("to", null),
    Mot("honour", Mastery.UNKNOWN),
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
    Mot("pleasure", Mastery.FAMILIAR),
    Mot("to", null),
    Mot("being", null),
    Mot("adorned", Mastery.UNKNOWN),
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
    Mot("fir", Mastery.LEARNED),
    Mot("tree", null),
    Mot("thought", Mastery.UNKNOWN),
    Mot("of", null),
    Mot("the", null),
    Mot("next", null),
    Mot("evening,", null),
    Mot("and", null),
    Mot("looked", null),
    Mot("forward", null),
    Mot("with", null),
    Mot("pleasure", Mastery.FAMILIAR),
    Mot("to", null),
    Mot("being", null),
    Mot("adorned", Mastery.UNKNOWN),
    Mot("again.", null),
    Mot("Yes,", null),
    Mot("so", null),
    Mot("it", null),
    Mot("happens", Mastery.UNKNOWN),
    Mot("in", null),
    Mot("the", null),
    Mot("world!", Mastery.RECOGNIZED),
    Mot("thought", Mastery.UNKNOWN),
    Mot("the", null),
    Mot("fir", Mastery.LEARNED),
    Mot("tree,", null),
    Mot("and", null),
    Mot("believed", Mastery.UNKNOWN),
    Mot("it", null),
    Mot("must", null),
    Mot("be", null),
    Mot("true,", null),
    Mot("because", null),
    Mot("it", null),
    Mot("was", null),
    Mot("such", null),
    Mot("a", null),
    Mot("nice", Mastery.RECOGNIZED),
    Mot("man", null),
    Mot("who", null),
    Mot("told", Mastery.UNKNOWN),
    Mot("it.", null),
    Mot("Well,", null),
    Mot("who", null),
    Mot("can", null),
    Mot("know?", Mastery.UNKNOWN),
    Mot("Perhaps", null),
    Mot("I", null),
    Mot("shall", null),
    Mot("fall", Mastery.RECOGNIZED),
    Mot("downstairs,", null),
    Mot("too,", null),
    Mot("and", null),
    Mot("marry", Mastery.UNKNOWN),
    Mot("a", null),
    Mot("Princess!", Mastery.FAMILIAR),
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
            text = "Page 99 of 357",
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
        ZoneTactile(theme, "Previous page", Modifier.weight(1f))
        ZoneTactile(theme, "Chrome", Modifier.weight(1f))
        ZoneTactile(theme, "Next page", Modifier.weight(1f))
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
                    text = "Text",
                    style = TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = theme.ink,
                    ),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "Close",
                    style = TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 13.sp,
                        color = theme.ink,
                    ),
                )
            }
            RowTexte(
                theme = theme,
                label = "Text face",
                options = listOf("Serif" to true, "Sans-serif" to false),
            )
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Text size",
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
                label = "Margins",
                options = listOf("Narrow" to false, "Comfortable" to true, "Wide" to false),
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

/** Un livre tel que la Library le montre — sans couverture, il n'y en a pas. */
private data class LivreListe(
    val titre: String,
    val code: String,
    val teinte: Color,
    val format: String,
    /** La part lue, ou `null` pour un livre jamais ouvert. */
    val part: Float?,
)

/** Les teintes de langue : mates, terreuses, hors du vocabulary de la maîtrise. */
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
                    text = "Library",
                    style = TextStyle(
                        fontFamily = FontFamily.Serif,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.ink,
                    ),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "Import",
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
private fun ChampRecherche(theme: Theme, placeholder: String = "Search", query: String? = null) {
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
                    ?: "${livre.format} · never opened",
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
            text = "No books yet",
            style = TextStyle(
                fontFamily = FontFamily.Serif,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = theme.ink,
            ),
        )
        Text(
            text = "An EPUB or a text file, and the first word is a minute away.",
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
                text = "Import a book",
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
            text = "This book could not be imported",
            style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = theme.ink,
            ),
        )
        Text(
            text = "The file may be protected. The library has not changed.",
            style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = theme.muted,
            ),
        )
        Text(
            text = "Close",
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
            Onglet(theme, LucideDemo.BookOpen, "Library", selection == 0)
            Onglet(theme, LucideDemo.Bookmark, "Vocabulary", selection == 1)
            Onglet(theme, LucideDemo.Settings, "Settings", selection == 2)
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
                        color = Light.ink,
                    ),
                )
                Text(
                    text = note,
                    style = TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = Light.muted,
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
                        tint = Light.ink,
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
                .background(Light.outline),
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
            text = "Ink at ${(alpha * 100).toInt()} % on the touched word — here « $mot »",
            style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 12.sp,
                color = theme.muted,
            ),
        )
        PageLave(theme, mot, alpha)
        Text(
            text = "With no selection, to compare",
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
private fun Traitement(titre: String, note: String, fond: Color? = null, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.width(300.dp).padding(end = 16.dp)) {
                Text(
                    text = titre,
                    style = TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Light.ink,
                    ),
                )
                Text(
                    text = note,
                    style = TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = Light.muted,
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
                .background(Light.outline),
        )
    }
}
