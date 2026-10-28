# Icons for Lekto: one coherent stroke set, and how to get it into Compose

**Date:** 2026-10-18
**Scope:** Choose how Lekto obtains a small, coherent icon set for its Compose
Multiplatform app (Android first, Compose/JVM desktop second). The target
geometry is Lucide's: a 24 × 24 grid, a 2 px stroke, round caps and round joins.
The set is about ten icons — library, vocabulary, languages, settings, import,
back, search, listen (text-to-speech), the "Aa" panel, close, delete.
**Constraints:** Compose Multiplatform 1.11.1 and Material 3 1.11.0-alpha07
(`gradle/libs.versions.toml`); every Gradle dependency, test-scope included, must
be AGPL-3.0-compatible and pass `./gradlew checkDependencyLicences`
([ADR-0011](../adr/0011-agpl3-with-separate-ccbysa-pack.md),
[`config/dependency-licences.txt`](../../config/dependency-licences.txt)). Today the
app declares no icon dependency, has no `composeResources` directory and bundles
no font.
**Method:** Primary sources only — Maven Central metadata and POMs, the Compose
Multiplatform changelog and Gradle-plugin source, JetBrains and Google
documentation, the icon projects' own repositories and licence files, the FSF
licence list, and a real conversion run of the ten icons with the Valkyrie CLI.
Versions and artefacts were read on **2026-10-18**. Claims I could not verify are
gathered in [§10](#10-unverified-and-flagged).

> **Headline:** None of the icon artefacts shipped with Compose is a good fit.
> `material-icons-core` and `material-icons-extended` are frozen at **1.7.3**
> (December 2024), removed from Material 3's transitive graph in Compose
> Multiplatform 1.8, and explicitly deprecated in the 1.11.1 Gradle plugin; the
> `extended` desktop jar is **37.8 MB** and **11,105 classes** for a set we do not
> want. Lucide has **no official Kotlin or Compose package**. The clean answer is
> therefore to **generate the ten Lucide SVGs once into `ImageVector` Kotlin with
> Valkyrie's CLI, commit the generated files, and add no dependency at all**.
> Measured: one command, **637 lines / 22.7 KB**, ktlint-clean, and the licence
> obligation is a notice file (ISC, plus MIT for the Feather-derived icons).
> Keep Material Symbols XML + Compose Resources as the "no code generation,
> Google's set" alternative, and the prebuilt `com.composables:icons-lucide`
> artefact as the "no conversion, whole pack" alternative.

---

## 0. What the app actually needs

The ten-or-so destinations map to current Lucide names as follows. Two names in
the brief no longer exist in Lucide: `trash-2` was folded into `trash`, and
`book-marked` was removed (both return 404 on `main`; verified 2026-10-18). Names
change, so pin a Lucide tag and record it ([§9.1](#91-generate-the-icons)).

| UI need | Lucide file | Note |
|---|---|---|
| Library | `library` | verified present |
| Vocabulary | `notebook-pen` (or `book-open-text`) | `book-marked` is gone |
| Languages | `languages` | |
| Settings | `settings` | |
| Import | `import` | |
| Back | `arrow-left` | directional: enable `autoMirror` |
| Search | `search` | Feather-derived (MIT arm) |
| Listen / TTS | `volume-2` (or `audio-lines`) | |
| "Aa" panel | `a-large-small` | |
| Close | `x` | Feather-derived (MIT arm) |
| Delete | `trash` | Feather-derived; `trash-2` removed |

Every Lucide file is a stroke drawing, not a filled one. The actual header of
`icons/library.svg` on `main` is:

```svg
<svg
  xmlns="http://www.w3.org/2000/svg"
  width="24"
  height="24"
  viewBox="0 0 24 24"
  fill="none"
  stroke="currentColor"
  stroke-width="2"
  stroke-linecap="round"
  stroke-linejoin="round"
>
```

That header is the geometry the brief asks for, and Lucide's own specification
makes it normative: a 24 × 24 canvas, strokes "**must** be 2 pixels wide", round
line joins, round caps on open paths, strokes centred on their paths
([Lucide design specification](https://lucide.dev/contribute/icons/specification)).
There are **1,891** icons on `main` today (counted from the repository tree).

---

## 1. `material-icons-core` and `material-icons-extended`

### 1.1 Do they still exist in 1.11.x?

No. Maven Central's metadata for both artefacts ends at **1.7.3**, published
**2024-12-19**:

- [`material-icons-extended/maven-metadata.xml`](https://repo1.maven.org/maven2/org/jetbrains/compose/material/material-icons-extended/maven-metadata.xml)
  — `<latest>1.7.3</latest>`, `lastUpdated 20241219232549`.
- [`material-icons-core/maven-metadata.xml`](https://repo1.maven.org/maven2/org/jetbrains/compose/material/material-icons-core/maven-metadata.xml)
  — same latest and date.

They did not disappear in one step; they were detached and then deprecated:

1. **Compose Multiplatform 1.8.0** removed the implicit dependency:
   "material/material3 libraries no longer add a dependency to
   `material-icons-core` so if your project relied on that, you will have to
   explicitly add that dependency"
   ([CMP changelog, 1.8.0 migration notes](https://github.com/JetBrains/compose-multiplatform/blob/master/CHANGELOG.md)).
   The 1.8.2 "what's new" page repeats it and points at Material Symbols as the
   replacement
   ([What's new in CMP 1.8.2](https://kotlinlang.org/docs/multiplatform/whats-new-compose-180.html)).
2. **Compose Multiplatform 1.10.1** improved the deprecation message to say the
   artefact "is pinned to version `1.7.3`" (changelog, 1.10.1).
3. **The 1.11.1 Gradle plugin source** hard-codes the pin and the deprecation:

   ```kotlin
   @Deprecated(
       "This artifact is pinned to version 1.7.3 and will not receive updates. " +
           "Either use this version explicitly or migrate to Material Symbols (vector resources). " +
           "See https://kotlinlang.org/docs/multiplatform/whats-new-compose-180.html",
       replaceWith = ReplaceWith("\"org.jetbrains.compose.material:material-icons-extended:1.7.3\"")
   )
   val materialIconsExtended get() = "org.jetbrains.compose.material:material-icons-extended:1.7.3"
   ```

   ([`ComposePlugin.kt`, tag `v1.11.1`](https://github.com/JetBrains/compose-multiplatform/blob/v1.11.1/gradle-plugins/compose/src/main/kotlin/org/jetbrains/compose/ComposePlugin.kt))

So `compose.materialIconsExtended` still *resolves* under 1.11.1 — but it resolves a
1.7.3 artefact built against Compose 1.7.3, not a member of the 1.11.1 release
train. Google's own guidance for the underlying library is blunter: it "is no
longer maintained or recommended for use in your apps, as it contains an older
look and feel and can also increase the build time of your apps *significantly*"
([Android Developers, Material icons](https://developer.android.com/develop/ui/compose/graphics/images/material)).

### 1.2 Weight

| Artefact | Desktop jar | Contents |
|---|---:|---|
| `material-icons-extended-desktop:1.7.3` | **37,768,805 B (37.8 MB)** | **11,105 classes** |
| `material-icons-core-desktop:1.7.3` | 878,084 B (878 KB) | **49 icons** |
| `material-icons-extended:1.7.3` sources | 12,085,967 B (12.1 MB) | generated source |

Measured from the published jars. The `extended` set is genuinely enormous — one
class per icon, thousands of them. Release builds can shrink unused icons with
R8, but debug builds, dexing, IDE indexing and build time pay for all of it; that
is exactly the cost Google's documentation calls out. `core` is small but only 49
icons: `ArrowBack`, `Search`, `Settings`, `Close`, `Delete`, `List`, `Home` are
there, but `Languages`, `VolumeUp`, `TextFields` and an import/download icon are
not.

### 1.3 Licence and KMP support

- **Licence:** Apache-2.0 for both artefacts (their POMs declare "The Apache
  Software License, Version 2.0"), so AGPL-3.0-compatible and already on the
  allowlist.
- **KMP:** the 1.7.3 publication has Android, desktop/JVM, iOS, JS and Wasm
  variants, so it does technically work on Android + desktop. That is not the
  problem.

### 1.4 Verdict

**Rejected.** The style is Material's filled geometry, not Lucide's 2 px stroke;
the artefacts have been frozen since December 2024; and `extended` costs 37.8 MB
for ten icons. `core` is defensible in size but does not contain the set, and
still misses the requested geometry.

---

## 2. Material Symbols as vector resources

Material Symbols is Google's current set; Material Icons is "the classic set, but
no longer updated" ([material-design-icons README](https://github.com/google/material-design-icons)).
The repository's licence is **Apache-2.0**
([LICENSE](https://github.com/google/material-design-icons/blob/master/LICENSE)),
so it is AGPL-compatible.

The delivery format that matters here is the Android vector XML. A real file from
the repository,
[`symbols/android/settings/materialsymbolsoutlined/settings_24px.xml`](https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android/settings/materialsymbolsoutlined/settings_24px.xml),
shows the shape:

```xml
<vector android:width="24dp" android:height="24dp"
    android:viewportWidth="960" android:viewportHeight="960"
    android:tint="?attr/colorControlNormal">
  <path android:fillColor="@android:color/white" android:pathData="M370,880L354,752…"/>
</vector>
```

Three consequences:

- the viewport is **960**, not 24 — the geometry is not on Lucide's grid;
- the glyph is a **single filled path**, with no stroke, cap or join to share
  with Lucide;
- `android:tint="?attr/colorControlNormal"` is an Android theme attribute that
  Compose Multiplatform cannot resolve, so it must be removed and the fill set to
  `#000000` — exactly the three-step edit JetBrains documents
  ([Using multiplatform resources, "Icons"](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-multiplatform-resources-usage.html)).

JetBrains explicitly recommends this route as the migration from Material Icons
("You can also use the vector Android XML icons from the Material Symbols
library", [CMP 1.8.2 notes](https://kotlinlang.org/docs/multiplatform/whats-new-compose-180.html)),
and the XML dialect is documented as Android's, "except that they don't support
external references to Android resources" (same resources page).

**Cost:** one XML per icon (roughly 1–2 KB each), the `components-resources`
runtime (228 KB desktop jar / 192 KB Android AAR), a generated `Res` class, and
`painterResource`/`vectorResource` at the call site. No Kotlin is generated for
the icons themselves.
**Verdict:** the best option **if Lucide's geometry is negotiable**. It is
official, Apache-2.0, and fully portable across Android and desktop — but it
produces filled 960-unit glyphs, so mixing it with Lucide would look wrong.

---

## 3. Lucide

### 3.1 Geometry and set

Covered in [§0](#0-what-the-app-actually-needs): 24 × 24, 2 px, round caps and
joins, 1,891 icons on `main`, and the ten requested names exist except the two
renamed ones.

### 3.2 Is there an official Kotlin/Compose package?

**No.** Lucide's own package page lists JavaScript, React, Vue, Svelte, Solid,
React Native, Angular, Preact, Astro, `lucide-static` — and third-party ports for
Laravel, Hyvä, Eleventy, Nuxt, Lustre, Flutter, Slint, Go, Rails, jte. There is no
Kotlin, JVM or Compose entry
([lucide.dev/packages](https://lucide.dev/packages)). The repository's source of
truth is the `icons/*.svg` directory, from which every package is generated
([CONTRIBUTING.md](https://github.com/lucide-icons/lucide/blob/main/CONTRIBUTING.md)).

### 3.3 Community ports, and what they are worth

| Artefact / repo | Latest | KMP? | Weight | Licence (declared) | State |
|---|---|---|---|---|---|
| [`com.composables:icons-lucide`](https://repo1.maven.org/maven2/com/composables/icons-lucide/1.1.0/) | **1.1.0**, Dec 2024 | yes: Android, JVM, iOS, JS, Wasm | Android AAR **4.9 MB**, JVM jar **5.2 MB**, **1,522** Kotlin files | MIT (POM) | repo [`composablehorizons/compose-icons`](https://github.com/composablehorizons/compose-icons), 322★, last push Jan 2026; built against CMP 1.6.11 |
| [`io.github.thelacspace:lucide-compose`](https://repo1.maven.org/maven2/io/github/thelacspace/lucide-compose/maven-metadata.xml) | 1.16.0, May 2026 | claims Android/JVM/iOS | JVM jar **27 KB**, **8 Kotlin files** | Apache-2.0 | single release; the published JVM artefact contains only a handful of icons — not a usable set |
| [`ShermanTsang/lucide-icon-kmp`](https://github.com/ShermanTsang/lucide-icon-kmp) | no release seen | claims KMP | — | MIT | 1★, personal |
| [`YumeYucca/lucide-compose`](https://github.com/YumeYucca/lucide-compose) | no release seen | claims KMP | — | ISC | 1★, personal |
| [`DevSrSouza/compose-icons`](https://github.com/DevSrSouza/compose-icons) | 854★ | yes | — | MIT | last push Sept 2024; ships **Feather** (Lucide's ancestor) and Material, not current Lucide |

The honest reading: **there is no maintained, production-grade Lucide port**. The
only artefact with a coherent publication is `com.composables`, and it is a
frozen December-2024 snapshot of the whole set — 5 MB to get ten icons. The
`thelacspace` publication looks broken on the JVM target (27 KB, 8 Kotlin files
for a "1.16.0" release). The one- and two-star ports have no release history to
judge.

### 3.4 Converting a Lucide SVG to a Compose `ImageVector`

This is the path that gives exactly the requested geometry with no dependency.

**The tool: Valkyrie.** ComposeGears' Valkyrie converts SVG/XML to
`ImageVector`, ships an IntelliJ/Android Studio plugin, a CLI and a Gradle plugin,
is Apache-2.0, had 901 stars and a push on 2026-10-18
([README](https://github.com/ComposeGears/Valkyrie)). Its SVG parser understands
`stroke`, `stroke-width`, `stroke-linecap` and `stroke-linejoin` — the parser's
`SVGStroke` type maps `butt/round/square` and `bevel/miter/round`
([`SVGStroke.kt`](https://github.com/ComposeGears/Valkyrie/blob/main/components/parser/kmp/svg/src/commonMain/kotlin/io/github/composegears/valkyrie/parser/kmp/svg/SVGStroke.kt)),
so Lucide's strokes are preserved as strokes rather than flattened to fills.

**Do not use Valkyrie's Gradle plugin in this repo.** Its compatibility table
requires **AGP 9.0.0 / Gradle 9.0.0** from plugin 0.4.0 onward, and 0.6.0 wants
AGP 9.4 / Gradle 9.6. Lekto pins **AGP 8.13.2 and Gradle 8.14.4**. Use the CLI
(a plain JVM application; the repo already has JDK 21) or the IDE plugin.

**Measured run (2026-10-18).** I downloaded the ten SVGs from Lucide's `main`
branch (tag `1.59.0` resolves the same paths), ran `valkyrie-cli 1.2.1` with
`--iconpack-name LektoIcons --trailing-comma=true`, and measured the result:

- **10 icons converted**, exit 0;
- **637 lines**, **22,661 bytes** of Kotlin;
- one file per icon, 47–85 lines each;
- `ktlint 1.8.0` with this repository's `.editorconfig` reports **no violations**
  (the `--trailing-comma=true` flag matters; without it, ktlint reports five
  `trailing-comma-on-call-site` errors on `Library.kt`, and the pattern recurs in
  every icon).

The generated code keeps the stroke exactly:

```kotlin
val LektoIcons.Library: ImageVector
    get() {
        if (_Library != null) return _Library!!
        _Library = ImageVector.Builder(
            name = "Library",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveToRelative(16f, 6f)
                lineToRelative(4f, 14f)
            }
            // … the other paths
        }.build()
        return _Library!!
    }

@Suppress("ObjectPropertyName")
private var _Library: ImageVector? = null
```

**Alternative converter:** `rafaeltonholo/svg-to-compose` (MIT, 222★, active
Sept 2026, CLI + Gradle plugin) does the same job
([repo](https://github.com/rafaeltonholo/svg-to-compose)). Valkyrie is preferred
here because its stroke handling and its icon-pack mode are documented and the
run above is reproducible.

**A second route with the same tool:** point Valkyrie at Material Symbols' SVGs.
That produces `ImageVector` code instead of resource XML, so you keep the
compile-time artefact and drop the resources dependency. It does not fix the
filled 960-unit geometry.

### 3.5 Licence and obligations

Lucide's repository licence is **ISC**, with an explicit **MIT** carve-out for the
icons inherited from Feather:

- ISC: "Permission to use, copy, modify, and/or distribute this software for any
  purpose with or without fee is hereby granted, provided that the above copyright
  notice and this permission notice appear in all copies."
  Copyright © 2026 Lucide Icons and Contributors.
- MIT: "The following Lucide icons are derived from the Feather project: …" —
  the list includes **`arrow-left`, `search`, `trash`, `x`** — Copyright ©
  2013-present Cole Bemis.
  ([LICENSE](https://github.com/lucide-icons/lucide/blob/main/LICENSE))

Both are GPL-compatible: the FSF lists **ISC** as "a free software license, and
compatible with the GNU GPL" and **Expat/MIT** as "compatible with the GNU GPL"
([GNU licence list — ISC](https://www.gnu.org/licenses/license-list.html#ISC),
[Expat](https://www.gnu.org/licenses/license-list.html#Expat)). Apache-2.0, used by
the other options, is "compatible with version 3 of the GNU GPL"
([Apache-2.0 entry](https://www.gnu.org/licenses/license-list.html#apache2)).

**Practical obligation:** because the generated Kotlin embeds the path data, it is
a copy of the icons. Ship the ISC notice **and** the Feather/MIT notice (for the
Feather-derived icons among those used) in a `THIRD-PARTY-NOTICES.md` at the repo
root, next to the AGPL `LICENSE`. Keep the source SVGs and the Lucide tag in
`tools/icons/` so the provenance is auditable. This is a documentation obligation,
not a code obligation: ISC and MIT do not reach the AGPL code.

**Blind spot in the gate:** `checkDependencyLicences` reads Gradle module
metadata. Icons copied into the tree are not dependencies, so the gate will not
see them; the notice file and the review are the only control.

---

## 4. Other stroke-consistent sets

| Set | Licence | Grid / stroke | Size | Official Compose port | Fit for Lekto |
|---|---|---|---|---|---|
| **Lucide** | ISC (+ MIT subset) | 24 / 2 / round | 1,891 | no | exact target |
| **Tabler** | **MIT** ([LICENSE](https://github.com/tabler/tabler-icons/blob/main/LICENSE)) | **24 / 2 / round** | **6,238** (5,184 outline) | no | drop-in substitute for the same pipeline |
| **Phosphor** | MIT ([LICENSE](https://github.com/phosphor-icons/core/blob/main/LICENSE)) | **256, filled**, 6 weights | six weights per icon | no official; scattered 1–31★ ports | different geometry; reject |
| **Iconoir** | MIT ([LICENSE](https://github.com/iconoir-icons/iconoir/blob/main/LICENSE)) | 24 / **1.5** / round | 1,600+ | no official; one 0★ port | close, but stroke is 1.5 |
| **Remix Icon** | **custom "Remix Icon License v1.0", Jan 2026** ([License](https://github.com/Remix-Design/RemixIcon/blob/master/License)) | 24 / outlined + filled | 3,200+ | no | **excluded — see below** |
| **Feather** | MIT | 24 / 2 / round | frozen set | `DevSrSouza/compose-icons` (stale) | ancestor of Lucide; use Lucide |

Notes that matter:

- **Tabler** is the strongest alternative source. Its README states "Each icon is
  designed on a 24x24 grid with a 2px stroke" — the same geometry as Lucide — and
  the licence is plain MIT, with no Feather carve-out. It plugs into the exact
  same Valkyrie command. The only reason to prefer Lucide is the specific glyph
  vocabulary (Lucide's `a-large-small`, `library`, `volume-2` are closer to the
  product's needs).
- **Phosphor** is not a stroke-2 set: `assets/regular/house.svg` is
  `<svg viewBox="0 0 256 256" fill="currentColor">` — filled geometry on a 256
  grid. Mixing it with Lucide would be visibly inconsistent.
- **Iconoir** is on a 24 grid but uses `stroke-width="1.5"` in its regular set
  (verified in `icons/regular/home.svg`). It is a coherent alternative on its own,
  not a mix-and-match source.
- **Remix Icon is the trap in the brief.** The prompt's premise ("Remix Icon,
  Apache-2.0") is out of date: the repository's `License` file is now a custom
  **Remix Icon License v1.0 (January 2026)** with restrictions on standalone icon
  sales and on creating competing icon libraries, and a section that says the
  licence "may be incompatible with licenses that require … mandatory application
  of strong copyleft or share-alike terms". The npm registry metadata **still says
  Apache-2.0** ([registry](https://registry.npmjs.org/remixicon/latest)), so two
  primary sources disagree. The safe reading is the repository's licence, and it
  is **not on Lekto's allowlist**; treat Remix as incompatible until a lawyer says
  otherwise.

**Verdict:** Tabler is the only set that can replace Lucide without changing the
pipeline or the geometry. Phosphor and Iconoir change the stroke; Remix fails the
licence test.

---

## 5. Compose Resources in 1.11.1: exact plumbing

This is the mechanism that makes Material Symbols (or any Android XML vector)
available identically on Android and desktop.

### 5.1 What to add

The official setup page says to add the resources library to `commonMain` and to
create `composeResources` in the source set
([Setup and configuration](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-multiplatform-resources-setup.html)):

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(compose.components.resources)
        }
    }
}
```

In **1.11.1 that accessor is itself deprecated**. The plugin source marks it
`@Deprecated("Specify dependency directly", …)` and rewrites it to
`org.jetbrains.compose.components:components-resources:${composeVersion}`
([`ComposePlugin.kt`, `CommonComponentsDependencies`](https://github.com/JetBrains/compose-multiplatform/blob/v1.11.1/gradle-plugins/compose/src/main/kotlin/org/jetbrains/compose/ComposePlugin.kt)).
Since this repo pins every dependency in the version catalogue, the honest
plumbing is:

```toml
# gradle/libs.versions.toml
compose-components-resources = { module = "org.jetbrains.compose.components:components-resources", version.ref = "compose-multiplatform" }
```

```kotlin
// app/build.gradle.kts
commonMain.dependencies {
    implementation(libs.compose.components.resources)
}
```

The artefact is Apache-2.0 (POM), so it passes the licence gate. Its runtime is
small: **228,082 B** for the desktop jar and **191,620 B** for the Android AAR
(measured, 1.11.1).

### 5.2 Directory and accessors

```
app/src/commonMain/composeResources/
└── drawable/
    ├── ic_library.xml
    ├── ic_search.xml
    └── …
```

Building generates a `Res` class. The default package is
`{group}.{module}.generated.resources`; because this root project sets no `group`,
name it explicitly to avoid surprises:

```kotlin
// app/build.gradle.kts
compose.resources {
    packageOfResClass = "app.lekto.resources"
}
```

Usage:

```kotlin
import app.lekto.resources.Res
import app.lekto.resources.ic_library
import org.jetbrains.compose.resources.painterResource

Icon(painter = painterResource(Res.drawable.ic_library), contentDescription = null)
```

`vectorResource(Res.drawable.x)` returns an `ImageVector` when you need one, and
`painterResource` returns a `Painter` (a `VectorPainter` for XML vectors)
([Using multiplatform resources](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-multiplatform-resources-usage.html)).

### 5.3 Limits and cross-platform behaviour

- **SVG is not an option for an Android-first app:** "SVG images are supported on
  all platforms **except Android**" (same page). XML vector is the portable
  format.
- **One XML, both targets:** JetBrains documents the XML as Android's own vector
  format "except that they don't support external references to Android
  resources", and gives no platform-specific caveat for XML vectors. The same
  `painterResource(Res.drawable.…)` call is common code, so Android and desktop
  load the same file through the same API. This is format parity as documented,
  not a pixel-diff I ran.
- **Runtime cost:** the XML is decoded when loaded, unlike an `ImageVector`
  compiled into the app. For a dozen 24 dp icons this is negligible; for hundreds
  it would be worth measuring.
- **Gradle/IDE coupling:** accessors are regenerated on build; a renamed file is a
  compile error, which is a feature.
- **Platform previews:** resources are packed into Android assets since 1.7, which
  is what makes Android Studio previews work (setup page).

---

## 6. Hand-written `ImageVector`s

Writing the paths by hand is perfectly viable for ten icons, and it is what the
generated code effectively is. The cost is real but bounded:

- **Size:** roughly 45–85 lines per icon with the builder DSL and stroke
  parameters (measured on the generator output), so ~500–700 lines for the set;
  more if you use `PathData` strings, fewer if you drop the backing-property
  pattern.
- **Correctness:** you are transcribing Bézier control points from SVG. One wrong
  digit is a subtly deformed icon that compiles and renders; the failure is visual,
  so it needs a golden test or a human eye.
- **Maintenance:** updating Lucide means re-transcribing. A generator makes the
  update a one-command diff.
- **Rendering:** identical to the generated route — both are the same
  `ImageVector`, drawn by Compose on Android and desktop.

**Verdict:** a valid fallback if no tool may be added to the contributor workflow,
but it is strictly more work and more error-prone than running Valkyrie once.

---

## 7. Cost matrix

Legend: ✅ good · 🟡 workable with the stated cost · ❌ fails a hard requirement.

| Option | Licence / AGPL | Available for CMP 1.11.1 | Added dependency | Weight for 10 icons | Code / plumbing | Maintenance risk | Effort |
|---|---|---|---|---|---|---|---|
| `material-icons-extended` (pinned 1.7.3) | Apache-2.0 ✅ | 🟡 resolves, outside the 1.11 train | 1 line | **37.8 MB**, 11,105 classes | none | frozen since Dec 2024; style mismatch | 5 min |
| `material-icons-core` (1.7.3) | Apache-2.0 ✅ | 🟡 same | 1 line | 878 KB, 49 icons | none | frozen; **missing 6 of the 11 icons**; style mismatch | 5 min |
| Material Symbols XML + Compose Resources | Apache-2.0 ✅ | ✅ | `components-resources:1.11.1` (~200 KB) | ~1–2 KB per XML | 11 XML files + `Res` imports | Google set is current; XML format stable | 30 min |
| Material Symbols → `ImageVector` via Valkyrie | Apache-2.0 ✅ | ✅ | **none** | ~20 KB of Kotlin | generator + commit | current; regeneration manual | 30 min |
| **Lucide → `ImageVector` via Valkyrie** | **ISC + MIT ✅** | ✅ | **none** | **22.7 KB, 637 lines** | generator + commit | Lucide tags are versioned; names can change | **~15 min** |
| `com.composables:icons-lucide:1.1.0` | MIT POM ✅ (art ISC) | 🟡 built against CMP 1.6.11 | 1 line | **4.9–5.2 MB**, 1,522 files | none | snapshot Dec 2024; no per-icon selection | 5 min |
| Tabler → `ImageVector` via Valkyrie | MIT ✅ | ✅ | none | ~20 KB | same as Lucide | active, 6,238 icons | 15 min |
| Hand-written `ImageVector` | n/a (ISC/MIT if derived) | ✅ | none | ~20 KB | ~600 lines by hand | transcription errors; manual updates | 2–4 h |
| Remix Icon (any delivery) | **custom, not allowlisted ❌** | — | — | — | — | — | — |

---

## 8. Recommendation

### Primary — generate the Lucide set into `ImageVector` with Valkyrie, commit it, add no dependency

**Why this one is decisive:**

1. **It is the only option that delivers the requested geometry exactly.** The
   generator preserves `stroke-width=2`, `StrokeCap.Round` and
   `StrokeJoin.Round`; the output above is the proof.
2. **It adds zero runtime weight and zero licence-gate surface.** No Gradle
   coordinate, no `Res` class, no 5 MB pack, no 37.8 MB artefact. The app already
   depends on `compose.ui`, which owns `ImageVector`.
3. **The cost is one command and 22.7 KB of committed code** — measured, not
   estimated — and the result is ktlint-clean in this repo.
4. **The licence is the easiest of all options:** ISC plus a small MIT carve-out,
   both GPL-compatible, discharged by a notice file. No Apache NOTICE file, no
   custom terms.
5. **It keeps the icons reviewable.** A diff to `Library.kt` is readable; a 37.8 MB
   jar is not.

**Costs accepted:** the generated code is committed (a regenerated icon shows up
as a diff), Lucide's names can change (`trash-2` → `trash`), and the update is a
manual re-run rather than a version bump. Record the Lucide tag in the generator
script and the diff tells you exactly what changed.

### Alternative 1 — Material Symbols XML through Compose Resources

Choose this if the team would rather not commit generated Kotlin, or if Google's
glyph vocabulary is preferred and Lucide's exact stroke is negotiable. It is the
workflow JetBrains documents, Apache-2.0, one XML serves Android and desktop, and
the runtime is ~200 KB. It loses on geometry: filled 960-unit glyphs, no 2 px
stroke.

### Alternative 2 — `com.composables:icons-lucide:1.1.0`

Choose this only if you need **many** Lucide icons and want zero conversion work.
It is a real KMP publication (Android/JVM/iOS/JS/Wasm), MIT in its POM, and the
generated code preserves Lucide's strokes. It loses on weight (5 MB for 1,522
Kotlin files), on freshness (December 2024 snapshot, built against CMP 1.6.11), and
on control (no way to take ten icons).

### Not recommended, but recorded

- **Tabler** through the same pipeline if a pure-MIT provenance with no Feather
  carve-out is preferred; geometry is identical.
- **Hand-written** only if adding a tool to the contributor workflow is
  unacceptable.
- **Remix Icon** excluded on licence grounds.
- **`material-icons-*`** excluded on style, age and weight.

---

## 9. Concrete implementation of the primary option

### 9.1 Generate the icons

The brief named about ten icons; the set that landed with issue #184 is twelve, plus the
`info` icon issue #213 adds for the vault's folder-sync caution and the `check` issue #214
adds for a chosen entry — fourteen, and `docs/design.md` §5 is authoritative on it. Pin a Lucide tag (the tag
`1.59.0` resolves on `raw.githubusercontent.com`), fetch the SVGs into
`tools/icons/lucide/`, and run the CLI from the repository root:

```bash
# tools/icons/fetch.sh — record the tag so the set is reproducible
LUCIDE_TAG=1.59.0
for name in book-open bookmark settings search import arrow-left \
            volume-2 a-large-small x trash chevron-left chevron-right; do
  curl -fsSL -o "tools/icons/lucide/$name.svg" \
    "https://raw.githubusercontent.com/lucide-icons/lucide/$LUCIDE_TAG/icons/$name.svg"
done

# one-off generation (Valkyrie CLI 1.2.1, Apache-2.0)
valkyrie svgxml2imagevector \
  --input-path tools/icons/lucide \
  --output-path app/src/commonMain/kotlin/app/lekto/ui/icons \
  --package-name app.lekto.ui.icons \
  --iconpack-name LektoIcons \
  --trailing-comma=true
```

`--trailing-comma=true` is not cosmetic: without it, ktlint 1.8.0 reports five
`trailing-comma-on-call-site` violations on `Library.kt` with this repository's
`.editorconfig` (verified).

Then run `./gradlew check` once. The generated files are ordinary Kotlin; if
detekt's `MagicNumber` or `LongMethod` (40 lines) objects to a large icon, exclude
`app.lekto.ui.icons` from detekt rather than hand-editing generated code — I did
not run detekt on the output, so treat that as a check, not a known failure.

### 9.2 The pack object

The generator emits extension properties on a pack object; the object itself is
yours to declare once:

```kotlin
// app/src/commonMain/kotlin/app/lekto/ui/icons/LektoIcons.kt
package app.lekto.ui.icons

/** The Lucide icon set, generated by `tools/icons/fetch.sh`. */
object LektoIcons
```

### 9.3 Use an icon in a Material 3 `NavigationBarItem`

The extension property is imported by name, and `Icon` tints it with
`LocalContentColor` (its default size is 24 dp, which is the navigation-bar icon
size — [Android Developers, Icons](https://developer.android.com/develop/ui/compose/graphics/images/material)):

```kotlin
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import app.lekto.ui.icons.BookOpen
import app.lekto.ui.icons.LektoIcons

NavigationBarItem(
    selected = selectedDestination == Destination.Library,
    onClick = { onSelect(Destination.Library) },
    icon = {
        Icon(
            imageVector = LektoIcons.BookOpen,
            // The label below names the item; a description here would repeat it.
            contentDescription = null,
        )
    },
    label = { Text("Library") },
)
```

If the label is hidden (`alwaysShowLabel = false`), pass a real
`contentDescription` instead of `null`; the icon is then the only accessible name.

### 9.4 Right-to-left

`arrow-left` must mirror in RTL. Valkyrie's `--auto-mirror=true` applies to every
icon it converts, so run the generator a second time on `arrow-left` alone, or
patch that one file after generation:

```kotlin
ImageVector.Builder(
    name = "ArrowLeft",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
    autoMirror = true,   // back is directional
)
```

Regeneration overwrites it, so keep the step in `tools/icons/fetch.sh`.

### 9.5 Notices

Add `THIRD-PARTY-NOTICES.md` with:

- the full ISC text and "Copyright (c) 2026 Lucide Icons and Contributors";
- the Feather/MIT text and "Copyright (c) 2013-present Cole Bemis", listing the
  derived icons actually used (`arrow-left`, `chevron-left`, `chevron-right`,
  `search`, `trash`, `x`);
- the Lucide tag the SVGs came from.

This satisfies ISC's "appear in all copies" and MIT's notice condition without
touching the AGPL `LICENSE`.

### 9.6 If you pick Material Symbols instead

```toml
compose-components-resources = { module = "org.jetbrains.compose.components:components-resources", version.ref = "compose-multiplatform" }
```

```kotlin
// app/build.gradle.kts
commonMain.dependencies { implementation(libs.compose.components.resources) }
compose.resources { packageOfResClass = "app.lekto.resources" }
```

```kotlin
import app.lekto.resources.Res
import app.lekto.resources.ic_library
import org.jetbrains.compose.resources.painterResource

NavigationBarItem(
    selected = …,
    onClick = …,
    icon = { Icon(painterResource(Res.drawable.ic_library), contentDescription = null) },
    label = { Text("Library") },
)
```

For each downloaded Material Symbols XML, apply JetBrains' three edits: set
`android:fillColor="#000000"`, remove `android:tint`, and remove any other
Android-only colour attribute.

---

## 10. Unverified and flagged

- **Runtime compatibility of `material-icons-extended:1.7.3` with CMP 1.11.1.**
  It resolves and the API it uses is stable, but I did not build the app with it.
  JetBrains' own message is "will not receive updates", which is enough to reject
  it without testing.
- **`com.composables:icons-lucide:1.1.0` against CMP 1.11.1.** Built against
  Compose 1.6.11 (POM); Gradle would upgrade it. Not compiled here.
- **detekt on the generated files.** ktlint 1.8.0 is clean with
  `--trailing-comma=true` (measured). detekt 2.0's `MagicNumber`/`LongMethod`
  behaviour on these files was not run; `./gradlew check` will tell.
- **Pixel identity of XML vectors across Android and desktop.** JetBrains
  documents one XML dialect for every target and gives no platform caveat for XML
  (unlike SVG, which Android does not support). I did not run a screenshot diff.
- **Lucide tag contents vs `main`.** I fetched the measured run from `main` and
  verified that tag `1.59.0` resolves the same paths; pin the tag in the script
  and regenerate from it.
- **Remix Icon licence.** The repository's `License` (v1.0, January 2026) and the
  npm `license` field (Apache-2.0) disagree. The repository text is treated as
  authoritative here; confirm before any use.
- **`io.github.thelacspace:lucide-compose:1.16.0`.** The JVM jar is 27 KB with 8
  Kotlin files; I infer the publication is incomplete rather than intentional.
  Either way it is unusable as a full set.
- **Valkyrie CLI reproducibility.** Version 1.2.1 was used; the tool is active and
  its output format may change between releases. Pin the CLI version in the
  script the same way the build pins Gradle.

---

## References

**Compose Multiplatform / JetBrains**
- CMP changelog (1.8.0 icons-core removal, 1.10.1 deprecation note) — https://github.com/JetBrains/compose-multiplatform/blob/master/CHANGELOG.md
- CMP 1.8.2 "what's new" (implicit dependency removed; Material Symbols recommended) — https://kotlinlang.org/docs/multiplatform/whats-new-compose-180.html
- Compose Gradle plugin 1.11.1 source (`materialIconsExtended` pinned, `components-resources` accessor deprecated) — https://github.com/JetBrains/compose-multiplatform/blob/v1.11.1/gradle-plugins/compose/src/main/kotlin/org/jetbrains/compose/ComposePlugin.kt
- Multiplatform resources — setup — https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-multiplatform-resources-setup.html
- Multiplatform resources — usage (XML vectors, `painterResource`, `vectorResource`, SVG caveat) — https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-multiplatform-resources-usage.html
- `components-resources:1.11.1` POM (Apache-2.0) — https://repo1.maven.org/maven2/org/jetbrains/compose/components/components-resources/1.11.1/components-resources-1.11.1.pom

**Material Icons / Material Symbols**
- Android Developers, Material icons (no longer maintained; build-time warning; `Icon` defaults) — https://developer.android.com/develop/ui/compose/graphics/images/material
- `material-icons-extended` metadata (latest 1.7.3, 2024-12-19) — https://repo1.maven.org/maven2/org/jetbrains/compose/material/material-icons-extended/maven-metadata.xml
- `material-icons-core` metadata — https://repo1.maven.org/maven2/org/jetbrains/compose/material/material-icons-core/maven-metadata.xml
- `material-icons-extended-desktop:1.7.3` jar (37.8 MB) — https://repo1.maven.org/maven2/org/jetbrains/compose/material/material-icons-extended-desktop/1.7.3/material-icons-extended-desktop-1.7.3.jar
- `material-design-icons` licence (Apache-2.0) — https://github.com/google/material-design-icons/blob/master/LICENSE
- `material-design-icons` README (Symbols current, Icons frozen) — https://github.com/google/material-design-icons
- Material Symbols Android XML example (960 viewport, filled path, `android:tint`) — https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android/settings/materialsymbolsoutlined/settings_24px.xml

**Lucide**
- Repository licence (ISC + Feather MIT subset) — https://github.com/lucide-icons/lucide/blob/main/LICENSE
- Design specification (24 × 24, 2 px, round caps/joins) — https://lucide.dev/contribute/icons/specification
- Package list (no Kotlin/Compose package) — https://lucide.dev/packages
- Contribution guide (`icons/*.svg` is the single source of truth) — https://github.com/lucide-icons/lucide/blob/main/CONTRIBUTING.md
- Example SVG (`library.svg`) — https://raw.githubusercontent.com/lucide-icons/lucide/main/icons/library.svg

**Other icon sets**
- Tabler Icons (MIT, 24 × 24, 2 px, 6,238 icons) — https://github.com/tabler/tabler-icons
- Tabler licence — https://github.com/tabler/tabler-icons/blob/main/LICENSE
- Phosphor core (MIT, 256 filled) — https://github.com/phosphor-icons/core
- Phosphor licence — https://github.com/phosphor-icons/core/blob/main/LICENSE
- Iconoir (MIT, 24 × 24, 1.5 px) — https://github.com/iconoir-icons/iconoir
- Iconoir licence — https://github.com/iconoir-icons/iconoir/blob/main/LICENSE
- Remix Icon licence v1.0 — https://github.com/Remix-Design/RemixIcon/blob/master/License
- Remix Icon on npm (still declares Apache-2.0) — https://registry.npmjs.org/remixicon/latest

**Generators and ports**
- Valkyrie (Apache-2.0; CLI, plugin, Gradle-plugin compatibility table) — https://github.com/ComposeGears/Valkyrie
- Valkyrie SVG stroke parser — https://github.com/ComposeGears/Valkyrie/blob/main/components/parser/kmp/svg/src/commonMain/kotlin/io/github/composegears/valkyrie/parser/kmp/svg/SVGStroke.kt
- `svg-to-compose` (MIT, alternative converter) — https://github.com/rafaeltonholo/svg-to-compose
- `com.composables:icons-lucide:1.1.0` POM (MIT) — https://repo1.maven.org/maven2/com/composables/icons-lucide/1.1.0/icons-lucide-1.1.0.pom
- `composablehorizons/compose-icons` (README, Lucide 1.6k) — https://github.com/composablehorizons/compose-icons
- `io.github.thelacspace:lucide-compose` metadata — https://repo1.maven.org/maven2/io/github/thelacspace/lucide-compose/maven-metadata.xml
- `DevSrSouza/compose-icons` (Feather/Material, last push 2024) — https://github.com/DevSrSouza/compose-icons

**Licences**
- GNU licence list — ISC — https://www.gnu.org/licenses/license-list.html#ISC
- GNU licence list — Expat/MIT — https://www.gnu.org/licenses/license-list.html#Expat
- GNU licence list — Apache-2.0 — https://www.gnu.org/licenses/license-list.html#apache2
- Lekto ADR-0011 — [`docs/adr/0011-agpl3-with-separate-ccbysa-pack.md`](../adr/0011-agpl3-with-separate-ccbysa-pack.md)
- Lekto licence allowlist — [`config/dependency-licences.txt`](../../config/dependency-licences.txt)
