# The app theme is a device-local choice among three, and the system chooses between two

Lekto has no theme. One `MaterialTheme {}` is passed no colour scheme, so Material 3's light
defaults apply; the Android manifest pins `Theme.Material.Light.NoActionBar`; and the **Mastery
level** palette's four colours are tuned to a light background. Issue #151 settled the **Reading
preference** — the face, size, leading and margins of a book's text — and deliberately left the
theme out, because a theme recolours the whole app rather than the book's text, so it is not a
reading preference (ADR-0035). This settles it.

**Three themes, and the system chooses between two.** Lekto ships **Light**, **Dark** and
**Sepia**. Light and Dark exist because a reading app is opened in the evening as much as at
midday, and the platform already reports which one the reader wants. Sepia exists because the
identity's paper is warm and a reader may want it in daylight, but no operating system has a sepia
state — so Sepia is only ever chosen by hand. The control offers four entries, System / Light /
Dark / Sepia, and System resolves to Light or Dark, never to Sepia.

**It is app-private device configuration.** The choice is one JSON document through the
app-private byte seam, beside the **App language** and the **Reading preference** (ADR-0023,
ADR-0034, ADR-0035). It never enters the **Vault**, no export and no sync. The reason is sharper
here than for a font size: the theme's default *is* the platform's own setting, and a preference
that follows the device cannot be authored elsewhere. A reader who wanted their theme on a second
device would be asking that device's system setting to be overridden — which is what turning sync
on (off by default, ADR-0009) would then buy them, at the price of a second source of truth. We
rejected the **Vault** record for that reason, and for ADR-0035's: a theme is a choice about where
the reader is — a bright room, a dark one — rather than a fact about the reader, the way a
**Reader level** is. We rejected **Material You** dynamic colour for the opposite reason: an
identity drawn from the reader's wallpaper is no identity at all, and the mastery palette is the
one colour system this product means something by.

**The mastery palette is per theme, and it had to be measured.** The palette today is one table of
four colours tuned to a light background, and it fails WCAG AA as body text — a coloured word is
18 sp of running text, so the bar is 4.5:1, not 3:1. Against white, level 0 is 5.2:1 and passes;
level 1 is **1.5:1**, level 2 and level 3 are **2.3:1**, all failing. The inversion decides the
rest: against a near-black background, level 0's blue falls to **3.6:1** and fails while the others
climb past 8:1. No single table serves both backgrounds, so the palette becomes a **per-theme
ramp** — four hues chosen against each theme's own surface, each measured at or above 4.5:1. The
hues keep their meanings (blue unknown, amber recognized, orange familiar, green learned); what
changes is their darkness. Level 4 stays colourless, because "known" is normal text. The
non-colour indicator stays a single underline: it says "this word is one you are learning" to a
reader who cannot read the hue, and a distinct shape per level would need a custom
`TextDecoration` renderer that Compose does not offer.

**The window background follows the theme.** Android paints the window background before the first
frame, and on Android 12 and later it draws a system launch screen from that background and the
app's icon. Pinned to a light platform theme, a dark-theme launch would flash white. The
application theme therefore stops pinning Light, and the window background becomes the active
theme's paper. This is also where the app's mark first appears, so the icon and the launch screen
are drawn together (issue #177).

**Consequences**: a `LektoTheme` composable replaces the bare `MaterialTheme`, holding one
`ColorScheme` per theme plus the shapes and typography the identity settles; `MasteryPalette`
stops being a single table and becomes a lookup by theme; the Android manifest loses its fixed
light theme; the golden lane records one swatch sheet per theme rather than one for the app, and
the existing `theme-colours.png` golden changes with it; and `CONTEXT.md` gains **App theme**.
A change of theme repaints the open screen at once with no toast, the way a **Reader level** change
recolours the text (ADR-0030).

**Not decided here**: the identity itself — the palette's neutral values, the accent policy, the
type scale, the corner radii and the icon set. Those are reversible choices about how the app
looks rather than a contract the app must keep, so they live in `docs/design.md` and in
`app/src/commonMain/kotlin/app/lekto/theme/`, not in an ADR. The one rule this ADR does fix is
that the mastery ramps are measured against their own background, because an inaccessible palette
is a defect rather than a taste.
