# Prototype — Lekto's visual identity

These images are the planches of the visual identity prototype. **They are throwaway**: the
prototype answers questions, it does not ship. What comes out of it is the decision, and the
decision lives in `docs/design.md` and, eventually, in
`app/src/commonMain/kotlin/app/lekto/theme/`.

They live on a throwaway branch, `prototype/visual-identity`, out of `main`. The pointer and the
verdict are on issue #168.

## The planches

| Image | The question it answers |
| ----- | ----------------------- |
| `identite.png` | What do the three papers, the ink and the mastery ramps look like, and which colours hold on each background? |
| `contraste.png` | Does the delivered palette fail, and by how much? |
| `traitements.png` | Colour the letters, underline them, or put a wash behind the word? |
| `selection.png` | How is the touched word marked, in the three backgrounds? |
| `geometrie.png` | How do a stroke icon set and a solid one differ? |
| `library.png` | What does the landing screen look like, in the three backgrounds? |
| `library-states.png` | Empty, loading, failed — and why may the first two never be confused? |
| `reader.png` | What does a page look like while being read, chrome included? |
| `reader-states.png` | The bare page, the tap zones, and the text panel. |
| `lookup.png` | The word panel over the page, in the three backgrounds. |
| `lookup-states.png` | A missing dictionary, a translated phrase, a phrase with no provider. |
| `desktop.png` | The desktop reader and its context column: chapters when nothing is touched, the word when something is. |
| `vocabulary.png` | The vocabulary screen, in the three backgrounds. |
| `vocabulary-states.png` | Empty vocabulary, no search result, and the delete confirmation. |
| `settings.png` | The settings index, the Appearance screen and the Languages screen. |
| `settings-forms.png` | The LLM provider, and the vault with its sync. |
| `icon.png` | Three mark candidates at 96 dp and 48 dp, and Android's launch screen on both papers. |
| `icon-mark.png` | The L in the book and the L on the page, at 96 dp and 48 dp, plus the launch screen. |
| `icon-final.png` | The decision: the page for the icon, the L alone for the monochrome layer and the launch screen. |

## Regenerate

From the repository root:

```sh
docs/prototype/visual-identity/record.sh
```

The script runs Roborazzi on the desktop target and moves the images here. A JVM is all it needs;
Gradle, the JDK and the dependencies are provisioned by the wrapper.

## What the prototype contains

- `app/src/desktopTest/kotlin/app/lekto/prototype/VisualIdentityPrototype.kt` — the planches, with
  the candidate tokens written inline. They depend on no production code, because the theme does
  not exist yet.
- `app/src/desktopTest/kotlin/app/lekto/prototype/icons/` — the icons generated for the
  demonstration: the Lucide set (`lucide/`) and six Material Symbols (`symbols/`), converted by
  Valkyrie from downloaded SVGs. None of this is the final set, which its own ticket will generate
  into `commonMain`.

## Why the images are committed

An image under `app/build/` disappears at the first `clean`. These planches are what the issue
points at and what the review reads, so they are committed — on this branch only.
